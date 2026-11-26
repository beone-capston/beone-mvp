package com.beone.api.user.persistence;

import java.util.List;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 회원의 업무 장부와 그 장부의 모든 업무 행을 지운다(결정 D10). 업무 표는 {@code users}를 외래 키로 참조하고 대부분 연쇄 삭제가 없으므로
 * 자식 표부터 차례로 지운다. 연쇄 삭제(ON DELETE CASCADE)가 걸린 손자 표(월간 상태의 버킷 사용·서비스 선택, 추천 카드 기준, 결제 취소,
 * 원장 사용 효과)는 부모와 함께 지워진다.
 *
 * <p>회원 장부({@code member_id IS NOT NULL})만 지운다. 시연 사용자·가상 포트폴리오 장부는 대상이 아니다. 새 업무 표를 추가하면 이 목록에도
 * 넣어야 한다. 빠뜨리면 {@code users} 삭제가 외래 키로 실패해 탈퇴 전체가 롤백된다(조용히 남지 않는다).
 */
@Repository
public class MemberLedgerEraser {

	/** 지우는 순서. 앞 표가 뒤 표를 참조한다. */
	static final List<String> TABLES = List.of("codef_approval_rows", "codef_performance_references",
			"codef_held_cards", "codef_syncs", "codef_connections", "ledger_transactions", "simulated_payments",
			"recommendations", "idempotency_keys", "payment_preferences", "monthly_states", "planned_spends", "orders",
			"user_cards");

	private final JdbcTemplate jdbcTemplate;

	public MemberLedgerEraser(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
	}

	/**
	 * 회원 장부 한 개와 그 업무 행을 지운다.
	 * @param userId 회원 장부 ID
	 * @return 지운 행 수(연쇄 삭제된 손자 행 제외, 장부 행 포함)
	 * @throws IllegalArgumentException 회원 장부가 아님
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public int erase(long userId) {
		Integer members = this.jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM users WHERE user_id = ? AND member_id IS NOT NULL", Integer.class, userId);
		if (members == null || members != 1) {
			throw new IllegalArgumentException("not a member ledger");
		}
		int rows = 0;
		for (String table : TABLES) {
			rows += this.jdbcTemplate.update("DELETE FROM " + table + " WHERE user_id = ?", userId);
		}
		return rows + this.jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", userId);
	}

}
