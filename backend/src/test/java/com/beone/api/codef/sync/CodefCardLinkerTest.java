package com.beone.api.codef.sync;

import static com.beone.api.codef.sync.SyntheticFixture.CHECKED_AT;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

import com.beone.api.codef.CodefCardPerformance;
import com.beone.api.codef.sync.CodefCardLink.Reason;
import com.beone.api.codef.sync.CodefLedgerCandidateRow.Decision;
import com.beone.api.codef.transport.CodefCallResult;

/**
 * 카드 연결(결정 R1). 같은 동기화의 보유카드 카드명과 문자열로 정확히 같은 카드가 정확히 한 장일 때만 연결하고, 정규화하지 않는다. 기대값은 결정 C R1 문장에서
 * 구현 전에 정했다. 공식 시트 기준, 실계정 관측 없음.
 */
class CodefCardLinkerTest {

	private static CodefCardLink notLinked(Reason reason) {
		return new CodefCardLink.NotLinked(reason);
	}

	@Test
	void exactlyOneHeldCardWithTheSameNameLinks() {
		assertThat(LedgerFixture.linker().link(Optional.of("SYN CARD A")))
			.isEqualTo(new CodefCardLink.Linked("SYN CARD A"));
		// "카드명|발급사"도 나누지 않고 문자열 그대로 비교한다.
		assertThat(LedgerFixture.linker().link(Optional.of("SYN CARD C|SYN ISSUER")))
			.isEqualTo(new CodefCardLink.Linked("SYN CARD C|SYN ISSUER"));
	}

	@Test
	void zeroOrTwoHeldCardsDoNotLink() {
		CodefCardLinker linker = LedgerFixture.linker();

		assertThat(linker.link(Optional.of("SYN CARD Z"))).isEqualTo(notLinked(Reason.NO_MATCHING_HELD_CARD));
		assertThat(linker.link(Optional.of("SYN CARD B"))).isEqualTo(notLinked(Reason.MULTIPLE_MATCHING_HELD_CARDS));
		assertThat(linker.link(Optional.empty())).isEqualTo(notLinked(Reason.ROW_CARD_NAME_ABSENT));
	}

	@Test
	void noNormalization() {
		CodefCardLinker linker = LedgerFixture.linker();

		for (String name : List.of("syn card a", "SYN CARD A ", "SYN  CARD A", " SYN CARD A", "SYN CARD C",
				"SYN CARD A|SYN ISSUER")) {
			assertThat(linker.link(Optional.of(name))).as(name).isEqualTo(notLinked(Reason.NO_MATCHING_HELD_CARD));
		}
	}

	@Test
	void unusableHeldCardsLinkNothing() {
		CodefTargetOutcome<CodefHeldCardCandidate> failed = CodefTargetOutcome.judge(
				new CodefCallResult.Failed<>(CodefCallResult.Stage.PRODUCT, CodefCallResult.Reason.TIMEOUT,
						OptionalInt.empty()),
				CHECKED_AT, CodefHeldCardCandidate::from);

		assertThat(CodefCardLinker.of(failed).link(Optional.of("SYN CARD A")))
			.isEqualTo(notLinked(Reason.HELD_CARDS_UNAVAILABLE));
	}

	@Test
	void unlinkedApprovalRowsAreUndetermined() {
		CodefLedgerCandidates candidates = LedgerFixture.build("ledger-approvals-link.json");

		assertThat(candidates.row(0).decision()).isInstanceOf(Decision.Accepted.class);
		assertThat(candidates.row(9).decision()).isInstanceOf(Decision.Accepted.class);
		for (int position = 1; position <= 8; position++) {
			assertThat(candidates.row(position).card()).as("row %d", position)
				.isInstanceOf(CodefCardLink.NotLinked.class);
			assertThat(candidates.row(position).decision()).as("row %d", position)
				.isEqualTo(new Decision.Incomplete(CodefIncompleteReason.UNDETERMINED_ROW));
		}
		assertThat(candidates.row(1).card()).isEqualTo(notLinked(Reason.MULTIPLE_MATCHING_HELD_CARDS));
		assertThat(candidates.row(8).card()).isEqualTo(notLinked(Reason.ROW_CARD_NAME_ABSENT));
		// 카드 미연결 행은 어느 카드에도 불완전을 표시하지 않는다.
		assertThat(candidates.incompleteMarks()).isEmpty();
	}

	@Test
	void performancesLinkWithTheSameRule() {
		CodefTargetOutcome<CodefPerformanceReference> outcome = CodefTargetOutcome.judge(
				SyntheticFixture.performances("ledger-performances.json"), CHECKED_AT,
				(CodefCardPerformance card) -> CodefPerformanceReference.from(card, CHECKED_AT));
		List<CodefPerformanceReference> references = ((CodefTargetOutcome.Usable<CodefPerformanceReference>) outcome)
			.items();

		List<CodefPerformanceLink> links = LedgerFixture.linker().linkPerformances(references);

		assertThat(links).extracting(CodefPerformanceLink::card)
			.containsExactly(new CodefCardLink.Linked("SYN CARD A"), notLinked(Reason.MULTIPLE_MATCHING_HELD_CARDS),
					notLinked(Reason.NO_MATCHING_HELD_CARD));
		assertThat(links).extracting(CodefPerformanceLink::reference).containsExactlyElementsOf(references);
		assertThat(links).allSatisfy(link -> assertThat(link.reference().usedInCalculation()).isFalse());
	}

}
