package com.beone.api.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;

import com.beone.api.support.WebSliceTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * local·demo 밖(프로필 없음)에서는 인증 API가 등록되지 않아 404 {@code NOT_FOUND}이고 세션 쿠키도 심지 않는다(관리자 승인, 결정 30).
 */
@WebSliceTest(controllers = { AuthCsrfController.class, MemberRegistrationController.class,
		MemberSessionController.class })
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
	void registrationApisAreNotFoundOutsideLocalAndDemo() throws Exception {
		for (String path : new String[] { "/api/v1/auth/sign-up", "/api/v1/auth/email-verification-requests",
				"/api/v1/auth/email-verifications" }) {
			MvcResult result = this.mockMvc
				.perform(post(path)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{}"))
				.andReturn();
			assertThat(result.getResponse().getStatus()).as(path).isEqualTo(404);
		}
	}

	@Test
	void sessionApisAreNotFoundOutsideLocalAndDemo() throws Exception {
		for (String path : new String[] { "/api/v1/auth/login", "/api/v1/auth/logout" }) {
			MvcResult result = this.mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andReturn();
			assertThat(result.getResponse().getStatus()).as(path).isEqualTo(404);
			assertThat(result.getResponse().getHeaders("Set-Cookie")).as(path).isEmpty();
		}
		MvcResult session = this.mockMvc.perform(get("/api/v1/auth/session")).andReturn();
		assertThat(session.getResponse().getStatus()).isEqualTo(404);
	}

	@Test
	void authBeansAreNotRegistered() {
		assertThat(this.context.getBeanNamesForType(AuthCsrfController.class)).isEmpty();
		assertThat(this.context.getBeanNamesForType(MemberRegistrationController.class)).isEmpty();
		assertThat(this.context.getBeanNamesForType(MemberSessionController.class)).isEmpty();
		assertThat(this.context.getBeanNamesForType(AuthWebConfiguration.class)).isEmpty();
	}

}
