package com.beone.api.benefit.evaluation;

import java.util.Objects;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.card.ServiceId;

/**
 * {@code 09271:FUEL} 같은 fixture 키. 카드와 그 카드의 서비스를 가리킨다.
 */
record ServiceRef(CardId cardId, ServiceId serviceId) {

	ServiceRef {
		Objects.requireNonNull(cardId, "cardId");
		Objects.requireNonNull(serviceId, "serviceId");
	}

	static ServiceRef parse(String key) {
		String[] parts = CardScopedKey.split(key);
		return new ServiceRef(CardId.of(parts[0]), ServiceId.of(parts[1]));
	}

}
