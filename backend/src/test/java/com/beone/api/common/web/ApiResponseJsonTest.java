package com.beone.api.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * 응답 레코드의 직렬화와 errors 정규화를 Spring 컨텍스트 없이 확인한다.
 */
class ApiResponseJsonTest {

	private static final JsonMapper DEFAULT_MAPPER = JsonMapper.builder().build();

	// 누군가 전역으로 null 속성을 생략하도록 설정한 경우를 재현한다.
	private static final JsonMapper NON_NULL_MAPPER = JsonMapper.builder()
		.changeDefaultPropertyInclusion(
				inclusion -> JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
		.build();

	@Test
	void successSerializesDataOnly() {
		String json = DEFAULT_MAPPER.writeValueAsString(ApiResponse.of(Map.of("id", "example-id")));

		assertThat(json).isEqualTo("{\"data\":{\"id\":\"example-id\"}}");
	}

	@Test
	void errorSerializesCodeMessageAndErrors() {
		ApiErrorResponse response = ApiErrorResponse.of(CommonApiErrorCode.INVALID_REQUEST,
				List.of(new FieldViolation("amountWon", "1 이상이어야 합니다."),
						new FieldViolation(null, "시작일은 종료일보다 늦을 수 없습니다.")));

		assertThat(DEFAULT_MAPPER.writeValueAsString(response)).isEqualTo("{\"code\":\"INVALID_REQUEST\","
				+ "\"message\":\"요청 값을 확인해 주세요.\",\"errors\":[{\"field\":\"amountWon\",\"message\":\"1 이상이어야 합니다.\"},"
				+ "{\"field\":null,\"message\":\"시작일은 종료일보다 늦을 수 없습니다.\"}]}");
	}

	@Test
	void nullFieldIsKeptEvenWhenNullsAreExcludedGlobally() {
		String json = NON_NULL_MAPPER.writeValueAsString(new FieldViolation(null, "요청 전체 오류"));

		assertThat(json).isEqualTo("{\"field\":null,\"message\":\"요청 전체 오류\"}");
	}

	@Test
	void nullErrorsBecomeEmptyArray() {
		ApiErrorResponse response = new ApiErrorResponse("INTERNAL_SERVER_ERROR", "요청 처리 중 오류가 발생했습니다.", null);

		assertThat(response.errors()).isEmpty();
		assertThat(DEFAULT_MAPPER.writeValueAsString(response)).endsWith("\"errors\":[]}");
		assertThat(DEFAULT_MAPPER.writeValueAsString(ApiErrorResponse.of(CommonApiErrorCode.NOT_FOUND)))
			.endsWith("\"errors\":[]}");
	}

	@Test
	void errorsAreAnImmutableCopy() {
		List<FieldViolation> source = new ArrayList<>(List.of(new FieldViolation("a", "m")));
		ApiErrorResponse response = ApiErrorResponse.of(CommonApiErrorCode.INVALID_REQUEST, source);

		source.add(new FieldViolation("b", "m"));

		assertThat(response.errors()).containsExactly(new FieldViolation("a", "m"));
		assertThatThrownBy(() -> response.errors().add(new FieldViolation("c", "m")))
			.isInstanceOf(UnsupportedOperationException.class);
	}

}
