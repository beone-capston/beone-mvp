package com.beone.api.codef.application;

/**
 * 재연결이 필요해 동기화하지 않는다. 409 {@code CODEF_RECONNECT_REQUIRED}다.
 */
public class CodefReconnectRequiredException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public CodefReconnectRequiredException() {
		super(null, null, false, false);
	}

}
