package com.beone.api.order.persistence;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 주문 저장소. 조회는 항상 사용자 ID와 함께 한다(D-09).
 */
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

	/**
	 * 이 사용자의 주문 하나를 찾는다. 다른 사용자의 주문은 찾지 않는다.
	 * @param orderId 주문 번호
	 * @param userId 사용자 ID
	 * @return 주문, 없으면 빈 값
	 */
	Optional<OrderEntity> findByOrderIdAndUserId(Long orderId, Long userId);

	/**
	 * 이 사용자의 주문만 한 페이지 찾는다. 조건에 {@code user_id}가 항상 들어가므로 다른 사용자의 주문은 담기지 않는다(결정 38-5).
	 * {@link Slice}는 페이지 크기보다 한 건 더 읽어 다음 페이지가 있는지 정한다(전체 건수는 세지 않는다).
	 * @param userId 사용자 ID
	 * @param pageable 페이지 번호·크기·정렬
	 * @return 주문 한 페이지
	 */
	Slice<OrderEntity> findByUserId(Long userId, Pageable pageable);

	/**
	 * 위 단건 조회와 같되 행에 쓰기 잠금을 건다. 같은 주문의 모의 승인 요청을 차례로 처리해 한 번만 승인한다. 트랜잭션 안에서만 부른다.
	 * @param orderId 주문 번호
	 * @param userId 사용자 ID
	 * @return 잠근 주문, 없으면 빈 값
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT o FROM OrderEntity o WHERE o.orderId = :orderId AND o.userId = :userId")
	Optional<OrderEntity> findForUpdate(@Param("orderId") Long orderId, @Param("userId") Long userId);

}
