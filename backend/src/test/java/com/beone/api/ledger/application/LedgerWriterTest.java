package com.beone.api.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import com.beone.api.ledger.persistence.LedgerTransactionEntity;
import com.beone.api.ledger.persistence.LedgerTransactionRepository;
import com.beone.api.ledger.persistence.LedgerUsageEffectEntity;
import com.beone.api.ledger.persistence.LedgerUsageEffectRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 원장 쓰기(docs/api/ledger.md 항목 형식). 출처는 항상 {@code SIMULATION}, 승인은 양수·취소는 음수이고 원 승인 행에 연결한다. 모르는
 * 차감액은 0이 아닌 {@code null}로 남긴다(D-18 K1).
 */
class LedgerWriterTest {

	private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 3, 10, 42, 0);

	private final LedgerTransactionRepository transactions = mock(LedgerTransactionRepository.class);

	private final LedgerUsageEffectRepository effects = mock(LedgerUsageEffectRepository.class);

	private final LedgerWriter writer = new LedgerWriter(this.transactions, this.effects);

	@Test
	void approvalIsASimulationRowWithItsBucketDeductions() {
		given(this.transactions.save(any())).willAnswer(invocation -> {
			LedgerTransactionEntity entity = invocation.getArgument(0);
			ReflectionTestUtils.setField(entity, "ledgerId", 9001L);
			return entity;
		});

		long id = this.writer.recordApproval(approval(), List.of(new LedgerUsageEffect("GREAT", 2100L),
				new LedgerUsageEffect("COMMON", null)));

		assertThat(id).isEqualTo(9001L);
		ArgumentCaptor<LedgerTransactionEntity> row = ArgumentCaptor.forClass(LedgerTransactionEntity.class);
		verify(this.transactions).save(row.capture());
		assertThat(row.getValue().getSource()).isEqualTo(LedgerSource.SIMULATION);
		assertThat(row.getValue().getEntryType()).isEqualTo(LedgerEntryType.APPROVAL);
		assertThat(row.getValue().getAmountWon()).isEqualTo(10000L);
		assertThat(row.getValue().getTransactionTime()).isNull();
		assertThat(row.getValue().getTransactionNature()).isNull();
		assertThat(row.getValue().getAttributionMonth()).isEqualTo("2026-11");
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<LedgerUsageEffectEntity>> saved = ArgumentCaptor.forClass(List.class);
		verify(this.effects).saveAll(saved.capture());
		assertThat(saved.getValue()).extracting(LedgerUsageEffectEntity::getLimitBucketId,
				LedgerUsageEffectEntity::getDeductionWon, LedgerUsageEffectEntity::getLedgerId)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("GREAT", 2100L, 9001L),
					org.assertj.core.groups.Tuple.tuple("COMMON", null, 9001L));
	}

	@Test
	void entriesMustMatchTheirMethod() {
		assertThatThrownBy(() -> this.writer.recordCancellation(approval()))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void entryShapeFollowsTheEntryType() {
		assertThatThrownBy(() -> entry(LedgerEntryType.APPROVAL, -1, null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> entry(LedgerEntryType.CANCELLATION, -1, null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> entry(LedgerEntryType.CANCELLATION, 1, 9001L))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(entry(LedgerEntryType.CANCELLATION, -4000, 9001L).originalLedgerId()).isEqualTo(9001L);
	}

	@Test
	void externalIdIsLtPlusNumber() {
		assertThat(LedgerTransactionId.format(9001)).isEqualTo("lt-9001");
		assertThat(LedgerTransactionId.parse("lt-9001")).contains(9001L);
		assertThat(LedgerTransactionId.parse("lt-09001")).isEmpty();
		assertThat(LedgerTransactionId.parse("pay-1")).isEmpty();
	}

	private static NewLedgerEntry approval() {
		return entry(LedgerEntryType.APPROVAL, 10000, null);
	}

	private static NewLedgerEntry entry(LedgerEntryType type, long amount, Long original) {
		return new NewLedgerEntry(1L, 1001L, type, amount, LocalDate.of(2026, 11, 3), null, "STARBUCKS_STORE", "DIRECT",
				"스타벅스 송도점", original, 7001L, YearMonth.of(2026, 11), AttributionStatus.CONFIRMED, AT);
	}

}
