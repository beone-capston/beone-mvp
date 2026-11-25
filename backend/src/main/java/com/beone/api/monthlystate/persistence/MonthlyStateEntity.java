package com.beone.api.monthlystate.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code monthly_states} 테이블 한 행(Flyway {@code V5__monthly_states.sql}). (보유 카드, 월)마다 하나이며 PUT이 값 전체를
 * 교체한다. 입력하지 않은 값은 {@code null}이다.
 *
 * <p>사용자·보유 카드는 ID 값으로만 참조하고 다른 패키지 엔티티에 연관을 두지 않는다. 한도 묶음 사용액·택1 선택은 별도 테이블이다.
 */
@Entity
@Table(name = "monthly_states")
public class MonthlyStateEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "monthly_state_id")
	private Long monthlyStateId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Column(name = "user_card_id", nullable = false, updatable = false)
	private Long userCardId;

	@Column(name = "state_month", nullable = false, length = 7, columnDefinition = "char(7)", updatable = false)
	private String stateMonth;

	@Column(name = "card_product_id", length = 16)
	private String cardProductId;

	@Column(name = "snapshot_at", nullable = false)
	private LocalDateTime snapshotAt;

	@Column(name = "previous_month_won")
	private Long previousMonthWon;

	@Column(name = "previous_month_from")
	private LocalDate previousMonthFrom;

	@Column(name = "previous_month_to")
	private LocalDate previousMonthTo;

	@Column(name = "other_buckets_used_won")
	private Long otherBucketsUsedWon;

	@Column(name = "other_buckets_from")
	private LocalDate otherBucketsFrom;

	@Column(name = "other_buckets_to")
	private LocalDate otherBucketsTo;

	@Column(name = "registered_on")
	private LocalDate registeredOn;

	@Column(name = "no_service_selection", nullable = false)
	private boolean noServiceSelection;

	@Column(name = "no_selection_effective_from")
	private LocalDate noSelectionEffectiveFrom;

	protected MonthlyStateEntity() {
	}

	public MonthlyStateEntity(long userId, long userCardId, String stateMonth) {
		this.userId = userId;
		this.userCardId = userCardId;
		this.stateMonth = stateMonth;
	}

	/**
	 * 그 달의 값 전체를 바꾼다. 인자 {@code null}은 미입력이다.
	 * @param cardProductId 저장 시점 카드 상품 ID
	 * @param snapshotAt 저장 시각
	 * @param previousMonthWon 전월 실적
	 * @param previousMonthFrom 전월 실적 기간 시작일
	 * @param previousMonthTo 전월 실적 기간 종료일
	 * @param otherBucketsUsedWon 나머지 한도 묶음 사용액
	 * @param otherBucketsFrom 나머지 사용액 기간 시작일
	 * @param otherBucketsTo 나머지 사용액 기간 종료일
	 * @param registeredOn 카드 등록일
	 * @param noServiceSelection 택1 선택 없음을 입력했으면 참
	 * @param noSelectionEffectiveFrom 선택 없음 적용일
	 */
	public void replace(String cardProductId, LocalDateTime snapshotAt, Long previousMonthWon,
			LocalDate previousMonthFrom, LocalDate previousMonthTo, Long otherBucketsUsedWon, LocalDate otherBucketsFrom,
			LocalDate otherBucketsTo, LocalDate registeredOn, boolean noServiceSelection,
			LocalDate noSelectionEffectiveFrom) {
		this.cardProductId = cardProductId;
		this.snapshotAt = snapshotAt;
		this.previousMonthWon = previousMonthWon;
		this.previousMonthFrom = previousMonthFrom;
		this.previousMonthTo = previousMonthTo;
		this.otherBucketsUsedWon = otherBucketsUsedWon;
		this.otherBucketsFrom = otherBucketsFrom;
		this.otherBucketsTo = otherBucketsTo;
		this.registeredOn = registeredOn;
		this.noServiceSelection = noServiceSelection;
		this.noSelectionEffectiveFrom = noSelectionEffectiveFrom;
	}

	public Long getMonthlyStateId() {
		return this.monthlyStateId;
	}

	public Long getUserId() {
		return this.userId;
	}

	public Long getUserCardId() {
		return this.userCardId;
	}

	public String getStateMonth() {
		return this.stateMonth;
	}

	public String getCardProductId() {
		return this.cardProductId;
	}

	public LocalDateTime getSnapshotAt() {
		return this.snapshotAt;
	}

	public Long getPreviousMonthWon() {
		return this.previousMonthWon;
	}

	public LocalDate getPreviousMonthFrom() {
		return this.previousMonthFrom;
	}

	public LocalDate getPreviousMonthTo() {
		return this.previousMonthTo;
	}

	public Long getOtherBucketsUsedWon() {
		return this.otherBucketsUsedWon;
	}

	public LocalDate getOtherBucketsFrom() {
		return this.otherBucketsFrom;
	}

	public LocalDate getOtherBucketsTo() {
		return this.otherBucketsTo;
	}

	public LocalDate getRegisteredOn() {
		return this.registeredOn;
	}

	public boolean isNoServiceSelection() {
		return this.noServiceSelection;
	}

	public LocalDate getNoSelectionEffectiveFrom() {
		return this.noSelectionEffectiveFrom;
	}

}
