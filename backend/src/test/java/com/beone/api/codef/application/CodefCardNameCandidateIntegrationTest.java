package com.beone.api.codef.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import com.beone.api.codef.CodefApproval;
import com.beone.api.codef.CodefCardPerformance;
import com.beone.api.codef.CodefHeldCard;
import com.beone.api.codef.CodefProductResponseReader;
import com.beone.api.codef.transport.CodefCallResult;
import com.beone.api.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/**
 * CODEF 카드명 사전 후보(#268)를 Testcontainers MySQL(V16 적용, {@code ddl-auto: validate})에서 동기화 → 보유 카드 조회 → 매핑 확인
 * 순서로 확인한다.
 *
 * <p>보유카드 응답만 테스트가 정하는 합성 게이트웨이로 바꾸고, 승인·실적은 가짜 게이트웨이의 합성 fixture를 그대로 쓴다. 카드명은 공개
 * 상품명과 합성 이름이며 실사용자 응답 원문이 아니다. 실 CODEF는 부르지 않는다. 기대값은 관리자 지시(2026-11-26 사용자 승인)의 적용
 * 범위·응답 조건과 docs/api/cards.md(CODEF_CANDIDATE는 현재 후보와 같을 때만)에서 옮겼다.
 */
@IntegrationTest
@ActiveProfiles("local")
@Import(CodefCardNameCandidateIntegrationTest.SyntheticConfiguration.class)
class CodefCardNameCandidateIntegrationTest {

	private static final String SYNCS = "/api/v1/me/codef/syncs";

	private static final String CARDS = "/api/v1/me/cards";

	private static final String TITANIUM = "올라운드 티타늄카드";

	private static final String NORI_WITH_ISSUER = "nori 체크카드(RF)|KB국민카드";

	private static final String PENGSOO = "펭수 노리 체크카드";

	private static final String UPPER_NORI = "NORI 체크카드(RF)";

	private static final String CHECK = "첵첵 체크카드";

	private static final String TOKTOK = "청춘대로 톡톡카드";

	private static final String MANUAL_MARK = "후보시험 수동카드";

	/** 다음 보유카드 조회가 돌려줄 카드명. */
	static final List<String> HELD = new CopyOnWriteArrayList<>();

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void clearRows() {
		HELD.clear();
		this.jdbcTemplate.update("DELETE FROM codef_approval_rows");
		this.jdbcTemplate.update("DELETE FROM codef_performance_references");
		this.jdbcTemplate.update("DELETE FROM codef_held_cards");
		this.jdbcTemplate.update("DELETE FROM codef_syncs");
		this.jdbcTemplate.update("DELETE FROM user_cards WHERE holding_source = 'CODEF'");
		this.jdbcTemplate.update("DELETE FROM user_cards WHERE holding_source = 'MANUAL' AND display_name = ?",
				MANUAL_MARK);
		this.jdbcTemplate.update("DELETE FROM codef_connections");
	}

	@Test
	void exactDictionaryNamesBecomeCandidatesAndVariantsStayUnmapped() {
		connect();
		HELD.addAll(List.of(TITANIUM, NORI_WITH_ISSUER, PENGSOO, UPPER_NORI));

		ResponseEntity<String> sync = send("POST", SYNCS, null);

		assertThat(sync.getStatusCode().value()).isEqualTo(200);
		List<Map<String, Object>> created = JsonPath.read(sync.getBody(), "$.data.heldCards.newCandidates");
		assertThat(created).extracting(row -> row.get("cardName"), row -> row.get("candidateCardProductId"),
				row -> row.get("mappingStatus"))
			.containsExactly(tuple(TITANIUM, "09271", "CANDIDATE"),
					tuple(NORI_WITH_ISSUER, "01664", "CANDIDATE"),
					tuple(PENGSOO, null, "UNMAPPED"),
					tuple(UPPER_NORI, null, "UNMAPPED"));

		Map<String, Map<String, Object>> mappings = mappingsByName();
		assertThat(mappings.get(TITANIUM)).containsEntry("status", "CANDIDATE")
			.containsEntry("candidateCardProductId", "09271")
			.containsEntry("cardProductId", null)
			.containsEntry("confirmationMethod", null)
			.containsEntry("confirmedAt", null)
			.containsEntry("candidateBasis", null);
		assertThat(mappings.get(NORI_WITH_ISSUER)).containsEntry("status", "CANDIDATE")
			.containsEntry("candidateCardProductId", "01664");
		assertThat(mappings.get(PENGSOO)).containsEntry("status", "UNMAPPED").containsEntry("candidateCardProductId", null);
		assertThat(mappings.get(UPPER_NORI)).containsEntry("status", "UNMAPPED")
			.containsEntry("candidateCardProductId", null);
		// 후보는 계산 대상이 아니다(MAPPING_UNCONFIRMED). 확정 상품·중복 금지 열에도 들어가지 않는다
		assertThat(eligibilityReason(TITANIUM)).isEqualTo("MAPPING_UNCONFIRMED");
		assertThat(count("SELECT COUNT(*) FROM user_cards WHERE holding_source = 'CODEF'"
				+ " AND (card_product_id IS NOT NULL OR active_product_id IS NOT NULL OR mapping_status = 'USER_CONFIRMED')"))
			.isZero();
	}

	@Test
	void userConfirmsTheCandidateWithCodefCandidateOnly() {
		connect();
		HELD.addAll(List.of(TITANIUM, PENGSOO));
		send("POST", SYNCS, null);
		String titanium = userCardId(TITANIUM);
		String pengsoo = userCardId(PENGSOO);

		// 다른 상품이나 후보 없는 카드에는 CODEF_CANDIDATE를 쓸 수 없다(400)
		assertThat(patch(titanium, "{\"status\":\"USER_CONFIRMED\",\"cardProductId\":\"09174\","
				+ "\"confirmationMethod\":\"CODEF_CANDIDATE\"}").getStatusCode().value()).isEqualTo(400);
		assertThat(patch(pengsoo, "{\"status\":\"USER_CONFIRMED\",\"cardProductId\":\"01664\","
				+ "\"confirmationMethod\":\"CODEF_CANDIDATE\"}").getStatusCode().value()).isEqualTo(400);
		assertThat(mappingsByName().get(TITANIUM)).containsEntry("status", "CANDIDATE");

		ResponseEntity<String> confirmed = patch(titanium, "{\"status\":\"USER_CONFIRMED\",\"cardProductId\":\"09271\","
				+ "\"confirmationMethod\":\"CODEF_CANDIDATE\"}");

		assertThat(confirmed.getStatusCode().value()).isEqualTo(200);
		Map<String, Object> mapping = JsonPath.read(confirmed.getBody(), "$.data.mapping");
		assertThat(mapping).containsEntry("status", "USER_CONFIRMED")
			.containsEntry("cardProductId", "09271")
			.containsEntry("confirmationMethod", "CODEF_CANDIDATE")
			.containsEntry("candidateCardProductId", null);
		assertThat(eligibilityReason(TITANIUM)).isNull();
	}

	@Test
	void resyncFillsOnlyUntouchedUnmappedCodefCardsAndKeepsProtectedMappings() {
		connect();
		HELD.addAll(List.of(TITANIUM, NORI_WITH_ISSUER, CHECK, TOKTOK));
		send("POST", SYNCS, null);
		// 후보 기능 전에 만든 카드처럼 모두 후보 없는 UNMAPPED로 되돌린다
		this.jdbcTemplate.update("UPDATE user_cards SET mapping_status = 'UNMAPPED', candidate_card_product_id = NULL"
				+ " WHERE holding_source = 'CODEF'");
		// 사용자가 매핑을 정한 카드: NOT_SUPPORTED, UNMAPPED(사용자 선택), 다른 상품으로 USER_CONFIRMED
		assertThat(patch(userCardId(NORI_WITH_ISSUER), "{\"status\":\"NOT_SUPPORTED\"}").getStatusCode().value())
			.isEqualTo(200);
		assertThat(patch(userCardId(CHECK), "{\"status\":\"UNMAPPED\"}").getStatusCode().value()).isEqualTo(200);
		assertThat(patch(userCardId(TOKTOK), "{\"status\":\"USER_CONFIRMED\",\"cardProductId\":\"09174\","
				+ "\"confirmationMethod\":\"MANUAL_SELECTION\"}").getStatusCode().value()).isEqualTo(200);
		Map<String, Map<String, Object>> before = mappingsByName();

		ResponseEntity<String> resync = send("POST", SYNCS, null);

		assertThat(resync.getStatusCode().value()).isEqualTo(200);
		// newCandidates는 이번에 새로 만든 카드만 담는다
		assertThat(JsonPath.<List<Object>>read(resync.getBody(), "$.data.heldCards.newCandidates")).isEmpty();
		Map<String, Map<String, Object>> after = mappingsByName();
		assertThat(after.get(TITANIUM)).containsEntry("status", "CANDIDATE").containsEntry("candidateCardProductId", "09271");
		assertThat(after.get(NORI_WITH_ISSUER)).isEqualTo(before.get(NORI_WITH_ISSUER))
			.containsEntry("status", "NOT_SUPPORTED");
		assertThat(after.get(CHECK)).isEqualTo(before.get(CHECK))
			.containsEntry("status", "UNMAPPED")
			.containsEntry("candidateCardProductId", null);
		assertThat(after.get(TOKTOK)).isEqualTo(before.get(TOKTOK))
			.containsEntry("status", "USER_CONFIRMED")
			.containsEntry("cardProductId", "09174");
	}

	@Test
	void productAlreadyConfirmedByTheUserIsNotProposedAgain() {
		connect();
		assertThat(send("POST", CARDS, "{\"displayName\":\"" + MANUAL_MARK + "\",\"cardProductId\":\"09271\"}")
			.getStatusCode()
			.value()).isEqualTo(201);
		HELD.add(TITANIUM);

		ResponseEntity<String> sync = send("POST", SYNCS, null);

		// 후보를 두면 확인할 때 409(USER_CARD_DUPLICATED)가 되므로 UNMAPPED로 둔다
		assertThat(JsonPath.<String>read(sync.getBody(), "$.data.heldCards.newCandidates[0].mappingStatus"))
			.isEqualTo("UNMAPPED");
		assertThat(JsonPath.<Object>read(sync.getBody(), "$.data.heldCards.newCandidates[0].candidateCardProductId"))
			.isNull();
		assertThat(mappingsByName().get(TITANIUM)).containsEntry("status", "UNMAPPED")
			.containsEntry("candidateCardProductId", null);

		// 재동기화에서도 다시 두지 않는다
		send("POST", SYNCS, null);
		assertThat(mappingsByName().get(TITANIUM)).containsEntry("status", "UNMAPPED");
	}

	private void connect() {
		int status = send("POST", "/api/v1/me/codef/connection",
				"{\"organization\":\"0301\",\"loginType\":\"ID\",\"loginId\":\"kbuser01\",\"password\":\"pw\"}")
			.getStatusCode()
			.value();
		assertThat(status).isIn(200, 201);
	}

	private Map<String, Map<String, Object>> mappingsByName() {
		List<Map<String, Object>> items = JsonPath.read(send("GET", CARDS, null).getBody(),
				"$.data.items[?(@.holdingSource == 'CODEF')]");
		return items.stream()
			.collect(Collectors.toMap(item -> (String) item.get("displayName"),
					item -> mappingOf(item)));
	}

	private String eligibilityReason(String displayName) {
		List<Object> reasons = JsonPath.read(send("GET", CARDS, null).getBody(),
				"$.data.items[?(@.displayName == '" + displayName + "')].recommendationEligibility.reason");
		return (String) reasons.getFirst();
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> mappingOf(Map<String, Object> item) {
		return (Map<String, Object>) item.get("mapping");
	}

	private String userCardId(String cardName) {
		return "uc-" + this.jdbcTemplate.queryForObject("SELECT user_card_id FROM codef_held_cards WHERE card_name = ?",
				Long.class, cardName);
	}

	private ResponseEntity<String> patch(String userCardId, String body) {
		return send("PATCH", CARDS + "/" + userCardId + "/mapping", body);
	}

	private int count(String sql) {
		return this.jdbcTemplate.queryForObject(sql, Integer.class);
	}

	private ResponseEntity<String> send(String method, String path, String body) {
		RestClient client = RestClient.builder()
			.baseUrl("http://localhost:" + this.port)
			.defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
			})
			.build();
		RestClient.RequestBodySpec spec = client.method(HttpMethod.valueOf(method))
			.uri(path)
			.header("X-BeONE-Demo-User", "MY_CARDS");
		if (body != null) {
			spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
		}
		return spec.retrieve().toEntity(String.class);
	}

	/** 보유카드만 {@link #HELD}로 답하고 승인·실적은 가짜 게이트웨이 fixture를 쓰는 합성 게이트웨이와 합성 키. */
	@TestConfiguration(proxyBeanMethods = false)
	static class SyntheticConfiguration {

		@Bean
		@Primary
		CodefSyncGateway heldCardsFromTest() {
			FakeCodefGateway fake = new FakeCodefGateway(Clock.fixed(Instant.parse("2026-11-26T00:00:00Z"),
					ZoneOffset.UTC));
			return new CodefSyncGateway() {

				@Override
				public CodefCallResult<CodefHeldCard> heldCards(String organization, String connectedId) {
					String data = HELD.stream()
						.map(name -> "{\"resCardName\":\"" + name + "\",\"resSleepYN\":\"N\"}")
						.collect(Collectors.joining(","));
					return new CodefCallResult.Answered<>(CodefProductResponseReader.readHeldCards(200,
							"{\"result\":{\"code\":\"CF-00000\",\"transactionId\":\"FAKE-TX-HELD\"},\"data\":[" + data
									+ "]}"));
				}

				@Override
				public CodefCallResult<CodefApproval> approvals(String organization, String connectedId,
						LocalDate from, LocalDate to) {
					return fake.approvals(organization, "unknown-connected-id", from, to);
				}

				@Override
				public CodefCallResult<CodefCardPerformance> performances(String organization, String connectedId) {
					return fake.performances(organization, "unknown-connected-id");
				}

			};
		}

		@Bean
		@Primary
		ConnectedIdKeySource syntheticConnectedIdKey() {
			return () -> Optional.of(new byte[32]);
		}

		@Bean
		@Primary
		ApprovalFingerprintKeySource syntheticApprovalKey() {
			byte[] key = new byte[32];
			for (int i = 0; i < key.length; i++) {
				key[i] = (byte) (i + 1);
			}
			return () -> Optional.of(key.clone());
		}

	}

}
