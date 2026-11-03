package com.beone.api.benefit.rule.loading;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.beone.api.benefit.rule.RuleCatalog;

/**
 * 규칙 카탈로그를 애플리케이션 시작 때 만든다. 규칙 데이터가 잘못되면 서버가 뜨지 않는다.
 */
@Configuration(proxyBeanMethods = false)
public class RuleCatalogConfiguration {

	@Bean
	public RuleCatalog ruleCatalog() {
		return RuleCatalogLoader.load();
	}

}
