package com.beone.api.codef;

import java.util.Objects;

/**
 * CODEF 응답 하나의 해석 결과. 결과 코드 분류와 {@code data} 해석은 서로 독립이며, 어느 한쪽으로 다른 쪽을 고치지 않는다.
 *
 * <p>
 * {@code data}의 항목을 계산에 쓸지는 호출자가 결과 코드와 항목 값을 따로 보고 판단한다. 성공 분류여도 {@code data}의 누락·형식 오류·미확인
 * 값은 그대로이고, 추가 인증 응답의 {@code data}도 형식상 객체 1건으로 읽힌다.
 *
 * @param <T> 항목 타입
 * @param result 공통 결과
 * @param data {@code data}의 항목
 */
public record CodefResponse<T>(CodefResult result, CodefList<T> data) {

	/**
	 * null을 거부한다.
	 *
	 * @throws NullPointerException 값이 null인 경우
	 */
	public CodefResponse {
		Objects.requireNonNull(result, "result");
		Objects.requireNonNull(data, "data");
	}

}
