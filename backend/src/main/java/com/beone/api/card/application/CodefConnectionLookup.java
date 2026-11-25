package com.beone.api.card.application;

import com.beone.api.user.application.CurrentUser;

/**
 * 현재 사용자의 CODEF 연결 여부를 묻는 조회 포트(docs/api/cards.md GET /api/v1/me {@code codefConnected}, #136).
 *
 * <p>codef가 보유 카드에 의존하므로(CODEF 동기화가 보유 카드를 만든다) 순환을 피하려고 인터페이스는 보유 카드 쪽에 두고 구현은 codef
 * 쪽에 둔다. 구현 빈이 없으면 {@link #NONE}을 써서 연결이 없는 것으로 본다.
 */
public interface CodefConnectionLookup {

	/** codef 구현이 없을 때 쓰는 기본 구현. 항상 연결 없음이다. */
	CodefConnectionLookup NONE = user -> false;

	/**
	 * @param user 현재 사용자
	 * @return CODEF 연결 행이 있으면 {@code true}. 상태가 {@code RECONNECT_REQUIRED}여도 연결은 있는 것이다
	 */
	boolean isConnected(CurrentUser user);

}
