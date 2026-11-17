package com.beone.api.codef.application;

/**
 * CODEF 계정 연결 경계. 이 버전에는 local·demo용 {@link FakeCodefGateway}만 있고 실 HTTP 구현은 없다(결정 30).
 *
 * <p>구현은 비밀번호를 저장·로그·예외 문구에 남기지 않는다. 실 구현은 받은 즉시 CODEF 공개키로 암호화해 보내야 한다(D-14 원칙 9).
 * 자동 재시도는 하지 않는다.
 */
public interface CodefGateway {

	/**
	 * 카드사 홈페이지 아이디·비밀번호로 CODEF 계정을 등록한다.
	 * @param organization 기관 코드
	 * @param loginId 카드사 홈페이지 아이디
	 * @param password 비밀번호. 호출자가 호출 뒤 지운다. 구현은 복사해 보관하지 않는다
	 * @return 등록 결과
	 */
	CodefRegistration register(String organization, String loginId, char[] password);

	/**
	 * 더는 쓰지 않는 connectedId를 CODEF 쪽에서 정리한다. 실패해도 BeONE 상태는 바꾸지 않는다.
	 * @param connectedId 정리할 connectedId
	 */
	void release(String connectedId);

}
