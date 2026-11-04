package com.beone.api.benefit.evaluation;

import java.util.List;
import java.util.Map;

/**
 * 모델로 바꾼 평가 fixture 전체. 설명용 필드는 텍스트로 보관해 모든 키가 매핑됐음을 확인한다.
 */
record EvaluationFixture(String schemaVersion, String status, String reviewStatus, String purpose,
		String policyBasisCommit, Map<String, String> defaultNotes, Map<String, String> sources,
		Map<String, String> reviewRecord, List<EvaluationScenario> cases, List<EvaluationScenario> mutations) {

	EvaluationFixture {
		defaultNotes = Map.copyOf(defaultNotes);
		sources = Map.copyOf(sources);
		reviewRecord = Map.copyOf(reviewRecord);
		cases = List.copyOf(cases);
		mutations = List.copyOf(mutations);
	}

}
