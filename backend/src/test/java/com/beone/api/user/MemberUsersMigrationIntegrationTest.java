package com.beone.api.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.beone.api.support.IntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Flyway {@code V14}(회원 장부)의 백필과 제약을 확인한다. 같은 컨테이너의 별도 스키마에 V13까지 적용하고 회원을 넣은 뒤 V14를 적용한다.
 *
 * <p>기대값은 결정 37 D1a·D2다. 이메일 인증을 마친 회원만 '내 카드'(REAL, 쓰기 가능) 장부를 1개 받고, 인증 전 회원은 받지 않는다. 시연
 * 사용자 2행은 바뀌지 않는다. 장부는 시연 사용자 구분과 회원 ID 중 정확히 하나만 가지며, 장부가 있는 회원은 지울 수 없다(RESTRICT).
 */
@IntegrationTest
class MemberUsersMigrationIntegrationTest {

	private static final String SCHEMA = "v14_backfill";

	@Autowired
	MySQLContainer mysql;

	private JdbcTemplate root;

	private JdbcTemplate schema;

	private DriverManagerDataSource schemaDataSource;

	@BeforeEach
	void createSchema() {
		this.root = new JdbcTemplate(dataSource(""));
		this.root.execute("DROP DATABASE IF EXISTS " + SCHEMA);
		this.root.execute("CREATE DATABASE " + SCHEMA);
		this.schemaDataSource = dataSource(SCHEMA);
		this.schema = new JdbcTemplate(this.schemaDataSource);
	}

	@AfterEach
	void dropSchema() {
		this.root.execute("DROP DATABASE IF EXISTS " + SCHEMA);
	}

	@Test
	void backfillsOnlyVerifiedMembersAndKeepsDemoUsers() {
		migrate("13");
		List<Map<String, Object>> demoBefore = demoRows();
		insertMember("usr-verified-1", "v1@example.com", "2026-11-17 09:00:00");
		insertMember("usr-verified-2", "v2@example.com", "2026-11-17 09:30:00");
		insertMember("usr-pending-01", "p1@example.com", null);

		migrate("14");

		assertThat(this.schema.queryForList(
				"SELECT member_id, demo_user, display_name, portfolio_type, read_only FROM users"
						+ " WHERE member_id IS NOT NULL ORDER BY member_id"))
			.containsExactly(memberRow("usr-verified-1"), memberRow("usr-verified-2"));
		assertThat(demoRows()).isEqualTo(demoBefore).hasSize(2);
		assertThat(this.schema.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isEqualTo(4);
	}

	@Test
	void ownerConstraintsAndRestrictAreEnforced() {
		migrate("14");
		insertMember("usr-verified-1", "v1@example.com", "2026-11-17 09:00:00");
		this.schema.update("INSERT INTO users (member_id, display_name, portfolio_type, read_only)"
				+ " VALUES ('usr-verified-1', '내 카드', 'REAL', FALSE)");

		// 회원당 장부 1개
		assertThatThrownBy(() -> this.schema.update("INSERT INTO users (member_id, display_name, portfolio_type,"
				+ " read_only) VALUES ('usr-verified-1', '내 카드', 'REAL', FALSE)"))
			.isInstanceOf(DataIntegrityViolationException.class);
		// 주인이 없거나 둘 다인 행은 넣을 수 없다
		// CHECK 위반은 MySQL 오류 3819이며 Spring이 무결성 위반으로 분류하지 않으므로 제약 이름으로 확인한다
		assertThatThrownBy(() -> this.schema.update(
				"INSERT INTO users (display_name, portfolio_type, read_only) VALUES ('x', 'REAL', FALSE)"))
			.isInstanceOf(DataAccessException.class)
			.hasMessageContaining("ck_users_owner");
		insertMember("usr-verified-2", "v2@example.com", "2026-11-17 09:00:00");
		assertThatThrownBy(() -> this.schema.update("INSERT INTO users (demo_user, member_id, display_name,"
				+ " portfolio_type, read_only) VALUES ('OTHER_DEMO', 'usr-verified-2', 'x', 'REAL', FALSE)"))
			.isInstanceOf(DataAccessException.class)
			.hasMessageContaining("ck_users_owner");
		// 없는 회원은 참조할 수 없고, 장부가 있는 회원은 지울 수 없다
		assertThatThrownBy(() -> this.schema.update("INSERT INTO users (member_id, display_name, portfolio_type,"
				+ " read_only) VALUES ('usr-missing-000', 'x', 'REAL', FALSE)"))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> this.schema.update("DELETE FROM members WHERE member_id = 'usr-verified-1'"))
			.isInstanceOf(DataIntegrityViolationException.class);
		// 시연 사용자 구분의 UNIQUE는 그대로다
		assertThatThrownBy(() -> this.schema.update("INSERT INTO users (demo_user, display_name, portfolio_type,"
				+ " read_only) VALUES ('MY_CARDS', 'x', 'REAL', FALSE)"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	private static Map<String, Object> memberRow(String memberId) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("member_id", memberId);
		row.put("demo_user", null);
		row.put("display_name", "내 카드");
		row.put("portfolio_type", "REAL");
		row.put("read_only", false);
		return row;
	}

	private List<Map<String, Object>> demoRows() {
		return this.schema.queryForList("SELECT user_id, demo_user, display_name, portfolio_type, read_only FROM users"
				+ " WHERE demo_user IS NOT NULL ORDER BY user_id");
	}

	private void insertMember(String memberId, String email, String verifiedAt) {
		this.schema.update(
				"INSERT INTO members (member_id, email, password_hash, email_verified_at, created_at) VALUES (?, ?, ?, ?, ?)",
				memberId, email, "hash", verifiedAt, "2026-11-17 08:00:00");
	}

	private void migrate(String target) {
		Flyway.configure()
			.dataSource(this.schemaDataSource)
			.locations("classpath:db/migration")
			.target(target)
			.load()
			.migrate();
	}

	// 테스트 컨테이너의 root 계정으로 접속한다(별도 스키마를 만들 권한이 필요하다). 비밀번호는 컨테이너 설정값과 같다
	private DriverManagerDataSource dataSource(String database) {
		String url = "jdbc:mysql://" + this.mysql.getHost() + ":" + this.mysql.getMappedPort(3306) + "/" + database;
		return new DriverManagerDataSource(url, "root", this.mysql.getPassword());
	}

}
