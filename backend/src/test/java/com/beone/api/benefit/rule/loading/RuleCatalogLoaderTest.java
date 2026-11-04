package com.beone.api.benefit.rule.loading;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.card.LimitBucketId;
import com.beone.api.benefit.card.MerchantClass;
import com.beone.api.benefit.card.PaymentMethod;
import com.beone.api.benefit.card.ServiceId;
import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.rule.BenefitCalculation;
import com.beone.api.benefit.rule.BenefitKind;
import com.beone.api.benefit.rule.BenefitService;
import com.beone.api.benefit.rule.CancellationAttribution;
import com.beone.api.benefit.rule.CardRuleVersion;
import com.beone.api.benefit.rule.FractionalWonPolicy;
import com.beone.api.benefit.rule.GracePartialPerformance;
import com.beone.api.benefit.rule.GraceSpan;
import com.beone.api.benefit.rule.InclusionUnconfirmed;
import com.beone.api.benefit.rule.LimitBucket;
import com.beone.api.benefit.rule.LimitTier;
import com.beone.api.benefit.rule.MerchantTarget;
import com.beone.api.benefit.rule.NewCardGrace;
import com.beone.api.benefit.rule.RuleSelection;
import com.beone.api.benefit.rule.ServiceTier;
import com.beone.api.benefit.rule.StackingBasis;
import com.beone.api.benefit.rule.TermBasis;
import com.beone.api.benefit.rule.TransactionDateBasis;
import com.beone.api.benefit.rule.VerificationStatus;

/**
 * 로더가 만든 모델을 근거 표에서 손으로 옮긴 값과 비교한다. JSON을 다시 읽어 기대값을 만들지 않는다.
 */
class RuleCatalogLoaderTest {

	private static final Set<PaymentMethod> ALL = Set.of(PaymentMethod.DIRECT, PaymentMethod.ELIGIBLE_SIMPLE_PAY,
			PaymentMethod.AUTOPAY);

	private static final Set<PaymentMethod> AUTOPAY = Set.of(PaymentMethod.AUTOPAY);

	private static final String GIFT = "GIFT_CARD_PREPAID";

	private static Map<String, CardRuleVersion> rules;

	@BeforeAll
	static void load() {
		rules = new LinkedHashMap<>();
		RuleCatalogLoader.loadVersions().forEach(rule -> rules.put(rule.cardId().toString(), rule));
	}

	@Test
	void loadsFourVerifiedCardsWithExpectedVersionsAndStartDates() {
		assertThat(rules).containsOnlyKeys("09174", "09271", "01664", "01914");
		assertVersion("09174", "09174-A-20170126-9120-00109-07", "2026-07-14");
		assertVersion("09271", "09271-A-20181030-9120-00489-02", "2024-04-17");
		assertVersion("01664", "01664-B-20101223-9162-00045-12", "2026-08-18");
		assertVersion("01914", "01914-B-20200608-9162-00529-07", "2026-08-18");
	}

	@Test
	void catalogSelectsEachCardOnItsStartDate() {
		var catalog = RuleCatalogLoader.load();
		rules.values().forEach(rule -> assertThat(catalog.select(rule.cardId(), rule.validity().from()))
			.isInstanceOf(RuleSelection.Selected.class));
		assertThat(catalog.select(CardId.of("09174"), LocalDate.parse("2026-07-13")))
			.isInstanceOf(RuleSelection.Unavailable.class);
	}

	@Test
	void card01664MatchesEvidenceTable() {
		String card = "01664";
		assertBucket(card, "COMMON", null, tier(200_000, 10_000), tier(300_000, 20_000), tier(500_000, 30_000),
				tier(1_000_000, 50_000));
		assertBucket(card, "TRANSIT", "COMMON", tier(300_000, 2_000));
		assertBucket(card, "TELECOM", "COMMON", tier(300_000, 2_500));
		assertRate(card, "CGV", 3_500, 200_000, 10_000, 20_000L, "COMMON");
		assertRate(card, "OUTBACK_VIPS", 2_000, 200_000, 30_000, 50_000L, "COMMON");
		assertRate(card, "STARBUCKS", 2_000, 200_000, 10_000, 20_000L, "COMMON");
		assertRate(card, "THEME_PARK", 5_000, 200_000, 30_000, 50_000L, "COMMON");
		assertRate(card, "GS25", 500, 200_000, 10_000, 20_000L, "COMMON");
		assertRate(card, "KYOBO", 500, 200_000, 20_000, 50_000L, "COMMON");
		assertRate(card, "TRANSIT", 1_000, 300_000, 0, null, "TRANSIT");
		assertFixed(card, "TELECOM", 50_000, null, "TELECOM", new long[] { 300_000, 2_500 });
		assertThat(service(card, "TELECOM").paymentMethods()).isEqualTo(AUTOPAY);
		assertThat(service(card, "TRANSIT").paymentMethods()).isEqualTo(ALL);
		assertGrace(card, GraceSpan.DAYS_FROM_REGISTRATION, 60, 200_000, "CGV", "OUTBACK_VIPS", "STARBUCKS", "THEME_PARK",
				"GS25", "KYOBO");
		var policy = rules.get(card).policy();
		assertThat(policy.performanceDateBasis().resolvedValue()).isEqualTo(TransactionDateBasis.USAGE);
		assertThat(policy.cancellationAttribution().resolvedValue()).isEqualTo(CancellationAttribution.RECEIPT_MONTH);
	}

	@Test
	void card01914MatchesEvidenceTable() {
		String card = "01914";
		assertBucket(card, "COMMON", null, tier(300_000, 10_000), tier(600_000, 20_000));
		
		for (String id : List.of("CU", "STARBUCKS", "CGV", "TENBYTEN")) {
			assert01914Service(id, 10_000);
		}
		for (String id : List.of("SIMPLE_PAY", "BEAUTY", "BOOKSTORE", "CULTURE")) {
			assert01914Service(id, 20_000);
		}
		assert01914Service("TRANSIT", 30_000);
		assertGrace(card, GraceSpan.UNTIL_END_OF_NEXT_MONTH, null, 300_000, "CU", "STARBUCKS", "CGV", "TENBYTEN",
				"SIMPLE_PAY", "BEAUTY", "BOOKSTORE", "CULTURE", "TRANSIT");
	}

	private void assert01914Service(String id, long minimum) {
		assertBucket("01914", id, "COMMON", tier(300_000, 2_000), tier(600_000, 4_000));
		assertFixed("01914", id, minimum, null, id, new long[] { 300_000, 1_000 }, new long[] { 600_000, 2_000 });
		assertThat(service("01914", id).paymentMethods()).isEqualTo(ALL);
	}

	@Test
	void card09174MatchesEvidenceTableAndSharesBasicBucket() {
		String card = "09174";
		assertBucket(card, "GREAT", null, tier(300_000, 10_000));
		assertBucket(card, "ENJOY", null, tier(300_000, 5_000));
		assertBucket(card, "CHECK", null, tier(300_000, 5_000));
		assertBucket(card, "BASIC", null, tier(300_000, 5_000));
		assertRate(card, "GREAT", 5_000, 300_000, 0, null, "GREAT");
		assertRate(card, "ENJOY", 2_000, 300_000, 0, null, "ENJOY");
		assertRate(card, "CHECK", 1_000, 300_000, 0, null, "CHECK");
		assertRate(card, "BASIC_TELECOM", 1_000, 300_000, 0, null, "BASIC");
		assertRate(card, "BASIC_TRANSIT", 1_000, 300_000, 0, null, "BASIC");

		assertThat(service(card, "CHECK").target()).isEqualTo(MerchantTarget.allMerchants());
		assertThat(service(card, "CHECK").paymentMethods()).containsExactly(PaymentMethod.ELIGIBLE_SIMPLE_PAY);
		assertTarget(card, "GREAT", "STARBUCKS_STORE");
		assertTarget(card, "BASIC_TELECOM", "TELECOM_SKT", "TELECOM_KT", "TELECOM_LGU");
		assertTarget(card, "BASIC_TRANSIT", "TRANSIT_BUS_SUBWAY", "TAXI");
		assertThat(service(card, "BASIC_TELECOM").paymentMethods()).isEqualTo(AUTOPAY);
		assertThat(service(card, "BASIC_TRANSIT").paymentMethods()).isEqualTo(ALL);
		assertThat(service(card, "GREAT").paymentMethods()).isEqualTo(ALL);
		assertThat(service(card, "BASIC_TELECOM").details().displayName()).contains("BASIC");
		assertThat(service(card, "BASIC_TRANSIT").details().displayName()).contains("BASIC");

		for (String id : List.of("GREAT", "ENJOY", "BASIC_TELECOM", "BASIC_TRANSIT")) {
			assertThat(service(card, id).stackableWith().resolvedValue()).containsExactly(ServiceId.of("CHECK"));
			assertThat(service(card, id).details().stackingBasis()).contains(StackingBasis.PRE_DISCOUNT_EACH_BUCKET);
		}
		assertThat(service(card, "CHECK").stackableWith().resolvedValue()).containsExactlyInAnyOrder(ServiceId.of("GREAT"),
				ServiceId.of("ENJOY"), ServiceId.of("BASIC_TELECOM"), ServiceId.of("BASIC_TRANSIT"));
		assertThat(service(card, "CHECK").details().stackingBasis()).contains(StackingBasis.PRE_DISCOUNT_EACH_BUCKET);

		assertGrace(card, GraceSpan.UNTIL_END_OF_NEXT_MONTH, null, 300_000, "GREAT", "ENJOY", "CHECK", "BASIC_TELECOM",
				"BASIC_TRANSIT");
	}

	@Test
	void card09271MatchesEvidenceTableAndKeepsFuelUnresolved() {
		String card = "09271";
		LimitTier[] tiers = { tier(400_000, 10_000), tier(800_000, 30_000), tier(1_200_000, 50_000) };
		assertBucket(card, "FUEL", null, tiers);
		assertBucket(card, "STORE", null, tiers);

		BenefitService base = service(card, "BASE");
		assertThat(base.kind()).isEqualTo(BenefitKind.POINT_ACCRUAL);
		assertThat(base.limitBuckets()).isEmpty();
		assertThat(base.tiers()).singleElement().satisfies(tier -> {
			assertThat(tier.minimumPerformance()).isEqualTo(Won.ZERO);
			assertThat(tier.calculation().resolvedValue()).isEqualTo(BenefitCalculation.rate(100));
		});

		BenefitService fuel = service(card, "FUEL");
		assertThat(fuel.requiresSelection()).isTrue();
		assertThat(fuel.tiers()).singleElement().satisfies(tier -> assertThat(tier.calculation().isResolved()).isFalse());
		assertThat(fuel.fractionalWon().isResolved()).isFalse();
		assertThat(fuel.details().fractionalWonBasis()).isEmpty();

		assertRate(card, "STORE", 500, 400_000, 0, null, "STORE");
		assertThat(service(card, "STORE").requiresSelection()).isTrue();
		assertTarget(card, "STORE", "HYPERMARKET_EMART", "HYPERMARKET_LOTTEMART", "HYPERMARKET_HOMEPLUS", "GS25_STORE",
				"CU_STORE", "COFFEE_BEVERAGE", "PHARMACY");
		assertGrace(card, GraceSpan.UNTIL_END_OF_NEXT_MONTH, null, 400_000, "FUEL", "STORE");
	}

	@Test
	void excludedClassesHoldOnlyApprovedCodes() {
		assertThat(codes(service("09271", "BASE").excludedClasses())).containsExactlyInAnyOrder("CANCELLED_AMOUNT",
				"CASH_ADVANCE", "CARD_LOAN", "TAX", "ELECTRICITY_WATER", "APARTMENT_FEE", "GOVERNMENT_SUBSIDY",
				"UNIVERSITY_TUITION", "SCHOOL_PAYMENT", "FEES_AND_INTEREST", "LATE_FEE", "ANNUAL_FEE", GIFT);
		assertThat(codes(service("09174", "BASIC_TRANSIT").excludedClasses())).containsExactlyInAnyOrder(GIFT,
				"TRANSIT_INTERCITY_BUS");
		assertThat(codes(service("09174", "BASIC_TELECOM").excludedClasses())).containsExactly(GIFT);
		assertThat(service("01664", "THEME_PARK").excludedClasses()).isEmpty();
		assertThat(codes(service("01664", "STARBUCKS").excludedClasses())).containsExactly(GIFT);
	}

	@Test
	void conditionsWithoutCodesStayAsUnevaluatedExclusions() {
		assertThat(service("09174", "CHECK").details().unevaluatedExclusions()).contains("ISP 이용금액", "MISP 이용금액", "무이자할부");
		assertThat(service("09174", "GREAT").details().unevaluatedExclusions()).contains("건물 내 임대매장", "무이자할부");
		assertThat(service("09174", "BASIC_TELECOM").details().unevaluatedExclusions()).contains("알뜰폰");
		assertThat(service("09174", "BASIC_TRANSIT").details().unevaluatedExclusions()).doesNotContain("알뜰폰");
		assertThat(service("01664", "CGV").details().unevaluatedExclusions()).contains("매점 이용분");
		assertThat(service("01664", "KYOBO").details().unevaluatedExclusions()).contains("핫트랙스");
		assertThat(service("01914", "SIMPLE_PAY").details().unevaluatedExclusions()).contains("QR/바코드 결제");
		assertThat(service("09271", "BASE").details().hasUnevaluatedExclusions()).isTrue();
		assertThat(service("01664", "THEME_PARK").details().hasUnevaluatedExclusions()).isFalse();
	}

	@Test
	void inclusionUnconfirmedPairsAreCarriedInTheModel() {
		assertThat(service("09271", "BASE").details().inclusionUnconfirmed())
			.containsExactly(pair("LOCAL_TAX", "TAX"));
		assertThat(service("09271", "STORE").details().inclusionUnconfirmed()).contains(pair("STARBUCKS_STORE", "COFFEE_BEVERAGE"));
		assertThat(service("09174", "BASIC_TELECOM").details().inclusionUnconfirmed()).isEmpty();
		assertThat(service("09174", "BASIC_TRANSIT").details().inclusionUnconfirmed()).isNotEmpty();
		assertThat(rules.get("09271").policy().inclusionUnconfirmed()).isNotEmpty();
	}

	@Test
	void truncationKeepsUserDecisionBasis() {
		assertThat(service("01664", "CGV").fractionalWon().resolvedValue()).isEqualTo(FractionalWonPolicy.TRUNCATE);
		assertThat(service("01664", "CGV").details().fractionalWonBasis())
			.contains(new TermBasis(TermBasis.Kind.USER_DECISION, "D-03"));
	}

	@Test
	void stackingBasisAppearsOnlyOnCard09174() {
		rules.values().stream().filter(rule -> !rule.cardId().equals(CardId.of("09174"))).forEach(rule ->
				rule.services().forEach(service -> assertThat(service.details().stackingBasis()).isEmpty()));
	}

	private static void assertVersion(String card, String version, String from) {
		CardRuleVersion rule = rules.get(card);
		assertThat(rule.ruleVersion()).isEqualTo(version);
		assertThat(rule.validity().from()).isEqualTo(LocalDate.parse(from));
		assertThat(rule.validity().to()).isEmpty();
		assertThat(rule.status()).isEqualTo(VerificationStatus.VERIFIED);
		assertThat(rule.evaluationOnly()).isFalse();
	}

	private static void assertBucket(String card, String id, String parent, LimitTier... tiers) {
		LimitBucket bucket = rules.get(card)
			.limitBuckets()
			.stream()
			.filter(candidate -> candidate.id().equals(LimitBucketId.of(id)))
			.findFirst()
			.orElseThrow();
		assertThat(bucket.tiers()).containsExactly(tiers);
		assertThat(bucket.parent()).isEqualTo(Optional.ofNullable(parent).map(LimitBucketId::of));
	}

	private static void assertRate(String card, String id, int basisPoints, long performance, long minimum, Long maximum,
			String bucket) {
		BenefitService service = service(card, id);
		assertAmounts(service, minimum, maximum, bucket);
		assertThat(service.tiers()).singleElement().satisfies(tier -> {
			assertThat(tier.minimumPerformance()).isEqualTo(Won.of(performance));
			assertThat(tier.calculation().resolvedValue()).isEqualTo(BenefitCalculation.rate(basisPoints));
		});
	}

	private static void assertFixed(String card, String id, long minimum, Long maximum, String bucket, long[]... tiers) {
		BenefitService service = service(card, id);
		assertAmounts(service, minimum, maximum, bucket);
		List<long[]> actual = service.tiers().stream().map(RuleCatalogLoaderTest::fixedRow).toList();
		assertThat(actual).hasSameSizeAs(tiers);
		for (int index = 0; index < tiers.length; index++) {
			assertThat(actual.get(index)).containsExactly(tiers[index]);
		}
	}

	private static long[] fixedRow(ServiceTier tier) {
		var fixed = (BenefitCalculation.FixedAmount) tier.calculation().resolvedValue();
		return new long[] { tier.minimumPerformance().value(), fixed.amount().value() };
	}

	private static void assertAmounts(BenefitService service, long minimum, Long maximum, String bucket) {
		assertThat(service.minimumAmount()).isEqualTo(Won.of(minimum));
		assertThat(service.maximumTargetAmount()).isEqualTo(Optional.ofNullable(maximum).map(Won::of));
		assertThat(service.limitBuckets()).containsExactly(LimitBucketId.of(bucket));
	}

	private static void assertTarget(String card, String id, String... classes) {
		assertThat(service(card, id).target()).isEqualTo(MerchantTarget.only(
				Arrays.stream(classes).map(MerchantClass::of).collect(Collectors.toSet())));
	}

	@Test
	void gracePartialPerformanceFollowsTheWordingOfEachCard() {
		// "실적이 없는 경우에도" 문구는 일부 실적을 말하지 않고, "1구간 서비스 제공" 문구는 실적을 가리지 않는다(D-20).
		for (String card : List.of("09174", "01664", "09271")) {
			assertThat(rules.get(card).policy().newCardGrace().resolvedValue().orElseThrow().partialPerformance())
				.isEqualTo(GracePartialPerformance.NEEDS_CONFIRMATION);
		}
		assertThat(rules.get("01914").policy().newCardGrace().resolvedValue().orElseThrow().partialPerformance())
			.isEqualTo(GracePartialPerformance.DEEMED_TIER);
	}

	private static void assertGrace(String card, GraceSpan span, Integer days, long deemed, String... services) {
		NewCardGrace grace = rules.get(card).policy().newCardGrace().resolvedValue().orElseThrow();
		assertThat(grace.span()).isEqualTo(span);
		assertThat(grace.days()).isEqualTo(Optional.ofNullable(days));
		assertThat(grace.deemedPerformance()).isEqualTo(Won.of(deemed));
		assertThat(grace.services()).containsExactlyInAnyOrder(Arrays.stream(services).map(ServiceId::of).toArray(ServiceId[]::new));
	}

	private static BenefitService service(String card, String id) {
		return rules.get(card)
			.services()
			.stream()
			.filter(candidate -> candidate.id().equals(ServiceId.of(id)))
			.findFirst()
			.orElseThrow();
	}

	private static LimitTier tier(long performance, long cap) {
		return new LimitTier(Won.of(performance), Won.of(cap));
	}

	private static InclusionUnconfirmed pair(String merchantClass, String relatedClass) {
		return new InclusionUnconfirmed(MerchantClass.of(merchantClass), MerchantClass.of(relatedClass));
	}

	private static Set<String> codes(Set<MerchantClass> classes) {
		return classes.stream().map(MerchantClass::toString).collect(Collectors.toSet());
	}

}
