package com.beone.api.codef.application;

/**
 * CODEF 연결 상태(docs/api/codef.md).
 */
public enum CodefConnectionStatus {

	/** 연결한 적이 없거나 연결 행이 없다. */
	NOT_CONNECTED,

	/** 연결 성공 뒤 쓸 수 있는 상태다. */
	CONNECTED,

	/** 연결은 있지만 다시 연결해야 조회할 수 있다. 이 상태로 바꾸는 실코드 대응은 아직 없다(관리자 판단 P2). */
	RECONNECT_REQUIRED

}
