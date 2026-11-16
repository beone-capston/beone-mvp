package com.beone.api.codef.sync;

import static com.beone.api.codef.sync.SyntheticFixture.CHECKED_AT;
import static com.beone.api.codef.sync.SyntheticFixture.KB;

import java.util.List;
import java.util.Map;

import com.beone.api.codef.CodefApproval;

/**
 * 원장 후보 테스트의 합성 입력. {@code ledger-*.json} fixture를 기존 전송 단계 해석기로 읽는다. 공식 시트 기준, 실계정 관측 없음.
 */
final class LedgerFixture {

	private LedgerFixture() {
	}

	static CodefTargetOutcome<CodefHeldCardCandidate> heldCards() {
		return CodefTargetOutcome.judge(SyntheticFixture.heldCards("ledger-held-cards.json"), CHECKED_AT,
				CodefHeldCardCandidate::from);
	}

	static CodefCardLinker linker() {
		return CodefCardLinker.of(heldCards());
	}

	static List<CodefApproval> approvals(String name) {
		CodefTargetOutcome<CodefApproval> outcome = CodefTargetOutcome.judge(SyntheticFixture.approvals(name),
				CHECKED_AT, row -> row);
		return ((CodefTargetOutcome.Usable<CodefApproval>) outcome).items();
	}

	static CodefLedgerCandidates build(String name) {
		return build(name, Map.of());
	}

	static CodefLedgerCandidates build(String name, Map<CodefApprovalKey, CodefLedgerRowKind> previous) {
		return CodefLedgerCandidateBuilder.build(KB, linker(), approvals(name), SyntheticHmacFingerprinter.KEY_ONE,
				previous);
	}

}
