package com.beone.api.codef.transport;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 전송 계층을 조립한다. 키는 프로세스 환경 변수에서만 읽는다. 키가 없어도 서버는 뜨고, 시작할 때 CODEF를 호출하지 않는다.
 */
@Configuration(proxyBeanMethods = false)
public class CodefTransportConfiguration {

	private static final Logger log = LoggerFactory.getLogger(CodefTransportConfiguration.class);

	@Bean
	public CodefClient codefClient(Environment environment) {
		return codefClient(environment, System.getenv());
	}

	CodefClient codefClient(Environment environment, Map<String, String> processVariables) {
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
		return new CodefClient(keys, settings);
	}

}
