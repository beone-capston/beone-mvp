package com.beone.api.codef.transport;

/**
 * 전송 계층이 호출하는 CODEF 계정 API와 경로. 상품 3종({@link CodefApi})과 따로 센다.
 *
 * <p>경로는 공식 SDK {@code codef-io/easycodef-java}(커밋 {@code ea96123})의 {@code EasyCodefConstant}와 같다. 공식 개발자 문서 본문은 확인하지 못했다(docs/domains/codef.md).
 */
public enum CodefAccountApi {

	ACCOUNT_CREATE("/v1/account/create"),

	ACCOUNT_DELETE("/v1/account/delete");

	private final String path;

	CodefAccountApi(String path) {
		this.path = path;
	}

	public String path() {
		return path;
	}

}
