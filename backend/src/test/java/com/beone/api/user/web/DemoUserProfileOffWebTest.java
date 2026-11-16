package com.beone.api.user.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

import java.nio.charset.StandardCharsets;

import com.beone.api.common.web.LocalDemoCorsConfiguration;
import com.beone.api.support.WebSliceTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * local·demo 밖(프로필 없음)에서는 시연 전환과 CORS 허용이 꺼진다(docs/api/cards.md: 다른 환경은 404).
 */
@WebSliceTest
class DemoUserProfileOffWebTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ApplicationContext context;

	@Test
	void demoUsersIsNotFoundOutsideLocalAndDemo() throws Exception {
		MvcResult result = this.mockMvc.perform(get("/api/v1/demo-users")).andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(404);
		assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
		JsonAssert.comparator(JsonCompareMode.STRICT)
			.assertIsMatch("{\"code\":\"NOT_FOUND\",\"message\":\"요청한 리소스를 찾을 수 없습니다.\",\"errors\":[]}",
					result.getResponse().getContentAsString(StandardCharsets.UTF_8));
	}

	@Test
	void demoSwitchingBeansAreNotRegistered() {
		assertThat(this.context.getBeanNamesForType(DemoUserController.class)).isEmpty();
		assertThat(this.context.getBeanNamesForType(CurrentUserResolver.class)).isEmpty();
		assertThat(this.context.getBeanNamesForType(UserWebConfiguration.class)).isEmpty();
		assertThat(this.context.getBeanNamesForType(LocalDemoCorsConfiguration.class)).isEmpty();
	}

	@Test
	void preflightFromFrontOriginGetsNoAllowOrigin() throws Exception {
		MvcResult result = this.mockMvc.perform(options("/api/v1/demo-users").header(HttpHeaders.ORIGIN, "http://localhost:5173")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-BeONE-Demo-User")).andReturn();

		assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
	}

}
