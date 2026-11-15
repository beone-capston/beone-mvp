package com.beone.api.codef;

import java.util.Objects;
import java.util.Optional;

import tools.jackson.databind.JsonNode;

/**
 * 승인내역 API({@code /v1/kr/card/p/account/approval-list}, 문서 갱신일 2026-10-21) 출력 항목 중 BeONE 계산에 필요한 비민감 필드.
 *
 * <p>
 * 거래 시점·금액·업종·결제 방식·국내외·취소 상태와 취소 대응에 쓰는 승인번호만 담는다. 카드번호({@code resCardNo}·{@code resCardNo1}),
 * 가맹점 사업자번호·전화번호·주소, 세션({@code commSession})은 옮기지 않는다. 가맹점번호·부가세·봉사료·할부개월·결제예정일·조회 기간
 * 필드는 현재 계산에 쓰지 않아 옮기지 않는다. 날짜·시각은 형식이 문서에 없어 문자열 그대로 둔다.
 *
 * @param usedDate 사용일자({@code resUsedDate})
 * @param usedTime 사용일시({@code resUsedTime})
 * @param cardName 카드명({@code resCardName}). 실제 상품 식별에 쓰지 않는다(D-01)
 * @param merchantName 가맹점명({@code resMemberStoreName})
 * @param merchantType 가맹점 업종({@code resMemberStoreType}). 가맹점 정보 포함 조회에서만 제공
 * @param usedAmount 이용금액({@code resUsedAmount})
 * @param krwAmount 원화금액({@code resKRWAmt})
 * @param currency 통화코드({@code resAccountCurrency}, ISO 4217)
 * @param paymentType 결제방법({@code resPaymentType})
 * @param domesticForeign 국내/외 구분({@code resHomeForeignType})
 * @param cancelStatus 취소여부({@code resCancelYN})
 * @param cancelAmount 취소금액({@code resCancelAmount}). 취소건에만 제공
 * @param approvalNo 승인번호({@code resApprovalNo})
 */
public record CodefApproval(Optional<String> usedDate, Optional<String> usedTime, Optional<String> cardName,
		Optional<String> merchantName, Optional<String> merchantType, CodefAmount usedAmount, CodefAmount krwAmount,
		Optional<String> currency, PaymentType paymentType, DomesticForeign domesticForeign, CancelStatus cancelStatus,
		CodefAmount cancelAmount, Optional<String> approvalNo) {

	/**
	 * null을 거부한다. 값 없음은 빈 {@code Optional}·{@code Absent}·{@code UNKNOWN}으로 표현한다.
	 *
	 * @throws NullPointerException 값이 null인 경우
	 */
	public CodefApproval {
		Objects.requireNonNull(usedDate, "usedDate");
		Objects.requireNonNull(usedTime, "usedTime");
		Objects.requireNonNull(cardName, "cardName");
		Objects.requireNonNull(merchantName, "merchantName");
		Objects.requireNonNull(merchantType, "merchantType");
		Objects.requireNonNull(usedAmount, "usedAmount");
		Objects.requireNonNull(krwAmount, "krwAmount");
		Objects.requireNonNull(currency, "currency");
		Objects.requireNonNull(paymentType, "paymentType");
		Objects.requireNonNull(domesticForeign, "domesticForeign");
		Objects.requireNonNull(cancelStatus, "cancelStatus");
		Objects.requireNonNull(cancelAmount, "cancelAmount");
		Objects.requireNonNull(approvalNo, "approvalNo");
	}

	static CodefApproval read(JsonNode item) {
		return new CodefApproval(CodefFields.text(item, "resUsedDate"), CodefFields.text(item, "resUsedTime"),
				CodefFields.text(item, "resCardName"), CodefFields.text(item, "resMemberStoreName"),
				CodefFields.text(item, "resMemberStoreType"), CodefFields.amount(item, "resUsedAmount"),
				CodefFields.amount(item, "resKRWAmt"), CodefFields.text(item, "resAccountCurrency"),
				CodefFields.code(item, "resPaymentType", PaymentType.values(), PaymentType::code, PaymentType.UNKNOWN),
				CodefFields.code(item, "resHomeForeignType", DomesticForeign.values(), DomesticForeign::code,
						DomesticForeign.UNKNOWN),
				CodefFields.code(item, "resCancelYN", CancelStatus.values(), CancelStatus::code, CancelStatus.UNKNOWN),
				CodefFields.amount(item, "resCancelAmount"), CodefFields.text(item, "resApprovalNo"));
	}

	/** 결제방법. 문서 값 "1"·"2"·"3". 국민카드의 "3"에는 선할인(할부건)이 포함된다. */
	public enum PaymentType {

		/** "1": 일시불. */
		LUMP_SUM("1"),

		/** "2": 할부. */
		INSTALLMENT("2"),

		/** "3": 그 외. */
		OTHER("3"),

		/** 빈 값 또는 문서 목록 밖의 값. */
		UNKNOWN(null);

		private final String code;

		PaymentType(String code) {
			this.code = code;
		}

		String code() {
			return code;
		}

	}

	/** 국내/외 구분. 문서 값 "1"·"2". */
	public enum DomesticForeign {

		/** "1": 국내. */
		DOMESTIC("1"),

		/** "2": 해외. */
		FOREIGN("2"),

		/** 빈 값 또는 문서 목록 밖의 값. */
		UNKNOWN(null);

		private final String code;

		DomesticForeign(String code) {
			this.code = code;
		}

		String code() {
			return code;
		}

	}

	/** 취소여부. 문서 값 "0"~"3". 부분취소는 명확히 구분 가능한 경우만 제공된다. */
	public enum CancelStatus {

		/** "0": 정상. */
		NORMAL("0"),

		/** "1": 취소. */
		CANCELED("1"),

		/** "2": 부분취소. */
		PARTIALLY_CANCELED("2"),

		/** "3": 거절. */
		REJECTED("3"),

		/** 빈 값 또는 문서 목록 밖의 값. */
		UNKNOWN(null);

		private final String code;

		CancelStatus(String code) {
			this.code = code;
		}

		String code() {
			return code;
		}

	}

}
