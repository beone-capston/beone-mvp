package com.beone.api.codef.sync;

import java.util.Objects;

/**
 * 승인번호의 지문. 비밀키로 만든 되돌릴 수 없는 값(HMAC)이며, 원문 승인번호를 남기지 않고 같은 번호인지만 비교할 때 쓴다(결정 R2).
 *
 * <p>
 * 이 타입은 값을 만들지 않는다. 값은 주입받은 {@link CodefApprovalFingerprinter}가 만든다. 키가 바뀌면 같은 승인번호의 지문도 달라져 이전 기록과 비교가
 * 끊긴다. 운영 키의 이름·공급·회전은 미결이다.
 *
 * @param value 지문 문자열. 형식은 지문 함수가 정한다
 */
public record CodefApprovalFingerprint(String value) {

	/**
	 * 빈 값을 거부한다.
	 *
	 * @throws NullPointerException 값이 null인 경우
	 * @throws IllegalArgumentException 값이 공백뿐인 경우
	 */
	public CodefApprovalFingerprint {
		Objects.requireNonNull(value, "value");
		if (value.isBlank()) {
			throw new IllegalArgumentException("fingerprint must not be blank");
		}
	}

}
