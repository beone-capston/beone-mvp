package com.beone.api.card;

import static org.assertj.core.api.Assertions.assertThat;

import com.beone.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * 전체 애플리케이션(Testcontainers MySQL, Flyway 적용·ddl-auto: validate)에서 카드 상품 API를 실제 HTTP로 확인한다.
 *
 * <p>카드 상품은 테이블이 없고 사용자 자원이 아니므로 저장·사용자별 분리 시험은 없다. 서버가 뜬다는 사실로 표시 정보 표와
 * 규칙 카탈로그의 카드 ID 일치 확인(결정 27 A1-2)이 통과했음을 함께 확인한다.
 */
@IntegrationTest
@ActiveProfiles("local")
class CardProductIntegrationTest {

	@LocalServerPort
	int port;

	@Test
	void listReturnsFourRuleCardsInIdOrderWithoutHeader() {
		String body = client().get().uri("/api/v1/card-products?asOf=2026-11-03").retrieve().body(String.class);

		JsonAssert.comparator(JsonCompareMode.LENIENT).assertIsMatch("""
				{"data":{"asOf":"2026-11-03","items":[
				  {"cardProductId":"01664","ruleStatus":"VERIFIED"},
				  {"cardProductId":"01914","ruleStatus":"VERIFIED"},
				  {"cardProductId":"09174","ruleStatus":"VERIFIED"},
				  {"cardProductId":"09271","ruleStatus":"VERIFIED"}]}}
				""", body);
		assertThat(body).contains("\"items\":[{").doesNotContain("\"cardProductId\":1664");
	}

	@Test
	void unknownProductIsNotFoundOverHttp() {
		try {
			client().get().uri("/api/v1/card-products/1664").retrieve().body(String.class);
			throw new AssertionError("404가 아님");
		}
		catch (HttpClientErrorException.NotFound ex) {
			JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch(
					"{\"code\":\"CARD_PRODUCT_NOT_FOUND\",\"message\":\"카드 상품을 찾을 수 없습니다.\",\"errors\":[]}",
					ex.getResponseBodyAsString());
		}
	}

	private RestClient client() {
		return RestClient.create("http://localhost:" + this.port);
	}

}
