package com.beone.api.payment.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Idempotency-Key에 연결하는 요청 지문(docs/api.md 중복 방지): 사용자·HTTP 메서드·경로(자원 ID 포함)·본문의 SHA-256 16진수.
 *
 * <p>본문은 받은 바이트가 아니라 해석한 값의 고정 표현이다. 공백·필드 순서·생략한 기본값({@code simulateFailure} 생략 = {@code false})
 * 차이는 같은 본문으로 본다. 원문을 저장하지 않으므로 지문에서 요청 값을 되살릴 수 없다.
 */
final class Fingerprint {

	private Fingerprint() {
	}

	static String of(long userId, String method, String path, String canonicalBody) {
		Objects.requireNonNull(method, "method");
		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(canonicalBody, "canonicalBody");
		String material = userId + "\n" + method + "\n" + path + "\n" + canonicalBody;
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is unavailable", ex);
		}
	}

}
