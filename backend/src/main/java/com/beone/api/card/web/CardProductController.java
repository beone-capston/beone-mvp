package com.beone.api.card.web;

import java.time.LocalDate;

import com.beone.api.card.application.CardProductDetail;
import com.beone.api.card.application.CardProductService;
import com.beone.api.common.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 카드 상품 목록·상세 API(docs/api/products.md).
 *
 * <p>상품은 사용자 자원이 아니므로 시연 사용자 헤더를 읽지 않고, 모든 프로필에서 등록한다(결정 27 A1-7).
 * {@code asOf}를 생략하면 Asia/Seoul 기준 오늘로 판정한다.
 */
@RestController
@RequestMapping("/api/v1/card-products")
public class CardProductController {

	private final CardProductService cardProductService;

	public CardProductController(CardProductService cardProductService) {
		this.cardProductService = cardProductService;
	}

	@GetMapping
	public ApiResponse<CardProductListResponse> listCardProducts(
			@RequestParam(name = AsOfParameter.NAME, required = false) String asOf) {
		LocalDate date = resolveAsOf(asOf);
		return ApiResponse.of(new CardProductListResponse(date, this.cardProductService.listProducts(date)));
	}

	@GetMapping("/{cardProductId}")
	public ApiResponse<CardProductDetail> getCardProduct(@PathVariable String cardProductId,
			@RequestParam(name = AsOfParameter.NAME, required = false) String asOf) {
		LocalDate date = resolveAsOf(asOf);
		return ApiResponse.of(this.cardProductService.getProduct(cardProductId, date));
	}

	private LocalDate resolveAsOf(String asOf) {
		return (asOf == null) ? this.cardProductService.today() : AsOfParameter.parse(asOf);
	}

}
