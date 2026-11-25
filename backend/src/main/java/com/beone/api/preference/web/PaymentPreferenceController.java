package com.beone.api.preference.web;

import com.beone.api.common.web.ApiResponse;
import com.beone.api.preference.application.PaymentPreferenceService;
import com.beone.api.user.application.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 결제 전 설정 API 2개(docs/api/preferences.md). 현재 사용자는 {@link CurrentUser} 인자로만 받는다(D-09).
 *
 * <p>{@code local}·{@code demo}·{@code member} 프로필에서 등록한다. 그 밖의 환경에서는 경로가 없어 404 {@code NOT_FOUND}다.
 * {@code member}에서는 회원 세션의 장부가 현재 사용자이고, 세션 401·쓰기 CSRF 403·캐시 금지는 {@code /api/v1/me/**} 공통
 * 인터셉터·필터가 컨트롤러 전에 처리한다(결정 37). 가상 포트폴리오 사용자도 설정을 바꿀 수 있다(docs/api.md 업무 오류 코드).
 */
@RestController
@Profile({ "local", "demo", "member" })
@RequestMapping("/api/v1/me/payment-preference")
public class PaymentPreferenceController {

	private final PaymentPreferenceService paymentPreferenceService;

	public PaymentPreferenceController(PaymentPreferenceService paymentPreferenceService) {
		this.paymentPreferenceService = paymentPreferenceService;
	}

	@GetMapping
	public ApiResponse<PaymentPreferenceResponse> getPreference(CurrentUser currentUser) {
		return ApiResponse.of(PaymentPreferenceResponse.from(this.paymentPreferenceService.get(currentUser)));
	}

	@PutMapping
	public ApiResponse<PaymentPreferenceResponse> changePreference(CurrentUser currentUser,
			@Valid @RequestBody PaymentPreferenceRequest request) {
		return ApiResponse.of(PaymentPreferenceResponse
			.from(this.paymentPreferenceService.change(currentUser, request.toChange())));
	}

}
