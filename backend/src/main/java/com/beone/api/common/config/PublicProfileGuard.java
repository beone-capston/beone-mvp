package com.beone.api.common.config;

import java.util.List;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * 공개 프로필 {@code prod}의 조합을 빈을 만들기 전에 검사한다(결정 38-6).
 *
 * <p>{@code prod}는 {@code application.yml}의 {@code spring.profiles.group.prod: member}로 회원 부품을 재사용한다.
 * 시연 부품({@code local}·{@code demo})과 함께 켜지거나, 그룹이 풀려 {@code member} 없이 켜지면 기동을 멈춘다.
 * {@code prod}와 {@code local}·{@code demo}를 함께 켜면 {@code CurrentUserModeGuard}도 걸리지만, 공개 프로필 문구가
 * 먼저 보이도록 순서를 앞에 둔다.
 *
 * <p>{@code prod}에 CODEF 키·토큰 환경 변수가 있어도 기동을 멈춘다(결정 38-8, LP-4). CODEF는 공개 {@code prod}에서 등록하지 않으며
 * 키는 비공개 검증 서버에만 둔다. 값은 읽지 않고 이름만 확인하며, 메시지에도 이름만 넣는다. 공개 전환이 승인되면 이 규칙을 걷어낸다.
 *
 * <p>이 가드는 공개 배포 가능 여부를 보증하지 않는다. 메일 설정 검사는 {@code SmtpMailConfiguration}(결정 39-6)이 따로 한다.
 */
@Component
public class PublicProfileGuard implements BeanFactoryPostProcessor, EnvironmentAware, Ordered {

	/**
	 * {@code prod}에 있으면 안 되는 CODEF 키·토큰 환경 변수 이름. CODEF 코드가 직접 읽는 이름과 같다({@code CodefApiKeys}의 클라이언트
	 * ID·비밀, {@code EnvironmentApprovalFingerprintKeySource}, {@code EnvironmentConnectedIdKeySource}, {@code CodefPasswordEncryptor}).
	 * CODEF 공개키는 비밀은 아니지만 CODEF 자격 정보이므로 함께 막는다(#193). {@code beone.codef.*}의 주소·시간 제한 설정은 비밀값이
	 * 아니므로 넣지 않는다.
	 */
	static final List<String> CODEF_SECRET_VARIABLES = List.of("CODEF_CLIENT_ID", "CODEF_CLIENT_SECRET",
			"BEONE_CODEF_APPROVAL_HMAC_KEY", "BEONE_CODEF_CONNECTED_ID_KEY", "CODEF_PUBLIC_KEY");

	private Environment environment;

	@Override
	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		check(this.environment);
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}

	/**
	 * 활성 프로필 조합을 검사한다. {@code prod}가 꺼져 있으면 아무것도 하지 않는다.
	 * @param environment 실행 환경
	 * @throws IllegalStateException {@code prod}가 {@code local}·{@code demo}·{@code codef-live}와 함께 켜졌거나 {@code member} 없이 켜진
	 * 경우, 또는 {@code prod}에 CODEF 키·토큰 환경 변수가 있는 경우(빈 값 포함)
	 */
	static void check(Environment environment) {
		if (!environment.acceptsProfiles(Profiles.of("prod"))) {
			return;
		}
		if (environment.acceptsProfiles(Profiles.of("local | demo"))) {
			throw new IllegalStateException("prod 프로필은 local·demo 프로필과 함께 켤 수 없습니다(공개 환경의 시연 부품 차단, 결정 38-6).");
		}
		// CODEF 실 게이트웨이 프로필은 비공개 검증 전용이다. 실 게이트웨이 조건(!prod)과 별개로 조합 자체를 막는다
		if (environment.acceptsProfiles(Profiles.of("codef-live"))) {
			throw new IllegalStateException("prod 프로필은 codef-live 프로필과 함께 켤 수 없습니다(CODEF는 공개 환경에 등록하지 않음, 결정 38-8).");
		}
		if (!environment.acceptsProfiles(Profiles.of("member"))) {
			throw new IllegalStateException(
					"prod 프로필은 member 프로필과 함께 켜져야 합니다(spring.profiles.group.prod: member 확인, 결정 38-6).");
		}
		// 값은 읽지 않는다. 이름이 있는지만 보고 메시지에도 이름만 넣는다.
		List<String> present = CODEF_SECRET_VARIABLES.stream().filter(environment::containsProperty).toList();
		if (!present.isEmpty()) {
			throw new IllegalStateException("prod 프로필에는 CODEF 키·토큰 환경 변수를 둘 수 없습니다: " + String.join(", ", present)
					+ " (CODEF는 공개 환경에 등록하지 않음, 결정 38-8).");
		}
	}

}
