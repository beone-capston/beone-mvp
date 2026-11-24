package com.beone.api.user.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * 현재 사용자 값의 쓰기 가능 판단 근거를 확인한다. 기대값은 시드(Flyway V2)와 결정 37 D7이다.
 */
class CurrentUserTest {

	@Test
	void demoShortcutFollowsSeedValues() {
		assertThat(new CurrentUser(1L, DemoUser.MY_CARDS))
			.isEqualTo(new CurrentUser(1L, DemoUser.MY_CARDS, PortfolioType.REAL, false));
		assertThat(new CurrentUser(2L, DemoUser.VIRTUAL_PORTFOLIO))
			.isEqualTo(new CurrentUser(2L, DemoUser.VIRTUAL_PORTFOLIO, PortfolioType.SYNTHETIC, true));
	}

	@Test
	void memberLedgerHasNoDemoUser() {
		CurrentUser member = new CurrentUser(3L, null, PortfolioType.REAL, false);

		assertThat(member.demoUser()).isNull();
		assertThat(member.readOnly()).isFalse();
	}

	@Test
	void portfolioTypeIsRequired() {
		assertThatNullPointerException().isThrownBy(() -> new CurrentUser(3L, null, null, false));
		assertThatNullPointerException().isThrownBy(() -> new CurrentUser(1L, null));
	}

}
