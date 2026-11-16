package com.beone.api.codef.sync;

import java.util.Objects;
import java.util.Optional;

import com.beone.api.codef.CodefAmount;
import com.beone.api.codef.CodefApproval;
import com.beone.api.codef.CodefCancellationForm;
import com.beone.api.codef.CodefCancellationFormDescriber;

/**
 * 승인내역 한 행의 형태 분류. 기존 취소 표현 기술({@link CodefCancellationFormDescriber}) 결과, 원화 금액을 만들 수 있는지, 승인번호가 있는지만 담는다.
 *
 * <p>
 * 원문 승인번호는 담지 않는다(관리 결정: 승인번호는 원문 대신 HMAC로 비교). 사용일자·사용일시는 형식이 문서에 없어 원문 문자열 그대로 두고 해석하지 않는다(R4
 * 미결). 이 행을 어느 보유카드에 붙일지(R1), 취소 행과 승인 행의 짝짓기·재조회 중복 키(R2), 원장 반영(R8)은 하지 않는다. 금액도 담지 않는다.
 *
 * @param usedDate 사용일자 원문
 * @param usedTime 사용일시 원문
 * @param form 취소 표현 형태. KB({@code 0301}) 밖의 기관은 근거 미정리
 * @param krwAmount 원화 금액을 만들 수 있는지
 * @param approvalNumberPresent 승인번호가 있는지. 값 자체는 담지 않는다
 */
public record CodefApprovalRowView(Optional<String> usedDate, Optional<String> usedTime, CodefCancellationForm form,
		KrwAmountAvailability krwAmount, boolean approvalNumberPresent) {

	private static final String KRW = "KRW";

	/**
	 * null을 거부한다.
	 *
	 * @throws NullPointerException 값이 null인 경우
	 */
	public CodefApprovalRowView {
		Objects.requireNonNull(usedDate, "usedDate");
		Objects.requireNonNull(usedTime, "usedTime");
		Objects.requireNonNull(form, "form");
		Objects.requireNonNull(krwAmount, "krwAmount");
	}

	/**
	 * 해석된 승인내역 한 행을 분류한다.
	 *
	 * @param organizationCode 응답을 받은 기관의 코드. 정확히 일치해야 하며 고치지 않는다
	 * @param approval 해석된 승인내역 한 행
	 * @return 형태 분류
	 * @throws NullPointerException 입력이 null인 경우
	 */
	public static CodefApprovalRowView describe(String organizationCode, CodefApproval approval) {
		Objects.requireNonNull(organizationCode, "organizationCode");
		Objects.requireNonNull(approval, "approval");
		return new CodefApprovalRowView(approval.usedDate(), approval.usedTime(),
				CodefCancellationFormDescriber.describe(organizationCode, approval), krwAvailability(approval),
				approval.approvalNo().isPresent());
	}

	private static KrwAmountAvailability krwAvailability(CodefApproval approval) {
		if (approval.currency().isEmpty()) {
			return KrwAmountAvailability.CURRENCY_ABSENT;
		}
		if (!KRW.equals(approval.currency().get())) {
			return KrwAmountAvailability.CURRENCY_NOT_KRW;
		}
		if (approval.domesticForeign() != CodefApproval.DomesticForeign.DOMESTIC) {
			return KrwAmountAvailability.NOT_DOMESTIC;
		}
		if (!(approval.usedAmount() instanceof CodefAmount.Parsed)) {
			return KrwAmountAvailability.USED_AMOUNT_NOT_PARSED;
		}
		return KrwAmountAvailability.AVAILABLE;
	}

	/**
	 * 이용금액을 원화 금액으로 볼 수 있는지. KB는 원화금액({@code resKRWAmt})을 제공하지 않으므로(승인내역!H46) 환산하지 않고, 제공된 값만 쓴다(D-04).
	 * 아래 순서대로 처음 걸린 이유를 돌려준다.
	 */
	public enum KrwAmountAvailability {

		/** 통화가 정확히 {@code KRW}이고 국내 거래이며 이용금액이 정수로 해석됐다. */
		AVAILABLE,

		/** 통화코드가 비어 있다. */
		CURRENCY_ABSENT,

		/** 통화코드가 {@code KRW}가 아니다. 대소문자·공백을 고치지 않는다. */
		CURRENCY_NOT_KRW,

		/** 국내/외 구분이 국내({@code "1"})가 아니다(해외 또는 값 없음). */
		NOT_DOMESTIC,

		/** 이용금액이 없거나 정수로 해석되지 않았다. */
		USED_AMOUNT_NOT_PARSED

	}

}
