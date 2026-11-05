package com.beone.api.benefit.engine;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.beone.api.benefit.result.Adoption;
import com.beone.api.benefit.result.ApplicationStatus;

/**
 * 비교 카드의 서비스 결과 하나.
 *
 * @param serviceId 서비스
 * @param applicationStatus 적용 상태
 * @param candidateWon 단독 후보 금액. 미확정이면 null
 * @param contribution 카드 금액에 실제로 넣은 기여액(D-18 K3)
 * @param adoption 채택 여부
 * @param limits 체인의 한도 버킷. 한도가 없으면 비어 있다
 * @param limitReason {@code USAGE_EXCEEDS_LIMIT} 또는 null
 * @param usageBasis 한도가 있으면 {@code USER_ENTERED}
 * @param mayDifferFromActual 한도가 있으면 참
 * @param usageNote 한도가 있을 때의 표시 문구
 * @param fractionalWonTruncated 원 미만을 버렸으면 참(D-03)
 * @param fractionalWonBasis 버렸을 때의 근거
 * @param unevaluatedExclusions 판정하지 못한 제외 조건(D-20)
 */
public record ServiceResult(String serviceId, String applicationStatus, Long candidateWon,
		BenefitAmountOutput contribution, String adoption, List<ServiceLimitLine> limits, String limitReason,
		String usageBasis, boolean mayDifferFromActual, String usageNote, boolean fractionalWonTruncated,
		FractionalWonBasisOutput fractionalWonBasis, List<String> unevaluatedExclusions) {

	public ServiceResult {
		EngineChecks.requireToken(serviceId, "serviceId");
		EngineChecks.requireEnumName(ApplicationStatus.class, applicationStatus, "applicationStatus");
		EngineChecks.requireOptionalNonNegative(candidateWon, "candidateWon");
		Objects.requireNonNull(contribution, "contribution");
		EngineChecks.requireEnumName(Adoption.class, adoption, "adoption");
		Adoption parsed = Adoption.valueOf(adoption);
		if (parsed == Adoption.NOT_ADOPTED && !Long.valueOf(0).equals(contribution.amountWon())) {
			throw new IllegalArgumentException("a service not adopted contributes a confirmed zero");
		}
		if ((parsed == Adoption.UNDETERMINED) == contribution.isConfirmed()) {
			throw new IllegalArgumentException("contribution is unconfirmed exactly when adoption is UNDETERMINED");
		}
		limits = EngineChecks.copy(limits, "limits");
		if (limitReason != null) {
			EngineChecks.requireOneOf(Set.of("USAGE_EXCEEDS_LIMIT"), limitReason, "limitReason");
		}
		if (limits.isEmpty()) {
			if (usageBasis != null || mayDifferFromActual || usageNote != null) {
				throw new IllegalArgumentException("usage display fields need a limit");
			}
		}
		else {
			EngineChecks.requireOneOf(Set.of("USER_ENTERED"), usageBasis, "usageBasis");
			if (!mayDifferFromActual) {
				throw new IllegalArgumentException("a limited service may differ from the actual");
			}
			EngineChecks.requireText(usageNote, "usageNote");
		}
		if (fractionalWonTruncated != (fractionalWonBasis != null)) {
			throw new IllegalArgumentException("fractionalWonBasis is present exactly when truncated");
		}
		unevaluatedExclusions = EngineChecks.copy(unevaluatedExclusions, "unevaluatedExclusions");
	}

}
