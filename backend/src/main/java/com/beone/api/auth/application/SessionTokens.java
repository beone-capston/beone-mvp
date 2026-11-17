package com.beone.api.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * 세션 ID와 CSRF 토큰을 만들고 비교하는 부품. 상태가 없다.
 *
 * <ul>
 * <li>세션 ID: {@link SecureRandom} 32바이트를 패딩 없는 Base64URL로 쓴 43자 문자열이다. 쿠키에만 담고 DB에는
 * {@link #hash(String) SHA-256 해시}만 저장한다.</li>
 * <li>CSRF 토큰: 세션 ID에서 SHA-256으로 유도한 43자 Base64URL 값이다. 세션마다 하나이고 세션 ID가 바뀌면 함께 바뀐다.
 * 쿠키는 HttpOnly라 다른 사이트의 스크립트가 세션 ID를 읽어 토큰을 만들 수 없다. 서버는 토큰을 따로 저장하지 않고 쿠키에서 다시
 * 계산해 비교한다.</li>
 * <li>비교는 {@link MessageDigest#isEqual(byte[], byte[])}로 상수 시간에 한다.</li>
 * </ul>
 * 원문 세션 ID와 토큰은 로그·예외 메시지에 넣지 않는다.
 */
public final class SessionTokens {

	/** 세션 ID와 CSRF 토큰 형식: 32바이트의 패딩 없는 Base64URL. */
	private static final Pattern TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");

	private static final int TOKEN_BYTES = 32;

	/** CSRF 토큰 유도용 도메인 구분 접두어. 세션 해시와 같은 값이 나오지 않게 한다. */
	private static final String CSRF_PREFIX = "beone-csrf-v1:";

	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

	private static final SecureRandom RANDOM = new SecureRandom();

	private SessionTokens() {
	}

	/** 새 세션 ID(43자 Base64URL)를 만든다. */
	public static String newSessionId() {
		byte[] bytes = new byte[TOKEN_BYTES];
		RANDOM.nextBytes(bytes);
		return ENCODER.encodeToString(bytes);
	}

	/**
	 * 값이 세션 ID·토큰 형식(43자 Base64URL)인지 본다.
	 * @param value 검사할 값, {@code null} 가능
	 * @return 형식이 맞으면 {@code true}
	 */
	public static boolean isWellFormed(String value) {
		return value != null && TOKEN_FORMAT.matcher(value).matches();
	}

	/**
	 * 세션 ID의 저장용 해시(SHA-256, 소문자 16진수 64자).
	 * @param sessionId 세션 ID 원문
	 * @return 16진수 해시
	 */
	public static String hash(String sessionId) {
		return HexFormat.of().formatHex(sha256(sessionId));
	}

	/**
	 * 세션 ID에 묶인 CSRF 토큰(43자 Base64URL).
	 * @param sessionId 세션 ID 원문
	 * @return CSRF 토큰
	 */
	public static String csrfTokenOf(String sessionId) {
		return ENCODER.encodeToString(sha256(CSRF_PREFIX + sessionId));
	}

	/**
	 * 받은 CSRF 토큰이 세션의 토큰과 같은지 상수 시간으로 비교한다.
	 * @param sessionId 세션 ID 원문
	 * @param presentedToken 요청 헤더의 토큰, {@code null} 가능
	 * @return 같으면 {@code true}
	 */
	public static boolean csrfMatches(String sessionId, String presentedToken) {
		if (presentedToken == null) {
			return false;
		}
		byte[] expected = csrfTokenOf(sessionId).getBytes(StandardCharsets.US_ASCII);
		byte[] presented = presentedToken.getBytes(StandardCharsets.UTF_8);
		return MessageDigest.isEqual(expected, presented);
	}

	private static byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException ex) {
			// 모든 Java 플랫폼은 SHA-256을 제공해야 한다.
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}

}
