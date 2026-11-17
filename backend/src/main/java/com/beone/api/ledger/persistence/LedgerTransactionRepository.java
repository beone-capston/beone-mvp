package com.beone.api.ledger.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 원장 행 저장소. 조회는 항상 사용자 ID와 함께 한다(D-09).
 */
public interface LedgerTransactionRepository extends JpaRepository<LedgerTransactionEntity, Long> {

	/**
	 * 이 사용자의 거래일이 기간 안인 행을 거래일·행 ID 오름차순으로 찾는다. 카드·출처 조건은 호출자가 거른다(한 달 단위라 건수가 작다).
	 */
	List<LedgerTransactionEntity> findByUserIdAndTransactionDateBetweenOrderByTransactionDateAscLedgerIdAsc(Long userId,
			LocalDate from, LocalDate to);

	/**
	 * 이 사용자·이 카드의 거래일이 기간 안이고 기록 시각이 기준 시각보다 뒤인 행을 행 ID 오름차순으로 찾는다. 월간 상태 스냅샷 뒤
	 * 활동(D-16)을 읽는 데 쓴다.
	 */
	List<LedgerTransactionEntity> findByUserIdAndUserCardIdAndTransactionDateBetweenAndRecordedAtAfterOrderByLedgerIdAsc(
			Long userId, Long userCardId, LocalDate from, LocalDate to, LocalDateTime after);

}
