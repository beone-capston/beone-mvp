package com.beone.api.codef.transport;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * 전송 계층 설정. 비밀값을 담지 않는다.
 *
 * @param tokenUri 토큰 발급 주소
 * @param productHost 상품 API 호스트. 기본은 샌드박스이고, 데모·정식은 별도 승인 뒤 설정으로 켠다
 * @param connectTimeout 연결 시간 초과
 * @param tokenResponseTimeout 토큰 응답 시간 초과
 * @param productResponseTimeout 상품 응답 시간 초과
 */
public record CodefTransportSettings(URI tokenUri, URI productHost, Duration connectTimeout,
		Duration tokenResponseTimeout, Duration productResponseTimeout) {

	public static final URI DEFAULT_TOKEN_URI = URI.create("https://oauth.codef.io/oauth/token");

	public static final URI SANDBOX_HOST = URI.create("https://sandbox.codef.io");

	/** 제안, 샌드박스에서 재확인. 근거 없음. */
	public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);

	/** 제안, 샌드박스에서 재확인. 공식 기본 응답 대기 100초에 여유 10초. 토큰 요청에 적용되는지는 문서에 없다. */
	public static final Duration DEFAULT_TOKEN_RESPONSE_TIMEOUT = Duration.ofSeconds(110);

	/** 공식 카드 상품 Timeout 300초에 여유 10초. 여유 10초는 제안이고, 샌드박스에서 재확인한다. */
	public static final Duration DEFAULT_PRODUCT_RESPONSE_TIMEOUT = Duration.ofSeconds(310);

	private static final Set<String> PRODUCT_HOSTS = Set.of("sandbox.codef.io", "development.codef.io", "api.codef.io");

	private static final Set<String> TOKEN_HOSTS = Set.of("oauth.codef.io");

	private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1");

	public CodefTransportSettings {
		requireAllowed(tokenUri, TOKEN_HOSTS, "token uri");
		requireAllowed(productHost, PRODUCT_HOSTS, "product host");
		requirePositive(connectTimeout, "connect timeout");
		requirePositive(tokenResponseTimeout, "token response timeout");
		requirePositive(productResponseTimeout, "product response timeout");
	}

	public static CodefTransportSettings defaults() {
		return new CodefTransportSettings(DEFAULT_TOKEN_URI, SANDBOX_HOST, DEFAULT_CONNECT_TIMEOUT,
				DEFAULT_TOKEN_RESPONSE_TIMEOUT, DEFAULT_PRODUCT_RESPONSE_TIMEOUT);
	}

	// CODEF 공식 호스트의 HTTPS만 허용한다. 평문 HTTP는 로컬 스텁 서버에만 허용한다.
	private static void requireAllowed(URI uri, Set<String> hosts, String name) {
		Objects.requireNonNull(uri, name);
		boolean official = "https".equals(uri.getScheme()) && hosts.contains(uri.getHost());
		boolean loopback = "http".equals(uri.getScheme()) && LOOPBACK_HOSTS.contains(uri.getHost());
		if (!official && !loopback) {
			throw new IllegalArgumentException(name + " is not an allowed codef address");
		}
	}

	private static void requirePositive(Duration duration, String name) {
		Objects.requireNonNull(duration, name);
		// 대기는 밀리초 단위이므로 1ms 미만은 대기 없이 시간 초과가 된다.
		if (duration.compareTo(Duration.ofMillis(1)) < 0) {
			throw new IllegalArgumentException(name + " must be at least 1ms");
		}
	}

}
