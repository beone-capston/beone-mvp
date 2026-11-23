package com.beone.api.ledger.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code ledger_usage_effects} 테이블 한 행(Flyway {@code V8__simulated_payments_ledger.sql}). 승인 행이 버킷 하나에 더하는
 * 사용액이다. 모르면 {@code deductionWon}이 {@code null}이다.
 */
@Entity
@Table(name = "ledger_usage_effects")
public class LedgerUsageEffectEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "usage_effect_id")
	private Long usageEffectId;

	@Column(name = "ledger_id", nullable = false, updatable = false)
	private Long ledgerId;

	@Column(name = "limit_bucket_id", nullable = false, length = 64, updatable = false)
	private String limitBucketId;

	@Column(name = "deduction_won", updatable = false)
	private Long deductionWon;

	protected LedgerUsageEffectEntity() {
	}

	public LedgerUsageEffectEntity(long ledgerId, String limitBucketId, Long deductionWon) {
		this.ledgerId = ledgerId;
		this.limitBucketId = limitBucketId;
		this.deductionWon = deductionWon;
	}

	public Long getUsageEffectId() {
		return this.usageEffectId;
	}

	public Long getLedgerId() {
		return this.ledgerId;
	}

	public String getLimitBucketId() {
		return this.limitBucketId;
	}

	public Long getDeductionWon() {
		return this.deductionWon;
	}

}
