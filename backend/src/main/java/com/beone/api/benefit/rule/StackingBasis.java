package com.beone.api.benefit.rule;

/**
 * 중복 서비스 혜택을 합산할 때의 계산 기준(D-13 #35 8번). 값이 없는 서비스는 합산하지 않는다.
 */
public enum StackingBasis {

	/** 각 서비스를 할인 전 금액으로 계산하고 한도는 버킷별로 각각 차감한다. */
	PRE_DISCOUNT_EACH_BUCKET

}
