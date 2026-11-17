package com.beone.api.monthlystate.persistence;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code monthly_state_service_selections} 한 행. 월간 상태 하나에서 고른 택1 서비스이며 {@code seq}가 요청 순서다.
 */
@Entity
@Table(name = "monthly_state_service_selections")
public class ServiceSelectionEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "service_selection_id")
	private Long serviceSelectionId;

	@Column(name = "monthly_state_id", nullable = false, updatable = false)
	private Long monthlyStateId;

	@Column(name = "seq", nullable = false, updatable = false)
	private int seq;

	@Column(name = "service_id", nullable = false, length = 64, updatable = false)
	private String serviceId;

	@Column(name = "effective_from", updatable = false)
	private LocalDate effectiveFrom;

	protected ServiceSelectionEntity() {
	}

	public ServiceSelectionEntity(long monthlyStateId, int seq, String serviceId, LocalDate effectiveFrom) {
		this.monthlyStateId = monthlyStateId;
		this.seq = seq;
		this.serviceId = serviceId;
		this.effectiveFrom = effectiveFrom;
	}

	public Long getMonthlyStateId() {
		return this.monthlyStateId;
	}

	public int getSeq() {
		return this.seq;
	}

	public String getServiceId() {
		return this.serviceId;
	}

	public LocalDate getEffectiveFrom() {
		return this.effectiveFrom;
	}

}
