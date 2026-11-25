package com.beone.api.auth.persistence;

/**
 * 메일로 보내는 1회용 토큰의 목적. 목적이 다른 토큰은 받지 않는다(docs/api/auth.md).
 *
 * <p>이메일 인증(U2)과 비밀번호 재설정(U4) 토큰은 같은 테이블에 두고 이 목적 열로 구분한다. 한쪽 API는 다른 목적의 토큰을 받지 않는다.
 */
public enum AuthTokenPurpose {

	/** 이메일 인증. 30분·1회용. */
	EMAIL_VERIFICATION,

	/** 비밀번호 재설정. 15분·1회용. */
	PASSWORD_RESET

}
