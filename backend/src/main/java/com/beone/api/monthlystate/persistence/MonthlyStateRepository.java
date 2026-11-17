package com.beone.api.monthlystate.persistence;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/**
 * 월간 상태 저장소. 조회는 항상 사용자 ID와 함께 한다(D-09).
 */
public interface MonthlyStateRepository extends JpaRepository<MonthlyStateEntity, Long> {

	/** 이 사용자의 이 카드·이 달 상태를 찾는다. */
	Optional<MonthlyStateEntity> findByUserIdAndUserCardIdAndStateMonth(Long userId, Long userCardId,
			String stateMonth);

	/**
	 * 위와 같되 교체용으로 행에 쓰기 잠금을 건다. 같은 달을 바꾸는 요청을 차례로 처리한다. 트랜잭션 안에서만 부른다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<MonthlyStateEntity> findForUpdateByUserIdAndUserCardIdAndStateMonth(Long userId, Long userCardId,
			String stateMonth);

}
