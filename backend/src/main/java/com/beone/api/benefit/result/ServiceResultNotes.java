package com.beone.api.benefit.result;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.beone.api.benefit.card.ServiceId;
import com.beone.api.benefit.rule.BenefitService;
import com.beone.api.benefit.rule.TermBasis;

/**
 * 서비스 결과와 함께 보여 줄 표시 값(D-03, D-18 K1, D-20). 금액을 바꾸지 않는다.
 *
 * @param usageBasis 한도가 있는 서비스의 사용액 기준. 한도가 없으면 비어 있다
 * @param mayDifferFromActual 실제 카드사 차감과 다를 수 있으면 참. 한도가 있는 모든 서비스(0원 결과 포함)에서 참이다
 * @param usageNote 사용액 기준 문장. 한도가 없으면 비어 있다
 * @param limitReason 한도 입력 때문에 판정하지 못한 사유
 * @param fractionalWonTruncated 후보 금액을 계산하며 원 미만을 버렸으면 참
 * @param fractionalWonBasis 원 미만을 버렸을 때 그 처리의 근거 표시. 버리지 않았으면 비어 있다
 * @param unevaluatedExclusions 주문 입력에 없어 판정하지 못한 제외 조건
 */
public record ServiceResultNotes(ServiceId serviceId, Optional<UsageBasis> usageBasis, boolean mayDifferFromActual,
		Optional<String> usageNote, Optional<LimitReason> limitReason, boolean fractionalWonTruncated,
		Optional<TermBasis> fractionalWonBasis, List<String> unevaluatedExclusions) {

	/** D-18 K1이 정한 표시 문장. */
	public static final String USAGE_NOTE = "입력한 사용액 기준, 미매입 전표에 따라 달라질 수 있음";

	public ServiceResultNotes {
		Objects.requireNonNull(serviceId, "serviceId");
		Objects.requireNonNull(usageBasis, "usageBasis");
		Objects.requireNonNull(usageNote, "usageNote");
		Objects.requireNonNull(limitReason, "limitReason");
		Objects.requireNonNull(fractionalWonBasis, "fractionalWonBasis");
		unevaluatedExclusions = List.copyOf(unevaluatedExclusions);
		if (usageBasis.isPresent() != mayDifferFromActual || usageBasis.isPresent() != usageNote.isPresent()) {
			throw new IllegalArgumentException("service " + serviceId + " must show the usage basis, note and difference together");
		}
		if (limitReason.isPresent() && usageBasis.isEmpty()) {
			throw new IllegalArgumentException("service " + serviceId + " has a limit reason but no limit");
		}
	}

	/**
	 * 규칙의 서비스와 판정 결과로 표시 값을 만든다.
	 *
	 * @param service 규칙의 서비스
	 * @param limitReason 한도 입력 때문에 판정하지 못한 사유
	 * @param truncated 후보 금액을 계산하며 원 미만을 버렸으면 참
	 * @return 서비스의 표시 값
	 */
	public static ServiceResultNotes of(BenefitService service, Optional<LimitReason> limitReason, boolean truncated) {
		boolean limited = !service.limitBuckets().isEmpty();
		return new ServiceResultNotes(service.id(), limited ? Optional.of(UsageBasis.USER_ENTERED) : Optional.empty(),
				limited, limited ? Optional.of(USAGE_NOTE) : Optional.empty(), limitReason, truncated,
				truncated ? service.details().fractionalWonBasis() : Optional.empty(),
				service.details().unevaluatedExclusions());
	}

}
