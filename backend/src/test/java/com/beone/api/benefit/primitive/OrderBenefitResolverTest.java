package com.beone.api.benefit.primitive;

import static com.beone.api.benefit.primitive.PrimitiveTestData.CAFE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.card.PaymentMethod;
import com.beone.api.benefit.card.ServiceId;
import com.beone.api.benefit.card.TransactionNature;
import com.beone.api.benefit.input.CardMonthlyState;
import com.beone.api.benefit.input.Order;
import com.beone.api.benefit.input.PolicyTermEvidence;
import com.beone.api.benefit.money.BenefitAmount;
import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.result.Adoption;
import com.beone.api.benefit.result.ApplicationStatus;
import com.beone.api.benefit.result.OrderBenefit;
import com.beone.api.benefit.result.OrderServiceBenefit;
import com.beone.api.benefit.result.PendingReason;
import com.beone.api.benefit.result.ServiceCandidate;
import com.beone.api.benefit.rule.BenefitCalculation;
import com.beone.api.benefit.rule.BenefitKind;
import com.beone.api.benefit.rule.BenefitService;
import com.beone.api.benefit.rule.CancellationAttribution;
import com.beone.api.benefit.rule.CardRuleVersion;
import com.beone.api.benefit.rule.FractionalWonPolicy;
import com.beone.api.benefit.rule.LimitDeductionOrder;
import com.beone.api.benefit.rule.LimitPeriod;
import com.beone.api.benefit.rule.MerchantTarget;
import com.beone.api.benefit.rule.OfficialSource;
import com.beone.api.benefit.rule.PerformanceWindow;
import com.beone.api.benefit.rule.RuleCatalog;
import com.beone.api.benefit.rule.RulePolicy;
import com.beone.api.benefit.rule.RuleTerm;
import com.beone.api.benefit.rule.ServiceTier;
import com.beone.api.benefit.rule.TransactionDateBasis;
import com.beone.api.benefit.rule.TriggerEvidence;
import com.beone.api.benefit.rule.ValidityPeriod;
import com.beone.api.benefit.rule.VerificationStatus;
import com.beone.api.benefit.state.BasisPeriod;
import com.beone.api.benefit.state.StateValue;

/**
 * 가상 카드·서비스의 합성 규칙으로 규칙 선택부터 주문 혜택 결정까지의 연결을 확인한다. 실제 카드 약관의 계산 결과를 주장하지 않는다.
 */
class OrderBenefitResolverTest {

	private static final CardId CARD = CardId.of("SYN-CARD");

	private static final CardId OTHER_CARD = CardId.of("SYN-OTHER");

	private static final LocalDate DAY = LocalDate.of(2026, 10, 15);

	/** 10,000원 주문. 날짜는 판정일과 다를 수 있음을 보이려고 따로 받는다. */
	private static Order order(LocalDate date) {
		return new Order(Optional.empty(), Optional.empty(), Won.of(10_000), date, Optional.empty(), CAFE,
				PaymentMethod.DIRECT);
	}

	private static final Order ORDER = order(DAY);

	private static final RulePolicy POLICY = new RulePolicy(RuleTerm.resolved(PerformanceWindow.PREVIOUS_CALENDAR_MONTH),
			RuleTerm.resolved(TransactionDateBasis.APPROVAL), RuleTerm.resolved(CancellationAttribution.RECEIPT_MONTH),
			Set.of(TransactionNature.TAX), Set.of(), RuleTerm.resolved(true), RuleTerm.resolved(LimitPeriod.CALENDAR_MONTH),
			RuleTerm.resolved(LimitDeductionOrder.APPROVAL_ORDER), RuleTerm.resolved(Optional.empty()));

	/** 전 가맹점 정률, 실적 조건·한도 없음, 중복 불가. 10,000원 주문이면 확정 {@code bp ÷ 100}원. */
	private static BenefitService rate(String id, int basisPoints) {
		return new BenefitService(ServiceId.of(id), BenefitKind.BILLING_DISCOUNT, MerchantTarget.allMerchants(), Set.of(),
				Set.of(PaymentMethod.DIRECT), Won.ZERO, Optional.empty(),
				List.of(new ServiceTier(Won.ZERO, RuleTerm.resolved(BenefitCalculation.rate(basisPoints)))), List.of(),
				false, RuleTerm.resolved(Set.of()), RuleTerm.resolved(FractionalWonPolicy.TRUNCATE));
	}

	private static CardRuleVersion version(CardId card, String version, ValidityPeriod validity,
			VerificationStatus status, BenefitService... services) {
		List<OfficialSource> sources = status == VerificationStatus.VERIFIED
				? List.of(OfficialSource.of("https://example.invalid/terms.pdf", "p.1"))
				: List.of();
		return new CardRuleVersion(card, version, validity, status, false, sources, POLICY, List.of(services), List.of());
	}

	/** 2026년 상반기 규칙은 1%, 하반기 규칙은 3%. 판정일에 따라 고른 버전이 금액으로 드러난다. */
	private static final RuleCatalog CATALOG = new RuleCatalog(List.of(
			version(CARD, "SYN-2026H1", ValidityPeriod.between(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)),
					VerificationStatus.VERIFIED, rate("SYN-A", 100)),
			version(CARD, "SYN-2026H2", ValidityPeriod.between(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31)),
					VerificationStatus.VERIFIED, rate("SYN-A", 300)),
			version(CARD, "SYN-2027-CANDIDATE", ValidityPeriod.startingAt(LocalDate.of(2027, 1, 1)),
					VerificationStatus.CANDIDATE, rate("SYN-A", 500))));

	private static CardMonthlyState state(CardId card) {
		return new CardMonthlyState(card,
				Optional.of(StateValue.synthetic(Won.ZERO, BasisPeriod.month(YearMonth.of(2026, 9)))), Map.of(),
				StateValue.unknown(null, null), List.of());
	}

	private static PolicyTermEvidence evidence(CardId card) {
		return new PolicyTermEvidence(card, TriggerEvidence.UNDETERMINED, TriggerEvidence.UNDETERMINED);
	}

	private static OrderBenefit resolve(RuleCatalog catalog, LocalDate decisionDate, Order order) {
		return OrderBenefitResolver.resolve(catalog, CARD, decisionDate, order, state(CARD), evidence(CARD));
	}

	private static OrderServiceBenefit adopted(String id, long won) {
		return new OrderServiceBenefit(new ServiceCandidate(ServiceId.of(id), BenefitAmount.confirmed(won), Set.of()),
				Adoption.ADOPTED, BenefitAmount.confirmed(won));
	}

	@Nested
	class Selected {

		@Test
		void sameAsDecidingTheCatalogSelection() {
			OrderBenefit result = resolve(CATALOG, DAY, ORDER);

			assertThat(result).isEqualTo(
					OrderBenefitDecider.decide(CATALOG.select(CARD, DAY), ORDER, state(CARD), evidence(CARD)));
			assertThat(result).isInstanceOfSatisfying(OrderBenefit.Decided.class, decided -> {
				assertThat(decided.services()).containsExactly(adopted("SYN-A", 300));
				assertThat(decided.orderBenefit()).isEqualTo(BenefitAmount.confirmed(300));
			});
		}

		@Test
		void keepsPendingReasonsOfTheDecider() {
			CardRuleVersion rule = new CardRuleVersion(CARD, "SYN-2026", ValidityPeriod.startingAt(LocalDate.of(2026, 1, 1)),
					VerificationStatus.VERIFIED, false, List.of(OfficialSource.of("https://example.invalid/terms.pdf", "p.1")),
					POLICY, List.of(rate("SYN-A", 100), new BenefitService(ServiceId.of("SYN-B"), BenefitKind.BILLING_DISCOUNT,
							MerchantTarget.allMerchants(), Set.of(), Set.of(PaymentMethod.DIRECT), Won.ZERO, Optional.empty(),
							List.of(new ServiceTier(Won.ZERO, RuleTerm.resolved(BenefitCalculation.rate(200)))), List.of(),
							false, RuleTerm.unresolved("synthetic: stacking"), RuleTerm.resolved(FractionalWonPolicy.TRUNCATE))),
					List.of());

			OrderBenefit result = resolve(new RuleCatalog(List.of(rule)), DAY, ORDER);

			assertThat(result).isInstanceOfSatisfying(OrderBenefit.Decided.class, decided -> {
				assertThat(decided.reasons()).extracting(PendingReason::kind)
					.containsExactly(PendingReason.Kind.STACKING_UNRESOLVED);
				assertThat(decided.orderBenefit().isConfirmed()).isFalse();
			});
		}

	}

	@Nested
	class DecisionDate {

		@Test
		void selectsTheVersionValidOnTheDecisionDate() {
			OrderBenefit firstHalfEnd = resolve(CATALOG, LocalDate.of(2026, 6, 30), ORDER);
			OrderBenefit secondHalfStart = resolve(CATALOG, LocalDate.of(2026, 7, 1), ORDER);

			assertThat(firstHalfEnd.orderBenefit()).isEqualTo(BenefitAmount.confirmed(100));
			assertThat(secondHalfStart.orderBenefit()).isEqualTo(BenefitAmount.confirmed(300));
		}

		@Test
		void doesNotUseTheOrderDateToSelectTheVersion() {
			Order firstHalfOrder = order(LocalDate.of(2026, 3, 10));

			OrderBenefit result = resolve(CATALOG, LocalDate.of(2026, 7, 1), firstHalfOrder);

			assertThat(result.orderBenefit()).isEqualTo(BenefitAmount.confirmed(300));
		}

	}

	@Nested
	class UnavailableRule {

		@Test
		void cardWithoutRuleVersionsHasNoVerifiedRule() {
			OrderBenefit result = OrderBenefitResolver.resolve(CATALOG, OTHER_CARD, DAY, ORDER, state(OTHER_CARD),
					evidence(OTHER_CARD));

			assertThat(result).isInstanceOfSatisfying(OrderBenefit.Unavailable.class, unavailable -> {
				assertThat(unavailable.cardId()).isEqualTo(OTHER_CARD);
				assertThat(unavailable.status()).isEqualTo(ApplicationStatus.NO_VERIFIED_RULE);
			});
			assertThat(result.orderBenefit().isConfirmed()).isFalse();
		}

		@Test
		void dateOutsideEveryVersionIsExpired() {
			OrderBenefit result = resolve(CATALOG, LocalDate.of(2025, 12, 31), ORDER);

			assertThat(result).isInstanceOfSatisfying(OrderBenefit.Unavailable.class,
					unavailable -> assertThat(unavailable.status()).isEqualTo(ApplicationStatus.RULE_EXPIRED));
			assertThat(result.orderBenefit().isConfirmed()).isFalse();
		}

		@Test
		void dateCoveredOnlyByCandidateHasNoVerifiedRule() {
			OrderBenefit result = resolve(CATALOG, LocalDate.of(2027, 1, 1), ORDER);

			assertThat(result).isInstanceOfSatisfying(OrderBenefit.Unavailable.class,
					unavailable -> assertThat(unavailable.status()).isEqualTo(ApplicationStatus.NO_VERIFIED_RULE));
			assertThat(result.orderBenefit().isConfirmed()).isFalse();
		}

	}

	@Nested
	class Inputs {

		@Test
		void rejectsNullInputs() {
			assertThatThrownBy(() -> OrderBenefitResolver.resolve(null, CARD, DAY, ORDER, state(CARD), evidence(CARD)))
				.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> OrderBenefitResolver.resolve(CATALOG, null, DAY, ORDER, state(CARD), evidence(CARD)))
				.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> OrderBenefitResolver.resolve(CATALOG, CARD, null, ORDER, state(CARD), evidence(CARD)))
				.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> OrderBenefitResolver.resolve(CATALOG, CARD, DAY, null, state(CARD), evidence(CARD)))
				.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> OrderBenefitResolver.resolve(CATALOG, CARD, DAY, ORDER, null, evidence(CARD)))
				.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> OrderBenefitResolver.resolve(CATALOG, CARD, DAY, ORDER, state(CARD), null))
				.isInstanceOf(NullPointerException.class);
		}

		@Test
		void rejectsStateOrEvidenceOfAnotherCardWhetherOrNotARuleIsAvailable() {
			LocalDate expired = LocalDate.of(2025, 12, 31);

			assertThatThrownBy(() -> OrderBenefitResolver.resolve(CATALOG, CARD, DAY, ORDER, state(OTHER_CARD),
					evidence(CARD)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("monthly state card");
			assertThatThrownBy(() -> OrderBenefitResolver.resolve(CATALOG, CARD, expired, ORDER, state(OTHER_CARD),
					evidence(CARD)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("monthly state card");
			assertThatThrownBy(() -> OrderBenefitResolver.resolve(CATALOG, CARD, DAY, ORDER, state(CARD),
					evidence(OTHER_CARD)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("policy evidence card");
			assertThatThrownBy(() -> OrderBenefitResolver.resolve(CATALOG, CARD, expired, ORDER, state(CARD),
					evidence(OTHER_CARD)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("policy evidence card");
		}

	}

}
