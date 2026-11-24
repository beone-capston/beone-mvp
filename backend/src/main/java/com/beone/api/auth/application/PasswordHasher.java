package com.beone.api.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 회원 비밀번호의 Argon2id 해시(D-24, docs/api/auth.md 「비밀번호 저장」).
 *
 * <p>파라미터는 m=19456KiB, t=2, p=1이다(OWASP Password Storage Cheat Sheet 최소 권장값, 관리자 승인 결정 30). 솔트는 무작위
 * 16바이트, 해시는 32바이트이고 표준 PHC 문자열({@code $argon2id$v=19$m=19456,t=2,p=1$<솔트>$<해시>})로 저장한다. 원문 비밀번호는
 * 저장하거나 로그에 남기지 않는다. {@link #matches}는 로그인 단위(U3)가 쓴다.
 */
@Component
@Profile({ "local", "demo", "member" })
public class PasswordHasher {

	static final int MEMORY_KIB = 19456;

	static final int ITERATIONS = 2;

	static final int PARALLELISM = 1;

	private static final int SALT_BYTES = 16;

	private static final int HASH_BYTES = 32;

	private static final Pattern PHC = Pattern
		.compile("\\$argon2id\\$v=19\\$m=(\\d+),t=(\\d+),p=(\\d+)\\$([A-Za-z0-9+/]+)\\$([A-Za-z0-9+/]+)");

	private static final Base64.Encoder ENCODER = Base64.getEncoder().withoutPadding();

	private static final Base64.Decoder DECODER = Base64.getDecoder();

	private final SecureRandom random = new SecureRandom();

	/**
	 * 무작위 솔트로 해시한다.
	 * @param password 원문 비밀번호
	 * @return PHC 문자열
	 */
	public String hash(String password) {
		byte[] salt = new byte[SALT_BYTES];
		this.random.nextBytes(salt);
		return encode(salt, derive(password, salt, MEMORY_KIB, ITERATIONS, PARALLELISM, HASH_BYTES));
	}

	/**
	 * 원문 비밀번호가 저장한 해시와 맞는지 상수 시간으로 비교한다. 저장값에 적힌 파라미터로 다시 계산한다.
	 * @param password 원문 비밀번호
	 * @param encoded 저장한 PHC 문자열
	 * @return 맞으면 {@code true}. 형식이 다르면 {@code false}
	 */
	public boolean matches(String password, String encoded) {
		Objects.requireNonNull(password, "password");
		Matcher m = PHC.matcher(Objects.requireNonNull(encoded, "encoded"));
		if (!m.matches()) {
			return false;
		}
		byte[] salt = DECODER.decode(m.group(4));
		byte[] expected = DECODER.decode(m.group(5));
		byte[] actual = derive(password, salt, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
				Integer.parseInt(m.group(3)), expected.length);
		return MessageDigest.isEqual(expected, actual);
	}

	static byte[] derive(String password, byte[] salt, int memoryKib, int iterations, int parallelism, int length) {
		Argon2Parameters parameters = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
			.withVersion(Argon2Parameters.ARGON2_VERSION_13)
			.withMemoryAsKB(memoryKib)
			.withIterations(iterations)
			.withParallelism(parallelism)
			.withSalt(salt)
			.build();
		Argon2BytesGenerator generator = new Argon2BytesGenerator();
		generator.init(parameters);
		byte[] out = new byte[length];
		generator.generateBytes(Objects.requireNonNull(password, "password").getBytes(StandardCharsets.UTF_8), out);
		return out;
	}

	private static String encode(byte[] salt, byte[] hash) {
		return "$argon2id$v=19$m=" + MEMORY_KIB + ",t=" + ITERATIONS + ",p=" + PARALLELISM + "$" + ENCODER.encodeToString(salt)
				+ "$" + ENCODER.encodeToString(hash);
	}

}
