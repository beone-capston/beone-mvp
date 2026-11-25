package com.beone.api.codef.application;

import java.util.Base64;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * connectedId 암호화 키를 프로세스 환경 변수에서 읽는다. {@code application*.yml}에는 두지 않는다(2026-11-18 사용자 직접 결정 P5).
 *
 * <p>값은 Base64로 적은 32바이트 키다. 없거나 형식이 틀리면 키 없음이며 연결 등록이 안전하게 실패한다. 값은 로그에 남기지 않는다.
 * 실키 공급·설정은 실연동 승인 때 별도로 한다.
 */
@Component
public class EnvironmentConnectedIdKeySource implements ConnectedIdKeySource {

	/** 환경 변수 이름. */
	public static final String VARIABLE = "BEONE_CODEF_CONNECTED_ID_KEY";

	private static final Logger log = LoggerFactory.getLogger(EnvironmentConnectedIdKeySource.class);

	private static final int KEY_BYTES = 32;

	@Override
	public Optional<byte[]> key() {
		return parse(System.getenv(VARIABLE));
	}

	static Optional<byte[]> parse(String value) {
		if (value == null || value.isEmpty()) {
			return Optional.empty();
		}
		try {
			byte[] key = Base64.getDecoder().decode(value);
			if (key.length == KEY_BYTES) {
				return Optional.of(key);
			}
		}
		catch (IllegalArgumentException ex) {
			// 아래에서 형식 오류로 남긴다. 예외 문구에 값이 담길 수 있어 쓰지 않는다
		}
		log.warn("CODEF connectedId key is malformed variable={}", VARIABLE);
		return Optional.empty();
	}

}
