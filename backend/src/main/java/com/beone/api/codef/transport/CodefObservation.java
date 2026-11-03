package com.beone.api.codef.transport;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beone.api.codef.CodefApproval;
import com.beone.api.codef.CodefCardPerformance;
import com.beone.api.codef.CodefHeldCard;
import com.beone.api.codef.CodefProductResponse;
import com.beone.api.codef.CodefProductResponseReader;
import com.beone.api.codef.transport.CodefCallResult.Reason;
import com.beone.api.codef.transport.CodefCallResult.Stage;

import tools.jackson.databind.json.JsonMapper;

/**
 * 관측 1회. 이 객체 하나가 호출 상한의 집계 범위이고, 토큰과 상품 API마다 {@value #CALL_LIMIT}회까지 보낸다. 넘으면 보내기 전에 거부한다.
 * 어떤 조회를 관측 1회로 볼지는 객체를 여는 호출자가 정한다. 프로세스를 넘어선 누적은 세지 않는다.
 *
 * <p>
 * 자동 재시도는 없다. 401이나 {@code CF-00401}이면 토큰을 버리고 결과를 그대로 돌려주며, 같은 요청을 다시 보내지 않는다.
 */
public final class CodefObservation {

	/** 사용자 결정. 샌드박스가 무제한이어도 줄이지 않는다. */
	public static final int CALL_LIMIT = 3;

	private static final Logger log = LoggerFactory.getLogger(CodefObservation.class);

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private static final String TOKEN_EXPIRED_CODE = "CF-00401";

	/** 로그에 남겨도 되는 결과 코드 형식. 응답 값이 로그에 그대로 실리지 않게 한다. */
	private static final Pattern RESULT_CODE = Pattern.compile("CF-\\d{5}");

	/** 이 계층이 받지 않는 로그인 입력. 연결 비밀번호와 카드 정보가 요청에 실리지 않게 한다. */
	private static final Set<String> FORBIDDEN_PARAMETERS = Set.of("id", "password", "cardNo", "cardPassword");

	private final Optional<CodefTokenCache> tokens;

	private final CodefHttp http;

	private final Map<CodefApi, Integer> productCalls = new EnumMap<>(CodefApi.class);

	private int tokenCalls;

	CodefObservation(Optional<CodefTokenCache> tokens, CodefHttp http) {
		this.tokens = tokens;
		this.http = http;
	}

	public CodefCallResult<CodefHeldCard> heldCards(Map<String, String> parameters) {
		return call(CodefApi.HELD_CARDS, parameters, CodefProductResponseReader::readHeldCards);
	}

	public CodefCallResult<CodefApproval> approvals(Map<String, String> parameters) {
		return call(CodefApi.APPROVALS, parameters, CodefProductResponseReader::readApprovals);
	}

	public CodefCallResult<CodefCardPerformance> cardPerformances(Map<String, String> parameters) {
		return call(CodefApi.CARD_PERFORMANCES, parameters, CodefProductResponseReader::readCardPerformances);
	}

	private <T> CodefCallResult<T> call(CodefApi api, Map<String, String> parameters, Reader<T> reader) {
		String body = requestBody(parameters);
		if (tokens.isEmpty()) {
			return failed(api, Stage.TOKEN, Reason.CREDENTIALS_MISSING, OptionalInt.empty());
		}
		if (!productCallRemains(api)) {
			return failed(api, Stage.PRODUCT, Reason.CALL_LIMIT_REACHED, OptionalInt.empty());
		}
		CodefTokenCache.Outcome outcome = tokens.get().current(this::acquireTokenCall);
		if (outcome instanceof CodefTokenCache.Outcome.Failure failure) {
			return failed(api, Stage.TOKEN, failure.reason(), failure.httpStatus());
		}
		// 토큰을 기다리는 동안 취소된 호출은 보내지 않는다. isInterrupted는 인터럽트 상태를 지우지 않는다.
		if (Thread.currentThread().isInterrupted()) {
			return failed(api, Stage.PRODUCT, Reason.INTERRUPTED, OptionalInt.empty());
		}
		String token = ((CodefTokenCache.Outcome.Token) outcome).value();
		if (!acquireProductCall(api)) {
			return failed(api, Stage.PRODUCT, Reason.CALL_LIMIT_REACHED, OptionalInt.empty());
		}
		return send(api, token, body, reader);
	}

	private <T> CodefCallResult<T> send(CodefApi api, String token, String body, Reader<T> reader) {
		CodefHttp.Reply reply;
		try {
			reply = http.postProduct(token, api, body);
		}
		catch (CodefHttp.Failure failure) {
			return failed(api, Stage.PRODUCT, failure.reason(), OptionalInt.empty());
		}
		CodefProductResponse<T> response = reader.read(reply.status(), reply.body());
		Optional<String> resultCode = resultCode(response);
		log.info("codef call api={} http={} resultCode={}", api, reply.status(), loggableCode(resultCode));
		if (response instanceof CodefProductResponse.Unauthorized<T> || resultCode.filter(TOKEN_EXPIRED_CODE::equals).isPresent()) {
			tokens.orElseThrow().discard(token);
		}
		return new CodefCallResult.Answered<>(response);
	}

	private static <T> CodefCallResult<T> failed(CodefApi api, Stage stage, Reason reason, OptionalInt status) {
		log.warn("codef call failed api={} stage={} reason={}", api, stage, reason);
		return new CodefCallResult.Failed<>(stage, reason, status);
	}

	private static String requestBody(Map<String, String> parameters) {
		for (String forbidden : FORBIDDEN_PARAMETERS) {
			if (parameters.containsKey(forbidden)) {
				throw new IllegalArgumentException("codef transport does not accept login input: " + forbidden);
			}
		}
		return MAPPER.writeValueAsString(parameters);
	}

	private static <T> Optional<String> resultCode(CodefProductResponse<T> response) {
		if (response instanceof CodefProductResponse.Interpreted<T> interpreted) {
			return interpreted.response().result().code();
		}
		return Optional.empty();
	}

	private static String loggableCode(Optional<String> code) {
		if (code.isEmpty()) {
			return "NONE";
		}
		return RESULT_CODE.matcher(code.get()).matches() ? code.get() : "UNRECOGNIZED";
	}

	private synchronized boolean productCallRemains(CodefApi api) {
		return productCalls.getOrDefault(api, 0) < CALL_LIMIT;
	}

	private synchronized boolean acquireProductCall(CodefApi api) {
		if (!productCallRemains(api)) {
			return false;
		}
		productCalls.merge(api, 1, Integer::sum);
		return true;
	}

	private synchronized boolean acquireTokenCall() {
		if (tokenCalls >= CALL_LIMIT) {
			return false;
		}
		tokenCalls++;
		return true;
	}

	@FunctionalInterface
	private interface Reader<T> {

		CodefProductResponse<T> read(int status, String body);

	}

}
