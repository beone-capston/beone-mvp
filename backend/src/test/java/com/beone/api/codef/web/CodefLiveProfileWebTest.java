package com.beone.api.codef.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.beone.api.auth.application.AuthSessionService;
import com.beone.api.auth.application.MemberRequestCheck;
import com.beone.api.codef.application.CodefConnectionService;
import com.beone.api.codef.application.CodefSyncService;
import com.beone.api.support.WebSliceTest;
import com.beone.api.user.application.MemberUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * {@code member,codef-live}에서 CODEF 경로 4개가 회원 세션 규칙 아래에 등록됨을 확인한다(LP-1). 세션이 없으면 404가 아니라 401이고
 * 서비스는 불리지 않는다. 회원 세션 판정({@link AuthSessionService})은 슬라이스 밖이라 "세션 없음"을 돌려주게 두고, 경로가 회원 인터셉터를
 * 거치는지만 본다. 실제 세션·CSRF·소유권을 포함한 흐름은 회원 통합 시험({@code CodefLiveMemberIntegrationTest})이 본다.
 */
@WebSliceTest(controllers = { CodefConnectionController.class, CodefSyncController.class })
@ActiveProfiles({ "member", "codef-live" })
class CodefLiveProfileWebTest {

	private static final String UNAUTHENTICATED = "{\"code\":\"UNAUTHENTICATED\",\"message\":\"로그인이 필요합니다.\",\"errors\":[]}";

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	MemberUserService memberUserService;

	@MockitoBean
	AuthSessionService authSessionService;

	@MockitoBean
	CodefConnectionService codefConnectionService;

	@MockitoBean
	CodefSyncService codefSyncService;

	@Test
	void allFourPathsRequireAMemberSession() throws Exception {
		given(this.authSessionService.checkMemberRequest(isNull(), isNull(), anyBoolean()))
			.willReturn(new MemberRequestCheck.Unauthenticated());
		List<MockHttpServletRequestBuilder> requests = List.of(get("/api/v1/me/codef/connection"),
				post("/api/v1/me/codef/connection").contentType(MediaType.APPLICATION_JSON)
					.content("{\"organization\":\"0301\",\"loginType\":\"ID\",\"loginId\":\"a\",\"password\":\"b\"}"),
				delete("/api/v1/me/codef/connection"),
				post("/api/v1/me/codef/syncs").contentType(MediaType.APPLICATION_JSON).content("{}"));
		for (MockHttpServletRequestBuilder request : requests) {
			MvcResult result = this.mockMvc.perform(request).andReturn();

			assertThat(result.getResponse().getStatus()).isEqualTo(401);
			JsonAssert.comparator(JsonCompareMode.STRICT)
				.assertIsMatch(UNAUTHENTICATED, result.getResponse().getContentAsString(StandardCharsets.UTF_8));
			assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
		}
		verifyNoInteractions(this.codefConnectionService, this.codefSyncService);
	}

}
