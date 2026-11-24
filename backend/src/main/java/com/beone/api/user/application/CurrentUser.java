package com.beone.api.user.application;

import java.util.Objects;

/**
 * 현재 요청의 사용자(장부). 프로필마다 하나뿐인 현재 사용자 결정 지점에서만 만들고 컨트롤러는 인자로 받아 쓴다(D-09).
 *
 * <p>사용자 데이터는 {@code userId}로 나눠 저장·조회한다. 쓰기 가능 여부는 {@code readOnly}로 판단하고 시연 사용자 구분으로 판단하지
 * 않는다. 회원 장부는 시연 사용자 구분이 없다(결정 37 D7).
 *
 * @param userId 저장소의 사용자 ID
 * @param demoUser 시연 사용자 구분. 회원 장부면 {@code null}
 * @param portfolioType 포트폴리오 종류
 * @param readOnly 보유 카드·CODEF 쓰기 불가 여부(D-19)
 */
public record CurrentUser(long userId, DemoUser demoUser, PortfolioType portfolioType, boolean readOnly) {

	public CurrentUser {
		Objects.requireNonNull(portfolioType, "portfolioType");
	}

	/**
	 * 시연 사용자. 포트폴리오 종류와 읽기 전용 여부는 시드(Flyway {@code V2})와 같은 값으로 정한다. 내 카드는 실제·쓰기 가능, 가상
	 * 포트폴리오는 시드·읽기 전용이다. 단위 테스트와 웹 슬라이스 테스트가 쓴다. 운영 경로는 DB 행 값으로 만든다.
	 * @param userId 저장소의 사용자 ID
	 * @param demoUser 시연 사용자 구분
	 */
	public CurrentUser(long userId, DemoUser demoUser) {
		this(userId, Objects.requireNonNull(demoUser, "demoUser"),
				(demoUser == DemoUser.VIRTUAL_PORTFOLIO) ? PortfolioType.SYNTHETIC : PortfolioType.REAL,
				demoUser == DemoUser.VIRTUAL_PORTFOLIO);
	}

}
