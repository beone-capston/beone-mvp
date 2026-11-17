package com.beone.api.codef.application;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 실 CODEF를 부르지 않는 가짜 게이트웨이. {@code local}·{@code demo} 프로필에서만 등록한다(결정 30).
 *
 * <p>정해진 합성 아이디로 실패 경로를 재현한다. 그 밖의 아이디는 모두 성공이며 무작위 connectedId({@code fake-cid-} 접두어)를 준다.
 * 비밀번호는 읽지 않는다. RSA 암호화는 하지 않는다(관리자 판단 P7). 아이디·비밀번호는 저장하지 않는다.
 */
@Component
@Profile({ "local", "demo" })
public class FakeCodefGateway implements CodefGateway {

	/** 이 아이디로 연결하면 로그인 거절. */
	public static final String LOGIN_REJECTED_ID = "fake-login-rejected";

	/** 이 아이디로 연결하면 카드소지확인 필요. */
	public static final String CARD_VERIFICATION_ID = "fake-card-verification";

	/** 이 아이디로 연결하면 CODEF 사용 불가. */
	public static final String UNAVAILABLE_ID = "fake-unavailable";

	private static final String PREFIX = "fake-cid-";

	private final Set<String> active = ConcurrentHashMap.newKeySet();

	@Override
	public CodefRegistration register(String organization, String loginId, char[] password) {
		return switch (loginId) {
			case LOGIN_REJECTED_ID -> new CodefRegistration.LoginRejected();
			case CARD_VERIFICATION_ID -> new CodefRegistration.CardVerificationRequired();
			case UNAVAILABLE_ID -> new CodefRegistration.Unavailable();
			default -> {
				String connectedId = PREFIX + UUID.randomUUID();
				this.active.add(connectedId);
				yield new CodefRegistration.Registered(connectedId);
			}
		};
	}

	@Override
	public void release(String connectedId) {
		this.active.remove(connectedId);
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
		return this.active.contains(connectedId);
	}

}
