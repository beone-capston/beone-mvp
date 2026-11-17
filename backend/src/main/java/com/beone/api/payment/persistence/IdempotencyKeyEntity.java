package com.beone.api.payment.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code idempotency_keys} 테이블 한 행(Flyway {@code V8__simulated_payments_ledger.sql}). 사용자·키마다 하나이고, 성공 응답 본문과
 * 요청 지문을 담는다. 요청 원문은 담지 않는다.
 */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKeyEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "idempotency_key_id")
	private Long idempotencyKeyId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Column(name = "idempotency_key", nullable = false, length = 255, updatable = false)
	private String idempotencyKey;

	@Column(name = "fingerprint", nullable = false, length = 64, columnDefinition = "char(64)", updatable = false)
	private String fingerprint;

	@Column(name = "response_json", nullable = false, columnDefinition = "mediumtext", updatable = false)
	private String responseJson;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected IdempotencyKeyEntity() {
	}

	public IdempotencyKeyEntity(long userId, String idempotencyKey, String fingerprint, String responseJson,
			LocalDateTime createdAt) {
		this.userId = userId;
		this.idempotencyKey = idempotencyKey;
		this.fingerprint = fingerprint;
		this.responseJson = responseJson;
		this.createdAt = createdAt;
	}

	public Long getIdempotencyKeyId() {
		return this.idempotencyKeyId;
	}

	public Long getUserId() {
		return this.userId;
	}

	public String getIdempotencyKey() {
		return this.idempotencyKey;
	}

	public String getFingerprint() {
		return this.fingerprint;
	}

	public String getResponseJson() {
		return this.responseJson;
	}

	public LocalDateTime getCreatedAt() {
		return this.createdAt;
	}

}
