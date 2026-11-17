package com.beone.api.monthlystate.persistence;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code monthly_state_bucket_usages} 한 행. 월간 상태 하나의 한도 묶음 사용액이며 {@code seq}가 요청 순서다.
 */
@Entity
@Table(name = "monthly_state_bucket_usages")
public class BucketUsageEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "bucket_usage_id")
	private Long bucketUsageId;

	@Column(name = "monthly_state_id", nullable = false, updatable = false)
	private Long monthlyStateId;

	@Column(name = "seq", nullable = false, updatable = false)
	private int seq;

	@Column(name = "limit_bucket_id", nullable = false, length = 64, updatable = false)
	private String limitBucketId;

	@Column(name = "used_won", nullable = false, updatable = false)
	private long usedWon;

	@Column(name = "period_from", nullable = false, updatable = false)
	private LocalDate periodFrom;

	@Column(name = "period_to", nullable = false, updatable = false)
	private LocalDate periodTo;

	protected BucketUsageEntity() {
	}

	public BucketUsageEntity(long monthlyStateId, int seq, String limitBucketId, long usedWon, LocalDate periodFrom,
			LocalDate periodTo) {
		this.monthlyStateId = monthlyStateId;
		this.seq = seq;
		this.limitBucketId = limitBucketId;
		this.usedWon = usedWon;
		this.periodFrom = periodFrom;
		this.periodTo = periodTo;
	}

	public Long getMonthlyStateId() {
		return this.monthlyStateId;
	}

	public int getSeq() {
		return this.seq;
	}

	public String getLimitBucketId() {
		return this.limitBucketId;
	}

	public long getUsedWon() {
		return this.usedWon;
	}

	public LocalDate getPeriodFrom() {
		return this.periodFrom;
	}

	public LocalDate getPeriodTo() {
		return this.periodTo;
	}

}
