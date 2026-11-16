package com.beone.api.card.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.card.LimitBucketId;
import com.beone.api.benefit.result.ApplicationStatus;
import com.beone.api.benefit.rule.BenefitService;
import com.beone.api.benefit.rule.CardRuleVersion;
import com.beone.api.benefit.rule.LimitBucket;
import com.beone.api.benefit.rule.RuleCatalog;
import com.beone.api.benefit.rule.RuleSelection;
import com.beone.api.benefit.rule.RuleTerm;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 규칙 카탈로그를 읽어 카드 상품 목록과 상세를 만든다. 카드 상품 테이블은 두지 않는다(D-23).
 *
 * <p>규칙 선택은 {@link RuleCatalog#select}에 맡기고 이 클래스는 결과를 응답 모양으로 옮기기만 한다.
 * 생성할 때 표시 정보 표의 상품 ID와 카탈로그의 카드 ID가 정확히 같은지 확인하며, 다르면 서버가 뜨지 않는다.
 */
@Service
public class CardProductService {

	/** {@code asOf}를 생략했을 때 오늘을 정하는 시간대. */
	static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final RuleCatalog ruleCatalog;

	private final List<CardProductProfile> profiles;

	private final Clock clock;

	/**
	 * 지원 카드 4종 표와 함께 만든다. {@link Clock} 빈이 하나 있으면 그것을, 없으면 시스템 시계를 쓴다.
	 * @param ruleCatalog 규칙 카탈로그
	 * @param clock 오늘 날짜를 정할 시계(테스트에서 고정)
	 */
	@Autowired
	public CardProductService(RuleCatalog ruleCatalog, ObjectProvider<Clock> clock) {
		this(ruleCatalog, SupportedCardProducts.ALL, clock.getIfUnique(Clock::systemUTC));
	}

	CardProductService(RuleCatalog ruleCatalog, List<CardProductProfile> profiles, Clock clock) {
		this.ruleCatalog = Objects.requireNonNull(ruleCatalog, "ruleCatalog");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.profiles = profiles.stream().sorted(Comparator.comparing(CardProductProfile::cardProductId)).toList();
		requireSameProducts(this.profiles, ruleCatalog);
	}

	// 표에만 있거나 카탈로그에만 있는 카드가 있으면 목록이 조용히 틀어지므로 시작 시 거부한다.
	private static void requireSameProducts(List<CardProductProfile> profiles, RuleCatalog ruleCatalog) {
		Set<String> profileIds = new TreeSet<>();
		for (CardProductProfile profile : profiles) {
			if (!profileIds.add(profile.cardProductId())) {
				throw new IllegalStateException("duplicate card product " + profile.cardProductId());
			}
		}
		Set<String> catalogIds = new TreeSet<>();
		ruleCatalog.cardIds().forEach(id -> catalogIds.add(id.value()));
		if (!profileIds.equals(catalogIds)) {
			throw new IllegalStateException(
					"card product table " + profileIds + " does not match rule catalog " + catalogIds);
		}
	}

	/** Asia/Seoul 기준 오늘. */
	public LocalDate today() {
		return LocalDate.now(this.clock.withZone(SEOUL));
	}

	/**
	 * 지원 카드 전체를 상품 ID 문자열 오름차순으로 돌려준다(결정 27 A1-3).
	 * @param asOf 규칙 유효성 판단 날짜
	 * @return 상품 목록
	 */
	public List<CardProductSummary> listProducts(LocalDate asOf) {
		Objects.requireNonNull(asOf, "asOf");
		return this.profiles.stream().map(profile -> summaryOf(profile, asOf)).toList();
	}

	/**
	 * 상품 하나의 판정일 기준 규칙 요약을 돌려준다.
	 * @param cardProductId 상품 ID. 문자열 그대로 비교한다
	 * @param asOf 규칙 유효성 판단 날짜
	 * @return 상품 상세
	 * @throws CardProductNotFoundException 지원하지 않는 상품 ID일 때
	 */
	public CardProductDetail getProduct(String cardProductId, LocalDate asOf) {
		Objects.requireNonNull(asOf, "asOf");
		CardProductProfile profile = this.profiles.stream()
			.filter(candidate -> candidate.cardProductId().equals(cardProductId))
			.findFirst()
			.orElseThrow(CardProductNotFoundException::new);
		RuleSelection selection = select(profile, asOf);
		if (!(selection instanceof RuleSelection.Selected selected)) {
			return new CardProductDetail(profile.cardProductId(), profile.cardName(), asOf, statusOf(selection), null,
					List.of(), List.of());
		}
		CardRuleVersion rule = selected.rule();
		return new CardProductDetail(profile.cardProductId(), profile.cardName(), asOf, RuleStatus.VERIFIED,
				ruleOf(rule), rule.services().stream().map(service -> serviceOf(service, rule)).toList(),
				rule.limitBuckets().stream().map(bucket -> bucketOf(bucket, rule)).toList());
	}

	private CardProductSummary summaryOf(CardProductProfile profile, LocalDate asOf) {
		RuleSelection selection = select(profile, asOf);
		if (selection instanceof RuleSelection.Selected selected) {
			CardRuleVersion rule = selected.rule();
			return new CardProductSummary(profile.cardProductId(), profile.cardName(), profile.issuer(),
					profile.cardType(), RuleStatus.VERIFIED, rule.ruleVersion(),
					rule.sources().stream().map(OfficialSourceView::from).toList());
		}
		return new CardProductSummary(profile.cardProductId(), profile.cardName(), profile.issuer(),
				profile.cardType(), statusOf(selection), null, List.of());
	}

	private RuleSelection select(CardProductProfile profile, LocalDate asOf) {
		return this.ruleCatalog.select(CardId.of(profile.cardProductId()), asOf);
	}

	private static RuleStatus statusOf(RuleSelection selection) {
		return switch (selection) {
			case RuleSelection.Selected ignored -> RuleStatus.VERIFIED;
			case RuleSelection.Unavailable unavailable ->
				(unavailable.status() == ApplicationStatus.RULE_EXPIRED) ? RuleStatus.RULE_EXPIRED
						: RuleStatus.NO_VERIFIED_RULE;
		};
	}

	private static CardProductDetail.Rule ruleOf(CardRuleVersion rule) {
		return new CardProductDetail.Rule(rule.ruleVersion(), rule.status(), rule.validity().from(),
				rule.validity().to().orElse(null), valueOrNull(rule.policy().performanceWindow()),
				valueOrNull(rule.policy().cancellationAttribution()),
				valueOrNull(rule.policy().limitDeductionOrder()));
	}

	private static CardProductDetail.Service serviceOf(BenefitService service, CardRuleVersion rule) {
		String name = service.details().displayName().orElse(service.id().value());
		return new CardProductDetail.Service(service.id().value(), name, service.kind(),
				SupportStatus.of(service, rule.policy()),
				service.limitBuckets().stream().map(LimitBucketId::value).toList(), service.requiresSelection());
	}

	private static CardProductDetail.LimitBucket bucketOf(LimitBucket bucket, CardRuleVersion rule) {
		List<CardProductDetail.Tier> tiers = bucket.tiers()
			.stream()
			.map(tier -> new CardProductDetail.Tier(tier.minimumPerformance().value(), tier.monthlyCap().value()))
			.toList();
		List<String> sharedBy = rule.services()
			.stream()
			.filter(service -> service.limitBuckets().contains(bucket.id()))
			.map(service -> service.id().value())
			.toList();
		return new CardProductDetail.LimitBucket(bucket.id().value(), tiers,
				bucket.parent().map(LimitBucketId::value).orElse(null), valueOrNull(rule.policy().limitPeriod()),
				sharedBy);
	}

	// 미확정 조건은 0·기본값으로 바꾸지 않고 null(확인 필요)로 둔다.
	private static <T> T valueOrNull(RuleTerm<T> term) {
		return term.isResolved() ? term.resolvedValue() : null;
	}

}
