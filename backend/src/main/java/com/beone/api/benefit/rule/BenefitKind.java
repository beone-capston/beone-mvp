package com.beone.api.benefit.rule;

/**
 * 금전 혜택의 지급 방식. 사용자에게 구분해 보여 준다(D-11).
 */
public enum BenefitKind {

	/** 포인트 적립. 1점 = 1원으로 본다(D-11). */
	POINT_ACCRUAL,

	/** 청구·환급 할인. */
	BILLING_DISCOUNT

}
