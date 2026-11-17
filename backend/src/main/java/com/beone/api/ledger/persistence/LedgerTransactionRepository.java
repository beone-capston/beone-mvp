package com.beone.api.ledger.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 원장 행 저장소. 이 작업은 저장만 한다. 사용자·기간·카드 조건 조회는 원장 조회 API(B4)가 더한다(D-09 사용자별 조회).
 */
public interface LedgerTransactionRepository extends JpaRepository<LedgerTransactionEntity, Long> {

}
