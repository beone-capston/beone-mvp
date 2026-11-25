package com.beone.api.ledger.application;

/**
 * 원장 조회의 {@code userCardId}가 없거나, 형식이 틀렸거나, 해제했거나, 다른 사용자의 카드다. 값은 메시지에 담지 않는다.
 */
public class LedgerCardNotFoundException extends RuntimeException {

	public LedgerCardNotFoundException() {
		super(null, null, false, false);
	}

}
