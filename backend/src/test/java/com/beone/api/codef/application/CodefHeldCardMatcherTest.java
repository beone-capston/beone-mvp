package com.beone.api.codef.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import com.beone.api.codef.CodefHeldCard;
import com.beone.api.codef.application.CodefHeldCardMatcher.ExistingCard;
import com.beone.api.codef.application.CodefHeldCardMatcher.Resolution;
import com.beone.api.codef.application.CodefHeldCardMatcher.SkipReason;
import com.beone.api.codef.sync.CodefHeldCardCandidate;
import org.junit.jupiter.api.Test;

/**
 * CODEF 보유카드 대조. 기대값은 관리자 판단 P8(카드명+발급일자로 정확히 하나일 때만 같은 카드)·Q1(둘 다 없는 발급일자도 같은 값, m=0이면 새로,
 * n=1·m=1이면 같은 카드, 그 밖은 건너뜀)·Q2(카드명 없음·표시 이름 1~30자 밖은 건너뜀, 자르지 않음)에서 썼다. 카드명은 합성 값이다.
 */
class CodefHeldCardMatcherTest {

	@Test
	void newCardsAreCreatedWithTheStrippedDisplayName() {
		List<Resolution> resolutions = CodefHeldCardMatcher.match(List.of(card("  가짜 카드 A ", null)), List.of());

		assertThat(resolutions).containsExactly(new Resolution.Create("  가짜 카드 A ", "가짜 카드 A", null));
	}

	@Test
	void exactlyOneExistingCardWithTheSameNameAndIssueDateIsTheSameCard() {
		List<Resolution> resolutions = CodefHeldCardMatcher.match(
				List.of(card("가짜 카드 A", null), card("가짜 카드 B", "20240101")),
				List.of(new ExistingCard(11, "가짜 카드 A", null), new ExistingCard(12, "가짜 카드 B", "20240101")));

		assertThat(resolutions).containsExactly(new Resolution.Existing(11), new Resolution.Existing(12));
	}

	@Test
	void issueDateSeparatesCardsWithTheSameName() {
		List<Resolution> resolutions = CodefHeldCardMatcher.match(List.of(card("가짜 카드 A", "20250101")),
				List.of(new ExistingCard(11, "가짜 카드 A", "20240101"), new ExistingCard(12, "가짜 카드 A", null)));

		assertThat(resolutions).containsExactly(new Resolution.Create("가짜 카드 A", "가짜 카드 A", "20250101"));
	}

	@Test
	void nameIsComparedExactly() {
		List<Resolution> resolutions = CodefHeldCardMatcher.match(List.of(card("가짜 카드 a", null)),
				List.of(new ExistingCard(11, "가짜 카드 A", null)));

		assertThat(resolutions).containsExactly(new Resolution.Create("가짜 카드 a", "가짜 카드 a", null));
	}

	@Test
	void sameCardsInTheResponseAreAllCreatedOnlyWhenNoneExist() {
		List<Resolution> first = CodefHeldCardMatcher.match(List.of(card("가짜 카드 B", null), card("가짜 카드 B", null)),
				List.of());
		List<Resolution> again = CodefHeldCardMatcher.match(List.of(card("가짜 카드 B", null), card("가짜 카드 B", null)),
				List.of(new ExistingCard(21, "가짜 카드 B", null), new ExistingCard(22, "가짜 카드 B", null)));
		List<Resolution> oneLeft = CodefHeldCardMatcher.match(
				List.of(card("가짜 카드 B", null), card("가짜 카드 B", null)),
				List.of(new ExistingCard(21, "가짜 카드 B", null)));

		assertThat(first).containsOnly(new Resolution.Create("가짜 카드 B", "가짜 카드 B", null)).hasSize(2);
		assertThat(again).containsOnly(new Resolution.Skipped(SkipReason.AMBIGUOUS)).hasSize(2);
		assertThat(oneLeft).containsOnly(new Resolution.Skipped(SkipReason.AMBIGUOUS)).hasSize(2);
	}

	@Test
	void twoExistingCardsForOneResponseCardAreAmbiguous() {
		List<Resolution> resolutions = CodefHeldCardMatcher.match(List.of(card("가짜 카드 C", null)),
				List.of(new ExistingCard(31, "가짜 카드 C", null), new ExistingCard(32, "가짜 카드 C", null)));

		assertThat(resolutions).containsExactly(new Resolution.Skipped(SkipReason.AMBIGUOUS));
	}

	@Test
	void cardsWithoutAUsableDisplayNameAreSkippedNotTruncated() {
		String thirty = "가".repeat(30);
		List<Resolution> resolutions = CodefHeldCardMatcher.match(
				List.of(card(null, null), card("   ", null), card(thirty + "나", null), card(" " + thirty + " ", null)),
				List.of());

		assertThat(resolutions).containsExactly(new Resolution.Skipped(SkipReason.CARD_NAME_ABSENT),
				new Resolution.Skipped(SkipReason.DISPLAY_NAME_INVALID),
				new Resolution.Skipped(SkipReason.DISPLAY_NAME_INVALID),
				new Resolution.Create(" " + thirty + " ", thirty, null));
	}

	@Test
	void displayNameLengthCountsCodePoints() {
		String supplementary = "\uD840\uDC00".repeat(30);
		assertThat(CodefHeldCardMatcher.match(List.of(card(supplementary, null)), List.of()))
			.containsExactly(new Resolution.Create(supplementary, supplementary, null));
	}

	private static CodefHeldCardCandidate card(String name, String issueDate) {
		return CodefHeldCardCandidate.from(new CodefHeldCard(Optional.ofNullable(name), Optional.empty(),
				CodefHeldCard.Dormancy.NOT_DORMANT, Optional.ofNullable(issueDate), Optional.empty()));
	}

}
