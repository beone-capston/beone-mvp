package com.beone.api.monthlystate.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

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

	// 전월 실적을 입력할 때 참고한 CODEF 실적 참고값의 스냅샷(V17, D-16). previousMonthCodefFetchedAt이 있으면 참고 입력이다.
	@Column(name = "previous_month_codef_reference_id")
	private Long previousMonthCodefReferenceId;

	@Column(name = "previous_month_codef_sync_id")
	private Long previousMonthCodefSyncId;

	@Column(name = "previous_month_codef_won")
	private Long previousMonthCodefWon;

	@Column(name = "previous_month_codef_from")
	private LocalDate previousMonthCodefFrom;

	@Column(name = "previous_month_codef_to")
	private LocalDate previousMonthCodefTo;

	@Column(name = "previous_month_codef_fetched_at")
	private LocalDateTime previousMonthCodefFetchedAt;

	// 전월 실적을 입력할 때 참고한 CODEF 승인 합계 추정값의 스냅샷(V18, #289). previousMonthInferredComputedAt이 있으면 참고 입력이다.
	@Column(name = "previous_month_inferred_won")
	private Long previousMonthInferredWon;

	@Column(name = "previous_month_inferred_sync_id")
	private Long previousMonthInferredSyncId;

	@Column(name = "previous_month_inferred_computed_at")
	private LocalDateTime previousMonthInferredComputedAt;

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
		// 교체는 참고 기록도 지운다. 이번 입력이 참고 입력이면 호출자가 이어서 referPreviousMonthToCodef·referPreviousMonthToInferred를 부른다
		this.previousMonthCodefReferenceId = null;
		this.previousMonthCodefSyncId = null;
		this.previousMonthCodefWon = null;
		this.previousMonthCodefFrom = null;
		this.previousMonthCodefTo = null;
		this.previousMonthCodefFetchedAt = null;
		this.previousMonthInferredWon = null;
		this.previousMonthInferredSyncId = null;
		this.previousMonthInferredComputedAt = null;
	}

	/**
	 * 이번 전월 실적 입력이 참고한 CODEF 값을 기록한다. 전월 실적 값이 있을 때만 부른다.
	 * @param referenceId 참고값 ID
	 * @param syncId 그 참고값을 저장한 동기화 ID
	 * @param won 그때의 현재이용금액. 해석하지 못했으면 {@code null}
	 * @param from 반영기간 시작일
	 * @param to 반영기간 종료일
	 * @param fetchedAt 그 참고값의 조회 시각
	 */
	public void referPreviousMonthToCodef(long referenceId, long syncId, Long won, LocalDate from, LocalDate to,
			LocalDateTime fetchedAt) {
		if (this.previousMonthWon == null) {
			throw new IllegalStateException("a CODEF reference needs a previous month value");
		}
		this.previousMonthCodefReferenceId = referenceId;
		this.previousMonthCodefSyncId = syncId;
		this.previousMonthCodefWon = won;
		this.previousMonthCodefFrom = Objects.requireNonNull(from, "from");
		this.previousMonthCodefTo = Objects.requireNonNull(to, "to");
		this.previousMonthCodefFetchedAt = Objects.requireNonNull(fetchedAt, "fetchedAt");
	}

	/**
	 * 전월 실적을 CODEF 승인 합계 추정값을 보고 입력했다고 기록한다(#289). {@link #replace} 뒤에 부른다.
	 * @param won 그때의 추정 합계
	 * @param syncId 산출 근거 동기화 ID
	 * @param computedAt 산출(확인 저장) 시각
	 * @throws IllegalStateException 전월 실적 값이 없는 경우
	 */
	public void referPreviousMonthToInferred(long won, long syncId, LocalDateTime computedAt) {
		if (this.previousMonthWon == null) {
			throw new IllegalStateException("an inferred reference needs a previous month value");
		}
		this.previousMonthInferredWon = won;
		this.previousMonthInferredSyncId = syncId;
		this.previousMonthInferredComputedAt = Objects.requireNonNull(computedAt, "computedAt");
	}

	public Long getPreviousMonthInferredWon() {
		return this.previousMonthInferredWon;
	}

	public Long getPreviousMonthInferredSyncId() {
		return this.previousMonthInferredSyncId;
	}

	public LocalDateTime getPreviousMonthInferredComputedAt() {
		return this.previousMonthInferredComputedAt;
	}

	public Long getPreviousMonthCodefReferenceId() {
		return this.previousMonthCodefReferenceId;
	}

	public Long getPreviousMonthCodefSyncId() {
		return this.previousMonthCodefSyncId;
	}

	public Long getPreviousMonthCodefWon() {
		return this.previousMonthCodefWon;
	}

	public LocalDate getPreviousMonthCodefFrom() {
		return this.previousMonthCodefFrom;
	}

	public LocalDate getPreviousMonthCodefTo() {
		return this.previousMonthCodefTo;
	}

	public LocalDateTime getPreviousMonthCodefFetchedAt() {
		return this.previousMonthCodefFetchedAt;
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
