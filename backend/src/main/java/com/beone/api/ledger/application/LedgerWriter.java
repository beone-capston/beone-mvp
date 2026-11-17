package com.beone.api.ledger.application;

import java.util.List;

import com.beone.api.ledger.persistence.LedgerTransactionEntity;
import com.beone.api.ledger.persistence.LedgerTransactionRepository;
import com.beone.api.ledger.persistence.LedgerUsageEffectEntity;
import com.beone.api.ledger.persistence.LedgerUsageEffectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자체 모의 원장의 쓰기 부품. 모의 결제(payment)가 승인·취소와 같은 트랜잭션에서 부른다(docs/api/mock-payments.md).
 *
 * <p>원장 조회 API(B4)는 아직 없다. 이 부품은 행을 남기고, 취소 영향 계산에 필요한 승인 행의 버킷 차감액만 다시 읽는다. 원장에서
 * 전월 실적·버킷 사용액을 자동 산출하지 않는다(D-16).
 */
@Service
public class LedgerWriter {

	private final LedgerTransactionRepository transactionRepository;

	private final LedgerUsageEffectRepository usageEffectRepository;

	public LedgerWriter(LedgerTransactionRepository transactionRepository,
			LedgerUsageEffectRepository usageEffectRepository) {
		this.transactionRepository = transactionRepository;
		this.usageEffectRepository = usageEffectRepository;
	}

	/**
	 * 승인 행과 그 행의 버킷 차감액을 남긴다.
	 * @param entry 승인 행
	 * @param usageEffects 승인 카드의 버킷별 차감액
	 * @return 원장 행 DB 번호
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public long recordApproval(NewLedgerEntry entry, List<LedgerUsageEffect> usageEffects) {
		if (entry.entryType() != LedgerEntryType.APPROVAL) {
			throw new IllegalArgumentException("not an approval");
		}
		long ledgerId = save(entry);
		this.usageEffectRepository.saveAll(usageEffects.stream()
			.map(effect -> new LedgerUsageEffectEntity(ledgerId, effect.limitBucketId(), effect.deductionWon()))
			.toList());
		return ledgerId;
	}

	/**
	 * 원 승인 행에 연결한 취소 행을 남긴다.
	 * @param entry 취소 행
	 * @return 원장 행 DB 번호
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public long recordCancellation(NewLedgerEntry entry) {
		if (entry.entryType() != LedgerEntryType.CANCELLATION) {
			throw new IllegalArgumentException("not a cancellation");
		}
		return save(entry);
	}

	/** 승인 행의 버킷 차감액을 저장 순서대로 돌려준다. */
	@Transactional(readOnly = true)
	public List<LedgerUsageEffect> usageEffectsOf(long ledgerId) {
		return this.usageEffectRepository.findByLedgerIdOrderByUsageEffectIdAsc(ledgerId)
			.stream()
			.map(effect -> new LedgerUsageEffect(effect.getLimitBucketId(), effect.getDeductionWon()))
			.toList();
	}

	private long save(NewLedgerEntry entry) {
		return this.transactionRepository.save(new LedgerTransactionEntity(entry.userId(), entry.userCardId(),
				LedgerSource.SIMULATION, entry.entryType(), entry.amountWon(), entry.transactionDate(),
				entry.transactionTime(), entry.merchantClass(), entry.paymentMethod(), entry.merchantName(),
				entry.originalLedgerId(), entry.simulatedPaymentId(), entry.attributionMonth().toString(),
				entry.attributionStatus(), entry.recordedAt()))
			.getLedgerId();
	}

}
