package com.beone.api.codef.application;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.crypto.Cipher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 카드사 비밀번호를 CODEF 공개키로 암호화한다(D-14 원칙 9). 실 게이트웨이({@code codef-live})만 쓴다.
 *
 * <p>공개키는 환경 변수 {@value #VARIABLE}(Base64 X.509 RSA 공개키)에서만 읽는다. 없거나 읽을 수 없으면 "설정 안 됨"이고, 등록은 CODEF를
 * 부르지 않고 실패한다. 공개키 해석(Base64 → X.509)·암호화·결과 Base64는 공식 SDK {@code codef-io/easycodef-java}(커밋 {@code ea96123})의
 * {@code EasyCodefUtil.encryptRSA}와 같다. SDK의 {@code "RSA"}는 JDK 기본 제공자에서 RSA/ECB/PKCS1Padding이고, 평문은 SDK가 기본 문자셋
 * (Java 21에서 UTF-8)으로 바꾸므로 여기서는 UTF-8을 명시한다. 평문 비밀번호의 바이트
 * 사본은 암호화 직후 지운다. 값과 암호문은 로그·예외 문구에 넣지 않는다.
 */
public final class CodefPasswordEncryptor {

	/** CODEF 공개키 환경 변수 이름. */
	public static final String VARIABLE = "CODEF_PUBLIC_KEY";

	private static final Logger log = LoggerFactory.getLogger(CodefPasswordEncryptor.class);

	private static final String TRANSFORMATION = "RSA/ECB/PKCS1Padding";

	private final Optional<PublicKey> key;

	private CodefPasswordEncryptor(Optional<PublicKey> key) {
		this.key = key;
	}

	/**
	 * 환경 변수에서 공개키를 읽는다. 읽지 못하면 이유만 남기고 "설정 안 됨"으로 둔다.
	 * @param environment 프로세스 환경 변수
	 * @return 암호화기
	 */
	public static CodefPasswordEncryptor fromEnvironment(Map<String, String> environment) {
		String value = environment.get(VARIABLE);
		if (value == null || value.isBlank()) {
			log.warn("CODEF 공개키 설정 안 됨 missing={}", VARIABLE);
			return new CodefPasswordEncryptor(Optional.empty());
		}
		try {
			byte[] encoded = Base64.getDecoder().decode(value.strip());
			PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
			return new CodefPasswordEncryptor(Optional.of(publicKey));
		}
		catch (IllegalArgumentException | GeneralSecurityException ex) {
			// 예외 문구에 값 일부가 들어갈 수 있어 남기지 않는다
			log.warn("CODEF 공개키를 읽을 수 없음 variable={}", VARIABLE);
			return new CodefPasswordEncryptor(Optional.empty());
		}
	}

	/**
	 * 주어진 공개키로 만든다. 테스트의 합성 공개키 주입용이다.
	 * @param publicKey RSA 공개키
	 * @return 암호화기
	 */
	public static CodefPasswordEncryptor of(PublicKey publicKey) {
		return new CodefPasswordEncryptor(Optional.of(Objects.requireNonNull(publicKey, "publicKey")));
	}

	public boolean isConfigured() {
		return this.key.isPresent();
	}

	/**
	 * 비밀번호를 암호화한다. 받은 배열은 지우지 않는다(호출자 책임).
	 * @param password 평문 비밀번호
	 * @return Base64 암호문. 공개키가 없거나 암호화하지 못하면 빈 값
	 */
	public Optional<String> encrypt(char[] password) {
		if (this.key.isEmpty()) {
			return Optional.empty();
		}
		ByteBuffer buffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password));
		byte[] plain = new byte[buffer.remaining()];
		buffer.get(plain);
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, this.key.get());
			return Optional.of(Base64.getEncoder().encodeToString(cipher.doFinal(plain)));
		}
		catch (GeneralSecurityException ex) {
			log.warn("CODEF 비밀번호 암호화 실패 reason={}", ex.getClass().getSimpleName());
			return Optional.empty();
		}
		finally {
			Arrays.fill(plain, (byte) 0);
			if (buffer.hasArray()) {
				Arrays.fill(buffer.array(), (byte) 0);
			}
		}
	}

	@Override
	public String toString() {
		return isConfigured() ? "CodefPasswordEncryptor[configured]" : "CodefPasswordEncryptor[not configured]";
	}

}
