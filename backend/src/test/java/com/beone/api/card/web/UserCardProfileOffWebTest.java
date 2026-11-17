package com.beone.api.card.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.beone.api.support.WebSliceTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * local·demo 밖(프로필 없음)에서는 현재 사용자·보유 카드 API가 등록되지 않아 404 {@code NOT_FOUND}다(시연 사용자 API와 같음).
 */
@WebSliceTest(controllers = { UserCardController.class, CurrentUserController.class })
class UserCardProfileOffWebTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ApplicationContext context;

	@Test
	void userCardApisAreNotFoundOutsideLocalAndDemo() throws Exception {
		List<MockHttpServletRequestBuilder> requests = List.of(get("/api/v1/me"), get("/api/v1/me/cards"),
				post("/api/v1/me/cards").contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"카드\"}"),
				patch("/api/v1/me/cards/uc-1/mapping").contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\":\"UNMAPPED\"}"),
				patch("/api/v1/me/cards/uc-1").contentType(MediaType.APPLICATION_JSON)
					.content("{\"displayName\":\"카드\"}"),
				delete("/api/v1/me/cards/uc-1"));
		for (MockHttpServletRequestBuilder request : requests) {
			MvcResult result = this.mockMvc.perform(request.header("X-BeONE-Demo-User", "MY_CARDS")).andReturn();

			assertThat(result.getResponse().getStatus()).isEqualTo(404);
			JsonAssert.comparator(JsonCompareMode.STRICT)
				.assertIsMatch("{\"code\":\"NOT_FOUND\",\"message\":\"요청한 리소스를 찾을 수 없습니다.\",\"errors\":[]}",
						result.getResponse().getContentAsString(StandardCharsets.UTF_8));
		}
	}

	@Test
	void userCardControllersAreNotRegistered() {
		assertThat(this.context.getBeanNamesForType(UserCardController.class)).isEmpty();
		assertThat(this.context.getBeanNamesForType(CurrentUserController.class)).isEmpty();
	}

}
