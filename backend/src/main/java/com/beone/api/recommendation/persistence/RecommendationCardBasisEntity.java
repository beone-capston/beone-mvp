package com.beone.api.recommendation.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code recommendation_card_bases} 한 행. 계산 때 엔진에 넘긴 보유 카드 한 장의 비교 대상 여부와 월간 상태 스냅샷 시각이다.
 */
@Entity
@Table(name = "recommendation_card_bases")
public class RecommendationCardBasisEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "card_basis_id")
	private Long cardBasisId;

	@Column(name = "recommendation_id", nullable = false, updatable = false)
	private Long recommendationId;

	@Column(name = "user_card_id", nullable = false, updatable = false)
	private Long userCardId;

	@Column(name = "mapping_status", length = 16, updatable = false)
	private String mappingStatus;

	@Column(name = "rule_card_product_id", length = 16, updatable = false)
	private String ruleCardProductId;

	@Column(name = "excluded_by_user", nullable = false, updatable = false)
	private boolean excludedByUser;

	@Column(name = "monthly_snapshot_at", updatable = false)
	private LocalDateTime monthlySnapshotAt;

	protected RecommendationCardBasisEntity() {
	}

	public RecommendationCardBasisEntity(long recommendationId, long userCardId, String mappingStatus,
			String ruleCardProductId, boolean excludedByUser, LocalDateTime monthlySnapshotAt) {
		this.recommendationId = recommendationId;
		this.userCardId = userCardId;
		this.mappingStatus = mappingStatus;
		this.ruleCardProductId = ruleCardProductId;
		this.excludedByUser = excludedByUser;
		this.monthlySnapshotAt = monthlySnapshotAt;
	}

	public Long getRecommendationId() {
		return this.recommendationId;
	}

	public Long getUserCardId() {
		return this.userCardId;
	}

	public String getMappingStatus() {
		return this.mappingStatus;
	}

	public String getRuleCardProductId() {
		return this.ruleCardProductId;
	}

	public boolean isExcludedByUser() {
		return this.excludedByUser;
	}

	public LocalDateTime getMonthlySnapshotAt() {
		return this.monthlySnapshotAt;
	}

}
