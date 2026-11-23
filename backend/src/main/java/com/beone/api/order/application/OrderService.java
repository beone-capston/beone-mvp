package com.beone.api.order.application;

import com.beone.api.order.persistence.OrderEntity;
import com.beone.api.order.persistence.OrderRepository;
import com.beone.api.user.application.CurrentUser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가상 주문을 만들고 조회한다(docs/api/orders.md). 주문 입력값은 수정하지 않고, 모의 결제(payment)가 상태 전이만 부른다.
 *
 * <p>가상 포트폴리오 사용자도 주문을 만들 수 있다(docs/api.md 업무 오류 코드). 다른 사용자의 주문은 없는 주문과 같게 다룬다(D-09).
 */
@Service
public class OrderService {

	private final OrderRepository orderRepository;

	private final OrderClock orderClock;

	private final ObjectProvider<LatestRecommendationLookup> latestRecommendationLookup;

	/**
	 * @param orderRepository 주문 저장소
	 * @param orderClock 생성 시각 시계
	 * @param latestRecommendationLookup 마지막 추천 조회 포트. 추천이 주문에 의존하므로 순환을 피하려고 부를 때 찾는다(#120)
	 */
	public OrderService(OrderRepository orderRepository, OrderClock orderClock,
			ObjectProvider<LatestRecommendationLookup> latestRecommendationLookup) {
		this.orderRepository = orderRepository;
		this.orderClock = orderClock;
		this.latestRecommendationLookup = latestRecommendationLookup;
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

	/**
	 * 이 주문으로 마지막에 계산한 추천 ID(docs/api/orders.md {@code latestRecommendationId}, #120). 주문 소유 확인은 {@link #get}이 한다.
	 * @return 추천 외부 ID 또는 계산한 추천이 없으면 {@code null}
	 */
	@Transactional(readOnly = true)
	public String latestRecommendationId(CurrentUser user, String orderId) {
		LatestRecommendationLookup lookup = this.latestRecommendationLookup
			.getIfAvailable(() -> LatestRecommendationLookup.NONE);
		return OrderId.parse(orderId).flatMap(id -> lookup.latestRecommendationId(user, id)).orElse(null);
	}

	/**
	 * 모의 승인 전에 현재 사용자의 주문 행을 쓰기 잠금하고 돌려준다. 같은 주문의 승인 요청은 앞 요청이 커밋할 때까지 기다린다.
	 * 호출자의 트랜잭션 안에서만 부른다.
	 * @throws OrderNotFoundException 형식이 틀렸거나, 없거나, 다른 사용자의 주문일 때
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public OrderDetail lockForPayment(CurrentUser user, String orderId) {
		return toDetail(lockedEntity(user, orderId));
	}

	/**
	 * 잠근 주문을 {@code PAID}로 바꾸고 결제 ID를 연결한다. {@link #lockForPayment}와 같은 트랜잭션에서 부른다.
	 * @param simulatedPaymentId 승인한 결제의 외부 ID
	 * @throws IllegalStateException 주문이 {@code CREATED}가 아닐 때
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void markPaid(CurrentUser user, String orderId, String simulatedPaymentId) {
		lockedEntity(user, orderId).markPaid(simulatedPaymentId);
	}

	/**
	 * 모의 승인을 전액 취소한 주문을 {@code CANCELLED}로 바꾼다. 호출자의 트랜잭션 안에서만 부른다.
	 * @throws IllegalStateException 주문이 {@code PAID}가 아닐 때
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void markCancelled(CurrentUser user, String orderId) {
		lockedEntity(user, orderId).markCancelled();
	}

	private OrderEntity lockedEntity(CurrentUser user, String orderId) {
		return OrderId.parse(orderId)
			.flatMap(id -> this.orderRepository.findForUpdate(id, user.userId()))
			.orElseThrow(OrderNotFoundException::new);
	}

	private static OrderDetail toDetail(OrderEntity entity) {
		return new OrderDetail(OrderId.format(entity.getOrderId()), entity.getAmountWon(), entity.getOrderDate(),
				entity.getOrderTime(), entity.getMerchantName(), entity.getMerchantClass(), entity.getPaymentMethod(),
				entity.getStatus(), entity.getCreatedAt(), entity.getSimulatedPaymentId());
	}

}
