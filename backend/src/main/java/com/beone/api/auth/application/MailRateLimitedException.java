package com.beone.api.auth.application;

import java.time.Duration;
import java.util.Objects;

/**
 * 같은 주소의 메일 요청이 60초 간격 안에 다시 왔다. 429 {@code RATE_LIMITED}와 {@code Retry-After}로 답한다.
 */
public class MailRateLimitedException extends RuntimeException {

	private final transient Duration retryAfter;

	/**
	 * @param retryAfter 다시 요청할 수 있을 때까지 남은 시간(1초 이상)
	 */
	public MailRateLimitedException(Duration retryAfter) {
		super("mail request rate limited");
		this.retryAfter = Objects.requireNonNull(retryAfter, "retryAfter");
	}

	/** @return 남은 시간 */
	public Duration retryAfter() {
		return this.retryAfter;
	}

}
