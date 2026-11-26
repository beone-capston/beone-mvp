package com.beone.api.recommendation.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
 * local·demo·member 밖(프로필 없음)에서는 추천 API가 등록되지 않아 404 {@code NOT_FOUND}다(다른 사용자 자원 API와 같음).
 */
@WebSliceTest(controllers = RecommendationController.class)
class RecommendationProfileOffWebTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ApplicationContext context;

	@Test
	void recommendationApisAreNotFoundOutsideLocalAndDemo() throws Exception {
		List<MockHttpServletRequestBuilder> requests = List.of(post("/api/v1/me/orders/ord-5001/recommendations"),
				get("/api/v1/me/recommendations/rec-3001"));
		for (MockHttpServletRequestBuilder request : requests) {
			MvcResult result = this.mockMvc.perform(request.header("X-BeONE-Demo-User", "MY_CARDS")).andReturn();

			assertThat(result.getResponse().getStatus()).isEqualTo(404);
			JsonAssert.comparator(JsonCompareMode.STRICT)
				.assertIsMatch("{\"code\":\"NOT_FOUND\",\"message\":\"요청한 리소스를 찾을 수 없습니다.\",\"errors\":[]}",
						result.getResponse().getContentAsString(StandardCharsets.UTF_8));
		}
	}

	@Test
	void recommendationControllerIsNotRegistered() {
		assertThat(this.context.getBeanNamesForType(RecommendationController.class)).isEmpty();
	}

}
