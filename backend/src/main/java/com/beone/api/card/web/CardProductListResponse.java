package com.beone.api.card.web;

import java.time.LocalDate;
import java.util.List;

import com.beone.api.card.application.CardProductSummary;

/**
 * {@code GET /api/v1/card-products}의 {@code data}.
 *
 * @param asOf 규칙 유효성 판단 날짜
 * @param items 상품 목록. 상품 ID 문자열 오름차순
 */
public record CardProductListResponse(LocalDate asOf, List<CardProductSummary> items) {

	public CardProductListResponse {
		items = List.copyOf(items);
	}

}
