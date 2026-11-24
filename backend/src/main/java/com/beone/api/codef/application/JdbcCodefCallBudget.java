package com.beone.api.codef.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.beone.api.codef.transport.CodefCallBudget;

/**
 * 프로세스를 넘는 CODEF 호출 총량을 {@code codef_call_budgets}에 센다(LP-2, N14). {@link LiveCodefGateway#PROFILE}에서만 등록하고 전송 계층
 * 클라이언트가 보내기 직전에 쓴다.
 *
 * <p>행이 없으면 0으로 만든 뒤, {@code used < 상한}일 때만 1을 더하는 갱신 한 문장으로 확인과 증가를 함께 한다. MySQL은 갱신하는 행을 잠그므로
 * 여러 요청이 동시에 와도 상한을 넘지 않는다. 각 문장은 자동 커밋이다(CODEF 호출은 DB 트랜잭션 밖에서 한다). 설정이 없거나 창 밖이거나 저장소
 * 오류면 거절한다. 거절 로그에는 API 이름과 이유만 남긴다.
 */
@Component
@Profile(LiveCodefGateway.PROFILE)
public class JdbcCodefCallBudget implements CodefCallBudget {

	private static final Logger log = LoggerFactory.getLogger(JdbcCodefCallBudget.class);

	private final JdbcTemplate jdbcTemplate;

	private final CodefCallBudgetSettings settings;

	private final Clock clock;

	/**
	 * 설정은 기동 때 한 번 읽는다. {@link Clock} 빈이 하나 있으면 그것을, 없으면 시스템 시계를 쓴다.
	 * @param jdbcTemplate 저장소
	 * @param environment 설정
	 * @param clock 시계
	 */
	@Autowired
	public JdbcCodefCallBudget(JdbcTemplate jdbcTemplate, Environment environment, ObjectProvider<Clock> clock) {
		this(jdbcTemplate, CodefCallBudgetSettings.from(environment), clock.getIfUnique(Clock::systemUTC));
	}

	JdbcCodefCallBudget(JdbcTemplate jdbcTemplate, CodefCallBudgetSettings settings, Clock clock) {
		this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
		this.settings = Objects.requireNonNull(settings, "settings");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.settings.closedReason(this.clock.instant())
			.ifPresent(reason -> log.warn("CODEF 호출 총량 닫힘 reason={} (설정: {}, {}, {})", reason,
					CodefCallBudgetSettings.PER_API, CodefCallBudgetSettings.WINDOW_START,
					CodefCallBudgetSettings.WINDOW_END));
	}

	@Override
	public boolean tryAcquire(String api) {
		Optional<String> closed = this.settings.closedReason(this.clock.instant());
		if (closed.isPresent()) {
			log.warn("codef call budget denied api={} reason={}", api, closed.get());
			return false;
		}
		LocalDateTime window = LocalDateTime.ofInstant(this.settings.windowStart().orElseThrow(), ZoneOffset.UTC);
		try {
			this.jdbcTemplate.update("INSERT INTO codef_call_budgets (api, window_start, used) VALUES (?, ?, 0)"
					+ " ON DUPLICATE KEY UPDATE used = used", api, window);
			int updated = this.jdbcTemplate.update("UPDATE codef_call_budgets SET used = used + 1"
					+ " WHERE api = ? AND window_start = ? AND used < ?", api, window, this.settings.perApi().orElseThrow());
			if (updated == 1) {
				return true;
			}
			log.warn("codef call budget denied api={} reason=EXHAUSTED", api);
			return false;
		}
		catch (DataAccessException ex) {
			log.warn("codef call budget denied api={} reason=STORAGE_ERROR", api);
			return false;
		}
	}

}
