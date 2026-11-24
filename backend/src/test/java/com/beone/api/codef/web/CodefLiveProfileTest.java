package com.beone.api.codef.web;

import java.util.Map;

import com.beone.api.codef.application.CodefConnectionService;
import com.beone.api.codef.application.CodefGateway;
import com.beone.api.codef.application.CodefSyncGateway;
import com.beone.api.codef.application.CodefSyncService;
import com.beone.api.codef.application.FakeCodefGateway;
import com.beone.api.codef.application.LiveCodefGateway;
import com.beone.api.codef.transport.CodefApiKeys;
import com.beone.api.codef.transport.CodefClient;
import com.beone.api.codef.transport.CodefTransportSettings;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * CODEF 경로·게이트웨이의 프로필 조합을 고정한다(LP-1, 결정 38-8). 실 게이트웨이와 회원 CODEF 경로는 {@code member & codef-live & !prod}
 * 에서만 등록하고, local·demo는 가짜 게이트웨이 그대로다. prod와 codef-live 조합의 기동 실패는 {@code PublicProfileGuardTest}가 본다.
 *
 * <p>{@link ApplicationContextRunner}는 프로필 그룹을 펼치지 않으므로 prod는 member를 함께 적어 펼친 상태를 흉내 낸다. 전송 계층은 키 없는
 * 클라이언트라 어떤 요청도 보내지 않는다.
 */
class CodefLiveProfileTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withBean(CodefClient.class,
				() -> new CodefClient(CodefApiKeys.fromEnvironment(Map.of()), CodefTransportSettings.defaults()))
		.withBean(CodefConnectionService.class, () -> mock(CodefConnectionService.class))
		.withBean(CodefSyncService.class, () -> mock(CodefSyncService.class))
		.withUserConfiguration(LiveCodefGateway.class, FakeCodefGateway.class, CodefConnectionController.class,
				CodefSyncController.class);

	@ParameterizedTest
	@ValueSource(strings = { "member,codef-live", "codef-live,member" })
	void memberWithCodefLiveRegistersLiveGatewayAndRoutes(String profiles) {
		this.runner.withPropertyValues("spring.profiles.active=" + profiles).run(context -> {
			assertThat(context).hasSingleBean(LiveCodefGateway.class)
				.doesNotHaveBean(FakeCodefGateway.class)
				.hasSingleBean(CodefConnectionController.class)
				.hasSingleBean(CodefSyncController.class);
			assertThat(context.getBean(CodefGateway.class)).isInstanceOf(LiveCodefGateway.class);
			assertThat(context.getBean(CodefSyncGateway.class)).isInstanceOf(LiveCodefGateway.class);
		});
	}

	@ParameterizedTest
	@ValueSource(strings = { "member", "codef-live", "prod,member,codef-live", "prod,member" })
	void withoutTheFullConditionNothingCodefIsRegistered(String profiles) {
		this.runner.withPropertyValues("spring.profiles.active=" + profiles).run(context -> {
			assertThat(context).doesNotHaveBean(LiveCodefGateway.class)
				.doesNotHaveBean(FakeCodefGateway.class)
				.doesNotHaveBean(CodefConnectionController.class)
				.doesNotHaveBean(CodefSyncController.class);
		});
	}

	@ParameterizedTest
	@ValueSource(strings = { "local", "demo", "local,codef-live" })
	void localAndDemoKeepTheFakeGateway(String profiles) {
		this.runner.withPropertyValues("spring.profiles.active=" + profiles).run(context -> {
			assertThat(context).hasSingleBean(FakeCodefGateway.class)
				.doesNotHaveBean(LiveCodefGateway.class)
				.hasSingleBean(CodefConnectionController.class)
				.hasSingleBean(CodefSyncController.class);
		});
	}

}
