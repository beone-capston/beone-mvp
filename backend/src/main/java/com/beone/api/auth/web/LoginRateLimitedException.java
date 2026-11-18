package com.beone.api.auth.web;

import java.time.Duration;
import java.util.Objects;

/**
 * 같은 이메일로 15분 안에 5번 실패해 로그인이 잠겼다. 429 {@code RATE_LIMITED}와 {@code Retry-After}로 답한다.
 */
class LoginRateLimitedException extends RuntimeException {

	private final transient Duration retryAfter;

	LoginRateLimitedException(Duration retryAfter) {
		super("login rate limited");
		this.retryAfter = Objects.requireNonNull(retryAfter, "retryAfter");
	}

	Duration retryAfter() {
		return this.retryAfter;
	}

}
