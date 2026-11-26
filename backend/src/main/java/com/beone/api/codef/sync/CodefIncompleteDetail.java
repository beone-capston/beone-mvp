package com.beone.api.codef.sync;

/**
 * 불완전 행이 걸린 판정 분기(내부 진단용, #270). 공개 사유 {@link CodefIncompleteReason}은 여러 분기를 하나로 묶으므로(특히
 * {@link CodefIncompleteReason#UNDETERMINED_ROW}) 동기화 진단 로그에서 실제 분기를 세려고 둔다. API 응답에는 내보내지 않는다.
 */
public enum CodefIncompleteDetail {

	/** 카드 미연결(R1). 연결하지 않은 이유는 행의 {@link CodefCardLink.NotLinked#reason()}에 있다. */
	CARD_NOT_LINKED,

	/** 부분취소 코드("2"). */
	PARTIAL_CANCEL_CODE,

	/** Type1 취소인데 취소금액이 이용금액과 다르다. */
	TYPE1_CANCEL_AMOUNT_MISMATCH,

	/** 취소여부가 값 없음이거나 문서의 값 목록 밖이다. */
	CANCEL_STATUS_UNKNOWN,

	/** 취소 형태 판단에 필요한 금액이 해석되지 않았다. */
	FORM_AMOUNT_NOT_PARSED,

	/** 취소 표현 설명에 없는 취소여부다. */
	CANCEL_STATUS_NOT_DESCRIBED,

	/** 취소여부와 금액 모양의 조합이 문서 예시와 다르다. */
	FORM_SHAPE_MISMATCH,

	/** KB 밖 기관이라 취소 표현 근거가 없다. */
	ORGANIZATION_NOT_DOCUMENTED,

	/** 원화 금액 불가: 통화코드 없음. */
	KRW_CURRENCY_ABSENT,

	/** 원화 금액 불가: 통화코드가 KRW가 아니다. */
	KRW_CURRENCY_NOT_KRW,

	/** 원화 금액 불가: 국내 거래가 아니다. */
	KRW_NOT_DOMESTIC,

	/** 원화 금액 불가: 이용금액을 정수로 해석하지 못했다. */
	KRW_USED_AMOUNT_NOT_PARSED,

	/** 승인번호 없음(R2). */
	APPROVAL_NO_ABSENT,

	/** 사용일자 없음(R2). */
	USED_DATE_ABSENT,

	/** 사용일자·사용일시를 해석하지 못했거나 사용일시가 없다(R4). */
	USED_DATE_TIME_NOT_PARSED,

	/** 한 응답 안에 같은 중복 판단 키가 둘 이상이다(R2). */
	DUPLICATE_KEY_IN_RESPONSE,

	/** 재조회 대조 모순(정상 → Type1 밖의 종류 변화). */
	RECHECK_CONTRADICTION,

	/** Type2 취소의 짝 정상 행이 없다. */
	NO_MATCHING_APPROVAL,

	/** Type2 취소의 짝 정상 행이 둘 이상이다. */
	MULTIPLE_MATCHING_APPROVALS,

	/** Type2 취소 금액 절댓값이 짝 승인보다 작다. */
	CANCEL_SMALLER_THAN_APPROVAL,

	/** Type2 취소 금액 절댓값이 짝 승인보다 크다. */
	CANCEL_LARGER_THAN_APPROVAL,

	/** Type2 취소의 짝 정상 행이 받아들여지지 않았다. */
	PAIRED_APPROVAL_NOT_ACCEPTED,

	/** 정상 행 하나를 전액 취소 행 여럿이 가리킨다. */
	MULTIPLE_CANCELS_FOR_ONE_APPROVAL

}
