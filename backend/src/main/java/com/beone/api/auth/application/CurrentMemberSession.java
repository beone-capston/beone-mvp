package com.beone.api.auth.application;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 현재 회원 세션 조회 결과. 시간은 Asia/Seoul 기준 시각이다.
 *
 * @param memberId 회원 ID
 * @param email 정규화한 이메일
 * @param authenticatedAt 로그인 시각
 * @param expiresAt 이번 사용 뒤 미사용 7일과 절대 만료 중 빠른 시각
 * @param absoluteExpiresAt 로그인 뒤 30일
 */
public record CurrentMemberSession(String memberId, String email, LocalDateTime authenticatedAt,
		LocalDateTime expiresAt, LocalDateTime absoluteExpiresAt) {

	public CurrentMemberSession {
		Objects.requireNonNull(memberId, "memberId");
		Objects.requireNonNull(email, "email");
		Objects.requireNonNull(authenticatedAt, "authenticatedAt");
		Objects.requireNonNull(expiresAt, "expiresAt");
		Objects.requireNonNull(absoluteExpiresAt, "absoluteExpiresAt");
	}

	@Override
	public String toString() {
		return "CurrentMemberSession[memberId=" + this.memberId + ", email=<hidden>, authenticatedAt="
				+ this.authenticatedAt + ", expiresAt=" + this.expiresAt + ", absoluteExpiresAt="
				+ this.absoluteExpiresAt + "]";
	}

}
