package com.beone.api.order.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 주문 저장소. 조회는 항상 사용자 ID와 함께 한다(D-09).
 */
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

	/** 이 사용자의 주문 하나를 찾는다. 다른 사용자의 주문은 찾지 않는다. */
	Optional<OrderEntity> findByOrderIdAndUserId(Long orderId, Long userId);

}
