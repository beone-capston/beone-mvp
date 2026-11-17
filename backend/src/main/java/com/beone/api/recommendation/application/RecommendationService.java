package com.beone.api.recommendation.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.beone.api.benefit.engine.EngineRequest;
import com.beone.api.benefit.engine.EngineResult;
import com.beone.api.benefit.engine.HeldCardInput;
import com.beone.api.benefit.engine.RecommendationEngine;
import com.beone.api.card.application.HoldingSource;
import com.beone.api.card.application.UserCardId;
import com.beone.api.card.application.UserCardService;
import com.beone.api.card.application.UserCardView;
import com.beone.api.monthlystate.application.MonthlyStateService;
import com.beone.api.monthlystate.application.MonthlyStateView;
import com.beone.api.order.application.OrderDetail;
import com.beone.api.order.application.OrderId;
import com.beone.api.order.application.OrderService;
import com.beone.api.order.application.OrderStatus;
import com.beone.api.preference.application.PaymentPreferenceService;
import com.beone.api.preference.application.PaymentPreferenceView;
import com.beone.api.recommendation.persistence.RecommendationCardBasisEntity;
import com.beone.api.recommendation.persistence.RecommendationCardBasisRepository;
import com.beone.api.recommendation.persistence.RecommendationEntity;
import com.beone.api.recommendation.persistence.RecommendationRepository;
import com.beone.api.user.application.CurrentUser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 주문 하나의 추천을 계산·저장하고, 저장한 결과를 다시 조회한다(docs/api/recommendations.md, D-12·D-15·D-18·D-22·D-23).
 *
 * <p>혜택 계산·비교·동률 처리는 엔진 입구({@link RecommendationEngine}) 한 번 호출로만 한다. 이 서비스는 주문(order)·보유 카드(card)·
 * 그 주문 월의 월간 상태(monthlystate)·결제 전 설정(preference)을 공개 부품으로 읽어 엔진 입력을 만들고, 결과를 계약 모양으로 옮겨 저장한다.
 * 다른 패키지 코드는 바꾸지 않는다.
 */
@Service
public class RecommendationService {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final RecommendationRepository recommendationRepository;

	private final RecommendationCardBasisRepository cardBasisRepository;

	private final OrderService orderService;

	private final UserCardService userCardService;

	private final MonthlyStateService monthlyStateService;

	private final PaymentPreferenceService paymentPreferenceService;

	private final RecommendationEngine engine;

	private final JsonMapper jsonMapper;

	private final Clock clock;

	/**
	 * {@link Clock} 빈이 하나 있으면 그것을, 없으면 시스템 시계를 쓴다.
	 * @param recommendationRepository 추천 저장소
	 * @param cardBasisRepository 카드 근거 저장소
	 * @param orderService 주문 조회
	 * @param userCardService 보유 카드 조회
	 * @param monthlyStateService 월간 상태 조회
	 * @param paymentPreferenceService 결제 전 설정 조회
	 * @param engine 엔진 입구
	 * @param jsonMapper 응답 본문 저장용 JSON 변환기
	 * @param clock 계산 시각을 정할 시계
	 */
	@Autowired
	public RecommendationService(RecommendationRepository recommendationRepository,
			RecommendationCardBasisRepository cardBasisRepository, OrderService orderService,
			UserCardService userCardService, MonthlyStateService monthlyStateService,
			PaymentPreferenceService paymentPreferenceService, RecommendationEngine engine, JsonMapper jsonMapper,
			ObjectProvider<Clock> clock) {
		this(recommendationRepository, cardBasisRepository, orderService, userCardService, monthlyStateService,
				paymentPreferenceService, engine, jsonMapper, clock.getIfUnique(Clock::systemUTC));
	}

	RecommendationService(RecommendationRepository recommendationRepository,
			RecommendationCardBasisRepository cardBasisRepository, OrderService orderService,
			UserCardService userCardService, MonthlyStateService monthlyStateService,
			PaymentPreferenceService paymentPreferenceService, RecommendationEngine engine, JsonMapper jsonMapper,
			Clock clock) {
		this.recommendationRepository = Objects.requireNonNull(recommendationRepository, "recommendationRepository");
		this.cardBasisRepository = Objects.requireNonNull(cardBasisRepository, "cardBasisRepository");
		this.orderService = Objects.requireNonNull(orderService, "orderService");
		this.userCardService = Objects.requireNonNull(userCardService, "userCardService");
		this.monthlyStateService = Objects.requireNonNull(monthlyStateService, "monthlyStateService");
		this.paymentPreferenceService = Objects.requireNonNull(paymentPreferenceService, "paymentPreferenceService");
		this.engine = Objects.requireNonNull(engine, "engine");
		this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
		this.clock = Objects.requireNonNull(clock, "clock").withZone(SEOUL);
	}

	/**
	 * 주문의 추천을 계산해 저장한다. 같은 주문이라도 부를 때마다 새 추천을 만든다.
	 * @throws com.beone.api.order.application.OrderNotFoundException 없거나 다른 사용자의 주문
	 * @throws OrderAlreadyPaidException 이미 모의 승인된 주문
	 */
	@Transactional
	public RecommendationDocument calculate(CurrentUser user, String orderId) {
		OrderDetail order = this.orderService.get(user, orderId);
		if (isPaid(order)) {
			throw new OrderAlreadyPaidException();
		}
		YearMonth month = YearMonth.from(order.orderDate());
		List<UserCardView> cards = this.userCardService.list(user);
		List<HeldCardInput> inputs = new ArrayList<>();
		Map<String, MonthlyStateView> states = new LinkedHashMap<>();
		for (UserCardView card : cards) {
			MonthlyStateView state = this.monthlyStateService.get(user, card.userCardId(), month);
			states.put(card.userCardId(), state);
			inputs.add(EngineInputAssembler.heldCard(card, state));
		}
		PaymentPreferenceView preference = this.paymentPreferenceService.get(user);
		String representative = heldOrNull(preference.representativeUserCardId(), cards);
		String selectionMode = preference.selectionMode().name();
		EngineResult result = this.engine.recommend(new EngineRequest(order.orderDate(),
				EngineInputAssembler.order(order), selectionMode, representative, inputs));

		LocalDateTime now = LocalDateTime.now(this.clock).truncatedTo(ChronoUnit.SECONDS);
		long orderKey = OrderId.parse(order.orderId()).orElseThrow();
		RecommendationEntity entity = this.recommendationRepository
			.saveAndFlush(new RecommendationEntity(user.userId(), orderKey, month.toString(), now));
		Map<String, String> snapshotAt = new LinkedHashMap<>();
		states.forEach((userCardId, state) -> {
			if (state.snapshotAt() != null) {
				snapshotAt.put(userCardId, DocumentAssembler.seoulInstant(state.snapshotAt()));
			}
		});
		RecommendationDocument document = DocumentAssembler.document(
				RecommendationId.format(entity.getRecommendationId()), now, order, selectionMode, representative, cards,
				snapshotAt, result);
		entity.attachDocument(this.jsonMapper.writeValueAsString(document));
		this.cardBasisRepository.saveAll(cards.stream()
			.map(card -> basis(entity.getRecommendationId(), card, states.get(card.userCardId())))
			.toList());
		return document;
	}

	/**
	 * 저장한 추천을 다시 계산하지 않고 돌려준다. 무효화 여부만 지금 상태와 비교해 판정한다.
	 * @throws RecommendationNotFoundException 없거나 형식이 틀렸거나 다른 사용자의 추천
	 */
	@Transactional(readOnly = true)
	public RecommendationView get(CurrentUser user, String recommendationId) {
		RecommendationEntity entity = RecommendationId.parse(recommendationId)
			.flatMap(id -> this.recommendationRepository.findByRecommendationIdAndUserId(id, user.userId()))
			.orElseThrow(RecommendationNotFoundException::new);
		RecommendationDocument document = this.jsonMapper.readValue(entity.getDocumentJson(),
				RecommendationDocument.class);
		Optional<InvalidationReason> reason = invalidation(user, entity, document.orderId());
		return new RecommendationView(document, reason.isPresent(), reason.orElse(null));
	}

	// 우선순위: ORDER_PAID > MAPPING_CHANGED > MONTHLY_STATE_CHANGED(결정 27 B2-4)
	private Optional<InvalidationReason> invalidation(CurrentUser user, RecommendationEntity entity, String orderId) {
		if (isPaid(this.orderService.get(user, orderId))) {
			return Optional.of(InvalidationReason.ORDER_PAID);
		}
		List<RecommendationCardBasisEntity> bases = this.cardBasisRepository
			.findByRecommendationIdOrderByCardBasisIdAsc(entity.getRecommendationId());
		List<UserCardView> current = this.userCardService.list(user);
		for (RecommendationCardBasisEntity basis : bases) {
			Optional<UserCardView> card = find(current, UserCardId.format(basis.getUserCardId()));
			if (card.isEmpty() || !sameComparisonTarget(basis, card.get())) {
				return Optional.of(InvalidationReason.MAPPING_CHANGED);
			}
		}
		YearMonth month = YearMonth.parse(entity.getOrderMonth());
		for (RecommendationCardBasisEntity basis : bases) {
			MonthlyStateView state = this.monthlyStateService.get(user, UserCardId.format(basis.getUserCardId()),
					month);
			if (!Objects.equals(state.snapshotAt(), basis.getMonthlySnapshotAt())) {
				return Optional.of(InvalidationReason.MONTHLY_STATE_CHANGED);
			}
		}
		return Optional.empty();
	}

	/** 주문 상태가 {@code CREATED}가 아니면 모의 승인된 주문으로 본다(결정 27 B2-1). */
	static boolean isPaid(OrderDetail order) {
		return order.status() != OrderStatus.CREATED;
	}

	private static boolean sameComparisonTarget(RecommendationCardBasisEntity basis, UserCardView card) {
		return Objects.equals(basis.getMappingStatus(), mappingStatusOf(card))
				&& Objects.equals(basis.getRuleCardProductId(), EngineInputAssembler.ruleProductOf(card))
				&& basis.isExcludedByUser() == card.excludedFromRecommendation();
	}

	private static RecommendationCardBasisEntity basis(long recommendationId, UserCardView card,
			MonthlyStateView state) {
		return new RecommendationCardBasisEntity(recommendationId, UserCardId.parse(card.userCardId()).orElseThrow(),
				mappingStatusOf(card), EngineInputAssembler.ruleProductOf(card), card.excludedFromRecommendation(),
				state.snapshotAt());
	}

	private static String mappingStatusOf(UserCardView card) {
		return (card.holdingSource() == HoldingSource.SYNTHETIC) ? null : card.mapping().status().name();
	}

	// 대표 카드가 보유 카드 목록에 없으면 넘기지 않는다. 결제 전 설정이 해제를 막으므로 보통 생기지 않는다.
	private static String heldOrNull(String representative, List<UserCardView> cards) {
		return (representative != null && find(cards, representative).isPresent()) ? representative : null;
	}

	private static Optional<UserCardView> find(List<UserCardView> cards, String userCardId) {
		return cards.stream().filter(card -> card.userCardId().equals(userCardId)).findFirst();
	}

}
