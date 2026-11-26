package com.beone.api.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 회원이 탈퇴한다. 탈퇴 트랜잭션 안에서 회원 행을 지우기 전에 동기로 발행한다. 업무 장부 삭제(user 패키지)가 받는다. 받는 쪽이 실패하면
 * 탈퇴 전체가 롤백된다.
 *
 * @param memberId 회원 ID
 * @param memberRef 로그용 회원 참조값. 회원 ID의 SHA-256 앞 12자로, 이메일·회원 ID 원문 대신 로그에 쓴다
 */
public record MemberWithdrawnEvent(String memberId, String memberRef) {

	public MemberWithdrawnEvent {
		Objects.requireNonNull(memberId, "memberId");
		Objects.requireNonNull(memberRef, "memberRef");
	}

	/**
	 * 회원 ID로 이벤트를 만든다.
	 * @param memberId 회원 ID
	 * @return 탈퇴 이벤트
	 */
	public static MemberWithdrawnEvent of(String memberId) {
		return new MemberWithdrawnEvent(memberId, refOf(memberId));
	}

	/**
	 * 로그용 회원 참조값(회원 ID의 SHA-256 소문자 16진수 앞 12자).
	 * @param memberId 회원 ID
	 * @return 참조값
	 */
	public static String refOf(String memberId) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(memberId.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest).substring(0, 12);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 not available", ex);
		}
	}

}
