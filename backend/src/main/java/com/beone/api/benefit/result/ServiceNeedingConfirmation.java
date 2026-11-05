package com.beone.api.benefit.result;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.beone.api.benefit.card.ServiceId;

/**
 * 후보 금액을 확정하지 못한 서비스. 0원 후보가 아니다(D-05).
 *
 * @param stage 확인 필요로 둔 단계
 * @param reasons 확인 필요 사유. 앞 단계가 준 문구를 바꾸지 않고 담는다
 * @param limitReason 한도 입력 때문에 판정하지 못한 사유(D-18 K1)
 */
public record ServiceNeedingConfirmation(ServiceId serviceId, ConfirmationStage stage, List<String> reasons,
		Optional<LimitReason> limitReason) {

	public ServiceNeedingConfirmation {
		Objects.requireNonNull(serviceId, "serviceId");
		Objects.requireNonNull(stage, "stage");
		Objects.requireNonNull(limitReason, "limitReason");
		reasons = List.copyOf(reasons);
		if (reasons.isEmpty()) {
			throw new IllegalArgumentException("service " + serviceId + " needs at least one confirmation reason");
		}
		for (String reason : reasons) {
			if (reason.isBlank()) {
				throw new IllegalArgumentException("service " + serviceId + " has a blank confirmation reason");
			}
		}
	}

	/**
	 * 한도 사유가 없는 확인 필요 서비스.
	 */
	public ServiceNeedingConfirmation(ServiceId serviceId, ConfirmationStage stage, List<String> reasons) {
		this(serviceId, stage, reasons, Optional.empty());
	}

}
