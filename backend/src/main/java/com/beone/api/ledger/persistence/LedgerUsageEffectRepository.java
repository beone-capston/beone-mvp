package com.beone.api.ledger.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 승인 행 버킷 차감액 저장소.
 */
public interface LedgerUsageEffectRepository extends JpaRepository<LedgerUsageEffectEntity, Long> {

	/** 한 승인 행의 차감액을 저장 순서대로 찾는다. */
	List<LedgerUsageEffectEntity> findByLedgerIdOrderByUsageEffectIdAsc(Long ledgerId);

}
