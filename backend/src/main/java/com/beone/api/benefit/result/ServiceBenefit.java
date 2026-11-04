package com.beone.api.benefit.result;

import java.util.Objects;

import com.beone.api.benefit.card.ServiceId;
import com.beone.api.benefit.money.BenefitAmount;

/**
 * 현재 주문에서 카드의 서비스 하나가 주는 혜택.
 */
public record ServiceBenefit(ServiceId serviceId, ApplicationStatus status, BenefitAmount amount) {

	public ServiceBenefit {
		Objects.requireNonNull(serviceId, "serviceId");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(amount, "amount");
		if (!status.isConsistentWith(amount)) {
			throw new IllegalArgumentException("service " + serviceId + " status " + status + " does not match amount " + amount);
		}
	}

}
