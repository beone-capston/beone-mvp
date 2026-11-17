package com.beone.api.order.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.beone.api.order.application.OrderStatus;
import com.beone.api.order.application.PaymentMethod;
import org.junit.jupiter.api.Test;

/**
 * 주문 상태 전이 {@code CREATED → PAID → CANCELLED}(docs/api/orders.md {@code status}). 다른 방향은 막는다.
 */
class OrderEntityTransitionTest {

	@Test
	void paidThenCancelledKeepsThePaymentId() {
		OrderEntity order = created();

		order.markPaid("pay-7001");
		assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
		assertThat(order.getSimulatedPaymentId()).isEqualTo("pay-7001");

		order.markCancelled();
		assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
		assertThat(order.getSimulatedPaymentId()).isEqualTo("pay-7001");
	}

	@Test
	void otherTransitionsAreRejected() {
		OrderEntity order = created();
		assertThatThrownBy(order::markCancelled).isInstanceOf(IllegalStateException.class);
		order.markPaid("pay-1");
		assertThatThrownBy(() -> order.markPaid("pay-2")).isInstanceOf(IllegalStateException.class);
		order.markCancelled();
		assertThatThrownBy(() -> order.markPaid("pay-3")).isInstanceOf(IllegalStateException.class);
	}

	private static OrderEntity created() {
		return new OrderEntity(1L, 10000, LocalDate.of(2026, 11, 3), null, null, "STARBUCKS_STORE", PaymentMethod.DIRECT,
				OrderStatus.CREATED, LocalDateTime.of(2026, 11, 3, 10, 0));
	}

}
