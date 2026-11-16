package com.beone.api.order.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.beone.api.order.application.OrderStatus;
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
 * {@code orders} 테이블 한 행(Flyway {@code V3__orders_planned_spends.sql}). 주문은 만든 뒤 입력값을 고치지 않는다.
 *
 * <p>사용자는 {@code user_id} 값으로만 참조하고 {@code user} 패키지 엔티티에 연관을 두지 않는다.
 */
@Entity
@Table(name = "orders")
public class OrderEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "order_id")
	private Long orderId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Column(name = "amount_won", nullable = false, updatable = false)
	private Long amountWon;

	@Column(name = "order_date", nullable = false, updatable = false)
	private LocalDate orderDate;

	@Column(name = "order_time", updatable = false)
	private LocalTime orderTime;

	@Column(name = "merchant_name", length = 50, updatable = false)
	private String merchantName;

	@Column(name = "merchant_class", nullable = false, length = 64, updatable = false)
	private String merchantClass;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "payment_method", nullable = false, length = 32, updatable = false)
	private PaymentMethod paymentMethod;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 16)
	private OrderStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected OrderEntity() {
	}

	public OrderEntity(long userId, long amountWon, LocalDate orderDate, LocalTime orderTime, String merchantName,
			String merchantClass, PaymentMethod paymentMethod, OrderStatus status, LocalDateTime createdAt) {
		this.userId = userId;
		this.amountWon = amountWon;
		this.orderDate = orderDate;
		this.orderTime = orderTime;
		this.merchantName = merchantName;
		this.merchantClass = merchantClass;
		this.paymentMethod = paymentMethod;
		this.status = status;
		this.createdAt = createdAt;
	}

	public Long getOrderId() {
		return this.orderId;
	}

	public Long getUserId() {
		return this.userId;
	}

	public Long getAmountWon() {
		return this.amountWon;
	}

	public LocalDate getOrderDate() {
		return this.orderDate;
	}

	public LocalTime getOrderTime() {
		return this.orderTime;
	}

	public String getMerchantName() {
		return this.merchantName;
	}

	public String getMerchantClass() {
		return this.merchantClass;
	}

	public PaymentMethod getPaymentMethod() {
		return this.paymentMethod;
	}

	public OrderStatus getStatus() {
		return this.status;
	}

	public LocalDateTime getCreatedAt() {
		return this.createdAt;
	}

}
