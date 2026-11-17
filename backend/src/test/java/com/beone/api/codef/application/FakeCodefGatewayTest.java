package com.beone.api.codef.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 가짜 게이트웨이의 합성 아이디별 결과와 connectedId 발급·정리.
 */
class FakeCodefGatewayTest {

	private final FakeCodefGateway gateway = new FakeCodefGateway();

	@Test
	void reservedIdsReproduceFailures() {
		assertThat(this.gateway.register("0301", FakeCodefGateway.LOGIN_REJECTED_ID, "pw".toCharArray()))
			.isInstanceOf(CodefRegistration.LoginRejected.class);
		assertThat(this.gateway.register("0301", FakeCodefGateway.CARD_VERIFICATION_ID, "pw".toCharArray()))
			.isInstanceOf(CodefRegistration.CardVerificationRequired.class);
		assertThat(this.gateway.register("0301", FakeCodefGateway.UNAVAILABLE_ID, "pw".toCharArray()))
			.isInstanceOf(CodefRegistration.Unavailable.class);
	}

	@Test
	void otherIdsGetADistinctSyntheticConnectedIdThatCanBeReleased() {
		CodefRegistration.Registered first = (CodefRegistration.Registered) this.gateway.register("0301", "user-a",
				"pw".toCharArray());
		CodefRegistration.Registered second = (CodefRegistration.Registered) this.gateway.register("0301", "user-a",
				"pw".toCharArray());

		assertThat(first.connectedId()).startsWith("fake-cid-").isNotEqualTo(second.connectedId());
		assertThat(this.gateway.isActive(first.connectedId())).isTrue();
		this.gateway.release(first.connectedId());
		assertThat(this.gateway.isActive(first.connectedId())).isFalse();
		assertThat(this.gateway.isActive(second.connectedId())).isTrue();
	}

}
