package com.beone.api.codef.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.beone.api.codef.CodefHeldCard;
import com.beone.api.codef.CodefProductResponse;
import com.beone.api.codef.CodefResultCategory;
import com.beone.api.codef.transport.CodefCallResult.Reason;
import com.beone.api.codef.transport.CodefCallResult.Stage;

/**
 * 합성 응답으로 전송 계층을 시험한다. 로컬 서버만 호출하며 실제 응답을 검증하는 것은 아니다.
 */
class CodefClientTest {

	private static final String TEST_ID = "synthetic-id";

	private static final String TEST_VALUE = "synthetic-value";

	private static final String TOKEN = "synthetic+token/1";

	private static final String TOKEN_JSON = "{\"access_token\":\"" + TOKEN + "\",\"token_type\":\"bearer\",\"expires_in\":604799}";

	private static final String SUCCESS = "{\"result\":{\"code\":\"CF-00000\",\"message\":\"body-message-marker\"},\"data\":[]}";

	private static final String CARDS = CodefApi.HELD_CARDS.path();

	private static final Map<String, String> PARAMETERS = Map.of("organization", "0301");

	private final MutableClock clock = new MutableClock();

	private CodefStubServer stub;

	private CodefClient client;

	@BeforeEach
	void start() throws IOException {
		stub = new CodefStubServer();
		stub.respond(CodefStubServer.TOKEN_PATH, 200, TOKEN_JSON);
		stub.respond(CARDS, 200, SUCCESS);
		client = clientWith(Duration.ofSeconds(5));
	}

	@AfterEach
	void stop() {
		stub.close();
	}

	@Test
	void tokenRequestUsesFormBodyAndBasicAuthWithoutQueryString() {
		client.startObservation().heldCards(PARAMETERS);

		CodefStubServer.Received request = stub.requestsTo(CodefStubServer.TOKEN_PATH).getFirst();
		assertThat(request.method()).isEqualTo("POST");
		assertThat(request.query()).isNull();
		assertThat(request.body()).isEqualTo("grant_type=client_credentials&scope=read");
		assertThat(request.header("Content-Type")).isEqualTo("application/x-www-form-urlencoded");
		String expected = Base64.getEncoder().encodeToString((TEST_ID + ":" + TEST_VALUE).getBytes(StandardCharsets.UTF_8));
		assertThat(request.header("Authorization")).isEqualTo("Basic " + expected);
	}

	@Test
	void rawJsonTokenKeepsPlusSign() {
		client.startObservation().heldCards(PARAMETERS);

		assertThat(stub.requestsTo(CARDS).getFirst().header("Authorization")).isEqualTo("Bearer " + TOKEN);
	}

	@Test
	void percentEncodedTokenResponseIsDecoded() {
		stub.respond(CodefStubServer.TOKEN_PATH, 200, URLEncoder.encode(TOKEN_JSON, StandardCharsets.UTF_8));

		client.startObservation().heldCards(PARAMETERS);

		assertThat(stub.requestsTo(CARDS).getFirst().header("Authorization")).isEqualTo("Bearer " + TOKEN);
	}

	@Test
	void unreadableTokenResponsesFailWithoutProductCall() {
		for (String body : List.of("not json", "%", "{}", "{\"access_token\":\"\"}", "{\"access_token\":1}")) {
			stub.respond(CodefStubServer.TOKEN_PATH, 200, body);

			assertFailed(client.startObservation().heldCards(PARAMETERS), Stage.TOKEN, Reason.TOKEN_UNREADABLE);
		}
		assertThat(stub.requestsTo(CARDS)).isEmpty();
	}

	@Test
	void rejectedTokenRequestReportsStatusWithoutProductCall() {
		stub.respond(CodefStubServer.TOKEN_PATH, 401, "body-message-marker");

		var result = (CodefCallResult.Failed<CodefHeldCard>) client.startObservation().heldCards(PARAMETERS);

		assertThat(result.reason()).isEqualTo(Reason.TOKEN_REJECTED);
		assertThat(result.httpStatus()).hasValue(401);
		assertThat(stub.requestsTo(CARDS)).isEmpty();
	}

	@Test
	void cachesTokenAcrossObservationsUntilSevenDaysPass() {
		client.startObservation().heldCards(PARAMETERS);
		clock.advance(Duration.ofDays(7).minusSeconds(1));
		client.startObservation().heldCards(PARAMETERS);
		assertThat(stub.requestsTo(CodefStubServer.TOKEN_PATH)).hasSize(1);

		clock.advance(Duration.ofSeconds(1));
		client.startObservation().heldCards(PARAMETERS);
		assertThat(stub.requestsTo(CodefStubServer.TOKEN_PATH)).hasSize(2);
	}

	@Test
	void concurrentCallsIssueOneToken() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(8);
		try {
			List<Callable<CodefCallResult<CodefHeldCard>>> calls = new ArrayList<>();
			for (int index = 0; index < 8; index++) {
				calls.add(() -> client.startObservation().heldCards(PARAMETERS));
			}
			for (Future<CodefCallResult<CodefHeldCard>> call : executor.invokeAll(calls)) {
				assertThat(call.get()).isInstanceOf(CodefCallResult.Answered.class);
			}
		}
		finally {
			executor.shutdownNow();
		}
		assertThat(stub.requestsTo(CodefStubServer.TOKEN_PATH)).hasSize(1);
		assertThat(stub.requestsTo(CARDS)).hasSize(8);
	}

	@Test
	void productRequestsUseBearerJsonAndPathForEachApi() {
		for (CodefApi api : CodefApi.values()) {
			stub.respond(api.path(), 200, SUCCESS);
		}
		Map<String, String> parameters = Map.of("organization", "한글");
		CodefObservation observation = client.startObservation();

		observation.heldCards(parameters);
		observation.approvals(parameters);
		observation.cardPerformances(parameters);

		assertThat(CodefApi.values()).extracting(CodefApi::path)
			.containsExactly("/v1/kr/card/p/account/card-list", "/v1/kr/card/p/account/approval-list",
					"/v1/kr/card/p/account/result-check-list");
		for (CodefApi api : CodefApi.values()) {
			CodefStubServer.Received request = stub.requestsTo(api.path()).getFirst();
			assertThat(request.method()).isEqualTo("POST");
			assertThat(request.header("Authorization")).isEqualTo("Bearer " + TOKEN);
			assertThat(request.header("Content-Type")).isEqualTo("application/json");
			assertThat(request.body()).isEqualTo("{\"organization\":\"한글\"}");
		}
	}

	@Test
	void responseGoesThroughExistingReader() {
		stub.respond(CARDS, 200, URLEncoder.encode(SUCCESS, StandardCharsets.UTF_8));

		var answered = (CodefCallResult.Answered<CodefHeldCard>) client.startObservation().heldCards(PARAMETERS);

		var interpreted = (CodefProductResponse.Interpreted<CodefHeldCard>) answered.response();
		assertThat(interpreted.response().result().category()).isEqualTo(CodefResultCategory.SUCCESS);
	}

	@Test
	void httpUnauthorizedDiscardsTokenWithoutResending() {
		stub.respond(CARDS, 401, "");

		var result = (CodefCallResult.Answered<CodefHeldCard>) client.startObservation().heldCards(PARAMETERS);

		assertThat(result.response()).isInstanceOf(CodefProductResponse.Unauthorized.class);
		assertThat(stub.requestsTo(CARDS)).hasSize(1);
		assertThat(stub.requestsTo(CodefStubServer.TOKEN_PATH)).hasSize(1);

		stub.respond(CARDS, 200, SUCCESS);
		client.startObservation().heldCards(PARAMETERS);
		assertThat(stub.requestsTo(CodefStubServer.TOKEN_PATH)).hasSize(2);
	}

	@Test
	void resultCodeTokenExpiryDiscardsTokenWithoutResending() {
		stub.respond(CARDS, 200, "{\"result\":{\"code\":\"CF-00401\"},\"data\":[]}");

		client.startObservation().heldCards(PARAMETERS);

		assertThat(stub.requestsTo(CARDS)).hasSize(1);
		client.startObservation().heldCards(PARAMETERS);
		assertThat(stub.requestsTo(CodefStubServer.TOKEN_PATH)).hasSize(2);
	}

	@Test
	void serverErrorIsNotRetriedAndKeepsToken() {
		stub.respond(CARDS, 500, "");

		var result = (CodefCallResult.Answered<CodefHeldCard>) client.startObservation().heldCards(PARAMETERS);

		assertThat(result.response()).isEqualTo(new CodefProductResponse.HttpFailure<CodefHeldCard>(500));
		assertThat(stub.requestsTo(CARDS)).hasSize(1);
		client.startObservation().heldCards(PARAMETERS);
		assertThat(stub.requestsTo(CodefStubServer.TOKEN_PATH)).hasSize(1);
	}

	@Test
	void droppedConnectionIsNotRetried() {
		stub.dropConnection(CARDS);

		assertFailed(client.startObservation().heldCards(PARAMETERS), Stage.PRODUCT, Reason.CONNECTION_FAILED);

		assertThat(stub.requestsTo(CARDS)).hasSize(1);
	}

	@Test
	void redirectIsNotFollowed() {
		stub.respondWith(CARDS, exchange -> {
			exchange.getResponseHeaders().add("Location", "/elsewhere");
			CodefStubServer.reply(exchange, 302, "");
		});

		var result = (CodefCallResult.Answered<CodefHeldCard>) client.startObservation().heldCards(PARAMETERS);

		assertThat(result.response()).isEqualTo(new CodefProductResponse.HttpFailure<CodefHeldCard>(302));
		assertThat(stub.requestsTo("/elsewhere")).isEmpty();
	}

	@Test
	void productTimeoutFailsOnceWithoutRetry() {
		stub.neverRespond(CARDS);
		CodefClient fast = clientWith(Duration.ofMillis(300));

		assertFailed(fast.startObservation().heldCards(PARAMETERS), Stage.PRODUCT, Reason.TIMEOUT);

		assertThat(stub.requestsTo(CARDS)).hasSize(1);
	}

	@Test
	void timeoutCoversStalledBody() {
		stub.stallBody(CARDS);
		CodefClient fast = clientWith(Duration.ofMillis(300));

		assertFailed(fast.startObservation().heldCards(PARAMETERS), Stage.PRODUCT, Reason.TIMEOUT);
	}

	@Test
	void tokenTimeoutFailsWithoutProductCall() {
		stub.neverRespond(CodefStubServer.TOKEN_PATH);
		CodefClient fast = clientWith(Duration.ofMillis(300));

		assertFailed(fast.startObservation().heldCards(PARAMETERS), Stage.TOKEN, Reason.TIMEOUT);

		assertThat(stub.requestsTo(CARDS)).isEmpty();
	}

	@Test
	void threadInterruptedWhileWaitingForTokenSendsNoProductRequestAndKeepsCallLimit() throws Exception {
		CountDownLatch releaseToken = new CountDownLatch(1);
		stub.respondWith(CodefStubServer.TOKEN_PATH, exchange -> {
			awaitQuietly(releaseToken);
			CodefStubServer.reply(exchange, 200, TOKEN_JSON);
		});
		Thread issuer = new Thread(() -> client.startObservation().heldCards(PARAMETERS));
		issuer.start();
		awaitUntil(() -> stub.requestsTo(CodefStubServer.TOKEN_PATH).size() == 1);

		CodefObservation waiting = client.startObservation();
		AtomicReference<CodefCallResult<CodefHeldCard>> result = new AtomicReference<>();
		AtomicBoolean interruptKept = new AtomicBoolean();
		AtomicReference<List<CodefCallResult<CodefHeldCard>>> later = new AtomicReference<>();
		Thread waiter = new Thread(() -> {
			result.set(waiting.heldCards(PARAMETERS));
			interruptKept.set(Thread.interrupted());
			// 거부된 호출이 상한을 쓰지 않았으면 3회를 그대로 보낼 수 있다.
			later.set(List.of(waiting.heldCards(PARAMETERS), waiting.heldCards(PARAMETERS), waiting.heldCards(PARAMETERS)));
		});
		waiter.start();
		awaitUntil(() -> waiter.getState() == Thread.State.WAITING);
		waiter.interrupt();
		releaseToken.countDown();
		issuer.join(5_000);
		awaitUntil(() -> result.get() != null);

		assertFailed(result.get(), Stage.PRODUCT, Reason.INTERRUPTED);
		assertThat(interruptKept).isTrue();
		assertThat(stub.requestsTo(CodefStubServer.TOKEN_PATH)).hasSize(1);
		waiter.join(5_000);
		assertThat(later.get()).allSatisfy(call -> assertThat(call).isInstanceOf(CodefCallResult.Answered.class));
		assertThat(stub.requestsTo(CARDS)).hasSize(4);
	}

	@Test
	void interruptDuringOwnTokenRequestSendsNoProductRequest() throws Exception {
		stub.neverRespond(CodefStubServer.TOKEN_PATH);
		AtomicReference<CodefCallResult<CodefHeldCard>> result = new AtomicReference<>();
		AtomicBoolean interruptKept = new AtomicBoolean();
		Thread caller = new Thread(() -> {
			result.set(client.startObservation().heldCards(PARAMETERS));
			interruptKept.set(Thread.currentThread().isInterrupted());
		});
		caller.start();
		awaitUntil(() -> stub.requestsTo(CodefStubServer.TOKEN_PATH).size() == 1);

		caller.interrupt();
		caller.join(5_000);

		assertFailed(result.get(), Stage.TOKEN, Reason.INTERRUPTED);
		assertThat(interruptKept).isTrue();
		assertThat(stub.requestsTo(CARDS)).isEmpty();
	}

	@Test
	void fourthCallPerApiIsRejectedBeforeSending() {
		stub.respond(CodefApi.APPROVALS.path(), 200, SUCCESS);
		CodefObservation observation = client.startObservation();
		for (int index = 0; index < CodefObservation.CALL_LIMIT; index++) {
			assertThat(observation.heldCards(PARAMETERS)).isInstanceOf(CodefCallResult.Answered.class);
		}

		assertFailed(observation.heldCards(PARAMETERS), Stage.PRODUCT, Reason.CALL_LIMIT_REACHED);

		assertThat(CodefObservation.CALL_LIMIT).isEqualTo(3);
		assertThat(stub.requestsTo(CARDS)).hasSize(3);
		assertThat(observation.approvals(PARAMETERS)).isInstanceOf(CodefCallResult.Answered.class);
		assertThat(client.startObservation().heldCards(PARAMETERS)).isInstanceOf(CodefCallResult.Answered.class);
	}

	@Test
	void tokenRequestsAreLimitedPerObservation() {
		stub.respond(CodefStubServer.TOKEN_PATH, 500, "");
		stub.respond(CodefApi.APPROVALS.path(), 200, SUCCESS);
		CodefObservation observation = client.startObservation();
		for (int index = 0; index < 3; index++) {
			assertFailed(observation.heldCards(PARAMETERS), Stage.TOKEN, Reason.TOKEN_REJECTED);
		}

		assertFailed(observation.approvals(PARAMETERS), Stage.TOKEN, Reason.CALL_LIMIT_REACHED);

		assertThat(stub.requestsTo(CodefStubServer.TOKEN_PATH)).hasSize(3);
	}

	@Test
	void missingKeysFailWithoutAnyRequest() {
		CodefClient noKeys = new CodefClient(CodefApiKeys.fromEnvironment(Map.of()), stub.settings(Duration.ofSeconds(1)));
		CodefObservation observation = noKeys.startObservation();

		for (int index = 0; index < 5; index++) {
			assertFailed(observation.heldCards(PARAMETERS), Stage.TOKEN, Reason.CREDENTIALS_MISSING);
		}

		assertThat(stub.requests()).isEmpty();
	}

	@Test
	void rejectsLoginInputWithoutSending() {
		for (String key : List.of("id", "password", "cardNo", "cardPassword")) {
			assertThatThrownBy(() -> client.startObservation().heldCards(Map.of(key, "login-value-marker")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageNotContaining("login-value-marker");
		}
		assertThat(stub.requests()).isEmpty();
	}

	@Test
	void httpClientDoesNotFollowRedirectsAndRequiresModernTls() {
		HttpClient http = client.http().client();

		assertThat(http.followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
		assertThat(http.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
		assertThat(http.connectTimeout()).contains(Duration.ofSeconds(2));
		assertThat(http.sslParameters().getProtocols()).containsExactlyInAnyOrder("TLSv1.3", "TLSv1.2");
	}

	private static void awaitUntil(BooleanSupplier condition) throws InterruptedException {
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (!condition.getAsBoolean()) {
			assertThat(System.nanoTime()).as("condition reached in time").isLessThan(deadline);
			Thread.sleep(5);
		}
	}

	private static void awaitQuietly(CountDownLatch latch) {
		try {
			latch.await();
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}

	private CodefClient clientWith(Duration responseTimeout) {
		CodefApiKeys keys = CodefApiKeys.fromEnvironment(
				Map.of(CodefApiKeys.CLIENT_ID_VARIABLE, TEST_ID, CodefApiKeys.CLIENT_SECRET_VARIABLE, TEST_VALUE));
		return new CodefClient(keys, stub.settings(responseTimeout), clock);
	}

	private static <T> void assertFailed(CodefCallResult<T> result, Stage stage, Reason reason) {
		assertThat(result).isInstanceOfSatisfying(CodefCallResult.Failed.class, failed -> {
			assertThat(failed.stage()).isEqualTo(stage);
			assertThat(failed.reason()).isEqualTo(reason);
		});
	}

	private static final class MutableClock extends Clock {

		private Instant now = Instant.parse("2026-11-03T00:00:00Z");

		synchronized void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public synchronized Instant instant() {
			return now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

	}

}
