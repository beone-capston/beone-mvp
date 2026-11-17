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
 * 성공(200)한 CODEF 동기화 한 번({@code codef_syncs}). 승인 행·실적 참고값·CODEF 카드 대조 정보가 이 행을 가리킨다.
 */
@Entity
@Table(name = "codef_syncs")
public class CodefSyncEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "sync_id")
	private Long syncId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "period_from", nullable = false)
	private LocalDate periodFrom;

	@Column(name = "period_to", nullable = false)
	private LocalDate periodTo;

	@Column(name = "synced_at", nullable = false)
	private LocalDateTime syncedAt;

	protected CodefSyncEntity() {
	}

	/**
	 * 동기화 기록을 만든다.
	 * @param userId 사용자 ID
	 * @param periodFrom 승인내역 조회 시작일
	 * @param periodTo 승인내역 조회 종료일
	 * @param syncedAt 동기화 시각(서울)
	 */
	public CodefSyncEntity(long userId, LocalDate periodFrom, LocalDate periodTo, LocalDateTime syncedAt) {
		this.userId = userId;
		this.periodFrom = periodFrom;
		this.periodTo = periodTo;
		this.syncedAt = syncedAt;
	}

	public Long getSyncId() {
		return this.syncId;
	}

	public Long getUserId() {
		return this.userId;
	}

	public LocalDate getPeriodFrom() {
		return this.periodFrom;
	}

	public LocalDate getPeriodTo() {
		return this.periodTo;
	}

	public LocalDateTime getSyncedAt() {
		return this.syncedAt;
	}

}
