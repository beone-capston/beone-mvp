package com.beone.api.benefit.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.beone.api.benefit.card.ServiceId;

/**
 * 미확정 사유 타입의 생성 검증과 정렬 순서. 값은 합성이다.
 */
class PendingReasonTest {

	private static final ServiceId A = ServiceId.of("SYN-A");

	private static final ServiceId B = ServiceId.of("SYN-B");

	private static final ServiceId LOWER = ServiceId.of("syn-a");

	@ParameterizedTest
	@EnumSource(value = PendingReason.Kind.class, names = { "NEEDS_CONFIRMATION", "UNSUPPORTED", "PERFORMANCE_POLICY" })
	void singleServiceKindsNeedExactlyOneService(PendingReason.Kind kind) {
		assertThat(new PendingReason(kind, List.of(A), "synthetic").services()).containsExactly(A);
		assertThatThrownBy(() -> new PendingReason(kind, List.of(), "synthetic"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("exactly 1");
		assertThatThrownBy(() -> new PendingReason(kind, List.of(A, B), "synthetic"))
			.hasMessageContaining("exactly 1");
	}

	@ParameterizedTest
	@EnumSource(value = PendingReason.Kind.class, names = { "STACKING_UNRESOLVED", "STACKING_CONFIRMED" })
	void stackingKindsNeedAnAscendingPairOfDistinctServices(PendingReason.Kind kind) {
		assertThat(new PendingReason(kind, List.of(A, B), "synthetic").services()).containsExactly(A, B);
		assertThatThrownBy(() -> new PendingReason(kind, List.of(A), "synthetic")).hasMessageContaining("exactly 2");
		assertThatThrownBy(() -> new PendingReason(kind, List.of(A, A), "synthetic")).hasMessageContaining("twice");
		assertThatThrownBy(() -> new PendingReason(kind, List.of(B, A), "synthetic")).hasMessageContaining("ascending");
	}

	@Test
	void exceedsPaymentNeedsAtLeastTwoServices() {
		ServiceId c = ServiceId.of("SYN-C");

		assertThat(new PendingReason(PendingReason.Kind.STACKING_EXCEEDS_PAYMENT, List.of(A, B, c), "synthetic").services())
			.containsExactly(A, B, c);
		assertThatThrownBy(() -> new PendingReason(PendingReason.Kind.STACKING_EXCEEDS_PAYMENT, List.of(A), "synthetic"))
			.hasMessageContaining("at least 2");
	}

	@Test
	void serviceOrderIsCaseSensitiveStringOrder() {
		// 'S'(0x53)가 's'(0x73)보다 앞선다. 대소문자를 무시하거나 로캘로 정렬하지 않는다.
		assertThat(PendingReason.SERVICE_ORDER.compare(B, LOWER)).isNegative();
		assertThat(new PendingReason(PendingReason.Kind.STACKING_UNRESOLVED, List.of(B, LOWER), "synthetic").services())
			.containsExactly(B, LOWER);
		// 숫자 정렬이 아니라 코드 단위 순서다: "SYN-10" < "SYN-9".
		assertThat(PendingReason.SERVICE_ORDER.compare(ServiceId.of("SYN-10"), ServiceId.of("SYN-9"))).isNegative();
	}

	@Test
	void requiresKindAndDetail() {
		assertThatThrownBy(() -> new PendingReason(null, List.of(A), "synthetic")).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new PendingReason(PendingReason.Kind.UNSUPPORTED, List.of(A), null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PendingReason(PendingReason.Kind.UNSUPPORTED, List.of(A), " "))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PendingReason(PendingReason.Kind.UNSUPPORTED, null, "synthetic"))
			.isInstanceOf(NullPointerException.class);
	}

	@Test
	void servicesAreAnImmutableCopy() {
		List<ServiceId> services = new ArrayList<>(List.of(A));
		PendingReason reason = new PendingReason(PendingReason.Kind.PERFORMANCE_POLICY, services, "synthetic");
		services.add(B);

		assertThat(reason.services()).containsExactly(A);
		assertThatThrownBy(() -> reason.services().add(B)).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void orderIsKindThenServiceIds() {
		PendingReason stackingAb = new PendingReason(PendingReason.Kind.STACKING_UNRESOLVED, List.of(A, B), "x");
		PendingReason stackingAc = new PendingReason(PendingReason.Kind.STACKING_UNRESOLVED,
				List.of(A, ServiceId.of("SYN-C")), "x");
		PendingReason policyB = new PendingReason(PendingReason.Kind.PERFORMANCE_POLICY, List.of(B), "x");
		PendingReason confirmationB = new PendingReason(PendingReason.Kind.NEEDS_CONFIRMATION, List.of(B), "x");
		PendingReason confirmationA = new PendingReason(PendingReason.Kind.NEEDS_CONFIRMATION, List.of(A), "x");
		List<PendingReason> reasons = new ArrayList<>(
				List.of(stackingAc, policyB, stackingAb, confirmationB, confirmationA));

		reasons.sort(PendingReason.ORDER);

		assertThat(reasons).containsExactly(confirmationA, confirmationB, policyB, stackingAb, stackingAc);
		assertThat(PendingReason.Kind.values()).containsExactly(PendingReason.Kind.NEEDS_CONFIRMATION,
				PendingReason.Kind.UNSUPPORTED, PendingReason.Kind.PERFORMANCE_POLICY,
				PendingReason.Kind.STACKING_UNRESOLVED, PendingReason.Kind.STACKING_CONFIRMED,
				PendingReason.Kind.STACKING_EXCEEDS_PAYMENT);
	}

}
