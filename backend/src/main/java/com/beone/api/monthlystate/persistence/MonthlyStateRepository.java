package com.beone.api.monthlystate.persistence;

import java.time.LocalDateTime;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * 월간 상태 저장소. 조회는 항상 사용자 ID와 함께 한다(D-09).
 */
public interface MonthlyStateRepository extends JpaRepository<MonthlyStateEntity, Long> {

	/**
	 * 이 사용자의 이 카드·이 달 상태를 찾는다.
	 * @param userId 사용자 ID
	 * @param userCardId 보유 카드 ID
	 * @param stateMonth 대상 달({@code yyyy-MM})
	 * @return 월간 상태, 없으면 빈 값
	 */
	Optional<MonthlyStateEntity> findByUserIdAndUserCardIdAndStateMonth(Long userId, Long userCardId,
			String stateMonth);

	/**
	 * 이 카드·이 달의 행이 없으면 빈 행을 넣고, 있으면 아무것도 바꾸지 않는다. 교체 전에 같은 트랜잭션에서 부른다.
	 *
	 * <p>행이 아직 없을 때도 쓰기 잠금이 걸릴 행을 먼저 만들어, 같은 달의 첫 교체 요청 두 개가 유니크 제약
	 * {@code uk_monthly_states_card_month} 위반으로 실패하지 않고 차례로 처리되게 한다. 넣은 빈 행은 같은 트랜잭션의 교체가 덮어쓰므로
	 * 다른 요청에 보이지 않는다.
	 * @param userId 사용자 ID
	 * @param userCardId 보유 카드 ID
	 * @param stateMonth 대상 달({@code yyyy-MM})
	 * @param snapshotAt 빈 행에 넣을 저장 시각
	 * @return 넣었으면 1, 이미 있었으면 0(MySQL 영향 행 수)
	 */
	@Modifying(flushAutomatically = true)
	@Query(nativeQuery = true, value = """
			INSERT INTO monthly_states (user_id, user_card_id, state_month, snapshot_at, no_service_selection)
			VALUES (:userId, :userCardId, :stateMonth, :snapshotAt, FALSE)
			ON DUPLICATE KEY UPDATE monthly_state_id = monthly_state_id""")
	int insertIfAbsent(Long userId, Long userCardId, String stateMonth, LocalDateTime snapshotAt);

	/**
	 * 위와 같되 교체용으로 행에 쓰기 잠금을 건다. 같은 달을 바꾸는 요청을 차례로 처리한다. 트랜잭션 안에서만 부른다.
	 * @param userId 사용자 ID
	 * @param userCardId 보유 카드 ID
	 * @param stateMonth 대상 달({@code yyyy-MM})
	 * @return 잠근 월간 상태, 없으면 빈 값
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<MonthlyStateEntity> findForUpdateByUserIdAndUserCardIdAndStateMonth(Long userId, Long userCardId,
			String stateMonth);

}
