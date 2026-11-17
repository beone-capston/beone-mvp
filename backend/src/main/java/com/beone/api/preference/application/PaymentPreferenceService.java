package com.beone.api.preference.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import com.beone.api.card.application.UserCardId;
import com.beone.api.card.application.UserCardNotFoundException;
import com.beone.api.card.application.UserCardService;
import com.beone.api.card.application.UserCardView;
import com.beone.api.preference.persistence.PaymentPreferenceEntity;
import com.beone.api.preference.persistence.PaymentPreferenceRepository;
import com.beone.api.user.application.CurrentUser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 현재 사용자의 결제 전 설정을 조회·변경한다(docs/api/preferences.md, D-12·D-15·D-23).
 *
 * <p>대표 카드는 해제하지 않은 자기 카드이면서 추천 비교 대상(보유 카드의 {@code recommendationEligibility.eligible})이어야 한다.
 * 매핑이 {@code USER_CONFIRMED}이거나 가상 포트폴리오 카드({@code SYNTHETIC})이고, 사용자가 추천에서 빼지 않은 카드다. 검사 순서는
 * 요청 형식 400(웹 계층) → 카드 없음 404 → 적격 아님 409다. 대표 카드의 혜택 {@code 확인 필요}(D-19)는 여기서 보지 않는다.
 */
@Service
public class PaymentPreferenceService {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final PaymentPreferenceRepository paymentPreferenceRepository;

	private final UserCardService userCardService;

	private final Clock clock;

	/**
	 * {@link Clock} 빈이 하나 있으면 그것을, 없으면 시스템 시계를 쓴다.
	 * @param paymentPreferenceRepository 설정 저장소
	 * @param userCardService 대표 카드 존재·적격 확인
	 * @param clock 설정 시각을 정할 시계
	 */
	@Autowired
	public PaymentPreferenceService(PaymentPreferenceRepository paymentPreferenceRepository,
			UserCardService userCardService, ObjectProvider<Clock> clock) {
		this(paymentPreferenceRepository, userCardService, clock.getIfUnique(Clock::systemUTC));
	}

	PaymentPreferenceService(PaymentPreferenceRepository paymentPreferenceRepository, UserCardService userCardService,
			Clock clock) {
		this.paymentPreferenceRepository = Objects.requireNonNull(paymentPreferenceRepository,
				"paymentPreferenceRepository");
		this.userCardService = Objects.requireNonNull(userCardService, "userCardService");
		this.clock = Objects.requireNonNull(clock, "clock").withZone(SEOUL);
	}

	/** 저장된 설정. 설정한 적이 없으면 {@link PaymentPreferenceView#NOT_SET}이다. */
	@Transactional(readOnly = true)
	public PaymentPreferenceView get(CurrentUser user) {
		return this.paymentPreferenceRepository.findById(user.userId())
			.map(PaymentPreferenceService::toView)
			.orElse(PaymentPreferenceView.NOT_SET);
	}

	/**
	 * 설정을 저장한다. 대표 카드 {@code null}은 대표 카드를 지운다.
	 * @throws UserCardNotFoundException 대표 카드가 없거나 다른 사용자·해제한 카드, 또는 ID 형식이 틀림
	 * @throws RepresentativeCardNotEligibleException 대표 카드가 추천 비교 대상이 아님
	 */
	@Transactional
	public PaymentPreferenceView change(CurrentUser user, PaymentPreferenceChange change) {
		// 첫 문장으로 사용자 행을 잠가 대표 카드 보호(보유 카드 추천 제외·해제)와 차례로 처리한다. 잠금을 얻은 뒤 카드를 처음 읽으므로
		// 앞서 커밋된 해제·추천 제외를 본다.
		this.paymentPreferenceRepository.lockUser(user.userId());
		Long cardId = (change.representativeUserCardId() == null) ? null
				: requireEligibleCard(user, change.representativeUserCardId());
		LocalDateTime now = LocalDateTime.now(this.clock).truncatedTo(ChronoUnit.SECONDS);
		this.paymentPreferenceRepository.upsert(user.userId(), change.selectionMode().name(), cardId, now);
		return new PaymentPreferenceView(change.selectionMode(), (cardId == null) ? null : UserCardId.format(cardId),
				now);
	}

	private long requireEligibleCard(CurrentUser user, String userCardId) {
		long id = UserCardId.parse(userCardId).orElseThrow(UserCardNotFoundException::new);
		String normalized = UserCardId.format(id);
		UserCardView card = this.userCardService.list(user)
			.stream()
			.filter(view -> view.userCardId().equals(normalized))
			.findFirst()
			.orElseThrow(UserCardNotFoundException::new);
		if (!card.recommendationEligibility().eligible()) {
			throw new RepresentativeCardNotEligibleException();
		}
		return id;
	}

	private static PaymentPreferenceView toView(PaymentPreferenceEntity entity) {
		Long cardId = entity.getRepresentativeUserCardId();
		return new PaymentPreferenceView(entity.getSelectionMode(), (cardId == null) ? null : UserCardId.format(cardId),
				entity.getUpdatedAt());
	}

}
