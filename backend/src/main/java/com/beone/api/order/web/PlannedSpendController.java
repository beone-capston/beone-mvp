package com.beone.api.order.web;

import com.beone.api.common.web.ApiResponse;
import com.beone.api.order.application.PlannedSpendService;
import com.beone.api.user.application.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 예정 소비 조회·일괄 교체 API(docs/api/orders.md). 1차에서는 저장만 하고 추천에 쓰지 않는다(D-18 K4).
 *
 * <p>{@code local}·{@code demo}·{@code member} 프로필에서 등록한다. {@code member}에서는 회원 세션의 장부가 현재 사용자이고,
 * 세션 401·쓰기 CSRF 403·캐시 금지는 {@code /api/v1/me/**} 공통 인터셉터·필터가 컨트롤러 전에 처리한다(결정 37).
 */
@RestController
@Profile({ "local", "demo", "member" })
@RequestMapping("/api/v1/me/planned-spends")
public class PlannedSpendController {

	private final PlannedSpendService plannedSpendService;

	public PlannedSpendController(PlannedSpendService plannedSpendService) {
		this.plannedSpendService = plannedSpendService;
	}

	@GetMapping
	public ApiResponse<PlannedSpendsResponse> listPlannedSpends(CurrentUser currentUser) {
		return ApiResponse.of(PlannedSpendsResponse.from(this.plannedSpendService.list(currentUser)));
	}

	@PutMapping
	public ApiResponse<PlannedSpendsResponse> replacePlannedSpends(CurrentUser currentUser,
			@Valid @RequestBody PlannedSpendsRequest request) {
		return ApiResponse.of(PlannedSpendsResponse
			.from(this.plannedSpendService.replace(currentUser, request.toNewPlannedSpends())));
	}

}
