package com.beone.api.codef.application;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.sql.DataSource;

import com.beone.api.codef.transport.CodefApi;
import com.beone.api.codef.transport.CodefApiKeys;
import com.beone.api.codef.transport.CodefCallResult;
import com.beone.api.codef.transport.CodefClient;
import com.beone.api.codef.transport.CodefStubServer;
import com.beone.api.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프로세스를 넘는 CODEF 호출 총량(LP-2, N14)을 Testcontainers MySQL(V15)로 확인한다.
 *
 * <p>고정하는 것: 동시 요청에도 상한을 넘지 않음, 새 애플리케이션 컨텍스트(재기동 흉내)에서도 누적이 남음, 창 경계(시작 포함·끝 제외)와 새 창의 새
 * 누적, 상한에 닿으면 루프백 스텁에 요청 0건. 상한 값은 검증 환경 권장값(API별 3)이다. 실 CODEF는 부르지 않는다.
 */
@IntegrationTest
@ActiveProfiles({ "member", "codef-live" })
@TestPropertySource(properties = { "beone.codef.budget.per-api=3", "beone.codef.budget.window-start=2026-01-01T00:00:00Z",
		"beone.codef.budget.window-end=2100-01-01T00:00:00Z" })
class CodefCallBudgetIntegrationTest {

	private static final Map<String, Object> SETTINGS = Map.of(CodefCallBudgetSettings.PER_API, "3",
			CodefCallBudgetSettings.WINDOW_START, "2026-01-01T00:00:00Z", CodefCallBudgetSettings.WINDOW_END,
			"2100-01-01T00:00:00Z");

	@Autowired
	JdbcCodefCallBudget budget;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	DataSource dataSource;

	@BeforeEach
	@AfterEach
	void clearRows() {
		this.jdbcTemplate.update("DELETE FROM codef_call_budgets");
	}

	@Test
	void concurrentRequestsNeverExceedTheLimit() throws Exception {
		int threads = 16;
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		try {
			List<Future<Boolean>> results = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				Callable<Boolean> task = () -> {
					ready.countDown();
					go.await();
					return this.budget.tryAcquire("HELD_CARDS");
				};
				results.add(executor.submit(task));
			}
			ready.await();
			go.countDown();
			int granted = 0;
			for (Future<Boolean> result : results) {
				granted += result.get() ? 1 : 0;
			}

			assertThat(granted).isEqualTo(3);
			assertThat(used("HELD_CARDS")).isEqualTo(3);
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void countSurvivesANewApplicationContext() {
		assertThat(this.budget.tryAcquire("APPROVALS")).isTrue();
		assertThat(this.budget.tryAcquire("APPROVALS")).isTrue();

		// 같은 DB만 공유하는 새 컨텍스트에서 새 빈을 만든다. 메모리 상태는 이어지지 않는다
		try (AnnotationConfigApplicationContext restarted = new AnnotationConfigApplicationContext()) {
			restarted.getEnvironment().setActiveProfiles("member", "codef-live");
			restarted.getEnvironment().getPropertySources().addFirst(new MapPropertySource("budget", SETTINGS));
			restarted.registerBean(JdbcTemplate.class, () -> new JdbcTemplate(this.dataSource));
			restarted.register(JdbcCodefCallBudget.class);
			restarted.refresh();
			JdbcCodefCallBudget afterRestart = restarted.getBean(JdbcCodefCallBudget.class);

			assertThat(afterRestart).isNotSameAs(this.budget);
			assertThat(afterRestart.tryAcquire("APPROVALS")).isTrue();
			assertThat(afterRestart.tryAcquire("APPROVALS")).isFalse();
		}
		assertThat(used("APPROVALS")).isEqualTo(3);
	}

	@Test
	void apisAreCountedSeparately() {
		for (int i = 0; i < 3; i++) {
			assertThat(this.budget.tryAcquire("TOKEN")).isTrue();
		}
		assertThat(this.budget.tryAcquire("TOKEN")).isFalse();
		assertThat(this.budget.tryAcquire("CARD_PERFORMANCES")).isTrue();
	}

	@Test
	void windowBoundaryAndNewWindow() {
		Instant start = Instant.parse("2026-12-01T00:00:00Z");
		Instant end = Instant.parse("2026-12-08T00:00:00Z");
		CodefCallBudgetSettings window = new CodefCallBudgetSettings(Optional.of(1), Optional.of(start), Optional.of(end));

		assertThat(at(window, start.minusMillis(1)).tryAcquire("HELD_CARDS")).isFalse();
		assertThat(at(window, end).tryAcquire("HELD_CARDS")).isFalse();
		assertThat(at(window, end.minusMillis(1)).tryAcquire("HELD_CARDS")).isTrue();
		assertThat(at(window, start).tryAcquire("HELD_CARDS")).isFalse();

		// 창을 새로 정하면 새 행으로 다시 센다
		CodefCallBudgetSettings next = new CodefCallBudgetSettings(Optional.of(1), Optional.of(end),
				Optional.of(end.plus(Duration.ofDays(7))));
		assertThat(at(next, end).tryAcquire("HELD_CARDS")).isTrue();
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM codef_call_budgets WHERE api = 'HELD_CARDS'",
				Integer.class)).isEqualTo(2);
	}

	@Test
	void missingSettingsSendNothing() {
		JdbcCodefCallBudget closed = new JdbcCodefCallBudget(this.jdbcTemplate, CodefCallBudgetSettings.NONE,
				Clock.systemUTC());

		assertThat(closed.tryAcquire("HELD_CARDS")).isFalse();
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM codef_call_budgets", Integer.class)).isZero();
	}

	@Test
	void reachedLimitSendsNoHttpRequest() throws IOException {
		try (CodefStubServer stub = new CodefStubServer()) {
			stub.respond(CodefStubServer.TOKEN_PATH, 200, "{\"access_token\":\"synthetic-token\"}");
			stub.respond(CodefApi.HELD_CARDS.path(), 200, "{\"result\":{\"code\":\"CF-00000\"},\"data\":[]}");
			CodefClient client = new CodefClient(
					CodefApiKeys.fromEnvironment(
							Map.of("CODEF_CLIENT_ID", "synthetic-id", "CODEF_CLIENT_SECRET", "synthetic-value")),
					stub.settings(Duration.ofSeconds(5)), this.budget);
			LiveCodefGateway gateway = new LiveCodefGateway(client, CodefPasswordEncryptor.fromEnvironment(Map.of()));

			for (int i = 0; i < 3; i++) {
				assertThat(gateway.heldCards("0301", "synthetic-connected-id"))
					.isInstanceOf(CodefCallResult.Answered.class);
			}
			var denied = (CodefCallResult.Failed<?>) gateway.heldCards("0301", "synthetic-connected-id");

			assertThat(denied.reason()).isEqualTo(CodefCallResult.Reason.BUDGET_EXHAUSTED);
			assertThat(stub.requestsTo(CodefApi.HELD_CARDS.path())).hasSize(3);
			assertThat(used("HELD_CARDS")).isEqualTo(3);
		}
	}

	private JdbcCodefCallBudget at(CodefCallBudgetSettings settings, Instant now) {
		return new JdbcCodefCallBudget(this.jdbcTemplate, settings, Clock.fixed(now, ZoneOffset.UTC));
	}

	private int used(String api) {
		return this.jdbcTemplate.queryForObject("SELECT used FROM codef_call_budgets WHERE api = ?", Integer.class, api);
	}

}
