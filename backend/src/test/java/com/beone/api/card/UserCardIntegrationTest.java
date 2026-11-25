package com.beone.api.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.beone.api.card.application.CardProductProfile;
import com.beone.api.card.application.MappingStatus;
import com.beone.api.card.application.SupportedCardProducts;
import com.beone.api.card.persistence.UserCardEntity;
import com.beone.api.card.persistence.UserCardRepository;
import com.beone.api.support.IntegrationTest;
import com.beone.api.user.application.DemoUser;
import com.beone.api.user.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

/**
 * Testcontainers MySQL에서 V4 마이그레이션(ddl-auto: validate 통과)·가상 포트폴리오 시드·보유 카드 저장과 조회·같은 상품 중복
 * 금지(D-23)·사용자별 분리(D-09)를 실제 HTTP로 확인한다.
 *
 * <p>기대 형식은 docs/api/cards.md, 계약에 없던 값은 결정 27 A2절이다. 시계는 서울 2026-11-17 10:30으로 고정한다.
 */
@IntegrationTest
@ActiveProfiles("local")
@Import(UserCardIntegrationTest.FixedClockConfiguration.class)
class UserCardIntegrationTest {

	private static final String HEADER = "X-BeONE-Demo-User";

	private static final String CARDS = "/api/v1/me/cards";

	private static final String NOW = "2026-11-17T10:30:00+09:00";

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	UserRepository userRepository;

	@Autowired
	UserCardRepository userCardRepository;

	@Autowired
	PlatformTransactionManager transactionManager;

	@BeforeEach
	void clearManualCards() {
		this.jdbcTemplate.update("DELETE FROM user_cards WHERE holding_source <> 'SYNTHETIC'");
	}

	@Test
	void userCardMigrationIsApplied() {
		Boolean success = this.jdbcTemplate.queryForObject(
				"SELECT success FROM flyway_schema_history WHERE version = '4'", Boolean.class);

		assertThat(success).isTrue();
	}

	@Test
	void virtualPortfolioSeedIsTheFourRuleCardsWithProductNames() {
		Map<String, String> expected = new LinkedHashMap<>();
		SupportedCardProducts.ALL.stream()
			.sorted(Comparator.comparing(CardProductProfile::cardProductId))
			.forEach(profile -> expected.put(profile.cardProductId(), profile.cardName()));

		List<Map<String, Object>> rows = this.jdbcTemplate.queryForList(
				"SELECT card_product_id, display_name, mapping_status, user_id FROM user_cards "
						+ "WHERE holding_source = 'SYNTHETIC' ORDER BY user_card_id");

		Map<String, String> seeded = new LinkedHashMap<>();
		rows.forEach(row -> seeded.put((String) row.get("card_product_id"), (String) row.get("display_name")));
		assertThat(seeded).containsExactlyEntriesOf(expected);
		assertThat(rows).allSatisfy(row -> {
			assertThat(row.get("mapping_status")).isNull();
			assertThat(((Number) row.get("user_id")).longValue()).isEqualTo(userId(DemoUser.VIRTUAL_PORTFOLIO));
		});
	}

	@Test
	void virtualPortfolioListsSeedCardsAndIsReadOnly() {
		List<Map<String, Object>> items = items(send("GET", CARDS, "VIRTUAL_PORTFOLIO", null).getBody());

		assertThat(items).extracting(item -> item.get("cardProductId"))
			.containsExactly("01664", "01914", "09174", "09271");
		assertThat(items).allSatisfy(item -> {
			assertThat(item.get("holdingSource")).isEqualTo("SYNTHETIC");
			assertThat(item).containsEntry("mapping", null);
			@SuppressWarnings("unchecked")
			Map<String, Object> eligibility = (Map<String, Object>) item.get("recommendationEligibility");
			assertThat(eligibility).containsEntry("eligible", true).containsEntry("reason", null);
			assertThat((String) item.get("userCardId")).matches("uc-[1-9][0-9]*");
		});
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch("""
				{"data":{"demoUser":"VIRTUAL_PORTFOLIO","displayName":"가상 포트폴리오","portfolioType":"SYNTHETIC",
				 "readOnly":true,"cardCount":4,"codefConnected":false}}
				""", send("GET", "/api/v1/me", "VIRTUAL_PORTFOLIO", null).getBody());

		String seedId = (String) items.get(0).get("userCardId");
		String readOnly = "{\"code\":\"PORTFOLIO_READ_ONLY\",\"message\":\"가상 포트폴리오의 보유 카드는 바꿀 수 없습니다.\",\"errors\":[]}";
		List<ResponseEntity<String>> writes = List.of(
				send("POST", CARDS, "VIRTUAL_PORTFOLIO", "{\"displayName\":\"카드\"}"),
				send("PATCH", CARDS + "/" + seedId + "/mapping", "VIRTUAL_PORTFOLIO", "{\"status\":\"UNMAPPED\"}"),
				send("PATCH", CARDS + "/" + seedId, "VIRTUAL_PORTFOLIO", "{\"excludedFromRecommendation\":true}"),
				send("DELETE", CARDS + "/" + seedId, "VIRTUAL_PORTFOLIO", null));
		for (ResponseEntity<String> response : writes) {
			assertThat(response.getStatusCode().value()).isEqualTo(409);
			JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch(readOnly, response.getBody());
		}
		assertThat(this.jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM user_cards WHERE holding_source = 'SYNTHETIC' AND released_at IS NULL "
						+ "AND excluded_from_recommendation = FALSE",
				Long.class)).isEqualTo(4L);
	}

	@Test
	void manualCardLifecycleIsStoredAndReleaseKeepsTheRow() {
		ResponseEntity<String> created = send("POST", CARDS, "MY_CARDS",
				"{\"displayName\":\" 청춘대로 톡톡 \",\"cardProductId\":\"09174\",\"notSupported\":false}");

		assertThat(created.getStatusCode().value()).isEqualTo(201);
		String id = (String) data(created.getBody()).get("userCardId");
		assertThat(id).matches("uc-[1-9][0-9]*");
		String item = """
				{"userCardId":"%s","displayName":"청춘대로 톡톡","holdingSource":"MANUAL",
				 "mapping":{"status":"USER_CONFIRMED","cardProductId":"09174","confirmationMethod":"MANUAL_SELECTION",
				            "confirmedAt":"%s","candidateCardProductId":null,"candidateBasis":null,
				            "note":"사용자 확인 매핑, 공식 SKU·약관 버전 미검증"},
				 "excludedFromRecommendation":false,"recommendationEligibility":{"eligible":true,"reason":null},
				 "registeredAt":"%s"}
				""".formatted(id, NOW, NOW);
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch("{\"data\":" + item + "}", created.getBody());
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch("{\"data\":{\"items\":[" + item + "]}}",
				send("GET", CARDS, "MY_CARDS", null).getBody());
		assertThat(data(send("GET", "/api/v1/me", "MY_CARDS", null).getBody())).containsEntry("cardCount", 1);

		ResponseEntity<String> excluded = send("PATCH", CARDS + "/" + id, "MY_CARDS",
				"{\"displayName\":\"톡톡\",\"excludedFromRecommendation\":true}");
		assertThat(excluded.getStatusCode().value()).isEqualTo(200);
		assertThat(data(excluded.getBody())).containsEntry("displayName", "톡톡")
			.containsEntry("excludedFromRecommendation", true)
			.containsEntry("invalidatedRecommendationIds", List.of());

		ResponseEntity<String> unmapped = send("PATCH", CARDS + "/" + id + "/mapping", "MY_CARDS",
				"{\"status\":\"UNMAPPED\"}");
		assertThat(unmapped.getStatusCode().value()).isEqualTo(200);
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch("""
				{"data":{"userCardId":"%s","displayName":"톡톡","holdingSource":"MANUAL",
				 "mapping":{"status":"UNMAPPED","cardProductId":null,"confirmationMethod":null,"confirmedAt":null,
				            "candidateCardProductId":null,"candidateBasis":null,"note":null},
				 "excludedFromRecommendation":true,
				 "recommendationEligibility":{"eligible":false,"reason":"USER_EXCLUDED"},
				 "invalidatedRecommendationIds":[]}}
				""".formatted(id), unmapped.getBody());

		ResponseEntity<String> released = send("DELETE", CARDS + "/" + id, "MY_CARDS", null);
		assertThat(released.getStatusCode().value()).isEqualTo(204);
		assertThat(released.getBody()).isNull();
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch("{\"data\":{\"items\":[]}}",
				send("GET", CARDS, "MY_CARDS", null).getBody());
		assertThat(data(send("GET", "/api/v1/me", "MY_CARDS", null).getBody())).containsEntry("cardCount", 0);
		assertThat(send("DELETE", CARDS + "/" + id, "MY_CARDS", null).getStatusCode().value()).isEqualTo(404);
		assertThat(send("PATCH", CARDS + "/" + id, "MY_CARDS", "{\"displayName\":\"x\"}").getStatusCode().value())
			.isEqualTo(404);
		Map<String, Object> row = this.jdbcTemplate.queryForMap(
				"SELECT user_id, released_at FROM user_cards WHERE user_card_id = ?", Long.parseLong(id.substring(3)));
		assertThat(row.get("released_at")).isNotNull();
		assertThat(((Number) row.get("user_id")).longValue()).isEqualTo(userId(DemoUser.MY_CARDS));
	}

	@Test
	void sameProductCannotBeConfirmedTwiceUntilReleased() {
		String duplicated = "{\"code\":\"USER_CARD_DUPLICATED\",\"message\":\"같은 상품의 카드가 이미 등록되어 있습니다.\",\"errors\":[]}";
		String first = (String) data(send("POST", CARDS, "MY_CARDS",
				"{\"displayName\":\"톡톡\",\"cardProductId\":\"09174\"}").getBody()).get("userCardId");

		ResponseEntity<String> again = send("POST", CARDS, "MY_CARDS",
				"{\"displayName\":\"톡톡2\",\"cardProductId\":\"09174\"}");
		assertThat(again.getStatusCode().value()).isEqualTo(409);
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch(duplicated, again.getBody());

		String unmapped = (String) data(send("POST", CARDS, "MY_CARDS", "{\"displayName\":\"모름\"}").getBody())
			.get("userCardId");
		String confirm = "{\"status\":\"USER_CONFIRMED\",\"cardProductId\":\"09174\",\"confirmationMethod\":\"MANUAL_SELECTION\"}";
		ResponseEntity<String> mapped = send("PATCH", CARDS + "/" + unmapped + "/mapping", "MY_CARDS", confirm);
		assertThat(mapped.getStatusCode().value()).isEqualTo(409);
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch(duplicated, mapped.getBody());

		assertThat(send("PATCH", CARDS + "/" + first + "/mapping", "MY_CARDS", confirm).getStatusCode().value())
			.isEqualTo(200);
		assertThat(send("POST", CARDS, "MY_CARDS", "{\"displayName\":\"법인\",\"notSupported\":true}")
			.getStatusCode()
			.value()).isEqualTo(201);
		assertThat(send("POST", CARDS, "MY_CARDS", "{\"displayName\":\"법인2\",\"notSupported\":true}")
			.getStatusCode()
			.value()).isEqualTo(201);

		assertThat(send("DELETE", CARDS + "/" + first, "MY_CARDS", null).getStatusCode().value()).isEqualTo(204);
		assertThat(send("PATCH", CARDS + "/" + unmapped + "/mapping", "MY_CARDS", confirm).getStatusCode().value())
			.isEqualTo(200);
	}

	@Test
	void renameThatReadTheCardBeforeAConcurrentReleaseKeepsTheRelease() {
		String id = (String) data(send("POST", CARDS, "MY_CARDS", "{\"displayName\":\"카드\"}").getBody())
			.get("userCardId");
		long cardId = Long.parseLong(id.substring(3));
		TransactionTemplate later = new TransactionTemplate(this.transactionManager);
		later.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

		// 이름 변경 트랜잭션이 카드를 읽은 뒤, 다른 트랜잭션이 해제를 커밋하고, 그다음 이름 변경이 저장된다.
		new TransactionTemplate(this.transactionManager).executeWithoutResult(status -> {
			UserCardEntity stale = this.userCardRepository.findById(cardId).orElseThrow();
			later.executeWithoutResult(inner -> this.jdbcTemplate
				.update("UPDATE user_cards SET released_at = NOW() WHERE user_card_id = ?", cardId));
			stale.rename("새 이름");
			this.userCardRepository.saveAndFlush(stale);
		});

		Map<String, Object> row = this.jdbcTemplate
			.queryForMap("SELECT display_name, released_at FROM user_cards WHERE user_card_id = ?", cardId);
		assertThat(row.get("display_name")).isEqualTo("새 이름");
		assertThat(row.get("released_at")).isNotNull();
		assertThat(items(send("GET", CARDS, "MY_CARDS", null).getBody())).isEmpty();
	}

	@Test
	void concurrentMappingChangeWaitsForTheFirstAndReadsItsCommittedState() throws Exception {
		String id = (String) data(send("POST", CARDS, "MY_CARDS",
				"{\"displayName\":\"카드\",\"cardProductId\":\"09174\"}").getBody()).get("userCardId");
		long cardId = Long.parseLong(id.substring(3));
		long myCards = userId(DemoUser.MY_CARDS);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			CompletableFuture<ResponseEntity<String>> second = new CompletableFuture<>();
			// 첫 요청과 같은 쓰기용 조회로 행을 잠근 채, 두 번째 요청(01914로 확인)을 보낸다.
			new TransactionTemplate(this.transactionManager).executeWithoutResult(status -> {
				UserCardEntity first = this.userCardRepository.findByUserCardIdAndUserIdAndReleasedAtIsNull(cardId, myCards)
					.orElseThrow();
				executor.submit(() -> second.complete(send("PATCH", CARDS + "/" + id + "/mapping", "MY_CARDS",
						"{\"status\":\"USER_CONFIRMED\",\"cardProductId\":\"01914\",\"confirmationMethod\":\"MANUAL_SELECTION\"}")));
				try {
					Thread.sleep(1500);
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(ex);
				}
				assertThat(second).isNotDone();
				first.changeMapping(MappingStatus.UNMAPPED, null, null, null);
				this.userCardRepository.saveAndFlush(first);
			});

			ResponseEntity<String> response = second.get(30, TimeUnit.SECONDS);
			assertThat(response.getStatusCode().value()).isEqualTo(200);
			Map<String, Object> row = this.jdbcTemplate
				.queryForMap("SELECT mapping_status, card_product_id FROM user_cards WHERE user_card_id = ?", cardId);
			assertThat(row).containsEntry("mapping_status", "USER_CONFIRMED").containsEntry("card_product_id", "01914");
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void databaseRejectsSecondActiveConfirmedCardAndProductOnUnmappedCard() {
		long myCards = userId(DemoUser.MY_CARDS);
		String insert = "INSERT INTO user_cards (user_id, display_name, holding_source, card_product_id, mapping_status, "
				+ "confirmation_method, confirmed_at, excluded_from_recommendation, registered_at) "
				+ "VALUES (?, '카드', 'MANUAL', '01664', 'USER_CONFIRMED', 'MANUAL_SELECTION', NOW(), FALSE, NOW())";
		this.jdbcTemplate.update(insert, myCards);

		assertThatThrownBy(() -> this.jdbcTemplate.update(insert, myCards))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("uk_user_cards_active_product");
		assertThatThrownBy(() -> this.jdbcTemplate.update(
				"INSERT INTO user_cards (user_id, display_name, holding_source, card_product_id, mapping_status, "
						+ "excluded_from_recommendation, registered_at) "
						+ "VALUES (?, '카드', 'MANUAL', '01914', 'UNMAPPED', FALSE, NOW())",
				myCards))
			.isInstanceOf(DataAccessException.class)
			.hasMessageContaining("ck_user_cards_product_by_status");
	}

	@Test
	void unknownProductIsNotFoundAndNothingIsStored() {
		ResponseEntity<String> response = send("POST", CARDS, "MY_CARDS",
				"{\"displayName\":\"카드\",\"cardProductId\":\"9174\"}");

		assertThat(response.getStatusCode().value()).isEqualTo(404);
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch(
				"{\"code\":\"CARD_PRODUCT_NOT_FOUND\",\"message\":\"카드 상품을 찾을 수 없습니다.\",\"errors\":[]}",
				response.getBody());
		assertThat(this.jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM user_cards WHERE holding_source = 'MANUAL'", Long.class)).isZero();
	}

	@Test
	void otherDemoUsersCardsAreInvisible() {
		String mine = (String) data(send("POST", CARDS, "MY_CARDS", "{\"displayName\":\"내 카드\"}").getBody())
			.get("userCardId");
		String seedId = (String) items(send("GET", CARDS, "VIRTUAL_PORTFOLIO", null).getBody()).get(0)
			.get("userCardId");

		assertThat(items(send("GET", CARDS, "VIRTUAL_PORTFOLIO", null).getBody()))
			.noneMatch(item -> mine.equals(item.get("userCardId")));
		assertThat(items(send("GET", CARDS, "MY_CARDS", null).getBody())).extracting(item -> item.get("userCardId"))
			.containsExactly(mine);

		String notFound = "{\"code\":\"USER_CARD_NOT_FOUND\",\"message\":\"보유 카드를 찾을 수 없습니다.\",\"errors\":[]}";
		List<ResponseEntity<String>> responses = List.of(
				send("PATCH", CARDS + "/" + seedId + "/mapping", "MY_CARDS", "{\"status\":\"UNMAPPED\"}"),
				send("PATCH", CARDS + "/" + seedId, "MY_CARDS", "{\"displayName\":\"x\"}"),
				send("DELETE", CARDS + "/" + seedId, "MY_CARDS", null),
				send("DELETE", CARDS + "/uc-999999999", "MY_CARDS", null),
				send("DELETE", CARDS + "/" + seedId.substring(3), "MY_CARDS", null));
		for (ResponseEntity<String> response : responses) {
			assertThat(response.getStatusCode().value()).isEqualTo(404);
			JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch(notFound, response.getBody());
		}
	}

	private long userId(DemoUser demoUser) {
		return this.userRepository.findByDemoUser(demoUser).orElseThrow().getUserId();
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> data(String body) {
		return (Map<String, Object>) tools.jackson.databind.json.JsonMapper.builder()
			.build()
			.readValue(body, Map.class)
			.get("data");
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> items(String body) {
		return (List<Map<String, Object>>) data(body).get("items");
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

	/** 등록·확인·해제 시각을 서울 2026-11-17 10:30으로 고정한다. */
	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClockConfiguration {

		@Bean
		Clock fixedClock() {
			return Clock.fixed(Instant.parse("2026-11-17T01:30:00Z"), ZoneOffset.UTC);
		}

	}

}
