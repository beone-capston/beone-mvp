package com.beone.api.codef.transport;

/**
 * 프로세스를 넘는 CODEF 호출 총량(LP-2, N14). 전송 계층이 요청을 보내기 직전에 API마다 한 번 묻는다.
 *
 * <p>관측 1회 상한({@link CodefObservation#CALL_LIMIT})과 별개다. 둘 다 허락해야 보낸다. 허락한 뒤 요청이 실패해도 되돌리지 않는다(시도 수를
 * 센다). 구현은 허락할 수 없거나 판단할 수 없으면 거절한다.
 */
@FunctionalInterface
public interface CodefCallBudget {

	/** 총량을 세지 않는다. 실 게이트웨이가 없는 프로필과 전송 계층 단위 시험의 기본값이다. */
	CodefCallBudget UNLIMITED = api -> true;

	/** 토큰 발급 요청의 이름. 상품·계정 API는 {@link CodefApi}·{@link CodefAccountApi}의 이름을 쓴다. */
	String TOKEN = "TOKEN";

	/**
	 * 호출 하나를 쓴다.
	 * @param api API 이름
	 * @return 보내도 되면 참. 거절이면 요청을 보내지 않는다
	 */
	boolean tryAcquire(String api);

}
