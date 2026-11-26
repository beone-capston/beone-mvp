package com.beone.api.codef.sync;

import static com.beone.api.codef.sync.SyntheticFixture.CHECKED_AT;
import static com.beone.api.codef.sync.SyntheticFixture.KB;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

import com.beone.api.codef.CodefCardPerformance;
import com.beone.api.codef.application.CodefCardNameDictionary;
import com.beone.api.codef.sync.CodefCardLink.Reason;
import com.beone.api.codef.sync.CodefLedgerCandidateRow.Decision;
import com.beone.api.codef.transport.CodefCallResult;

/**
 * 카드 연결(결정 R1)의 이름 사전 단계(#270, 2026-11-27 사용자 결정). 정확 일치가 우선이고, 없을 때만 행 카드명을 이름 사전으로 상품 ID로 바꿔 같은 상품이
 * 되는 보유카드가 정확히 한 장이면 연결한다. 기대값은 관리자 지시의 규칙에서 구현 전에 정했다. 카드명은 공개 상품명이고 응답은 합성이다.
 */
class CodefCardLinkerDictionaryTest {

	private static final String NORI = "nori 체크카드(RF)";

	private static final String TITANIUM = "올라운드 티타늄카드";

	private static final String TOKTOK = "청춘대로 톡톡카드";

	private static CodefTargetOutcome<CodefHeldCardCandidate> heldCards() {
		return CodefTargetOutcome.judge(SyntheticFixture.heldCards("ledger-held-cards-dictionary.json"), CHECKED_AT,
				CodefHeldCardCandidate::from);
	}

	private static CodefCardLinker linker() {
		return CodefCardLinker.of(heldCards(), CodefCardNameDictionary::candidateFor);
	}

	private static CodefCardLink notLinked(Reason reason) {
		return new CodefCardLink.NotLinked(reason);
	}

	@Test
	void exactMatchComesFirst() {
		assertThat(linker().link(Optional.of(NORI))).isEqualTo(new CodefCardLink.Linked(NORI));
		// 사전으로는 09174 보유카드가 두 장이지만, 정확히 같은 이름의 보유카드가 한 장이면 그 카드다
		assertThat(linker().link(Optional.of(TOKTOK))).isEqualTo(new CodefCardLink.Linked(TOKTOK));
		// 사전에 없는 카드도 정확 일치면 그대로 연결한다
		assertThat(linker().link(Optional.of("KB Youth Club 체크카드(RF)")))
			.isEqualTo(new CodefCardLink.Linked("KB Youth Club 체크카드(RF)"));
	}

	@Test
	void dictionaryLinksToTheSingleHeldCardOfTheSameProduct() {
		// 실적 API 표기 → 01664 → 보유카드 "nori 체크카드(RF)" 한 장. 연결 결과는 보유카드의 이름이다
		assertThat(linker().link(Optional.of("KB국민 노리체크카드"))).isEqualTo(new CodefCardLink.Linked(NORI));
		// 사전 조회 규칙의 '카드명|발급사' 앞부분 재비교를 그대로 쓴다
		assertThat(linker().link(Optional.of(TITANIUM + "|KB국민카드"))).isEqualTo(new CodefCardLink.Linked(TITANIUM));
	}

	@Test
	void zeroOrSeveralHeldCardsOfTheProductOrNoDictionaryEntryDoNotLink() {
		CodefCardLinker linker = linker();

		assertThat(linker.link(Optional.of("첵첵 체크카드"))).isEqualTo(notLinked(Reason.NO_HELD_CARD_FOR_PRODUCT));
		// 정확 일치는 뒤 공백 때문에 실패하고, 사전(앞뒤 공백 제거)으로는 09174 보유카드가 두 장이다
		assertThat(linker.link(Optional.of(TOKTOK + " ")))
			.isEqualTo(notLinked(Reason.MULTIPLE_HELD_CARDS_FOR_PRODUCT));
		assertThat(linker.link(Optional.of("KB Youth Club 체크카드"))).isEqualTo(notLinked(Reason.ROW_NAME_NOT_IN_DICTIONARY));
		assertThat(linker.link(Optional.empty())).isEqualTo(notLinked(Reason.ROW_CARD_NAME_ABSENT));
	}

	@Test
	void noNormalizationBeyondTheDictionaryRule() {
		CodefCardLinker linker = linker();

		for (String name : List.of("kb국민 노리체크카드", "KB국민 노리 체크카드", "KB국민노리체크카드", "노리체크카드", "KB국민 노리체크",
				"펭수 노리 체크카드", "올라운드  티타늄카드")) {
			assertThat(linker.link(Optional.of(name))).as(name).isEqualTo(notLinked(Reason.ROW_NAME_NOT_IN_DICTIONARY));
		}
	}

	@Test
	void unusableHeldCardsLinkNothingEvenWithTheDictionary() {
		CodefTargetOutcome<CodefHeldCardCandidate> failed = CodefTargetOutcome.judge(
				new CodefCallResult.Failed<>(CodefCallResult.Stage.PRODUCT, CodefCallResult.Reason.TIMEOUT,
						OptionalInt.empty()),
				CHECKED_AT, CodefHeldCardCandidate::from);

		assertThat(CodefCardLinker.of(failed, CodefCardNameDictionary::candidateFor)
			.link(Optional.of("KB국민 노리체크카드"))).isEqualTo(notLinked(Reason.HELD_CARDS_UNAVAILABLE));
	}

	@Test
	void sameNameHeldTwiceIsAmbiguousForTheDictionaryToo() {
		// 같은 이름의 보유카드 두 장은 사전으로도 같은 상품 두 장이라 연결하지 않는다
		CodefTargetOutcome.Usable<CodefHeldCardCandidate> base = (CodefTargetOutcome.Usable<CodefHeldCardCandidate>) heldCards();
		CodefTargetOutcome<CodefHeldCardCandidate> twice = new CodefTargetOutcome.Usable<>(base.checkedAt(), base.result(),
				base.shape(), List.of(held(NORI), held(NORI)));

		CodefCardLinker linker = CodefCardLinker.of(twice, CodefCardNameDictionary::candidateFor);

		assertThat(linker.link(Optional.of(NORI))).isEqualTo(notLinked(Reason.MULTIPLE_MATCHING_HELD_CARDS));
		assertThat(linker.link(Optional.of("KB국민 노리체크카드")))
			.isEqualTo(notLinked(Reason.MULTIPLE_HELD_CARDS_FOR_PRODUCT));
	}

	@Test
	void approvalRowsLinkedByTheDictionaryAreJudgedOnTheHeldCard() {
		CodefLedgerCandidates candidates = CodefLedgerCandidateBuilder.build(KB, linker(),
				LedgerFixture.approvals("ledger-approvals-dictionary.json"), SyntheticHmacFingerprinter.KEY_ONE, Map.of());

		assertThat(candidates.rows()).extracting(CodefLedgerCandidateRow::card)
			.containsExactly(new CodefCardLink.Linked(NORI), new CodefCardLink.Linked(NORI),
					notLinked(Reason.ROW_NAME_NOT_IN_DICTIONARY), notLinked(Reason.NO_HELD_CARD_FOR_PRODUCT),
					notLinked(Reason.MULTIPLE_HELD_CARDS_FOR_PRODUCT), new CodefCardLink.Linked(TITANIUM),
					notLinked(Reason.ROW_NAME_NOT_IN_DICTIONARY), new CodefCardLink.Linked(TOKTOK),
					new CodefCardLink.Linked(NORI));
		for (int position : new int[] { 0, 1, 5, 7 }) {
			assertThat(candidates.row(position).decision()).as("row %d", position).isInstanceOf(Decision.Accepted.class);
		}
		// 사전으로 연결한 행의 중복 판단 키는 보유카드 이름을 쓴다
		assertThat(((Decision.Accepted) candidates.row(1).decision()).key().cardName()).isEqualTo(NORI);
		// 미연결 행은 기존처럼 판단 불가(공개 사유 불변)이고, 내부 분기는 카드 미연결이다
		for (int position : new int[] { 2, 3, 4, 6 }) {
			assertThat(candidates.row(position).decision()).as("row %d", position)
				.isEqualTo(new Decision.Incomplete(CodefIncompleteReason.UNDETERMINED_ROW));
			assertThat(candidates.row(position).incompleteDetail()).contains(CodefIncompleteDetail.CARD_NOT_LINKED);
		}
		// 연결된 뒤에는 나머지 판정이 그대로 적용된다
		assertThat(candidates.row(8).decision()).isEqualTo(new Decision.Incomplete(CodefIncompleteReason.UNDETERMINED_ROW));
		assertThat(candidates.row(8).incompleteDetail()).contains(CodefIncompleteDetail.KRW_CURRENCY_NOT_KRW);
		assertThat(candidates.incompleteMarks()).singleElement()
			.satisfies(mark -> assertThat(mark.cardName()).isEqualTo(NORI));
	}

	@Test
	void performancesLinkWithTheSameRule() {
		CodefTargetOutcome<CodefPerformanceReference> outcome = CodefTargetOutcome.judge(
				SyntheticFixture.performances("ledger-performances-dictionary.json"), CHECKED_AT,
				(CodefCardPerformance card) -> CodefPerformanceReference.from(card, CHECKED_AT));
		List<CodefPerformanceReference> references = ((CodefTargetOutcome.Usable<CodefPerformanceReference>) outcome)
			.items();

		List<CodefPerformanceLink> links = linker().linkPerformances(references);

		assertThat(links).extracting(CodefPerformanceLink::card)
			.containsExactly(new CodefCardLink.Linked(NORI), notLinked(Reason.ROW_NAME_NOT_IN_DICTIONARY),
					new CodefCardLink.Linked(TITANIUM), notLinked(Reason.MULTIPLE_HELD_CARDS_FOR_PRODUCT));
		assertThat(links).extracting(CodefPerformanceLink::reference).containsExactlyElementsOf(references);
	}

	@Test
	void approvalApiNamesLinkToTheHeldCardsByTheDictionary() {
		// 2026-11-27 재동기화의 승인 API 표기(#272). 보유카드 이름과 다르지만 사전으로 같은 상품 1장에 연결된다
		CodefCardLinker linker = linker();

		assertThat(linker.link(Optional.of("KB국민 올라운드 티타늄카드"))).isEqualTo(new CodefCardLink.Linked(TITANIUM));
		assertThat(linker.link(Optional.of("KB국민 nori 체크카드(RF)"))).isEqualTo(new CodefCardLink.Linked(NORI));
		// 보유카드에 없고 지원 상품도 아닌 카드는 미연결로 남는다
		assertThat(linker.link(Optional.of("KB국민 나라사랑체크카드(일반형 RF)")))
			.isEqualTo(notLinked(Reason.ROW_NAME_NOT_IN_DICTIONARY));
	}

	@Test
	void linkerWithoutTheDictionaryKeepsTheExactOnlyRule() {
		CodefCardLinker exactOnly = CodefCardLinker.of(heldCards());

		assertThat(exactOnly.link(Optional.of("KB국민 노리체크카드"))).isEqualTo(notLinked(Reason.NO_MATCHING_HELD_CARD));
		assertThat(exactOnly.link(Optional.of(NORI))).isEqualTo(new CodefCardLink.Linked(NORI));
	}

	private static CodefHeldCardCandidate held(String name) {
		CodefTargetOutcome<CodefHeldCardCandidate> outcome = heldCards();
		return ((CodefTargetOutcome.Usable<CodefHeldCardCandidate>) outcome).items()
			.stream()
			.filter(card -> card.cardName().filter(name::equals).isPresent())
			.findFirst()
			.orElseThrow();
	}

}
