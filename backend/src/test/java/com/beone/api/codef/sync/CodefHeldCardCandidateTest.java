package com.beone.api.codef.sync;

import static com.beone.api.codef.sync.SyntheticFixture.CHECKED_AT;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.beone.api.codef.CodefHeldCard.Dormancy;

/**
 * 보유카드 → 매핑 전 후보. 공식 시트 기준(보유카드!H10·H12·H14·H16·H17), 실계정 관측 없음. 기대값은 D-15·D-16과 시트 셀에서 구현 전에 정했다.
 */
class CodefHeldCardCandidateTest {

	private static List<CodefHeldCardCandidate> candidates() {
		CodefTargetOutcome<CodefHeldCardCandidate> outcome = CodefTargetOutcome
			.judge(SyntheticFixture.heldCards("held-cards-kb.json"), CHECKED_AT, CodefHeldCardCandidate::from);
		return ((CodefTargetOutcome.Usable<CodefHeldCardCandidate>) outcome).items();
	}

	@Test
	void everyCandidateIsCodefHeldAndUnmappedWithUnknownRegistrationDate() {
		// D-15: 카드명은 후보 근거일 뿐이며 매핑 상태는 UNMAPPED로 시작한다. 등록일은 발급일로 추정하지 않는다(KB 발급일자 -).
		for (CodefHeldCardCandidate candidate : candidates()) {
			assertThat(candidate.holdingSource()).isEqualTo("CODEF");
			assertThat(candidate.mappingStatus()).isEqualTo("UNMAPPED");
			assertThat(candidate.registeredOn()).isEmpty();
		}
	}

	@Test
	void dormancyIsKeptForYesNoAndBlank() {
		assertThat(candidates()).extracting(CodefHeldCardCandidate::dormant)
			.containsExactly(Dormancy.DORMANT, Dormancy.NOT_DORMANT, Dormancy.UNKNOWN);
	}

	@Test
	void cardNameIsKeptVerbatimWithoutSplittingTheIssuer() {
		assertThat(candidates()).extracting(CodefHeldCardCandidate::cardName)
			.containsExactly(Optional.of("SYN 휴면 카드"), Optional.of("SYN 일반 카드|SYN 발급사"), Optional.of("SYN 빈 휴면 카드"));
	}

	@Test
	void kbUnprovidedFieldsStayEmpty() {
		// 보유카드!H12·H14·H17: KB는 카드구분·발급일자·상태를 제공하지 않는다(-).
		for (CodefHeldCardCandidate candidate : candidates()) {
			assertThat(candidate.cardType()).isEmpty();
			assertThat(candidate.issueDate()).isEmpty();
			assertThat(candidate.state()).isEmpty();
		}
	}

	@Test
	void sensitiveFieldsDoNotAppear() {
		assertThat(candidates().toString()).doesNotContain("SYN-MASKED-0000").doesNotContain("SYN-USER");
	}

}
