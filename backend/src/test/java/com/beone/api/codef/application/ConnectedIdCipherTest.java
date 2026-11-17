package com.beone.api.codef.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

/**
 * connectedId AES-256-GCM 암호화(2026-11-18 사용자 직접 결정 P5). 키는 테스트 코드 안 합성 값이다.
 */
class ConnectedIdCipherTest {

	private static final byte[] KEY = filled((byte) 7);

	private final ConnectedIdCipher cipher = new ConnectedIdCipher(KEY);

	@Test
	void roundTripsAndNeverStoresThePlainValue() {
		String stored = this.cipher.encrypt(1L, "fake-cid-123");

		assertThat(stored).startsWith("v1:").doesNotContain("fake-cid-123");
		assertThat(this.cipher.decrypt(1L, stored)).isEqualTo("fake-cid-123");
	}

	@Test
	void eachEncryptionUsesAFreshIv() {
		assertThat(this.cipher.encrypt(1L, "same")).isNotEqualTo(this.cipher.encrypt(1L, "same"));
	}

	@Test
	void valueMovedToAnotherUserOrTamperedOrUnderAnotherKeyDoesNotDecrypt() {
		String stored = this.cipher.encrypt(1L, "fake-cid-123");
		char[] chars = stored.toCharArray();
		chars[chars.length - 2] = (chars[chars.length - 2] == 'A') ? 'B' : 'A';

		assertDecryptFails(() -> this.cipher.decrypt(2L, stored));
		assertDecryptFails(() -> this.cipher.decrypt(1L, new String(chars)));
		assertDecryptFails(() -> new ConnectedIdCipher(filled((byte) 8)).decrypt(1L, stored));
		assertDecryptFails(() -> this.cipher.decrypt(1L, "fake-cid-123"));
		assertDecryptFails(() -> this.cipher.decrypt(1L, "v1:%%%"));
		assertDecryptFails(() -> this.cipher.decrypt(1L, "v1:AAAA"));
	}

	@Test
	void keyMustBe32Bytes() {
		assertThatThrownBy(() -> new ConnectedIdCipher(new byte[16])).isInstanceOf(IllegalArgumentException.class);
	}

	private static void assertDecryptFails(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
		assertThatThrownBy(call).isInstanceOf(IllegalStateException.class).message().doesNotContain("fake-cid-123");
	}

	private static byte[] filled(byte value) {
		byte[] key = new byte[32];
		Arrays.fill(key, value);
		return key;
	}

}
