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

	private final CodefCallBudget budget;

	public CodefClient(CodefApiKeys keys, CodefTransportSettings settings) {
		this(keys, settings, CodefCallBudget.UNLIMITED);
	}

	/**
	 * 호출 총량을 함께 쓰는 클라이언트를 만든다(LP-2).
	 * @param keys 클라이언트 키
	 * @param settings 전송 설정
	 * @param budget 프로세스를 넘는 호출 총량
	 */
	public CodefClient(CodefApiKeys keys, CodefTransportSettings settings, CodefCallBudget budget) {
		this(keys, settings, Clock.systemUTC(), budget);
	}

	CodefClient(CodefApiKeys keys, CodefTransportSettings settings, Clock clock) {
		this(keys, settings, clock, CodefCallBudget.UNLIMITED);
	}

	CodefClient(CodefApiKeys keys, CodefTransportSettings settings, Clock clock, CodefCallBudget budget) {
		Objects.requireNonNull(keys, "keys");
		Objects.requireNonNull(settings, "settings");
		Objects.requireNonNull(clock, "clock");
		this.budget = Objects.requireNonNull(budget, "budget");
		this.http = new CodefHttp(settings);
		this.tokens = keys.isConfigured() ? Optional.of(new CodefTokenCache(http, keys, clock)) : Optional.empty();
	}

	/**
	 * 관측 1회를 연다. 호출 상한은 관측마다 따로 센다.
	 * @return 새 관측
	 */
	public CodefObservation startObservation() {
		return new CodefObservation(tokens, http, budget);
	}

	CodefHttp http() {
		return http;
	}

}
