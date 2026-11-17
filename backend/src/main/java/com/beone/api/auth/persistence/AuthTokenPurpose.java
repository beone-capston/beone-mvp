package com.beone.api.auth.persistence;

/**
 * 메일로 보내는 1회용 토큰의 목적. 목적이 다른 토큰은 받지 않는다(docs/api/auth.md).
 *
 * <p>U2는 이메일 인증만 쓴다. 비밀번호 재설정 목적은 그 API(U4)와 함께 추가한다.
 */
public enum AuthTokenPurpose {

	/** 이메일 인증. 30분·1회용. */
	EMAIL_VERIFICATION

}
