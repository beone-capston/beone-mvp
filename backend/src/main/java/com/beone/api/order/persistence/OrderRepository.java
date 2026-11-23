package com.beone.api.order.persistence;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 주문 저장소. 조회는 항상 사용자 ID와 함께 한다(D-09).
 */
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

	/** 이 사용자의 주문 하나를 찾는다. 다른 사용자의 주문은 찾지 않는다. */
	Optional<OrderEntity> findByOrderIdAndUserId(Long orderId, Long userId);

	/**
	 * 위와 같되 행에 쓰기 잠금을 건다. 같은 주문의 모의 승인 요청을 차례로 처리해 한 번만 승인한다. 트랜잭션 안에서만 부른다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT o FROM OrderEntity o WHERE o.orderId = :orderId AND o.userId = :userId")
	Optional<OrderEntity> findForUpdate(@Param("orderId") Long orderId, @Param("userId") Long userId);

}
