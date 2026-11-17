package com.beone.api.preference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import com.beone.api.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.web.client.RestClient;

/**
 * 결제 전 설정 API와 대표 카드 보호를 실제 HTTP·MySQL로 확인한다. Flyway 적용({@code ddl-auto: validate}), 저장·조회, 사용자별
 * 분리(D-09), 보유 카드 API의 409 {@code REPRESENTATIVE_CARD_IN_USE}와 자동 추천 모드 대표 카드 해제 시 비움(D-23)을 본다.
 *
 * <p>기대값은 docs/api/preferences.md·docs/api/cards.md와 결정 28 관리자 판단이다. 시계는 서울 2026-11-17 10:30으로 고정한다.
 * 설정 행이 보유 카드를 참조하므로(외래 키) 다른 통합 테스트의 보유 카드 정리를 막지 않게 테스트 앞뒤로 설정과 수동 카드를 지운다.
 */
@IntegrationTest
@ActiveProfiles("local")
@Import(PaymentPreferenceIntegrationTest.FixedClockConfiguration.class)
class PaymentPreferenceIntegrationTest {

	private static final String HEADER = "X-BeONE-Demo-User";

	private static final String PATH = "/api/v1/me/payment-preference";

	private static final String CARDS = "/api/v1/me/cards";

	private static final String NOW = "2026-11-17T10:30:00+09:00";

	private static final String IN_USE = "{\"code\":\"REPRESENTATIVE_CARD_IN_USE\",\"message\":\"결제 전 설정의 대표 카드입니다. 먼저 설정을 바꿔 주세요.\",\"errors\":[]}";

	private static final String NOT_ELIGIBLE = "{\"code\":\"REPRESENTATIVE_CARD_NOT_ELIGIBLE\",\"message\":\"추천 비교 대상인 카드만 대표 카드로 고를 수 있습니다.\",\"errors\":[]}";

	private static final String CARD_NOT_FOUND = "{\"code\":\"USER_CARD_NOT_FOUND\",\"message\":\"보유 카드를 찾을 수 없습니다.\",\"errors\":[]}";

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void clearPreferencesAndManualCards() {
		this.jdbcTemplate.update("DELETE FROM payment_preferences");
		this.jdbcTemplate.update("DELETE FROM user_cards WHERE holding_source <> 'SYNTHETIC'");
	}

	@Test
	void preferenceMigrationIsApplied() {
		Boolean success = this.jdbcTemplate.queryForObject(
				"SELECT success FROM flyway_schema_history WHERE version = '6'", Boolean.class);

		assertThat(success).isTrue();
	}

	@Test
	void neverSavedPreferenceIsAutoRecommendWithoutRow() {
		assertBody(send("GET", PATH, "MY_CARDS", null), 200,
				"{\"data\":{\"selectionMode\":\"AUTO_RECOMMEND\",\"representativeUserCardId\":null,\"updatedAt\":null}}");
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_preferences", Integer.class)).isZero();
	}

	@Test
	void fixedRepresentativeIsSavedAndProtectedFromExclusionAndRelease() {
		String card = registerConfirmed("09174");

		ResponseEntity<String> saved = send("PUT", PATH, "MY_CARDS", body("FIXED_REPRESENTATIVE", card));
		String expected = "{\"data\":{\"selectionMode\":\"FIXED_REPRESENTATIVE\",\"representativeUserCardId\":\"%s\",\"updatedAt\":\"%s\"}}"
			.formatted(card, NOW);
		assertBody(saved, 200, expected);
		assertBody(send("GET", PATH, "MY_CARDS", null), 200, expected);
		Map<String, Object> row = this.jdbcTemplate.queryForMap(
				"SELECT p.selection_mode, p.representative_user_card_id, p.updated_at FROM payment_preferences p "
						+ "JOIN users u ON u.user_id = p.user_id WHERE u.demo_user = 'MY_CARDS'");
		assertThat(row.get("selection_mode")).isEqualTo("FIXED_REPRESENTATIVE");
		assertThat(((Number) row.get("representative_user_card_id")).longValue()).isEqualTo(number(card));

		assertBody(send("PATCH", CARDS + "/" + card, "MY_CARDS", "{\"excludedFromRecommendation\":true}"), 409, IN_USE);
		assertBody(send("DELETE", CARDS + "/" + card, "MY_CARDS", null), 409, IN_USE);
		Map<String, Object> cardRow = this.jdbcTemplate.queryForMap(
				"SELECT excluded_from_recommendation, released_at FROM user_cards WHERE user_card_id = ?", number(card));
		assertThat(cardRow.get("excluded_from_recommendation")).isEqualTo(false);
		assertThat(cardRow.get("released_at")).isNull();
	}

	@Test
	void autoModeRepresentativeSurvivesExclusionAndIsClearedOnRelease() {
		String card = registerConfirmed("09271");
		assertThat(send("PUT", PATH, "MY_CARDS", body("AUTO_RECOMMEND", card)).getStatusCode().value()).isEqualTo(200);

		assertThat(send("PATCH", CARDS + "/" + card, "MY_CARDS", "{\"excludedFromRecommendation\":true}")
			.getStatusCode()
			.value()).isEqualTo(200);
		assertThat(data(send("GET", PATH, "MY_CARDS", null).getBody())).containsEntry("representativeUserCardId", card);

		// 추천에서 뺀 카드는 새로 대표 카드로 고를 수 없다(D-15).
		assertBody(send("PUT", PATH, "MY_CARDS", body("AUTO_RECOMMEND", card)), 409, NOT_ELIGIBLE);

		assertThat(send("DELETE", CARDS + "/" + card, "MY_CARDS", null).getStatusCode().value()).isEqualTo(204);
		assertBody(send("GET", PATH, "MY_CARDS", null), 200,
				"{\"data\":{\"selectionMode\":\"AUTO_RECOMMEND\",\"representativeUserCardId\":null,\"updatedAt\":\"%s\"}}"
					.formatted(NOW));
	}

	@Test
	void unmappedReleasedAndForeignCardsCannotBeRepresentative() {
		String unmapped = (String) data(send("POST", CARDS, "MY_CARDS", "{\"displayName\":\"모르는 카드\"}").getBody())
			.get("userCardId");
		assertBody(send("PUT", PATH, "MY_CARDS", body("FIXED_REPRESENTATIVE", unmapped)), 409, NOT_ELIGIBLE);

		String released = registerConfirmed("01664");
		assertThat(send("DELETE", CARDS + "/" + released, "MY_CARDS", null).getStatusCode().value()).isEqualTo(204);
		String portfolioSeed = seedCardOfPortfolio();
		for (String id : List.of(released, portfolioSeed, "uc-999999999", "1001")) {
			assertBody(send("PUT", PATH, "MY_CARDS", body("AUTO_RECOMMEND", id)), 404, CARD_NOT_FOUND);
		}
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_preferences", Integer.class)).isZero();
	}

	@Test
	void virtualPortfolioCanFixItsSeedCardAndUsersAreSeparated() {
		String seed = seedCardOfPortfolio();

		assertBody(send("PUT", PATH, "VIRTUAL_PORTFOLIO", body("FIXED_REPRESENTATIVE", seed)), 200,
				"{\"data\":{\"selectionMode\":\"FIXED_REPRESENTATIVE\",\"representativeUserCardId\":\"%s\",\"updatedAt\":\"%s\"}}"
					.formatted(seed, NOW));

		assertBody(send("GET", PATH, "MY_CARDS", null), 200,
				"{\"data\":{\"selectionMode\":\"AUTO_RECOMMEND\",\"representativeUserCardId\":null,\"updatedAt\":null}}");
		assertThat(data(send("GET", PATH, "VIRTUAL_PORTFOLIO", null).getBody())).containsEntry("representativeUserCardId",
				seed);
	}

	@Test
	void repeatedPutOverwritesSingleRow() {
		String card = registerConfirmed("01914");
		send("PUT", PATH, "MY_CARDS", body("FIXED_REPRESENTATIVE", card));

		assertBody(send("PUT", PATH, "MY_CARDS", "{\"selectionMode\":\"AUTO_RECOMMEND\",\"representativeUserCardId\":null}"),
				200, "{\"data\":{\"selectionMode\":\"AUTO_RECOMMEND\",\"representativeUserCardId\":null,\"updatedAt\":\"%s\"}}"
					.formatted(NOW));
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_preferences", Integer.class)).isOne();
		// 대표 카드를 비웠으므로 해제할 수 있다.
		assertThat(send("DELETE", CARDS + "/" + card, "MY_CARDS", null).getStatusCode().value()).isEqualTo(204);
	}

	@Test
	void databaseRejectsFixedModeWithoutCard() {
		Long userId = this.jdbcTemplate.queryForObject("SELECT user_id FROM users WHERE demo_user = 'MY_CARDS'",
				Long.class);

		assertThatThrownBy(() -> this.jdbcTemplate.update("INSERT INTO payment_preferences (user_id, selection_mode, "
				+ "representative_user_card_id, updated_at) VALUES (?, 'FIXED_REPRESENTATIVE', NULL, NOW())", userId))
			.isInstanceOf(DataAccessException.class)
			.hasMessageContaining("ck_payment_preferences_fixed_card");
		assertThatThrownBy(() -> this.jdbcTemplate.update("INSERT INTO payment_preferences (user_id, selection_mode, "
				+ "representative_user_card_id, updated_at) VALUES (?, 'MANUAL', NULL, NOW())", userId))
			.isInstanceOf(DataAccessException.class)
			.hasMessageContaining("ck_payment_preferences_mode");
	}

	private String registerConfirmed(String cardProductId) {
		ResponseEntity<String> created = send("POST", CARDS, "MY_CARDS",
				"{\"displayName\":\"카드\",\"cardProductId\":\"%s\"}".formatted(cardProductId));
		assertThat(created.getStatusCode().value()).isEqualTo(201);
		return (String) data(created.getBody()).get("userCardId");
	}

	private String seedCardOfPortfolio() {
		Long id = this.jdbcTemplate.queryForObject("SELECT MIN(c.user_card_id) FROM user_cards c "
				+ "JOIN users u ON u.user_id = c.user_id WHERE u.demo_user = 'VIRTUAL_PORTFOLIO'", Long.class);
		return "uc-" + id;
	}

	private static long number(String userCardId) {
		return Long.parseLong(userCardId.substring("uc-".length()));
	}

	private static String body(String mode, String card) {
		return "{\"selectionMode\":\"%s\",\"representativeUserCardId\":\"%s\"}".formatted(mode, card);
	}

	private static void assertBody(ResponseEntity<String> response, int status, String expected) {
		assertThat(response.getStatusCode().value()).isEqualTo(status);
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch(expected, response.getBody());
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> data(String body) {
		return (Map<String, Object>) tools.jackson.databind.json.JsonMapper.builder()
			.build()
			.readValue(body, Map.class)
			.get("data");
	}

	private ResponseEntity<String> send(String method, String path, String demoUser, String body) {
		RestClient client = RestClient.builder()
			.baseUrl("http://localhost:" + this.port)
			.defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
			})
			.build();
		RestClient.RequestBodySpec spec = client.method(HttpMethod.valueOf(method)).uri(path).header(HEADER, demoUser);
		if (body != null) {
			spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
		}
		return spec.retrieve().toEntity(String.class);
	}

	/** 설정·등록·해제 시각을 서울 2026-11-17 10:30으로 고정한다. */
	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClockConfiguration {

		@Bean
		Clock fixedClock() {
			return Clock.fixed(Instant.parse("2026-11-17T01:30:00Z"), ZoneOffset.UTC);
		}

	}

}
