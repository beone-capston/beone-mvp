package com.beone.api.codef.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;

import com.beone.api.codef.CodefHeldCard;
import com.beone.api.codef.CodefProductResponse;
import com.beone.api.codef.CodefResultCategory;
import com.beone.api.codef.transport.CodefApi;
import com.beone.api.codef.transport.CodefApiKeys;
import com.beone.api.codef.transport.CodefCallResult;
import com.beone.api.codef.transport.CodefClient;
import com.beone.api.codef.transport.CodefStubServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실 게이트웨이(LP-1)를 루프백 스텁 HTTP로만 확인한다. 실제 CODEF 호스트로는 나가지 않는다(스텁 주소만 설정, 키·공개키는 합성 값).
 *
 * <p>고정하는 것: 비밀번호는 공개키 암호문으로만 나간다, 결과 코드 분류 대응, 실패해도 다시 부르지 않음(시간 초과·5xx·연결 끊김),
 * 리다이렉트를 따르지 않음, 키·공개키가 없으면 요청 0건, 동기화 요청 본문.
 */
class LiveCodefGatewayTest {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private static final String CREATE = "/v1/account/create";

	private static final String DELETE = "/v1/account/delete";

	private static final String CARDS = CodefApi.HELD_CARDS.path();

	private static final String APPROVALS = CodefApi.APPROVALS.path();

	private static final String PERFORMANCES = CodefApi.CARD_PERFORMANCES.path();

	private static final String TOKEN_JSON = "{\"access_token\":\"synthetic-token\",\"token_type\":\"bearer\"}";

	private static final String PASSWORD = "synthetic-pass-1!";

	private static final String CONNECTED_ID = "synthetic-connected-id";

	private static final String EMPTY_SUCCESS = "{\"result\":{\"code\":\"CF-00000\"},\"data\":[]}";

	private static KeyPair keys;

	private CodefStubServer stub;

	@BeforeAll
	static void generateKeys() throws GeneralSecurityException {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		keys = generator.generateKeyPair();
	}

	@BeforeEach
	void start() throws IOException {
		this.stub = new CodefStubServer();
		this.stub.respond(CodefStubServer.TOKEN_PATH, 200, TOKEN_JSON);
	}

	@AfterEach
	void stop() {
		this.stub.close();
	}

	@Test
	void registerSendsOnlyEncryptedPasswordAndReturnsConnectedId() throws Exception {
		this.stub.respond(CREATE, 200, account("CF-00000", CONNECTED_ID));

		CodefRegistration registration = gateway().register("0301", "synthetic-login", PASSWORD.toCharArray());

		assertThat(registration).isEqualTo(new CodefRegistration.Registered(CONNECTED_ID));
		CodefStubServer.Received request = this.stub.requestsTo(CREATE).getFirst();
		assertThat(request.header("Authorization")).isEqualTo("Bearer synthetic-token");
		assertThat(request.body()).doesNotContain(PASSWORD);
		JsonNode account = MAPPER.readTree(request.body()).get("accountList").get(0);
		assertThat(account.get("organization").stringValue()).isEqualTo("0301");
		assertThat(account.get("id").stringValue()).isEqualTo("synthetic-login");
		assertThat(account.get("loginType").stringValue()).isEqualTo("1");
		assertThat(decrypt(account.get("password").stringValue())).isEqualTo(PASSWORD);
		assertThat(this.stub.requestsTo(CodefStubServer.TOKEN_PATH)).hasSize(1);
	}

	@Test
	void registrationResultCodesMapThroughTheExistingTable() {
		this.stub.respond(CREATE, 200, account("CF-12108", null));
		assertThat(gateway().register("0301", "a", PASSWORD.toCharArray()))
			.isInstanceOf(CodefRegistration.CardVerificationRequired.class);

		// 근거 없는 코드는 로그인 실패로 묶지 않는다(CodefRegistrations)
		this.stub.respond(CREATE, 200, account("CF-12401", null));
		assertThat(gateway().register("0301", "a", PASSWORD.toCharArray()))
			.isInstanceOf(CodefRegistration.Unavailable.class);

		// 성공 코드여도 connectedId가 없으면 쓸 수 없다
		this.stub.respond(CREATE, 200, "{\"result\":{\"code\":\"CF-00000\"},\"data\":{}}");
		assertThat(gateway().register("0301", "a", PASSWORD.toCharArray()))
			.isInstanceOf(CodefRegistration.Unavailable.class);
	}

	@Test
	void errorListCodesDoNotChangeTheRegistrationResult() {
		// 관리자 판단 P1(#47) 유지: errorList 코드는 관측 로그에만 쓰고 로그인 실패(400)로 바꾸지 않는다
		for (String errorList : List.of("[{\"code\":\"CF-12401\"}]", "[{\"code\":\"CF-12834\"}]",
				"[{\"code\":\"CF-12999\"}]", "[{\"code\":\"OTHER-VALUE\"}]", "[]")) {
			this.stub.respond(CREATE, 200, "{\"result\":{\"code\":\"CF-04000\"},\"data\":{\"errorList\":" + errorList + "}}");
			assertThat(gateway().register("0301", "a", PASSWORD.toCharArray())).as(errorList)
				.isInstanceOf(CodefRegistration.Unavailable.class);
		}
		this.stub.respond(CREATE, 200, "{\"result\":{\"code\":\"CF-04000\"},\"data\":{}}");
		assertThat(gateway().register("0301", "a", PASSWORD.toCharArray()))
			.isInstanceOf(CodefRegistration.Unavailable.class);
	}

	@Test
	void serverErrorOnRegisterIsNotRetried() {
		this.stub.respond(CREATE, 503, "");

		assertThat(gateway().register("0301", "a", PASSWORD.toCharArray()))
			.isInstanceOf(CodefRegistration.Unavailable.class);
		assertThat(this.stub.requestsTo(CREATE)).hasSize(1);
	}

	@Test
	void timeoutOnRegisterIsNotRetried() {
		this.stub.neverRespond(CREATE);

		assertThat(gateway(Duration.ofMillis(300)).register("0301", "a", PASSWORD.toCharArray()))
			.isInstanceOf(CodefRegistration.Unavailable.class);
		assertThat(this.stub.requestsTo(CREATE)).hasSize(1);
	}

	@Test
	void missingPublicKeyOrApiKeysSendNothing() {
		LiveCodefGateway noPublicKey = new LiveCodefGateway(client(Duration.ofSeconds(5), true),
				CodefPasswordEncryptor.fromEnvironment(Map.of()));
		assertThat(noPublicKey.register("0301", "a", PASSWORD.toCharArray()))
			.isInstanceOf(CodefRegistration.Unavailable.class);

		LiveCodefGateway noApiKeys = new LiveCodefGateway(client(Duration.ofSeconds(5), false),
				CodefPasswordEncryptor.of(keys.getPublic()));
		assertThat(noApiKeys.register("0301", "a", PASSWORD.toCharArray()))
			.isInstanceOf(CodefRegistration.Unavailable.class);
		assertThat(noApiKeys.heldCards("0301", CONNECTED_ID)).isInstanceOf(CodefCallResult.Failed.class);

		assertThat(this.stub.requests()).isEmpty();
	}

	@Test
	void unreadablePublicKeyIsNotConfigured() {
		assertThat(CodefPasswordEncryptor.fromEnvironment(Map.of(CodefPasswordEncryptor.VARIABLE, "not-a-key"))
			.isConfigured()).isFalse();
		String encoded = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
		assertThat(CodefPasswordEncryptor.fromEnvironment(Map.of(CodefPasswordEncryptor.VARIABLE, encoded))
			.isConfigured()).isTrue();
		assertThat(CodefPasswordEncryptor.of(keys.getPublic()).toString()).doesNotContain(encoded);
	}

	@Test
	void syncRequestsCarryConnectedIdAndPeriodWithoutLoginInput() throws Exception {
		this.stub.respond(CARDS, 200, EMPTY_SUCCESS);
		this.stub.respond(APPROVALS, 200, EMPTY_SUCCESS);
		this.stub.respond(PERFORMANCES, 200, EMPTY_SUCCESS);
		LiveCodefGateway gateway = gateway();

		gateway.heldCards("0301", CONNECTED_ID);
		gateway.approvals("0301", CONNECTED_ID, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
		gateway.performances("0301", CONNECTED_ID);

		assertThat(MAPPER.readTree(this.stub.requestsTo(CARDS).getFirst().body()))
			.isEqualTo(MAPPER.readTree("{\"organization\":\"0301\",\"connectedId\":\"" + CONNECTED_ID + "\"}"));
		assertThat(MAPPER.readTree(this.stub.requestsTo(APPROVALS).getFirst().body()))
			.isEqualTo(MAPPER.readTree("{\"organization\":\"0301\",\"connectedId\":\"" + CONNECTED_ID
					+ "\",\"startDate\":\"20261001\",\"endDate\":\"20261031\",\"orderBy\":\"0\"}"));
		assertThat(MAPPER.readTree(this.stub.requestsTo(PERFORMANCES).getFirst().body()))
			.isEqualTo(MAPPER.readTree("{\"organization\":\"0301\",\"connectedId\":\"" + CONNECTED_ID + "\"}"));
		// 토큰은 한 번 발급해 세 호출이 같이 쓴다
		assertThat(this.stub.requestsTo(CodefStubServer.TOKEN_PATH)).hasSize(1);
	}

	@Test
	void codefErrorCodeIsReturnedAsAnsweredForSyncToJudge() {
		this.stub.respond(CARDS, 200, "{\"result\":{\"code\":\"CF-12801\"},\"data\":[]}");

		var result = (CodefCallResult.Answered<CodefHeldCard>) gateway().heldCards("0301", CONNECTED_ID);

		var interpreted = (CodefProductResponse.Interpreted<CodefHeldCard>) result.response();
		assertThat(interpreted.response().result().code()).hasValue("CF-12801");
		assertThat(interpreted.response().result().category()).isNotEqualTo(CodefResultCategory.SUCCESS);
		assertThat(this.stub.requestsTo(CARDS)).hasSize(1);
	}

	@Test
	void serverErrorAndTimeoutOnSyncAreNotRetried() {
		this.stub.respond(CARDS, 500, "");
		this.stub.neverRespond(PERFORMANCES);
		LiveCodefGateway gateway = gateway(Duration.ofMillis(300));

		assertThat(((CodefCallResult.Answered<CodefHeldCard>) gateway.heldCards("0301", CONNECTED_ID)).response())
			.isEqualTo(new CodefProductResponse.HttpFailure<>(500));
		var timedOut = (CodefCallResult.Failed<?>) gateway.performances("0301", CONNECTED_ID);

		assertThat(timedOut.reason()).isEqualTo(CodefCallResult.Reason.TIMEOUT);
		assertThat(this.stub.requestsTo(CARDS)).hasSize(1);
		assertThat(this.stub.requestsTo(PERFORMANCES)).hasSize(1);
	}

	@Test
	void droppedConnectionIsNotRetried() {
		this.stub.dropConnection(APPROVALS);

		var result = (CodefCallResult.Failed<?>) gateway().approvals("0301", CONNECTED_ID, LocalDate.of(2026, 10, 1),
				LocalDate.of(2026, 10, 31));

		assertThat(result.reason()).isEqualTo(CodefCallResult.Reason.CONNECTION_FAILED);
		assertThat(this.stub.requestsTo(APPROVALS)).hasSize(1);
	}

	@Test
	void redirectIsNotFollowed() {
		this.stub.respondWith(CARDS, exchange -> {
			exchange.getResponseHeaders().add("Location", "/redirected");
			CodefStubServer.reply(exchange, 302, "");
		});
		this.stub.respond("/redirected", 200, EMPTY_SUCCESS);

		var result = (CodefCallResult.Answered<CodefHeldCard>) gateway().heldCards("0301", CONNECTED_ID);

		assertThat(result.response()).isEqualTo(new CodefProductResponse.HttpFailure<>(302));
		assertThat(this.stub.requestsTo("/redirected")).isEmpty();
		assertThat(this.stub.requestsTo(CARDS)).hasSize(1);
	}

	@Test
	void releaseDeletesTheAccountWithoutLoginInput() throws Exception {
		this.stub.respond(DELETE, 200, account("CF-00000", CONNECTED_ID));

		gateway().release(CONNECTED_ID);

		JsonNode body = MAPPER.readTree(this.stub.requestsTo(DELETE).getFirst().body());
		assertThat(body.get("connectedId").stringValue()).isEqualTo(CONNECTED_ID);
		JsonNode account = body.get("accountList").get(0);
		assertThat(account.get("organization").stringValue()).isEqualTo("0301");
		assertThat(account.has("id")).isFalse();
		assertThat(account.has("password")).isFalse();
	}

	@Test
	void failedReleaseIsNotRetriedAndDoesNotThrow() {
		this.stub.respond(DELETE, 500, "");

		gateway().release(CONNECTED_ID);

		assertThat(this.stub.requestsTo(DELETE)).hasSize(1);
	}

	@Test
	void stubSettingsPointOnlyAtLoopback() {
		assertThat(this.stub.settings(Duration.ofSeconds(1)).productHost().getHost()).isEqualTo("127.0.0.1");
		assertThat(this.stub.settings(Duration.ofSeconds(1)).tokenUri().getHost()).isEqualTo("127.0.0.1");
	}

	private LiveCodefGateway gateway() {
		return gateway(Duration.ofSeconds(5));
	}

	private LiveCodefGateway gateway(Duration responseTimeout) {
		return new LiveCodefGateway(client(responseTimeout, true), CodefPasswordEncryptor.of(keys.getPublic()));
	}

	private CodefClient client(Duration responseTimeout, boolean withKeys) {
		Map<String, String> environment = withKeys
				? Map.of("CODEF_CLIENT_ID", "synthetic-id", "CODEF_CLIENT_SECRET", "synthetic-value") : Map.of();
		return new CodefClient(CodefApiKeys.fromEnvironment(environment), this.stub.settings(responseTimeout));
	}

	private static String account(String code, String connectedId) {
		String data = (connectedId == null) ? "{}" : "{\"connectedId\":\"" + connectedId + "\"}";
		return "{\"result\":{\"code\":\"" + code + "\"},\"data\":" + data + "}";
	}

	private static String decrypt(String encrypted) throws GeneralSecurityException {
		Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
		cipher.init(Cipher.DECRYPT_MODE, keys.getPrivate());
		return new String(cipher.doFinal(Base64.getDecoder().decode(encrypted)), StandardCharsets.UTF_8);
	}


}
