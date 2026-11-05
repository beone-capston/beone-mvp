package com.beone.api.benefit.engine;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import com.beone.api.benefit.input.SelectionMode;

/**
 * 엔진 입구의 입력.
 *
 * <p>같은 규칙 카드를 두 장 넣을 수 없다(D-23). {@code UNMAPPED}·{@code NOT_SUPPORTED} 카드는 규칙 카드가 없어 이 검사를
 * 받지 않는다. 예정 소비와 원장은 입력에 없다(D-18 K4, D-22).
 *
 * @param decisionDate 판정일. 주문일과 같아야 한다(D-23)
 * @param order 주문
 * @param selectionMode {@code AUTO_RECOMMEND}·{@code FIXED_REPRESENTATIVE}
 * @param representativeUserCardId 대표 카드. 보유 카드 중 하나이며 {@code FIXED_REPRESENTATIVE}면 필수
 * @param cards 보유 카드. 비어 있을 수 있다
 */
public record EngineRequest(LocalDate decisionDate, OrderInput order, String selectionMode,
		String representativeUserCardId, List<HeldCardInput> cards) {

	public EngineRequest {
		Objects.requireNonNull(decisionDate, "decisionDate");
		Objects.requireNonNull(order, "order");
		if (!decisionDate.equals(order.date())) {
			throw new IllegalArgumentException("decisionDate must be the order date");
		}
		EngineChecks.requireEnumName(SelectionMode.class, selectionMode, "selectionMode");
		cards = EngineChecks.copy(cards, "cards");
		List<String> userCardIds = cards.stream().map(HeldCardInput::userCardId).toList();
		EngineChecks.requireDistinct(userCardIds, "userCardId");
		EngineChecks.requireDistinct(cards.stream().filter(HeldCardInput::hasRuleCard).map(HeldCardInput::cardProductId).toList(),
				"cardProductId");
		if (SelectionMode.FIXED_REPRESENTATIVE.name().equals(selectionMode) && representativeUserCardId == null) {
			throw new IllegalArgumentException("fixed representative mode needs a representative card");
		}
		if (representativeUserCardId != null && !userCardIds.contains(representativeUserCardId)) {
			throw new IllegalArgumentException("representative card is not held");
		}
	}

}
