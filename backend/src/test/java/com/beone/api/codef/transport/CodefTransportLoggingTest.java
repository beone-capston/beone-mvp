package com.beone.api.codef.transport;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * 모든 로거를 DEBUG로 낮춘 상태에서도 비밀값과 본문이 로그에 없는지 확인한다.
 * JDK HTTP 클라이언트의 내부 로그는 시스템 속성을 켜야 나오므로 그 속성이 꺼져 있는지도 본다.
 */
class CodefTransportLoggingTest {

	private static final String ID_MARKER = "synthetic-id-marker";

	private static final String VALUE_MARKER = "synthetic-value-marker";

	private static final String TOKEN_MARKER = "synthetic-token-marker";

	private static final String CONNECTED_MARKER = "synthetic-connected-marker";

	private static final String BODY_MARKER = "synthetic-body-marker";

	private static final String LOGIN_MARKER = "synthetic-login-marker";

	private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

	private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

	private Level previous;

	private CodefStubServer stub;

	private CodefClient client;

	@BeforeEach
	void start() throws IOException {
		previous = root.getLevel();
		root.setLevel(Level.DEBUG);
		appender.start();
		root.addAppender(appender);
		stub = new CodefStubServer();
		stub.respond(CodefStubServer.TOKEN_PATH, 200, "{\"access_token\":\"" + TOKEN_MARKER + "\"}");
		client = new CodefClient(CodefApiKeys.fromEnvironment(
				Map.of(CodefApiKeys.CLIENT_ID_VARIABLE, ID_MARKER, CodefApiKeys.CLIENT_SECRET_VARIABLE, VALUE_MARKER)),
				stub.settings(Duration.ofMillis(500)));
	}

	@AfterEach
	void stop() {
		stub.close();
		root.detachAppender(appender);
		root.setLevel(previous);
	}

	@Test
	void logsOnlyApiNameStatusAndResultCode() {
		stub.respond(CodefApi.HELD_CARDS.path(), 200, "{\"result\":{\"code\":\"CF-00000\",\"message\":\"" + BODY_MARKER + "\"},\"data\":[]}");

		client.startObservation().heldCards(Map.of("connectedId", CONNECTED_MARKER));

		assertThat(codefMessages()).containsExactly("codef call api=TOKEN http=200",
				"codef call api=HELD_CARDS http=200 resultCode=CF-00000");
		assertNoSecretsLogged();
	}

	@Test
	void unexpectedResultCodeIsNotCopiedToLog() {
		stub.respond(CodefApi.HELD_CARDS.path(), 200, "{\"result\":{\"code\":\"" + BODY_MARKER + "\"},\"data\":[]}");

		client.startObservation().heldCards(Map.of());

		assertThat(codefMessages()).contains("codef call api=HELD_CARDS http=200 resultCode=UNRECOGNIZED");
		assertNoSecretsLogged();
	}

	@Test
	void failuresLogReasonOnly() {
		stub.respond(CodefApi.HELD_CARDS.path(), 500, BODY_MARKER);
		stub.neverRespond(CodefApi.APPROVALS.path());
		stub.dropConnection(CodefApi.CARD_PERFORMANCES.path());
		CodefObservation observation = client.startObservation();

		observation.heldCards(Map.of("connectedId", CONNECTED_MARKER));
		observation.approvals(Map.of("connectedId", CONNECTED_MARKER));
		observation.cardPerformances(Map.of("connectedId", CONNECTED_MARKER));

		assertThat(codefMessages()).contains("codef call api=HELD_CARDS http=500 resultCode=NONE",
				"codef call failed api=APPROVALS stage=PRODUCT reason=TIMEOUT",
				"codef call failed api=CARD_PERFORMANCES stage=PRODUCT reason=CONNECTION_FAILED");
		assertNoSecretsLogged();
	}

	@Test
	void accountFailureLogsOnlyCodesCountAndExtraMessagePresence() {
		StringBuilder errors = new StringBuilder("{\"code\":\"CF-12834\",\"message\":\"" + BODY_MARKER + "\",\"extraMessage\":\""
				+ BODY_MARKER + "\",\"id\":\"" + LOGIN_MARKER + "\"},{\"code\":\"" + BODY_MARKER + "\\nresultCode=CF-00000\"}");
		for (int i = 0; i < 5; i++) {
			errors.append(",{\"code\":\"CF-1240").append(i).append("\"}");
		}
		stub.respond(CodefAccountApi.ACCOUNT_CREATE.path(), 200, "{\"result\":{\"code\":\"CF-04000\",\"message\":\"" + BODY_MARKER
				+ "\",\"extraMessage\":\"" + BODY_MARKER + "\"},\"data\":{\"connectedId\":\"" + CONNECTED_MARKER
				+ "\",\"errorList\":[" + errors + "]}}");

		client.startObservation().createAccount("0301", LOGIN_MARKER, VALUE_MARKER);

		assertThat(codefMessages()).containsExactly("codef call api=TOKEN http=200",
				"codef call api=ACCOUNT_CREATE http=200 resultCode=CF-04000",
				"codef call detail api=ACCOUNT_CREATE extraMessage=true errorCodes=CF-12834,OTHER,CF-12400,CF-12401,CF-12402 errorCount=7");
		assertNoSecretsLogged();
		assertThat(appender.list).allSatisfy(event -> assertThat(event.getFormattedMessage()).doesNotContain(LOGIN_MARKER));
	}

	@Test
	void accountFailureWithoutErrorListLogsEmptyCodes() {
		stub.respond(CodefAccountApi.ACCOUNT_CREATE.path(), 200, "{\"result\":{\"code\":\"CF-04000\"},\"data\":{}}");

		client.startObservation().createAccount("0301", LOGIN_MARKER, VALUE_MARKER);

		assertThat(codefMessages()).contains("codef call detail api=ACCOUNT_CREATE extraMessage=false errorCodes= errorCount=0");
		assertNoSecretsLogged();
	}

	@Test
	void productFailureLogsExtraMessagePresenceOnly() {
		stub.respond(CodefApi.HELD_CARDS.path(), 200,
				"{\"result\":{\"code\":\"CF-12801\",\"extraMessage\":\"" + BODY_MARKER + "\"},\"data\":[]}");

		client.startObservation().heldCards(Map.of("connectedId", CONNECTED_MARKER));

		assertThat(codefMessages()).containsExactly("codef call api=TOKEN http=200",
				"codef call api=HELD_CARDS http=200 resultCode=CF-12801", "codef call detail api=HELD_CARDS extraMessage=true");
		assertNoSecretsLogged();
	}

	@Test
	void jdkHttpClientDebugLoggingIsNotEnabled() {
		assertThat(System.getProperty("jdk.httpclient.HttpClient.log")).isNull();
		assertThat(System.getProperty("jdk.internal.httpclient.debug")).isNull();
	}

	@Test
	void stringFormsHideValues() {
		CodefApiKeys keys = CodefApiKeys.fromEnvironment(
				Map.of(CodefApiKeys.CLIENT_ID_VARIABLE, ID_MARKER, CodefApiKeys.CLIENT_SECRET_VARIABLE, VALUE_MARKER));

		assertThat(keys.toString()).doesNotContain(ID_MARKER, VALUE_MARKER);
		assertThat(new CodefTokenCache.Outcome.Token(TOKEN_MARKER).toString()).doesNotContain(TOKEN_MARKER);
		assertThat(new CodefHttp.Reply(200, BODY_MARKER).toString()).doesNotContain(BODY_MARKER);
		assertThat(stub.settings(Duration.ofSeconds(1)).toString()).doesNotContain(ID_MARKER, VALUE_MARKER, TOKEN_MARKER);
	}

	private List<String> codefMessages() {
		return appender.list.stream()
			.filter(event -> event.getLoggerName().startsWith("com.beone.api.codef"))
			.map(ILoggingEvent::getFormattedMessage)
			.toList();
	}

	private void assertNoSecretsLogged() {
		assertThat(appender.list).isNotEmpty();
		for (ILoggingEvent event : appender.list) {
			assertThat(event.getFormattedMessage()).doesNotContain(ID_MARKER, VALUE_MARKER, TOKEN_MARKER, CONNECTED_MARKER, BODY_MARKER);
			assertThat(event.getThrowableProxy()).isNull();
		}
	}

}
