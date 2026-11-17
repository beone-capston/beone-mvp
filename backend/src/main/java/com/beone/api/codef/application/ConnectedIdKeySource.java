package com.beone.api.codef.application;

import java.util.Optional;

/**
 * connectedId 암호화 키 공급(2026-11-18 사용자 직접 결정 P5). 운영 구현은 프로세스 환경 변수
 * {@value EnvironmentConnectedIdKeySource#VARIABLE}에서 읽고, 테스트는 합성 키를 주입한다. local·demo에도 고정 기본값은 없다.
 */
@FunctionalInterface
public interface ConnectedIdKeySource {

	/**
	 * AES-256 키. 없거나 형식이 틀리면 비어 있다.
	 * @return 32바이트 키
	 */
	Optional<byte[]> key();

}
