package com.beone.api.auth.web;

import com.beone.api.auth.application.MemberWithdrawalService;
import com.beone.api.auth.application.WithdrawalResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 탈퇴 API {@code DELETE /api/v1/me}(docs/api/auth.md, 결정 D10). 회원 프로필 {@code member} 전용이며 {@code local}·{@code demo}에서는
 * 경로의 DELETE가 없어 405다.
 *
 * <p>판정 순서는 401 {@code UNAUTHENTICATED} → 403 {@code CSRF_INVALID}(둘 다 {@link MemberApiInterceptor}) → 400
 * {@code INVALID_REQUEST}(본문) → 429 {@code RATE_LIMITED} → 400 {@code INVALID_REQUEST}(비밀번호 불일치, {@code password} 필드 오류)
 * → 204다. 성공 응답은 세션 쿠키를 지우는 쿠키 한 개로 인터셉터가 심은 갱신 쿠키를 덮어쓴다.
 */
@RestController
@Profile("member")
class MemberWithdrawalController {

	private final MemberWithdrawalService withdrawalService;

	MemberWithdrawalController(MemberWithdrawalService withdrawalService) {
		this.withdrawalService = withdrawalService;
	}

	@DeleteMapping("/api/v1/me")
	ResponseEntity<Void> withdraw(@Valid @RequestBody AccountDeletionRequest request,
			HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
		String memberId = AuthenticatedMember.memberIdOf(servletRequest).orElseThrow(UnauthenticatedException::new);
		WithdrawalResult result = this.withdrawalService.withdraw(memberId, request.password());
		return switch (result) {
			case WithdrawalResult.Withdrawn ignored -> {
				servletResponse.setHeader(HttpHeaders.SET_COOKIE, SessionCookie.clear());
				yield ResponseEntity.noContent().build();
			}
			case WithdrawalResult.PasswordMismatch ignored -> throw new PasswordConfirmationFailedException();
			case WithdrawalResult.RateLimited limited -> throw new LoginRateLimitedException(limited.retryAfter());
			case WithdrawalResult.MemberGone ignored -> {
				servletResponse.setHeader(HttpHeaders.SET_COOKIE, SessionCookie.clear());
				throw new UnauthenticatedException();
			}
		};
	}

}
