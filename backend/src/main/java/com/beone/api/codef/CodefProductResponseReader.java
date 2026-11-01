package com.beone.api.codef;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * CODEF 상품 API 응답의 전송 단계 해석(#43). HTTP 상태 코드와 응답 본문 문자열을 받아 {@link CodefProductResponse}로 나눈다.
 *
 * <p>
 * 근거는 공식 SDK {@code codef-io/easycodef-java-v2}(커밋 {@code 721f566})의 {@code ResponseHandler}다. 공식 SDK 구현에서 확인한 동작이며,
 * 공식 명세 문서가 아니다. SDK와 같게 상품 응답 본문을 UTF-8로 URL 디코딩하므로 {@code +}는 공백이 된다. 잘못된 퍼센트 인코딩은 SDK에서
 * 처리되지 않는 예외지만, 여기서는 원문 없이 형식 오류로 돌려준다. 실제 HTTP 호출·토큰·재시도·요청 본문은 다루지 않는다.
 */
public final class CodefProductResponseReader {

	private CodefProductResponseReader() {
	}

	/**
	 * 승인내역 응답을 전송 단계에서 나눈다.
	 *
	 * @param status HTTP 상태 코드
	 * @param body 응답 본문. 200이 아니면 읽지 않는다
	 * @return 상태별 결과
	 * @throws NullPointerException 본문이 null인 경우
	 * @throws IllegalArgumentException 상태 코드가 100~599 밖인 경우
	 */
	public static CodefProductResponse<CodefApproval> readApprovals(int status, String body) {
		return read(status, body, CodefCardResponseParser::parseApprovals);
	}

	/**
	 * 보유카드 응답을 전송 단계에서 나눈다.
	 *
	 * @param status HTTP 상태 코드
	 * @param body 응답 본문. 200이 아니면 읽지 않는다
	 * @return 상태별 결과
	 * @throws NullPointerException 본문이 null인 경우
	 * @throws IllegalArgumentException 상태 코드가 100~599 밖인 경우
	 */
	public static CodefProductResponse<CodefHeldCard> readHeldCards(int status, String body) {
		return read(status, body, CodefCardResponseParser::parseHeldCards);
	}

	/**
	 * 실적조회 응답을 전송 단계에서 나눈다.
	 *
	 * @param status HTTP 상태 코드
	 * @param body 응답 본문. 200이 아니면 읽지 않는다
	 * @return 상태별 결과
	 * @throws NullPointerException 본문이 null인 경우
	 * @throws IllegalArgumentException 상태 코드가 100~599 밖인 경우
	 */
	public static CodefProductResponse<CodefCardPerformance> readCardPerformances(int status, String body) {
		return read(status, body, CodefCardResponseParser::parseCardPerformances);
	}

	static void requireStatus(int status) {
		if (status < 100 || status > 599) {
			throw new IllegalArgumentException("status must be between 100 and 599");
		}
	}

	private static <T> CodefProductResponse<T> read(int status, String body, Function<String, CodefResponse<T>> parser) {
		requireStatus(status);
		Objects.requireNonNull(body, "body");
		if (status == 401) {
			return new CodefProductResponse.Unauthorized<>();
		}
		if (status != 200) {
			return new CodefProductResponse.HttpFailure<>(status);
		}
		String decoded;
		try {
			decoded = URLDecoder.decode(body, StandardCharsets.UTF_8);
		}
		catch (IllegalArgumentException invalid) {
			// 디코더 예외 문구에는 본문 일부가 들어갈 수 있어 전달하지 않는다.
			return new CodefProductResponse.Interpreted<>(new CodefResponse<>(
					new CodefResult(Optional.empty(), Optional.empty(), CodefResultCategory.UNCLASSIFIED),
					new CodefList.Malformed<>("response body is not valid URL encoding")));
		}
		return new CodefProductResponse.Interpreted<>(parser.apply(decoded));
	}

}
