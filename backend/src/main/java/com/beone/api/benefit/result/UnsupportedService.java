package com.beone.api.benefit.result;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.beone.api.benefit.card.ServiceId;

/**
 * 서비스 후보 취합(#33)이 판정하지 않는 서비스. 금액이나 확정·미충족 의미가 없으며 0원 후보가 아니다.
 *
 * @param reasons 판정하지 않는 이유. 비어 있을 수 없다
 * @param selectionReasons 규칙 선택 단계에서 이미 확인 필요로 분리된 경우 그 사유 원문. 해당하지 않으면 비어 있다
 */
public record UnsupportedService(ServiceId serviceId, Set<UnsupportedReason> reasons, List<String> selectionReasons) {

	public UnsupportedService {
		Objects.requireNonNull(serviceId, "serviceId");
		reasons = Set.copyOf(reasons);
		if (reasons.isEmpty()) {
			throw new IllegalArgumentException("service " + serviceId + " needs an unsupported reason");
		}
		selectionReasons = List.copyOf(selectionReasons);
	}

}
