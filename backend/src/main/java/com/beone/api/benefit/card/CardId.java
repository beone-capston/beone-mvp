package com.beone.api.benefit.card;

/**
 * 내부 카드 상품 식별자(예: {@code 09271}).
 * 앞자리 0을 유지하고 숫자가 아닌 문자열 순서로 비교한다(D-12).
 */
public record CardId(String value) implements Comparable<CardId> {

	public CardId {
		Identifiers.requireToken(value, "card id");
	}

	public static CardId of(String value) {
		return new CardId(value);
	}

	@Override
	public int compareTo(CardId other) {
		return value.compareTo(other.value);
	}

	@Override
	public String toString() {
		return value;
	}

}
