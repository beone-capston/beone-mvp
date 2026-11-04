package com.beone.api.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import javax.sql.DataSource;

import com.beone.api.support.WebSliceTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 다른 테스트의 전용 컨트롤러가 섞이지 않고, 웹 슬라이스가 DB 설정 없이 뜨는지 확인한다.
 */
@WebSliceTest
class WebSliceIsolationWebTest {

	@Autowired
	ApplicationContext context;

	@Autowired
	MockMvc mockMvc;

	@Test
	void testControllersFromOtherTestsAreNotRegistered() throws Exception {
		assertThat(this.context.getBeanNamesForType(GlobalExceptionHandlerWebTest.ContractTestController.class))
			.isEmpty();
		assertThat(this.context.getBeanNamesForType(GlobalExceptionHandlerWebTest.FeatureExceptionHandler.class))
			.isEmpty();
		assertThat(this.mockMvc.perform(get("/test/api/success")).andReturn().getResponse().getStatus())
			.isEqualTo(404);
	}

	@Test
	void sliceHasCommonHandlerButNoDatabase() {
		assertThat(this.context.getBeanNamesForType(GlobalExceptionHandler.class)).hasSize(1);
		assertThat(this.context.getBeanNamesForType(DataSource.class)).isEmpty();
	}

}
