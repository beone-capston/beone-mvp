package com.beone.api.codef.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * CODEF 승인 원장 창구(#289). 동기화가 덮는 날(조회일 전날까지), 최근 전체 덮기 동기화 뒤만 보기, 불완전 표시의 카드·월 범위, 추정 합계의
 * 취소·불완전 처리를 확인한다. 값은 합성이다. 기대값은 규칙 문장에서 손으로 셌다.
 */
class CodefApprovalLedgerQueryTest {

	private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

	private static final long USER = 1L;

	private static final long CARD = 11L;

	private final CodefApprovalRowRepository rows = mock(CodefApprovalRowRepository.class);

	private final CodefSyncRepository syncs = mock(CodefSyncRepository.class);

	private final CodefSyncIncompleteMarkRepository marks = mock(CodefSyncIncompleteMarkRepository.class);

	private final CodefHeldCardRepository held = mock(CodefHeldCardRepository.class);

	private final CodefApprovalLedgerQuery query = new CodefApprovalLedgerQuery(this.rows, this.syncs, this.marks,
			this.held);

	private final List<CodefSyncEntity> judged = new ArrayList<>();

	private final List<CodefSyncIncompleteMarkEntity> stored = new ArrayList<>();

	private final List<CodefApprovalRowEntity> approvals = new ArrayList<>();

	@BeforeEach
	void setUp() {
		given(this.held.findByUserIdAndUserCardIdIn(eq(USER), anyCollection()))
			.willAnswer(invocation -> ((java.util.Collection<?>) invocation.getArgument(1)).contains(CARD)
					? List.of(new CodefHeldCardEntity(CARD, USER, "SYN CARD", null, 1L)) : List.of());
		given(this.syncs.findJudgedOverlapping(eq(USER), any(), any())).willAnswer(invocation -> this.judged);
		given(this.marks.findByUserIdAndSyncIdIn(eq(USER), anyCollection()))
			.willAnswer(invocation -> this.stored.stream()
				.filter(mark -> ((java.util.Collection<?>) invocation.getArgument(1)).contains(mark.getSyncId()))
				.toList());
		given(this.rows.findByUserIdAndUserCardIdInAndUsedDateBetweenOrderByUsedDateAscUsedTimeAscCodefApprovalRowIdAsc(
				eq(USER), anyCollection(), any(), any()))
			.willAnswer(invocation -> this.approvals);
	}

	private void sync(long id, String from, String to, String syncedAt) {
		CodefSyncEntity entity = new CodefSyncEntity(USER, LocalDate.parse(from), LocalDate.parse(to),
				LocalDateTime.parse(syncedAt));
		ReflectionTestUtils.setField(entity, "syncId", id);
		entity.recordApprovalsJudged(true);
		this.judged.add(entity);
	}

	private void mark(long syncId, Long card, String month, CodefIncompleteReason reason) {
		this.stored.add(new CodefSyncIncompleteMarkEntity(syncId, USER, card, month, reason, 1));
	}

	private void row(long id, CodefLedgerRowKind kind, String date, long amount) {
		CodefApprovalRowEntity entity = new CodefApprovalRowEntity(USER, CARD, 41L, "SYN CARD", "0".repeat(64),
				LocalDate.parse(date), LocalTime.of(12, 0), amount, kind, LocalDateTime.of(2026, 11, 5, 9, 0));
		ReflectionTestUtils.setField(entity, "codefApprovalRowId", id);
		this.approvals.add(entity);
	}

	@Test
	void aSyncCoversUpToTheDayBeforeItRan() {
		// 10월 31일에 돌린 동기화는 10월 30일까지만 덮으므로 10월이 불완전, 11월 1일에 돌렸으면 완전
		sync(41, "2026-10-01", "2026-10-31", "2026-10-31T09:00:00");
		assertThat(this.query.coverage(USER, List.of(CARD), OCTOBER).cards().get(CARD).incompleteReasons())
			.containsExactly("PERIOD_NOT_COVERED");

		sync(42, "2026-10-01", "2026-10-31", "2026-11-01T09:00:00");
		CodefApprovalLedgerQuery.Coverage coverage = this.query.coverage(USER, List.of(CARD), OCTOBER)
			.cards()
			.get(CARD);
		assertThat(coverage.complete()).isTrue();
		assertThat(coverage.basisSyncId()).isEqualTo(42L);
	}

	@Test
	void partialSyncsJoinToCoverTheMonth() {
		sync(41, "2026-09-15", "2026-10-15", "2026-10-16T09:00:00");
		sync(42, "2026-10-16", "2026-11-03", "2026-11-04T09:00:00");
		assertThat(this.query.coverage(USER, List.of(CARD), OCTOBER).cards().get(CARD).complete()).isTrue();

		this.judged.clear();
		sync(41, "2026-09-15", "2026-10-15", "2026-10-16T09:00:00");
		sync(43, "2026-10-17", "2026-11-03", "2026-11-04T09:00:00");
		assertThat(this.query.coverage(USER, List.of(CARD), OCTOBER).cards().get(CARD).incompleteReasons())
			.containsExactly("PERIOD_NOT_COVERED");
	}

	@Test
	void olderMarksAreReplacedByTheLatestFullCoveringSync() {
		sync(41, "2026-10-01", "2026-11-03", "2026-11-04T09:00:00");
		mark(41, CARD, "2026-10", CodefIncompleteReason.PARTIAL_CANCELLATION_SUSPECTED);
		assertThat(this.query.coverage(USER, List.of(CARD), OCTOBER).cards().get(CARD).incompleteReasons())
			.containsExactly("PARTIAL_CANCELLATION_SUSPECTED");

		sync(42, "2026-10-01", "2026-11-10", "2026-11-11T09:00:00");
		assertThat(this.query.coverage(USER, List.of(CARD), OCTOBER).cards().get(CARD).complete()).isTrue();
	}

	@Test
	void marksCountOnlyForTheirCardAndMonthOrAnUnknownMonth() {
		sync(41, "2026-09-01", "2026-11-03", "2026-11-04T09:00:00");
		mark(41, CARD, "2026-09", CodefIncompleteReason.NO_MATCHING_APPROVAL);
		mark(41, 12L, "2026-10", CodefIncompleteReason.DUPLICATE_KEY_AMBIGUOUS);
		mark(41, null, "2026-10", CodefIncompleteReason.UNDETERMINED_ROW);
		CodefApprovalLedgerQuery.MonthCoverage coverage = this.query.coverage(USER, List.of(CARD, 99L), OCTOBER);
		// 다른 달·다른 카드·카드 미연결 표시는 이 카드를 불완전으로 만들지 않는다(사용자 결정 Q5). 수동 카드 99는 담지 않는다
		assertThat(coverage.cards()).containsOnlyKeys(CARD);
		assertThat(coverage.cards().get(CARD).complete()).isTrue();
		assertThat(coverage.unlinkedReasons()).containsExactly("UNDETERMINED_ROW");

		mark(41, CARD, null, CodefIncompleteReason.UNDETERMINED_ROW);
		mark(41, CARD, "2026-10", CodefIncompleteReason.MULTIPLE_MATCHING_APPROVALS);
		assertThat(this.query.coverage(USER, List.of(CARD), OCTOBER).cards().get(CARD).incompleteReasons())
			.containsExactly("MULTIPLE_MATCHING_APPROVALS", "UNDETERMINED_ROW");
	}

	@Test
	void estimateSumsOrdinaryApprovalsOfACompleteMonth() {
		sync(41, "2026-10-01", "2026-11-03", "2026-11-04T09:00:00");
		row(1, CodefLedgerRowKind.APPROVAL, "2026-10-02", 120000);
		row(2, CodefLedgerRowKind.APPROVAL, "2026-10-20", 392000);

		assertThat(this.query.estimate(USER, CARD, OCTOBER)).contains(new CodefApprovalLedgerQuery.Estimate(OCTOBER,
				512000L, List.of(), 41, LocalDateTime.of(2026, 11, 4, 9, 0)));
	}

	@Test
	void cancellationsOrIncompleteMonthsGiveNoTotal() {
		sync(41, "2026-10-01", "2026-11-03", "2026-11-04T09:00:00");
		row(1, CodefLedgerRowKind.APPROVAL, "2026-10-02", 120000);
		row(2, CodefLedgerRowKind.APPROVAL_FULLY_CANCELED, "2026-10-05", 30000);
		mark(41, CARD, "2026-10", CodefIncompleteReason.UNDETERMINED_ROW);
		// 카드 미연결 표시는 이 카드의 합계에 영향이 없다
		mark(41, null, "2026-10", CodefIncompleteReason.DUPLICATE_KEY_AMBIGUOUS);

		CodefApprovalLedgerQuery.Estimate estimate = this.query.estimate(USER, CARD, OCTOBER).orElseThrow();
		// 0으로 바꾸지 않고 null과 사유(동기화 판정 → 취소 접수월 미상 순)
		assertThat(estimate.totalWon()).isNull();
		assertThat(estimate.incompleteReasons()).containsExactly("UNDETERMINED_ROW",
				"CANCELLATION_ATTRIBUTION_UNCONFIRMED");
	}

	@Test
	void noEstimateForManualCardsOrWithoutAJudgedSync() {
		assertThat(this.query.estimate(USER, CARD, OCTOBER)).isEmpty();
		sync(41, "2026-10-01", "2026-11-03", "2026-11-04T09:00:00");
		assertThat(this.query.estimate(USER, 99L, OCTOBER)).isEmpty();
		// 행이 하나도 없는 완전한 달은 0원(실제로 승인이 없음)
		assertThat(this.query.estimate(USER, CARD, OCTOBER).orElseThrow().totalWon()).isZero();
	}

	@Test
	void considerDropsSyncsThatCoverNoDay() {
		// 조회일 당일만 조회한 동기화는 덮는 날이 없다
		CodefApprovalLedgerQuery.SyncSpan empty = new CodefApprovalLedgerQuery.SyncSpan(1, LocalDate.of(2026, 10, 5),
				LocalDate.of(2026, 10, 4), LocalDateTime.of(2026, 10, 5, 9, 0));
		assertThat(CodefApprovalLedgerQuery.consider(List.of(empty), OCTOBER)).isEmpty();
		assertThat(CodefApprovalLedgerQuery.covers(List.of(), OCTOBER)).isFalse();
	}

}
