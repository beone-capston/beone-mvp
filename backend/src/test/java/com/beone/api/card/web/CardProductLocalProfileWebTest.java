package com.beone.api.card.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.beone.api.benefit.rule.loading.RuleCatalogConfiguration;
import com.beone.api.card.application.CardProductService;
import com.beone.api.support.WebSliceTest;
import com.beone.api.user.application.DemoUserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * local 프로필에서도 카드 상품 API가 시연 사용자 헤더 없이 동작하고, 잘못된 헤더 값을 사용자 판정에 쓰지 않는지
 * 확인한다(docs/api.md 시연 사용자: 상품 조회는 헤더 불필요).
 */
@WebSliceTest(controllers = CardProductController.class)
@ActiveProfiles("local")
@Import({ RuleCatalogConfiguration.class, CardProductService.class })
class CardProductLocalProfileWebTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	DemoUserService demoUserService;

	@Test
	void productsNeedNoDemoUserHeader() throws Exception {
		MvcResult withoutHeader = this.mockMvc.perform(get("/api/v1/card-products").param("asOf", "2026-11-03"))
			.andReturn();
		MvcResult withInvalidHeader = this.mockMvc
			.perform(get("/api/v1/card-products/01914").param("asOf", "2026-11-03").header("X-BeONE-Demo-User", "bad"))
			.andReturn();

		assertThat(withoutHeader.getResponse().getStatus()).isEqualTo(200);
		assertThat(withInvalidHeader.getResponse().getStatus()).isEqualTo(200);
		verify(this.demoUserService, never()).currentUserOf(ArgumentMatchers.any());
	}

}
