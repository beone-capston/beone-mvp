package com.beone.api.payment.persistence;

import java.time.LocalDateTime;

import com.beone.api.payment.application.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * {@code simulated_payments} 테이블 한 행(Flyway {@code V8__simulated_payments_ledger.sql}). 승인 때 정한 응답 본문은
 * {@code documentJson}에 두고, 상태와 취소 합계만 취소마다 바꾼다. 사용자·주문·보유 카드는 ID 값으로만 참조한다.
 */
@Entity
@Table(name = "simulated_payments")
public class SimulatedPaymentEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "simulated_payment_id")
	private Long simulatedPaymentId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Column(name = "order_id", nullable = false, updatable = false)
	private Long orderId;

	@Column(name = "user_card_id", nullable = false, updatable = false)
	private Long userCardId;

	@Column(name = "card_product_id", length = 16, updatable = false)
	private String cardProductId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 24)
	private PaymentStatus status;

	@Column(name = "amount_won", nullable = false, updatable = false)
	private Long amountWon;

	@Column(name = "cancelled_won", nullable = false)
	private Long cancelledWon;

	@Column(name = "approved_at", nullable = false, updatable = false)
	private LocalDateTime approvedAt;

	@Column(name = "approval_ledger_id")
	private Long approvalLedgerId;

	@Column(name = "benefit_service_ids", nullable = false, length = 1024, updatable = false)
	private String benefitServiceIds;

	@Column(name = "document_json", columnDefinition = "mediumtext")
	private String documentJson;

	protected SimulatedPaymentEntity() {
	}

	public SimulatedPaymentEntity(long userId, long orderId, long userCardId, String cardProductId,
			PaymentStatus status, long amountWon, LocalDateTime approvedAt, String benefitServiceIds) {
		this.userId = userId;
		this.orderId = orderId;
		this.userCardId = userCardId;
		this.cardProductId = cardProductId;
		this.status = status;
		this.amountWon = amountWon;
		this.cancelledWon = 0L;
		this.approvedAt = approvedAt;
		this.benefitServiceIds = benefitServiceIds;
	}

	/** 원장 승인 행을 연결한다. 승인한 결제만 부른다. */
	public void attachApprovalLedger(long ledgerId) {
		this.approvalLedgerId = ledgerId;
	}

	/** 결제 ID를 받은 뒤 응답 본문을 채운다. */
	public void attachDocument(String documentJson) {
		this.documentJson = documentJson;
	}

	/**
	 * 취소 금액을 더하고 상태를 부분·전액 취소로 바꾼다. 검사는 호출자가 먼저 한다.
	 * @param amountWon 이번 취소 금액
	 */
	public void cancel(long amountWon) {
		long total = this.cancelledWon + amountWon;
		if (amountWon < 1 || total > this.amountWon || this.approvalLedgerId == null) {
			throw new IllegalStateException("cancellation exceeds the approved amount");
		}
		this.cancelledWon = total;
		this.status = (total == this.amountWon) ? PaymentStatus.CANCELLED : PaymentStatus.PARTIALLY_CANCELLED;
	}

	/** 남은 취소 가능 금액. 실패한 결제는 0이다. */
	public long cancellableWon() {
		return (this.status == PaymentStatus.FAILED) ? 0 : this.amountWon - this.cancelledWon;
	}

	public Long getSimulatedPaymentId() {
		return this.simulatedPaymentId;
	}

	public Long getUserId() {
		return this.userId;
	}

	public Long getOrderId() {
		return this.orderId;
	}

	public Long getUserCardId() {
		return this.userCardId;
	}

	public String getCardProductId() {
		return this.cardProductId;
	}

	public PaymentStatus getStatus() {
		return this.status;
	}

	public Long getAmountWon() {
		return this.amountWon;
	}

	public Long getCancelledWon() {
		return this.cancelledWon;
	}

	public LocalDateTime getApprovedAt() {
		return this.approvedAt;
	}

	public Long getApprovalLedgerId() {
		return this.approvalLedgerId;
	}

	public String getBenefitServiceIds() {
		return this.benefitServiceIds;
	}

	public String getDocumentJson() {
		return this.documentJson;
	}

}
