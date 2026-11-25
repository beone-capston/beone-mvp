package com.beone.api.codef.application;

import java.time.LocalDate;

import com.beone.api.codef.CodefApproval;
import com.beone.api.codef.CodefCardPerformance;
import com.beone.api.codef.CodefHeldCard;
import com.beone.api.codef.transport.CodefCallResult;

/**
 * CODEF 조회 경계(보유카드·승인내역·실적조회). local·demo용 {@link FakeCodefGateway}와 비공개 검증 프로필용
 * {@link LiveCodefGateway}(#193, 실호출은 별도 승인)가 있다(결정 30·38-8).
 *
 * <p>결과는 전송 계층과 같은 {@link CodefCallResult}로 돌려주며 판정은 {@code codef.sync}가 한다. 자동 재시도는 하지 않는다. connectedId를
 * 로그·예외 문구에 남기지 않는다.
 */
public interface CodefSyncGateway {

	/**
	 * 보유카드를 조회한다.
	 * @param organization 기관 코드
	 * @param connectedId 연결 ID
	 * @return 호출 결과
	 * @throws CodefConnectionInvalidException 연결이 무효라 재연결이 필요한 경우
	 */
	CodefCallResult<CodefHeldCard> heldCards(String organization, String connectedId);

	/**
	 * 승인내역을 조회한다. 기간은 호출자가 검사한 값이며 짝을 맞추려고 넓히지 않는다.
	 * @param organization 기관 코드
	 * @param connectedId 연결 ID
	 * @param from 조회 시작일
	 * @param to 조회 종료일
	 * @return 호출 결과
	 * @throws CodefConnectionInvalidException 연결이 무효라 재연결이 필요한 경우
	 */
	CodefCallResult<CodefApproval> approvals(String organization, String connectedId, LocalDate from, LocalDate to);

	/**
	 * 실적을 조회한다.
	 * @param organization 기관 코드
	 * @param connectedId 연결 ID
	 * @return 호출 결과
	 * @throws CodefConnectionInvalidException 연결이 무효라 재연결이 필요한 경우
	 */
	CodefCallResult<CodefCardPerformance> performances(String organization, String connectedId);

}
