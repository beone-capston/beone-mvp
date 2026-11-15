package com.beone.api.benefit.card;

/**
 * 카드 규칙 안의 월 혜택 한도 식별자(예: {@code Great}, {@code CU_AREA}).
 * 대소문자를 바꾸지 않고 적힌 그대로 비교한다.
 */
public record LimitBucketId(String value) {

	public LimitBucketId {
		Identifiers.requireToken(value, "limit bucket id");
	}

	public static LimitBucketId of(String value) {
		return new LimitBucketId(value);
	}

	@Override
	public String toString() {
		return value;
	}

}
