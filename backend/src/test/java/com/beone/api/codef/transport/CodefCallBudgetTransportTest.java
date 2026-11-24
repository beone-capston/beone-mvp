package com.beone.api.codef.transport;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 전송 계층이 호출 총량(LP-2)을 보내기 직전에 묻고, 거절이면 루프백 스텁에 아무 요청도 보내지 않음을 확인한다.
 */
class CodefCallBudgetTransportTest {

	private static final String CARDS = CodefApi.HELD_CARDS.path();

	private static final Map<String, String> PARAMETERS = Map.of("organization", "0301");

	private static final String SUCCESS = "{\"result\":{\"code\":\"CF-00000\"},\"data\":[]}";

	private final List<String> asked = new CopyOnWriteArrayList<>();

	private CodefStubServer stub;

	@BeforeEach
	void start() throws IOException {
		this.stub = new CodefStubServer();
		this.stub.respond(CodefStubServer.TOKEN_PATH, 200, "{\"access_token\":\"synthetic-token\"}");
		this.stub.respond(CARDS, 200, SUCCESS);
		this.stub.respond(CodefAccountApi.ACCOUNT_CREATE.path(), 200, SUCCESS);
	}

	@AfterEach
	void stop() {
		this.stub.close();
	}

	@Test
	void deniedTokenBudgetSendsNothing() {
		var result = (CodefCallResult.Failed<?>) client(denying(Set.of(CodefCallBudget.TOKEN))).startObservation()
			.heldCards(PARAMETERS);

		assertThat(result.stage()).isEqualTo(CodefCallResult.Stage.TOKEN);
		assertThat(result.reason()).isEqualTo(CodefCallResult.Reason.BUDGET_EXHAUSTED);
		assertThat(this.stub.requests()).isEmpty();
	}

	@Test
	void deniedProductBudgetSendsNoProductRequest() {
		var result = (CodefCallResult.Failed<?>) client(denying(Set.of("HELD_CARDS"))).startObservation()
			.heldCards(PARAMETERS);

		assertThat(result.stage()).isEqualTo(CodefCallResult.Stage.PRODUCT);
		assertThat(result.reason()).isEqualTo(CodefCallResult.Reason.BUDGET_EXHAUSTED);
		assertThat(this.stub.requestsTo(CARDS)).isEmpty();
	}

	@Test
	void deniedAccountBudgetSendsNoAccountRequest() {
		var result = (CodefCallResult.Failed<?>) client(denying(Set.of("ACCOUNT_CREATE"))).startObservation()
			.createAccount("0301", "synthetic-login", "synthetic-ciphertext");

		assertThat(result.reason()).isEqualTo(CodefCallResult.Reason.BUDGET_EXHAUSTED);
		assertThat(this.stub.requestsTo(CodefAccountApi.ACCOUNT_CREATE.path())).isEmpty();
	}

	@Test
	void budgetIsAskedOncePerSentRequestAndNotForCachedToken() {
		CodefClient client = client(denying(Set.of()));

		client.startObservation().heldCards(PARAMETERS);
		client.startObservation().heldCards(PARAMETERS);

		// 토큰은 처음 한 번만 발급하므로 두 번째에는 묻지 않는다
		assertThat(this.asked).containsExactly(CodefCallBudget.TOKEN, "HELD_CARDS", "HELD_CARDS");
		assertThat(this.stub.requestsTo(CARDS)).hasSize(2);
	}

	@Test
	void observationLimitIsCheckedBeforeTheBudget() {
		CodefObservation observation = client(denying(Set.of())).startObservation();
		for (int i = 0; i < CodefObservation.CALL_LIMIT + 1; i++) {
			observation.heldCards(PARAMETERS);
		}

		// 관측 상한(3)을 넘은 네 번째는 총량을 쓰지 않는다
		assertThat(this.asked.stream().filter("HELD_CARDS"::equals)).hasSize(CodefObservation.CALL_LIMIT);
	}

	@Test
	void defaultClientDoesNotCountAcrossProcesses() {
		assertThat(CodefCallBudget.UNLIMITED.tryAcquire("HELD_CARDS")).isTrue();
	}

	private CodefCallBudget denying(Set<String> denied) {
		return api -> {
			this.asked.add(api);
			return !denied.contains(api);
		};
	}

	private CodefClient client(CodefCallBudget budget) {
		CodefApiKeys keys = CodefApiKeys.fromEnvironment(
				Map.of(CodefApiKeys.CLIENT_ID_VARIABLE, "synthetic-id", CodefApiKeys.CLIENT_SECRET_VARIABLE, "synthetic-value"));
		return new CodefClient(keys, this.stub.settings(Duration.ofSeconds(5)), budget);
	}

}
