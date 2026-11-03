package com.beone.api.codef.transport;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLParameters;

import com.beone.api.codef.transport.CodefCallResult.Reason;

/**
 * CODEF로 HTTP 요청을 한 번 보낸다. 해석, 캐시, 로그는 하지 않는다.
 *
 * <p>
 * 다시 보내지 않고 리다이렉트를 따라가지 않는다. 본문까지 포함한 전체 대기를 여기서 제한한다.
 * 예외 메시지에 본문이 섞이지 않도록 실패는 이유만 남긴다.
 */
final class CodefHttp {

	private static final String[] TLS_PROTOCOLS = { "TLSv1.3", "TLSv1.2" };

	/** 공식 REST API 예제의 토큰 요청 본문. */
	private static final String TOKEN_FORM = "grant_type=client_credentials&scope=read";

	/** 제안, 샌드박스에서 재확인. 공식 문서와 SDK 모두 명시하지 않는다. */
	private static final String PRODUCT_CONTENT_TYPE = "application/json";

	private final CodefTransportSettings settings;

	private final HttpClient client;

	CodefHttp(CodefTransportSettings settings) {
		this.settings = settings;
		SSLParameters tls = new SSLParameters();
		tls.setProtocols(TLS_PROTOCOLS);
		this.client = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.followRedirects(HttpClient.Redirect.NEVER)
			.connectTimeout(settings.connectTimeout())
			.sslParameters(tls)
			.build();
	}

	Reply postToken(CodefApiKeys keys) throws Failure {
		HttpRequest request = HttpRequest.newBuilder(settings.tokenUri())
			.header("Authorization", keys.basicAuthorization())
			.header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString(TOKEN_FORM, StandardCharsets.UTF_8))
			.build();
		return send(request, settings.tokenResponseTimeout());
	}

	Reply postProduct(String accessToken, CodefApi api, String jsonBody) throws Failure {
		HttpRequest request = HttpRequest.newBuilder(productUri(api))
			.header("Authorization", "Bearer " + accessToken)
			.header("Content-Type", PRODUCT_CONTENT_TYPE)
			.POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
			.build();
		return send(request, settings.productResponseTimeout());
	}

	HttpClient client() {
		return client;
	}

	private URI productUri(CodefApi api) {
		return settings.productHost().resolve(api.path());
	}

	private Reply send(HttpRequest request, Duration timeout) throws Failure {
		CompletableFuture<HttpResponse<String>> future = client.sendAsync(request,
				HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		try {
			HttpResponse<String> response = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
			return new Reply(response.statusCode(), response.body());
		}
		catch (TimeoutException exception) {
			future.cancel(true);
			throw new Failure(Reason.TIMEOUT);
		}
		catch (InterruptedException exception) {
			future.cancel(true);
			Thread.currentThread().interrupt();
			throw new Failure(Reason.INTERRUPTED);
		}
		catch (ExecutionException exception) {
			throw new Failure(exception.getCause() instanceof HttpTimeoutException ? Reason.TIMEOUT : Reason.CONNECTION_FAILED);
		}
	}

	/** HTTP 응답 하나. 값을 로그나 예외로 내보내지 않는다. */
	record Reply(int status, String body) {

		@Override
		public String toString() {
			return "Reply[status=" + status + "]";
		}

	}

	/** 응답을 받지 못한 실패. 원인 예외와 메시지를 싣지 않는다. */
	static final class Failure extends Exception {

		private static final long serialVersionUID = 1L;

		private final transient Reason reason;

		Failure(Reason reason) {
			super(reason.name(), null, false, false);
			this.reason = reason;
		}

		Reason reason() {
			return reason;
		}

	}

}
