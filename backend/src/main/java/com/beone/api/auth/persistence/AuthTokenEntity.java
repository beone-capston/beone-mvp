package com.beone.api.auth.persistence;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 메일로 보낸 1회용 토큰 행({@code auth_tokens}). 원문은 두지 않고 SHA-256 해시만 둔다.
 *
 * <p>사용하거나 재발급하면 행을 지운다. 만료 시각과 같은 시각이 되면 이미 만료다. 시간 값은 Asia/Seoul 기준 시각이다.
 */
@Entity
@Table(name = "auth_tokens")
public class AuthTokenEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "auth_token_id")
	private Long authTokenId;

	@Column(name = "member_id", nullable = false, length = 36)
	private String memberId;

	@Enumerated(EnumType.STRING)
	@Column(name = "purpose", nullable = false, length = 30)
	private AuthTokenPurpose purpose;

	@Column(name = "token_hash", nullable = false, length = 64, unique = true, columnDefinition = "CHAR(64)")
	private String tokenHash;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	protected AuthTokenEntity() {
	}

	/**
	 * @param memberId 회원 ID
	 * @param purpose 토큰 목적
	 * @param tokenHash 토큰 원문의 SHA-256 16진수
	 * @param createdAt 발급 시각
	 * @param expiresAt 만료 시각
	 */
	public AuthTokenEntity(String memberId, AuthTokenPurpose purpose, String tokenHash, LocalDateTime createdAt,
			LocalDateTime expiresAt) {
		this.memberId = Objects.requireNonNull(memberId, "memberId");
		this.purpose = Objects.requireNonNull(purpose, "purpose");
		this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
		this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
	}

	/** 만료 시각에 도달했거나 지났으면(같은 시각 포함) 만료다. */
	public boolean isExpiredAt(LocalDateTime now) {
		return !now.isBefore(this.expiresAt);
	}

	public String getMemberId() {
		return this.memberId;
	}

	public AuthTokenPurpose getPurpose() {
		return this.purpose;
	}

	public String getTokenHash() {
		return this.tokenHash;
	}

	public LocalDateTime getCreatedAt() {
		return this.createdAt;
	}

	public LocalDateTime getExpiresAt() {
		return this.expiresAt;
	}

	@Override
	public String toString() {
		return "AuthTokenEntity[memberId=" + this.memberId + ", purpose=" + this.purpose + ", tokenHash=<hidden>]";
	}

}
