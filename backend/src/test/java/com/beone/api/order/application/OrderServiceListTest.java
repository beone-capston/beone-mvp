package com.beone.api.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.beone.api.order.persistence.OrderEntity;
import com.beone.api.order.persistence.OrderRepository;
import com.beone.api.user.application.CurrentUser;
import com.beone.api.user.application.DemoUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 주문 목록 조회(결정 38-5)의 서비스 규칙: 현재 사용자 ID로만 조회하고, 정렬은 생성 시각·주문 번호 내림차순이며, 다음 페이지 여부는 저장소
 * {@code Slice}에서 그대로 옮긴다. 기대값은 docs/api/orders.md 내 주문 목록 조회 절에서 옮겼다.
 */
class OrderServiceListTest {

	private static final CurrentUser USER = new CurrentUser(7L, DemoUser.MY_CARDS);

	private final OrderRepository repository = mock(OrderRepository.class);

	@SuppressWarnings("unchecked")
	private final OrderService service = new OrderService(this.repository, new OrderClock(Clock.systemUTC()),
			mock(ObjectProvider.class));

	@Test
	void listQueriesOnlyTheCurrentUserWithDeterministicOrder() {
		given(this.repository.findByUserId(anyLong(), any())).willReturn(new SliceImpl<>(List.of()));

		this.service.list(USER, 3, 20);

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(this.repository).findByUserId(org.mockito.ArgumentMatchers.eq(7L), pageable.capture());
		assertThat(pageable.getValue()).isEqualTo(PageRequest.of(3, 20,
				Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("orderId"))));
	}

	@Test
	void listMapsTheSliceAndItsNextFlag() {
		OrderEntity entity = new OrderEntity(7L, 4500, LocalDate.of(2026, 11, 3), null, null, "CU_STORE",
				PaymentMethod.DIRECT, OrderStatus.CREATED, LocalDateTime.of(2026, 11, 3, 11, 2, 0));
		ReflectionTestUtils.setField(entity, "orderId", 5002L);
		given(this.repository.findByUserId(anyLong(), any()))
			.willReturn(new SliceImpl<>(List.of(entity), PageRequest.of(0, 1), true));

		OrderPage page = this.service.list(USER, 0, 1);

		assertThat(page.page()).isZero();
		assertThat(page.size()).isOne();
		assertThat(page.hasNext()).isTrue();
		assertThat(page.items()).singleElement().satisfies(order -> {
			assertThat(order.orderId()).isEqualTo("ord-5002");
			assertThat(order.amountWon()).isEqualTo(4500);
			assertThat(order.merchantClass()).isEqualTo("CU_STORE");
			assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
		});
	}

	@Test
	void listRejectsOutOfRangeValuesWithoutQuerying() {
		int[][] invalid = { { -1, 20 }, { 1001, 20 }, { 0, 0 }, { 0, 51 } };
		for (int[] values : invalid) {
			assertThatThrownBy(() -> this.service.list(USER, values[0], values[1]))
				.isInstanceOf(IllegalArgumentException.class);
		}
		verify(this.repository, never()).findByUserId(anyLong(), any());
	}

	@Test
	void boundaryValuesAreAllowed() {
		assertThat(OrderPage.isAllowed(0, 1)).isTrue();
		assertThat(OrderPage.isAllowed(OrderPage.MAX_PAGE, OrderPage.MAX_SIZE)).isTrue();
		assertThat(OrderPage.DEFAULT_PAGE).isZero();
		assertThat(OrderPage.DEFAULT_SIZE).isEqualTo(20);
		assertThat(OrderPage.MAX_SIZE).isEqualTo(50);
		assertThat(OrderPage.MAX_PAGE).isEqualTo(1000);
	}

}
