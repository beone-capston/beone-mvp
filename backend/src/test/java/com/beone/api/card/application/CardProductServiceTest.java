package com.beone.api.card.application;

import static com.beone.api.card.application.CardRuleFixtures.between;
import static com.beone.api.card.application.CardRuleFixtures.bucket;
import static com.beone.api.card.application.CardRuleFixtures.from;
import static com.beone.api.card.application.CardRuleFixtures.policy;
import static com.beone.api.card.application.CardRuleFixtures.rule;
import static com.beone.api.card.application.CardRuleFixtures.service;
import static com.beone.api.card.application.CardRuleFixtures.simpleRule;
import static com.beone.api.card.application.CardRuleFixtures.tier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import com.beone.api.benefit.rule.BenefitKind;
import com.beone.api.benefit.rule.CancellationAttribution;
import com.beone.api.benefit.rule.CardRuleVersion;
import com.beone.api.benefit.rule.LimitDeductionOrder;
import com.beone.api.benefit.rule.LimitPeriod;
import com.beone.api.benefit.rule.PerformanceWindow;
import com.beone.api.benefit.rule.RuleCatalog;
import com.beone.api.benefit.rule.RuleTerm;
import com.beone.api.benefit.rule.VerificationStatus;
import com.beone.api.benefit.rule.loading.RuleCatalogLoader;
import org.junit.jupiter.api.Test;

/**
 * 규칙 선택 결과를 docs/api/products.md 응답 모양으로 옮기는 규칙과 결정 27 A1의 판단을 확인한다.
 *
 * <p>규칙은 합성 값이다. 기대값은 계약 문서의 필드 설명(미확정은 null, 규칙 없으면 빈 배열 등)에서 정했다.
 */
class CardProductServiceTest {

	private static final LocalDate AS_OF = LocalDate.of(2026, 11, 3);

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-16T15:30:00Z"), ZoneOffset.UTC);

	private static final List<CardProductProfile> PROFILES = List.of(
			new CardProductProfile("09174", "카드 B", "카드사", CardType.CREDIT),
			new CardProductProfile("01664", "카드 A", "카드사", CardType.CHECK),
			new CardProductProfile("01914", "카드 C", "카드사", CardType.CHECK));

	@Test
	void listIsSortedByProductIdStringAndKeepsLeadingZeros() {
		CardProductService service = productService(simpleRule("09174", "B-1", from("2026-01-01")),
				simpleRule("01664", "A-1", from("2026-01-01")), simpleRule("01914", "C-1", from("2026-01-01")));

		assertThat(service.listProducts(AS_OF)).extracting(CardProductSummary::cardProductId)
			.containsExactly("01664", "01914", "09174");
	}

	@Test
	void verifiedRuleGivesVersionAndAllOfficialSources() {
		CardProductService service = productService(simpleRule("09174", "B-1", from("2026-01-01")),
				simpleRule("01664", "A-1", from("2026-01-01")), simpleRule("01914", "C-1", from("2026-01-01")));

		CardProductSummary first = service.listProducts(AS_OF).get(0);

		assertThat(first).isEqualTo(new CardProductSummary("01664", "카드 A", "카드사", CardType.CHECK,
				RuleStatus.VERIFIED, "A-1", List.of(new OfficialSourceView("https://example.invalid/a.pdf", "p.1"),
						new OfficialSourceView("https://example.invalid/b", null))));
	}

	@Test
	void expiredAndUnverifiedRulesHaveNoVersionAndNoSources() {
		CardRuleVersion expired = simpleRule("01664", "A-OLD", between("2025-01-01", "2025-12-31"));
		CardRuleVersion candidate = rule("01914", "C-DRAFT", from("2026-01-01"), VerificationStatus.CANDIDATE,
				CardRuleFixtures.resolvedPolicy(), List.of(service("BASE", List.of(), false, null)), List.of());
		CardProductService service = productService(expired, candidate, simpleRule("09174", "B-1", from("2026-01-01")));

		List<CardProductSummary> items = service.listProducts(AS_OF);

		assertThat(items.get(0)).isEqualTo(new CardProductSummary("01664", "카드 A", "카드사", CardType.CHECK,
				RuleStatus.RULE_EXPIRED, null, List.of()));
		assertThat(items.get(1)).isEqualTo(new CardProductSummary("01914", "카드 C", "카드사", CardType.CHECK,
				RuleStatus.NO_VERIFIED_RULE, null, List.of()));
		assertThat(items.get(2).ruleStatus()).isEqualTo(RuleStatus.VERIFIED);
	}

	@Test
	void ruleStatusFollowsAsOfDate() {
		CardProductService service = productService(simpleRule("09174", "B-1", from("2026-07-14")),
				simpleRule("01664", "A-1", from("2026-01-01")), simpleRule("01914", "C-1", from("2026-01-01")));

		assertThat(service.getProduct("09174", LocalDate.of(2026, 7, 13)).ruleStatus())
			.isEqualTo(RuleStatus.RULE_EXPIRED);
		assertThat(service.getProduct("09174", LocalDate.of(2026, 7, 14)).ruleStatus())
			.isEqualTo(RuleStatus.VERIFIED);
	}

	@Test
	void detailMapsRuleServicesAndBucketsWithDirectSharingOnly() {
		CardRuleVersion rich = rule("09174", "B-2", between("2026-01-01", "2026-12-31"), VerificationStatus.VERIFIED,
				CardRuleFixtures.resolvedPolicy(),
				List.of(service("FREE", List.of(), false, null), service("CHILD", List.of("AREA"), false, "보기 이름"),
						service("TOP", List.of("COMMON"), false, null), service("PICK", List.of("PICKED"), true, null)),
				List.of(bucket("COMMON", null, tier(200_000, 10_000), tier(500_000, 30_000)),
						bucket("AREA", "COMMON", tier(300_000, 2_000)), bucket("PICKED", null, tier(400_000, 10_000))));
		CardProductService service = productService(rich, simpleRule("01664", "A-1", from("2026-01-01")),
				simpleRule("01914", "C-1", from("2026-01-01")));

		CardProductDetail detail = service.getProduct("09174", AS_OF);

		assertThat(detail.cardProductId()).isEqualTo("09174");
		assertThat(detail.cardName()).isEqualTo("카드 B");
		assertThat(detail.asOf()).isEqualTo(AS_OF);
		assertThat(detail.ruleStatus()).isEqualTo(RuleStatus.VERIFIED);
		assertThat(detail.rule()).isEqualTo(new CardProductDetail.Rule("B-2", VerificationStatus.VERIFIED,
				LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), PerformanceWindow.PREVIOUS_CALENDAR_MONTH,
				CancellationAttribution.RECEIPT_MONTH, LimitDeductionOrder.PURCHASE_ORDER));
		assertThat(detail.services()).containsExactly(
				new CardProductDetail.Service("FREE", "FREE", BenefitKind.BILLING_DISCOUNT, SupportStatus.SUPPORTED,
						List.of(), false),
				new CardProductDetail.Service("CHILD", "보기 이름", BenefitKind.BILLING_DISCOUNT,
						SupportStatus.SUPPORTED_SINGLE_MONTHLY_LIMIT, List.of("AREA"), false),
				new CardProductDetail.Service("TOP", "TOP", BenefitKind.BILLING_DISCOUNT,
						SupportStatus.SUPPORTED_SINGLE_MONTHLY_LIMIT, List.of("COMMON"), false),
				new CardProductDetail.Service("PICK", "PICK", BenefitKind.BILLING_DISCOUNT,
						SupportStatus.UNSUPPORTED_SELECTION_RULE, List.of("PICKED"), true));
		assertThat(detail.limitBuckets()).containsExactly(
				new CardProductDetail.LimitBucket("COMMON",
						List.of(new CardProductDetail.Tier(200_000, 10_000), new CardProductDetail.Tier(500_000, 30_000)),
						null, LimitPeriod.CALENDAR_MONTH, List.of("TOP")),
				new CardProductDetail.LimitBucket("AREA", List.of(new CardProductDetail.Tier(300_000, 2_000)), "COMMON",
						LimitPeriod.CALENDAR_MONTH, List.of("CHILD")),
				new CardProductDetail.LimitBucket("PICKED", List.of(new CardProductDetail.Tier(400_000, 10_000)), null,
						LimitPeriod.CALENDAR_MONTH, List.of("PICK")));
	}

	@Test
	void unresolvedTermsStayNullAndAreNotReplacedWithDefaults() {
		CardRuleVersion unresolved = rule("09174", "B-3", from("2026-01-01"), VerificationStatus.VERIFIED,
				policy(RuleTerm.unresolved("귀속월 표기 없음"), RuleTerm.unresolved("한도 기간 표기 없음"),
						RuleTerm.unresolved("차감 순서 표기 없음")),
				List.of(service("LIMITED", List.of("L"), false, null)), List.of(bucket("L", null, tier(300_000, 5_000))));
		CardProductService service = productService(unresolved, simpleRule("01664", "A-1", from("2026-01-01")),
				simpleRule("01914", "C-1", from("2026-01-01")));

		CardProductDetail detail = service.getProduct("09174", AS_OF);

		assertThat(detail.rule().validTo()).isNull();
		assertThat(detail.rule().cancellationAttribution()).isNull();
		assertThat(detail.rule().limitDeductionOrder()).isNull();
		assertThat(detail.services().get(0).supportStatus()).isEqualTo(SupportStatus.UNSUPPORTED_LIMIT_PERIOD);
		assertThat(detail.limitBuckets().get(0).period()).isNull();
	}

	@Test
	void detailWithoutUsableRuleHasNullRuleAndEmptyLists() {
		CardProductService service = productService(simpleRule("09174", "B-1", from("2027-01-01")),
				simpleRule("01664", "A-1", from("2026-01-01")), simpleRule("01914", "C-1", from("2026-01-01")));

		assertThat(service.getProduct("09174", AS_OF)).isEqualTo(
				new CardProductDetail("09174", "카드 B", AS_OF, RuleStatus.RULE_EXPIRED, null, List.of(), List.of()));
	}

	@Test
	void unknownOrNumericallyEqualIdIsNotFound() {
		CardProductService service = productService(simpleRule("09174", "B-1", from("2026-01-01")),
				simpleRule("01664", "A-1", from("2026-01-01")), simpleRule("01914", "C-1", from("2026-01-01")));

		for (String id : List.of("9174", "009174", "09175", "", " 09174", "09174 ")) {
			assertThatThrownBy(() -> service.getProduct(id, AS_OF)).isInstanceOf(CardProductNotFoundException.class);
		}
	}

	@Test
	void requireSupportedAcceptsOnlyExactTableIds() {
		CardProductService service = productService(simpleRule("09174", "B-1", from("2026-01-01")),
				simpleRule("01664", "A-1", from("2026-01-01")), simpleRule("01914", "C-1", from("2026-01-01")));

		service.requireSupported("09174");
		service.requireSupported("01664");
		for (String id : List.of("9174", "009174", "09175", "", " 09174")) {
			assertThatThrownBy(() -> service.requireSupported(id)).isInstanceOf(CardProductNotFoundException.class);
		}
		assertThatThrownBy(() -> service.requireSupported(null)).isInstanceOf(CardProductNotFoundException.class);
	}

	@Test
	void todayIsSeoulDateEvenWhenClockIsUtc() {
		CardProductService service = productService(simpleRule("09174", "B-1", from("2026-01-01")),
				simpleRule("01664", "A-1", from("2026-01-01")), simpleRule("01914", "C-1", from("2026-01-01")));

		// 2026-11-16T15:30Z는 서울 기준 2026-11-17 00:30이다.
		assertThat(service.today()).isEqualTo(LocalDate.of(2026, 11, 17));
	}

	@Test
	void startupRejectsTableThatDiffersFromCatalog() {
		RuleCatalog missingOne = new RuleCatalog(List.of(simpleRule("09174", "B-1", from("2026-01-01")),
				simpleRule("01664", "A-1", from("2026-01-01"))));
		RuleCatalog extraOne = new RuleCatalog(List.of(simpleRule("09174", "B-1", from("2026-01-01")),
				simpleRule("01664", "A-1", from("2026-01-01")), simpleRule("01914", "C-1", from("2026-01-01")),
				simpleRule("09271", "D-1", from("2026-01-01"))));

		assertThatThrownBy(() -> new CardProductService(missingOne, PROFILES, CLOCK))
			.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new CardProductService(extraOne, PROFILES, CLOCK))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void startupRejectsDuplicateProfile() {
		RuleCatalog catalog = new RuleCatalog(List.of(simpleRule("09174", "B-1", from("2026-01-01"))));
		List<CardProductProfile> duplicated = List.of(new CardProductProfile("09174", "카드 B", "카드사", CardType.CREDIT),
				new CardProductProfile("09174", "카드 B2", "카드사", CardType.CREDIT));

		assertThatThrownBy(() -> new CardProductService(catalog, duplicated, CLOCK))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void supportedTableMatchesRepositoryRuleCatalog() {
		RuleCatalog catalog = RuleCatalogLoader.load();

		CardProductService service = new CardProductService(catalog, SupportedCardProducts.ALL, CLOCK);

		assertThat(service.listProducts(AS_OF)).extracting(CardProductSummary::cardProductId)
			.containsExactly("01664", "01914", "09174", "09271");
	}

	private static CardProductService productService(CardRuleVersion... versions) {
		return new CardProductService(new RuleCatalog(List.of(versions)), PROFILES, CLOCK);
	}

}
