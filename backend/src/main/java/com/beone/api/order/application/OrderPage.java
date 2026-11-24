package com.beone.api.order.application;

import java.util.List;

/**
 * 주문 목록 한 페이지(docs/api/orders.md 내 주문 목록 조회, 결정 38-5). 정렬은 생성 시각 내림차순, 같으면 주문 번호 내림차순이다.
 *
 * @param items 이 페이지의 주문. 마지막 페이지를 지나면 빈 목록
 * @param page 적용한 페이지 번호(0부터)
 * @param size 적용한 페이지 크기
 * @param hasNext 다음 페이지에 주문이 더 있으면 {@code true}
 */
public record OrderPage(List<OrderDetail> items, int page, int size, boolean hasNext) {

	/** 페이지 번호 기본값. */
	public static final int DEFAULT_PAGE = 0;

	/** 페이지 번호 상한. 아주 큰 오프셋 조회를 막는다. */
	public static final int MAX_PAGE = 1000;

	/** 페이지 크기 기본값. */
	public static final int DEFAULT_SIZE = 20;

	/** 페이지 크기 상한. */
	public static final int MAX_SIZE = 50;

	public OrderPage {
		items = List.copyOf(items);
	}

	/**
	 * 허용 범위 안의 페이지 요청인지 확인한다.
	 * @param page 페이지 번호
	 * @param size 페이지 크기
	 * @return {@code 0 <= page <= MAX_PAGE}이고 {@code 1 <= size <= MAX_SIZE}이면 {@code true}
	 */
	public static boolean isAllowed(int page, int size) {
		return page >= 0 && page <= MAX_PAGE && size >= 1 && size <= MAX_SIZE;
	}

}
