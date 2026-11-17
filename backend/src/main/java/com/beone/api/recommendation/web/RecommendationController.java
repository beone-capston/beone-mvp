package com.beone.api.recommendation.web;

import com.beone.api.common.web.ApiResponse;
import com.beone.api.recommendation.application.RecommendationDocument;
import com.beone.api.recommendation.application.RecommendationService;
import com.beone.api.user.application.CurrentUser;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 추천 계산·조회 API 2개(docs/api/recommendations.md). 현재 사용자는 {@link CurrentUser} 인자로만 받는다(D-09).
 *
 * <p>{@code local}·{@code demo} 프로필에서만 등록한다. 계산 응답의 계산 상태(확인 필요 등)는 HTTP 오류가 아니라 {@code data} 안에 담는다.
 */
@RestController
@Profile({ "local", "demo" })
@RequestMapping("/api/v1/me")
public class RecommendationController {

	private final RecommendationService recommendationService;

	public RecommendationController(RecommendationService recommendationService) {
		this.recommendationService = recommendationService;
	}

	@PostMapping("/orders/{orderId}/recommendations")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<RecommendationDocument> calculateRecommendation(CurrentUser currentUser,
			@PathVariable String orderId) {
		return ApiResponse.of(this.recommendationService.calculate(currentUser, orderId));
	}

	@GetMapping("/recommendations/{recommendationId}")
	public ApiResponse<RecommendationDetailResponse> getRecommendation(CurrentUser currentUser,
			@PathVariable String recommendationId) {
		return ApiResponse
			.of(RecommendationDetailResponse.from(this.recommendationService.get(currentUser, recommendationId)));
	}

}
