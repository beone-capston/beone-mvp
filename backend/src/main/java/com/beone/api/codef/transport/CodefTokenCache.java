package com.beone.api.codef.transport;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beone.api.codef.transport.CodefCallResult.Reason;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 접근 토큰을 서버 메모리에만 보관한다. 호출마다 발급하지 않고, 발급 시각부터 7일이 지나면 다시 발급한다.
 * 잠금으로 동시 요청이 토큰을 여러 번 발급하지 않게 한다.
 */
final class CodefTokenCache {

	/** 공식 유효기간 일주일. 응답의 만료 필드 존재는 샌드박스에서 확인한다. */
	static final Duration TOKEN_LIFETIME = Duration.ofDays(7);

	private static final Logger log = LoggerFactory.getLogger(CodefTokenCache.class);

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private final CodefHttp http;

	private final CodefApiKeys keys;

	private final Clock clock;

	private final ReentrantLock lock = new ReentrantLock();

	private Issued issued;

	CodefTokenCache(CodefHttp http, CodefApiKeys keys, Clock clock) {
		this.http = http;
		this.keys = keys;
		this.clock = clock;
	}

	/**
	 * 유효한 토큰을 돌려준다. 없으면 {@code mayIssue}가 허락할 때만 발급을 요청한다.
	 */
	Outcome current(BooleanSupplier mayIssue) {
		lock.lock();
		try {
			if (issued != null && clock.instant().isBefore(issued.expiresAt())) {
				return new Outcome.Token(issued.value());
			}
			issued = null;
			if (!mayIssue.getAsBoolean()) {
				return new Outcome.Failure(Reason.CALL_LIMIT_REACHED, OptionalInt.empty());
			}
			return issue();
		}
		finally {
			lock.unlock();
		}
	}

	/**
	 * 인증 실패한 토큰을 버린다. 그사이 새로 발급된 토큰은 건드리지 않는다.
	 */
	void discard(String token) {
		lock.lock();
		try {
			if (issued != null && issued.value().equals(token)) {
				issued = null;
			}
		}
		finally {
			lock.unlock();
		}
	}

	private Outcome issue() {
		// 요청 전 시각을 발급 시각으로 쓴다. 실제 발급보다 늦게 만료된다고 보지 않기 위해서다.
		Instant requestedAt = clock.instant();
		CodefHttp.Reply reply;
		try {
			reply = http.postToken(keys);
		}
		catch (CodefHttp.Failure failure) {
			log.warn("codef call failed api=TOKEN reason={}", failure.reason());
			return new Outcome.Failure(failure.reason(), OptionalInt.empty());
		}
		log.info("codef call api=TOKEN http={}", reply.status());
		if (reply.status() != 200) {
			return new Outcome.Failure(Reason.TOKEN_REJECTED, OptionalInt.of(reply.status()));
		}
		Optional<String> token = readAccessToken(reply.body());
		if (token.isEmpty()) {
			return new Outcome.Failure(Reason.TOKEN_UNREADABLE, OptionalInt.of(reply.status()));
		}
		issued = new Issued(token.get(), requestedAt.plus(TOKEN_LIFETIME));
		return new Outcome.Token(token.get());
	}

	/**
	 * 응답이 중괄호로 시작하면 그대로 읽고, 아니면 URL 디코딩한 뒤 읽는다. 샌드박스 확인 뒤 한쪽으로 고정한다.
	 * 인코딩되지 않은 토큰을 디코딩하면 {@code +}가 공백이 되어 토큰이 망가지므로 항상 디코딩하지 않는다.
	 */
	static Optional<String> readAccessToken(String body) {
		try {
			String json = body.startsWith("{") ? body : URLDecoder.decode(body, StandardCharsets.UTF_8);
			JsonNode token = MAPPER.readTree(json).get("access_token");
			if (token == null || !token.isString() || token.stringValue().isBlank()) {
				return Optional.empty();
			}
			return Optional.of(token.stringValue());
		}
		catch (JacksonException | IllegalArgumentException exception) {
			return Optional.empty();
		}
	}

	private record Issued(String value, Instant expiresAt) {

		@Override
		public String toString() {
			return "Issued[masked]";
		}

	}

	/** 토큰 조회 결과. */
	sealed interface Outcome {

		record Token(String value) implements Outcome {

			@Override
			public String toString() {
				return "Token[masked]";
			}

		}

		record Failure(Reason reason, OptionalInt httpStatus) implements Outcome {

		}

	}

}
