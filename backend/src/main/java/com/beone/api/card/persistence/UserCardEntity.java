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
	 * @param cardProductId 카드 상품 ID. 받은 값을 그대로 저장한다
	 * @param confirmationMethod 확인 방법
	 * @param confirmedAt 확인 시각
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
	 * CODEF 보유카드 조회로 얻은 카드를 만든다(D-15, #129). 상품 후보를 정하지 않으므로 항상 {@code UNMAPPED}이고 상품 ID가 없다
	 * (결정 20 R3).
	 * @param userId 소유 사용자 ID
	 * @param displayName 조회된 카드 이름
	 * @param registeredAt 등록 시각
	 * @return 저장 전 {@code UNMAPPED} 카드
	 */
	public static UserCardEntity codef(long userId, String displayName, LocalDateTime registeredAt) {
		UserCardEntity entity = new UserCardEntity();
		entity.userId = userId;
		entity.displayName = displayName;
		entity.holdingSource = HoldingSource.CODEF;
		entity.mappingStatus = MappingStatus.UNMAPPED;
		entity.excludedFromRecommendation = false;
		entity.registeredAt = registeredAt;
		return entity;
	}

	/**
	 * 매핑을 바꾼다. 상품 ID는 {@code USER_CONFIRMED}일 때만 둔다.
	 * @param mappingStatus 새 매핑 상태
	 * @param cardProductId 카드 상품 ID. 받은 값을 그대로 저장한다
	 * @param confirmationMethod 확인 방법
	 * @param confirmedAt 확인 시각
	 */
	public void changeMapping(MappingStatus mappingStatus, String cardProductId,
			ConfirmationMethod confirmationMethod, LocalDateTime confirmedAt) {
		this.mappingStatus = mappingStatus;
		this.cardProductId = cardProductId;
		this.confirmationMethod = confirmationMethod;
		this.confirmedAt = confirmedAt;
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
