package com.beone.api.codef.sync;

import static com.beone.api.codef.sync.SyntheticFixture.CHECKED_AT;
import static com.beone.api.codef.sync.SyntheticFixture.KB;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.beone.api.codef.CodefApproval;
import com.beone.api.codef.CodefCancellationForm;
import com.beone.api.codef.CodefCancellationForm.Reason;
import com.beone.api.codef.sync.CodefApprovalRowView.KrwAmountAvailability;

/**
 * 승인 행 하나의 형태 분류. 공식 시트 기준(승인내역!H24:H48·H42·B52, 참고 승인내역 취소건!I14·I15), 실계정 관측 없음. 기대값은 D-17과 시트 셀에서 구현 전에
 * 정했다. 짝짓기·중복 키·날짜 해석·카드 연결은 하지 않는다.
 */
class CodefApprovalRowViewTest {

	private static List<CodefApproval> rows() {
		CodefTargetOutcome<CodefApproval> outcome = CodefTargetOutcome
			.judge(SyntheticFixture.approvals("approvals-kb.json"), CHECKED_AT, row -> row);
		return ((CodefTargetOutcome.Usable<CodefApproval>) outcome).items();
	}

	private static CodefApprovalRowView kbRow(int index) {
		return CodefApprovalRowView.describe(KB, rows().get(index));
	}

	private static CodefCancellationForm undetermined(Reason reason) {
		return new CodefCancellationForm.Undetermined(reason);
	}

	@Test
	void normalRow() {
		CodefApprovalRowView view = kbRow(0);

		assertThat(view.form()).isEqualTo(new CodefCancellationForm.NormalRow());
		assertThat(view.krwAmount()).isEqualTo(KrwAmountAvailability.AVAILABLE);
		assertThat(view.approvalNumberPresent()).isTrue();
		assertThat(view.usedDate()).contains("20261015");
		assertThat(view.usedTime()).contains("120000");
	}

	@Test
	void type1FullCancellation() {
		// 취소건!I14: 이용 5000, 취소 1, 취소금액 5000.
		assertThat(kbRow(1).form()).isEqualTo(new CodefCancellationForm.Type1Canceled());
	}

	@Test
	void type1WithDifferentCancelAmountIsUndetermined() {
		// D-17: 금액이 다르면 부분취소일 수 있으므로 전액 취소로 보지 않는다.
		assertThat(kbRow(2).form()).isEqualTo(undetermined(Reason.SHAPE_MISMATCH));
	}

	@Test
	void type2CancelRow() {
		// 취소건!I15 취소건·승인내역!B52: 이용 -5000, 취소 1, 취소금액 빈 값. 짝짓기는 하지 않는다.
		assertThat(kbRow(3).form()).isEqualTo(new CodefCancellationForm.Type2CancelRow());
	}

	@Test
	void negativeAmountWithNormalStatusIsNotACancellation() {
		// 음수 금액만으로 취소라고 판단하지 않는다(D-17, #45).
		assertThat(kbRow(4).form()).isEqualTo(undetermined(Reason.SHAPE_MISMATCH));
	}

	@Test
	void partialCancellationIsNotDistinguishedForKb() {
		// 승인내역!H42 주석, 취소건!C7·D7 X.
		assertThat(kbRow(5).form()).isEqualTo(undetermined(Reason.PARTIAL_CANCEL_NOT_DISTINGUISHED));
	}

	@Test
	void rejectionIsNotDescribed() {
		assertThat(kbRow(6).form()).isEqualTo(undetermined(Reason.STATUS_NOT_DESCRIBED));
	}

	@Test
	void krwAmountNeedsKrwCurrencyDomesticAndParsedAmount() {
		// KB 원화금액은 -(승인내역!H46)이므로 환산하지 않는다. 통화 KRW·국내·금액 해석이 모두 맞을 때만 원화 금액을 만들 수 있다.
		assertThat(kbRow(7).krwAmount()).isEqualTo(KrwAmountAvailability.CURRENCY_ABSENT);
		assertThat(kbRow(8).krwAmount()).isEqualTo(KrwAmountAvailability.CURRENCY_NOT_KRW);
		assertThat(kbRow(9).krwAmount()).isEqualTo(KrwAmountAvailability.NOT_DOMESTIC);
		assertThat(kbRow(11).krwAmount()).isEqualTo(KrwAmountAvailability.USED_AMOUNT_NOT_PARSED);
	}

	@Test
	void missingApprovalNumberIsRecordedAsAbsent() {
		CodefApprovalRowView view = kbRow(10);

		assertThat(view.approvalNumberPresent()).isFalse();
		assertThat(view.form()).isEqualTo(new CodefCancellationForm.NormalRow());
	}

	@Test
	void unparsedAmountIsUndeterminedAndDatesStayRaw() {
		// 승인 일자·시각 형식은 문서에 없어(R4 미결) 원문 문자열 그대로 둔다.
		CodefApprovalRowView view = kbRow(11);

		assertThat(view.form()).isEqualTo(undetermined(Reason.AMOUNT_NOT_PARSED));
		assertThat(view.usedDate()).isEqualTo(Optional.of("2026-10-18"));
		assertThat(view.usedTime()).isEqualTo(Optional.of("11:01"));
	}

	@Test
	void otherOrganizationIsNotDocumented() {
		assertThat(CodefApprovalRowView.describe("0302", rows().get(0)).form())
			.isEqualTo(new CodefCancellationForm.OrganizationNotDocumented());
	}

	@Test
	void rawApprovalNumberAndCardNumberDoNotAppear() {
		String text = rows().stream().map(row -> CodefApprovalRowView.describe(KB, row)).toList().toString();

		assertThat(text).doesNotContain("SYN-APR-").doesNotContain("SYN-MASKED-0000");
	}

}
