package com.beone.api.auth.web;

/**
 * 세션 없음·만료·로그아웃·익명 세션이라 현재 회원을 알 수 없다. 401 {@code UNAUTHENTICATED}로 답한다.
 */
class UnauthenticatedException extends RuntimeException {

	UnauthenticatedException() {
		super("unauthenticated");
	}

}
