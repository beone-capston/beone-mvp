package com.beone.api.ledger.application;

import java.util.List;

/**
 * 월간 상태 스냅샷 뒤에 기록된 원장 행 한 건(D-16). 월간 상태 요약과 추천 입력의 사용액 가산에 쓴다.
 *
 * @param entryType 승인 또는 취소
 * @param amountWon 승인은 양수, 취소는 음수
 * @param usageEffects 승인 행은 그 행의 버킷 차감액, 취소 행은 원 승인 행의 버킷 차감액. 모르는 차감액은 {@code null}
 */
public record LedgerActivity(LedgerEntryType entryType, long amountWon, List<LedgerUsageEffect> usageEffects) {

	public LedgerActivity {
		usageEffects = List.copyOf(usageEffects);
	}

}
