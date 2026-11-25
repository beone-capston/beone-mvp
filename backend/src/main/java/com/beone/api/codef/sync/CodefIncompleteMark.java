package com.beone.api.codef.sync;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * R1로 보유카드에 붙은 불완전 행의 표시 범위(D-17: 해당 카드·해당 기간에만). 다른 카드나 다른 날짜로 넓히지 않는다. 카드의 실적 기간으로 묶는 일은 호출자가
 * 한다.
 *
 * @param cardName 연결된 보유카드의 카드명
 * @param usedDate 그 행의 사용일자. 해석하지 못했으면 빈 값(기간 미확인)
 * @param reason 불완전 사유
 * @param position 승인내역 응답 안의 위치
 */
public record CodefIncompleteMark(String cardName, Optional<LocalDate> usedDate, CodefIncompleteReason reason,
		int position) {

	/**
	 * null을 거부한다.
	 *
	 * @throws NullPointerException 값이 null인 경우
	 */
	public CodefIncompleteMark {
		Objects.requireNonNull(cardName, "cardName");
		Objects.requireNonNull(usedDate, "usedDate");
		Objects.requireNonNull(reason, "reason");
	}

}
