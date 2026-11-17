package com.beone.api.codef.application;

import java.util.Objects;

/**
 * 계정 연결 결과.
 *
 * @param connection 연결 뒤 상태
 * @param created 연결이 없던 사용자의 최초 연결이면 참(201), 이미 연결된 사용자의 재연결이면 거짓(200)
 */
public record CodefConnectResult(CodefConnectionView connection, boolean created) {

	public CodefConnectResult {
		Objects.requireNonNull(connection, "connection");
	}

}
