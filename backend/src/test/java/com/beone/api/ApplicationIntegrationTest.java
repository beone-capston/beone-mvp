package com.beone.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.engine.EngineRequest;
import com.beone.api.benefit.engine.OrderInput;
import com.beone.api.benefit.engine.RecommendationEngine;
import com.beone.api.benefit.rule.RuleCatalog;
import com.beone.api.benefit.rule.RuleSelection;
import com.beone.api.support.IntegrationTest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

@IntegrationTest
class ApplicationIntegrationTest {

	@LocalServerPort
	int port;

	@Autowired
	RuleCatalog ruleCatalog;

	@Autowired
	RecommendationEngine recommendationEngine;

	@Test
	void healthEndpointReportsUp() {
		String body = RestClient.create("http://localhost:" + port)
			.get()
			.uri("/actuator/health")
			.retrieve()
			.body(String.class);

		assertThat(body).contains("\"status\":\"UP\"");
	}

	@Test
	void startupLoadsRuleCatalog() {
		assertThat(ruleCatalog.select(CardId.of("09174"), LocalDate.parse("2026-09-01")))
			.isInstanceOf(RuleSelection.Selected.class);
	}

	@Test
	void startupProvidesTheRecommendationEngine() {
		LocalDate date = LocalDate.parse("2026-10-15");
		OrderInput order = new OrderInput(null, null, 10_000, date, null, "ORDINARY_DOMESTIC", "DIRECT");

		assertThat(recommendationEngine.recommend(new EngineRequest(date, order, "AUTO_RECOMMEND", null, List.of())).outcome())
			.isEqualTo("NOT_DETERMINED");
	}

}
