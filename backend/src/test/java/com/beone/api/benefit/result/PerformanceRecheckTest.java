package com.beone.api.benefit.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.beone.api.benefit.card.ServiceId;
import com.beone.api.benefit.money.BenefitAmount;

/**
 * 실적 정책 재확인 후보의 생성 검증. 원본 후보를 그대로 담는지와 허용하는 후보 모양만 확인한다. 값은 합성이다.
 */
class PerformanceRecheckTest {

	private static final ServiceId SERVICE = ServiceId.of("SYN-SVC");

	private static final List<String> REASONS = List.of("synthetic: cancellation attribution");

	private static ServiceCandidate zero(Set<ApplicationStatus> unmet) {
		return new ServiceCandidate(SERVICE, BenefitAmount.confirmed(0), unmet);
	}

	@Test
	void keepsPerformanceOnlyZeroCandidateAsIs() {
		ServiceCandidate candidate = zero(Set.of(ApplicationStatus.NOT_APPLICABLE_PERFORMANCE));

		PerformanceRecheck recheck = new PerformanceRecheck(candidate, REASONS);

		assertThat(recheck.candidate()).isSameAs(candidate);
		assertThat(recheck.candidate().unmetConditions()).containsExactly(ApplicationStatus.NOT_APPLICABLE_PERFORMANCE);
		assertThat(recheck.reasons()).isEqualTo(REASONS);
	}

	@Test
	void keepsZeroCandidateWithoutUnmetConditions() {
		assertThat(new PerformanceRecheck(zero(Set.of()), REASONS).candidate().standaloneAmount())
			.isEqualTo(BenefitAmount.confirmed(0));
	}

	@Test
	void acceptsLimitExhaustedBecauseTheLimitTierDependsOnPerformance() {
		assertThat(new PerformanceRecheck(zero(Set.of(ApplicationStatus.LIMIT_EXHAUSTED)), REASONS).candidate()
			.unmetConditions()).containsExactly(ApplicationStatus.LIMIT_EXHAUSTED);
	}

	@ParameterizedTest
	@EnumSource(value = ApplicationStatus.class, names = { "NOT_APPLICABLE_EXCLUDED", "NOT_APPLICABLE_CATEGORY",
			"NOT_APPLICABLE_MIN_AMOUNT", "NOT_APPLICABLE_PAYMENT_METHOD", "NOT_APPLICABLE_SELECTION" })
	void rejectsOtherUnmetConditionsEvenWithPerformance(ApplicationStatus other) {
		assertThatThrownBy(() -> new PerformanceRecheck(zero(Set.of(other)), REASONS))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("other than performance");
		assertThatThrownBy(
				() -> new PerformanceRecheck(zero(Set.of(other, ApplicationStatus.NOT_APPLICABLE_PERFORMANCE)), REASONS))
			.hasMessageContaining("other than performance");
	}

	@Test
	void rejectsPositiveCandidate() {
		ServiceCandidate positive = new ServiceCandidate(SERVICE, BenefitAmount.confirmed(100), Set.of());

		assertThatThrownBy(() -> new PerformanceRecheck(positive, REASONS)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("confirmed zero");
	}

	@Test
	void requiresNonBlankReasons() {
		ServiceCandidate candidate = zero(Set.of());

		assertThatThrownBy(() -> new PerformanceRecheck(candidate, List.of())).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PerformanceRecheck(candidate, List.of(" ")))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PerformanceRecheck(null, REASONS)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new PerformanceRecheck(candidate, null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void reasonsAreAnImmutableCopy() {
		List<String> reasons = new ArrayList<>(REASONS);
		PerformanceRecheck recheck = new PerformanceRecheck(zero(Set.of()), reasons);
		reasons.add("later");

		assertThat(recheck.reasons()).isEqualTo(REASONS);
		assertThatThrownBy(() -> recheck.reasons().add("x")).isInstanceOf(UnsupportedOperationException.class);
	}

}
