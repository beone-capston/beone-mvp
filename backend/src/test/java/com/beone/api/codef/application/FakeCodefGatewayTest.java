package com.beone.api.codef.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import com.beone.api.codef.CodefApproval;
import com.beone.api.codef.CodefCardPerformance;
import com.beone.api.codef.CodefHeldCard;
import com.beone.api.codef.sync.CodefPerformanceReference;
import com.beone.api.codef.sync.CodefTargetOutcome;
import com.beone.api.codef.transport.CodefCallResult;
import org.junit.jupiter.api.Test;

/**
 * 가짜 게이트웨이의 합성 아이디별 결과, connectedId 발급·정리, 합성 fixture 조회(#129). 실 CODEF는 부르지 않는다.
 */
class FakeCodefGatewayTest {

	private final FakeCodefGateway gateway = new FakeCodefGateway(
			Clock.fixed(Instant.parse("2026-11-04T00:00:00Z"), ZoneOffset.UTC));

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

	@Test
	void defaultScenarioReadsTheSyntheticFixturesThroughTheResponseReader() {
		String connectedId = registered("user-a");
		Instant checkedAt = Instant.parse("2026-11-04T00:00:00Z");

		CodefTargetOutcome<CodefHeldCard> held = CodefTargetOutcome.judge(this.gateway.heldCards("0301", connectedId),
				checkedAt, card -> card);
		CodefTargetOutcome<CodefApproval> approvals = CodefTargetOutcome.judge(
				this.gateway.approvals("0301", connectedId, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 4)),
				checkedAt, approval -> approval);
		CodefTargetOutcome<CodefPerformanceReference> performances = CodefTargetOutcome.judge(
				this.gateway.performances("0301", connectedId), checkedAt,
				card -> CodefPerformanceReference.from(card, checkedAt));

		assertThat(held).isInstanceOfSatisfying(CodefTargetOutcome.Usable.class,
				usable -> assertThat(usable.items()).hasSize(3));
		assertThat(approvals).isInstanceOfSatisfying(CodefTargetOutcome.Usable.class, usable -> {
			assertThat(usable.items()).hasSize(5);
			assertThat(usable.items()).allSatisfy(
					item -> assertThat(((CodefApproval) item).usedDate()).hasValue("20261101"));
		});
		// 실적 기간은 서울 기준 전월이다
		assertThat(performances).isInstanceOfSatisfying(CodefTargetOutcome.Usable.class, usable -> {
			CodefPerformanceReference reference = (CodefPerformanceReference) usable.items().getFirst();
			assertThat(CodefSyncService.flatten(null, reference, java.time.LocalDateTime.of(2026, 11, 4, 9, 0)))
				.singleElement()
				.satisfies(item -> {
					assertThat(item.periodFrom()).isEqualTo(LocalDate.of(2026, 10, 1));
					assertThat(item.periodTo()).isEqualTo(LocalDate.of(2026, 10, 31));
					assertThat(item.currentUseWon()).isEqualTo(120000L);
				});
		});
	}

	@Test
	void syncScenariosAreChosenByTheLoginIdUsedToConnect() {
		String reconnect = registered(FakeCodefGateway.SYNC_RECONNECT_ID);
		String unavailable = registered(FakeCodefGateway.SYNC_UNAVAILABLE_ID);
		String partial = registered(FakeCodefGateway.SYNC_PARTIAL_ID);

		assertThatThrownBy(() -> this.gateway.heldCards("0301", reconnect))
			.isInstanceOf(CodefConnectionInvalidException.class);
		assertThat(this.gateway.heldCards("0301", unavailable)).isInstanceOf(CodefCallResult.Failed.class);
		assertThat(this.gateway.approvals("0301", unavailable, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 4)))
			.isInstanceOf(CodefCallResult.Failed.class);
		assertThat(this.gateway.performances("0301", unavailable)).isInstanceOf(CodefCallResult.Failed.class);
		assertThat(this.gateway.heldCards("0301", partial)).isInstanceOf(CodefCallResult.Answered.class);
		CodefCallResult<CodefCardPerformance> partialPerformances = this.gateway.performances("0301", partial);
		assertThat(partialPerformances).isInstanceOf(CodefCallResult.Failed.class);
	}

	@Test
	void unknownConnectedIdUsesTheDefaultScenario() {
		assertThat(this.gateway.heldCards("0301", "fake-cid-unknown-after-restart"))
			.isInstanceOf(CodefCallResult.Answered.class);
	}

	private String registered(String loginId) {
		return ((CodefRegistration.Registered) this.gateway.register("0301", loginId, "pw".toCharArray()))
			.connectedId();
	}

}
