package com.beone.api.codef.transport;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/**
 * CODEF 전송 계층의 진입점. 토큰 캐시는 여기서 공유하고, 호출은 {@link #startObservation()}으로 연 관측 안에서 한다.
 * HTTP 엔드포인트, 계정 연결, DB 저장, BeONE 상태값 변환은 하지 않는다.
 */
public final class CodefClient {

	private final Optional<CodefTokenCache> tokens;

	private final CodefHttp http;

	public CodefClient(CodefApiKeys keys, CodefTransportSettings settings) {
		this(keys, settings, Clock.systemUTC());
	}

	CodefClient(CodefApiKeys keys, CodefTransportSettings settings, Clock clock) {
		Objects.requireNonNull(keys, "keys");
		Objects.requireNonNull(settings, "settings");
		Objects.requireNonNull(clock, "clock");
		this.http = new CodefHttp(settings);
		this.tokens = keys.isConfigured() ? Optional.of(new CodefTokenCache(http, keys, clock)) : Optional.empty();
	}

	/**
	 * 관측 1회를 연다. 호출 상한은 관측마다 따로 센다.
	 */
	public CodefObservation startObservation() {
		return new CodefObservation(tokens, http);
	}

	CodefHttp http() {
		return http;
	}

}
