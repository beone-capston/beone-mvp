package com.beone.api.codef.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.beone.api.codef.sync.CodefLedgerRowKind;
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
 * 받아들인 CODEF 승인 행 하나({@code codef_approval_rows}). 도메인 원장({@code ledger_transactions})과 연결하지 않는다(결정 22 R8).
 *
 * <p>승인번호는 HMAC 지문만 담는다(결정 20 R2, P6). 중복 판단 키는 카드명·지문·사용일자·사용일시·부호 포함 금액이다.
 */
@Entity
@Table(name = "codef_approval_rows")
public class CodefApprovalRowEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "codef_approval_row_id")
	private Long codefApprovalRowId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "user_card_id", nullable = false)
	private Long userCardId;

	@Column(name = "sync_id", nullable = false)
	private Long syncId;

	@Column(name = "card_name", nullable = false, length = 255)
	private String cardName;

	@Column(name = "approval_fingerprint", nullable = false, length = 64)
	private String approvalFingerprint;

	@Column(name = "used_date", nullable = false)
	private LocalDate usedDate;

	@Column(name = "used_time", nullable = false)
	private LocalTime usedTime;

	@Column(name = "signed_amount", nullable = false)
	private Long signedAmount;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "row_kind", nullable = false, length = 32)
	private CodefLedgerRowKind rowKind;

	@Column(name = "recorded_at", nullable = false)
	private LocalDateTime recordedAt;

	@Column(name = "kind_changed_at")
	private LocalDateTime kindChangedAt;

	protected CodefApprovalRowEntity() {
	}

	/**
	 * 승인 행을 만든다.
	 * @param userId 사용자 ID
	 * @param userCardId 보유 카드 ID
	 * @param syncId 처음 기록한 동기화 ID
	 * @param cardName 중복 판단 키의 카드명
	 * @param approvalFingerprint 승인번호 지문
	 * @param usedDate 사용일자
	 * @param usedTime 사용일시
	 * @param signedAmount 부호 포함 이용금액
	 * @param rowKind 행 종류
	 * @param recordedAt 기록 시각
	 */
	public CodefApprovalRowEntity(long userId, long userCardId, long syncId, String cardName,
			String approvalFingerprint, LocalDate usedDate, LocalTime usedTime, long signedAmount,
			CodefLedgerRowKind rowKind, LocalDateTime recordedAt) {
		this.userId = userId;
		this.userCardId = userCardId;
		this.syncId = syncId;
		this.cardName = cardName;
		this.approvalFingerprint = approvalFingerprint;
		this.usedDate = usedDate;
		this.usedTime = usedTime;
		this.signedAmount = signedAmount;
		this.rowKind = rowKind;
		this.recordedAt = recordedAt;
	}

	/**
	 * 정상 승인으로 기록한 행이 재조회에서 Type1 전액 취소로 바뀌었을 때 종류를 바꾼다(CodefRecheck.CANCELED_SINCE_RECORDED).
	 * @param changedAt 바꾼 시각
	 */
	public void markFullyCanceled(LocalDateTime changedAt) {
		this.rowKind = CodefLedgerRowKind.APPROVAL_FULLY_CANCELED;
		this.kindChangedAt = changedAt;
	}

	public Long getCodefApprovalRowId() {
		return this.codefApprovalRowId;
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

	public String getApprovalFingerprint() {
		return this.approvalFingerprint;
	}

	public LocalDate getUsedDate() {
		return this.usedDate;
	}

	public LocalTime getUsedTime() {
		return this.usedTime;
	}

	public Long getSignedAmount() {
		return this.signedAmount;
	}

	public CodefLedgerRowKind getRowKind() {
		return this.rowKind;
	}

	public LocalDateTime getRecordedAt() {
		return this.recordedAt;
	}

	public LocalDateTime getKindChangedAt() {
		return this.kindChangedAt;
	}

	// 지문도 비교용 값이지만 로그에 남길 이유가 없어 빼고 보여 준다
	@Override
	public String toString() {
		return "CodefApprovalRowEntity[id=" + this.codefApprovalRowId + ", userId=" + this.userId + ", rowKind="
				+ this.rowKind + "]";
	}

}
