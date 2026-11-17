package com.beone.api.auth.web;

/**
 * {@code GET /api/v1/auth/csrf} 응답 {@code data}(docs/api/auth.md).
 *
 * @param headerName 토큰을 보낼 요청 헤더 이름. 항상 {@code X-CSRF-TOKEN}
 * @param token CSRF 토큰
 */
public record CsrfTokenResponse(String headerName, String token) {

	/** CSRF 토큰 요청 헤더 이름. */
	public static final String HEADER_NAME = "X-CSRF-TOKEN";

	/**
	 * 토큰으로 응답을 만든다.
	 * @param token CSRF 토큰
	 * @return 응답 데이터
	 */
	public static CsrfTokenResponse of(String token) {
		return new CsrfTokenResponse(HEADER_NAME, token);
	}

	@Override
	public String toString() {
		return "CsrfTokenResponse[headerName=" + this.headerName + ", token=<hidden>]";
	}

}
