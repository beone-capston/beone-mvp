package com.beone.api.codef.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
 * 승인·실적 행의 이름 사전 상품 단위 연결과 동기화 진단 로그(#270, 2026-11-27 사용자 결정)를 Testcontainers MySQL에서 동기화 API로 확인한다.
 *
 * <p>세 조회 대상 모두 테스트가 정하는 합성 응답이다. KB가 API마다 카드명을 다르게 주는 상황(보유카드 "nori 체크카드(RF)", 승인·실적 "KB국민 노리체크카드")을
 * 공개 상품명으로 재현한다. 승인번호·금액·일시는 합성 값이며 실사용자 응답 원문이 아니다. 실 CODEF는 부르지 않는다. API 응답의 필드·공개 사유 코드는 그대로다.
 */
@IntegrationTest
@ActiveProfiles("local")
@ExtendWith(OutputCaptureExtension.class)
@Import(CodefRowDictionaryLinkIntegrationTest.SyntheticConfiguration.class)
class CodefRowDictionaryLinkIntegrationTest {

	private static final String SYNCS = "/api/v1/me/codef/syncs";

	private static final String NORI = "nori 체크카드(RF)";

	private static final String YOUTH_HELD = "KB Youth Club 체크카드(RF)";

	private static final String TITANIUM = "올라운드 티타늄카드";

	private static final String NORI_ROW = "KB국민 노리체크카드";

	private static final String YOUTH_ROW = "KB Youth Club 체크카드";

	private static final String CHECK = "첵첵 체크카드";

	/** 표시 이름 규칙(1~30자)에 맞지 않아 동기화가 건너뛰는 합성 보유카드 이름(31자). */
	private static final String SKIPPED = "합성 건너뜀 카드 " + "가".repeat(21);

	/** 보유카드·실적 응답에 덧붙일 카드명. 테스트마다 비운다. */
	static final List<String> EXTRA = new CopyOnWriteArrayList<>();

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void clearRows() {
		EXTRA.clear();
		this.jdbcTemplate.update("DELETE FROM codef_approval_rows");
		this.jdbcTemplate.update("DELETE FROM codef_performance_references");
		this.jdbcTemplate.update("DELETE FROM codef_held_cards");
		this.jdbcTemplate.update("DELETE FROM codef_sync_incomplete_marks");
		this.jdbcTemplate.update("DELETE FROM codef_syncs");
		this.jdbcTemplate.update("DELETE FROM user_cards WHERE holding_source = 'CODEF'");
		this.jdbcTemplate.update("DELETE FROM codef_connections");
	}

	@Test
	void approvalAndPerformanceRowsLinkByDictionaryProductAndTheDiagnosticsLineIsLogged(CapturedOutput output) {
		connect();

		ResponseEntity<String> sync = send("POST", SYNCS, "{\"from\":\"2026-11-01\",\"to\":\"2026-11-20\"}");

		assertThat(sync.getStatusCode().value()).isEqualTo(200);
		String body = sync.getBody();
		String nori = userCardId(NORI);
		String titanium = userCardId(TITANIUM);
		// 승인: 정확 일치 1행과 사전 연결 1행이 같은 보유카드로 들어가고, 사전 불일치·같은 상품 보유카드 없음은 기존처럼 미연결
		assertThat(JsonPath.<Integer>read(body, "$.data.approvals.imported")).isEqualTo(2);
		List<Map<String, Object>> incomplete = JsonPath.read(body, "$.data.approvals.incompleteRows");
		assertThat(incomplete).extracting(row -> row.get("userCardId"), row -> row.get("reason"))
			.containsExactly(tuple(null, "UNDETERMINED_ROW"), tuple(null, "UNDETERMINED_ROW"));
		assertThat(this.jdbcTemplate.queryForList("SELECT CONCAT('uc-', user_card_id) FROM codef_approval_rows",
				String.class))
			.containsExactly(nori, nori);
		// 실적: 사전 연결(노리)과 정확 일치(티타늄)는 붙고, 지원 상품 밖 카드는 미연결
		List<Map<String, Object>> performances = JsonPath.read(body, "$.data.performances");
		assertThat(performances).extracting(item -> item.get("userCardId")).containsExactly(nori, null, titanium);

		String line = Arrays.stream(output.getOut().split("\\R"))
			.filter(text -> text.contains("codef sync diagnostics syncId="))
			.reduce((first, second) -> second)
			.orElseThrow();
		assertThat(line).contains("approvals={rows=4, imported=2, linkedByDictionary=1, "
				+ "incomplete={CARD_NOT_LINKED.NO_HELD_CARD_FOR_PRODUCT=1, CARD_NOT_LINKED.ROW_NAME_NOT_IN_DICTIONARY=1}, "
				+ "unlinkedCardNames={\"KB Youth Club 체크카드\"=1, \"첵첵 체크카드\"=1}}")
			.contains("performances={cards=3, linkedByDictionary=1, "
					+ "incomplete={CARD_NOT_LINKED.ROW_NAME_NOT_IN_DICTIONARY=1}, "
					+ "unlinkedCardNames={\"KB Youth Club 체크카드\"=1}}");
		// 승인번호·금액·일시·connectedId·로그인 아이디는 담지 않는다
		assertThat(line).doesNotContain("SYN-APR").doesNotContain("13579").doesNotContain("24680")
			.doesNotContain("20261110").doesNotContain("120000").doesNotContain("kbuser01").doesNotContain("101500");
	}

	@Test
	void performanceLinkedToASkippedHeldCardIsCountedAsSkippedInTheDiagnostics(CapturedOutput output) {
		// 건너뛴 보유카드와 같은 이름의 실적은 연결기가 이름을 주지만 보유 카드 ID가 없어 붙지 않는다(#272)
		assertThat(SKIPPED.codePointCount(0, SKIPPED.length())).isEqualTo(31);
		EXTRA.add(SKIPPED);
		connect();

		ResponseEntity<String> sync = send("POST", SYNCS, "{\"from\":\"2026-11-01\",\"to\":\"2026-11-20\"}");

		assertThat(sync.getStatusCode().value()).isEqualTo(200);
		List<Map<String, Object>> performances = JsonPath.read(sync.getBody(), "$.data.performances");
		assertThat(performances).extracting(item -> item.get("userCardId"))
			.containsExactly(userCardId(NORI), null, userCardId(TITANIUM), null);
		String line = Arrays.stream(output.getOut().split("\\R"))
			.filter(text -> text.contains("codef sync diagnostics syncId="))
			.reduce((first, second) -> second)
			.orElseThrow();
		assertThat(line).contains("performances={cards=4, linkedByDictionary=1, "
				+ "incomplete={CARD_NOT_LINKED.ROW_NAME_NOT_IN_DICTIONARY=1, HELD_CARD_SKIPPED=1}, "
				+ "unlinkedCardNames={\"KB Youth Club 체크카드\"=1, \"" + SKIPPED + "\"=1}}");
	}

	private void connect() {
		int status = send("POST", "/api/v1/me/codef/connection",
				"{\"organization\":\"0301\",\"loginType\":\"ID\",\"loginId\":\"kbuser01\",\"password\":\"pw\"}")
			.getStatusCode()
			.value();
		assertThat(status).isIn(200, 201);
	}

	private String userCardId(String cardName) {
		return "uc-" + this.jdbcTemplate.queryForObject("SELECT user_card_id FROM codef_held_cards WHERE card_name = ?",
				Long.class, cardName);
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

	private static String approval(String cardName, String time, String amount, String number) {
		return "{\"resUsedDate\":\"20261110\",\"resUsedTime\":\"" + time + "\",\"resCardName\":\"" + cardName
				+ "\",\"resUsedAmount\":\"" + amount + "\",\"resAccountCurrency\":\"KRW\",\"resHomeForeignType\":\"1\","
				+ "\"resCancelYN\":\"0\",\"resCancelAmount\":\"\",\"resApprovalNo\":\"" + number + "\"}";
	}

	private static String performance(String cardName) {
		return "{\"resCardName\":\"" + cardName + "\",\"resCardBenefitList\":{\"resCardPerformanceList\":"
				+ "{\"resStandardUseAmt\":\"300000\",\"resCurrentUseAmt\":\"120000\",\"commStartDate\":\"20261101\","
				+ "\"commEndDate\":\"20261115\"}}}";
	}

	private static String envelope(String transactionId, String data) {
		return "{\"result\":{\"code\":\"CF-00000\",\"transactionId\":\"" + transactionId + "\"},\"data\":[" + data
				+ "]}";
	}

	/** 세 조회 대상을 모두 합성 응답으로 답하는 게이트웨이와 합성 키. */
	@TestConfiguration(proxyBeanMethods = false)
	static class SyntheticConfiguration {

		@Bean
		@Primary
		CodefSyncGateway dictionaryNamesFromTest() {
			return new CodefSyncGateway() {

				@Override
				public CodefCallResult<CodefHeldCard> heldCards(String organization, String connectedId) {
					String data = String.join(",",
							Stream.concat(List.of(NORI, YOUTH_HELD, TITANIUM).stream(), EXTRA.stream())
								.map(name -> "{\"resCardName\":\"" + name + "\",\"resSleepYN\":\"N\"}")
								.toList());
					return new CodefCallResult.Answered<>(
							CodefProductResponseReader.readHeldCards(200, envelope("SYN-TX-HELD", data)));
				}

				@Override
				public CodefCallResult<CodefApproval> approvals(String organization, String connectedId,
						LocalDate from, LocalDate to) {
					String data = String.join(",", approval(NORI, "101500", "13579", "SYN-APR-I01"),
							approval(NORI_ROW, "111500", "24680", "SYN-APR-I02"),
							approval(YOUTH_ROW, "121500", "13579", "SYN-APR-I03"),
							approval(CHECK, "131500", "24680", "SYN-APR-I04"));
					return new CodefCallResult.Answered<>(
							CodefProductResponseReader.readApprovals(200, envelope("SYN-TX-APR", data)));
				}

				@Override
				public CodefCallResult<CodefCardPerformance> performances(String organization, String connectedId) {
					String data = Stream
						.concat(List.of(NORI_ROW, YOUTH_ROW, TITANIUM).stream(), EXTRA.stream())
						.map(CodefRowDictionaryLinkIntegrationTest::performance)
						.collect(Collectors.joining(","));
					return new CodefCallResult.Answered<>(
							CodefProductResponseReader.readCardPerformances(200, envelope("SYN-TX-PERF", data)));
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
