package com.beone.api.auth.application;

import java.util.Objects;

import com.beone.api.auth.persistence.AuthTokenPurpose;

/**
 * 인증 메일 한 통의 내용. 링크 주소 형식은 미정이라(실발송·화면 경로 결정 전, 관리자 판단 결정 28) 원문 토큰만 담는다.
 *
 * @param to 받는 사람(정규화한 이메일)
 * @param purpose 토큰 목적
 * @param token 원문 토큰. 로그에 남기지 않는다
 */
public record AuthMail(String to, AuthTokenPurpose purpose, String token) {

	public AuthMail {
		Objects.requireNonNull(to, "to");
		Objects.requireNonNull(purpose, "purpose");
		Objects.requireNonNull(token, "token");
	}

	@Override
	public String toString() {
		return "AuthMail[purpose=" + this.purpose + ", to=<hidden>, token=<hidden>]";
	}

}
