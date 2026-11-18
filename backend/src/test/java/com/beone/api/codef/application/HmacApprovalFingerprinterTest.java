package com.beone.api.codef.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * 승인번호 HMAC 지문(결정 20 R2, P6). 기대값은 Python {@code hmac.new(key, msg, hashlib.sha256).hexdigest()}로 따로 계산한 값이다.
 * 키와 승인번호는 모두 합성 값이다.
 */
class HmacApprovalFingerprinterTest {

	private static final byte[] KEY = new byte[32];

	@Test
	void producesLowercaseHexHmacSha256() {
		assertThat(new HmacApprovalFingerprinter(KEY).fingerprint("abc").value())
			.isEqualTo("fd7adb152c05ef80dccf50a1fa4c05d5a3ec6da95575fc312ae7c5d091836351");
	}

	@Test
	void sameKeyAndNumberGiveTheSameValueAndNeverTheNumberItself() {
		HmacApprovalFingerprinter fingerprinter = new HmacApprovalFingerprinter(KEY);
		String first = fingerprinter.fingerprint("FAKE-APR-0001").value();

		assertThat(fingerprinter.fingerprint("FAKE-APR-0001").value()).isEqualTo(first);
		assertThat(first).matches("[0-9a-f]{64}").doesNotContain("FAKE-APR-0001");
		assertThat(fingerprinter.fingerprint("FAKE-APR-0002").value()).isNotEqualTo(first);
	}

	@Test
	void differentKeyBreaksComparison() {
		byte[] other = new byte[32];
		other[0] = 1;
		assertThat(new HmacApprovalFingerprinter(other).fingerprint("abc").value())
			.isNotEqualTo(new HmacApprovalFingerprinter(KEY).fingerprint("abc").value());
	}

	@Test
	void keyMustBe32Bytes() {
		assertThatThrownBy(() -> new HmacApprovalFingerprinter(new byte[16]))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
