package com.beone.api.codef.application;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 현재 사용자의 CODEF 연결 상태. connectedId·아이디·비밀번호는 담지 않는다(docs/api/codef.md).
 *
 * @param organization 기관 코드. MVP는 KB국민카드 {@code 0301}뿐이다
 * @param status 연결 상태
 * @param connectedAt 마지막 연결 성공 시각(Asia/Seoul). 연결이 없으면 {@code null}
 * @param lastSyncedAt 마지막 동기화 시각(Asia/Seoul). 동기화한 적이 없으면 {@code null}
 */
public record CodefConnectionView(String organization, CodefConnectionStatus status, LocalDateTime connectedAt,
		LocalDateTime lastSyncedAt) {

	/** KB국민카드 기관 코드. */
	public static final String KB_KOOKMIN_CARD = "0301";

	/** 연결이 없는 사용자의 상태. */
	public static final CodefConnectionView NOT_CONNECTED = new CodefConnectionView(KB_KOOKMIN_CARD,
			CodefConnectionStatus.NOT_CONNECTED, null, null);

	public CodefConnectionView {
		Objects.requireNonNull(organization, "organization");
		Objects.requireNonNull(status, "status");
	}

	/**
	 * 다시 연결해야 하면 참이다.
	 * @return 다시 연결해야 하면 {@code true}
	 */
	public boolean reconnectRequired() {
		return this.status == CodefConnectionStatus.RECONNECT_REQUIRED;
	}

}
