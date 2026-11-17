package com.beone.api.auth.application;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CSRF 토큰 조회가 발급하는 익명 세션의 수명({@code beone.auth.anonymous-session.*}).
 *
 * <p>인증 명세 사본에는 익명 세션 수명이 없다. 미사용 2시간 만료는 2026-11-18 사용자 직접 결정(Aside 질문)이다. 절대 상한 2시간은
 * 관리자 판단이다(결정 28, 사용자 선택지의 '짧게, 오래된 익명 세션이 쌓이지 않게' 의도에 맞춤). 익명 세션은 회원 동시 세션 5개 계산에
 * 넣지 않는다. 테스트는 값을 명시해 주입한다.
 *
 * @param idleTimeout 마지막 사용 뒤 만료까지의 시간
 * @param absoluteTimeout 발급 뒤 최대 유지 시간. 쿠키 {@code Max-Age}로도 쓴다
 */
@ConfigurationProperties("beone.auth.anonymous-session")
public record AnonymousSessionProperties(Duration idleTimeout, Duration absoluteTimeout) {

	/** 미사용 만료 기본값: 2시간(2026-11-18 사용자 직접 결정). */
	public static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofHours(2);

	/** 절대 만료 기본값: 2시간(관리자 판단). 쿠키 {@code Max-Age}로도 쓴다. */
	public static final Duration DEFAULT_ABSOLUTE_TIMEOUT = Duration.ofHours(2);

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
