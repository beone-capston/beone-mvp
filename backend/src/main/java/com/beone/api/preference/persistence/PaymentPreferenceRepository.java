package com.beone.api.preference.persistence;

import java.time.LocalDateTime;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 결제 전 설정 저장소. 사용자 ID가 기본 키라 다른 사용자의 설정은 찾을 수 없다(D-09).
 */
public interface PaymentPreferenceRepository extends JpaRepository<PaymentPreferenceEntity, Long> {

	/**
	 * 사용자 행({@code users})에 쓰기 잠금을 건다. 트랜잭션 안에서만 부른다.
	 *
	 * <p>설정 변경과 대표 카드 보호(보유 카드 추천 제외·해제)를 사용자 단위로 차례로 처리한다. 설정 행은 없을 수 있어, 없는 행을
	 * 잠그면 생기는 간격 잠금끼리의 교착 대신 항상 있는 사용자 행을 잠근다. {@code user} 패키지 코드는 쓰지 않고 행만 잠근다.
	 * @param userId 사용자 ID
	 * @return 잠근 사용자 ID
	 */
	@Query(value = "SELECT user_id FROM users WHERE user_id = :userId FOR UPDATE", nativeQuery = true)
	Long lockUser(@Param("userId") long userId);

	/**
	 * 보유 카드 행({@code user_cards})에 쓰기 잠금을 건다. 트랜잭션 안에서만 부른다.
	 *
	 * <p>설정 저장의 외래 키 검사는 대표 카드 행에 공유 잠금을 건다. 보유 카드 변경은 카드 행 → 사용자 행 순으로 잠그므로, 설정 변경도
	 * 같은 순서로 카드 행을 먼저 잠가야 교착이 생기지 않는다. {@code card} 패키지 코드는 쓰지 않고 행만 잠근다.
	 * @param userCardId 보유 카드 저장소 번호
	 * @return 잠근 카드 번호. 행이 없으면 {@code null}
	 */
	@Query(value = "SELECT user_card_id FROM user_cards WHERE user_card_id = :userCardId FOR UPDATE",
			nativeQuery = true)
	Long lockUserCard(@Param("userCardId") long userCardId);

	/**
	 * 이 사용자의 설정 행을 쓰기 잠금으로 찾는다. {@link #lockUser} 뒤에 불러, 앞 요청이 커밋한 최신 값을 읽는다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<PaymentPreferenceEntity> findForUpdateByUserId(Long userId);

	/**
	 * 설정 행을 넣거나 덮어쓴다. 처음 설정하는 요청 두 개가 겹쳐도 기본 키 충돌 없이 나중 값이 남는다.
	 * @param userId 사용자 ID
	 * @param selectionMode 카드 선택 방식 이름
	 * @param representativeUserCardId 대표 카드 저장소 번호. 없으면 {@code null}
	 * @param updatedAt 설정 시각(서울)
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			INSERT INTO payment_preferences (user_id, selection_mode, representative_user_card_id, updated_at)
			VALUES (:userId, :selectionMode, :representativeUserCardId, :updatedAt) AS incoming
			ON DUPLICATE KEY UPDATE selection_mode = incoming.selection_mode,
			    representative_user_card_id = incoming.representative_user_card_id,
			    updated_at = incoming.updated_at
			""", nativeQuery = true)
	void upsert(@Param("userId") long userId, @Param("selectionMode") String selectionMode,
			@Param("representativeUserCardId") Long representativeUserCardId,
			@Param("updatedAt") LocalDateTime updatedAt);

}
