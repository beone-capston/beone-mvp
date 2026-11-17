package com.beone.api.auth.application;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CSRF 토큰 조회가 발급하는 익명 세션의 수명({@code beone.auth.anonymous-session.*}).
 *
 * <p>인증 명세 사본에는 익명 세션 수명이 없다. 2026-11-18 사용자 직접 결정(Aside 질문)으로 미사용 2시간에 만료하고, 발급 시점부터의
 * 절대 상한은 두지 않으며, 회원 동시 세션 5개 계산에 넣지 않는다. 유효하게 쓸 때마다 서버의 미사용 만료와 쿠키 {@code Max-Age}를 함께
 * 다시 정한다. 테스트는 값을 명시해 주입한다.
 *
 * @param idleTimeout 마지막 사용 뒤 만료까지의 시간. 쿠키 {@code Max-Age}로도 쓴다
 */
@ConfigurationProperties("beone.auth.anonymous-session")
public record AnonymousSessionProperties(Duration idleTimeout) {

	/** 미사용 만료 기본값: 2시간(2026-11-18 사용자 직접 결정). */
	public static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofHours(2);

	public AnonymousSessionProperties {
		idleTimeout = Objects.requireNonNullElse(idleTimeout, DEFAULT_IDLE_TIMEOUT);
		if (idleTimeout.toSeconds() <= 0) {
			throw new IllegalArgumentException("anonymous session idle timeout must be at least one second");
		}
	}

}
