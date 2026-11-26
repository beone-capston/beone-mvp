package com.beone.api.codef.application;

/**
 * 시험용: 다른 패키지의 통합 시험이 connectedId 암호문 행을 직접 넣을 수 있게 한다. 값은 합성이다.
 */
public final class ConnectedIdCiphers {

	private ConnectedIdCiphers() {
	}

	/**
	 * 서비스와 같은 방식으로 암호화한다.
	 * @param key 32바이트 키
	 * @param userId 장부 ID
	 * @param connectedId 합성 connectedId
	 * @return 암호문
	 */
	public static String encrypt(byte[] key, long userId, String connectedId) {
		return new ConnectedIdCipher(key).encrypt(userId, connectedId);
	}

}
