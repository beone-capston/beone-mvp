package com.beone.api.codef.application;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.beone.api.codef.sync.CodefApprovalFingerprint;
import com.beone.api.codef.sync.CodefApprovalFingerprinter;

/**
 * 승인번호 원문을 HMAC-SHA256 소문자 16진수 64자로 바꾼다(결정 20 R2, P6). 원문은 이 메서드 밖으로 내보내지 않고 예외 문구에도 넣지 않는다.
 * 키가 바뀌면 같은 승인번호의 지문도 달라진다(회전은 미구현).
 */
final class HmacApprovalFingerprinter implements CodefApprovalFingerprinter {

	private static final String ALGORITHM = "HmacSHA256";

	private final SecretKeySpec key;

	HmacApprovalFingerprinter(byte[] key) {
		Objects.requireNonNull(key, "key");
		if (key.length != 32) {
			throw new IllegalArgumentException("approval HMAC key must be 32 bytes");
		}
		this.key = new SecretKeySpec(key, ALGORITHM);
	}

	@Override
	public CodefApprovalFingerprint fingerprint(String approvalNumber) {
		Objects.requireNonNull(approvalNumber, "approvalNumber");
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(this.key);
			return new CodefApprovalFingerprint(
					HexFormat.of().formatHex(mac.doFinal(approvalNumber.getBytes(StandardCharsets.UTF_8))));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("approval HMAC is not available");
		}
	}

}
