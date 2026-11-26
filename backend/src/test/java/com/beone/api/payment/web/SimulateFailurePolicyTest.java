package com.beone.api.payment.web;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

/**
 * {@code simulateFailure}는 {@code local}·{@code demo} 프로필에서만 받고 그 밖에는 400이다(docs/api/mock-payments.md 요청 본문·실패
 * 목록, D-23-4). {@code false}와 생략은 어느 프로필에서나 받는다.
 */
class SimulateFailurePolicyTest {

	private static final ApprovalRequest FAILURE = new ApprovalRequest(null, true);

	private static final ApprovalRequest NO_FAILURE = new ApprovalRequest("rec-1", false);

	@ParameterizedTest
	@ValueSource(strings = { "local", "demo" })
	void localAndDemoAcceptFailure(String profile) {
		SimulateFailurePolicy policy = policyFor(profile);

		assertThatCode(() -> policy.check(FAILURE)).doesNotThrowAnyException();
		assertThatCode(() -> policy.check(NO_FAILURE)).doesNotThrowAnyException();
		assertThatCode(() -> policy.check(ApprovalRequest.EMPTY)).doesNotThrowAnyException();
	}

	@Test
	void memberRejectsOnlyTrue() {
		SimulateFailurePolicy policy = policyFor("member");

		assertThatThrownBy(() -> policy.check(FAILURE)).isInstanceOf(SimulateFailureNotAllowedException.class);
		assertThatCode(() -> policy.check(NO_FAILURE)).doesNotThrowAnyException();
		assertThatCode(() -> policy.check(ApprovalRequest.EMPTY)).doesNotThrowAnyException();
	}

	@Test
	void noProfileRejectsTrue() {
		assertThatThrownBy(() -> SimulateFailurePolicy.of(new MockEnvironment()).check(FAILURE))
			.isInstanceOf(SimulateFailureNotAllowedException.class);
	}

	private static SimulateFailurePolicy policyFor(String profile) {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles(profile);
		return SimulateFailurePolicy.of(environment);
	}

}
