package com.beone.api.codef;

import java.util.Objects;

import com.beone.api.codef.CodefCancellationForm.Reason;

/**
 * 이미 해석된 승인내역 한 건을 공급자 취소 표현 설명과 맞춰 본다(#45). 기존 해석 결과({@link CodefApproval})만 읽으며 값을 바꾸지 않는다.
 *
 * <p>
 * 취소여부 코드와 금액 모양을 함께 본다. 이용금액이 음수라는 사실만으로 취소나 type2로 판정하지 않는다. 기관은 KB(기관별 개발현황!C3
 * {@code 0301})만 정리했고, 다른 기관은 {@link CodefCancellationForm.OrganizationNotDocumented}다. 근거는 공식 명세 시트이며 실제 응답 관측은
 * 없다.
 */
public final class CodefCancellationFormDescriber {

	/** 기관별 개발현황!C3:D3의 KB카드 기관코드. 판정할 기관을 고르는 데만 쓰며 요청 파라미터가 아니다. */
	private static final String KB_ORGANIZATION = "0301";

	private CodefCancellationFormDescriber() {
	}

	/**
	 * 승인내역 한 건이 어느 취소 표현 형태와 맞는지 기술한다.
	 *
	 * @param organizationCode 응답을 받은 기관의 코드. 정확히 일치해야 하며 공백·자릿수를 고치지 않는다
	 * @param approval 해석된 승인내역 한 건
	 * @return 형태 기술. 금액은 담지 않는다
	 * @throws NullPointerException 입력이 null인 경우
	 */
	public static CodefCancellationForm describe(String organizationCode, CodefApproval approval) {
		Objects.requireNonNull(organizationCode, "organizationCode");
		Objects.requireNonNull(approval, "approval");
		if (!KB_ORGANIZATION.equals(organizationCode)) {
			return new CodefCancellationForm.OrganizationNotDocumented();
		}
		return describeKb(approval);
	}

	private static CodefCancellationForm describeKb(CodefApproval approval) {
		CodefCancellationForm byStatus = switch (approval.cancelStatus()) {
			case UNKNOWN -> undetermined(Reason.CANCEL_STATUS_UNKNOWN);
			case PARTIALLY_CANCELED -> undetermined(Reason.PARTIAL_CANCEL_NOT_DISTINGUISHED);
			case REJECTED -> undetermined(Reason.STATUS_NOT_DESCRIBED);
			case NORMAL, CANCELED -> null;
		};
		if (byStatus != null) {
			return byStatus;
		}
		if (!(approval.usedAmount() instanceof CodefAmount.Parsed(long used))
				|| approval.cancelAmount() instanceof CodefAmount.Unparsed) {
			return undetermined(Reason.AMOUNT_NOT_PARSED);
		}
		boolean cancelAmountAbsent = approval.cancelAmount() instanceof CodefAmount.Absent;
		if (approval.cancelStatus() == CodefApproval.CancelStatus.NORMAL) {
			// 정상 행: 양수 이용금액과 빈 취소금액(취소건!I15 승인건).
			return used > 0 && cancelAmountAbsent ? new CodefCancellationForm.NormalRow()
					: undetermined(Reason.SHAPE_MISMATCH);
		}
		// 취소 "1": type1은 양수 이용금액과 같은 취소금액, type2 취소 행은 음수 이용금액과 빈 취소금액.
		if (used > 0 && approval.cancelAmount() instanceof CodefAmount.Parsed(long canceled) && canceled == used) {
			return new CodefCancellationForm.Type1Canceled();
		}
		if (used < 0 && cancelAmountAbsent) {
			return new CodefCancellationForm.Type2CancelRow();
		}
		return undetermined(Reason.SHAPE_MISMATCH);
	}

	private static CodefCancellationForm undetermined(Reason reason) {
		return new CodefCancellationForm.Undetermined(reason);
	}

}
