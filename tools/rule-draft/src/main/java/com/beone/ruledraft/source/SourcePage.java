package com.beone.ruledraft.source;

/**
 * 원문 한 쪽. 쪽 번호는 1부터 센다.
 */
public record SourcePage(int number, String text) {

	public SourcePage {
		if (number < 1) {
			throw new IllegalArgumentException("쪽 번호는 1 이상이어야 한다");
		}
		text = text == null ? "" : text;
	}

}
