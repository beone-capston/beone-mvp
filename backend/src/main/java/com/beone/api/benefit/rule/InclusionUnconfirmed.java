package com.beone.api.benefit.rule;

import java.util.Objects;

import com.beone.api.benefit.card.MerchantClass;

/**
 * 포함 관계가 확인되지 않은 업종 코드 쌍(D-18 U2 ① 예외).
 * 주문이나 거래의 코드가 {@code merchantClass}일 때 {@code relatedClass}에 드는지 모른다는 뜻이다.
 * 코드가 달라도 불일치로 단정하지 않는다. 이 타입은 표시만 담고, 판정은 하지 않는다.
 */
public record InclusionUnconfirmed(MerchantClass merchantClass, MerchantClass relatedClass) {

	public InclusionUnconfirmed {
		Objects.requireNonNull(merchantClass, "merchantClass");
		Objects.requireNonNull(relatedClass, "relatedClass");
		if (merchantClass.equals(relatedClass)) {
			throw new IllegalArgumentException("inclusion pair needs two different classes: " + merchantClass);
		}
	}

}
