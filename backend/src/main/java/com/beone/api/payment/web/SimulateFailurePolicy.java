package com.beone.api.payment.web;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * 모의 승인 {@code simulateFailure}를 받을지 정한다(docs/api/mock-payments.md, D-23-4). 계약 문언대로 {@code local}·{@code demo}
 * 프로필에서만 받고, 그 밖(예: {@code member})에서 {@code true}를 보내면 400 {@code INVALID_REQUEST}다.
 *
 * <p>{@code false}와 생략은 어느 프로필에서나 받는다. 지문 정규화가 생략을 {@code false}와 같게 보기 때문이다. member에서 {@code true}만
 * 400으로 거부하고 {@code false}·생략은 정상 처리하는 것은 결정 38-1로 확정됐다.
 */
final class SimulateFailurePolicy {

	private static final Profiles FAILURE_PROFILES = Profiles.of("local | demo");

	private final boolean failureAllowed;

	private SimulateFailurePolicy(boolean failureAllowed) {
		this.failureAllowed = failureAllowed;
	}

	static SimulateFailurePolicy of(Environment environment) {
		return new SimulateFailurePolicy(environment.acceptsProfiles(FAILURE_PROFILES));
	}

	/**
	 * 요청을 받을 수 없으면 예외를 던진다. 서비스 호출 전에 불러야 결제·멱등 키를 저장하지 않는다.
	 * @throws SimulateFailureNotAllowedException {@code local}·{@code demo} 밖에서 {@code simulateFailure=true}
	 */
	void check(ApprovalRequest request) {
		if (!this.failureAllowed && Boolean.TRUE.equals(request.simulateFailure())) {
			throw new SimulateFailureNotAllowedException();
		}
	}

}
