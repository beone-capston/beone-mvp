package com.beone.api.payment.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 요청 지문 = 사용자·메서드·경로·본문(docs/api.md 중복 방지). 넷 중 하나만 달라도 다른 지문이다.
 */
class FingerprintTest {

	@Test
	void sameInputsGiveTheSameSha256Hex() {
		String a = Fingerprint.of(1, "POST", "/api/v1/me/orders/ord-1/simulated-payments", "{}");

		assertThat(a).hasSize(64).matches("[0-9a-f]{64}");
		assertThat(Fingerprint.of(1, "POST", "/api/v1/me/orders/ord-1/simulated-payments", "{}")).isEqualTo(a);
	}

	@Test
	void anyPartChangesTheFingerprint() {
		String base = Fingerprint.of(1, "POST", "/p", "{}");

		assertThat(Fingerprint.of(2, "POST", "/p", "{}")).isNotEqualTo(base);
		assertThat(Fingerprint.of(1, "PUT", "/p", "{}")).isNotEqualTo(base);
		assertThat(Fingerprint.of(1, "POST", "/q", "{}")).isNotEqualTo(base);
		assertThat(Fingerprint.of(1, "POST", "/p", "{\"a\":1}")).isNotEqualTo(base);
	}

}
