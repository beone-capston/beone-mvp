package com.beone.api.auth.application;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CSRF 토큰 조회가 발급하는 익명 세션의 수명({@code beone.auth.anonymous-session.*}).
 *
 * <p>인증 명세 사본에는 익명 세션 수명이 없다. <strong>사용자 결정 대기</strong>이며, 결정 전까지 기본값은 회원 세션과 같은 미사용
 * 7일·최대 30일로 둔다(관리자 판단, 결정 30). 결정이 나오면 이 기본값만 바꾼다. 테스트는 값을 명시해 주입한다.
 *
 * @param idleTimeout 마지막 사용 뒤 만료까지의 시간
 * @param absoluteTimeout 발급 뒤 최대 유지 시간. 쿠키 {@code Max-Age}로도 쓴다
 */
@ConfigurationProperties("beone.auth.anonymous-session")
public record AnonymousSessionProperties(Duration idleTimeout, Duration absoluteTimeout) {

	/** 미사용 만료 기본값(사용자 결정 대기). */
	public static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofDays(7);

	/** 절대 만료 기본값(사용자 결정 대기). */
	public static final Duration DEFAULT_ABSOLUTE_TIMEOUT = Duration.ofDays(30);

	public AnonymousSessionProperties {
		idleTimeout = Objects.requireNonNullElse(idleTimeout, DEFAULT_IDLE_TIMEOUT);
		absoluteTimeout = Objects.requireNonNullElse(absoluteTimeout, DEFAULT_ABSOLUTE_TIMEOUT);
		if (idleTimeout.toSeconds() <= 0 || absoluteTimeout.toSeconds() <= 0) {
			throw new IllegalArgumentException("anonymous session timeouts must be at least one second");
		}
		if (idleTimeout.compareTo(absoluteTimeout) > 0) {
			throw new IllegalArgumentException("anonymous session idle timeout must not exceed absolute timeout");
		}
	}

}
