package com.beone.api.card.application;

import com.beone.api.benefit.rule.BenefitService;
import com.beone.api.benefit.rule.RulePolicy;

/**
 * 서비스 하나를 BeONE이 계산할 수 있는 범위인지 나타낸다(D-18 K1). 응답 {@code supportStatus} 값과 같다.
 *
 * <p>범위 밖 서비스는 계산하지 않고 {@code 확인 필요}로 둔다. 이 값은 지원 범위만 나타내며, 계산식·실적 기간이
 * 미확정이라 계산이 막히는 경우는 표시하지 않는다(결정 27 A1-5, docs/domains/card.md 제약).
 */
public enum SupportStatus {

	/** 한도 없는 비선택형 서비스. */
	SUPPORTED,

	/** 한도가 있고 한도 기간이 월 1일~말일로 확정된 비선택형 서비스. 통합 한도·매입 순서 명시 카드를 포함한다. */
	SUPPORTED_SINGLE_MONTHLY_LIMIT,

	/** 한도가 있지만 한도 기간이 미확정인 서비스. */
	UNSUPPORTED_LIMIT_PERIOD,

	/** 사용자가 선택해야 적용되는 선택형 서비스. */
	UNSUPPORTED_SELECTION_RULE;

	/**
	 * 서비스의 지원 범위를 판정한다. 선택형 → 한도 없음 → 한도 기간 확정 여부 순으로 본다.
	 *
	 * <p>한도 기간은 규칙 정책 하나({@link RulePolicy#limitPeriod()})로 정해지므로 상위 한도까지 같은 값이다.
	 * 현재 정책이 표현하는 확정 기간은 {@code CALENDAR_MONTH}뿐이다.
	 * @param service 판정할 서비스
	 * @param policy 서비스가 속한 규칙의 정책
	 * @return 지원 범위
	 */
	public static SupportStatus of(BenefitService service, RulePolicy policy) {
		if (service.requiresSelection()) {
			return UNSUPPORTED_SELECTION_RULE;
		}
		if (service.limitBuckets().isEmpty()) {
			return SUPPORTED;
		}
		return policy.limitPeriod().isResolved() ? SUPPORTED_SINGLE_MONTHLY_LIMIT : UNSUPPORTED_LIMIT_PERIOD;
	}

}
