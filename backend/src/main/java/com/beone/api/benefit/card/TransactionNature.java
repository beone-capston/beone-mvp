package com.beone.api.benefit.card;

/**
 * 전월 실적 판정에 쓰는 원장 거래의 성격. 승인·취소 구분과 별도로 둔다(사례집 {@code ORDINARY}, {@code TAX}).
 */
public enum TransactionNature {

	/** 할인을 받지 않은 실적 인정 거래. */
	ORDINARY,

	/** 세금·공과금. 후보 규칙에서 실적 제외 대상이다. */
	TAX

}
