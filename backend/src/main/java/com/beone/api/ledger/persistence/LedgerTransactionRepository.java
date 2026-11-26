package com.beone.api.ledger.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.beone.api.ledger.application.LedgerEntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 원장 행 저장소. 조회는 항상 사용자 ID와 함께 한다(D-09).
 */
public interface LedgerTransactionRepository extends JpaRepository<LedgerTransactionEntity, Long> {

	/**
	 * 이 사용자의 거래일이 기간 안인 행을 거래일·행 ID 오름차순으로 찾는다. 카드·출처 조건은 호출자가 거른다(한 달 단위라 건수가 작다).
	 * @param userId 사용자 ID
	 * @param from 시작 거래일(포함)
	 * @param to 종료 거래일(포함)
	 * @return 기간 안의 원장 행 목록
	 */
	List<LedgerTransactionEntity> findByUserIdAndTransactionDateBetweenOrderByTransactionDateAscLedgerIdAsc(Long userId,
			LocalDate from, LocalDate to);

	/**
	 * 이 사용자·이 카드의 월간 상태 스냅샷 뒤 활동(D-16, #120)을 행 ID 오름차순으로 찾는다. 기록 시각이 기준 시각보다 뒤인 행 중
	 * 승인은 자기 거래일이, 취소는 원 승인 행의 거래일이 기간 안인 것이다. 취소를 원 승인의 달에 귀속해 그 달 버킷 판단에 쓰기
	 * 위해서다(docs/api/monthly-states.md 218행, D-17, 관리자 승인 규칙).
	 * @param userId 사용자 ID
	 * @param userCardId 보유 카드 ID
	 * @param from 시작 거래일(포함)
	 * @param to 종료 거래일(포함)
	 * @param after 스냅샷 기준 시각. 이 시각보다 뒤에 기록된 행만 찾는다
	 * @param approval 승인 구분 값
	 * @param cancellation 취소 구분 값
	 * @return 스냅샷 뒤 활동 행 목록
	 */
	@Query("select t from LedgerTransactionEntity t where t.userId = :userId and t.userCardId = :userCardId"
			+ " and t.recordedAt > :after and ((t.entryType = :approval and t.transactionDate between :from and :to)"
			+ " or (t.entryType = :cancellation and exists (select o.ledgerId from LedgerTransactionEntity o"
			+ " where o.ledgerId = t.originalLedgerId and o.transactionDate between :from and :to)))"
			+ " order by t.ledgerId")
	List<LedgerTransactionEntity> findActivityAfter(@Param("userId") Long userId, @Param("userCardId") Long userCardId,
			@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("after") LocalDateTime after,
			@Param("approval") LedgerEntryType approval, @Param("cancellation") LedgerEntryType cancellation);

}
