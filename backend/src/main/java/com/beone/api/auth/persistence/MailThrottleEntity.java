package com.beone.api.auth.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 같은 주소의 마지막 메일 요청 시각({@code auth_mail_throttles}). 이메일 원문 대신 SHA-256 해시를 키로 쓴다.
 *
 * <p>행은 {@link MailThrottleRepository#createOrLock}로만 만들고, 잠금 읽기 뒤 {@link #accept}로 시각을 바꾼다. 시각이
 * {@code null}이면 같은 트랜잭션이 방금 만든 행이다.
 */
@Entity
@Table(name = "auth_mail_throttles")
public class MailThrottleEntity {

	@Id
	@Column(name = "email_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
	private String emailHash;

	@Column(name = "last_requested_at")
	private LocalDateTime lastRequestedAt;

	protected MailThrottleEntity() {
	}

	public String getEmailHash() {
		return this.emailHash;
	}

	/**
	 * 마지막 접수 시각. 방금 만든 행이면 {@code null}.
	 * @return 마지막 접수 시각, 방금 만든 행이면 {@code null}
	 */
	public LocalDateTime getLastRequestedAt() {
		return this.lastRequestedAt;
	}

	/**
	 * 요청을 접수한 시각으로 기록한다.
	 * @param requestedAt 요청을 접수한 시각
	 */
	public void accept(LocalDateTime requestedAt) {
		this.lastRequestedAt = requestedAt;
	}

}
