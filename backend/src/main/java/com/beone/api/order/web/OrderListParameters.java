package com.beone.api.order.web;

import java.util.regex.Pattern;

import com.beone.api.order.application.OrderPage;

/**
 * 주문 목록 쿼리 값 검사(docs/api/orders.md 내 주문 목록 조회). 틀리면 {@link InvalidOrderQueryException}에 필드 이름만 담는다.
 *
 * <p>십진 숫자만 받는다. 빈 값, 부호·공백·소수점, 앞자리 0, 같은 이름을 두 번 보낸 값({@code 1,2}로 합쳐짐), 범위 밖은 형식 오류다.
 */
final class OrderListParameters {

	static final String PAGE = "page";

	static final String SIZE = "size";

	// 앞자리 0 없는 십진수. 자릿수를 묶어 int 넘침을 막는다
	private static final Pattern NUMBER = Pattern.compile("0|[1-9][0-9]{0,8}");

	private OrderListParameters() {
	}

	/** 생략하면 {@link OrderPage#DEFAULT_PAGE}. 허용 {@code 0}~{@link OrderPage#MAX_PAGE}. */
	static int page(String value) {
		return parse(value, PAGE, OrderPage.DEFAULT_PAGE, 0, OrderPage.MAX_PAGE);
	}

	/** 생략하면 {@link OrderPage#DEFAULT_SIZE}. 허용 {@code 1}~{@link OrderPage#MAX_SIZE}. */
	static int size(String value) {
		return parse(value, SIZE, OrderPage.DEFAULT_SIZE, 1, OrderPage.MAX_SIZE);
	}

	private static int parse(String value, String field, int defaultValue, int min, int max) {
		if (value == null) {
			return defaultValue;
		}
		if (!NUMBER.matcher(value).matches()) {
			throw new InvalidOrderQueryException(field);
		}
		int number = Integer.parseInt(value);
		if (number < min || number > max) {
			throw new InvalidOrderQueryException(field);
		}
		return number;
	}

}
