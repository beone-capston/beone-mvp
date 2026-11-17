package com.beone.api.auth.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 회원 인증 설정값을 등록한다. {@code local}·{@code demo} 프로필 전용이다.
 */
@Configuration(proxyBeanMethods = false)
@Profile({ "local", "demo" })
@EnableConfigurationProperties(AnonymousSessionProperties.class)
public class AuthConfiguration {

}
