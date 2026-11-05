package com.beone.api.benefit.engine;

import java.util.List;

import com.beone.api.benefit.rule.VerificationStatus;

/**
 * 계산에 쓴 규칙 버전과 근거(D-14 원칙 5).
 *
 * @param ruleVersion 규칙 버전
 * @param verificationStatus 검증 상태
 * @param officialSources 공식 근거
 */
public record AppliedRuleOutput(String ruleVersion, String verificationStatus, List<OfficialSourceOutput> officialSources) {

	public AppliedRuleOutput {
		EngineChecks.requireText(ruleVersion, "ruleVersion");
		EngineChecks.requireEnumName(VerificationStatus.class, verificationStatus, "verificationStatus");
		officialSources = EngineChecks.copy(officialSources, "officialSources");
	}

}
