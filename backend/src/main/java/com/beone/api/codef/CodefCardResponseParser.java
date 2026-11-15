package com.beone.api.codef;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * CODEF 카드 API 응답 본문을 해석한다(#41). 전송 계층(HTTP·OAuth·키·암호화)과 분리된 순수 해석이며 요청을 만들거나 보내지 않는다.
 *
 * <p>
 * 공통 결과 구조는 {@code result}와 {@code data}다(공통 REST 문서). {@code result.code}는 {@link CodefResultCategory}로 분류하고,
 * {@code data}는 {@link CodefList} 규칙으로 항목을 읽는다. 두 결과는 독립이다. 본문이 JSON 객체가 아니어도 예외를 던지지 않고 미분류·형식
 * 오류로 돌려주며, 결과·오류 문구에 응답 원문을 담지 않는다.
 */
public final class CodefCardResponseParser {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private CodefCardResponseParser() {
	}

	/**
	 * 승인내역 응답을 해석한다.
	 *
	 * @param body 응답 본문
	 * @return 결과 코드 분류와 승인내역 항목
	 * @throws NullPointerException 본문이 null인 경우
	 */
	public static CodefResponse<CodefApproval> parseApprovals(String body) {
		return parse(body, CodefApproval::read);
	}

	/**
	 * 보유카드 응답을 해석한다.
	 *
	 * @param body 응답 본문
	 * @return 결과 코드 분류와 보유카드 항목
	 * @throws NullPointerException 본문이 null인 경우
	 */
	public static CodefResponse<CodefHeldCard> parseHeldCards(String body) {
		return parse(body, CodefHeldCard::read);
	}

	/**
	 * 실적조회 응답을 해석한다.
	 *
	 * @param body 응답 본문
	 * @return 결과 코드 분류와 카드별 실적 항목
	 * @throws NullPointerException 본문이 null인 경우
	 */
	public static CodefResponse<CodefCardPerformance> parseCardPerformances(String body) {
		return parse(body, CodefCardPerformance::read);
	}

	private static <T> CodefResponse<T> parse(String body, Function<JsonNode, T> reader) {
		Objects.requireNonNull(body, "body");
		JsonNode root;
		try {
			root = MAPPER.readTree(body);
		}
		catch (JacksonException invalid) {
			// 파서 예외 문구에는 본문 일부가 들어갈 수 있어 전달하지 않는다.
			return unreadable("response body is not valid JSON");
		}
		if (root == null || !root.isObject()) {
			return unreadable("response body is not a JSON object");
		}
		JsonNode data = root.get("data");
		boolean continue2Way = data != null && data.isObject() && data.get("continue2Way") != null
				&& data.get("continue2Way").isBoolean() && data.get("continue2Way").booleanValue();
		JsonNode result = root.get("result");
		Optional<String> code = Optional.empty();
		Optional<String> transactionId = Optional.empty();
		if (result != null && result.isObject()) {
			code = CodefFields.text(result, "code");
			transactionId = CodefFields.text(result, "transactionId");
		}
		return new CodefResponse<>(
				new CodefResult(code, transactionId, CodefResultCategory.classify(code, continue2Way)),
				CodefFields.list(data, reader, false));
	}

	private static <T> CodefResponse<T> unreadable(String reason) {
		return new CodefResponse<>(new CodefResult(Optional.empty(), Optional.empty(), CodefResultCategory.UNCLASSIFIED),
				new CodefList.Malformed<>(reason));
	}

}
