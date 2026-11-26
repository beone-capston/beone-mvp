package com.beone.api.monthlystate.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.beone.api.codef.application.CodefPerformanceReferenceQuery;
import com.beone.api.monthlystate.application.DatePeriod;
import com.beone.api.monthlystate.application.MonthlyStateView;
import com.beone.api.monthlystate.application.ServiceSelectionStatus;
import com.beone.api.monthlystate.application.StateReason;
import com.beone.api.monthlystate.application.StateSource;
import com.beone.api.monthlystate.application.StateValue;

/**
 * 월간 상태 조회 응답(docs/api/monthly-states.md). 입력 응답은 {@link Saved}이며 {@code afterSnapshot}이 없다는 점만 다르다. 시점은
 * {@code +09:00}이 붙은 서울 시각 문자열이다.
 *
 * <p>전월 실적에는 미리 채워 보여 줄 카드사 조회 실적(참고) {@code codefReference}와, 입력할 때 참고한 CODEF 값 {@code referencedCodef}가
 * 붙는다(D-16, #281). {@code conflicts}는 같은 기준 기간의 참고값과 전월 실적 값이 다를 때만 항목이 있다.
 *
 * @param userCardId 보유 카드 ID
 * @param month {@code YYYY-MM}
 * @param snapshotAt 수동 입력 시각. 없으면 {@code null}
 * @param previousMonthPerformanceWon 전월 실적
 * @param limitBucketUsages 한도 묶음 사용액
 * @param otherBucketsUsedWon 나머지 한도 묶음 사용액
 * @param selectedServices 고른 택1 서비스
 * @param serviceSelectionStatus 택1 입력 상태
 * @param noServiceSelection 선택 없음
 * @param registeredOn 카드 사용 등록일
 * @param conflicts 출처 충돌. 없으면 빈 목록
 * @param afterSnapshot 스냅샷 뒤 모의 승인 요약. 입력이 없으면 {@code null}
 */
public record MonthlyStateResponse(String userCardId, String month, String snapshotAt,
		PreviousMonthState previousMonthPerformanceWon, List<BucketUsageState> limitBucketUsages,
		State otherBucketsUsedWon, List<SelectionState> selectedServices,
		ServiceSelectionStatus serviceSelectionStatus, NoSelectionState noServiceSelection, State registeredOn,
		List<ConflictState> conflicts, AfterSnapshot afterSnapshot) {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private static final DateTimeFormatter INSTANT = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssXXX");

	static MonthlyStateResponse from(MonthlyStateView view) {
		return new MonthlyStateResponse(view.userCardId(), view.month().toString(), instant(view.snapshotAt()),
				PreviousMonthState.from(view.previousMonthPerformanceWon(), view.referencedCodef(), view.codefReference()),
				view.limitBucketUsages().stream().map(BucketUsageState::from).toList(),
				State.from(view.otherBucketsUsedWon()),
				view.selectedServices().stream().map(SelectionState::from).toList(), view.serviceSelectionStatus(),
				NoSelectionState.from(view.noServiceSelection()), State.from(view.registeredOn()),
				view.conflicts().stream().map(ConflictState::from).toList(),
				AfterSnapshot.from(view.afterSnapshot()));
	}

	private static String instant(LocalDateTime seoulTime) {
		return (seoulTime == null) ? null : INSTANT.format(seoulTime.atZone(SEOUL));
	}

	/**
	 * 입력 응답. 조회 응답에서 {@code afterSnapshot}만 뺀 모양이다.
	 *
	 * @param userCardId 보유 카드 ID
	 * @param month {@code YYYY-MM}
	 * @param snapshotAt 수동 입력 시각(저장 시각)
	 * @param previousMonthPerformanceWon 전월 실적
	 * @param limitBucketUsages 한도 묶음 사용액
	 * @param otherBucketsUsedWon 나머지 한도 묶음 사용액
	 * @param selectedServices 고른 택1 서비스
	 * @param serviceSelectionStatus 택1 입력 상태
	 * @param noServiceSelection 선택 없음
	 * @param registeredOn 카드 사용 등록일
	 * @param conflicts 출처 충돌. 없으면 빈 목록
	 */
	public record Saved(String userCardId, String month, String snapshotAt,
			PreviousMonthState previousMonthPerformanceWon, List<BucketUsageState> limitBucketUsages,
			State otherBucketsUsedWon, List<SelectionState> selectedServices,
			ServiceSelectionStatus serviceSelectionStatus, NoSelectionState noServiceSelection, State registeredOn,
			List<ConflictState> conflicts) {

		static Saved from(MonthlyStateView view) {
			MonthlyStateResponse r = MonthlyStateResponse.from(view);
			return new Saved(r.userCardId(), r.month(), r.snapshotAt(), r.previousMonthPerformanceWon(),
					r.limitBucketUsages(), r.otherBucketsUsedWon(), r.selectedServices(), r.serviceSelectionStatus(),
					r.noServiceSelection(), r.registeredOn(), r.conflicts());
		}

	}

	/**
	 * 기준 기간.
	 *
	 * @param from {@code YYYY-MM-DD}
	 * @param to {@code YYYY-MM-DD}
	 */
	record Period(LocalDate from, LocalDate to) {

		static Period from(DatePeriod period) {
			return (period == null) ? null : new Period(period.from(), period.to());
		}

	}

	/**
	 * 상태값 하나.
	 */
	record State(Object value, StateSource source, Period period, String updatedAt, boolean usable,
			StateReason reason) {

		static State from(StateValue<?> state) {
			return new State(state.value(), state.source(), Period.from(state.period()), instant(state.updatedAt()),
					state.usable(), state.reason());
		}

	}

	/**
	 * 전월 실적. {@code referencedCodef}는 참고 입력일 때만, {@code codefReference}는 전월 기간의 연결 참고값이 있을 때만 있다.
	 */
	record PreviousMonthState(Long value, StateSource source, Period period, String updatedAt, boolean usable,
			StateReason reason, ReferencedCodefState referencedCodef, CodefReferenceState codefReference) {

		static PreviousMonthState from(StateValue<Long> state, MonthlyStateView.ReferencedCodef referenced,
				MonthlyStateView.CodefReference reference) {
			return new PreviousMonthState(state.value(), state.source(), Period.from(state.period()),
					instant(state.updatedAt()), state.usable(), state.reason(), ReferencedCodefState.from(referenced),
					CodefReferenceState.from(reference));
		}

	}

	/**
	 * 입력할 때 참고한 CODEF 값(입력 당시 스냅샷). 조회월은 반영기간 시작일의 달이다.
	 */
	record ReferencedCodefState(String referenceId, String syncId, String inquiryMonth, Period period,
			Long currentUseWon, String fetchedAt) {

		static ReferencedCodefState from(MonthlyStateView.ReferencedCodef referenced) {
			return (referenced == null) ? null
					: new ReferencedCodefState(
							(referenced.referenceId() == null) ? null
									: CodefPerformanceReferenceQuery.ReferenceId.format(referenced.referenceId()),
							"sync-" + referenced.syncId(), YearMonth.from(referenced.period().from()).toString(),
							Period.from(referenced.period()), referenced.currentUseWon(),
							instant(referenced.fetchedAt()));
		}

	}

	/**
	 * 미리 채워 보여 줄 카드사 조회 실적(참고). {@code usedInCalculation}은 항상 {@code false}다(D-16).
	 */
	record CodefReferenceState(String referenceId, String label, String inquiryMonth, Period period,
			Long currentUseWon, String fetchedAt, boolean usedInCalculation) {

		static CodefReferenceState from(MonthlyStateView.CodefReference reference) {
			return (reference == null) ? null
					: new CodefReferenceState(CodefPerformanceReferenceQuery.ReferenceId.format(reference.referenceId()),
							"카드사 조회 실적(참고)", YearMonth.from(reference.period().from()).toString(),
							Period.from(reference.period()), reference.currentUseWon(), instant(reference.fetchedAt()),
							false);
		}

	}

	/**
	 * 출처 충돌 하나. 계산 기준 선택({@code source-selections})은 아직 저장하지 않으므로 {@code selection}은 항상 {@code null}이다.
	 */
	record ConflictState(String item, Period period, String conflictVersion, List<CandidateState> candidates,
			Object selection) {

		static ConflictState from(MonthlyStateView.Conflict conflict) {
			return new ConflictState(conflict.item(), Period.from(conflict.period()), conflict.conflictVersion(),
					conflict.candidates().stream().map(CandidateState::from).toList(), null);
		}

	}

	/**
	 * 충돌 후보 값 하나.
	 */
	record CandidateState(StateSource source, Long value, Period period, String updatedAt) {

		static CandidateState from(MonthlyStateView.Candidate candidate) {
			return new CandidateState(candidate.source(), candidate.value(), Period.from(candidate.period()),
					instant(candidate.updatedAt()));
		}

	}

	/**
	 * 한도 묶음 사용액.
	 */
	record BucketUsageState(String limitBucketId, Long value, StateSource source, Period period, String updatedAt,
			boolean usable, StateReason reason) {

		static BucketUsageState from(MonthlyStateView.BucketUsage usage) {
			StateValue<Long> state = usage.usage();
			return new BucketUsageState(usage.limitBucketId(), state.value(), state.source(),
					Period.from(state.period()), instant(state.updatedAt()), state.usable(), state.reason());
		}

	}

	/**
	 * 고른 택1 서비스.
	 */
	record SelectionState(String serviceId, LocalDate effectiveFrom, StateSource source, String updatedAt,
			boolean usable, StateReason reason) {

		static SelectionState from(MonthlyStateView.Selection selection) {
			return new SelectionState(selection.serviceId(), selection.effectiveFrom(), StateSource.MANUAL,
					instant(selection.updatedAt()), selection.usable(), selection.reason());
		}

	}

	/**
	 * 선택 없음.
	 */
	record NoSelectionState(LocalDate effectiveFrom, StateSource source, String updatedAt, boolean usable,
			StateReason reason) {

		static NoSelectionState from(MonthlyStateView.NoSelection noSelection) {
			return (noSelection == null) ? null
					: new NoSelectionState(noSelection.effectiveFrom(), StateSource.MANUAL,
							instant(noSelection.updatedAt()), noSelection.usable(), noSelection.reason());
		}

	}

	/**
	 * 스냅샷 뒤 모의 승인 요약.
	 */
	record AfterSnapshot(int approvalCount, long approvedAmountWon, int cancellationCount) {

		static AfterSnapshot from(MonthlyStateView.AfterSnapshot summary) {
			return (summary == null) ? null
					: new AfterSnapshot(summary.approvalCount(), summary.approvedAmountWon(),
							summary.cancellationCount());
		}

	}

}
