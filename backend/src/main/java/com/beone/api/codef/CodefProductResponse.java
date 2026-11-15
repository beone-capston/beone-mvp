package com.beone.api.codef;

import java.util.Objects;

/**
 * CODEF 상품 API 응답 하나의 전송 단계 결과(#43). HTTP 상태 구분과 본문 해석 결과를 나눠 담는다.
 *
 * <p>
 * HTTP 상태 구분은 결과 코드 분류({@link CodefResultCategory})와 별개다. 본문은 HTTP 200일 때만 해석하며({@link Interpreted}), 401과 그 밖의
 * 상태에서는 본문을 해석하거나 보관하지 않는다. 어느 결과도 응답 원문을 담지 않는다. CODEF 결과를 쓸 수 없어도 수동 입력 경로는 그대로
 * 유지한다(호출자 책임).
 *
 * @param <T> 본문 항목 타입
 */
public sealed interface CodefProductResponse<T> {

	/**
	 * HTTP 200. 본문을 URL 디코딩한 뒤 {@link CodefCardResponseParser}로 해석한 결과다. 디코딩에 실패하면 결과는 미분류, {@code data}는 형식
	 * 오류다.
	 *
	 * @param <T> 본문 항목 타입
	 * @param response 본문 해석 결과
	 */
	record Interpreted<T>(CodefResponse<T> response) implements CodefProductResponse<T> {

		/**
		 * null을 거부한다.
		 *
		 * @throws NullPointerException 값이 null인 경우
		 */
		public Interpreted {
			Objects.requireNonNull(response, "response");
		}

	}

	/**
	 * HTTP 401. 공식 SDK가 인증 오류로 나누는 상태다. 본문은 해석하지 않는다.
	 *
	 * @param <T> 본문 항목 타입
	 */
	record Unauthorized<T>() implements CodefProductResponse<T> {
	}

	/**
	 * 200·401이 아닌 상태. 공식 SDK가 서버 오류로 묶는 상태이며, 이 단계에서는 서버·전송 오류로 둔다. 본문은 해석하지 않는다.
	 *
	 * @param <T> 본문 항목 타입
	 * @param status HTTP 상태 코드
	 */
	record HttpFailure<T>(int status) implements CodefProductResponse<T> {

		/**
		 * 상태 코드 범위를 확인한다.
		 *
		 * @throws IllegalArgumentException 200·401이거나 100~599 밖인 경우
		 */
		public HttpFailure {
			CodefProductResponseReader.requireStatus(status);
			if (status == 200 || status == 401) {
				throw new IllegalArgumentException("status 200 and 401 have their own results");
			}
		}

	}

}
