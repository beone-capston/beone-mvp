package com.beone.api.monthlystate.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
import com.beone.api.codef.application.CodefApprovalLedgerQuery;
import com.beone.api.codef.application.CodefPerformanceReferenceQuery;
import com.beone.api.ledger.application.LedgerActivity;
import com.beone.api.ledger.application.LedgerEntryType;
import com.beone.api.ledger.application.LedgerQueryService;
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
 *
 * <p>CODEF 실적 참고값(D-16, 사용자 결정 2026-11-27, #281)은 {@code codef} 패키지의 {@link CodefPerformanceReferenceQuery}로 읽기만
 * 한다. 전월 실적 칸에 미리 채워 보여 줄 참고값({@code codefReference})을 주되 계산에는 쓰지 않는다. 사용자가 참고값 ID를 함께 보내면 값은
 * 사용자가 정한 {@code MANUAL}로 저장하고 참고한 CODEF 값을 스냅샷으로 남긴다. 같은 기준 기간의 최신 참고값 금액이 전월 실적 값과 다르고
 * 그 값이 바로 그 참고값을 참고해 저장된 것이 아니면 출처 충돌이며, 전월 실적은 {@code usable=false}({@code SOURCE_CONFLICT})로 계산에서
 * 빠진다. 해소는 최신 참고값 ID로 다시 저장하는 것이다. 자동 우선순위는 없다.
 *
 * <p>CODEF 승인 합계 추정값(INFERRED, 사용자 결정 2026-11-27 T4, #289)은 {@link CodefApprovalLedgerQuery}로 읽는다. 전월 실적 칸에
 * 전월 승인 합계({@code inferredReference})를 보여 주되 계산에 쓰지 않는다. 사용자가 그 근거 동기화 ID를 함께 보내면 지금 다시 낸 합계가
 * 완전하고 근거가 같을 때만 값을 {@code MANUAL}로 저장하며 참고한 합계·산출 시각을 남긴다. 추정값은 보조 표시값이라 수동 값과 달라도
 * 충돌로 보지 않는다.
 */
@Service
public class MonthlyStateService {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private static final String UNKNOWN_IN_RULE = "카드 규칙에 없는 값입니다.";

	private static final String REFERENCED_CODEF_FIELD = "previousMonthPerformanceWon.referencedCodefId";

	private static final String REFERENCED_CODEF_INVALID = "참고할 수 없는 카드사 조회 실적입니다.";

	private static final String REFERENCED_INFERRED_FIELD = "previousMonthPerformanceWon.referencedInferredSyncId";

	private static final String REFERENCED_INFERRED_INVALID = "참고할 수 없는 승인 합계 추정값입니다.";

	/** 충돌 항목 이름. 응답 필드 이름과 같다. */
	static final String PREVIOUS_MONTH_ITEM = "previousMonthPerformanceWon";

	private static final DateTimeFormatter VERSION_TIME = DateTimeFormatter.ofPattern("uuuuMMddHHmmss");

	private final MonthlyStateRepository monthlyStateRepository;

	private final BucketUsageRepository bucketUsageRepository;

	private final ServiceSelectionRepository serviceSelectionRepository;

	private final UserCardService userCardService;

	private final CardProductService cardProductService;

	private final LedgerQueryService ledgerQueryService;

	private final CodefPerformanceReferenceQuery codefReferences;

	private final CodefApprovalLedgerQuery codefApprovals;

	private final Clock clock;

	/**
	 * {@link Clock} 빈이 하나 있으면 그것을, 없으면 시스템 시계를 쓴다.
	 * @param monthlyStateRepository 월간 상태 저장소
	 * @param bucketUsageRepository 한도 묶음 사용액 저장소
	 * @param serviceSelectionRepository 택1 선택 저장소
	 * @param userCardService 보유 카드 조회(소유·매핑)
	 * @param cardProductService 카드 규칙 요약(한도 묶음·서비스 ID)
	 * @param ledgerQueryService 스냅샷 뒤 모의 승인·취소 요약(#120)
	 * @param codefReferences CODEF 실적 참고값 조회(읽기만, #281)
	 * @param codefApprovals CODEF 승인 합계 추정값 조회(읽기만, #289)
	 * @param clock 입력 시각을 정할 시계
	 */
	@Autowired
	public MonthlyStateService(MonthlyStateRepository monthlyStateRepository,
			BucketUsageRepository bucketUsageRepository, ServiceSelectionRepository serviceSelectionRepository,
			UserCardService userCardService, CardProductService cardProductService,
			LedgerQueryService ledgerQueryService, CodefPerformanceReferenceQuery codefReferences,
			CodefApprovalLedgerQuery codefApprovals, ObjectProvider<Clock> clock) {
		this(monthlyStateRepository, bucketUsageRepository, serviceSelectionRepository, userCardService,
				cardProductService, ledgerQueryService, codefReferences, codefApprovals,
				clock.getIfUnique(Clock::systemUTC));
	}

	MonthlyStateService(MonthlyStateRepository monthlyStateRepository, BucketUsageRepository bucketUsageRepository,
			ServiceSelectionRepository serviceSelectionRepository, UserCardService userCardService,
			CardProductService cardProductService, LedgerQueryService ledgerQueryService,
			CodefPerformanceReferenceQuery codefReferences, CodefApprovalLedgerQuery codefApprovals, Clock clock) {
		this.monthlyStateRepository = Objects.requireNonNull(monthlyStateRepository, "monthlyStateRepository");
		this.bucketUsageRepository = Objects.requireNonNull(bucketUsageRepository, "bucketUsageRepository");
		this.serviceSelectionRepository = Objects.requireNonNull(serviceSelectionRepository,
				"serviceSelectionRepository");
		this.userCardService = Objects.requireNonNull(userCardService, "userCardService");
		this.cardProductService = Objects.requireNonNull(cardProductService, "cardProductService");
		this.ledgerQueryService = Objects.requireNonNull(ledgerQueryService, "ledgerQueryService");
		this.codefReferences = Objects.requireNonNull(codefReferences, "codefReferences");
		this.codefApprovals = Objects.requireNonNull(codefApprovals, "codefApprovals");
		this.clock = Objects.requireNonNull(clock, "clock").withZone(SEOUL);
	}

	/**
	 * 이 카드·이 달의 상태. 입력한 적이 없으면 모든 값이 미입력인 결과다.
	 * @param user 현재 사용자
	 * @param userCardId 보유 카드 ID 외부 표현
	 * @param month 대상 달
	 * @return 이 카드·이 달의 상태
	 * @throws MonthlyStateCardNotFoundException 없거나 해제했거나 다른 사용자의 카드
	 */
	@Transactional(readOnly = true)
	public MonthlyStateView get(CurrentUser user, String userCardId, YearMonth month) {
		UserCardView card = findCard(user, userCardId);
		long cardId = UserCardId.parse(card.userCardId()).orElseThrow();
		return this.monthlyStateRepository
			.findByUserIdAndUserCardIdAndStateMonth(user.userId(), cardId, month.toString())
			.map(state -> toView(user, card, month, state, ruleProductOf(card), true))
			.orElseGet(() -> emptyView(card.userCardId(), month, codefReference(user, cardId, month),
					inferredReference(user, cardId, month)));
	}

	/**
	 * 이 카드·이 달의 수동 입력을 교체한다. 저장 시각이 {@code snapshotAt}과 모든 값의 입력 시각이 된다. 가상 포트폴리오도 쓸 수 있다
	 * (D-19).
	 * @param user 현재 사용자
	 * @param userCardId 보유 카드 ID 외부 표현
	 * @param month 대상 달
	 * @param input 교체할 수동 입력
	 * @return 저장한 상태
	 * @throws MonthlyStateCardNotFoundException 없거나 해제했거나 다른 사용자의 카드
	 * @throws MappingRequiredException 매핑이 확인되지 않은 카드에 한도 묶음·택1을 보냄
	 * @throws RuleMismatchException 카드 규칙에 없는 ID, 선택형 서비스가 없는 카드의 선택 없음, 참고할 수 없는 CODEF 참고값 ID·승인 합계
	 *     추정값 근거
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
		CodefPerformanceReferenceQuery.Reference referenced = referencedCodef(user, cardId,
				input.previousMonthPerformanceWon());
		CodefApprovalLedgerQuery.Estimate inferred = referencedInferred(user, cardId, month,
				input.previousMonthPerformanceWon());
		// 행을 먼저 확보한 뒤 잠가 같은 카드·같은 달의 교체를 첫 저장부터 차례로 처리한다.
		this.monthlyStateRepository.insertIfAbsent(user.userId(), cardId, month.toString(), now());
		MonthlyStateEntity state = this.monthlyStateRepository
			.findForUpdateByUserIdAndUserCardIdAndStateMonth(user.userId(), cardId, month.toString())
			.orElseThrow(() -> new IllegalStateException("monthly state row must exist after insertIfAbsent"));
		// 저장 시각은 잠금을 얻은 뒤에 읽는다. 앞선 요청을 기다린 요청의 snapshotAt이 그 요청보다 앞서지 않게 한다.
		LocalDateTime now = now();
		MonthlyStateInput.Amount previous = input.previousMonthPerformanceWon();
		MonthlyStateInput.Amount other = input.otherBucketsUsedWon();
		state.replace(productId, now, (previous == null) ? null : previous.valueWon(),
				(previous == null) ? null : previous.period().from(), (previous == null) ? null : previous.period().to(),
				(other == null) ? null : other.valueWon(), (other == null) ? null : other.period().from(),
				(other == null) ? null : other.period().to(), input.registeredOn(), input.noServiceSelection() != null,
				(input.noServiceSelection() == null) ? null : input.noServiceSelection().effectiveFrom());
		if (referenced != null) {
			state.referPreviousMonthToCodef(referenced.referenceId(), referenced.syncId(), referenced.currentUseWon(),
					referenced.periodFrom(), referenced.periodTo(), referenced.fetchedAt());
		}
		if (inferred != null) {
			state.referPreviousMonthToInferred(inferred.totalWon(), inferred.basisSyncId(), now);
		}
		MonthlyStateEntity saved = this.monthlyStateRepository.saveAndFlush(state);
		replaceChildren(saved.getMonthlyStateId(), input);
		return toView(user, card, month, saved, productId, false);
	}

	/**
	 * 출처 충돌의 계산 기준을 고른다(관리자 판단 P10, D-16). 카드를 확인한 뒤, 충돌이 없으면 항상
	 * {@link SourceConflictNotFoundException}이다. 가상 포트폴리오도 다른 사용자와 같다(월간 상태는 가상 포트폴리오에서도 동작, D-19).
	 *
	 * <p>이 메서드는 항상 예외로 끝나고 아무것도 저장하지 않는다. 전월 실적과 CODEF 참고값의 충돌(#281)은 조회 {@code conflicts[]}에
	 * 나오지만, CODEF 값을 계산 기준으로 고르면 D-16의 "자동 반영 안 함"과 맞지 않아 여기서 고르지 않고 최신 참고값으로 다시 저장해
	 * 해소한다(사용자 결정 2026-11-27 Q3). 승인 합계 추정값({@code INFERRED}, #289)은 보조 표시값이라 충돌을 만들지 않는다.
	 *
	 * <p>확장 지점: 월간 상태에 두 번째 출처(CODEF 값 반영 또는 {@code INFERRED} 산출)가 결정으로 열리면, 이 카드·항목·기준 기간의
	 * 출처별 후보를 모아 값이 다를 때만 충돌로 보고, {@code conflictVersion} 대조(바뀌었으면 409 {@code SOURCE_CONFLICT_CHANGED}),
	 * 고른 출처 값 없음(400), 선택 저장과 200 응답, 월간 상태 조회 {@code conflicts[]}를 함께 구현한다.
	 * @param user 현재 사용자
	 * @param userCardId 보유 카드 ID 외부 표현
	 * @param month 대상 달
	 * @param selection 고른 계산 기준
	 * @throws MonthlyStateCardNotFoundException 없거나 해제했거나 다른 사용자의 카드
	 * @throws SourceConflictNotFoundException 항상(계산 기준 선택을 저장하지 않음)
	 */
	@Transactional(readOnly = true)
	public void selectSource(CurrentUser user, String userCardId, YearMonth month, SourceSelection selection) {
		Objects.requireNonNull(month, "month");
		Objects.requireNonNull(selection, "selection");
		findCard(user, userCardId);
		throw new SourceConflictNotFoundException();
	}

	// 사용자가 보낸 참고값 ID를 확인한다: 본인의 참고값이고, 이 카드에 연결됐고, 반영기간이 입력한 기준 기간과 정확히 같아야 한다(D-16 #281)
	private CodefPerformanceReferenceQuery.Reference referencedCodef(CurrentUser user, long cardId,
			MonthlyStateInput.Amount previous) {
		if (previous == null || previous.referencedCodefId() == null) {
			return null;
		}
		return this.codefReferences.find(user.userId(), previous.referencedCodefId())
			.filter(reference -> reference.userCardId() == cardId)
			.filter(reference -> reference.periodFrom().equals(previous.period().from())
					&& reference.periodTo().equals(previous.period().to()))
			.orElseThrow(() -> new RuleMismatchException(
					List.of(new RuleMismatchException.Violation(REFERENCED_CODEF_FIELD, REFERENCED_CODEF_INVALID))));
	}

	// 사용자가 보낸 승인 합계 추정값 근거를 확인한다: 지금 다시 낸 전월 합계가 완전하고, 근거 동기화가 보낸 값과 같고, 입력한 기준 기간이
	// 전월 1일~말일이어야 한다(#289). 그 사이 동기화로 근거가 바뀌었으면 화면이 새 추정값을 다시 보여 주도록 400이다
	private CodefApprovalLedgerQuery.Estimate referencedInferred(CurrentUser user, long cardId, YearMonth month,
			MonthlyStateInput.Amount previous) {
		if (previous == null || previous.referencedInferredSyncId() == null) {
			return null;
		}
		YearMonth target = month.minusMonths(1);
		return this.codefApprovals.estimate(user.userId(), cardId, target)
			.filter(CodefApprovalLedgerQuery.Estimate::complete)
			.filter(estimate -> estimate.basisSyncId() == previous.referencedInferredSyncId())
			.filter(estimate -> previous.period().from().equals(target.atDay(1))
					&& previous.period().to().equals(target.atEndOfMonth()))
			.orElseThrow(() -> new RuleMismatchException(List
				.of(new RuleMismatchException.Violation(REFERENCED_INFERRED_FIELD, REFERENCED_INFERRED_INVALID))));
	}

	// 전월 실적 칸에 미리 채워 보여 줄 승인 합계 추정값(INFERRED, #289). 계산에 쓰지 않는다
	private MonthlyStateView.InferredReference inferredReference(CurrentUser user, long cardId, YearMonth month) {
		YearMonth target = month.minusMonths(1);
		return this.codefApprovals.estimate(user.userId(), cardId, target)
			.map(estimate -> new MonthlyStateView.InferredReference(target,
					new DatePeriod(target.atDay(1), target.atEndOfMonth()), estimate.totalWon(),
					estimate.incompleteReasons(), estimate.basisSyncId(), estimate.basisSyncedAt()))
			.orElse(null);
	}

	private static MonthlyStateView.ReferencedInferred referencedInferred(MonthlyStateEntity state) {
		if (state.getPreviousMonthInferredComputedAt() == null) {
			return null;
		}
		return new MonthlyStateView.ReferencedInferred(state.getPreviousMonthInferredSyncId(),
				state.getPreviousMonthInferredWon(), state.getPreviousMonthInferredComputedAt());
	}

	// 전월 실적 칸에 미리 채워 보여 줄 참고값: 이 카드에 연결된 참고값 중 반영기간이 대상 월의 전월 1일~말일인 최신 값
	private MonthlyStateView.CodefReference codefReference(CurrentUser user, long cardId, YearMonth month) {
		YearMonth previous = month.minusMonths(1);
		return this.codefReferences.latest(user.userId(), cardId, previous.atDay(1), previous.atEndOfMonth())
			.map(MonthlyStateService::toCodefReference)
			.orElse(null);
	}

	private static MonthlyStateView.CodefReference toCodefReference(CodefPerformanceReferenceQuery.Reference reference) {
		return new MonthlyStateView.CodefReference(reference.referenceId(),
				new DatePeriod(reference.periodFrom(), reference.periodTo()), reference.currentUseWon(),
				reference.fetchedAt());
	}

	private LocalDateTime now() {
		return LocalDateTime.now(this.clock).truncatedTo(ChronoUnit.SECONDS);
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

	private MonthlyStateView toView(CurrentUser user, UserCardView card, YearMonth month, MonthlyStateEntity state,
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
		long cardId = state.getUserCardId();
		StateValue<Long> previous = amount(state.getPreviousMonthWon(), state.getPreviousMonthFrom(),
				state.getPreviousMonthTo(), at);
		MonthlyStateView.ReferencedCodef referenced = referencedCodef(state);
		MonthlyStateView.Conflict conflict = previousMonthConflict(user, cardId, previous, referenced);
		return new MonthlyStateView(card.userCardId(), month, at, (conflict == null) ? previous : previous.conflicted(),
				usages,
				amount(state.getOtherBucketsUsedWon(), state.getOtherBucketsFrom(), state.getOtherBucketsTo(), at),
				selections, status, noSelection,
				(state.getRegisteredOn() == null) ? StateValue.unknown()
						: StateValue.manual(state.getRegisteredOn(),
								new DatePeriod(state.getRegisteredOn(), state.getRegisteredOn()), at),
				withAfterSnapshot ? afterSnapshot(user, card.userCardId(), month, at) : null,
				codefReference(user, cardId, month), referenced, (conflict == null) ? List.of() : List.of(conflict),
				inferredReference(user, cardId, month), referencedInferred(state));
	}

	private static MonthlyStateView.ReferencedCodef referencedCodef(MonthlyStateEntity state) {
		if (state.getPreviousMonthCodefFetchedAt() == null) {
			return null;
		}
		return new MonthlyStateView.ReferencedCodef(state.getPreviousMonthCodefReferenceId(),
				state.getPreviousMonthCodefSyncId(),
				new DatePeriod(state.getPreviousMonthCodefFrom(), state.getPreviousMonthCodefTo()),
				state.getPreviousMonthCodefWon(), state.getPreviousMonthCodefFetchedAt());
	}

	/**
	 * 전월 실적의 출처 충돌(D-16, 사용자 결정 2026-11-27 Q1). 같은 기준 기간의 최신 연결 참고값 금액이 있고 전월 실적 값과 다르며, 그 값이
	 * 바로 그 참고값을 참고해 저장된 것이 아니면 충돌이다. 참고 없이 입력한 값도 마찬가지다. 값이 같거나, 참고값 금액을 모르거나(미확인은 충돌이
	 * 아님), 기준 기간이 다르면 충돌이 아니다.
	 */
	private MonthlyStateView.Conflict previousMonthConflict(CurrentUser user, long cardId, StateValue<Long> previous,
			MonthlyStateView.ReferencedCodef referenced) {
		if (previous.source() != StateSource.MANUAL) {
			return null;
		}
		return this.codefReferences
			.latest(user.userId(), cardId, previous.period().from(), previous.period().to())
			.filter(reference -> reference.currentUseWon() != null)
			.filter(reference -> !reference.currentUseWon().equals(previous.value()))
			.filter(reference -> referenced == null || referenced.referenceId() == null
					|| referenced.referenceId() != reference.referenceId())
			.map(reference -> new MonthlyStateView.Conflict(PREVIOUS_MONTH_ITEM, previous.period(),
					"cf-" + reference.referenceId() + "-" + VERSION_TIME.format(previous.updatedAt()),
					List.of(new MonthlyStateView.Candidate(StateSource.MANUAL, previous.value(), previous.period(),
							previous.updatedAt()),
							new MonthlyStateView.Candidate(StateSource.CODEF, reference.currentUseWon(),
									new DatePeriod(reference.periodFrom(), reference.periodTo()),
									reference.fetchedAt()))))
			.orElse(null);
	}

	// 스냅샷 뒤 모의 승인·취소 요약(docs/api/monthly-states.md afterSnapshot, #120). 화면 표시용이며 상태값을 덮어쓰지 않는다.
	// 승인 금액은 승인 행 합계이고 취소는 건수로만 센다.
	private MonthlyStateView.AfterSnapshot afterSnapshot(CurrentUser user, String userCardId, YearMonth month,
			LocalDateTime snapshotAt) {
		int approvals = 0;
		long approvedWon = 0;
		int cancellations = 0;
		for (LedgerActivity activity : this.ledgerQueryService.activityAfter(user, userCardId, month, snapshotAt)) {
			if (activity.entryType() == LedgerEntryType.APPROVAL) {
				approvals++;
				approvedWon += activity.amountWon();
			}
			else {
				cancellations++;
			}
		}
		return new MonthlyStateView.AfterSnapshot(approvals, approvedWon, cancellations);
	}

	private static StateValue<Long> amount(Long value, LocalDate from, LocalDate to,
			LocalDateTime at) {
		return (value == null) ? StateValue.unknown() : StateValue.manual(value, new DatePeriod(from, to), at);
	}

	private static MonthlyStateView emptyView(String userCardId, YearMonth month,
			MonthlyStateView.CodefReference codefReference, MonthlyStateView.InferredReference inferredReference) {
		return new MonthlyStateView(userCardId, month, null, StateValue.unknown(), List.of(), StateValue.unknown(),
				List.of(), ServiceSelectionStatus.UNKNOWN, null, StateValue.unknown(), null, codefReference, null,
				List.of(), inferredReference, null);
	}

}
