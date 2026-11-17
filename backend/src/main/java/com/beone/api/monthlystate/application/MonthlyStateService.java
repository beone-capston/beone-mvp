package com.beone.api.monthlystate.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.beone.api.card.application.CardProductDetail;
import com.beone.api.card.application.CardProductService;
import com.beone.api.card.application.HoldingSource;
import com.beone.api.card.application.MappingStatus;
import com.beone.api.card.application.UserCardId;
import com.beone.api.card.application.UserCardService;
import com.beone.api.card.application.UserCardView;
import com.beone.api.monthlystate.persistence.BucketUsageEntity;
import com.beone.api.monthlystate.persistence.BucketUsageRepository;
import com.beone.api.monthlystate.persistence.MonthlyStateEntity;
import com.beone.api.monthlystate.persistence.MonthlyStateRepository;
import com.beone.api.monthlystate.persistence.ServiceSelectionEntity;
import com.beone.api.monthlystate.persistence.ServiceSelectionRepository;
import com.beone.api.user.application.CurrentUser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보유 카드 한 장의 월간 상태를 조회하고 수동 입력으로 교체한다(docs/api/monthly-states.md, D-16·D-17·D-18 K1·D-19).
 *
 * <p>보유 카드 존재·소유·매핑은 {@code card} 패키지의 {@link UserCardService}로, 한도 묶음·서비스 ID는 {@link CardProductService}로
 * 확인한다(읽기만). 기준 기간이 카드의 실적·한도 기간과 맞는지는 판단하지 않고 받은 값을 저장한다(D-22). 스냅샷 뒤 모의 승인 합산은
 * 모의 결제(B3)가 생긴 뒤 연결한다.
 */
@Service
public class MonthlyStateService {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private static final String UNKNOWN_IN_RULE = "카드 규칙에 없는 값입니다.";

	private final MonthlyStateRepository monthlyStateRepository;

	private final BucketUsageRepository bucketUsageRepository;

	private final ServiceSelectionRepository serviceSelectionRepository;

	private final UserCardService userCardService;

	private final CardProductService cardProductService;

	private final Clock clock;

	/**
	 * {@link Clock} 빈이 하나 있으면 그것을, 없으면 시스템 시계를 쓴다.
	 * @param monthlyStateRepository 월간 상태 저장소
	 * @param bucketUsageRepository 한도 묶음 사용액 저장소
	 * @param serviceSelectionRepository 택1 선택 저장소
	 * @param userCardService 보유 카드 조회(소유·매핑)
	 * @param cardProductService 카드 규칙 요약(한도 묶음·서비스 ID)
	 * @param clock 입력 시각을 정할 시계
	 */
	@Autowired
	public MonthlyStateService(MonthlyStateRepository monthlyStateRepository,
			BucketUsageRepository bucketUsageRepository, ServiceSelectionRepository serviceSelectionRepository,
			UserCardService userCardService, CardProductService cardProductService, ObjectProvider<Clock> clock) {
		this(monthlyStateRepository, bucketUsageRepository, serviceSelectionRepository, userCardService,
				cardProductService, clock.getIfUnique(Clock::systemUTC));
	}

	MonthlyStateService(MonthlyStateRepository monthlyStateRepository, BucketUsageRepository bucketUsageRepository,
			ServiceSelectionRepository serviceSelectionRepository, UserCardService userCardService,
			CardProductService cardProductService, Clock clock) {
		this.monthlyStateRepository = Objects.requireNonNull(monthlyStateRepository, "monthlyStateRepository");
		this.bucketUsageRepository = Objects.requireNonNull(bucketUsageRepository, "bucketUsageRepository");
		this.serviceSelectionRepository = Objects.requireNonNull(serviceSelectionRepository,
				"serviceSelectionRepository");
		this.userCardService = Objects.requireNonNull(userCardService, "userCardService");
		this.cardProductService = Objects.requireNonNull(cardProductService, "cardProductService");
		this.clock = Objects.requireNonNull(clock, "clock").withZone(SEOUL);
	}

	/**
	 * 이 카드·이 달의 상태. 입력한 적이 없으면 모든 값이 미입력인 결과다.
	 * @throws MonthlyStateCardNotFoundException 없거나 해제했거나 다른 사용자의 카드
	 */
	@Transactional(readOnly = true)
	public MonthlyStateView get(CurrentUser user, String userCardId, YearMonth month) {
		UserCardView card = findCard(user, userCardId);
		long cardId = UserCardId.parse(card.userCardId()).orElseThrow();
		return this.monthlyStateRepository
			.findByUserIdAndUserCardIdAndStateMonth(user.userId(), cardId, month.toString())
			.map(state -> toView(card, month, state, ruleProductOf(card), true))
			.orElseGet(() -> emptyView(card.userCardId(), month));
	}

	/**
	 * 이 카드·이 달의 수동 입력을 교체한다. 저장 시각이 {@code snapshotAt}과 모든 값의 입력 시각이 된다. 가상 포트폴리오도 쓸 수 있다
	 * (D-19).
	 * @throws MonthlyStateCardNotFoundException 없거나 해제했거나 다른 사용자의 카드
	 * @throws MappingRequiredException 매핑이 확인되지 않은 카드에 한도 묶음·택1을 보냄
	 * @throws RuleMismatchException 카드 규칙에 없는 ID, 선택형 서비스가 없는 카드의 선택 없음
	 */
	@Transactional
	public MonthlyStateView replace(CurrentUser user, String userCardId, YearMonth month, MonthlyStateInput input) {
		UserCardView card = findCard(user, userCardId);
		String productId = ruleProductOf(card);
		if (input.needsRule()) {
			if (productId == null) {
				throw new MappingRequiredException();
			}
			checkAgainstRule(productId, month, input);
		}
		long cardId = UserCardId.parse(card.userCardId()).orElseThrow();
		LocalDateTime now = LocalDateTime.now(this.clock).truncatedTo(ChronoUnit.SECONDS);
		// 행을 먼저 확보한 뒤 잠가 같은 카드·같은 달의 교체를 첫 저장부터 차례로 처리한다.
		this.monthlyStateRepository.insertIfAbsent(user.userId(), cardId, month.toString(), now);
		MonthlyStateEntity state = this.monthlyStateRepository
			.findForUpdateByUserIdAndUserCardIdAndStateMonth(user.userId(), cardId, month.toString())
			.orElseThrow(() -> new IllegalStateException("monthly state row must exist after insertIfAbsent"));
		MonthlyStateInput.Amount previous = input.previousMonthPerformanceWon();
		MonthlyStateInput.Amount other = input.otherBucketsUsedWon();
		state.replace(productId, now, (previous == null) ? null : previous.valueWon(),
				(previous == null) ? null : previous.period().from(), (previous == null) ? null : previous.period().to(),
				(other == null) ? null : other.valueWon(), (other == null) ? null : other.period().from(),
				(other == null) ? null : other.period().to(), input.registeredOn(), input.noServiceSelection() != null,
				(input.noServiceSelection() == null) ? null : input.noServiceSelection().effectiveFrom());
		MonthlyStateEntity saved = this.monthlyStateRepository.saveAndFlush(state);
		replaceChildren(saved.getMonthlyStateId(), input);
		return toView(card, month, saved, productId, false);
	}

	private void replaceChildren(long stateId, MonthlyStateInput input) {
		// 같은 ID를 다시 넣을 수 있도록 지운 뒤(flush) 넣는다. 유니크(monthly_state_id, ID)와 충돌하지 않는다.
		this.bucketUsageRepository.deleteByMonthlyStateId(stateId);
		this.serviceSelectionRepository.deleteByMonthlyStateId(stateId);
		List<BucketUsageEntity> usages = new ArrayList<>();
		for (int i = 0; i < input.limitBucketUsages().size(); i++) {
			MonthlyStateInput.BucketUsage usage = input.limitBucketUsages().get(i);
			usages.add(new BucketUsageEntity(stateId, i, usage.limitBucketId(), usage.valueWon(), usage.period().from(),
					usage.period().to()));
		}
		List<ServiceSelectionEntity> selections = new ArrayList<>();
		for (int i = 0; i < input.selectedServices().size(); i++) {
			MonthlyStateInput.Selection selection = input.selectedServices().get(i);
			selections.add(new ServiceSelectionEntity(stateId, i, selection.serviceId(), selection.effectiveFrom()));
		}
		this.bucketUsageRepository.saveAllAndFlush(usages);
		this.serviceSelectionRepository.saveAllAndFlush(selections);
	}

	// 그 달 1일과 말일에 적용되는 규칙의 한도 묶음·선택형 서비스 ID 합집합으로 검사한다(결정 27 A3-2·A3-3).
	private void checkAgainstRule(String productId, YearMonth month, MonthlyStateInput input) {
		Set<String> bucketIds = new LinkedHashSet<>();
		Set<String> selectionServiceIds = new LinkedHashSet<>();
		for (CardProductDetail detail : List.of(this.cardProductService.getProduct(productId, month.atDay(1)),
				this.cardProductService.getProduct(productId, month.atEndOfMonth()))) {
			if (detail.rule() == null) {
				continue;
			}
			detail.limitBuckets().forEach(bucket -> bucketIds.add(bucket.limitBucketId()));
			detail.services()
				.stream()
				.filter(CardProductDetail.Service::requiresSelection)
				.forEach(service -> selectionServiceIds.add(service.serviceId()));
		}
		List<RuleMismatchException.Violation> violations = new ArrayList<>();
		for (int i = 0; i < input.limitBucketUsages().size(); i++) {
			if (!bucketIds.contains(input.limitBucketUsages().get(i).limitBucketId())) {
				violations.add(new RuleMismatchException.Violation("limitBucketUsages[" + i + "].limitBucketId",
						UNKNOWN_IN_RULE));
			}
		}
		for (int i = 0; i < input.selectedServices().size(); i++) {
			if (!selectionServiceIds.contains(input.selectedServices().get(i).serviceId())) {
				violations.add(new RuleMismatchException.Violation("selectedServices[" + i + "].serviceId",
						UNKNOWN_IN_RULE));
			}
		}
		if (input.noServiceSelection() != null && selectionServiceIds.isEmpty()) {
			violations.add(new RuleMismatchException.Violation("noServiceSelection", "선택형 서비스가 없는 카드입니다."));
		}
		if (!violations.isEmpty()) {
			throw new RuleMismatchException(violations);
		}
	}

	private UserCardView findCard(CurrentUser user, String userCardId) {
		return this.userCardService.list(user)
			.stream()
			.filter(card -> card.userCardId().equals(userCardId))
			.findFirst()
			.orElseThrow(MonthlyStateCardNotFoundException::new);
	}

	// 계산 규칙의 상품: 가상 포트폴리오는 카드 상품 자체, 그 밖에는 USER_CONFIRMED 매핑 상품. 없으면 null(매핑 확인 필요).
	private static String ruleProductOf(UserCardView card) {
		if (card.holdingSource() == HoldingSource.SYNTHETIC) {
			return card.cardProductId();
		}
		if (card.mapping().status() == MappingStatus.USER_CONFIRMED) {
			return card.mapping().cardProductId();
		}
		return null;
	}

	private MonthlyStateView toView(UserCardView card, YearMonth month, MonthlyStateEntity state,
			String currentProductId, boolean withAfterSnapshot) {
		LocalDateTime at = state.getSnapshotAt();
		// 저장 때와 지금의 규칙 상품이 다르면 그 버킷·서비스 ID는 지금 규칙의 것이 아니므로 미입력으로 보여 준다(결정 27 A3-7).
		boolean sameRule = Objects.equals(state.getCardProductId(), currentProductId);
		List<MonthlyStateView.BucketUsage> usages = sameRule ? this.bucketUsageRepository
			.findByMonthlyStateIdOrderBySeqAsc(state.getMonthlyStateId())
			.stream()
			.map(usage -> new MonthlyStateView.BucketUsage(usage.getLimitBucketId(), StateValue.manual(
					usage.getUsedWon(), new DatePeriod(usage.getPeriodFrom(), usage.getPeriodTo()), at)))
			.toList() : List.of();
		List<MonthlyStateView.Selection> selections = sameRule ? this.serviceSelectionRepository
			.findByMonthlyStateIdOrderBySeqAsc(state.getMonthlyStateId())
			.stream()
			.map(selection -> new MonthlyStateView.Selection(selection.getServiceId(), selection.getEffectiveFrom(), at))
			.toList() : List.of();
		MonthlyStateView.NoSelection noSelection = (sameRule && state.isNoServiceSelection())
				? new MonthlyStateView.NoSelection(state.getNoSelectionEffectiveFrom(), at) : null;
		ServiceSelectionStatus status = !selections.isEmpty() ? ServiceSelectionStatus.SELECTED
				: (noSelection != null) ? ServiceSelectionStatus.NONE : ServiceSelectionStatus.UNKNOWN;
		return new MonthlyStateView(card.userCardId(), month, at,
				amount(state.getPreviousMonthWon(), state.getPreviousMonthFrom(), state.getPreviousMonthTo(), at), usages,
				amount(state.getOtherBucketsUsedWon(), state.getOtherBucketsFrom(), state.getOtherBucketsTo(), at),
				selections, status, noSelection,
				(state.getRegisteredOn() == null) ? StateValue.unknown()
						: StateValue.manual(state.getRegisteredOn(),
								new DatePeriod(state.getRegisteredOn(), state.getRegisteredOn()), at),
				withAfterSnapshot ? MonthlyStateView.AfterSnapshot.NO_SIMULATED_PAYMENTS : null);
	}

	private static StateValue<Long> amount(Long value, LocalDate from, LocalDate to,
			LocalDateTime at) {
		return (value == null) ? StateValue.unknown() : StateValue.manual(value, new DatePeriod(from, to), at);
	}

	private static MonthlyStateView emptyView(String userCardId, YearMonth month) {
		return new MonthlyStateView(userCardId, month, null, StateValue.unknown(), List.of(), StateValue.unknown(),
				List.of(), ServiceSelectionStatus.UNKNOWN, null, StateValue.unknown(), null);
	}

}
