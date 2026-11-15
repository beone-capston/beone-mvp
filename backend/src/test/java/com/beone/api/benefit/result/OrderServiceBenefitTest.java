package com.beone.api.benefit.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import com.beone.api.benefit.card.ServiceId;
import com.beone.api.benefit.money.BenefitAmount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 후보·채택·기여액의 표현만 확인한다. 중복·동률·제외 정책이 맞는지는 검증하지 않는다. 금액은 합성 값이다.
 */
class OrderServiceBenefitTest {

	private static final ServiceId SERVICE = ServiceId.of("SYN-SVC");

	private static final BenefitAmount ZERO = BenefitAmount.confirmed(0);

	private static final BenefitAmount UNKNOWN = BenefitAmount.needsConfirmation("원 미만 처리 미확정");

	@ParameterizedTest
	@EnumSource(value = ApplicationStatus.class, names = { "NOT_APPLICABLE_EXCLUDED", "NOT_APPLICABLE_CATEGORY",
			"NOT_APPLICABLE_PERFORMANCE", "NOT_APPLICABLE_MIN_AMOUNT", "NOT_APPLICABLE_PAYMENT_METHOD", "LIMIT_EXHAUSTED" })
	void allowedUnmetConditions(ApplicationStatus condition) {
		ServiceCandidate candidate = new ServiceCandidate(SERVICE, ZERO, Set.of(condition));

		assertThat(candidate.unmetConditions()).containsExactly(condition);
		assertThat(candidate.hasUnmetConditions()).isTrue();
	}

	@ParameterizedTest
	@EnumSource(value = ApplicationStatus.class, names = { "APPLIED", "APPLIED_CAP_REMAINING", "RULE_EXPIRED",
			"NO_VERIFIED_RULE", "NEEDS_CONFIRMATION" })
	void rejectedUnmetConditions(ApplicationStatus condition) {
		BenefitAmount anyAmount = condition.isApplied() ? BenefitAmount.confirmed(1_000) : ZERO;

		assertThatThrownBy(() -> new ServiceCandidate(SERVICE, anyAmount, Set.of(condition)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("cannot use");
		assertThatThrownBy(() -> new ServiceCandidate(SERVICE, UNKNOWN, Set.of(condition)))
			.hasMessageContaining("cannot use");
	}

	@Test
	void allowedAndRejectedConditionsCoverEveryStatus() {
		// 상태가 새로 추가되면 허용 여부를 명시적으로 정하도록 실패시킨다.
		Set<ApplicationStatus> rejected = EnumSet.of(ApplicationStatus.APPLIED, ApplicationStatus.APPLIED_CAP_REMAINING,
				ApplicationStatus.RULE_EXPIRED, ApplicationStatus.NO_VERIFIED_RULE, ApplicationStatus.NEEDS_CONFIRMATION);

		assertThat(ServiceCandidate.ALLOWED_UNMET_CONDITIONS).doesNotContainAnyElementsOf(rejected);
		assertThat(ServiceCandidate.ALLOWED_UNMET_CONDITIONS.size() + rejected.size())
			.isEqualTo(ApplicationStatus.values().length);
	}

	@Test
	void allowedUnmetConditionsCannotBeChanged() {
		// 같은 패키지 코드가 생성 검증 기준을 바꾸지 못해야 한다.
		Set<ApplicationStatus> allowed = ServiceCandidate.ALLOWED_UNMET_CONDITIONS;

		assertThatThrownBy(() -> allowed.add(ApplicationStatus.APPLIED)).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> allowed.remove(ApplicationStatus.LIMIT_EXHAUSTED))
			.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(allowed::clear).isInstanceOf(UnsupportedOperationException.class);

		assertThat(allowed).containsExactlyInAnyOrder(ApplicationStatus.NOT_APPLICABLE_EXCLUDED,
				ApplicationStatus.NOT_APPLICABLE_CATEGORY, ApplicationStatus.NOT_APPLICABLE_PERFORMANCE,
				ApplicationStatus.NOT_APPLICABLE_MIN_AMOUNT, ApplicationStatus.NOT_APPLICABLE_PAYMENT_METHOD,
				ApplicationStatus.NOT_APPLICABLE_SELECTION, ApplicationStatus.LIMIT_EXHAUSTED);
		assertThat(new ServiceCandidate(SERVICE, ZERO, Set.of(ApplicationStatus.LIMIT_EXHAUSTED)).hasUnmetConditions())
			.isTrue();
		assertThatThrownBy(() -> new ServiceCandidate(SERVICE, ZERO, Set.of(ApplicationStatus.APPLIED)))
			.hasMessageContaining("cannot use");
	}

	@Test
	void unmetConditionsAreCopiedAndImmutable() {
		Set<ApplicationStatus> input = new HashSet<>(Set.of(ApplicationStatus.NOT_APPLICABLE_CATEGORY));
		ServiceCandidate candidate = new ServiceCandidate(SERVICE, ZERO, input);

		input.add(ApplicationStatus.NOT_APPLICABLE_MIN_AMOUNT);

		assertThat(candidate.unmetConditions()).containsExactly(ApplicationStatus.NOT_APPLICABLE_CATEGORY);
		assertThatThrownBy(() -> candidate.unmetConditions().add(ApplicationStatus.LIMIT_EXHAUSTED))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void unmetConditionOrderIsNotPartOfTheContract() {
		ServiceCandidate first = new ServiceCandidate(SERVICE, ZERO,
				Set.of(ApplicationStatus.NOT_APPLICABLE_CATEGORY, ApplicationStatus.NOT_APPLICABLE_MIN_AMOUNT));
		ServiceCandidate second = new ServiceCandidate(SERVICE, ZERO,
				Set.of(ApplicationStatus.NOT_APPLICABLE_MIN_AMOUNT, ApplicationStatus.NOT_APPLICABLE_CATEGORY));

		assertThat(first).isEqualTo(second);
	}

	@Test
	void candidateRejectsNulls() {
		Set<ApplicationStatus> withNull = new HashSet<>();
		withNull.add(null);

		assertThatThrownBy(() -> new ServiceCandidate(null, ZERO, Set.of())).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new ServiceCandidate(SERVICE, null, Set.of())).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new ServiceCandidate(SERVICE, ZERO, null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new ServiceCandidate(SERVICE, ZERO, withNull)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void unmetConditionRequiresConfirmedZeroCandidate() {
		Set<ApplicationStatus> unmet = Set.of(ApplicationStatus.NOT_APPLICABLE_MIN_AMOUNT);

		assertThatThrownBy(() -> new ServiceCandidate(SERVICE, BenefitAmount.confirmed(1), unmet))
			.hasMessageContaining("unmet conditions");
		// 미충족이 확인됐다고 확인 필요 금액을 0원으로 바꿔 주지 않는다.
		assertThatThrownBy(() -> new ServiceCandidate(SERVICE, UNKNOWN, unmet)).hasMessageContaining("unmet conditions");
	}

	@Test
	void candidateWithoutUnmetConditionMayBeZeroPositiveOrUnconfirmed() {
		assertThat(new ServiceCandidate(SERVICE, ZERO, Set.of()).hasUnmetConditions()).isFalse();
		assertThat(new ServiceCandidate(SERVICE, BenefitAmount.confirmed(1_000), Set.of()).standaloneAmount())
			.isEqualTo(BenefitAmount.confirmed(1_000));
		assertThat(new ServiceCandidate(SERVICE, UNKNOWN, Set.of()).standaloneAmount()).isEqualTo(UNKNOWN);
	}

	@Test
	void adoptedCandidateContributesUpToItsAmount() {
		ServiceCandidate candidate = qualified(BenefitAmount.confirmed(1_000));

		assertThat(new OrderServiceBenefit(candidate, Adoption.ADOPTED, BenefitAmount.confirmed(1_000)).contribution())
			.isEqualTo(BenefitAmount.confirmed(1_000));
		// 공유 한도 등으로 후보보다 작게 들어가는 표현
		assertThat(new OrderServiceBenefit(candidate, Adoption.ADOPTED, BenefitAmount.confirmed(500)).contribution())
			.isEqualTo(BenefitAmount.confirmed(500));
		assertThatThrownBy(() -> new OrderServiceBenefit(candidate, Adoption.ADOPTED, BenefitAmount.confirmed(1_001)))
			.hasMessageContaining("above its candidate amount");
		assertThatThrownBy(() -> new OrderServiceBenefit(candidate, Adoption.UNDETERMINED, BenefitAmount.confirmed(1_001)))
			.hasMessageContaining("above its candidate amount");
	}

	@Test
	void positiveCandidateMayBeNotAdoptedWithZeroContribution() {
		ServiceCandidate candidate = qualified(BenefitAmount.confirmed(1_000));

		assertThat(new OrderServiceBenefit(candidate, Adoption.NOT_ADOPTED, ZERO).contribution()).isEqualTo(ZERO);
		assertThatThrownBy(() -> new OrderServiceBenefit(candidate, Adoption.NOT_ADOPTED, BenefitAmount.confirmed(1)))
			.hasMessageContaining("not adopted");
		assertThatThrownBy(() -> new OrderServiceBenefit(candidate, Adoption.NOT_ADOPTED, UNKNOWN))
			.hasMessageContaining("not adopted");
	}

	@Test
	void unconfirmedCandidateMayBeNotAdoptedOnSeparateEvidence() {
		// 채택하지 않은 근거는 계산기가 정한다. 이 타입은 그 결과를 담을 수 있는지만 본다.
		OrderServiceBenefit result = new OrderServiceBenefit(qualified(UNKNOWN), Adoption.NOT_ADOPTED, ZERO);

		assertThat(result.candidate().standaloneAmount()).isEqualTo(UNKNOWN);
		assertThat(result.contribution()).isEqualTo(ZERO);
	}

	@Test
	void unmetCandidateIsOnlyNotAdopted() {
		ServiceCandidate unmet = new ServiceCandidate(SERVICE, ZERO, Set.of(ApplicationStatus.NOT_APPLICABLE_CATEGORY));

		assertThat(new OrderServiceBenefit(unmet, Adoption.NOT_ADOPTED, ZERO).adoption()).isEqualTo(Adoption.NOT_ADOPTED);
		assertThatThrownBy(() -> new OrderServiceBenefit(unmet, Adoption.ADOPTED, ZERO))
			.hasMessageContaining("unmet conditions");
		assertThatThrownBy(() -> new OrderServiceBenefit(unmet, Adoption.UNDETERMINED, ZERO))
			.hasMessageContaining("unmet conditions");
	}

	@Test
	void confirmedZeroCandidateContributesConfirmedZero() {
		ServiceCandidate zero = qualified(ZERO);

		assertThat(new OrderServiceBenefit(zero, Adoption.UNDETERMINED, ZERO).contribution()).isEqualTo(ZERO);
		assertThatThrownBy(() -> new OrderServiceBenefit(zero, Adoption.UNDETERMINED, UNKNOWN))
			.hasMessageContaining("confirmed zero candidate");
		assertThatThrownBy(() -> new OrderServiceBenefit(zero, Adoption.ADOPTED, UNKNOWN))
			.hasMessageContaining("confirmed zero candidate");
	}

	@Test
	void undeterminedCannotHaveConfirmedPositiveContribution() {
		// 채택 여부를 모르면 미채택(0원)일 수도 있으므로 양수 기여액을 확정할 수 없다.
		ServiceCandidate positive = qualified(BenefitAmount.confirmed(1_000));
		ServiceCandidate unknown = qualified(UNKNOWN);

		assertThatThrownBy(() -> new OrderServiceBenefit(positive, Adoption.UNDETERMINED, BenefitAmount.confirmed(1_000)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("undetermined");
		assertThatThrownBy(() -> new OrderServiceBenefit(positive, Adoption.UNDETERMINED, BenefitAmount.confirmed(1)))
			.hasMessageContaining("undetermined");
		assertThatThrownBy(() -> new OrderServiceBenefit(unknown, Adoption.UNDETERMINED, BenefitAmount.confirmed(1_000)))
			.hasMessageContaining("undetermined");
	}

	@Test
	void undeterminedAllowsConfirmedZeroOrUnconfirmedContribution() {
		ServiceCandidate positive = qualified(BenefitAmount.confirmed(1_000));
		ServiceCandidate unknown = qualified(UNKNOWN);

		assertThat(new OrderServiceBenefit(positive, Adoption.UNDETERMINED, ZERO).contribution()).isEqualTo(ZERO);
		assertThat(new OrderServiceBenefit(unknown, Adoption.UNDETERMINED, ZERO).contribution()).isEqualTo(ZERO);
		assertThat(new OrderServiceBenefit(positive, Adoption.UNDETERMINED, UNKNOWN).contribution()).isEqualTo(UNKNOWN);
		assertThat(new OrderServiceBenefit(unknown, Adoption.UNDETERMINED, UNKNOWN).contribution()).isEqualTo(UNKNOWN);
		assertThat(new OrderServiceBenefit(qualified(ZERO), Adoption.UNDETERMINED, ZERO).contribution()).isEqualTo(ZERO);
	}

	@Test
	void adoptedMayHaveConfirmedPositiveContribution() {
		// UNDETERMINED 제한이 ADOPTED에 번지지 않았는지 확인한다.
		assertThat(new OrderServiceBenefit(qualified(UNKNOWN), Adoption.ADOPTED, BenefitAmount.confirmed(500)).contribution())
			.isEqualTo(BenefitAmount.confirmed(500));
	}

	@Test
	void unconfirmedAmountsCanBeExpressed() {
		// 한쪽이라도 확인 필요이면 대소를 비교하지 않고 그대로 담는다.
		assertThat(new OrderServiceBenefit(qualified(UNKNOWN), Adoption.UNDETERMINED, UNKNOWN).contribution())
			.isEqualTo(UNKNOWN);
		assertThat(new OrderServiceBenefit(qualified(UNKNOWN), Adoption.ADOPTED, UNKNOWN).contribution()).isEqualTo(UNKNOWN);
		assertThat(new OrderServiceBenefit(qualified(BenefitAmount.confirmed(1_000)), Adoption.UNDETERMINED, UNKNOWN)
			.contribution()).isEqualTo(UNKNOWN);
		assertThat(new OrderServiceBenefit(qualified(BenefitAmount.confirmed(1_000)), Adoption.ADOPTED, UNKNOWN)
			.contribution()).isEqualTo(UNKNOWN);
	}

	@Test
	void orderServiceBenefitRejectsNulls() {
		ServiceCandidate candidate = qualified(ZERO);

		assertThatThrownBy(() -> new OrderServiceBenefit(null, Adoption.NOT_ADOPTED, ZERO))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new OrderServiceBenefit(candidate, null, ZERO)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new OrderServiceBenefit(candidate, Adoption.NOT_ADOPTED, null))
			.isInstanceOf(NullPointerException.class);
	}

	private static ServiceCandidate qualified(BenefitAmount amount) {
		return new ServiceCandidate(SERVICE, amount, Set.of());
	}

}
