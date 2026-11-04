package com.beone.api.benefit.evaluation;

/**
 * fixture의 {@code 카드:이름} 키(예: {@code 09174:Great})를 나누는 도구.
 */
final class CardScopedKey {

	private CardScopedKey() {
	}

	/**
	 * {@code 카드:이름} 형식의 키를 비어 있지 않은 두 부분으로 나눈다.
	 */
	static String[] split(String key) {
		String[] parts = key.split(":", -1);
		if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
			throw new IllegalArgumentException("expected '<card>:<name>' but got '" + key + "'");
		}
		return parts;
	}

}
