package com.beone.api.codef.sync;

/**
 * 원장 후보로 받아들인 행의 종류(D-17). 재조회 대조의 이전 기록에도 이 값을 쓴다.
 */
public enum CodefLedgerRowKind {

	/** 정상 승인 행. */
	APPROVAL,

	/** Type1: 한 행에 승인과 같은 금액의 전액 취소가 함께 있다(취소금액 = 이용금액). 취소 귀속월은 확인 필요다. */
	APPROVAL_FULLY_CANCELED,

	/** Type2: 같은 카드·같은 승인번호 지문의 정상 행 하나와 금액 절댓값이 같은 취소 행. 취소 귀속월은 확인 필요다. */
	CANCELLATION;

	/**
	 * 취소를 담은 종류인지.
	 *
	 * @return Type1·Type2이면 참
	 */
	public boolean includesCancellation() {
		return this != APPROVAL;
	}

}
