package com.beone.api.codef.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.beone.api.codef.CodefApproval;
import com.beone.api.codef.CodefCardPerformance;
import com.beone.api.codef.CodefHeldCard;
import com.beone.api.codef.CodefProductResponseReader;
import com.beone.api.codef.transport.CodefCallResult;
import com.beone.api.support.IntegrationTest;
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
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.web.client.RestClient;

/**
 * 재조회 대조와 카드 대조를 {@code src/test/resources/codef/sync/}의 합성 fixture로 확인한다. 조회 게이트웨이만 테스트용으로 바꾸고 연결은 가짜
 * 게이트웨이로 한다.
 *
 * <p>보유카드는 {@code ledger-held-cards.json}(A 1장, B 2장, 발급사 붙은 C, 이름 없음), 승인내역은 1회차 {@code ledger-recheck-first.json}·2회차
 * {@code ledger-recheck-second.json}, 실적은 {@code ledger-performances.json}(A, B, 일치 없는 Z)이다. 기대값은 결정 20 R1·R2(카드명이 하나뿐인
 * 카드만 연결, 같은 키·같은 종류는 다시 넣지 않음, 정상→Type1은 같은 승인의 취소로 기록, 그 밖의 종류 변화는 불완전), 결정 22 #93, 관리자 판단
 * P8·Q1(같은 이름 2장은 처음엔 둘 다 만들고 다음엔 같은 카드를 정할 수 없어 건너뜀)·Q4·Q5에서 썼다.
 */
@IntegrationTest
@ActiveProfiles("local")
@Import(CodefSyncRecheckIntegrationTest.ScriptedConfiguration.class)
class CodefSyncRecheckIntegrationTest {

	private static final String SYNCS = "/api/v1/me/codef/syncs";

	private static final AtomicReference<String> APPROVALS = new AtomicReference<>("ledger-recheck-first.json");

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void clearRows() {
		APPROVALS.set("ledger-recheck-first.json");
		this.jdbcTemplate.update("DELETE FROM codef_approval_rows");
		this.jdbcTemplate.update("DELETE FROM codef_performance_references");
		this.jdbcTemplate.update("DELETE FROM codef_held_cards");
		this.jdbcTemplate.update("DELETE FROM codef_syncs");
		this.jdbcTemplate.update("DELETE FROM user_cards WHERE holding_source = 'CODEF'");
		this.jdbcTemplate.update("DELETE FROM codef_connections");
	}

	@Test
	void secondSyncRecordsFullCancellationAndRejectsContradictions() {
		assertThat(send("POST", "/api/v1/me/codef/connection",
				"{\"organization\":\"0301\",\"loginType\":\"ID\",\"loginId\":\"kbuser01\",\"password\":\"pw\"}")
			.getStatusCode()
			.value()).isEqualTo(201);

		ResponseEntity<String> first = send("POST", SYNCS, null);

		long cardA = cardIds("SYN CARD A")[0];
		long[] cardB = cardIds("SYN CARD B");
		long cardC = cardIds("SYN CARD C|SYN ISSUER")[0];
		assertJson(first, """
				{"data":{"syncId":"sync-%1$d","syncedAt":"2026-11-04T09:00:00+09:00",
				 "heldCards":{"fetched":5,"newCandidates":[
				  {"userCardId":"uc-%2$d","cardName":"SYN CARD A","candidateCardProductId":null,"mappingStatus":"UNMAPPED"},
				  {"userCardId":"uc-%3$d","cardName":"SYN CARD B","candidateCardProductId":null,"mappingStatus":"UNMAPPED"},
				  {"userCardId":"uc-%4$d","cardName":"SYN CARD B","candidateCardProductId":null,"mappingStatus":"UNMAPPED"},
				  {"userCardId":"uc-%5$d","cardName":"SYN CARD C|SYN ISSUER","candidateCardProductId":null,
				   "mappingStatus":"UNMAPPED"}]},
				 "approvals":{"imported":4,"skippedDuplicates":0,"incompleteRows":[]},
				 "performances":[
				  {"userCardId":"uc-%2$d","label":"카드사 조회 실적(참고)","period":{"from":"2026-11-01","to":"2026-11-15"},
				   "currentUseWon":120000,"fetchedAt":"2026-11-04T09:00:00+09:00","usedInCalculation":false},
				  {"userCardId":null,"label":"카드사 조회 실적(참고)","period":{"from":"2026-11-01","to":"2026-11-15"},
				   "currentUseWon":50000,"fetchedAt":"2026-11-04T09:00:00+09:00","usedInCalculation":false},
				  {"userCardId":null,"label":"카드사 조회 실적(참고)","period":{"from":"2026-11-01","to":"2026-11-15"},
				   "currentUseWon":70000,"fetchedAt":"2026-11-04T09:00:00+09:00","usedInCalculation":false}],
				 "partialFailures":[]}}
				""".formatted(syncId(), cardA, cardB[0], cardB[1], cardC), 200);
		assertThat(kinds()).containsExactly(Map.of("signed_amount", 12000L, "row_kind", "APPROVAL"),
				Map.of("signed_amount", 13000L, "row_kind", "APPROVAL"),
				Map.of("signed_amount", 14000L, "row_kind", "APPROVAL_FULLY_CANCELED"),
				Map.of("signed_amount", 15000L, "row_kind", "APPROVAL"));

		APPROVALS.set("ledger-recheck-second.json");
		ResponseEntity<String> second = send("POST", SYNCS, null);

		// 같은 이름 B 2장은 이제 같은 카드를 정할 수 없어 새로 만들지 않는다(Q1). R31은 전액 취소로 갱신, R32는 이미 기록, R33은 Type1→정상 모순,
		// R34는 취소금액 불일치, R35는 새 행
		assertJson(second, """
				{"data":{"syncId":"sync-%1$d","syncedAt":"2026-11-04T09:00:00+09:00",
				 "heldCards":{"fetched":5,"newCandidates":[]},
				 "approvals":{"imported":2,"skippedDuplicates":1,"incompleteRows":[
				  {"userCardId":"uc-%2$d","approvalNoPresent":true,"reason":"UNDETERMINED_ROW"},
				  {"userCardId":"uc-%2$d","approvalNoPresent":true,"reason":"PARTIAL_CANCELLATION_SUSPECTED"}]},
				 "performances":[
				  {"userCardId":"uc-%2$d","label":"카드사 조회 실적(참고)","period":{"from":"2026-11-01","to":"2026-11-15"},
				   "currentUseWon":120000,"fetchedAt":"2026-11-04T09:00:00+09:00","usedInCalculation":false},
				  {"userCardId":null,"label":"카드사 조회 실적(참고)","period":{"from":"2026-11-01","to":"2026-11-15"},
				   "currentUseWon":50000,"fetchedAt":"2026-11-04T09:00:00+09:00","usedInCalculation":false},
				  {"userCardId":null,"label":"카드사 조회 실적(참고)","period":{"from":"2026-11-01","to":"2026-11-15"},
				   "currentUseWon":70000,"fetchedAt":"2026-11-04T09:00:00+09:00","usedInCalculation":false}],
				 "partialFailures":[]}}
				""".formatted(syncId(), cardA), 200);
		assertThat(kinds()).containsExactly(Map.of("signed_amount", 12000L, "row_kind", "APPROVAL_FULLY_CANCELED"),
				Map.of("signed_amount", 13000L, "row_kind", "APPROVAL"),
				Map.of("signed_amount", 14000L, "row_kind", "APPROVAL_FULLY_CANCELED"),
				Map.of("signed_amount", 15000L, "row_kind", "APPROVAL"),
				Map.of("signed_amount", 16000L, "row_kind", "APPROVAL"));
		assertThat(this.jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM codef_approval_rows WHERE kind_changed_at IS NOT NULL", Integer.class))
			.isEqualTo(1);
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_cards WHERE holding_source = 'CODEF'",
				Integer.class))
			.isEqualTo(4);
	}

	private java.util.List<Map<String, Object>> kinds() {
		return this.jdbcTemplate
			.queryForList("SELECT signed_amount, row_kind FROM codef_approval_rows ORDER BY used_date, used_time");
	}

	private long[] cardIds(String cardName) {
		return this.jdbcTemplate
			.queryForList("SELECT user_card_id FROM codef_held_cards WHERE card_name = ? ORDER BY user_card_id",
					Long.class, cardName)
			.stream()
			.mapToLong(Long::longValue)
			.toArray();
	}

	private long syncId() {
		return this.jdbcTemplate.queryForObject("SELECT MAX(sync_id) FROM codef_syncs", Long.class);
	}

	private static void assertJson(ResponseEntity<String> response, String expected, int status) {
		assertThat(response.getStatusCode().value()).isEqualTo(status);
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch(expected, response.getBody());
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

	private static String fixture(String name) {
		try (InputStream in = CodefSyncRecheckIntegrationTest.class.getResourceAsStream("/codef/sync/" + name)) {
			assertThat(in).as("fixture %s", name).isNotNull();
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** 고정 시계, 합성 키 두 개, 테스트 fixture를 돌려주는 조회 게이트웨이. */
	@TestConfiguration(proxyBeanMethods = false)
	static class ScriptedConfiguration {

		@Bean
		@Primary
		ConnectedIdKeySource syntheticConnectedIdKey() {
			return () -> Optional.of(new byte[32]);
		}

		@Bean
		@Primary
		ApprovalFingerprintKeySource syntheticApprovalKey() {
			return () -> Optional.of(new byte[32]);
		}

		@Bean
		Clock fixedClock() {
			return Clock.fixed(Instant.parse("2026-11-04T00:00:00Z"), ZoneOffset.UTC);
		}

		@Bean
		@Primary
		CodefSyncGateway scriptedSyncGateway() {
			return new CodefSyncGateway() {

				@Override
				public CodefCallResult<CodefHeldCard> heldCards(String organization, String connectedId) {
					return new CodefCallResult.Answered<>(
							CodefProductResponseReader.readHeldCards(200, fixture("ledger-held-cards.json")));
				}

				@Override
				public CodefCallResult<CodefApproval> approvals(String organization, String connectedId,
						LocalDate from, LocalDate to) {
					return new CodefCallResult.Answered<>(
							CodefProductResponseReader.readApprovals(200, fixture(APPROVALS.get())));
				}

				@Override
				public CodefCallResult<CodefCardPerformance> performances(String organization, String connectedId) {
					return new CodefCallResult.Answered<>(
							CodefProductResponseReader.readCardPerformances(200, fixture("ledger-performances.json")));
				}

			};
		}

	}

}
