package com.beone.api.codef.sync;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * 재조회 때 "같은 거래"를 알아보는 중복 판단 키(결정 R2). 취소여부는 넣지 않아, 같은 행이 정상에서 Type1 전액 취소로 바뀌어도 키가 같다.
 *
 * <p>
 * 원문 승인번호 대신 지문만 담는다. 카드는 R1로 연결된 보유카드의 카드명이다(같은 동기화에서 이름이 유일한 카드만 연결되므로). 키 구성 요소가 하나라도 없는 행은
 * 키를 만들지 않고 불완전으로 둔다.
 *
 * @param cardName R1로 연결된 보유카드의 카드명
 * @param approval 승인번호 지문
 * @param usedDate 사용일자(R4 해석값)
 * @param usedTime 사용일시(R4 해석값)
 * @param signedAmount 이용금액. 부호를 그대로 둔다(Type2 취소 행은 음수)
 */
public record CodefApprovalKey(String cardName, CodefApprovalFingerprint approval, LocalDate usedDate,
		LocalTime usedTime, long signedAmount) {

	/**
	 * null을 거부한다.
	 *
	 * @throws NullPointerException 값이 null인 경우
	 */
	public CodefApprovalKey {
		Objects.requireNonNull(cardName, "cardName");
		Objects.requireNonNull(approval, "approval");
		Objects.requireNonNull(usedDate, "usedDate");
		Objects.requireNonNull(usedTime, "usedTime");
	}

}
