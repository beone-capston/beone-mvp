package com.beone.api.codef.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.beone.api.codef.application.CodefGateway;
import com.beone.api.codef.application.CodefSyncGateway;
import com.beone.api.codef.application.FakeCodefGateway;
import com.beone.api.support.WebSliceTest;
import com.beone.api.user.application.MemberUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 공개 프로필 {@code prod}에서 CODEF 연결·조회 API와 가짜 게이트웨이가 등록되지 않음을 고정한다(결정 38-8, G5 L4).
 *
 * <p>공개 전환은 비공개 실연동 검증 뒤 별도 승인과 코드 PR로만 한다. 그때 이 테스트의 기대값을 새 계약에 맞춰 고친다.
 * {@link FakeCodefGateway}는 웹 슬라이스 스캔 대상이 아니므로 {@code @Import}로 직접 넣어 {@code @Profile} 조건이 prod에서 빠지는지 본다.
 */
@WebSliceTest(controllers = { CodefConnectionController.class, CodefSyncController.class })
@ActiveProfiles("prod")
@Import(FakeCodefGateway.class)
class CodefPublicProfileWebTest {

	private static final String NOT_FOUND = "{\"code\":\"NOT_FOUND\",\"message\":\"요청한 리소스를 찾을 수 없습니다.\",\"errors\":[]}";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ApplicationContext context;

	@Autowired
	Environment environment;

	// member 프로필의 현재 사용자 해석기(MemberCurrentUserResolver)가 슬라이스에 함께 올라오므로 그 의존성만 대신한다.
	@MockitoBean
	MemberUserService memberUserService;

	@Test
	void prodSliceExpandsToMemberWithoutLocalOrDemo() {
		// 프로필 그룹(prod: member)이 펼쳐진 상태에서 확인해야 member 경로 구성까지 포함한 공개 조건이 된다
		assertThat(this.environment.acceptsProfiles(Profiles.of("prod & member"))).isTrue();
		assertThat(this.environment.acceptsProfiles(Profiles.of("local | demo"))).isFalse();
	}

	@Test
	void allFourCodefPathsAreNotFound() throws Exception {
		List<MockHttpServletRequestBuilder> requests = List.of(get("/api/v1/me/codef/connection"),
				post("/api/v1/me/codef/connection").contentType(MediaType.APPLICATION_JSON)
					.content("{\"organization\":\"0301\",\"loginType\":\"ID\",\"loginId\":\"a\",\"password\":\"b\"}"),
				delete("/api/v1/me/codef/connection"),
				post("/api/v1/me/codef/syncs").contentType(MediaType.APPLICATION_JSON)
					.content("{\"from\":\"2026-11-01\",\"to\":\"2026-11-04\"}"));
		for (MockHttpServletRequestBuilder request : requests) {
			// 세션이 없어도 401이 아니라 404다. 회원 인터셉터는 컨트롤러가 정해진 요청만 검사한다
			MvcResult result = this.mockMvc.perform(request).andReturn();

			assertThat(result.getResponse().getStatus()).isEqualTo(404);
			JsonAssert.comparator(JsonCompareMode.STRICT)
				.assertIsMatch(NOT_FOUND, result.getResponse().getContentAsString(StandardCharsets.UTF_8));
			// member 업무 경로의 no-store 필터는 prod에도 걸린다
			assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
		}
	}

	@Test
	void codefControllersAreNotRegistered() {
		assertThat(this.context.getBeanNamesForType(CodefConnectionController.class)).isEmpty();
		assertThat(this.context.getBeanNamesForType(CodefSyncController.class)).isEmpty();
	}

	@Test
	void fakeGatewayIsNotRegistered() {
		assertThat(this.context.getBeanNamesForType(FakeCodefGateway.class)).isEmpty();
		assertThat(this.context.getBeanNamesForType(CodefGateway.class)).isEmpty();
		assertThat(this.context.getBeanNamesForType(CodefSyncGateway.class)).isEmpty();
	}

}
