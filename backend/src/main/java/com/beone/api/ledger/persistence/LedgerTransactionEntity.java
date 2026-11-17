package com.beone.api.ledger.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.beone.api.ledger.application.AttributionStatus;
import com.beone.api.ledger.application.LedgerEntryType;
import com.beone.api.ledger.application.LedgerSource;
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
 * {@code ledger_transactions} 테이블 한 행(Flyway {@code V8__simulated_payments_ledger.sql}). 원장 행은 남긴 뒤 고치지 않는다.
 *
 * <p>사용자·보유 카드·모의 결제는 ID 값으로만 참조하고 다른 패키지 엔티티에 연관을 두지 않는다.
 */
@Entity
@Table(name = "ledger_transactions")
public class LedgerTransactionEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "ledger_id")
	private Long ledgerId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Column(name = "user_card_id", nullable = false, updatable = false)
	private Long userCardId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "source", nullable = false, length = 16, updatable = false)
	private LedgerSource source;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "entry_type", nullable = false, length = 16, updatable = false)
	private LedgerEntryType entryType;

	@Column(name = "amount_won", nullable = false, updatable = false)
	private Long amountWon;

	@Column(name = "transaction_date", nullable = false, updatable = false)
	private LocalDate transactionDate;

	@Column(name = "transaction_time", updatable = false)
	private LocalTime transactionTime;

	@Column(name = "merchant_class", nullable = false, length = 64, updatable = false)
	private String merchantClass;

	@Column(name = "payment_method", nullable = false, length = 32, updatable = false)
	private String paymentMethod;

	@Column(name = "transaction_nature", length = 16, updatable = false)
	private String transactionNature;

	@Column(name = "merchant_name", length = 50, updatable = false)
	private String merchantName;

	@Column(name = "original_ledger_id", updatable = false)
	private Long originalLedgerId;

	@Column(name = "simulated_payment_id", updatable = false)
	private Long simulatedPaymentId;

	@Column(name = "attribution_month", nullable = false, length = 7, columnDefinition = "char(7)", updatable = false)
	private String attributionMonth;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "attribution_status", nullable = false, length = 24, updatable = false)
	private AttributionStatus attributionStatus;

	@Column(name = "recorded_at", nullable = false, updatable = false)
	private LocalDateTime recordedAt;

	protected LedgerTransactionEntity() {
	}

	public LedgerTransactionEntity(long userId, long userCardId, LedgerSource source, LedgerEntryType entryType,
			long amountWon, LocalDate transactionDate, LocalTime transactionTime, String merchantClass,
			String paymentMethod, String merchantName, Long originalLedgerId, Long simulatedPaymentId,
			String attributionMonth, AttributionStatus attributionStatus, LocalDateTime recordedAt) {
		this.userId = userId;
		this.userCardId = userCardId;
		this.source = source;
		this.entryType = entryType;
		this.amountWon = amountWon;
		this.transactionDate = transactionDate;
		this.transactionTime = transactionTime;
		this.merchantClass = merchantClass;
		this.paymentMethod = paymentMethod;
		this.merchantName = merchantName;
		this.originalLedgerId = originalLedgerId;
		this.simulatedPaymentId = simulatedPaymentId;
		this.attributionMonth = attributionMonth;
		this.attributionStatus = attributionStatus;
		this.recordedAt = recordedAt;
	}

	public Long getLedgerId() {
		return this.ledgerId;
	}

	public Long getUserId() {
		return this.userId;
	}

	public Long getUserCardId() {
		return this.userCardId;
	}

	public LedgerSource getSource() {
		return this.source;
	}

	public LedgerEntryType getEntryType() {
		return this.entryType;
	}

	public Long getAmountWon() {
		return this.amountWon;
	}

	public LocalDate getTransactionDate() {
		return this.transactionDate;
	}

	public LocalTime getTransactionTime() {
		return this.transactionTime;
	}

	public String getMerchantClass() {
		return this.merchantClass;
	}

	public String getPaymentMethod() {
		return this.paymentMethod;
	}

	public String getTransactionNature() {
		return this.transactionNature;
	}

	public String getMerchantName() {
		return this.merchantName;
	}

	public Long getOriginalLedgerId() {
		return this.originalLedgerId;
	}

	public Long getSimulatedPaymentId() {
		return this.simulatedPaymentId;
	}

	public String getAttributionMonth() {
		return this.attributionMonth;
	}

	public AttributionStatus getAttributionStatus() {
		return this.attributionStatus;
	}

	public LocalDateTime getRecordedAt() {
		return this.recordedAt;
	}

}
