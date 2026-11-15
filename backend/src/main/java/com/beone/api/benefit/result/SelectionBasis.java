package com.beone.api.benefit.result;

/**
 * 선택 카드를 결정한 D-12 단계.
 */
public enum SelectionBasis {

	/** 1단계: 월간 총예상 혜택이 가장 큼. */
	MONTHLY_TOTAL(false),

	/** 2단계: 현재 주문 혜택이 가장 큼. */
	CURRENT_ORDER_BENEFIT(false),

	/** 3단계: 동률 카드 중 사용자가 지정한 대표 카드. 임의 규칙. */
	REPRESENTATIVE_CARD(true),

	/** 4단계: 카드 ID 문자열 오름차순. 임의 규칙. */
	CARD_ID_ASC(true);

	private final boolean arbitrary;

	SelectionBasis(boolean arbitrary) {
		this.arbitrary = arbitrary;
	}

	/**
	 * 선택된 카드가 더 낫다는 뜻이 아닌 단계면 참(D-12).
	 */
	public boolean isArbitrary() {
		return arbitrary;
	}

}
