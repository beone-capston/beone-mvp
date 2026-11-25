package com.beone.api.codef.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import com.beone.api.codef.CodefCardPerformance;
import com.beone.api.codef.CodefList;
import com.beone.api.codef.CodefProductResponseReader;
import com.beone.api.codef.sync.CodefPerformanceReference;
import com.beone.api.codef.sync.CodefTargetOutcome;
import com.beone.api.codef.transport.CodefCallResult;
import org.junit.jupiter.api.Test;

/**
 * 동기화 서비스의 순수 판정: 실적 펼치기(관리자 판단 Q5: 실적현황 하나당 항목 하나, 모르는 값은 null, 누락·형식 오류 목록은 값을 모르는 항목
 * 하나로 0건과 구분)와 부분 실패 대상(docs/api/codef.md partialFailures, P6: HMAC 키 없음은 승인내역 실패). 합성 fixture만 쓴다.
 */
class CodefSyncServiceHelpersTest {

	private static final Instant CHECKED_AT = Instant.parse("2026-11-16T03:00:00Z");

	private static final LocalDateTime FETCHED_AT = LocalDateTime.of(2026, 11, 16, 12, 0);

	@Test
	void eachPerformanceEntryBecomesOneItemAndUnknownValuesStayNull() {
		List<CodefPerformanceReference> references = references("performances-kb.json");

		// 카드 A: 실적현황 2개 + 실적현황 목록이 빈 문자열인 혜택 하나
		assertThat(CodefSyncService.flatten(7L, references.get(0), FETCHED_AT)).containsExactly(
				new CodefSyncResult.PerformanceItem(7L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 15), 120000L,
						FETCHED_AT),
				new CodefSyncResult.PerformanceItem(7L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 120000L,
						FETCHED_AT),
				new CodefSyncResult.PerformanceItem(7L, null, null, null, FETCHED_AT));
		// 카드 B: 금액 "12만"·날짜 "2026-11-01"·없는 날짜 "20261131"은 읽지 못해 null이다(0원·빈 기간으로 바꾸지 않음)
		assertThat(CodefSyncService.flatten(null, references.get(1), FETCHED_AT))
			.containsExactly(new CodefSyncResult.PerformanceItem(null, null, null, null, FETCHED_AT));
		// 카드 C: 혜택 목록이 빈 문자열
		assertThat(CodefSyncService.flatten(null, references.get(2), FETCHED_AT))
			.containsExactly(new CodefSyncResult.PerformanceItem(null, null, null, null, FETCHED_AT));
	}

	@Test
	void emptyListsGiveNoItem() {
		CodefPerformanceReference noBenefits = new CodefPerformanceReference(Optional.of("가짜 카드"), Optional.empty(),
				Optional.empty(), new CodefList.Items<>(CodefList.Shape.ARRAY, List.of()), CHECKED_AT);
		CodefPerformanceReference noEntries = new CodefPerformanceReference(Optional.of("가짜 카드"), Optional.empty(),
				Optional.empty(),
				new CodefList.Items<>(CodefList.Shape.ARRAY,
						List.of(new CodefPerformanceReference.BenefitReference(Optional.empty(), Optional.empty(),
								new CodefList.Items<>(CodefList.Shape.ARRAY, List.of())))),
				CHECKED_AT);

		assertThat(CodefSyncService.flatten(1L, noBenefits, FETCHED_AT)).isEmpty();
		assertThat(CodefSyncService.flatten(1L, noEntries, FETCHED_AT)).isEmpty();
	}

	@Test
	void failuresListUnusableTargetsAndMissingApprovals() {
		CodefTargetOutcome<CodefPerformanceReference> usable = outcome("performances-kb.json");
		CodefTargetOutcome<Object> unusable = CodefTargetOutcome.judge(new CodefCallResult.Failed<>(
				CodefCallResult.Stage.PRODUCT, CodefCallResult.Reason.TIMEOUT, OptionalInt.empty()), CHECKED_AT,
				item -> item);

		assertThat(CodefSyncService.failures(usable, Optional.of(usable), usable)).isEmpty();
		assertThat(CodefSyncService.failures(usable, Optional.empty(), usable))
			.containsExactly(CodefSyncTarget.APPROVALS);
		assertThat(CodefSyncService.failures(unusable, Optional.of(unusable), unusable))
			.containsExactly(CodefSyncTarget.HELD_CARDS, CodefSyncTarget.APPROVALS, CodefSyncTarget.PERFORMANCES);
		assertThat(CodefSyncService.failures(usable, Optional.of(usable), unusable))
			.containsExactly(CodefSyncTarget.PERFORMANCES);
	}

	private static List<CodefPerformanceReference> references(String name) {
		return ((CodefTargetOutcome.Usable<CodefPerformanceReference>) outcome(name)).items();
	}

	private static CodefTargetOutcome<CodefPerformanceReference> outcome(String name) {
		CodefCallResult<CodefCardPerformance> call = new CodefCallResult.Answered<>(
				CodefProductResponseReader.readCardPerformances(200, body(name)));
		return CodefTargetOutcome.judge(call, CHECKED_AT, card -> CodefPerformanceReference.from(card, CHECKED_AT));
	}

	private static String body(String name) {
		try (InputStream in = CodefSyncServiceHelpersTest.class.getResourceAsStream("/codef/sync/" + name)) {
			assertThat(in).as("fixture %s", name).isNotNull();
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
