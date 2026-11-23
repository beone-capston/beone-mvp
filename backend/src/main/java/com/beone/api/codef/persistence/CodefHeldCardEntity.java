package com.beone.api.codef.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * CODEF 보유카드로 만든 보유 카드의 대조 정보({@code codef_held_cards}, 관리자 판단 P8·Q1). 보유 카드 자체는 {@code user_cards}에 있다.
 */
@Entity
@Table(name = "codef_held_cards")
public class CodefHeldCardEntity {

	@Id
	@Column(name = "user_card_id")
	private Long userCardId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "card_name", nullable = false, length = 255)
	private String cardName;

	@Column(name = "issue_date", length = 64)
	private String issueDate;

	@Column(name = "first_sync_id", nullable = false)
	private Long firstSyncId;

	protected CodefHeldCardEntity() {
	}

	/**
	 * 대조 정보를 만든다.
	 * @param userCardId 방금 만든 보유 카드 ID
	 * @param userId 사용자 ID
	 * @param cardName CODEF 카드명 원문
	 * @param issueDate CODEF 발급일자 원문. 없으면 {@code null}
	 * @param firstSyncId 카드를 만든 동기화 ID
	 */
	public CodefHeldCardEntity(long userCardId, long userId, String cardName, String issueDate, long firstSyncId) {
		this.userCardId = userCardId;
		this.userId = userId;
		this.cardName = cardName;
		this.issueDate = issueDate;
		this.firstSyncId = firstSyncId;
	}

	public Long getUserCardId() {
		return this.userCardId;
	}

	public Long getUserId() {
		return this.userId;
	}

	public String getCardName() {
		return this.cardName;
	}

	public String getIssueDate() {
		return this.issueDate;
	}

	public Long getFirstSyncId() {
		return this.firstSyncId;
	}

}
