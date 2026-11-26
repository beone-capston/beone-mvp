package com.beone.api.card.persistence;

import java.time.LocalDateTime;

import com.beone.api.card.application.ConfirmationMethod;
import com.beone.api.card.application.HoldingSource;
import com.beone.api.card.application.MappingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * {@code user_cards} 테이블 한 행(Flyway {@code V4__user_cards.sql}). 해제해도 지우지 않고 {@code releasedAt}을 남긴다.
 *
 * <p>사용자는 {@code user_id} 값으로만 참조하고 {@code user} 패키지 엔티티에 연관을 두지 않는다. 같은 상품 중복을 막는 생성 열
 * {@code active_product_id}는 DB만 쓰므로 매핑하지 않는다.
 *
 * <p>바뀐 열만 갱신한다({@link DynamicUpdate}). 이름 변경과 해제가 겹쳐도 이름 변경이 읽어 둔 {@code released_at = NULL}로 해제를
 * 덮어써 카드가 되살아나지 않게 한다.
 */
@Entity
@Table(name = "user_cards")
@DynamicUpdate
public class UserCardEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "user_card_id")
	private Long userCardId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Column(name = "display_name", nullable = false, length = 30)
	private String displayName;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "holding_source", nullable = false, length = 16, updatable = false)
	private HoldingSource holdingSource;

	@Column(name = "card_product_id", length = 16)
	private String cardProductId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "mapping_status", length = 16)
	private MappingStatus mappingStatus;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "confirmation_method", length = 32)
	private ConfirmationMethod confirmationMethod;

	@Column(name = "confirmed_at")
	private LocalDateTime confirmedAt;

	@Column(name = "candidate_card_product_id", length = 16)
	private String candidateCardProductId;

	@Column(name = "mapping_changed_by_user", nullable = false)
	private boolean mappingChangedByUser;

	@Column(name = "excluded_from_recommendation", nullable = false)
	private boolean excludedFromRecommendation;

	@Column(name = "registered_at", nullable = false, updatable = false)
	private LocalDateTime registeredAt;

	@Column(name = "released_at")
	private LocalDateTime releasedAt;

	protected UserCardEntity() {
	}

	/**
	 * 수동 등록 카드를 만든다. 매핑 값은 {@link #changeMapping}과 같은 규칙으로 채운다.
	 * @param userId 소유 사용자 ID
	 * @param displayName 표시 이름
	 * @param mappingStatus 매핑 상태
	 * @param cardProductId 카드 상품 ID. {@code USER_CONFIRMED}가 아니면 호출자가 {@code null}을 넘긴다(이 메서드는 검사하지 않고 그대로 저장한다)
	 * @param confirmationMethod 확인 방법. {@code UNMAPPED}이면 호출자가 {@code null}을 넘긴다
	 * @param confirmedAt 확인 시각. {@code UNMAPPED}이면 호출자가 {@code null}을 넘긴다
	 * @param registeredAt 등록 시각
	 * @return 저장 전 수동 등록 카드
	 */
	public static UserCardEntity manual(long userId, String displayName, MappingStatus mappingStatus,
			String cardProductId, ConfirmationMethod confirmationMethod, LocalDateTime confirmedAt,
			LocalDateTime registeredAt) {
		UserCardEntity entity = new UserCardEntity();
		entity.userId = userId;
		entity.displayName = displayName;
		entity.holdingSource = HoldingSource.MANUAL;
		entity.cardProductId = cardProductId;
		entity.mappingStatus = mappingStatus;
		entity.confirmationMethod = confirmationMethod;
		entity.confirmedAt = confirmedAt;
		entity.excludedFromRecommendation = false;
		entity.registeredAt = registeredAt;
		return entity;
	}

	/**
	 * CODEF 보유카드 조회로 얻은 카드를 만든다(D-15, #129). 후보 상품이 있으면 {@code CANDIDATE}, 없으면 {@code UNMAPPED}이고
	 * 확정 상품 ID는 어느 쪽이든 없다(D-15 2026-11-26 변경, #268).
	 * @param userId 소유 사용자 ID
	 * @param displayName 조회된 카드 이름
	 * @param candidateCardProductId 이름 사전으로 찾은 후보 상품 ID. 없으면 {@code null}
	 * @param registeredAt 등록 시각
	 * @return 저장 전 {@code CANDIDATE} 또는 {@code UNMAPPED} 카드
	 */
	public static UserCardEntity codef(long userId, String displayName, String candidateCardProductId,
			LocalDateTime registeredAt) {
		UserCardEntity entity = new UserCardEntity();
		entity.userId = userId;
		entity.displayName = displayName;
		entity.holdingSource = HoldingSource.CODEF;
		entity.mappingStatus = (candidateCardProductId == null) ? MappingStatus.UNMAPPED : MappingStatus.CANDIDATE;
		entity.candidateCardProductId = candidateCardProductId;
		entity.excludedFromRecommendation = false;
		entity.registeredAt = registeredAt;
		return entity;
	}

	/**
	 * 기존 {@code UNMAPPED} 카드에 CODEF 후보 상품을 둔다(#268). 적용 조건(CODEF 출처, 후보 없음, 사용자 변경 없음)은 호출자가
	 * 확인한다. 확인 방법·확인 시각은 비워 둔다.
	 * @param candidateCardProductId 후보 상품 ID
	 */
	public void proposeCandidate(String candidateCardProductId) {
		this.mappingStatus = MappingStatus.CANDIDATE;
		this.candidateCardProductId = candidateCardProductId;
		this.cardProductId = null;
		this.confirmationMethod = null;
		this.confirmedAt = null;
	}

	/**
	 * 사용자 요청으로 매핑을 바꾼다. 상품 ID는 {@code USER_CONFIRMED}일 때만 둔다. CODEF 후보는 지우고, 사용자가 매핑을 바꿨다고
	 * 표시해 재동기화가 다시 후보를 두지 않게 한다(#268).
	 * @param mappingStatus 새 매핑 상태
	 * @param cardProductId 카드 상품 ID. {@code USER_CONFIRMED}가 아니면 호출자가 {@code null}을 넘긴다(이 메서드는 검사하지 않고 그대로 저장한다)
	 * @param confirmationMethod 확인 방법. {@code UNMAPPED}이면 호출자가 {@code null}을 넘긴다
	 * @param confirmedAt 확인 시각. {@code UNMAPPED}이면 호출자가 {@code null}을 넘긴다
	 */
	public void changeMapping(MappingStatus mappingStatus, String cardProductId,
			ConfirmationMethod confirmationMethod, LocalDateTime confirmedAt) {
		this.mappingStatus = mappingStatus;
		this.cardProductId = cardProductId;
		this.confirmationMethod = confirmationMethod;
		this.confirmedAt = confirmedAt;
		this.candidateCardProductId = null;
		this.mappingChangedByUser = true;
	}

	public void rename(String displayName) {
		this.displayName = displayName;
	}

	public void changeExclusion(boolean excludedFromRecommendation) {
		this.excludedFromRecommendation = excludedFromRecommendation;
	}

	public void release(LocalDateTime releasedAt) {
		this.releasedAt = releasedAt;
	}

	public Long getUserCardId() {
		return this.userCardId;
	}

	public Long getUserId() {
		return this.userId;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	public HoldingSource getHoldingSource() {
		return this.holdingSource;
	}

	public String getCardProductId() {
		return this.cardProductId;
	}

	public MappingStatus getMappingStatus() {
		return this.mappingStatus;
	}

	public ConfirmationMethod getConfirmationMethod() {
		return this.confirmationMethod;
	}

	public LocalDateTime getConfirmedAt() {
		return this.confirmedAt;
	}

	public String getCandidateCardProductId() {
		return this.candidateCardProductId;
	}

	public boolean isMappingChangedByUser() {
		return this.mappingChangedByUser;
	}

	public boolean isExcludedFromRecommendation() {
		return this.excludedFromRecommendation;
	}

	public LocalDateTime getRegisteredAt() {
		return this.registeredAt;
	}

	public LocalDateTime getReleasedAt() {
		return this.releasedAt;
	}

}
