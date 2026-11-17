package com.beone.api.payment.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code payment_cancellations} 테이블 한 행(Flyway {@code V8__simulated_payments_ledger.sql}). 남긴 뒤 고치지 않는다.
 */
@Entity
@Table(name = "payment_cancellations")
public class PaymentCancellationEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "cancellation_id")
	private Long cancellationId;

	@Column(name = "simulated_payment_id", nullable = false, updatable = false)
	private Long simulatedPaymentId;

	@Column(name = "amount_won", nullable = false, updatable = false)
	private Long amountWon;

	@Column(name = "cancelled_at", nullable = false, updatable = false)
	private LocalDateTime cancelledAt;

	@Column(name = "ledger_id", nullable = false, updatable = false)
	private Long ledgerId;

	protected PaymentCancellationEntity() {
	}

	public PaymentCancellationEntity(long simulatedPaymentId, long amountWon, LocalDateTime cancelledAt,
			long ledgerId) {
		this.simulatedPaymentId = simulatedPaymentId;
		this.amountWon = amountWon;
		this.cancelledAt = cancelledAt;
		this.ledgerId = ledgerId;
	}

	public Long getCancellationId() {
		return this.cancellationId;
	}

	public Long getSimulatedPaymentId() {
		return this.simulatedPaymentId;
	}

	public Long getAmountWon() {
		return this.amountWon;
	}

	public LocalDateTime getCancelledAt() {
		return this.cancelledAt;
	}

	public Long getLedgerId() {
		return this.ledgerId;
	}

}
