package com.beone.api.benefit.card;

/**
 * 사용자나 시나리오가 확인한 가맹점·업종 분류(예: {@code STARBUCKS_STORE}).
 * 비슷한 가맹점 이름으로 추정한 값을 넣지 않는다.
 */
public record MerchantClass(String code) {

	public MerchantClass {
		Identifiers.requireToken(code, "merchant class");
	}

	public static MerchantClass of(String code) {
		return new MerchantClass(code);
	}

	@Override
	public String toString() {
		return code;
	}

}
