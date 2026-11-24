package com.beone.api.codef.transport;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 전송 계층을 조립한다. 키는 프로세스 환경 변수에서만 읽는다. 키가 없어도 서버는 뜨고, 시작할 때 CODEF를 호출하지 않는다.
 */
@Configuration(proxyBeanMethods = false)
public class CodefTransportConfiguration {

	private static final Logger log = LoggerFactory.getLogger(CodefTransportConfiguration.class);

	/**
	 * 호출 총량 빈이 있으면(codef-live, LP-2) 그것을 쓰고, 없으면 세지 않는다. 그 밖의 프로필에는 이 클라이언트를 쓰는 게이트웨이가 없다.
	 * @param environment 설정
	 * @param budget 호출 총량. 보통 codef-live에서만 있다
	 * @return 전송 계층 클라이언트
	 */
	@Bean
	public CodefClient codefClient(Environment environment, ObjectProvider<CodefCallBudget> budget) {
		return codefClient(environment, System.getenv(), budget.getIfUnique(() -> CodefCallBudget.UNLIMITED));
	}

	CodefClient codefClient(Environment environment, Map<String, String> processVariables) {
		return codefClient(environment, processVariables, CodefCallBudget.UNLIMITED);
	}

	CodefClient codefClient(Environment environment, Map<String, String> processVariables, CodefCallBudget budget) {
		CodefApiKeys keys = CodefApiKeys.fromEnvironment(processVariables);
		if (!keys.isConfigured()) {
			log.warn("CODEF 키 설정 안 됨 missing={}", keys.missingVariables());
		}
		CodefTransportSettings settings = new CodefTransportSettings(CodefTransportSettings.DEFAULT_TOKEN_URI,
				environment.getProperty("beone.codef.product-host", URI.class, CodefTransportSettings.SANDBOX_HOST),
				environment.getProperty("beone.codef.connect-timeout", Duration.class,
						CodefTransportSettings.DEFAULT_CONNECT_TIMEOUT),
				environment.getProperty("beone.codef.token-response-timeout", Duration.class,
						CodefTransportSettings.DEFAULT_TOKEN_RESPONSE_TIMEOUT),
				environment.getProperty("beone.codef.product-response-timeout", Duration.class,
						CodefTransportSettings.DEFAULT_PRODUCT_RESPONSE_TIMEOUT));
		return new CodefClient(keys, settings, budget);
	}

}
