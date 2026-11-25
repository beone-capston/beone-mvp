package com.beone.api.ledger.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.beone.api.support.WebSliceTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * local·demo·member 밖(프로필 없음)에서는 원장 조회 API가 등록되지 않아 404 {@code NOT_FOUND}다(다른 사용자 자원 API와 같음).
 */
@WebSliceTest(controllers = LedgerTransactionController.class)
class LedgerTransactionProfileOffWebTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ApplicationContext context;

	@Test
	void ledgerApiIsNotFoundOutsideLocalAndDemo() throws Exception {
		List<MockHttpServletRequestBuilder> requests = List.of(
				get("/api/v1/me/ledger-transactions").param("month", "2026-11"));
		for (MockHttpServletRequestBuilder request : requests) {
			MvcResult result = this.mockMvc.perform(request.header("X-BeONE-Demo-User", "MY_CARDS")).andReturn();

			assertThat(result.getResponse().getStatus()).isEqualTo(404);
			JsonAssert.comparator(JsonCompareMode.STRICT)
				.assertIsMatch("{\"code\":\"NOT_FOUND\",\"message\":\"요청한 리소스를 찾을 수 없습니다.\",\"errors\":[]}",
						result.getResponse().getContentAsString(StandardCharsets.UTF_8));
		}
	}

	@Test
	void ledgerControllerIsNotRegistered() {
		assertThat(this.context.getBeanNamesForType(LedgerTransactionController.class)).isEmpty();
	}

}
