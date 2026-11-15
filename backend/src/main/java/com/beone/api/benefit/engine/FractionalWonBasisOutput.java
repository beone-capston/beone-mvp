package com.beone.api.benefit.engine;

/**
 * 원 미만을 버린 근거(D-03).
 *
 * @param kind 근거 종류
 * @param decision 결정 표시
 */
public record FractionalWonBasisOutput(String kind, String decision) {

	public FractionalWonBasisOutput {
		EngineChecks.requireText(kind, "kind");
		EngineChecks.requireText(decision, "decision");
	}

}
