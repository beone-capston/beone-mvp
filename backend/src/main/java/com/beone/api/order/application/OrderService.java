package com.beone.api.order.application;

import com.beone.api.order.persistence.OrderEntity;
import com.beone.api.order.persistence.OrderRepository;
import com.beone.api.user.application.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가상 주문을 만들고 조회한다(docs/api/orders.md). 주문은 수정하지 않는다.
 *
 * <p>가상 포트폴리오 사용자도 주문을 만들 수 있다(docs/api.md 업무 오류 코드). 다른 사용자의 주문은 없는 주문과 같게 다룬다(D-09).
 */
@Service
public class OrderService {

	private final OrderRepository orderRepository;

	private final OrderClock orderClock;

	public OrderService(OrderRepository orderRepository, OrderClock orderClock) {
		this.orderRepository = orderRepository;
		this.orderClock = orderClock;
	}

	/** 현재 사용자의 주문을 {@link OrderStatus#CREATED} 상태로 만든다. */
	@Transactional
	public OrderDetail create(CurrentUser user, NewOrder order) {
		OrderEntity saved = this.orderRepository.save(new OrderEntity(user.userId(), order.amountWon(),
				order.orderDate(), order.orderTime(), order.merchantName(), order.merchantClass(),
				order.paymentMethod(), OrderStatus.CREATED, this.orderClock.now()));
		return toDetail(saved);
	}

	/**
	 * 현재 사용자의 주문 하나를 돌려준다.
	 * @throws OrderNotFoundException 형식이 틀렸거나, 없거나, 다른 사용자의 주문일 때
	 */
	@Transactional(readOnly = true)
	public OrderDetail get(CurrentUser user, String orderId) {
		return OrderId.parse(orderId)
			.flatMap(id -> this.orderRepository.findByOrderIdAndUserId(id, user.userId()))
			.map(OrderService::toDetail)
			.orElseThrow(OrderNotFoundException::new);
	}

	private static OrderDetail toDetail(OrderEntity entity) {
		return new OrderDetail(OrderId.format(entity.getOrderId()), entity.getAmountWon(), entity.getOrderDate(),
				entity.getOrderTime(), entity.getMerchantName(), entity.getMerchantClass(), entity.getPaymentMethod(),
				entity.getStatus(), entity.getCreatedAt());
	}

}
