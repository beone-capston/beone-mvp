package com.beone.api.auth.persistence;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 로그인 실패 한 건({@code auth_login_failures}). 이메일 원문 대신 SHA-256 해시를 담는다.
 */
@Entity
@Table(name = "auth_login_failures")
public class LoginFailureEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "auth_login_failure_id")
	private Long authLoginFailureId;

	@Column(name = "email_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
	private String emailHash;

	@Column(name = "failed_at", nullable = false)
	private LocalDateTime failedAt;

	protected LoginFailureEntity() {
	}

	/**
	 * 실패 기록을 만든다.
	 * @param emailHash 정규화 이메일의 SHA-256 16진수
	 * @param failedAt 실패 시각
	 */
	public LoginFailureEntity(String emailHash, LocalDateTime failedAt) {
		this.emailHash = Objects.requireNonNull(emailHash, "emailHash");
		this.failedAt = Objects.requireNonNull(failedAt, "failedAt");
	}

	public String getEmailHash() {
		return this.emailHash;
	}

	public LocalDateTime getFailedAt() {
		return this.failedAt;
	}

}
