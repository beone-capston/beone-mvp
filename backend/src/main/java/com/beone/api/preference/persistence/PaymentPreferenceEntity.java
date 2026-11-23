package com.beone.api.preference.persistence;

import java.time.LocalDateTime;

import com.beone.api.preference.application.PaymentSelectionMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * {@code payment_preferences} 테이블 한 행(Flyway {@code V6__payment_preferences.sql}). 사용자당 한 행이고 기본 키가 사용자 ID다.
 *
 * <p>사용자와 대표 카드는 ID 값으로만 참조하고 {@code user}·{@code card} 패키지 엔티티에 연관을 두지 않는다. 새 행 저장과 덮어쓰기는
 * {@link PaymentPreferenceRepository#upsert}가 맡는다. 이 엔티티는 조회와 대표 카드 비우기에 쓴다.
 */
@Entity
@Table(name = "payment_preferences")
public class PaymentPreferenceEntity {

	@Id
	@Column(name = "user_id")
	private Long userId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "selection_mode", nullable = false, length = 32)
	private PaymentSelectionMode selectionMode;

	@Column(name = "representative_user_card_id")
	private Long representativeUserCardId;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected PaymentPreferenceEntity() {
	}

	/**
	 * 저장된 설정 한 행을 나타낸다. 새 행 저장은 {@link PaymentPreferenceRepository#upsert}로 한다.
	 * @param userId 사용자 ID
	 * @param selectionMode 카드 선택 방식
	 * @param representativeUserCardId 대표 카드 저장소 번호. 없으면 {@code null}
	 * @param updatedAt 설정 시각(서울)
	 */
	public PaymentPreferenceEntity(long userId, PaymentSelectionMode selectionMode, Long representativeUserCardId,
			LocalDateTime updatedAt) {
		this.userId = userId;
		this.selectionMode = selectionMode;
		this.representativeUserCardId = representativeUserCardId;
		this.updatedAt = updatedAt;
	}

	public Long getUserId() {
		return this.userId;
	}

	public PaymentSelectionMode getSelectionMode() {
		return this.selectionMode;
	}

	public Long getRepresentativeUserCardId() {
		return this.representativeUserCardId;
	}

	public LocalDateTime getUpdatedAt() {
		return this.updatedAt;
	}

	/** 이 카드가 대표 카드인지. */
	public boolean represents(long userCardId) {
		return this.representativeUserCardId != null && this.representativeUserCardId == userCardId;
	}

	/**
	 * 자동 추천 모드에서 대표 카드를 비운다(대표 카드 해제, D-23).
	 * @param now 변경 시각(서울)
	 * @throws IllegalStateException 대표 카드 고정 모드. 고정 모드의 대표 카드는 비울 수 없다
	 */
	public void clearRepresentative(LocalDateTime now) {
		if (this.selectionMode == PaymentSelectionMode.FIXED_REPRESENTATIVE) {
			throw new IllegalStateException("fixed mode requires a representative card");
		}
		this.representativeUserCardId = null;
		this.updatedAt = now;
	}

}
