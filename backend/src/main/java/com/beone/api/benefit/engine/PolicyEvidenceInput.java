package com.beone.api.benefit.engine;

import java.util.Set;

/**
 * 카드 한 장의 실적 정책 조건(취소 귀속·할인 매출 실적 제외)에 대한 근거(2026-11-16 사용자 결정).
 *
 * <p>규칙에 미확정 실적 정책 조건이 있으면, 그 상황이 실적 기간에 없다고 확인된 경우({@code CONFIRMED_ABSENT})에만 조건을 무시한다.
 * 업무 API가 원장·시드로 확인하지 못했으면 {@code UNDETERMINED}를 넘기고, 엔진은 실적에 의존하는 서비스를 {@code 확인 필요}로 둔다.
 *
 * @param cancellation 실적 기간에 귀속될 수 있는 취소가 있는지. {@code PRESENT}·{@code CONFIRMED_ABSENT}·{@code UNDETERMINED}
 * @param benefitedSales 실적 기간에 할인받은 매출이 있는지. 값은 위와 같다
 */
public record PolicyEvidenceInput(String cancellation, String benefitedSales) {

	private static final Set<String> VALUES = Set.of("PRESENT", "CONFIRMED_ABSENT", "UNDETERMINED");

	public PolicyEvidenceInput {
		EngineChecks.requireOneOf(VALUES, cancellation, "cancellation");
		EngineChecks.requireOneOf(VALUES, benefitedSales, "benefitedSales");
	}

	/**
	 * 확인하지 못한 근거. 실적 정책 조건을 무시하지 않는다.
	 * @return 근거를 확인하지 못한 입력
	 */
	public static PolicyEvidenceInput undetermined() {
		return new PolicyEvidenceInput("UNDETERMINED", "UNDETERMINED");
	}

}
