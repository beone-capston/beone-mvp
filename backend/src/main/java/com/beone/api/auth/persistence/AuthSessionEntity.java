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
 * {@code auth_sessions} 테이블 한 행. 서버 세션 하나이며 세션 ID 원문 대신 SHA-256 해시만 담는다(Flyway {@code V9__auth_sessions.sql}).
 *
 * <p>시간 값은 Asia/Seoul 기준 시각이다. 미사용 만료는 사용할 때마다 늘어나고, 절대 만료가 있으면 그것을 넘지 않는다. 익명 세션은 절대
 * 만료가 없다({@code null}, 2026-11-18 사용자 직접 결정). 회원 세션의 절대 만료는 로그인 단위에서 채운다.
 */
@Entity
@Table(name = "auth_sessions")
public class AuthSessionEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "auth_session_id")
	private Long authSessionId;

	@Column(name = "session_hash", nullable = false, length = 64, unique = true, columnDefinition = "CHAR(64)")
	private String sessionHash;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "last_used_at", nullable = false)
	private LocalDateTime lastUsedAt;

	@Column(name = "idle_expires_at", nullable = false)
	private LocalDateTime idleExpiresAt;

	@Column(name = "absolute_expires_at")
	private LocalDateTime absoluteExpiresAt;

	protected AuthSessionEntity() {
	}

	/**
	 * 새 세션 행을 만든다.
	 * @param sessionHash 세션 ID의 SHA-256 해시(16진수 64자)
	 * @param createdAt 발급 시각
	 * @param idleExpiresAt 미사용 만료 시각(절대 만료가 있으면 그 이하)
	 * @param absoluteExpiresAt 절대 만료 시각, 없으면 {@code null}
	 */
	public AuthSessionEntity(String sessionHash, LocalDateTime createdAt, LocalDateTime idleExpiresAt,
			LocalDateTime absoluteExpiresAt) {
		this.sessionHash = Objects.requireNonNull(sessionHash, "sessionHash");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
		this.lastUsedAt = createdAt;
		this.idleExpiresAt = Objects.requireNonNull(idleExpiresAt, "idleExpiresAt");
		this.absoluteExpiresAt = absoluteExpiresAt;
	}

	/**
	 * 사용 시각과 미사용 만료 시각을 갱신한다.
	 * @param usedAt 사용 시각
	 * @param newIdleExpiresAt 새 미사용 만료 시각(절대 만료가 있으면 그 이하)
	 */
	public void touch(LocalDateTime usedAt, LocalDateTime newIdleExpiresAt) {
		this.lastUsedAt = Objects.requireNonNull(usedAt, "usedAt");
		this.idleExpiresAt = Objects.requireNonNull(newIdleExpiresAt, "newIdleExpiresAt");
	}

	public Long getAuthSessionId() {
		return this.authSessionId;
	}

	public String getSessionHash() {
		return this.sessionHash;
	}

	public LocalDateTime getCreatedAt() {
		return this.createdAt;
	}

	public LocalDateTime getLastUsedAt() {
		return this.lastUsedAt;
	}

	public LocalDateTime getIdleExpiresAt() {
		return this.idleExpiresAt;
	}

	/** 절대 만료 시각. 익명 세션은 {@code null}이다. */
	public LocalDateTime getAbsoluteExpiresAt() {
		return this.absoluteExpiresAt;
	}

}
