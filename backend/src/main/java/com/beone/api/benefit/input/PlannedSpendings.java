package com.beone.api.benefit.input;

import java.util.List;

/**
 * 한 번의 최적화에 쓰는 예정 소비 목록. 최대 {@value #MAX_COUNT}건이다(PRD FR-04).
 */
public record PlannedSpendings(List<PlannedSpending> items) {

	public static final int MAX_COUNT = 5;

	public PlannedSpendings {
		items = List.copyOf(items);
		if (items.size() > MAX_COUNT) {
			throw new IllegalArgumentException(
					"at most " + MAX_COUNT + " planned spendings are allowed, got " + items.size());
		}
	}

	public static PlannedSpendings none() {
		return new PlannedSpendings(List.of());
	}

}
