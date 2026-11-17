package com.beone.api.order.web;

import com.beone.api.common.web.ApiResponse;
import com.beone.api.order.application.OrderDetail;
import com.beone.api.order.application.OrderService;
import com.beone.api.user.application.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주문 생성·상세 조회 API(docs/api/orders.md). 현재 사용자는 {@link CurrentUser} 인자로만 받는다(D-09).
 *
 * <p>{@code local}·{@code demo} 프로필에서만 등록한다. 그 밖의 환경에서는 경로가 없어 404 {@code NOT_FOUND}다(시연 사용자 API와 같음).
 */
@RestController
@Profile({ "local", "demo" })
@RequestMapping("/api/v1/me/orders")
public class OrderController {

	private final OrderService orderService;

	public OrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<CreatedOrderResponse> createOrder(CurrentUser currentUser,
			@Valid @RequestBody CreateOrderRequest request) {
		return ApiResponse.of(CreatedOrderResponse.from(this.orderService.create(currentUser, request.toNewOrder())));
	}

	@GetMapping("/{orderId}")
	public ApiResponse<OrderDetailResponse> getOrder(CurrentUser currentUser, @PathVariable String orderId) {
		OrderDetail order = this.orderService.get(currentUser, orderId);
		return ApiResponse.of(OrderDetailResponse.from(order,
				this.orderService.latestRecommendationId(currentUser, order.orderId())));
	}

}
