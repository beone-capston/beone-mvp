package com.beone.api.codef.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 카드사 조회 실적(참고) 하나({@code codef_performance_references}, D-16). 계산에 쓰지 않는다. 확인하지 못한 값은 {@code null}이다.
 */
@Entity
@Table(name = "codef_performance_references")
public class CodefPerformanceReferenceEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "codef_performance_reference_id")
	private Long codefPerformanceReferenceId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "user_card_id")
	private Long userCardId;

	@Column(name = "sync_id", nullable = false)
	private Long syncId;

	@Column(name = "card_name", length = 255)
	private String cardName;

	@Column(name = "period_from")
	private LocalDate periodFrom;

	@Column(name = "period_to")
	private LocalDate periodTo;

	@Column(name = "current_use_won")
	private Long currentUseWon;

	@Column(name = "fetched_at", nullable = false)
	private LocalDateTime fetchedAt;

	protected CodefPerformanceReferenceEntity() {
	}

	/**
	 * 실적 참고값을 만든다. 확인하지 못한 값은 {@code null}로 넘긴다.
	 * @param userId 사용자 ID
	 * @param userCardId 보유 카드 ID. 카드에 붙지 않았으면 {@code null}
	 * @param syncId 동기화 ID
	 * @param cardName CODEF 카드명 원문. 없으면 {@code null}
	 * @param periodFrom 이용금액 반영기간 시작일
	 * @param periodTo 이용금액 반영기간 종료일
	 * @param currentUseWon 현재이용금액
	 * @param fetchedAt 조회 시각(서울)
	 */
	public CodefPerformanceReferenceEntity(long userId, Long userCardId, long syncId, String cardName,
			LocalDate periodFrom, LocalDate periodTo, Long currentUseWon, LocalDateTime fetchedAt) {
		this.userId = userId;
		this.userCardId = userCardId;
		this.syncId = syncId;
		this.cardName = cardName;
		this.periodFrom = periodFrom;
		this.periodTo = periodTo;
		this.currentUseWon = currentUseWon;
		this.fetchedAt = fetchedAt;
	}

	public Long getCodefPerformanceReferenceId() {
		return this.codefPerformanceReferenceId;
	}

	public Long getUserId() {
		return this.userId;
	}

	public Long getUserCardId() {
		return this.userCardId;
	}

	public Long getSyncId() {
		return this.syncId;
	}

	public String getCardName() {
		return this.cardName;
	}

	public LocalDate getPeriodFrom() {
		return this.periodFrom;
	}

	public LocalDate getPeriodTo() {
		return this.periodTo;
	}

	public Long getCurrentUseWon() {
		return this.currentUseWon;
	}

	public LocalDateTime getFetchedAt() {
		return this.fetchedAt;
	}

}
