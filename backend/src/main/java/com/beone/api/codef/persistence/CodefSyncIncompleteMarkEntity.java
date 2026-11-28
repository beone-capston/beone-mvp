package com.beone.api.codef.persistence;

import com.beone.api.codef.sync.CodefIncompleteReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 동기화 한 번에서 저장하지 않은 승인 행(불완전 행)의 카드·사용월·사유별 건수(V18, #289). 원문·금액·승인번호는 담지 않는다.
 */
@Entity
@Table(name = "codef_sync_incomplete_marks")
public class CodefSyncIncompleteMarkEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "codef_sync_incomplete_mark_id")
	private Long codefSyncIncompleteMarkId;

	@Column(name = "sync_id", nullable = false)
	private Long syncId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	// 보유 카드에 붙지 않은 행이면 NULL
	@Column(name = "user_card_id")
	private Long userCardId;

	// 사용일자를 해석하지 못했으면 NULL(그 동기화 기간 전체에 걸림)
	@Column(name = "used_month", length = 7)
	private String usedMonth;

	@Enumerated(EnumType.STRING)
	@Column(name = "reason", nullable = false, length = 40)
	private CodefIncompleteReason reason;

	@Column(name = "row_count", nullable = false)
	private Integer rowCount;

	protected CodefSyncIncompleteMarkEntity() {
	}

	public CodefSyncIncompleteMarkEntity(long syncId, long userId, Long userCardId, String usedMonth,
			CodefIncompleteReason reason, int rowCount) {
		this.syncId = syncId;
		this.userId = userId;
		this.userCardId = userCardId;
		this.usedMonth = usedMonth;
		this.reason = reason;
		this.rowCount = rowCount;
	}

	public Long getSyncId() {
		return this.syncId;
	}

	public Long getUserId() {
		return this.userId;
	}

	public Long getUserCardId() {
		return this.userCardId;
	}

	public String getUsedMonth() {
		return this.usedMonth;
	}

	public CodefIncompleteReason getReason() {
		return this.reason;
	}

	public Integer getRowCount() {
		return this.rowCount;
	}

}
