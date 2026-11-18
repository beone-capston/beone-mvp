package com.beone.api.auth.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 같은 이메일의 로그인 잠금 상태({@code auth_login_throttles}). 이메일 원문 대신 SHA-256 해시를 키로 쓴다.
 *
 * <p>행은 {@link LoginThrottleRepository#createOrLock}로만 만들고 잠금 읽기 뒤 바꾼다. 잠금 시각이 {@code null}이거나 지났으면
 * 잠기지 않은 상태다.
 */
@Entity
@Table(name = "auth_login_throttles")
public class LoginThrottleEntity {

	@Id
	@Column(name = "email_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
	private String emailHash;

	@Column(name = "locked_until")
	private LocalDateTime lockedUntil;

	protected LoginThrottleEntity() {
	}

	public String getEmailHash() {
		return this.emailHash;
	}

	/** 잠금이 풀리는 시각. 잠근 적이 없으면 {@code null}. */
	public LocalDateTime getLockedUntil() {
		return this.lockedUntil;
	}

	/** 지정한 시각까지 잠근다. */
	public void lockUntil(LocalDateTime until) {
		this.lockedUntil = until;
	}

	/** 잠금을 푼다. */
	public void unlock() {
		this.lockedUntil = null;
	}

}
