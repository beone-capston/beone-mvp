package com.beone.api.auth.application;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 쿠키로 찾은 유효한 회원 로그인 세션. 시간은 Asia/Seoul 기준 시각이다.
 *
 * @param memberId 회원 ID
 * @param authenticatedAt 로그인 시각
 * @param expiresAt 이번 사용 뒤 미사용 7일과 절대 만료 중 빠른 시각
 * @param absoluteExpiresAt 로그인 뒤 30일
 */
public record MemberSession(String memberId, LocalDateTime authenticatedAt, LocalDateTime expiresAt,
		LocalDateTime absoluteExpiresAt) {

	public MemberSession {
		Objects.requireNonNull(memberId, "memberId");
		Objects.requireNonNull(authenticatedAt, "authenticatedAt");
		Objects.requireNonNull(expiresAt, "expiresAt");
		Objects.requireNonNull(absoluteExpiresAt, "absoluteExpiresAt");
	}

}
