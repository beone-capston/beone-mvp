package com.beone.api.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import com.beone.api.benefit.engine.BucketUsageInput;
import com.beone.api.benefit.engine.StateInput;
import com.beone.api.ledger.application.LedgerActivity;
import com.beone.api.ledger.application.LedgerEntryType;
import com.beone.api.ledger.application.LedgerUsageEffect;
import org.junit.jupiter.api.Test;

/**
 * 스냅샷 뒤 사용액 반영(#120). 기대값은 docs/api/monthly-states.md 97·132~137·218행(스냅샷 뒤 승인만 더한다, 취소는 D-17 확정 규칙일
 * 때만 뺀다, 스냅샷 전 승인의 취소는 확인 필요)과 관리자 승인 규칙(결정 28 보충: 같은 버킷 가산, 미입력 버킷은 그 밖의 사용액에 가산, 4종 모두
 * 한도 복원 규칙이 없어 취소·모르는 차감액은 그 버킷을 확인 필요로, 출처·기간·시각은 스냅샷 값 유지)에서 썼다.
 */
class SnapshotUsageTest {

	private static final LocalDate FROM = LocalDate.of(2026, 11, 1);

	private static final LocalDate TO = LocalDate.of(2026, 11, 30);

	private static final OffsetDateTime SNAPSHOT = OffsetDateTime.parse("2026-11-17T10:30:00+09:00");

	@Test
	void withoutActivityTheSnapshotValuesPassUnchanged() {
		List<BucketUsageInput> buckets = List.of(new BucketUsageInput("STORE", manual(5000)));

		SnapshotUsage.Adjusted adjusted = SnapshotUsage.adjust(buckets, manual(2000), List.of());

		assertThat(adjusted.buckets()).isEqualTo(buckets);
		assertThat(adjusted.otherBuckets()).isEqualTo(manual(2000));
	}

	@Test
	void approvalDeductionsAreAddedToTheSameBucketKeepingSourcePeriodAndTime() {
		SnapshotUsage.Adjusted adjusted = SnapshotUsage.adjust(
				List.of(new BucketUsageInput("STORE", manual(5000)), new BucketUsageInput("FUEL", manual(0))),
				manual(2000), List.of(approval(effect("STORE", 1000L)), approval(effect("STORE", 500L))));

		assertThat(adjusted.buckets()).containsExactly(new BucketUsageInput("STORE", manual(6500)),
				new BucketUsageInput("FUEL", manual(0)));
		assertThat(adjusted.otherBuckets()).isEqualTo(manual(2000));
	}

	@Test
	void deductionsOfBucketsNotEnteredGoToOtherBucketsOnlyWhenThatValueIsKnown() {
		List<LedgerActivity> activity = List.of(approval(effect("FUEL", 700L)));

		assertThat(SnapshotUsage.adjust(List.of(), manual(2000), activity).otherBuckets()).isEqualTo(manual(2700));
		assertThat(SnapshotUsage.adjust(List.of(), StateInput.notEntered(), activity).otherBuckets())
			.isEqualTo(StateInput.notEntered());
	}

	@Test
	void anUnknownApprovalDeductionMakesTheBucketNeedConfirmationInsteadOfZero() {
		SnapshotUsage.Adjusted adjusted = SnapshotUsage.adjust(List.of(new BucketUsageInput("STORE", manual(5000))),
				manual(2000), List.of(approval(effect("STORE", 1000L)), approval(effect("STORE", null)),
						approval(effect("FUEL", null))));

		assertThat(adjusted.buckets()).containsExactly(new BucketUsageInput("STORE", unresolved()));
		assertThat(adjusted.otherBuckets()).isEqualTo(unresolved());
	}

	@Test
	void cancellingAnApprovalWithAPositiveDeductionMakesTheBucketNeedConfirmation() {
		// 스냅샷 전 승인(스냅샷에 포함)의 취소든 스냅샷 뒤 승인의 취소든 한도 복원 규칙이 없으니 확인 필요다(D-17)
		SnapshotUsage.Adjusted adjusted = SnapshotUsage.adjust(
				List.of(new BucketUsageInput("STORE", manual(5000)), new BucketUsageInput("FUEL", manual(300))),
				manual(2000), List.of(cancellation(effect("STORE", 1000L))));

		assertThat(adjusted.buckets()).containsExactly(new BucketUsageInput("STORE", unresolved()),
				new BucketUsageInput("FUEL", manual(300)));
		assertThat(adjusted.otherBuckets()).isEqualTo(manual(2000));
	}

	@Test
	void cancellingAnApprovalThatDeductedNothingChangesNothing() {
		List<BucketUsageInput> buckets = List.of(new BucketUsageInput("STORE", manual(5000)));

		SnapshotUsage.Adjusted adjusted = SnapshotUsage.adjust(buckets, manual(2000),
				List.of(approval(), cancellation(effect("STORE", 0L)), cancellation()));

		assertThat(adjusted.buckets()).isEqualTo(buckets);
		assertThat(adjusted.otherBuckets()).isEqualTo(manual(2000));
	}

	@Test
	void cancellingAnUnknownDeductionOutsideEnteredBucketsMakesOtherBucketsNeedConfirmation() {
		SnapshotUsage.Adjusted adjusted = SnapshotUsage.adjust(List.of(), manual(2000),
				List.of(cancellation(effect("FUEL", null))));

		assertThat(adjusted.otherBuckets()).isEqualTo(unresolved());
	}

	@Test
	void valuesThatWereNeverEnteredKeepNotEnteredEvenWhenActivityIsUnresolved() {
		// 결정 32 Q6: 진짜 미입력은 NOT_ENTERED 그대로다. 새 사유는 입력한 값에만 쓴다
		SnapshotUsage.Adjusted adjusted = SnapshotUsage.adjust(
				List.of(new BucketUsageInput("STORE", StateInput.notEntered())), StateInput.notEntered(),
				List.of(approval(effect("STORE", null)), cancellation(effect("FUEL", 500L))));

		assertThat(adjusted.buckets()).containsExactly(new BucketUsageInput("STORE", StateInput.notEntered()));
		assertThat(adjusted.otherBuckets()).isEqualTo(StateInput.notEntered());
		assertThat(adjusted.otherBuckets().reason()).isEqualTo("NOT_ENTERED");
	}

	@Test
	void unresolvedActivityChangesOnlyTheReasonOfTheEnteredValue() {
		StateInput<Long> value = SnapshotUsage
			.adjust(List.of(new BucketUsageInput("STORE", manual(5000))), manual(2000),
					List.of(approval(effect("STORE", null))))
			.buckets()
			.get(0)
			.usedWon();

		assertThat(value.value()).isNull();
		assertThat(value.source()).isEqualTo("UNKNOWN");
		assertThat(value.usable()).isFalse();
		assertThat(value.reason()).isEqualTo("POST_SNAPSHOT_ACTIVITY_UNRESOLVED");
	}

	private static StateInput<Long> unresolved() {
		return StateInput.postSnapshotActivityUnresolved();
	}

	private static StateInput<Long> manual(long value) {
		return StateInput.manual(value, FROM, TO, SNAPSHOT);
	}

	private static LedgerUsageEffect effect(String bucket, Long deduction) {
		return new LedgerUsageEffect(bucket, deduction);
	}

	private static LedgerActivity approval(LedgerUsageEffect... effects) {
		return new LedgerActivity(LedgerEntryType.APPROVAL, 10000, Arrays.asList(effects));
	}

	private static LedgerActivity cancellation(LedgerUsageEffect... effects) {
		return new LedgerActivity(LedgerEntryType.CANCELLATION, -10000, Arrays.asList(effects));
	}

}
