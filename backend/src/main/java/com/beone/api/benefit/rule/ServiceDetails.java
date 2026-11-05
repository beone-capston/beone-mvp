package com.beone.api.benefit.rule;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.beone.api.benefit.card.MerchantClass;

/**
 * 서비스의 계산 조건 밖에서 규칙 데이터가 전하는 값. 합산 기준처럼 아직 판정 코드가 쓰지 않는 값도 있다.
 *
 * @param displayName 화면 이름. 내부 서비스를 나눠도 문서의 이름을 유지할 때 쓴다(D-20)
 * @param stackingBasis 중복 합산 계산 기준. 없으면 합산하지 않는다(D-13 #35 8번)
 * @param fractionalWonBasis 원 미만 처리의 근거 표시(D-03)
 * @param inclusionUnconfirmed 포함 관계가 확인되지 않은 코드 쌍(D-18 U2 ① 예외)
 * @param unevaluatedExclusions 주문 입력에 없어 판정하지 못하는 제외 조건의 문서 표현(D-20). 비어 있으면 그런 조건이 없다
 */
public record ServiceDetails(Optional<String> displayName, Optional<StackingBasis> stackingBasis,
		Optional<TermBasis> fractionalWonBasis, Set<InclusionUnconfirmed> inclusionUnconfirmed,
		List<String> unevaluatedExclusions) {

	public ServiceDetails {
		Objects.requireNonNull(displayName, "displayName");
		if (displayName.isPresent() && displayName.get().isBlank()) {
			throw new IllegalArgumentException("display name must not be blank when present");
		}
		Objects.requireNonNull(stackingBasis, "stackingBasis");
		Objects.requireNonNull(fractionalWonBasis, "fractionalWonBasis");
		inclusionUnconfirmed = Set.copyOf(inclusionUnconfirmed);
		unevaluatedExclusions = List.copyOf(unevaluatedExclusions);
		for (String exclusion : unevaluatedExclusions) {
			if (exclusion.isBlank()) {
				throw new IllegalArgumentException("unevaluated exclusion must not be blank");
			}
		}
	}

	/** 추가 값이 없는 서비스. 규칙 데이터를 읽는 로더는 이 값을 쓰지 않고 데이터의 값을 그대로 담는다. */
	public static ServiceDetails none() {
		return new ServiceDetails(Optional.empty(), Optional.empty(), Optional.empty(), Set.of(), List.of());
	}

	/**
	 * 주문 업종이 포함 관계 미확인 쌍의 코드이면 참. 그 주문에서 이 서비스의 대상 여부를 단정하지 않는다.
	 */
	public boolean inclusionUnconfirmedFor(MerchantClass merchantClass) {
		return inclusionUnconfirmed.stream().anyMatch(pair -> pair.merchantClass().equals(merchantClass));
	}

	/** 주문 입력으로 판정하지 못하는 제외 조건이 있으면 참. 그런 서비스의 확정 금액은 실제보다 클 수 있다(D-20). */
	public boolean hasUnevaluatedExclusions() {
		return !unevaluatedExclusions.isEmpty();
	}

}
