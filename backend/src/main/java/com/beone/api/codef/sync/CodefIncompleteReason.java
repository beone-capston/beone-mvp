package com.beone.api.codef.sync;

/**
 * 원장 후보로 받아들이지 않은 행의 불완전 사유. 원장 조회 명세(사본)의 5종에 맞춘다. 이 행이 R1로 보유카드에 붙었으면 그 카드의 그 행 날짜에만 불완전을 표시한다.
 */
public enum CodefIncompleteReason {

	/** 부분취소일 수 있다: 부분취소 코드("2"), Type1인데 취소금액이 이용금액과 다름, Type2 취소 금액 절댓값이 짝 승인보다 작음(D-17). */
	PARTIAL_CANCELLATION_SUSPECTED,

	/** Type2 취소 행과 같은 카드·같은 승인번호 지문의 정상 행이 이 응답에 없다. 조회 기간을 넓히지 않는다(D-17). */
	NO_MATCHING_APPROVAL,

	/** Type2 취소 행과 같은 카드·같은 승인번호 지문의 정상 행이 둘 이상이다(D-17). */
	MULTIPLE_MATCHING_APPROVALS,

	/** 판단 불가 행: 카드 미연결(R1), 날짜·시각 해석 실패(R4), 형태·통화 판단 불가, 재조회 모순 변화 등. */
	UNDETERMINED_ROW,

	/** 중복 판단 키로 하나를 정할 수 없다: 승인번호·사용일자 없음, 한 응답 안 같은 키 둘 이상(R2). */
	DUPLICATE_KEY_AMBIGUOUS

}
