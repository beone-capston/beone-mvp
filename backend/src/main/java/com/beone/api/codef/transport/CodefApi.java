package com.beone.api.codef.transport;

/**
 * 전송 계층이 호출하는 CODEF 개인 카드 상품 3종과 경로.
 */
public enum CodefApi {

	HELD_CARDS("/v1/kr/card/p/account/card-list"),

	APPROVALS("/v1/kr/card/p/account/approval-list"),

	CARD_PERFORMANCES("/v1/kr/card/p/account/result-check-list");

	private final String path;

	CodefApi(String path) {
		this.path = path;
	}

	public String path() {
		return path;
	}

}
