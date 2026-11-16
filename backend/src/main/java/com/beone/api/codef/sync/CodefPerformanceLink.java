package com.beone.api.codef.sync;

import java.util.Objects;

/**
 * 카드사 조회 실적(참고) 하나와 그 카드 연결 결과(결정 R1). 연결해도 참고값은 계산에 쓰지 않는다(D-16).
 *
 * @param reference 실적 참고값
 * @param card 보유카드 연결 결과. 연결하지 못하면 카드 미연결
 */
public record CodefPerformanceLink(CodefPerformanceReference reference, CodefCardLink card) {

	/**
	 * null을 거부한다.
	 *
	 * @throws NullPointerException 값이 null인 경우
	 */
	public CodefPerformanceLink {
		Objects.requireNonNull(reference, "reference");
		Objects.requireNonNull(card, "card");
	}

}
