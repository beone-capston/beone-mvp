package com.beone.api.auth.application;

import java.util.UUID;

/**
 * 회원 ID 발급. {@code usr-} + 무작위 UUID v4의 하이픈을 뺀 소문자 16진수 32자(총 36자)다(관리자 판단, 결정 28).
 *
 * <p>순번을 쓰지 않아 ID로 가입 규모나 다른 회원을 추정할 수 없다. 사본의 예시 {@code usr-1001}은 모양 예시다.
 */
final class MemberIds {

	static final String PREFIX = "usr-";

	private MemberIds() {
	}

	static String newId() {
		return PREFIX + UUID.randomUUID().toString().replace("-", "");
	}

}
