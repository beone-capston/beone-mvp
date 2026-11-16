package com.beone.api.order.persistence;

import java.time.LocalDate;
import java.time.LocalTime;

import com.beone.api.order.application.PaymentMethod;
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
 * {@code planned_spends} 테이블 한 행(Flyway {@code V3__orders_planned_spends.sql}). 고치지 않고 통째로 교체한다.
 */
@Entity
@Table(name = "planned_spends")
public class PlannedSpendEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "planned_spend_id")
	private Long plannedSpendId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Column(name = "planned_date", nullable = false, updatable = false)
	private LocalDate plannedDate;

	@Column(name = "planned_time", updatable = false)
	private LocalTime plannedTime;

	@Column(name = "amount_won", nullable = false, updatable = false)
	private Long amountWon;

	@Column(name = "merchant_class", nullable = false, length = 64, updatable = false)
	private String merchantClass;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "payment_method", nullable = false, length = 32, updatable = false)
	private PaymentMethod paymentMethod;

	@Column(name = "merchant_name", length = 50, updatable = false)
	private String merchantName;

	protected PlannedSpendEntity() {
	}

	public PlannedSpendEntity(long userId, LocalDate plannedDate, LocalTime plannedTime, long amountWon,
			String merchantClass, PaymentMethod paymentMethod, String merchantName) {
		this.userId = userId;
		this.plannedDate = plannedDate;
		this.plannedTime = plannedTime;
		this.amountWon = amountWon;
		this.merchantClass = merchantClass;
		this.paymentMethod = paymentMethod;
		this.merchantName = merchantName;
	}

	public Long getPlannedSpendId() {
		return this.plannedSpendId;
	}

	public Long getUserId() {
		return this.userId;
	}

	public LocalDate getPlannedDate() {
		return this.plannedDate;
	}

	public LocalTime getPlannedTime() {
		return this.plannedTime;
	}

	public Long getAmountWon() {
		return this.amountWon;
	}

	public String getMerchantClass() {
		return this.merchantClass;
	}

	public PaymentMethod getPaymentMethod() {
		return this.paymentMethod;
	}

	public String getMerchantName() {
		return this.merchantName;
	}

}
