package com.beone.api.payment.persistence;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 모의 결제 저장소. 조회는 항상 사용자 ID와 함께 한다(D-09).
 */
public interface SimulatedPaymentRepository extends JpaRepository<SimulatedPaymentEntity, Long> {

	/** 이 사용자의 모의 결제 하나를 찾는다. 다른 사용자의 결제는 찾지 않는다. */
	Optional<SimulatedPaymentEntity> findBySimulatedPaymentIdAndUserId(Long simulatedPaymentId, Long userId);

	/**
	 * 위와 같되 행에 쓰기 잠금을 건다. 같은 결제의 취소 요청을 차례로 처리해 취소 합계가 승인 금액을 넘지 않게 한다. 트랜잭션 안에서만 부른다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT p FROM SimulatedPaymentEntity p WHERE p.simulatedPaymentId = :id AND p.userId = :userId")
	Optional<SimulatedPaymentEntity> findForUpdate(@Param("id") Long simulatedPaymentId, @Param("userId") Long userId);

}
