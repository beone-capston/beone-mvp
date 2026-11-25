package com.beone.api.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

import java.nio.charset.StandardCharsets;

import com.beone.api.auth.application.AuthMailSender;
import com.beone.api.auth.application.AuthSessionService;
import com.beone.api.auth.application.FakeAuthMailSender;
import com.beone.api.auth.application.MemberRequestCheck;
import com.beone.api.auth.application.SmtpAuthMailSender;
import com.beone.api.auth.application.SmtpMailConfiguration;
import com.beone.api.auth.application.SmtpStubServer;
import com.beone.api.card.application.UserCardService;
import com.beone.api.card.web.CurrentUserController;
import com.beone.api.support.WebSliceTest;
import com.beone.api.user.application.MemberUserService;
import com.beone.api.user.web.CurrentUserResolver;
import com.beone.api.user.web.DemoUserController;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 공개 프로필 {@code prod}의 웹 계층에서 시연 부품과 가짜 메일 발송기가 빠지는지 MockMvc로 확인한다(결정 38-6, G5 L2).
 *
 * <p>{@code prod}는 프로필 그룹으로 {@code member}를 함께 켠다. {@code @ActiveProfiles}는 그룹을 펼치지 않으므로 두 프로필을 직접 켠다.
 * 메일 발송기는 실제 prod 구성({@link SmtpMailConfiguration}, 결정 39-6·#207)을 그대로 올리고 받는 쪽만 루프백 스텁 SMTP로 둔다. 세션 판정은
 * 대역이고, 실제 HTTP·MySQL 확인은 {@code PublicProfileIntegrationTest}가 한다. CODEF 경로와 전달 헤더는 다른 테스트(#187, L4·L5)가 맡아
 * 여기서 다시 보지 않는다.
 */
@WebSliceTest(controllers = { DemoUserController.class, CurrentUserController.class })
@ActiveProfiles({ "prod", "member" })
@Import(SmtpMailConfiguration.class)
@ImportAutoConfiguration(MailSenderAutoConfiguration.class)
@TestPropertySource(properties = { "spring.mail.host=127.0.0.1", "spring.mail.username=synthetic-user",
		"spring.mail.password=synthetic-app-password", "beone.mail.from=no-reply@beone.example.test",
		"beone.mail.link-base-url=https://beone.example.test" })
class PublicProfileWebTest {

	private static final SmtpStubServer SMTP = new SmtpStubServer();

	private static final String UNAUTHENTICATED = "{\"code\":\"UNAUTHENTICATED\",\"message\":\"로그인이 필요합니다.\",\"errors\":[]}";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ApplicationContext context;

	@MockitoBean
	AuthSessionService authSessionService;

	// member 프로필의 현재 사용자 해석기가 슬라이스에 함께 올라오므로 그 의존성만 대신한다.
	@MockitoBean
	MemberUserService memberUserService;

	@MockitoBean
	UserCardService userCardService;

	@DynamicPropertySource
	static void smtpPort(DynamicPropertyRegistry registry) {
		registry.add("spring.mail.port", SMTP::port);
	}

	@AfterAll
	static void stopSmtp() {
		SMTP.close();
	}

	@Test
	void demoPartsAndFakeMailSenderAreNotRegistered() {
		assertThat(this.context.getBeanNamesForType(CurrentUserResolver.class)).isEmpty();
		assertThat(this.context.getBeanNamesForType(DemoUserController.class)).isEmpty();
		assertThat(this.context.getBeanNamesForType(LocalDemoCorsConfiguration.class)).isEmpty();
		assertThat(this.context.getBeanNamesForType(FakeAuthMailSender.class)).isEmpty();
		assertThat(this.context.getBean(AuthMailSender.class)).isInstanceOf(SmtpAuthMailSender.class);
		assertThat(this.context.getBeanNamesForType(CurrentUserController.class)).hasSize(1);
	}

	@Test
	void demoUserListIsNotFound() throws Exception {
		MvcResult result = this.mockMvc.perform(get("/api/v1/demo-users")).andReturn();

		assertBody(result, 404, "{\"code\":\"NOT_FOUND\",\"message\":\"요청한 리소스를 찾을 수 없습니다.\",\"errors\":[]}");
	}

	@Test
	void demoHeaderAloneIsUnauthenticated() throws Exception {
		given(this.authSessionService.checkMemberRequest(isNull(), isNull(), anyBoolean()))
			.willReturn(new MemberRequestCheck.Unauthenticated());

		MvcResult result = this.mockMvc.perform(get("/api/v1/me").header("X-BeONE-Demo-User", "MY_CARDS")).andReturn();

		assertBody(result, 401, UNAUTHENTICATED);
		verifyNoInteractions(this.memberUserService, this.userCardService);
	}

	@Test
	void crossOriginPreflightGetsNoCorsHeaders() throws Exception {
		MvcResult result = this.mockMvc
			.perform(options("/api/v1/me").header(HttpHeaders.ORIGIN, "http://localhost:5173")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-CSRF-TOKEN"))
			.andReturn();

		assertThat(result.getResponse().getHeaderNames()).noneMatch(name -> name.startsWith("Access-Control-Allow-"));
	}

	private static void assertBody(MvcResult result, int status, String expected) throws Exception {
		assertThat(result.getResponse().getStatus()).isEqualTo(status);
		JsonAssert.comparator(JsonCompareMode.STRICT)
			.assertIsMatch(expected, result.getResponse().getContentAsString(StandardCharsets.UTF_8));
	}

}
