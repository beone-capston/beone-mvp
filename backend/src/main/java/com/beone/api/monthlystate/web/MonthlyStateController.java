package com.beone.api.monthlystate.web;

import com.beone.api.common.web.ApiResponse;
import com.beone.api.monthlystate.application.MonthlyStateService;
import com.beone.api.user.application.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 카드 월간 상태 조회·수동 입력 API 2개(docs/api/monthly-states.md). 현재 사용자는 {@link CurrentUser} 인자로만 받는다(D-09).
 *
 * <p>{@code local}·{@code demo} 프로필에서만 등록한다. 경로 {@code month} 형식은 서비스 호출 전에 확인한다.
 */
@RestController
@Profile({ "local", "demo" })
@RequestMapping("/api/v1/me/cards/{userCardId}/monthly-states/{month}")
public class MonthlyStateController {

	private final MonthlyStateService monthlyStateService;

	public MonthlyStateController(MonthlyStateService monthlyStateService) {
		this.monthlyStateService = monthlyStateService;
	}

	@GetMapping
	public ApiResponse<MonthlyStateResponse> getMonthlyState(CurrentUser currentUser, @PathVariable String userCardId,
			@PathVariable String month) {
		return ApiResponse.of(MonthlyStateResponse
			.from(this.monthlyStateService.get(currentUser, userCardId, MonthParameter.parse(month))));
	}

	@PutMapping
	public ApiResponse<MonthlyStateResponse.Saved> replaceMonthlyState(CurrentUser currentUser,
			@PathVariable String userCardId, @PathVariable String month,
			@Valid @RequestBody MonthlyStateRequest request) {
		return ApiResponse.of(MonthlyStateResponse.Saved.from(this.monthlyStateService.replace(currentUser, userCardId,
				MonthParameter.parse(month), request.toInput())));
	}

}
