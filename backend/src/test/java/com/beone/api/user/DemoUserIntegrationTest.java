package com.beone.api.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.beone.api.support.IntegrationTest;
import com.beone.api.user.application.CurrentUser;
import com.beone.api.user.application.DemoUser;
import com.beone.api.user.application.DemoUserProfile;
import com.beone.api.user.application.DemoUserService;
import com.beone.api.user.application.PortfolioType;
import com.beone.api.user.persistence.UserEntity;
import com.beone.api.user.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.web.client.RestClient;

/**
 * Testcontainers MySQL에서 V2 마이그레이션(ddl-auto: validate 통과)·시드 2명·저장소 조회·실제 HTTP 응답을 확인한다.
 *
 * <p>시드 기대값은 docs/api/cards.md의 {@code GET /api/v1/demo-users} 응답 예시에서 옮겼다.
 */
@IntegrationTest
@ActiveProfiles("local")
class DemoUserIntegrationTest {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	UserRepository userRepository;

	@Autowired
	DemoUserService demoUserService;

	@Test
	void demoUserMigrationIsApplied() {
		Boolean success = this.jdbcTemplate.queryForObject(
				"SELECT success FROM flyway_schema_history WHERE version = '2'", Boolean.class);

		assertThat(success).isTrue();
	}

	@Test
	void seedHasExactlyTwoDemoUsers() {
		List<UserEntity> users = this.userRepository.findAll();

		assertThat(users).hasSize(2);
		assertThat(users).extracting(UserEntity::getUserId).doesNotContainNull().doesNotHaveDuplicates();
	}

	@Test
	void repositoryFindsEachSeedByDemoUser() {
		UserEntity myCards = this.userRepository.findByDemoUser(DemoUser.MY_CARDS).orElseThrow();
		UserEntity portfolio = this.userRepository.findByDemoUser(DemoUser.VIRTUAL_PORTFOLIO).orElseThrow();

		assertThat(myCards.getDisplayName()).isEqualTo("내 카드");
		assertThat(myCards.getPortfolioType()).isEqualTo(PortfolioType.REAL);
		assertThat(myCards.isReadOnly()).isFalse();
		assertThat(portfolio.getDisplayName()).isEqualTo("가상 포트폴리오");
		assertThat(portfolio.getPortfolioType()).isEqualTo(PortfolioType.SYNTHETIC);
		assertThat(portfolio.isReadOnly()).isTrue();
	}

	@Test
	void serviceListsInContractOrderAndResolvesCurrentUser() {
		assertThat(this.demoUserService.listDemoUsers()).containsExactly(
				new DemoUserProfile(DemoUser.MY_CARDS, "내 카드", PortfolioType.REAL, false),
				new DemoUserProfile(DemoUser.VIRTUAL_PORTFOLIO, "가상 포트폴리오", PortfolioType.SYNTHETIC, true));

		CurrentUser current = this.demoUserService.currentUserOf(DemoUser.VIRTUAL_PORTFOLIO);
		assertThat(current.demoUser()).isEqualTo(DemoUser.VIRTUAL_PORTFOLIO);
		assertThat(current.userId())
			.isEqualTo(this.userRepository.findByDemoUser(DemoUser.VIRTUAL_PORTFOLIO).orElseThrow().getUserId());
	}

	@Test
	void demoUserColumnIsUnique() {
		assertThatThrownBy(() -> this.jdbcTemplate.update(
				"INSERT INTO users (demo_user, display_name, portfolio_type, read_only) VALUES ('MY_CARDS', 'x', 'REAL', FALSE)"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void demoUsersEndpointReturnsSeedOverHttp() {
		String body = client().get().uri("/api/v1/demo-users").retrieve().body(String.class);

		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch("""
				{"data":{"users":[
				  {"demoUser":"MY_CARDS","displayName":"내 카드","portfolioType":"REAL","readOnly":false},
				  {"demoUser":"VIRTUAL_PORTFOLIO","displayName":"가상 포트폴리오","portfolioType":"SYNTHETIC","readOnly":true}
				]}}
				""", body);
	}

	private RestClient client() {
		return RestClient.create("http://localhost:" + this.port);
	}

}
