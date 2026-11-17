package com.beone.api.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;

import com.beone.api.support.WebSliceTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * local·demo 밖(프로필 없음)에서는 인증 API가 등록되지 않아 404 {@code NOT_FOUND}이고 세션 쿠키도 심지 않는다(관리자 승인, 결정 30).
 */
@WebSliceTest(controllers = AuthCsrfController.class)
class AuthCsrfProfileOffWebTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ApplicationContext context;

	@Test
	void csrfApiIsNotFoundOutsideLocalAndDemo() throws Exception {
		MvcResult result = this.mockMvc.perform(get("/api/v1/auth/csrf")).andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(404);
		JsonAssert.comparator(JsonCompareMode.STRICT)
			.assertIsMatch("{\"code\":\"NOT_FOUND\",\"message\":\"요청한 리소스를 찾을 수 없습니다.\",\"errors\":[]}",
					result.getResponse().getContentAsString(StandardCharsets.UTF_8));
		assertThat(result.getResponse().getHeaders("Set-Cookie")).isEmpty();
	}

	@Test
	void authBeansAreNotRegistered() {
		assertThat(this.context.getBeanNamesForType(AuthCsrfController.class)).isEmpty();
		assertThat(this.context.getBeanNamesForType(AuthWebConfiguration.class)).isEmpty();
	}

}
