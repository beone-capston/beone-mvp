package com.beone.api.order.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 주문 패키지 전용 시계. 기준 시간대는 Asia/Seoul이다(docs/api.md 값 형식, 결정 27).
 *
 * <p>공유 {@link Clock} 빈을 만들지 않고 이 부품만 주입해 다른 기능 패키지와 빈이 겹치지 않게 한다. 테스트는 고정 시계로 만든다.
 */
@Component
public class OrderClock {

	/** 업무 API 기준 시간대. */
	public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	private final Clock clock;

	@Autowired
	public OrderClock() {
		this(Clock.system(ZONE));
	}

	public OrderClock(Clock clock) {
		this.clock = Objects.requireNonNull(clock, "clock").withZone(ZONE);
	}

	/**
	 * 서울 기준 이번 달. 예정 소비의 조회 범위이자 허용 범위다.
	 * @return 서울 기준 이번 달
	 */
	public YearMonth currentMonth() {
		return YearMonth.now(this.clock);
	}

	/**
	 * 서울 기준 현재 시각(초 단위). 주문 생성 시각으로 저장한다.
	 * @return 서울 기준 현재 시각(초 단위)
	 */
	public LocalDateTime now() {
		return LocalDateTime.now(this.clock).truncatedTo(ChronoUnit.SECONDS);
	}

}
