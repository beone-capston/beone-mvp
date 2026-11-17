package com.beone.api.auth.application;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 이메일 정규화와 형식 검사(docs/api/auth.md 「값 검증」).
 *
 * <p>앞뒤 공백을 지우고 소문자로 통일한다. 점과 {@code +태그}는 유지한다. 형식은 공백·{@code @}가 없는 로컬부, {@code @}, 점이 있는
 * 도메인이며 정규화 뒤 최대 254자다.
 */
public final class EmailAddresses {

	public static final int MAX_LENGTH = 254;

	private static final Pattern FORMAT = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@.]+");

	private EmailAddresses() {
	}

	/**
	 * @param raw 요청 값
	 * @return 정규화한 이메일
	 */
	public static String normalize(String raw) {
		return raw.strip().toLowerCase(Locale.ROOT);
	}

	/**
	 * @param raw 요청 값, {@code null} 가능
	 * @return 정규화했을 때 형식과 길이가 맞으면 {@code true}
	 */
	public static boolean isValid(String raw) {
		if (raw == null) {
			return false;
		}
		String normalized = normalize(raw);
		return normalized.length() <= MAX_LENGTH && FORMAT.matcher(normalized).matches();
	}

}
