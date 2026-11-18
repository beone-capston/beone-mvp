package com.beone.api.auth.persistence;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 회원 행({@code members}). 시연 사용자({@code users})와 별도다.
 *
 * <p>이메일은 정규화한 값이고, 비밀번호는 Argon2id 해시 문자열만 둔다. 이메일 인증 전이면 {@link #getEmailVerifiedAt()}이
 * {@code null}이다. 시간 값은 Asia/Seoul 기준 시각이다.
 */
@Entity
@Table(name = "members")
public class MemberEntity {

	@Id
	@Column(name = "member_id", nullable = false, length = 36)
	private String memberId;

	@Column(name = "email", nullable = false, length = 254, unique = true)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	@Column(name = "email_verified_at")
	private LocalDateTime emailVerifiedAt;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	protected MemberEntity() {
	}

	/**
	 * 이메일 미인증 회원을 만든다.
	 * @param memberId {@code usr-} 회원 ID
	 * @param email 정규화한 이메일
	 * @param passwordHash Argon2id 해시 문자열
	 * @param createdAt 가입 시각
	 */
	public MemberEntity(String memberId, String email, String passwordHash, LocalDateTime createdAt) {
		this.memberId = Objects.requireNonNull(memberId, "memberId");
		this.email = Objects.requireNonNull(email, "email");
		this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
	}

	/** 이메일 인증을 마친 것으로 기록한다. 이미 인증했으면 처음 시각을 유지한다. */
	/**
	 * 비밀번호 해시를 바꾼다(비밀번호 재설정, U4).
	 * @param newPasswordHash 새 Argon2id 해시 문자열
	 */
	public void changePassword(String newPasswordHash) {
		this.passwordHash = Objects.requireNonNull(newPasswordHash, "newPasswordHash");
	}

	public void markEmailVerified(LocalDateTime verifiedAt) {
		if (this.emailVerifiedAt == null) {
			this.emailVerifiedAt = Objects.requireNonNull(verifiedAt, "verifiedAt");
		}
	}

	public boolean isEmailVerified() {
		return this.emailVerifiedAt != null;
	}

	public String getMemberId() {
		return this.memberId;
	}

	public String getEmail() {
		return this.email;
	}

	public String getPasswordHash() {
		return this.passwordHash;
	}

	public LocalDateTime getEmailVerifiedAt() {
		return this.emailVerifiedAt;
	}

	public LocalDateTime getCreatedAt() {
		return this.createdAt;
	}

	@Override
	public String toString() {
		return "MemberEntity[memberId=" + this.memberId + ", verified=" + isEmailVerified() + "]";
	}

}
