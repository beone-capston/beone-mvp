package com.beone.api.codef.sync;

import static com.beone.api.codef.sync.SyntheticFixture.CHECKED_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.beone.api.codef.CodefCardPerformance.PerformanceMet;
import com.beone.api.codef.CodefList;
import com.beone.api.codef.sync.CodefPerformanceReference.BenefitReference;
import com.beone.api.codef.sync.CodefPerformanceReference.PerformanceEntry;
import com.beone.api.codef.sync.CodefPerformanceReference.ReferenceAmount;
import com.beone.api.codef.sync.CodefPerformanceReference.ReferenceDate;
import com.beone.api.codef.sync.CodefPerformanceReference.UnknownReason;

/**
 * 실적조회 → 카드사 조회 실적(참고). 공식 시트 기준(실적조회!H12·H17·H21:H28), 실계정 관측 없음. 기대값은 D-16과 시트 셀에서 구현 전에 정했다.
 *
 * <p>
 * D-16: 실적조회 금액은 참고 표시로만 쓰고 전월 실적에 자동으로 넣지 않는다. 그래서 참고값은 계산에 쓰이지 않음({@code usedInCalculation=false})으로
 * 고정한다. 여러 항목 중 하나를 고르지 않는다(R9).
 */
class CodefPerformanceReferenceTest {

	private static List<CodefPerformanceReference> references() {
		CodefTargetOutcome<CodefPerformanceReference> outcome = CodefTargetOutcome.judge(
				SyntheticFixture.performances("performances-kb.json"), CHECKED_AT,
				card -> CodefPerformanceReference.from(card, CHECKED_AT));
		return ((CodefTargetOutcome.Usable<CodefPerformanceReference>) outcome).items();
	}

	private static <T> List<T> items(CodefList<T> list) {
		assertThat(list).isInstanceOf(CodefList.Items.class);
		return ((CodefList.Items<T>) list).items();
	}

	@Test
	void everyReferenceIsNotUsedInCalculationAndKeepsTheCheckedAt() {
		for (CodefPerformanceReference reference : references()) {
			assertThat(reference.usedInCalculation()).isFalse();
			assertThat(reference.checkedAt()).isEqualTo(CHECKED_AT);
		}
	}

	@Test
	void allEntriesAreKeptWithoutPickingOne() {
		CodefPerformanceReference cardA = references().get(0);

		assertThat(cardA.cardName()).contains("SYN 실적 카드 A");
		List<BenefitReference> benefits = items(cardA.benefits());
		assertThat(benefits).hasSize(2);
		assertThat(items(benefits.get(0).performances())).hasSize(2);
	}

	@Test
	void documentedAmountsAndPeriodAreReadAsGiven() {
		PerformanceEntry entry = items(items(references().get(0).benefits()).get(0).performances()).get(0);

		assertThat(entry.standardUse()).isEqualTo(ReferenceAmount.of(300000));
		assertThat(entry.currentUse()).isEqualTo(ReferenceAmount.of(120000));
		assertThat(entry.requiredPerformance()).isEqualTo(ReferenceAmount.of(180000));
		assertThat(entry.startDate()).isEqualTo(ReferenceDate.of(LocalDate.of(2026, 11, 1)));
		assertThat(entry.met()).isEqualTo(PerformanceMet.SHORT);
	}

	@Test
	void currentMonthEndDateIsNotMovedToTheMonthEnd() {
		// 실적조회!H27 주석: 조회월이 당월이면 종료일은 조회일 기준 전날이다. 말일로 바꾸지 않는다.
		PerformanceEntry entry = items(items(references().get(0).benefits()).get(0).performances()).get(0);

		assertThat(entry.endDate()).isEqualTo(ReferenceDate.of(LocalDate.of(2026, 11, 15)));
	}

	@Test
	void blankRequiredPerformanceAndMetStayUnknown() {
		// 실적조회!H22·H25: 충족여부·필요실적은 옵션(△)이다. 빈 값은 0원·부족이 아니다.
		PerformanceEntry entry = items(items(references().get(0).benefits()).get(0).performances()).get(1);

		assertThat(entry.requiredPerformance()).isEqualTo(new ReferenceAmount(null, UnknownReason.ABSENT));
		assertThat(entry.requiredPerformance().value()).isNull();
		assertThat(entry.met()).isEqualTo(PerformanceMet.UNKNOWN);
		assertThat(entry.coverageCriteria()).isEmpty();
	}

	@Test
	void blankNestedListIsMissingNotZeroEntries() {
		BenefitReference second = items(references().get(0).benefits()).get(1);

		assertThat(second.performances()).isInstanceOf(CodefList.Missing.class);
		assertThat(references().get(2).benefits()).isInstanceOf(CodefList.Missing.class);
	}

	@Test
	void unparsedAmountAndDatesAreNullWithReason() {
		CodefPerformanceReference cardB = references().get(1);
		List<BenefitReference> benefits = items(cardB.benefits());
		PerformanceEntry entry = items(benefits.get(0).performances()).get(0);

		assertThat(entry.currentUse()).isEqualTo(new ReferenceAmount(null, UnknownReason.UNPARSED));
		assertThat(entry.startDate()).isEqualTo(new ReferenceDate(null, UnknownReason.UNPARSED));
		// 20261131은 yyyyMMdd 모양이지만 있는 날짜가 아니다.
		assertThat(entry.endDate()).isEqualTo(new ReferenceDate(null, UnknownReason.UNPARSED));
		assertThat(entry.met()).isEqualTo(PerformanceMet.MET);
	}

	@Test
	void kbUnprovidedCardFieldsStayEmpty() {
		// 실적조회!H14·H15: 카드구분·카드사는 KB가 제공하지 않는다(-).
		CodefPerformanceReference cardA = references().get(0);

		assertThat(cardA.cardType()).isEqualTo(Optional.empty());
		assertThat(cardA.cardCompany()).isEqualTo(Optional.empty());
		assertThat(references().toString()).doesNotContain("SYN-MASKED-0000");
	}

	@Test
	void onlyExactlyEightDigitsAreReadAsADate() {
		// 문서 형식은 yyyyMMdd다. 부호 있는 연도·자릿수가 다른 값은 날짜로 읽지 않는다(PR #83 리뷰).
		for (String text : List.of("-00010101", "+20261101", "202611011", "2026111", " 20261101")) {
			assertThat(ReferenceDate.from(Optional.of(text))).as(text)
				.isEqualTo(new ReferenceDate(null, UnknownReason.UNPARSED));
		}
		assertThat(ReferenceDate.from(Optional.of("20261101"))).isEqualTo(ReferenceDate.of(LocalDate.of(2026, 11, 1)));
	}

	@Test
	void referenceValuesNeedExactlyOneOfValueOrReason() {
		assertThatThrownBy(() -> new ReferenceAmount(null, null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ReferenceAmount(1L, UnknownReason.ABSENT))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ReferenceDate(null, null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ReferenceDate(LocalDate.of(2026, 11, 1), UnknownReason.UNPARSED))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
