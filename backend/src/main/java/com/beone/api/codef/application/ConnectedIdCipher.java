package com.beone.api.codef.application;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * connectedId를 AES-256-GCM으로 암호화·복호화한다(2026-11-18 사용자 직접 결정 P5).
 *
 * <p>저장 형식은 {@code v1:} + Base64(무작위 12바이트 IV ‖ 암호문 ‖ 16바이트 인증 태그)다. 사용자 ID를 추가 인증 데이터로 묶어 다른
 * 사용자 행으로 옮긴 값은 복호화되지 않는다. 키 회전은 구현하지 않았다. 예외 문구에 평문·암호문을 넣지 않는다.
 */
final class ConnectedIdCipher {

	private static final String PREFIX = "v1:";

	private static final int IV_BYTES = 12;

	private static final int TAG_BITS = 128;

	private static final SecureRandom RANDOM = new SecureRandom();

	private final SecretKeySpec key;

	ConnectedIdCipher(byte[] key) {
		Objects.requireNonNull(key, "key");
		if (key.length != 32) {
			throw new IllegalArgumentException("key must be 32 bytes");
		}
		this.key = new SecretKeySpec(key, "AES");
	}

	String encrypt(long userId, String connectedId) {
		byte[] iv = new byte[IV_BYTES];
		RANDOM.nextBytes(iv);
		try {
			Cipher cipher = cipher(Cipher.ENCRYPT_MODE, iv, userId);
			byte[] sealed = cipher.doFinal(connectedId.getBytes(StandardCharsets.UTF_8));
			return PREFIX + Base64.getEncoder()
				.encodeToString(ByteBuffer.allocate(IV_BYTES + sealed.length).put(iv).put(sealed).array());
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("connectedId encryption failed");
		}
	}

	String decrypt(long userId, String stored) {
		if (stored == null || !stored.startsWith(PREFIX)) {
			throw new IllegalStateException("unsupported connectedId format");
		}
		try {
			byte[] packed = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
			if (packed.length <= IV_BYTES) {
				throw new IllegalStateException("connectedId decryption failed");
			}
			ByteBuffer buffer = ByteBuffer.wrap(packed);
			byte[] iv = new byte[IV_BYTES];
			buffer.get(iv);
			byte[] sealed = new byte[buffer.remaining()];
			buffer.get(sealed);
			return new String(cipher(Cipher.DECRYPT_MODE, iv, userId).doFinal(sealed), StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException | IllegalArgumentException ex) {
			throw new IllegalStateException("connectedId decryption failed");
		}
	}

	private Cipher cipher(int mode, byte[] iv, long userId) throws GeneralSecurityException {
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(mode, this.key, new GCMParameterSpec(TAG_BITS, iv));
		cipher.updateAAD(("codef-connection:" + userId).getBytes(StandardCharsets.UTF_8));
		return cipher;
	}

}
