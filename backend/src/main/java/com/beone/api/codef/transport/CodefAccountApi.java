package com.beone.api.codef.transport;

/**
 * 전송 계층이 호출하는 CODEF 계정 API와 경로. 상품 3종({@link CodefApi})과 따로 센다.
 *
 * <p>경로와 요청 형식은 비공개 실연동 전 근거 확인 대상이다. 저장소에는 계정 API의 공식 문서 사본이 아직 없다.
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
