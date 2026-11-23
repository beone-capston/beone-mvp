package com.beone.api.recommendation.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code recommendations} 테이블 한 행(Flyway {@code V7__recommendations.sql}). 계산 때의 응답 본문을 JSON으로 담는다. 사용자·주문은
 * ID 값으로만 참조한다.
 */
@Entity
@Table(name = "recommendations")
public class RecommendationEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "recommendation_id")
	private Long recommendationId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Column(name = "order_id", nullable = false, updatable = false)
	private Long orderId;

	@Column(name = "order_month", nullable = false, length = 7, columnDefinition = "char(7)", updatable = false)
	private String orderMonth;

	@Column(name = "calculated_at", nullable = false, updatable = false)
	private LocalDateTime calculatedAt;

	@Column(name = "document_json", columnDefinition = "mediumtext")
	private String documentJson;

	protected RecommendationEntity() {
	}

	public RecommendationEntity(long userId, long orderId, String orderMonth, LocalDateTime calculatedAt) {
		this.userId = userId;
		this.orderId = orderId;
		this.orderMonth = orderMonth;
		this.calculatedAt = calculatedAt;
	}

	/** 번호를 받은 뒤 응답 본문을 채운다. */
	public void attachDocument(String documentJson) {
		this.documentJson = documentJson;
	}

	public Long getRecommendationId() {
		return this.recommendationId;
	}

	public Long getUserId() {
		return this.userId;
	}

	public Long getOrderId() {
		return this.orderId;
	}

	public String getOrderMonth() {
		return this.orderMonth;
	}

	public LocalDateTime getCalculatedAt() {
		return this.calculatedAt;
	}

	public String getDocumentJson() {
		return this.documentJson;
	}

}
