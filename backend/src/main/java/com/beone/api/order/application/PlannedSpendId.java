package com.beone.api.order.application;

/**
 * 예정 소비 ID의 외부 표현. {@code ps-} 뒤에 저장소 번호를 선행 0 없이 붙인다(결정 27, 예: {@code ps-1}).
 */
public final class PlannedSpendId {

	private static final String PREFIX = "ps-";

	private PlannedSpendId() {
	}

	/**
	 * 저장소 번호를 외부 표현으로 바꾼다.
	 * @param id 저장소 번호
	 * @return 외부 예정 소비 ID
	 */
	public static String format(long id) {
		if (id < 1) {
			throw new IllegalArgumentException("planned spend id must be positive");
		}
		return PREFIX + id;
	}

}
