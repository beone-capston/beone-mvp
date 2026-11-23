package com.beone.api.codef.sync;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 테스트 전용 HMAC-SHA256 지문 함수. 키는 테스트 코드 안의 명백한 합성 값이며 운영 키가 아니다. 운영 구현체·키 이름·공급·회전은 미결이다(결정 R2).
 */
final class SyntheticHmacFingerprinter implements CodefApprovalFingerprinter {

	/** 합성 테스트 키 1. 비밀값이 아니다. */
	static final SyntheticHmacFingerprinter KEY_ONE = new SyntheticHmacFingerprinter(
			"SYN-TEST-HMAC-KEY-ONE-NOT-A-SECRET");

	/** 합성 테스트 키 2. 비밀값이 아니다. */
	static final SyntheticHmacFingerprinter KEY_TWO = new SyntheticHmacFingerprinter(
			"SYN-TEST-HMAC-KEY-TWO-NOT-A-SECRET");

	private static final String ALGORITHM = "HmacSHA256";

	private final SecretKeySpec key;

	private SyntheticHmacFingerprinter(String syntheticKey) {
		this.key = new SecretKeySpec(syntheticKey.getBytes(StandardCharsets.UTF_8), ALGORITHM);
	}

	@Override
	public CodefApprovalFingerprint fingerprint(String approvalNumber) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(key);
			byte[] digest = mac.doFinal(approvalNumber.getBytes(StandardCharsets.UTF_8));
			return new CodefApprovalFingerprint(HexFormat.of().formatHex(digest));
		}
		catch (GeneralSecurityException failure) {
			throw new IllegalStateException("HMAC is unavailable", failure);
		}
	}

	@Override
	public String toString() {
		return "SyntheticHmacFingerprinter";
	}

}
