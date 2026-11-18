package com.beone.api.auth.web;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.beone.api.auth.application.CurrentMemberSession;

/**
 * 현재 회원 세션 조회 응답(docs/api/auth.md). 시점은 {@code +09:00}이 붙은 ISO-8601이다(docs/api.md 값 형식).
 *
 * @param userId 회원 ID
 * @param email 정규화한 이메일
 * @param authenticatedAt 로그인 시각
 * @param expiresAt 이번 활동 뒤 미사용 7일과 절대 만료 중 빠른 시각
 * @param absoluteExpiresAt 로그인 뒤 30일
 */
public record MemberSessionResponse(String userId, String email, String authenticatedAt, String expiresAt,
		String absoluteExpiresAt) {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private static final DateTimeFormatter INSTANT = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssXXX");

	static MemberSessionResponse from(CurrentMemberSession session) {
		return new MemberSessionResponse(session.memberId(), session.email(), seoul(session.authenticatedAt()),
				seoul(session.expiresAt()), seoul(session.absoluteExpiresAt()));
	}

	private static String seoul(LocalDateTime seoulTime) {
		return INSTANT.format(seoulTime.atZone(SEOUL));
	}

	@Override
	public String toString() {
		return "MemberSessionResponse[userId=" + this.userId + ", email=<hidden>, authenticatedAt="
				+ this.authenticatedAt + ", expiresAt=" + this.expiresAt + ", absoluteExpiresAt="
				+ this.absoluteExpiresAt + "]";
	}

}
