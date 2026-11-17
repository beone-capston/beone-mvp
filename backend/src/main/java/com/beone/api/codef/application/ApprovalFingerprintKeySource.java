package com.beone.api.codef.application;

import java.util.Optional;

/**
 * 승인번호 HMAC 키 공급(결정 22, 2026-11-18 사용자 직접 결정 P6). 운영 구현은 프로세스 환경 변수
 * {@value EnvironmentApprovalFingerprintKeySource#VARIABLE}에서 읽고, 테스트는 합성 키를 주입한다. local·demo에도 고정 기본값은 없다.
 * 키 회전(이전 키 병행 비교)은 구현하지 않았다.
 */
@FunctionalInterface
public interface ApprovalFingerprintKeySource {

	/**
	 * HMAC-SHA256 키. 없거나 형식이 틀리면 비어 있다.
	 * @return 32바이트 키
	 */
	Optional<byte[]> key();

}
