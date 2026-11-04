package com.beone.api.benefit.evaluation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.beone.api.benefit.input.RecommendationInput;
import com.beone.api.benefit.rule.CardRuleVersion;

/**
 * 모델로 바꾼 fixture 사례(C01~C20) 또는 변형(M01~M10) 하나.
 *
 * @param baseCaseId 변형의 기준 사례. 사례 자체는 비어 있다
 * @param ruleSnapshots fixture가 적은 평가 전용 규칙 스냅샷(C02). 다른 사례는 주문일에 유효한 규칙을 전제하지만 fixture에 규칙 내용은 없다
 * @param description 사례 주제 또는 변형 차원
 * @param sourceKeys 사례가 인용한 공식 근거 키
 */
record EvaluationScenario(String id, Optional<String> baseCaseId, String description, List<String> sourceKeys,
		RecommendationInput input, List<CardRuleVersion> ruleSnapshots, ExpectedOutcome expected) {

	EvaluationScenario {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(baseCaseId, "baseCaseId");
		Objects.requireNonNull(description, "description");
		sourceKeys = List.copyOf(sourceKeys);
		Objects.requireNonNull(input, "input");
		ruleSnapshots = List.copyOf(ruleSnapshots);
		Objects.requireNonNull(expected, "expected");
	}

}
