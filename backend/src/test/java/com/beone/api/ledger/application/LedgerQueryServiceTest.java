package com.beone.api.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import com.beone.api.card.application.ConfirmationMethod;
import com.beone.api.card.application.HoldingSource;
import com.beone.api.card.application.MappingStatus;
import com.beone.api.card.application.RecommendationEligibility;
import com.beone.api.card.application.UserCardMapping;
import com.beone.api.card.application.UserCardService;
import com.beone.api.card.application.UserCardView;
import com.beone.api.codef.application.CodefApprovalLedgerQuery;
import com.beone.api.codef.sync.CodefLedgerRowKind;
import com.beone.api.ledger.persistence.LedgerTransactionEntity;
import com.beone.api.ledger.persistence.LedgerTransactionRepository;
import com.beone.api.ledger.persistence.LedgerUsageEffectEntity;
import com.beone.api.ledger.persistence.LedgerUsageEffectRepository;
import com.beone.api.user.application.CurrentUser;
import com.beone.api.user.application.DemoUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 원장 조회 변환·거르기(docs/api/ledger.md). 기대값은 계약 응답 예시({@code lt-9001} 승인 10,000원, {@code lt-9002} 취소 -4,000원,
 * {@code pay-7001}, 2026-11 SIMULATION 완전)와 관리자 승인 규칙(#118: 거래일 기준 달, 거래일·ID 오름차순, CODEF는 완전 선언 안 함)에서 썼다.
 */
class LedgerQueryServiceTest {

	private static final CurrentUser ME = new CurrentUser(1L, DemoUser.MY_CARDS);

	private static final YearMonth NOV = YearMonth.of(2026, 11);

	private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 3, 10, 42);

	private final LedgerTransactionRepository repository = mock(LedgerTransactionRepository.class);

	private final UserCardService cards = mock(UserCardService.class);

	private final LedgerUsageEffectRepository effects = mock(LedgerUsageEffectRepository.class);

	private final CodefApprovalLedgerQuery codef = mock(CodefApprovalLedgerQuery.class);

	private final LedgerQueryService service = new LedgerQueryService(this.repository, this.effects, this.cards,
			this.codef);

	@BeforeEach
	void setUp() {
		given(this.cards.list(ME)).willReturn(List.of(card("uc-1001"), card("uc-1003")));
		given(this.repository.findByUserIdAndTransactionDateBetweenOrderByTransactionDateAscLedgerIdAsc(1L,
				LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)))
			.willReturn(List.of(approval(9001L, 1001L), cancellation(9002L, 1001L, 9001L), approval(9003L, 1003L)));
		// CODEF 카드가 없는 장부: CODEF 행·완전성 판단 대상이 없다
		given(this.codef.coverage(anyLong(), any(), any()))
			.willReturn(new CodefApprovalLedgerQuery.MonthCoverage(Map.of(), List.of()));
	}

	@Test
	void oneCardSimulationMatchesTheContractExample() {
		LedgerMonthView view = this.service.find(ME, NOV, "uc-1001", LedgerSource.SIMULATION);

		assertThat(view).isEqualTo(new LedgerMonthView("2026-11", List.of(
				new LedgerMonthView.Item("lt-9001", "uc-1001", "SIMULATION", "APPROVAL", 10000, "2026-11-03", null,
						"STARBUCKS_STORE", "DIRECT", null, "스타벅스 송도점", null, "pay-7001",
						new LedgerMonthView.AttributionMonth("2026-11", "CONFIRMED")),
				new LedgerMonthView.Item("lt-9002", "uc-1001", "SIMULATION", "CANCELLATION", -4000, "2026-11-03", null,
						"STARBUCKS_STORE", "DIRECT", null, "스타벅스 송도점", "lt-9001", "pay-7001",
						new LedgerMonthView.AttributionMonth("2026-11", "CONFIRMED"))),
				List.of(new LedgerMonthView.Coverage("uc-1001", "SIMULATION",
						new LedgerMonthView.Period("2026-11-01", "2026-11-30"), true, List.of()))));
	}

	@Test
	void withoutFiltersEveryHeldCardGetsOneCoveragePerSourceAndCodefIsNotDeclaredComplete() {
		LedgerMonthView view = this.service.find(ME, NOV, null, null);

		assertThat(view.items()).extracting(LedgerMonthView.Item::ledgerTransactionId)
			.containsExactly("lt-9001", "lt-9002", "lt-9003");
		assertThat(view.coverage()).extracting(c -> c.userCardId() + ":" + c.source() + ":" + c.complete())
			.containsExactly("uc-1001:SIMULATION:true", "uc-1001:CODEF:false", "uc-1003:SIMULATION:true",
					"uc-1003:CODEF:false");
		assertThat(view.coverage()).allSatisfy(c -> assertThat(c.incompleteReasons()).isEmpty());
	}

	@Test
	void codefRowsAreMergedAfterSimulationRowsOfTheSameDayWithTheirCoverage() {
		given(this.codef.rows(1L, List.of(1001L, 1003L), NOV)).willReturn(List.of(
				new CodefApprovalLedgerQuery.ApprovalRow(31, 1001, 41, CodefLedgerRowKind.APPROVAL_FULLY_CANCELED,
						LocalDate.of(2026, 11, 2), LocalTime.of(12, 30, 5), 12000),
				new CodefApprovalLedgerQuery.ApprovalRow(32, 1001, 41, CodefLedgerRowKind.CANCELLATION,
						LocalDate.of(2026, 11, 3), LocalTime.of(8, 0), -5000)));
		given(this.codef.coverage(1L, List.of(1001L, 1003L), NOV)).willReturn(new CodefApprovalLedgerQuery.MonthCoverage(
				Map.of(1001L, new CodefApprovalLedgerQuery.Coverage(List.of("PERIOD_NOT_COVERED"), 41L, AT)),
				List.of("UNDETERMINED_ROW")));

		LedgerMonthView view = this.service.find(ME, NOV, null, null);

		assertThat(view.items()).extracting(LedgerMonthView.Item::ledgerTransactionId)
			.containsExactly("car-31", "lt-9001", "lt-9002", "lt-9003", "car-32");
		assertThat(view.items().get(0)).isEqualTo(new LedgerMonthView.Item("car-31", "uc-1001", "CODEF", "APPROVAL",
				12000, "2026-11-02", "12:30:05", null, null, null, null, null, null,
				new LedgerMonthView.AttributionMonth("2026-11", "CONFIRMED"), "sync-41", true));
		// 취소는 접수일을 몰라 귀속월을 정하지 않는다(D-17)
		assertThat(view.items().get(4).attributionMonth())
			.isEqualTo(new LedgerMonthView.AttributionMonth(null, "NEEDS_CONFIRMATION"));
		assertThat(view.items().get(4).fullyCanceled()).isFalse();
		assertThat(view.items().get(1).syncId()).isNull();
		assertThat(view.coverage()).extracting(c -> c.userCardId() + ":" + c.source() + ":" + c.incompleteReasons())
			.containsExactly("uc-1001:SIMULATION:[]", "uc-1001:CODEF:[PERIOD_NOT_COVERED]", "uc-1003:SIMULATION:[]",
					"uc-1003:CODEF:[]", "null:CODEF:[UNDETERMINED_ROW]");
	}

	@Test
	void unlinkedCodefCoverageIsShownOnlyWithoutACardFilter() {
		given(this.codef.coverage(1L, List.of(1003L), NOV))
			.willReturn(new CodefApprovalLedgerQuery.MonthCoverage(Map.of(), List.of("UNDETERMINED_ROW")));

		assertThat(this.service.find(ME, NOV, "uc-1003", null).coverage())
			.extracting(LedgerMonthView.Coverage::userCardId)
			.containsExactly("uc-1003", "uc-1003");
	}

	@Test
	void simulationOnlyQueriesDoNotReadCodef() {
		this.service.find(ME, NOV, null, LedgerSource.SIMULATION);
		org.mockito.Mockito.verifyNoInteractions(this.codef);
	}

	@Test
	void manualCardHasNoCodefRowsOrReasons() {
		LedgerMonthView view = this.service.find(ME, NOV, "uc-1003", LedgerSource.CODEF);

		assertThat(view.items()).isEmpty();
		assertThat(view.coverage()).containsExactly(new LedgerMonthView.Coverage("uc-1003", "CODEF",
				new LedgerMonthView.Period("2026-11-01", "2026-11-30"), false, List.of()));
	}

	@Test
	void rowsOfCardsNoLongerHeldAreHidden() {
		given(this.cards.list(ME)).willReturn(List.of(card("uc-1003")));

		assertThat(this.service.find(ME, NOV, null, LedgerSource.SIMULATION).items())
			.extracting(LedgerMonthView.Item::ledgerTransactionId)
			.containsExactly("lt-9003");
	}

	@Test
	void timeIsFormattedWithSeconds() {
		LedgerTransactionEntity row = entity(9010L, 1001L, LedgerEntryType.APPROVAL, 5000, LocalTime.of(10, 40), null,
				null);
		given(this.repository.findByUserIdAndTransactionDateBetweenOrderByTransactionDateAscLedgerIdAsc(1L,
				LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)))
			.willReturn(List.of(row));

		assertThat(this.service.find(ME, NOV, "uc-1001", null).items().get(0).transactionTime()).isEqualTo("10:40:00");
	}

	@Test
	void unknownMalformedOrOtherUsersCardIsNotFound() {
		for (String id : new String[] { "uc-1002", "uc-01001", "1001", "", "lt-9001" }) {
			assertThatThrownBy(() -> this.service.find(ME, NOV, id, null)).as(id)
				.isInstanceOf(LedgerCardNotFoundException.class);
		}
	}

	@Test
	void activityAfterTheSnapshotCarriesTheOriginalApprovalDeductionsOnCancellations() {
		// 취소 행은 차감액이 없으므로 원 승인 행의 버킷 차감액을 붙여 추천 입력이 그 버킷을 판단하게 한다(#120)
		LocalDateTime snapshot = LocalDateTime.of(2026, 11, 3, 9, 0);
		given(this.repository.findActivityAfter(1L, 1001L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
				snapshot, LedgerEntryType.APPROVAL, LedgerEntryType.CANCELLATION))
			.willReturn(List.of(approval(9001, 1001), cancellation(9002, 1001, 8000)));
		given(this.effects.findByLedgerIdOrderByUsageEffectIdAsc(9001L))
			.willReturn(List.of(new LedgerUsageEffectEntity(9001L, "STORE", 500L)));
		given(this.effects.findByLedgerIdOrderByUsageEffectIdAsc(8000L))
			.willReturn(List.of(new LedgerUsageEffectEntity(8000L, "FUEL", null)));

		List<LedgerActivity> activity = this.service.activityAfter(ME, "uc-1001", NOV, snapshot);

		assertThat(activity).containsExactly(
				new LedgerActivity(LedgerEntryType.APPROVAL, 10000, List.of(new LedgerUsageEffect("STORE", 500L))),
				new LedgerActivity(LedgerEntryType.CANCELLATION, -4000, List.of(new LedgerUsageEffect("FUEL", null))));
	}

	@Test
	void activityOfAMalformedCardIdIsEmpty() {
		assertThat(this.service.activityAfter(ME, "uc-x", NOV, AT)).isEmpty();
	}

	private static LedgerTransactionEntity approval(long id, long card) {
		return entity(id, card, LedgerEntryType.APPROVAL, 10000, null, null, 7001L);
	}

	private static LedgerTransactionEntity cancellation(long id, long card, long original) {
		return entity(id, card, LedgerEntryType.CANCELLATION, -4000, null, original, 7001L);
	}

	private static LedgerTransactionEntity entity(long id, long card, LedgerEntryType type, long amount, LocalTime time,
			Long original, Long payment) {
		LedgerTransactionEntity entity = new LedgerTransactionEntity(1L, card, LedgerSource.SIMULATION, type, amount,
				LocalDate.of(2026, 11, 3), time, "STARBUCKS_STORE", "DIRECT", "스타벅스 송도점", original, payment,
				"2026-11", AttributionStatus.CONFIRMED, AT);
		ReflectionTestUtils.setField(entity, "ledgerId", id);
		return entity;
	}

	private static UserCardView card(String id) {
		return new UserCardView(id, "카드", HoldingSource.MANUAL, "09174",
				new UserCardMapping(MappingStatus.USER_CONFIRMED, "09174", ConfirmationMethod.MANUAL_SELECTION, AT), false,
				RecommendationEligibility.of(HoldingSource.MANUAL, MappingStatus.USER_CONFIRMED, false), AT);
	}

}
