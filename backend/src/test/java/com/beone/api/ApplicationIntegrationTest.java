package com.beone.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.rule.RuleCatalog;
import com.beone.api.benefit.rule.RuleSelection;
import com.beone.api.support.IntegrationTest;
import java.time.LocalDate;
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

}
