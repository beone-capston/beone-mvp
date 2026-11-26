package com.beone.api.codef.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

import com.beone.api.codef.sync.CodefLedgerCandidateRow.Decision;

/**
 * CODEF 원장 후보 판정(D-17 짝짓기, 결정 R2 중복 키·재조회 대조). 기대값은 D-17과 결정 C 문장에서 구현 전에 정했다. 공식 시트 기준 합성 응답, 실계정 관측 없음.
 * 지문은 테스트 안의 합성 키로만 만든다.
 */
class CodefLedgerCandidateBuilderTest {

	private static Decision incomplete(CodefIncompleteReason reason) {
		return new Decision.Incomplete(reason);
	}

	private static Decision.Accepted accepted(CodefLedgerCandidates candidates, int position) {
		assertThat(candidates.row(position).decision()).as("row %d", position).isInstanceOf(Decision.Accepted.class);
		return (Decision.Accepted) candidates.row(position).decision();
	}

	private static CodefApprovalFingerprint fingerprint(String approvalNumber) {
		return SyntheticHmacFingerprinter.KEY_ONE.fingerprint(approvalNumber);
	}

	@Test
	void type2WithSharedApprovalNumberPairsWithTheSingleNormalRow() {
		CodefLedgerCandidates candidates = LedgerFixture.build("ledger-approvals-pairing.json");

		Decision.Accepted approval = accepted(candidates, 0);
		assertThat(approval.kind()).isEqualTo(CodefLedgerRowKind.APPROVAL);
		assertThat(approval.cancellationAttribution()).isEmpty();
		Decision.Accepted cancel = accepted(candidates, 1);
		assertThat(cancel.kind()).isEqualTo(CodefLedgerRowKind.CANCELLATION);
		assertThat(cancel.pairedApprovalPosition()).isEqualTo(OptionalInt.of(0));
		// 응답에 취소 접수일이 없으므로 귀속월은 확인 필요다(D-17).
		assertThat(cancel.cancellationAttribution()).contains(CodefAttributionMonth.NEEDS_CONFIRMATION);
		assertThat(cancel.key()).isEqualTo(new CodefApprovalKey("SYN CARD A", fingerprint("SYN-APR-P01"),
				LocalDate.of(2026, 10, 12), LocalTime.of(9, 0), -10000));
		assertThat(cancel.recheck()).isEqualTo(CodefRecheck.NEW);
	}

	@Test
	void type2WithoutSharedApprovalNumberHasNoMatchingApproval() {
		CodefLedgerCandidates candidates = LedgerFixture.build("ledger-approvals-pairing.json");

		// 승인번호를 공유하지 않으면 짝이 없다. 금액·날짜로 대신 짝짓지 않는다(D-17 공유 여부 확인 필요).
		assertThat(accepted(candidates, 2).kind()).isEqualTo(CodefLedgerRowKind.APPROVAL);
		assertThat(candidates.row(3).decision()).isEqualTo(incomplete(CodefIncompleteReason.NO_MATCHING_APPROVAL));
	}

	@Test
	void type2WithTwoMatchingNormalRowsIsNotPaired() {
		CodefLedgerCandidates candidates = LedgerFixture.build("ledger-approvals-pairing.json");

		assertThat(accepted(candidates, 4).kind()).isEqualTo(CodefLedgerRowKind.APPROVAL);
		assertThat(accepted(candidates, 5).kind()).isEqualTo(CodefLedgerRowKind.APPROVAL);
		assertThat(candidates.row(6).decision())
			.isEqualTo(incomplete(CodefIncompleteReason.MULTIPLE_MATCHING_APPROVALS));
	}

	@Test
	void type2SmallerThanTheApprovalIsSuspectedPartial() {
		CodefLedgerCandidates candidates = LedgerFixture.build("ledger-approvals-pairing.json");

		assertThat(candidates.row(8).decision())
			.isEqualTo(incomplete(CodefIncompleteReason.PARTIAL_CANCELLATION_SUSPECTED));
	}

	@Test
	void type2WhoseApprovalIsOutsideTheQueryPeriodIsNotWidened() {
		CodefLedgerCandidates candidates = LedgerFixture.build("ledger-approvals-pairing.json");

		// 원 승인 행이 조회 기간 밖이면 기간을 넓히지 않고 그 취소 행만 불완전이다.
		assertThat(candidates.row(9).decision()).isEqualTo(incomplete(CodefIncompleteReason.NO_MATCHING_APPROVAL));
		assertThat(candidates.incompleteMarks()).filteredOn(mark -> mark.position() == 9)
			.singleElement()
			.isEqualTo(new CodefIncompleteMark("SYN CARD A", Optional.of(LocalDate.of(2026, 10, 1)),
					CodefIncompleteReason.NO_MATCHING_APPROVAL, 9));
	}

	@Test
	void type2OnAnotherCardDoesNotPair() {
		CodefLedgerCandidates candidates = LedgerFixture.build("ledger-approvals-pairing.json");

		assertThat(accepted(candidates, 14).kind()).isEqualTo(CodefLedgerRowKind.APPROVAL);
		assertThat(candidates.row(15).decision()).isEqualTo(incomplete(CodefIncompleteReason.NO_MATCHING_APPROVAL));
	}

	@Test
	void type2LargerThanTheApprovalIsUndetermined() {
		CodefLedgerCandidates candidates = LedgerFixture.build("ledger-approvals-pairing.json");

		assertThat(candidates.row(17).decision()).isEqualTo(incomplete(CodefIncompleteReason.UNDETERMINED_ROW));
	}

	@Test
	void type1FullCancellationOnlyWhenAmountsMatch() {
		CodefLedgerCandidates candidates = LedgerFixture.build("ledger-approvals-pairing.json");

		Decision.Accepted type1 = accepted(candidates, 10);
		assertThat(type1.kind()).isEqualTo(CodefLedgerRowKind.APPROVAL_FULLY_CANCELED);
		assertThat(type1.pairedApprovalPosition()).isEmpty();
		assertThat(type1.cancellationAttribution()).contains(CodefAttributionMonth.NEEDS_CONFIRMATION);
		// 취소금액이 이용금액과 다르면 부분취소일 수 있다(D-17).
		assertThat(candidates.row(11).decision())
			.isEqualTo(incomplete(CodefIncompleteReason.PARTIAL_CANCELLATION_SUSPECTED));
		// 부분취소 코드 "2".
		assertThat(candidates.row(12).decision())
			.isEqualTo(incomplete(CodefIncompleteReason.PARTIAL_CANCELLATION_SUSPECTED));
	}

	@Test
	void undeterminedFormsCurrencyAndNegativeNormalRows() {
		CodefLedgerCandidates candidates = LedgerFixture.build("ledger-approvals-pairing.json");

		// 거절 "3", 외화, 음수 금액의 정상 코드(음수만으로 취소라고 보지 않음).
		for (int position : new int[] { 13, 18, 19 }) {
			assertThat(candidates.row(position).decision()).as("row %d", position)
				.isEqualTo(incomplete(CodefIncompleteReason.UNDETERMINED_ROW));
		}
	}

	@Test
	void incompleteMarksStayOnTheLinkedCardAndRowDate() {
		CodefLedgerCandidates candidates = LedgerFixture.build("ledger-approvals-pairing.json");

		assertThat(candidates.incompleteMarks()).extracting(CodefIncompleteMark::position)
			.containsExactly(3, 6, 8, 9, 11, 12, 13, 15, 17, 18, 19);
		assertThat(candidates.incompleteMarks()).filteredOn(mark -> mark.position() == 15)
			.singleElement()
			.satisfies(mark -> {
				assertThat(mark.cardName()).isEqualTo("SYN CARD C|SYN ISSUER");
				assertThat(mark.usedDate()).contains(LocalDate.of(2026, 10, 16));
			});
		assertThat(candidates.incompleteMarks()).filteredOn(mark -> mark.position() != 15)
			.allSatisfy(mark -> assertThat(mark.cardName()).isEqualTo("SYN CARD A"));
	}

	@Test
	void sameKeyTwiceInOneResponseIsAmbiguous() {
		CodefLedgerCandidates candidates = LedgerFixture.build("ledger-approvals-duplicate.json");
		Decision ambiguous = incomplete(CodefIncompleteReason.DUPLICATE_KEY_AMBIGUOUS);

		assertThat(candidates.row(0).decision()).isEqualTo(ambiguous);
		assertThat(candidates.row(1).decision()).isEqualTo(ambiguous);
		// 승인번호 없음, 사용일자 없음.
		assertThat(candidates.row(2).decision()).isEqualTo(ambiguous);
		assertThat(candidates.row(3).decision()).isEqualTo(ambiguous);
		assertThat(accepted(candidates, 4).kind()).isEqualTo(CodefLedgerRowKind.APPROVAL);
		// 취소여부는 키에 넣지 않으므로 정상 행과 Type1 행이 같은 키다.
		assertThat(candidates.row(5).decision()).isEqualTo(ambiguous);
		assertThat(candidates.row(6).decision()).isEqualTo(ambiguous);
	}

	@Test
	void recheckDetectsNewRecordedCanceledAndContradiction() {
		CodefLedgerCandidates first = LedgerFixture.build("ledger-recheck-first.json");

		assertThat(first.rows()).allSatisfy(row -> assertThat(((Decision.Accepted) row.decision()).recheck())
			.isEqualTo(CodefRecheck.NEW));
		assertThat(first.acceptedKinds()).hasSize(4);

		CodefLedgerCandidates second = LedgerFixture.build("ledger-recheck-second.json", first.acceptedKinds());

		Decision.Accepted canceled = accepted(second, 0);
		assertThat(canceled.kind()).isEqualTo(CodefLedgerRowKind.APPROVAL_FULLY_CANCELED);
		assertThat(canceled.recheck()).isEqualTo(CodefRecheck.CANCELED_SINCE_RECORDED);
		assertThat(canceled.key()).isEqualTo(((Decision.Accepted) first.row(0).decision()).key());
		assertThat(accepted(second, 1).recheck()).isEqualTo(CodefRecheck.ALREADY_RECORDED);
		// Type1로 기록된 키가 정상으로 돌아오면 모순이므로 넣지 않는다.
		assertThat(second.row(2).decision()).isEqualTo(incomplete(CodefIncompleteReason.UNDETERMINED_ROW));
		// 정상 → 취소금액 불일치는 형태 불일치 변화로 불완전이다.
		assertThat(second.row(3).decision())
			.isEqualTo(incomplete(CodefIncompleteReason.PARTIAL_CANCELLATION_SUSPECTED));
		assertThat(accepted(second, 4).recheck()).isEqualTo(CodefRecheck.NEW);
	}

	@Test
	void reloadingTheSameResponseCreatesNoNewRows() {
		CodefLedgerCandidates first = LedgerFixture.build("ledger-approvals-pairing.json");
		CodefLedgerCandidates again = LedgerFixture.build("ledger-approvals-pairing.json", first.acceptedKinds());

		assertThat(again.acceptedKinds()).isEqualTo(first.acceptedKinds());
		assertThat(again.rows()).filteredOn(row -> row.decision() instanceof Decision.Accepted)
			.isNotEmpty()
			.allSatisfy(row -> assertThat(((Decision.Accepted) row.decision()).recheck())
				.isEqualTo(CodefRecheck.ALREADY_RECORDED));
	}

	@Test
	void organizationOutsideKbIsUndetermined() {
		CodefLedgerCandidates candidates = CodefLedgerCandidateBuilder.build("0309", LedgerFixture.linker(),
				LedgerFixture.approvals("ledger-approvals-pairing.json"), SyntheticHmacFingerprinter.KEY_ONE, Map.of());

		assertThat(candidates.rows()).allSatisfy(row -> assertThat(row.decision())
			.isEqualTo(incomplete(CodefIncompleteReason.UNDETERMINED_ROW)));
	}

	@Test
	void incompleteDetailsNameTheActualBranchWhilePublicReasonsStay() {
		// 공개 사유는 위 시험들 그대로이고, 내부 진단 분기만 나눠 센다(#270)
		CodefLedgerCandidates pairing = LedgerFixture.build("ledger-approvals-pairing.json");
		Map<Integer, CodefIncompleteDetail> expected = Map.of(3, CodefIncompleteDetail.NO_MATCHING_APPROVAL, 6,
				CodefIncompleteDetail.MULTIPLE_MATCHING_APPROVALS, 8, CodefIncompleteDetail.CANCEL_SMALLER_THAN_APPROVAL,
				11, CodefIncompleteDetail.TYPE1_CANCEL_AMOUNT_MISMATCH, 12, CodefIncompleteDetail.PARTIAL_CANCEL_CODE, 13,
				CodefIncompleteDetail.CANCEL_STATUS_NOT_DESCRIBED, 17, CodefIncompleteDetail.CANCEL_LARGER_THAN_APPROVAL,
				18, CodefIncompleteDetail.KRW_CURRENCY_NOT_KRW, 19, CodefIncompleteDetail.FORM_SHAPE_MISMATCH);
		expected.forEach((position, detail) -> assertThat(pairing.row(position).incompleteDetail())
			.as("row %d", position)
			.contains(detail));
		assertThat(pairing.row(2).incompleteDetail()).isEmpty();

		CodefLedgerCandidates duplicate = LedgerFixture.build("ledger-approvals-duplicate.json");
		assertThat(duplicate.row(0).incompleteDetail()).contains(CodefIncompleteDetail.DUPLICATE_KEY_IN_RESPONSE);
		assertThat(duplicate.row(2).incompleteDetail()).contains(CodefIncompleteDetail.APPROVAL_NO_ABSENT);
		assertThat(duplicate.row(3).incompleteDetail()).contains(CodefIncompleteDetail.USED_DATE_ABSENT);

		CodefLedgerCandidates first = LedgerFixture.build("ledger-recheck-first.json");
		assertThat(LedgerFixture.build("ledger-recheck-second.json", first.acceptedKinds()).row(2).incompleteDetail())
			.contains(CodefIncompleteDetail.RECHECK_CONTRADICTION);
		assertThat(LedgerFixture.build("ledger-approvals-dates.json").row(4).incompleteDetail())
			.contains(CodefIncompleteDetail.USED_DATE_TIME_NOT_PARSED);
		assertThat(LedgerFixture.build("ledger-approvals-link.json").row(2).incompleteDetail())
			.contains(CodefIncompleteDetail.CARD_NOT_LINKED);
		assertThat(CodefLedgerCandidateBuilder
			.build("0309", LedgerFixture.linker(), LedgerFixture.approvals("ledger-approvals-pairing.json"),
					SyntheticHmacFingerprinter.KEY_ONE, Map.of())
			.row(0)
			.incompleteDetail()).contains(CodefIncompleteDetail.ORGANIZATION_NOT_DOCUMENTED);
	}

	@Test
	void sameInputGivesTheSameResult() {
		assertThat(LedgerFixture.build("ledger-approvals-pairing.json"))
			.isEqualTo(LedgerFixture.build("ledger-approvals-pairing.json"));
	}

}
