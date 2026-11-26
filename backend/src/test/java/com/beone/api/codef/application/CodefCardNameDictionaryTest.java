package com.beone.api.codef.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.beone.api.card.application.CardProductProfile;
import com.beone.api.card.application.SupportedCardProducts;
import com.beone.api.codef.application.CodefCardNameDictionary.Basis;
import com.beone.api.codef.application.CodefCardNameDictionary.Entry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/**
 * CODEF 카드명 사전의 정확 일치 규칙과 사전 무결성(#268). 기대값은 관리자 지시(2026-11-26 사용자 승인)의 사전·일치 규칙에서 옮겼다.
 * 카드명은 공개 상품명이며 실사용자 응답 원문이 아니다.
 */
class CodefCardNameDictionaryTest {

	@Test
	void everyListedNameMatchesItsProductExactly() {
		assertThat(CodefCardNameDictionary.candidateFor("올라운드 티타늄카드")).hasValue("09271");
		assertThat(CodefCardNameDictionary.candidateFor("nori 체크카드(RF)")).hasValue("01664");
		assertThat(CodefCardNameDictionary.candidateFor("nori 체크카드")).hasValue("01664");
		assertThat(CodefCardNameDictionary.candidateFor("노리 체크카드")).hasValue("01664");
		assertThat(CodefCardNameDictionary.candidateFor("노리 체크카드(RF)")).hasValue("01664");
		assertThat(CodefCardNameDictionary.candidateFor("청춘대로 톡톡카드")).hasValue("09174");
		assertThat(CodefCardNameDictionary.candidateFor("청춘대로 톡톡카드(RF)")).hasValue("09174");
		assertThat(CodefCardNameDictionary.candidateFor("첵첵 체크카드")).hasValue("01914");
		assertThat(CodefCardNameDictionary.candidateFor("첵첵 체크카드(RF)")).hasValue("01914");
	}

	@Test
	void leadingAndTrailingWhitespaceIsIgnored() {
		assertThat(CodefCardNameDictionary.candidateFor("  올라운드 티타늄카드\t")).hasValue("09271");
		assertThat(CodefCardNameDictionary.candidateFor("　첵첵 체크카드 ")).hasValue("01914");
	}

	@Test
	void cardNameWithIssuerSuffixIsComparedOnceMoreByThePartBeforeTheBar() {
		// CodefHeldCard: resCardName은 "카드명|발급사" 형식일 수 있다
		assertThat(CodefCardNameDictionary.candidateFor("올라운드 티타늄카드|KB국민카드")).hasValue("09271");
		assertThat(CodefCardNameDictionary.candidateFor(" nori 체크카드(RF) | KB국민카드 ")).hasValue("01664");
		// '|' 앞부분도 정확히 같아야 한다
		assertThat(CodefCardNameDictionary.candidateFor("올라운드 티타늄|KB국민카드")).isEmpty();
		assertThat(CodefCardNameDictionary.candidateFor("|올라운드 티타늄카드")).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			// 대소문자
			"NORI 체크카드(RF)", "Nori 체크카드",
			// 부분 일치·앞뒤 덧붙임
			"올라운드 티타늄", "티타늄카드", "올라운드 티타늄카드 플러스", "KB 청춘대로 톡톡카드", "첵첵",
			// 안쪽 공백 차이
			"올라운드  티타늄카드", "올라운드티타늄카드", "nori체크카드(RF)",
			// 다른 괄호 표기
			"nori 체크카드 (RF)", "청춘대로 톡톡카드[RF]",
			// 동일 계열·변형 카드
			"펭수 노리 체크카드", "펭수 노리", "위글위글 첵첵 체크카드", "위글위글 첵첵", "락스타 체크카드", "해피노리 체크카드",
			"노리2 체크카드",
			// 빈 값
			"", "   ", "|" })
	void variantsAndSiblingCardsAreNotMatched(String cardName) {
		assertThat(CodefCardNameDictionary.candidateFor(cardName)).isEmpty();
	}

	@Test
	void missingCardNameHasNoCandidate() {
		assertThat(CodefCardNameDictionary.candidateFor(null)).isEqualTo(Optional.empty());
	}

	@Test
	void dictionaryHasNoDuplicateNameAcrossOrWithinProducts() {
		List<String> names = CodefCardNameDictionary.entries().stream().map(Entry::cardName).toList();

		assertThat(names).doesNotHaveDuplicates();
	}

	@Test
	void indexRejectsANameMappedToTwoProducts() {
		List<Entry> clash = List.of(new Entry("01664", "노리 체크카드", Basis.ASSUMED),
				new Entry("01914", "노리 체크카드", Basis.ASSUMED));

		assertThatThrownBy(() -> CodefCardNameDictionary.index(clash)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void entryNamesMustBeStripped() {
		assertThatThrownBy(() -> new Entry("09271", " 올라운드 티타늄카드", Basis.ASSUMED))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Entry("09271", "", Basis.ASSUMED)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void productIdsAreExactlyTheSupportedCardProducts() {
		List<String> supported = SupportedCardProducts.ALL.stream().map(CardProductProfile::cardProductId).toList();

		assertThat(CodefCardNameDictionary.entries().stream().map(Entry::cardProductId).distinct())
			.containsExactlyInAnyOrderElementsOf(supported);
	}

	@Test
	void basisFollowsTheRecordedEvidence() {
		Map<String, Basis> basis = CodefCardNameDictionary.entries()
			.stream()
			.collect(Collectors.toMap(Entry::cardName, Entry::basis));

		assertThat(basis).containsExactlyInAnyOrderEntriesOf(Map.of("올라운드 티타늄카드", Basis.OBSERVED,
				"nori 체크카드(RF)", Basis.OBSERVED, "nori 체크카드", Basis.ASSUMED, "노리 체크카드", Basis.ASSUMED,
				"노리 체크카드(RF)", Basis.ASSUMED, "청춘대로 톡톡카드", Basis.ASSUMED, "청춘대로 톡톡카드(RF)", Basis.ASSUMED,
				"첵첵 체크카드", Basis.ASSUMED, "첵첵 체크카드(RF)", Basis.ASSUMED));
	}

}
