package com.beone.api.codef.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.beone.api.codef.CodefApproval;
import com.beone.api.codef.CodefCardPerformance;
import com.beone.api.codef.CodefHeldCard;
import com.beone.api.codef.CodefProductResponseReader;
import com.beone.api.codef.transport.CodefCallResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 실 CODEF를 부르지 않는 가짜 게이트웨이. {@code local}·{@code demo} 프로필에서만 등록한다(결정 30).
 *
 * <p>정해진 합성 아이디로 실패 경로를 재현한다. 그 밖의 아이디는 모두 성공이며 무작위 connectedId({@code fake-cid-} 접두어)를 준다.
 * 비밀번호는 읽지 않는다. RSA 암호화는 하지 않는다(관리자 판단 P7). 아이디·비밀번호는 저장하지 않는다.
 *
 * <p>조회는 {@code classpath:codef/fake/}의 합성 fixture를 응답 본문으로 읽어 전송 계층과 같은 해석기를 거친다(#129). 승인 사용일자는
 * 요청 시작일, 실적 기간은 서울 기준 전월로 채운다. 연결 때 쓴 합성 아이디로 동기화 실패 경로를 고른다. 서버를 다시 띄워 모르는
 * connectedId는 기본 성공 시나리오로 본다.
 */
@Component
@Profile({ "local", "demo" })
public class FakeCodefGateway implements CodefGateway, CodefSyncGateway {

	/** 이 아이디로 연결하면 로그인 거절. */
	public static final String LOGIN_REJECTED_ID = "fake-login-rejected";

	/** 이 아이디로 연결하면 카드소지확인 필요. */
	public static final String CARD_VERIFICATION_ID = "fake-card-verification";

	/** 이 아이디로 연결하면 CODEF 사용 불가. */
	public static final String UNAVAILABLE_ID = "fake-unavailable";

	/** 이 아이디로 연결하면 동기화 때 연결 무효(재연결 필요). */
	public static final String SYNC_RECONNECT_ID = "fake-sync-reconnect";

	/** 이 아이디로 연결하면 동기화의 세 조회가 모두 실패. */
	public static final String SYNC_UNAVAILABLE_ID = "fake-sync-unavailable";

	/** 이 아이디로 연결하면 동기화의 실적조회만 실패. */
	public static final String SYNC_PARTIAL_ID = "fake-sync-partial";

	private static final String PREFIX = "fake-cid-";

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

	private final Map<String, Scenario> active = new ConcurrentHashMap<>();

	private final Clock clock;

	/**
	 * {@link Clock} 빈이 하나 있으면 그것을, 없으면 시스템 시계를 쓴다.
	 * @param clock 실적 기간을 정할 시계
	 */
	@Autowired
	public FakeCodefGateway(ObjectProvider<Clock> clock) {
		this(clock.getIfUnique(Clock::systemUTC));
	}

	FakeCodefGateway(Clock clock) {
		this.clock = clock.withZone(SEOUL);
	}

	@Override
	public CodefRegistration register(String organization, String loginId, char[] password) {
		return switch (loginId) {
			case LOGIN_REJECTED_ID -> new CodefRegistration.LoginRejected();
			case CARD_VERIFICATION_ID -> new CodefRegistration.CardVerificationRequired();
			case UNAVAILABLE_ID -> new CodefRegistration.Unavailable();
			default -> {
				String connectedId = PREFIX + UUID.randomUUID();
				this.active.put(connectedId, Scenario.of(loginId));
				yield new CodefRegistration.Registered(connectedId);
			}
		};
	}

	@Override
	public void release(String connectedId) {
		this.active.remove(connectedId);
	}

	@Override
	public CodefCallResult<CodefHeldCard> heldCards(String organization, String connectedId) {
		Scenario scenario = scenario(connectedId);
		if (scenario == Scenario.UNAVAILABLE) {
			return failed();
		}
		return new CodefCallResult.Answered<>(CodefProductResponseReader.readHeldCards(200, fixture("held-cards")));
	}

	@Override
	public CodefCallResult<CodefApproval> approvals(String organization, String connectedId, LocalDate from,
			LocalDate to) {
		Scenario scenario = scenario(connectedId);
		if (scenario == Scenario.UNAVAILABLE) {
			return failed();
		}
		String body = fixture("approvals").replace("{{USED_DATE}}", YYYYMMDD.format(from));
		return new CodefCallResult.Answered<>(CodefProductResponseReader.readApprovals(200, body));
	}

	@Override
	public CodefCallResult<CodefCardPerformance> performances(String organization, String connectedId) {
		Scenario scenario = scenario(connectedId);
		if (scenario == Scenario.UNAVAILABLE || scenario == Scenario.PARTIAL) {
			return failed();
		}
		YearMonth previous = YearMonth.now(this.clock).minusMonths(1);
		String body = fixture("performances").replace("{{PREVIOUS_START}}", YYYYMMDD.format(previous.atDay(1)))
			.replace("{{PREVIOUS_END}}", YYYYMMDD.format(previous.atEndOfMonth()));
		return new CodefCallResult.Answered<>(CodefProductResponseReader.readCardPerformances(200, body));
	}

	/**
	 * 발급 뒤 정리하지 않은 connectedId 수. 테스트 확인용이다.
	 * @return 정리하지 않은 connectedId 수
	 */
	public int activeCount() {
		return this.active.size();
	}

	/**
	 * 이 가짜가 발급하고 아직 정리하지 않은 connectedId인지. 테스트 확인용이다.
	 * @param connectedId 확인할 값
	 * @return 발급 뒤 정리하지 않았으면 참
	 */
	public boolean isActive(String connectedId) {
		return this.active.containsKey(connectedId);
	}

	private Scenario scenario(String connectedId) {
		Scenario scenario = this.active.getOrDefault(connectedId, Scenario.DEFAULT);
		if (scenario == Scenario.RECONNECT) {
			throw new CodefConnectionInvalidException();
		}
		return scenario;
	}

	private static <T> CodefCallResult<T> failed() {
		return new CodefCallResult.Failed<>(CodefCallResult.Stage.PRODUCT, CodefCallResult.Reason.CONNECTION_FAILED,
				OptionalInt.empty());
	}

	private static String fixture(String name) {
		try (InputStream in = FakeCodefGateway.class.getResourceAsStream("/codef/fake/" + name + ".json")) {
			if (in == null) {
				throw new IllegalStateException("fake CODEF fixture is missing: " + name);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** 연결 때 고른 동기화 시나리오. */
	private enum Scenario {

		DEFAULT, RECONNECT, UNAVAILABLE, PARTIAL;

		static Scenario of(String loginId) {
			return switch (loginId) {
				case SYNC_RECONNECT_ID -> RECONNECT;
				case SYNC_UNAVAILABLE_ID -> UNAVAILABLE;
				case SYNC_PARTIAL_ID -> PARTIAL;
				default -> DEFAULT;
			};
		}

	}

}
