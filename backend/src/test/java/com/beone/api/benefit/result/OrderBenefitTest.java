package com.beone.api.benefit.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.card.ServiceId;
import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.money.BenefitAmount;
import com.beone.api.benefit.rule.RuleVersionRef;

/**
 * 단일 주문 혜택 결과 타입의 생성 검증. 규칙·근거가 필요한 판단(실적 조건 여부, 중복 관계, 정책 영향)은 결정기 테스트에서 확인한다. 값은
 * 합성이다.
 */
class OrderBenefitTest {

	private static final CardId CARD = CardId.of("SYN-CARD");

	private static final RuleVersionRef RULE = new RuleVersionRef(CARD, "SYN-2026");

	private static final BenefitAmount PENDING = BenefitAmount.needsConfirmation("synthetic: pending");

	private static final ServiceCandidate A_300 = positive("SYN-A", 300);

	private static final ServiceCandidate B_100 = positive("SYN-B", 100);

	private static final ServiceCandidate B_300 = positive("SYN-B", 300);

	private static final ServiceCandidate C_CATEGORY = new ServiceCandidate(ServiceId.of("SYN-C"),
			BenefitAmount.confirmed(0), Set.of(ApplicationStatus.NOT_APPLICABLE_CATEGORY));

	private static final ServiceCandidate D_PERFORMANCE = new ServiceCandidate(ServiceId.of("SYN-D"),
			BenefitAmount.confirmed(0), Set.of(ApplicationStatus.NOT_APPLICABLE_PERFORMANCE));

	private static final ServiceCandidate E_ZERO = new ServiceCandidate(ServiceId.of("SYN-E"), BenefitAmount.confirmed(0),
			Set.of());

	private static final ServiceNeedingConfirmation P_PENDING = new ServiceNeedingConfirmation(ServiceId.of("SYN-P"),
			ConfirmationStage.RULE_SELECTION, List.of("synthetic: calculation"));

	private static final UnsupportedService U_LIMITED = new UnsupportedService(ServiceId.of("SYN-U"),
			Set.of(UnsupportedReason.HAS_LIMIT_BUCKETS), List.of());

	private static ServiceCandidate positive(String id, long won) {
		return new ServiceCandidate(ServiceId.of(id), BenefitAmount.confirmed(won), Set.of());
	}

	private static CardServiceCandidates.Collected source(List<ServiceCandidate> candidates) {
		return source(candidates, List.of(), List.of());
	}

	private static CardServiceCandidates.Collected source(List<ServiceCandidate> candidates,
			List<ServiceNeedingConfirmation> pending, List<UnsupportedService> unsupported) {
		return new CardServiceCandidates.Collected(RULE, candidates, pending, unsupported, List.of());
	}

	private static OrderServiceBenefit adopted(ServiceCandidate candidate) {
		return new OrderServiceBenefit(candidate, Adoption.ADOPTED, candidate.standaloneAmount());
	}

	private static OrderServiceBenefit notAdopted(ServiceCandidate candidate) {
		return new OrderServiceBenefit(candidate, Adoption.NOT_ADOPTED, BenefitAmount.confirmed(0));
	}

	private static OrderServiceBenefit undetermined(ServiceCandidate candidate) {
		return new OrderServiceBenefit(candidate, Adoption.UNDETERMINED, PENDING);
	}

	private static PendingReason reason(PendingReason.Kind kind, ServiceCandidate... candidates) {
		return new PendingReason(kind, List.of(candidates).stream().map(ServiceCandidate::serviceId).toList(), "synthetic");
	}

	private static PendingReason reason(PendingReason.Kind kind, String service) {
		return new PendingReason(kind, List.of(ServiceId.of(service)), "synthetic");
	}

	private static PerformanceRecheck recheck(ServiceCandidate candidate) {
		return new PerformanceRecheck(candidate, List.of("synthetic: cancellation"));
	}

	@Nested
	class UnavailableResult {

		@ParameterizedTest
		@EnumSource(value = ApplicationStatus.class, names = { "RULE_EXPIRED", "NO_VERIFIED_RULE" })
		void amountNeedsConfirmationWithTheReason(ApplicationStatus status) {
			OrderBenefit.Unavailable result = new OrderBenefit.Unavailable(CARD, status, "synthetic: no rule");

			assertThat(result.cardId()).isEqualTo(CARD);
			assertThat(result.orderBenefit()).isEqualTo(BenefitAmount.needsConfirmation("synthetic: no rule"));
		}

		@ParameterizedTest
		@EnumSource(value = ApplicationStatus.class, names = { "RULE_EXPIRED", "NO_VERIFIED_RULE" }, mode = EnumSource.Mode.EXCLUDE)
		void rejectsOtherStatuses(ApplicationStatus status) {
			assertThatThrownBy(() -> new OrderBenefit.Unavailable(CARD, status, "synthetic"))
				.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		void requiresCardAndReason() {
			assertThatThrownBy(() -> new OrderBenefit.Unavailable(null, ApplicationStatus.RULE_EXPIRED, "synthetic"))
				.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> new OrderBenefit.Unavailable(CARD, ApplicationStatus.RULE_EXPIRED, " "))
				.isInstanceOf(IllegalArgumentException.class);
		}

	}

	@Nested
	class Confirmed {

		@Test
		void adoptsTheLargestAndSumsContributions() {
			OrderBenefit.Decided result = new OrderBenefit.Decided(source(List.of(C_CATEGORY, B_100, A_300)),
					List.of(adopted(A_300), notAdopted(B_100), notAdopted(C_CATEGORY)), List.of(), List.of(),
					BenefitAmount.confirmed(300), List.of());

			assertThat(result.cardId()).isEqualTo(CARD);
			assertThat(result.orderBenefit()).isEqualTo(BenefitAmount.confirmed(300));
		}

		@Test
		void onlyZeroCandidatesGiveConfirmedZero() {
			OrderBenefit.Decided result = new OrderBenefit.Decided(source(List.of(C_CATEGORY, E_ZERO)),
					List.of(notAdopted(C_CATEGORY), notAdopted(E_ZERO)), List.of(), List.of(), BenefitAmount.confirmed(0), List.of());

			assertThat(result.orderBenefit()).isEqualTo(BenefitAmount.confirmed(0));
		}

		@Test
		void rejectsSumMismatch() {
			assertThatThrownBy(() -> new OrderBenefit.Decided(source(List.of(A_300, B_100)),
					List.of(adopted(A_300), notAdopted(B_100)), List.of(), List.of(), BenefitAmount.confirmed(400), List.of()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("not the sum");
		}

		@Test
		void rejectsTwoAdoptedServices() {
			assertThatThrownBy(() -> new OrderBenefit.Decided(source(List.of(A_300, B_100)),
					List.of(adopted(A_300), adopted(B_100)), List.of(), List.of(), BenefitAmount.confirmed(400), List.of()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("adopted services");
		}

		@Test
		void rejectsAdoptingTheSmallerOrTheLaterIdOnTie() {
			assertThatThrownBy(() -> new OrderBenefit.Decided(source(List.of(A_300, B_100)),
					List.of(notAdopted(A_300), adopted(B_100)), List.of(), List.of(), BenefitAmount.confirmed(100), List.of()))
				.hasMessageContaining("adopted services");
			assertThatThrownBy(() -> new OrderBenefit.Decided(source(List.of(A_300, B_300)),
					List.of(notAdopted(A_300), adopted(B_300)), List.of(), List.of(), BenefitAmount.confirmed(300), List.of()))
				.hasMessageContaining("adopted services");
		}

		@Test
		void rejectsNoAdoptionWhenPositiveCandidatesExist() {
			assertThatThrownBy(() -> new OrderBenefit.Decided(source(List.of(A_300)), List.of(notAdopted(A_300)),
					List.of(), List.of(), BenefitAmount.confirmed(0), List.of()))
				.hasMessageContaining("adopted services");
		}

		@Test
		void rejectsUndeterminedWhileConfirmed() {
			assertThatThrownBy(() -> new OrderBenefit.Decided(source(List.of(A_300)), List.of(undetermined(A_300)),
					List.of(), List.of(), BenefitAmount.confirmed(0), List.of()))
				.hasMessageContaining("undetermined");
		}

		@Test
		void rejectsAdoptedContributionBelowItsCandidate() {
			OrderServiceBenefit partial = new OrderServiceBenefit(A_300, Adoption.ADOPTED, BenefitAmount.confirmed(200));

			assertThatThrownBy(() -> new OrderBenefit.Decided(source(List.of(A_300)), List.of(partial), List.of(),
					List.of(), BenefitAmount.confirmed(200), List.of()))
				.hasMessageContaining("amount after limit");
		}

		@Test
		void adoptedContributionFollowsTheRemainingLimit() {
			ServiceCandidate capped = new ServiceCandidate(ServiceId.of("SYN-A"), BenefitAmount.confirmed(300), Set.of(),
					Optional.of(Won.of(200)), Optional.empty(), false);
			OrderServiceBenefit adopted = new OrderServiceBenefit(capped, Adoption.ADOPTED, BenefitAmount.confirmed(200));

			OrderBenefit.Decided result = new OrderBenefit.Decided(source(List.of(capped)), List.of(adopted), List.of(),
					List.of(), BenefitAmount.confirmed(200), List.of());

			assertThat(result.orderBenefit()).isEqualTo(BenefitAmount.confirmed(200));
			assertThat(adopted.appliedStatus()).contains(ApplicationStatus.APPLIED_CAP_REMAINING);
		}

		@Test
		void rejectsZeroCandidateThatIsNotNotAdopted() {
			OrderServiceBenefit undeterminedZero = new OrderServiceBenefit(E_ZERO, Adoption.UNDETERMINED,
					BenefitAmount.confirmed(0));

			assertThatThrownBy(() -> new OrderBenefit.Decided(source(List.of(E_ZERO)), List.of(undeterminedZero),
					List.of(), List.of(), BenefitAmount.confirmed(0), List.of()))
				.hasMessageContaining("NOT_ADOPTED");
		}

		@Test
		void rejectsConfirmedAmountWithReasons() {
			assertThatThrownBy(() -> new OrderBenefit.Decided(source(List.of(A_300), List.of(P_PENDING), List.of()),
					List.of(adopted(A_300)), List.of(), List.of(reason(PendingReason.Kind.NEEDS_CONFIRMATION, "SYN-P")),
					BenefitAmount.confirmed(300), List.of()))
				.hasMessageContaining("exactly when there are no reasons");
		}

	}

	@Nested
	class Pending {

		private final CardServiceCandidates.Collected source = source(List.of(A_300, B_100, D_PERFORMANCE),
				List.of(P_PENDING), List.of(U_LIMITED));

		private final List<PendingReason> reasons = List.of(reason(PendingReason.Kind.NEEDS_CONFIRMATION, "SYN-P"),
				reason(PendingReason.Kind.UNSUPPORTED, "SYN-U"), reason(PendingReason.Kind.PERFORMANCE_POLICY, D_PERFORMANCE),
				reason(PendingReason.Kind.STACKING_UNRESOLVED, A_300, B_100));

		@Test
		void keepsPositiveCandidatesUndeterminedAndRechecksSeparate() {
			OrderBenefit.Decided result = new OrderBenefit.Decided(source, List.of(undetermined(A_300), undetermined(B_100)),
					List.of(recheck(D_PERFORMANCE)), reasons, PENDING, List.of());

			assertThat(result.orderBenefit().isConfirmed()).isFalse();
			assertThat(result.services()).extracting(service -> service.candidate().serviceId())
				.doesNotContain(D_PERFORMANCE.serviceId());
		}

		@Test
		void rejectsPendingAmountWithoutReasons() {
			assertThatThrownBy(() -> new OrderBenefit.Decided(source(List.of(A_300)), List.of(undetermined(A_300)),
					List.of(), List.of(), PENDING, List.of()))
				.hasMessageContaining("exactly when there are no reasons");
		}

		@Test
		void rejectsAdoptedWhilePending() {
			assertThatThrownBy(() -> new OrderBenefit.Decided(source, List.of(adopted(A_300), undetermined(B_100)),
					List.of(recheck(D_PERFORMANCE)), reasons, PENDING, List.of()))
				.hasMessageContaining("cannot be ADOPTED");
		}

		@Test
		void rejectsPositiveNotAdoptedWhilePending() {
			assertThatThrownBy(() -> new OrderBenefit.Decided(source, List.of(notAdopted(A_300), undetermined(B_100)),
					List.of(recheck(D_PERFORMANCE)), reasons, PENDING, List.of()))
				.hasMessageContaining("must be UNDETERMINED");
		}

		@Test
		void rejectsMissingReasonLinks() {
			// 재확인 후보의 실적 정책 사유 누락
			assertThatThrownBy(() -> new OrderBenefit.Decided(source, List.of(undetermined(A_300), undetermined(B_100)),
					List.of(recheck(D_PERFORMANCE)), without(PendingReason.Kind.PERFORMANCE_POLICY), PENDING, List.of()))
				.hasMessageContaining("PERFORMANCE_POLICY reason");
			// 확인 필요 서비스의 사유 누락
			assertThatThrownBy(() -> new OrderBenefit.Decided(source, List.of(undetermined(A_300), undetermined(B_100)),
					List.of(recheck(D_PERFORMANCE)), without(PendingReason.Kind.NEEDS_CONFIRMATION), PENDING, List.of()))
				.hasMessageContaining("NEEDS_CONFIRMATION reason");
			// 지원 범위 밖 서비스의 사유 누락
			assertThatThrownBy(() -> new OrderBenefit.Decided(source, List.of(undetermined(A_300), undetermined(B_100)),
					List.of(recheck(D_PERFORMANCE)), without(PendingReason.Kind.UNSUPPORTED), PENDING, List.of()))
				.hasMessageContaining("UNSUPPORTED reason");
		}

		@Test
		void rejectsReasonsOutsideTheirClassification() {
			// 확인 필요 사유가 후보 서비스를 가리킴
			assertThatThrownBy(() -> new OrderBenefit.Decided(source(List.of(A_300)), List.of(undetermined(A_300)),
					List.of(), List.of(reason(PendingReason.Kind.NEEDS_CONFIRMATION, A_300)), PENDING, List.of()))
				.hasMessageContaining("outside its classification");
			// 원본에 없는 서비스
			assertThatThrownBy(() -> new OrderBenefit.Decided(source(List.of(A_300)), List.of(undetermined(A_300)),
					List.of(), List.of(reason(PendingReason.Kind.UNSUPPORTED, "SYN-X")), PENDING, List.of()))
				.hasMessageContaining("outside its classification");
			// 중복 관계 사유가 0원 후보를 가리킴
			assertThatThrownBy(() -> new OrderBenefit.Decided(source(List.of(A_300, E_ZERO)),
					List.of(undetermined(A_300), notAdopted(E_ZERO)), List.of(),
					List.of(reason(PendingReason.Kind.STACKING_UNRESOLVED, A_300, E_ZERO)), PENDING, List.of()))
				.hasMessageContaining("outside its classification");
		}

		@Test
		void rejectsUnorderedOrDuplicateReasons() {
			List<PendingReason> reversed = new ArrayList<>(reasons);
			Collections.reverse(reversed);
			assertThatThrownBy(() -> new OrderBenefit.Decided(source, List.of(undetermined(A_300), undetermined(B_100)),
					List.of(recheck(D_PERFORMANCE)), reversed, PENDING, List.of()))
				.hasMessageContaining("unique and in order");
			List<PendingReason> duplicated = new ArrayList<>(reasons);
			duplicated.add(1, reasons.get(0));
			assertThatThrownBy(() -> new OrderBenefit.Decided(source, List.of(undetermined(A_300), undetermined(B_100)),
					List.of(recheck(D_PERFORMANCE)), duplicated, PENDING, List.of()))
				.hasMessageContaining("unique and in order");
		}

		private List<PendingReason> without(PendingReason.Kind kind) {
			return reasons.stream().filter(reason -> reason.kind() != kind).toList();
		}

	}

	@Nested
	class SourceCoverage {

		@Test
		void rejectsMissingCandidate() {
			assertThatThrownBy(() -> new OrderBenefit.Decided(source(List.of(A_300, C_CATEGORY)), List.of(adopted(A_300)),
					List.of(), List.of(), BenefitAmount.confirmed(300), List.of()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("not decided");
		}

		@Test
		void rejectsCandidateInBothServicesAndRechecks() {
			assertThatThrownBy(() -> new OrderBenefit.Decided(source(List.of(D_PERFORMANCE)),
					List.of(notAdopted(D_PERFORMANCE)), List.of(recheck(D_PERFORMANCE)),
					List.of(reason(PendingReason.Kind.PERFORMANCE_POLICY, D_PERFORMANCE)), PENDING, List.of()))
				.hasMessageContaining("more than once");
		}

		@Test
		void rejectsCandidateNotInSource() {
			assertThatThrownBy(() -> new OrderBenefit.Decided(source(List.of(A_300)),
					List.of(adopted(A_300), notAdopted(C_CATEGORY)), List.of(), List.of(), BenefitAmount.confirmed(300), List.of()))
				.hasMessageContaining("not in the source");
			// 같은 서비스 ID라도 원본과 다른 후보는 거부한다.
			assertThatThrownBy(() -> new OrderBenefit.Decided(source(List.of(A_300)),
					List.of(adopted(positive("SYN-A", 200))), List.of(), List.of(), BenefitAmount.confirmed(200), List.of()))
				.hasMessageContaining("not in the source");
		}

		@Test
		void rejectsUnsortedServicesAndRechecks() {
			assertThatThrownBy(() -> new OrderBenefit.Decided(source(List.of(A_300, B_100)),
					List.of(notAdopted(B_100), adopted(A_300)), List.of(), List.of(), BenefitAmount.confirmed(300), List.of()))
				.hasMessageContaining("ascending");
			ServiceCandidate earlier = new ServiceCandidate(ServiceId.of("SYN-0"), BenefitAmount.confirmed(0), Set.of());
			assertThatThrownBy(() -> new OrderBenefit.Decided(source(List.of(D_PERFORMANCE, earlier)), List.of(),
					List.of(recheck(D_PERFORMANCE), recheck(earlier)),
					List.of(reason(PendingReason.Kind.PERFORMANCE_POLICY, earlier),
							reason(PendingReason.Kind.PERFORMANCE_POLICY, D_PERFORMANCE)),
					PENDING, List.of()))
				.hasMessageContaining("ascending");
		}

		@Test
		void listsAreImmutableCopies() {
			List<OrderServiceBenefit> services = new ArrayList<>(List.of(adopted(A_300)));
			OrderBenefit.Decided result = new OrderBenefit.Decided(source(List.of(A_300)), services, new ArrayList<>(),
					new ArrayList<>(), BenefitAmount.confirmed(300), List.of());
			services.clear();

			assertThat(result.services()).containsExactly(adopted(A_300));
			assertThatThrownBy(() -> result.services().clear()).isInstanceOf(UnsupportedOperationException.class);
			assertThatThrownBy(() -> result.rechecks().add(recheck(E_ZERO)))
				.isInstanceOf(UnsupportedOperationException.class);
			assertThatThrownBy(() -> result.reasons().add(reason(PendingReason.Kind.UNSUPPORTED, "SYN-U")))
				.isInstanceOf(UnsupportedOperationException.class);
		}

		@Test
		void requiresSourceAndAmount() {
			assertThatThrownBy(() -> new OrderBenefit.Decided(null, List.of(), List.of(), List.of(),
					BenefitAmount.confirmed(0), List.of()))
				.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> new OrderBenefit.Decided(source(List.of()), List.of(), List.of(), List.of(), null, List.of()))
				.isInstanceOf(NullPointerException.class);
		}

	}

}
