package com.beone.api.codef.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import com.beone.api.codef.persistence.CodefApprovalRowEntity;
import com.beone.api.codef.persistence.CodefApprovalRowRepository;
import com.beone.api.codef.persistence.CodefHeldCardEntity;
import com.beone.api.codef.persistence.CodefHeldCardRepository;
import com.beone.api.codef.persistence.CodefSyncEntity;
import com.beone.api.codef.persistence.CodefSyncIncompleteMarkEntity;
import com.beone.api.codef.persistence.CodefSyncIncompleteMarkRepository;
import com.beone.api.codef.persistence.CodefSyncRepository;
import com.beone.api.codef.sync.CodefIncompleteReason;
import com.beone.api.codef.sync.CodefLedgerRowKind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저장된 CODEF 승인 행과 그 달의 완전성, 승인 합계 추정값(INFERRED)을 다른 패키지가 읽는 창구(사용자 결정 2026-11-27 T4, #289).
 * 원장 조회가 CODEF 행과 완전성을, 월간 상태가 전월 승인 합계 추정값을 보여 주는 데 쓴다. 읽기만 하며 추정값을 계산 입력으로 바꾸지 않는다.
 *
 * <p>완전성 규칙(카드 C, 달 M):
 * <ul>
 * <li>승인 판정을 마친 동기화({@code approvals_judged = TRUE}, V18 뒤)만 본다. 동기화가 덮는 날은 조회 기간 중 조회일 전날까지다(조회일
 * 당일 거래는 뒤에 더 생길 수 있음). 그래서 이번 달은 다음 달에 동기화하기 전까지 항상 불완전이다.</li>
 * <li>M 전체를 덮는 동기화가 있으면 그중 가장 최근 것과 그 뒤 동기화만 본다(오래된 동기화의 불완전 표시는 재조회로 대체됨). 없으면 M과 겹치는
 * 동기화를 모두 본다. 본 동기화들이 덮는 날의 합집합이 M 전체가 아니면 {@link #PERIOD_NOT_COVERED}다.</li>
 * <li>본 동기화의 불완전 행 중 카드 C이고 사용월이 M이거나 사용월을 모르는 것의 사유를 더한다(해외·원화 아님·판단 불가 행은
 * {@code UNDETERMINED_ROW}).</li>
 * </ul>
 * 추정 합계는 완전한 달에서 사용일자가 M인 정상 승인({@code APPROVAL}) 행 금액의 합이다. 전액 취소(Type1)·취소(Type2) 행이 M에 있으면 취소
 * 접수월을 몰라(D-17) {@link #CANCELLATION_ATTRIBUTION_UNCONFIRMED}로 합계를 내지 않는다. 사유가 있으면 합계는 {@code null}이고 0으로
 * 바꾸지 않는다.
 */
@Service
public class CodefApprovalLedgerQuery {

	/** 승인 판정을 마친 동기화가 그 달 전체를 덮지 않음(이번 달, V18 전 동기화만 있음, 승인 조회 실패 포함). */
	public static final String PERIOD_NOT_COVERED = "PERIOD_NOT_COVERED";

	/** 그 달에 취소 행이 있어 접수월을 모름(D-17). 추정 합계에만 쓴다. */
	public static final String CANCELLATION_ATTRIBUTION_UNCONFIRMED = "CANCELLATION_ATTRIBUTION_UNCONFIRMED";

	private final CodefApprovalRowRepository approvalRows;

	private final CodefSyncRepository syncs;

	private final CodefSyncIncompleteMarkRepository incompleteMarks;

	private final CodefHeldCardRepository heldCards;

	public CodefApprovalLedgerQuery(CodefApprovalRowRepository approvalRows, CodefSyncRepository syncs,
			CodefSyncIncompleteMarkRepository incompleteMarks, CodefHeldCardRepository heldCards) {
		this.approvalRows = Objects.requireNonNull(approvalRows, "approvalRows");
		this.syncs = Objects.requireNonNull(syncs, "syncs");
		this.incompleteMarks = Objects.requireNonNull(incompleteMarks, "incompleteMarks");
		this.heldCards = Objects.requireNonNull(heldCards, "heldCards");
	}

	/**
	 * 주어진 보유 카드들에서 사용일자가 그 달인 승인 행. 사용일자 → 사용일시 → 행 ID 오름차순.
	 * @param userId 사용자 ID
	 * @param userCardIds 보유 카드 ID
	 * @param month 달
	 * @return 승인 행
	 */
	@Transactional(readOnly = true)
	public List<ApprovalRow> rows(long userId, Collection<Long> userCardIds, YearMonth month) {
		if (userCardIds.isEmpty()) {
			return List.of();
		}
		return this.approvalRows
			.findByUserIdAndUserCardIdInAndUsedDateBetweenOrderByUsedDateAscUsedTimeAscCodefApprovalRowIdAsc(userId,
					userCardIds, month.atDay(1), month.atEndOfMonth())
			.stream()
			.map(CodefApprovalLedgerQuery::toRow)
			.toList();
	}

	/**
	 * 그 달의 CODEF 완전성. CODEF로 만든 보유 카드만 담는다(수동 카드에는 CODEF 행이 생기지 않아 완전성을 판단하지 않음).
	 * @param userId 사용자 ID
	 * @param userCardIds 보유 카드 ID
	 * @param month 달
	 * @return CODEF 카드별 완전성(입력 순서)과 카드 미연결 행의 불완전 사유
	 */
	@Transactional(readOnly = true)
	public MonthCoverage coverage(long userId, Collection<Long> userCardIds, YearMonth month) {
		Window window = window(userId, month);
		Map<Long, Coverage> cards = new LinkedHashMap<>();
		for (Long cardId : codefCards(userId, userCardIds)) {
			cards.put(cardId, window.coverage(cardId::equals, month));
		}
		return new MonthCoverage(cards, window.markReasons(Objects::isNull, month));
	}

	/**
	 * 한 CODEF 카드의 그 달 승인 합계 추정값. 계산에 쓰지 않는다.
	 * @param userId 사용자 ID
	 * @param userCardId 보유 카드 ID
	 * @param month 합계를 낼 달
	 * @return 추정값. CODEF 카드가 아니거나 그 달과 겹치는 승인 판정 동기화가 없으면 빈 값
	 */
	@Transactional(readOnly = true)
	public Optional<Estimate> estimate(long userId, long userCardId, YearMonth month) {
		if (codefCards(userId, List.of(userCardId)).isEmpty()) {
			return Optional.empty();
		}
		Window window = window(userId, month);
		if (window.considered().isEmpty()) {
			return Optional.empty();
		}
		// 카드 미연결 표시(user_card_id NULL)는 이 카드의 것이 아니다
		Coverage coverage = window.coverage(id -> id != null && id == userCardId, month);
		List<ApprovalRow> rows = rows(userId, List.of(userCardId), month);
		List<String> reasons = new ArrayList<>(coverage.incompleteReasons());
		if (rows.stream().anyMatch(row -> row.kind() != CodefLedgerRowKind.APPROVAL)) {
			reasons.add(CANCELLATION_ATTRIBUTION_UNCONFIRMED);
		}
		Long total = reasons.isEmpty() ? rows.stream().mapToLong(ApprovalRow::signedAmount).sum() : null;
		return Optional.of(new Estimate(month, total, reasons, coverage.basisSyncId(), coverage.basisSyncedAt()));
	}

	private List<Long> codefCards(long userId, Collection<Long> userCardIds) {
		if (userCardIds.isEmpty()) {
			return List.of();
		}
		Set<Long> codef = new HashSet<>();
		for (CodefHeldCardEntity card : this.heldCards.findByUserIdAndUserCardIdIn(userId, userCardIds)) {
			codef.add(card.getUserCardId());
		}
		return userCardIds.stream().filter(codef::contains).toList();
	}

	private Window window(long userId, YearMonth month) {
		List<SyncSpan> spans = this.syncs.findJudgedOverlapping(userId, month.atDay(1), month.atEndOfMonth())
			.stream()
			.map(CodefApprovalLedgerQuery::toSpan)
			.toList();
		List<SyncSpan> considered = consider(spans, month);
		List<CodefSyncIncompleteMarkEntity> marks = considered.isEmpty() ? List.of()
				: this.incompleteMarks.findByUserIdAndSyncIdIn(userId,
						considered.stream().map(SyncSpan::syncId).toList());
		return new Window(considered, covers(considered, month), marks);
	}

	/**
	 * 그 달 판단에 쓸 동기화를 고른다. 달과 겹치는(덮는 날이 있는) 동기화 중, 달 전체를 덮는 가장 최근 동기화가 있으면 그것과 그 뒤 동기화만
	 * 남긴다. 최근 순서는 조회 시각 → 동기화 ID다.
	 * @param spans 승인 판정 동기화
	 * @param month 달
	 * @return 고른 동기화(최근 순)
	 */
	static List<SyncSpan> consider(List<SyncSpan> spans, YearMonth month) {
		List<SyncSpan> overlapping = spans.stream()
			.filter(span -> !span.coveredTo().isBefore(span.from()))
			.filter(span -> !span.coveredTo().isBefore(month.atDay(1)) && !span.from().isAfter(month.atEndOfMonth()))
			.sorted(SyncSpan.LATEST_FIRST)
			.toList();
		for (int index = 0; index < overlapping.size(); index++) {
			SyncSpan span = overlapping.get(index);
			if (!span.from().isAfter(month.atDay(1)) && !span.coveredTo().isBefore(month.atEndOfMonth())) {
				return overlapping.subList(0, index + 1);
			}
		}
		return overlapping;
	}

	/**
	 * 고른 동기화가 덮는 날의 합집합이 그 달 전체인지.
	 * @param considered 고른 동기화
	 * @param month 달
	 * @return 전체를 덮으면 {@code true}
	 */
	static boolean covers(List<SyncSpan> considered, YearMonth month) {
		List<SyncSpan> byStart = considered.stream().sorted(Comparator.comparing(SyncSpan::from)).toList();
		LocalDate next = month.atDay(1);
		for (SyncSpan span : byStart) {
			if (span.from().isAfter(next)) {
				return false;
			}
			if (!span.coveredTo().isBefore(next)) {
				next = span.coveredTo().plusDays(1);
			}
			if (next.isAfter(month.atEndOfMonth())) {
				return true;
			}
		}
		return false;
	}

	private static SyncSpan toSpan(CodefSyncEntity sync) {
		LocalDate dayBefore = sync.getSyncedAt().toLocalDate().minusDays(1);
		LocalDate coveredTo = sync.getPeriodTo().isBefore(dayBefore) ? sync.getPeriodTo() : dayBefore;
		return new SyncSpan(sync.getSyncId(), sync.getPeriodFrom(), coveredTo, sync.getSyncedAt());
	}

	private static ApprovalRow toRow(CodefApprovalRowEntity row) {
		return new ApprovalRow(row.getCodefApprovalRowId(), row.getUserCardId(), row.getSyncId(), row.getRowKind(),
				row.getUsedDate(), row.getUsedTime(), row.getSignedAmount());
	}

	private record Window(List<SyncSpan> considered, boolean covered, List<CodefSyncIncompleteMarkEntity> marks) {

		Coverage coverage(Predicate<Long> card, YearMonth month) {
			List<String> reasons = new ArrayList<>(markReasons(card, month));
			if (!this.covered) {
				reasons.add(PERIOD_NOT_COVERED);
			}
			SyncSpan basis = this.considered.isEmpty() ? null : this.considered.get(0);
			return new Coverage(reasons, (basis == null) ? null : basis.syncId(),
					(basis == null) ? null : basis.syncedAt());
		}

		List<String> markReasons(Predicate<Long> card, YearMonth month) {
			EnumSet<CodefIncompleteReason> reasons = EnumSet.noneOf(CodefIncompleteReason.class);
			for (CodefSyncIncompleteMarkEntity mark : this.marks) {
				if (card.test(mark.getUserCardId())
						&& (mark.getUsedMonth() == null || mark.getUsedMonth().equals(month.toString()))) {
					reasons.add(mark.getReason());
				}
			}
			return reasons.stream().map(Enum::name).toList();
		}

	}

	/**
	 * 동기화 한 번이 덮는 날.
	 *
	 * @param syncId 동기화 ID
	 * @param from 조회 기간 시작일
	 * @param coveredTo 덮는 마지막 날(조회 기간 종료일과 조회일 전날 중 이른 날)
	 * @param syncedAt 조회 시각(서울)
	 */
	record SyncSpan(long syncId, LocalDate from, LocalDate coveredTo, LocalDateTime syncedAt) {

		static final Comparator<SyncSpan> LATEST_FIRST = Comparator.comparing(SyncSpan::syncedAt)
			.thenComparingLong(SyncSpan::syncId)
			.reversed();

	}

	/**
	 * 저장된 CODEF 승인 행 하나. 승인번호·카드번호·가맹점은 담지 않는다.
	 *
	 * @param rowId 승인 행 ID
	 * @param userCardId 보유 카드 ID
	 * @param syncId 이 행을 처음 저장한 동기화 ID
	 * @param kind 정상 승인 / 전액 취소된 승인(Type1, 한 행) / 짝지은 취소(Type2)
	 * @param usedDate 사용일자
	 * @param usedTime 사용일시
	 * @param signedAmount 부호 포함 원화 금액(취소는 음수)
	 */
	public record ApprovalRow(long rowId, long userCardId, long syncId, CodefLedgerRowKind kind, LocalDate usedDate,
			LocalTime usedTime, long signedAmount) {
	}

	/**
	 * 한 카드의 그 달 CODEF 원장 완전성.
	 *
	 * @param incompleteReasons 불완전 사유(동기화 판정 사유 → {@link #PERIOD_NOT_COVERED}). 비면 완전
	 * @param basisSyncId 판단에 쓴 가장 최근 동기화 ID, 없으면 {@code null}
	 * @param basisSyncedAt 그 동기화의 조회 시각, 없으면 {@code null}
	 */
	public record Coverage(List<String> incompleteReasons, Long basisSyncId, LocalDateTime basisSyncedAt) {

		public Coverage {
			incompleteReasons = List.copyOf(incompleteReasons);
		}

		/**
		 * 모든 거래를 확보했다고 말할 수 있는지.
		 * @return 사유가 없으면 {@code true}
		 */
		public boolean complete() {
			return this.incompleteReasons.isEmpty();
		}

	}

	/**
	 * 그 달 CODEF 완전성 묶음.
	 *
	 * @param cards CODEF 카드별 완전성
	 * @param unlinkedReasons 보유 카드에 붙지 않은 불완전 행의 사유. 특정 카드를 불완전으로 만들지 않는다
	 */
	public record MonthCoverage(Map<Long, Coverage> cards, List<String> unlinkedReasons) {

		public MonthCoverage {
			cards = Collections.unmodifiableMap(new LinkedHashMap<>(cards));
			unlinkedReasons = List.copyOf(unlinkedReasons);
		}

	}

	/**
	 * 한 카드의 그 달 승인 합계 추정값(INFERRED). 계산에 쓰지 않는다.
	 *
	 * @param month 합계를 낸 달
	 * @param totalWon 정상 승인 금액 합. 사유가 있으면 {@code null}
	 * @param incompleteReasons 합계를 낼 수 없는 사유. 비면 완전
	 * @param basisSyncId 판단에 쓴 가장 최근 동기화 ID
	 * @param basisSyncedAt 그 동기화의 조회 시각
	 */
	public record Estimate(YearMonth month, Long totalWon, List<String> incompleteReasons, long basisSyncId,
			LocalDateTime basisSyncedAt) {

		public Estimate {
			Objects.requireNonNull(month, "month");
			incompleteReasons = List.copyOf(incompleteReasons);
			Objects.requireNonNull(basisSyncedAt, "basisSyncedAt");
			if ((totalWon == null) == incompleteReasons.isEmpty()) {
				throw new IllegalArgumentException("total must be present exactly when complete");
			}
		}

		/**
		 * 합계를 낼 수 있는지.
		 * @return 사유가 없으면 {@code true}
		 */
		public boolean complete() {
			return this.incompleteReasons.isEmpty();
		}

	}

}
