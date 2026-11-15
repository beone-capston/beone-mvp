package com.beone.api.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.beone.api.support.WebSliceTest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/**
 * MockMvc로 실제 디스패치·직렬화·예외 처리 경계를 거쳐 공통 응답 계약을 확인한다.
 *
 * <p>입력값과 예외 메시지에 비밀 표식을 넣고, 모든 테스트 뒤에 응답·로그에 표식이 없는지 확인한다.
 */
@WebSliceTest(properties = "logging.level.com.beone.api.common.web=DEBUG")
@Import({ GlobalExceptionHandlerWebTest.ContractTestController.class,
		GlobalExceptionHandlerWebTest.FeatureExceptionHandler.class })
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerWebTest {

	static final String SECRET_INPUT = "SECRET-INPUT-7f3a";

	static final String SECRET_MESSAGE = "SECRET-EXCEPTION-9c1d";

	private static final String BASE = "/test/api";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	MockMvc mockMvc;

	@AfterEach
	void logsHideSecretsAndFrameworkMessages(CapturedOutput output) {
		assertThat(output.getAll()).doesNotContain(SECRET_INPUT)
			.doesNotContain(SECRET_MESSAGE)
			.doesNotContain("Request method")
			.doesNotContain("Response already committed")
			.doesNotContain("Failure in @ExceptionHandler")
			.doesNotContainPattern("(?m)^\\s+at ");
	}

	@Test
	void successWrapsDataOnly() throws Exception {
		MvcResult result = perform(get(BASE + "/success"));

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertJson(result, "{\"data\":{\"id\":\"example-id\"}}");
	}

	@Test
	void noContentHasEmptyBody() throws Exception {
		MvcResult result = perform(delete(BASE + "/items/1"));

		assertThat(result.getResponse().getStatus()).isEqualTo(204);
		assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
	}

	@Test
	void requestBodyValidationReturnsFieldAndObjectErrors(CapturedOutput output) throws Exception {
		String body = """
				{"amountWon":0,"memo":"%s","startDate":"2026-10-10","endDate":"2026-10-01"}
				""".formatted(SECRET_INPUT);

		MvcResult result = perform(post(BASE + "/periods").contentType(MediaType.APPLICATION_JSON).content(body));

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		Map<String, Object> json = assertErrorShape(result, "INVALID_REQUEST", "요청 값을 확인해 주세요.");
		assertThat(errorsOf(json)).containsExactlyInAnyOrder(violation("amountWon", "1 이상이어야 합니다."),
				violation("memo", "10자 이하여야 합니다."), violation(null, "시작일은 종료일보다 늦을 수 없습니다."));
		assertThat(output.getAll()).contains("API error code=INVALID_REQUEST status=400 "
				+ "exception=org.springframework.web.bind.MethodArgumentNotValidException");
	}

	@Test
	void malformedJsonIsBadRequestWithoutDetails() throws Exception {
		MvcResult result = perform(post(BASE + "/periods").contentType(MediaType.APPLICATION_JSON)
			.content("{\"amountWon\": \"" + SECRET_INPUT));

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertJson(result, "{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 값을 확인해 주세요.\",\"errors\":[]}");
	}

	@Test
	void jsonTypeErrorIsBadRequestWithoutDetails() throws Exception {
		String body = """
				{"amountWon":"%s","startDate":"2026-10-01","endDate":"2026-10-10"}
				""".formatted(SECRET_INPUT);

		MvcResult result = perform(post(BASE + "/periods").contentType(MediaType.APPLICATION_JSON).content(body));

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertJson(result, "{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 값을 확인해 주세요.\",\"errors\":[]}");
	}

	@Test
	void pathVariableTypeMismatchNamesField() throws Exception {
		MvcResult result = perform(get(BASE + "/items/" + SECRET_INPUT));

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		Map<String, Object> json = assertErrorShape(result, "INVALID_REQUEST", "요청 값을 확인해 주세요.");
		assertThat(errorsOf(json)).containsExactly(violation("id", "형식이 올바르지 않습니다."));
	}

	@Test
	void queryParameterTypeMismatchNamesField() throws Exception {
		MvcResult result = perform(get(BASE + "/search").param("amountWon", SECRET_INPUT));

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		Map<String, Object> json = assertErrorShape(result, "INVALID_REQUEST", "요청 값을 확인해 주세요.");
		assertThat(errorsOf(json)).containsExactly(violation("amountWon", "형식이 올바르지 않습니다."));
	}

	@Test
	void missingQueryParameterIsRequired() throws Exception {
		MvcResult result = perform(get(BASE + "/search"));

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		Map<String, Object> json = assertErrorShape(result, "INVALID_REQUEST", "요청 값을 확인해 주세요.");
		assertThat(errorsOf(json)).containsExactly(violation("amountWon", "필수 값입니다."));
	}

	@Test
	void methodParameterValidationIsBadRequest() throws Exception {
		MvcResult result = perform(get(BASE + "/validated-params").param("amountWon", "0").param("nickname", " "));

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		Map<String, Object> json = assertErrorShape(result, "INVALID_REQUEST", "요청 값을 확인해 주세요.");
		assertThat(errorsOf(json)).containsExactlyInAnyOrder(violation("amountWon", "1 이상이어야 합니다."),
				violation("nickname", "필수 값입니다."));
	}

	@Test
	void returnValueValidationIsSafeServerError(CapturedOutput output) throws Exception {
		MvcResult result = perform(get(BASE + "/return-invalid"));

		assertThat(result.getResponse().getStatus()).isEqualTo(500);
		assertJson(result,
				"{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"요청 처리 중 오류가 발생했습니다.\",\"errors\":[]}");
		assertThat(output.getAll()).contains("API error code=INTERNAL_SERVER_ERROR status=500 "
				+ "exception=org.springframework.web.method.annotation.HandlerMethodValidationException");
	}

	@Test
	void unknownPathIsNotFound() throws Exception {
		MvcResult result = perform(get(BASE + "/does-not-exist"));

		assertThat(result.getResponse().getStatus()).isEqualTo(404);
		assertJson(result, "{\"code\":\"NOT_FOUND\",\"message\":\"요청한 리소스를 찾을 수 없습니다.\",\"errors\":[]}");
	}

	@Test
	void unsupportedMethodKeepsAllowHeader() throws Exception {
		MvcResult result = perform(get(BASE + "/periods"));

		assertThat(result.getResponse().getStatus()).isEqualTo(405);
		assertThat(result.getResponse().getHeader(HttpHeaders.ALLOW)).contains("POST");
		assertJson(result,
				"{\"code\":\"METHOD_NOT_ALLOWED\",\"message\":\"지원하지 않는 요청 방식입니다.\",\"errors\":[]}");
	}

	@Test
	void unsupportedMediaTypeKeepsAcceptHeader() throws Exception {
		MvcResult result = perform(
				post(BASE + "/periods").contentType(MediaType.TEXT_PLAIN).content(SECRET_INPUT));

		assertThat(result.getResponse().getStatus()).isEqualTo(415);
		assertThat(result.getResponse().getHeader(HttpHeaders.ACCEPT)).contains(MediaType.APPLICATION_JSON_VALUE);
		assertJson(result,
				"{\"code\":\"UNSUPPORTED_MEDIA_TYPE\",\"message\":\"지원하지 않는 콘텐츠 형식입니다.\",\"errors\":[]}");
	}

	@Test
	void notAcceptableMapsToInvalidRequestWithJsonBody() throws Exception {
		MvcResult result = perform(get(BASE + "/success").accept(MediaType.APPLICATION_XML));

		assertThat(result.getResponse().getStatus()).isEqualTo(406);
		assertJson(result, "{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 값을 확인해 주세요.\",\"errors\":[]}");
	}

	@Test
	void errorBodyIsJsonEvenWhenClientDoesNotAcceptJson() throws Exception {
		MvcResult result = perform(get(BASE + "/search").accept(MediaType.APPLICATION_XML));

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertErrorShape(result, "INVALID_REQUEST", "요청 값을 확인해 주세요.");
	}

	@Test
	void missingHeaderMapsToInvalidRequestWithoutDetails() throws Exception {
		MvcResult result = perform(get(BASE + "/header-required"));

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertJson(result, "{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 값을 확인해 주세요.\",\"errors\":[]}");
	}

	@Test
	void unlistedClientStatusKeepsStatusAndMapsToInvalidRequest() throws Exception {
		MvcResult result = perform(get(BASE + "/status/409"));

		assertThat(result.getResponse().getStatus()).isEqualTo(409);
		assertJson(result, "{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 값을 확인해 주세요.\",\"errors\":[]}");
	}

	@Test
	void unlistedServerStatusKeepsStatusAndMapsToInternalError() throws Exception {
		MvcResult result = perform(get(BASE + "/status/503"));

		assertThat(result.getResponse().getStatus()).isEqualTo(503);
		assertThat(result.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("30");
		assertJson(result,
				"{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"요청 처리 중 오류가 발생했습니다.\",\"errors\":[]}");
	}

	@Test
	void unexpectedExceptionIsSafeServerErrorWithDiagnostics(CapturedOutput output) throws Exception {
		MvcResult result = perform(get(BASE + "/unexpected"));

		assertThat(result.getResponse().getStatus()).isEqualTo(500);
		assertJson(result,
				"{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"요청 처리 중 오류가 발생했습니다.\",\"errors\":[]}");
		assertThat(output.getAll()).contains("API error code=INTERNAL_SERVER_ERROR status=500 "
				+ "exception=java.lang.IllegalStateException causes=[java.io.IOException] "
				+ "origin=com.beone.api.common.web.GlobalExceptionHandlerWebTest$ContractTestController#unexpected:");
	}

	@Test
	void illegalArgumentIsNotTreatedAsBadRequest() throws Exception {
		MvcResult result = perform(get(BASE + "/illegal-argument"));

		assertThat(result.getResponse().getStatus()).isEqualTo(500);
		assertErrorShape(result, "INTERNAL_SERVER_ERROR", "요청 처리 중 오류가 발생했습니다.");
	}

	@Test
	void committedResponseIsNotRewrittenForUnexpectedException() throws Exception {
		MvcResult result = perform(get(BASE + "/committed"));

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).isEqualTo("partial");
	}

	@Test
	void committedResponseIsNotRewrittenForFrameworkException() throws Exception {
		MvcResult result = perform(get(BASE + "/committed-framework"));

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).isEqualTo("partial");
	}

	@Test
	void featureHandlerTakesPrecedenceOverCommonHandler() throws Exception {
		MvcResult result = perform(get(BASE + "/feature-conflict"));

		assertThat(result.getResponse().getStatus()).isEqualTo(409);
		assertJson(result, "{\"code\":\"SAMPLE_CONFLICT\",\"message\":\"이미 처리된 요청입니다.\",\"errors\":[]}");
	}

	private MvcResult perform(RequestBuilder request) throws Exception {
		MvcResult result = this.mockMvc.perform(request).andReturn();
		String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(body).doesNotContain(SECRET_INPUT).doesNotContain(SECRET_MESSAGE);
		return result;
	}

	private static void assertJson(MvcResult result, String expected) throws Exception {
		assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
		JsonAssert.comparator(JsonCompareMode.STRICT)
			.assertIsMatch(expected, result.getResponse().getContentAsString(StandardCharsets.UTF_8));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> assertErrorShape(MvcResult result, String code, String message)
			throws Exception {
		assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
		Map<String, Object> json = JSON.readValue(result.getResponse().getContentAsString(StandardCharsets.UTF_8),
				Map.class);
		assertThat(json).containsOnlyKeys("code", "message", "errors")
			.containsEntry("code", code)
			.containsEntry("message", message);
		assertThat(json.get("errors")).isInstanceOf(List.class);
		return json;
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> errorsOf(Map<String, Object> json) {
		List<Map<String, Object>> errors = (List<Map<String, Object>>) json.get("errors");
		assertThat(errors).allSatisfy(error -> assertThat(error).containsOnlyKeys("field", "message"));
		return errors;
	}

	// field:null을 포함한 키 집합까지 비교하도록 null 값을 허용하는 Map을 만든다.
	private static Map<String, Object> violation(String field, String message) {
		Map<String, Object> violation = new HashMap<>();
		violation.put("field", field);
		violation.put("message", message);
		return violation;
	}

	@ValidPeriod(message = "시작일은 종료일보다 늦을 수 없습니다.")
	record PeriodRequest(@NotNull(message = "필수 값입니다.") @Min(value = 1, message = "1 이상이어야 합니다.") Long amountWon,
			@Size(max = 10, message = "10자 이하여야 합니다.") String memo,
			@NotNull(message = "필수 값입니다.") LocalDate startDate,
			@NotNull(message = "필수 값입니다.") LocalDate endDate) {
	}

	@Documented
	@Target(ElementType.TYPE)
	@Retention(RetentionPolicy.RUNTIME)
	@Constraint(validatedBy = ValidPeriodValidator.class)
	@interface ValidPeriod {

		String message();

		Class<?>[] groups() default {};

		Class<? extends Payload>[] payload() default {};

	}

	static class ValidPeriodValidator implements ConstraintValidator<ValidPeriod, PeriodRequest> {

		@Override
		public boolean isValid(PeriodRequest value, ConstraintValidatorContext context) {
			if (value == null || value.startDate() == null || value.endDate() == null) {
				return true;
			}
			return !value.startDate().isAfter(value.endDate());
		}

	}

	static class SampleConflictException extends RuntimeException {

		SampleConflictException() {
			super(SECRET_MESSAGE);
		}

	}

	enum SampleErrorCode implements ApiErrorCode {

		SAMPLE_CONFLICT("SAMPLE_CONFLICT", HttpStatus.CONFLICT, "이미 처리된 요청입니다.");

		private final String code;

		private final HttpStatus status;

		private final String message;

		SampleErrorCode(String code, HttpStatus status, String message) {
			this.code = code;
			this.status = status;
			this.message = message;
		}

		@Override
		public String code() {
			return this.code;
		}

		@Override
		public HttpStatus status() {
			return this.status;
		}

		@Override
		public String message() {
			return this.message;
		}

	}

	// 기능별 advice는 공통 처리기보다 높은 우선순위로 둔다.
	@RestControllerAdvice
	@Order(Ordered.HIGHEST_PRECEDENCE)
	static class FeatureExceptionHandler {

		@ExceptionHandler(SampleConflictException.class)
		ResponseEntity<ApiErrorResponse> handleSampleConflict() {
			SampleErrorCode errorCode = SampleErrorCode.SAMPLE_CONFLICT;
			return ResponseEntity.status(errorCode.status()).body(ApiErrorResponse.of(errorCode));
		}

	}

	@RestController
	@RequestMapping(BASE)
	static class ContractTestController {

		@GetMapping("/success")
		ApiResponse<Map<String, String>> success() {
			return ApiResponse.of(Map.of("id", "example-id"));
		}

		@DeleteMapping("/items/{id}")
		ResponseEntity<Void> deleteItem(@PathVariable long id) {
			return ResponseEntity.noContent().build();
		}

		@GetMapping("/items/{id}")
		ApiResponse<Long> item(@PathVariable long id) {
			return ApiResponse.of(id);
		}

		@PostMapping(path = "/periods", consumes = MediaType.APPLICATION_JSON_VALUE)
		ApiResponse<String> period(@Valid @RequestBody PeriodRequest request) {
			return ApiResponse.of("ok");
		}

		@GetMapping("/search")
		ApiResponse<Integer> search(@RequestParam int amountWon) {
			return ApiResponse.of(amountWon);
		}

		@GetMapping("/validated-params")
		ApiResponse<String> validatedParams(
				@RequestParam @Min(value = 1, message = "1 이상이어야 합니다.") int amountWon,
				@RequestParam("nickname") @NotBlank(message = "필수 값입니다.") String name) {
			return ApiResponse.of(name);
		}

		@GetMapping("/return-invalid")
		@NotBlank(message = SECRET_MESSAGE)
		String returnInvalid() {
			return "";
		}

		@GetMapping("/header-required")
		ApiResponse<String> headerRequired(@RequestHeader("X-Sample-Token") String token) {
			return ApiResponse.of("ok");
		}

		@GetMapping("/status/{status}")
		ApiResponse<String> status(@PathVariable int status) {
			throw new ResponseStatusException(HttpStatus.valueOf(status), SECRET_MESSAGE) {

				@Override
				public HttpHeaders getHeaders() {
					HttpHeaders headers = new HttpHeaders();
					headers.set(HttpHeaders.RETRY_AFTER, "30");
					return headers;
				}

			};
		}

		@GetMapping("/unexpected")
		ApiResponse<String> unexpected() {
			throw new IllegalStateException(SECRET_MESSAGE, new IOException(SECRET_MESSAGE));
		}

		@GetMapping("/illegal-argument")
		ApiResponse<String> illegalArgument() {
			throw new IllegalArgumentException(SECRET_MESSAGE);
		}

		@GetMapping("/committed")
		void committed(HttpServletResponse response) throws IOException {
			commitPartialBody(response);
			throw new IllegalStateException(SECRET_MESSAGE);
		}

		@GetMapping("/committed-framework")
		void committedFramework(HttpServletResponse response) throws IOException {
			commitPartialBody(response);
			throw new ResponseStatusException(HttpStatus.CONFLICT, SECRET_MESSAGE);
		}

		@GetMapping("/feature-conflict")
		ApiResponse<String> featureConflict() {
			throw new SampleConflictException();
		}

		private static void commitPartialBody(HttpServletResponse response) throws IOException {
			response.setStatus(200);
			response.getWriter().write("partial");
			response.flushBuffer();
		}

	}

}
