package com.beone.api.monthlystate.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.beone.api.benefit.rule.loading.RuleCatalogLoader;
import com.beone.api.card.application.CardProductService;
import com.beone.api.card.application.ConfirmationMethod;
import com.beone.api.card.application.HoldingSource;
import com.beone.api.card.application.MappingStatus;
import com.beone.api.card.application.RecommendationEligibility;
import com.beone.api.card.application.UserCardMapping;
import com.beone.api.card.application.UserCardService;
import com.beone.api.card.application.UserCardView;
import com.beone.api.monthlystate.persistence.BucketUsageEntity;
import com.beone.api.monthlystate.persistence.BucketUsageRepository;
import com.beone.api.monthlystate.persistence.MonthlyStateEntity;
import com.beone.api.monthlystate.persistence.MonthlyStateRepository;
import com.beone.api.monthlystate.persistence.ServiceSelectionEntity;
import com.beone.api.monthlystate.persistence.ServiceSelectionRepository;
import com.beone.api.user.application.CurrentUser;
import com.beone.api.user.application.DemoUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 월간 상태 서비스의 소유 확인·매핑 409·규칙 ID 검사·교체 저장·조회 변환을 확인한다. 카드 규칙은 실제 규칙 파일(카드 4종)을 읽는다.
 *
 * <p>기대값은 docs/api/monthly-states.md와 결정 27 A3절에서 옮겼다. 09271은 선택형 서비스 FUEL·STORE와 한도 묶음 FUEL·STORE가,
 * 09174는 선택형 서비스 없이 한도 묶음 GREAT·ENJOY·CHECK·BASIC이 있다.
 */
class MonthlyStateServiceTest {

	private static final CurrentUser ME = new CurrentUser(1L, DemoUser.MY_CARDS);

	private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 10, 30, 0);

	private static final DatePeriod OCTOBER = new DatePeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

	private static final DatePeriod NOVEMBER_PERIOD = new DatePeriod(LocalDate.of(2026, 11, 1),
			LocalDate.of(2026, 11, 30));

	private final MonthlyStateRepository states = mock(MonthlyStateRepository.class);

	private final BucketUsageRepository usages = mock(BucketUsageRepository.class);

	private final ServiceSelectionRepository selections = mock(ServiceSelectionRepository.class);

	private final UserCardService userCards = mock(UserCardService.class);

	private final List<BucketUsageEntity> savedUsages = new ArrayList<>();

	private final List<ServiceSelectionEntity> savedSelections = new ArrayList<>();

	private MonthlyStateService service;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-11-17T01:30:00Z"), ZoneOffset.UTC);
		CardProductService products = new CardProductService(RuleCatalogLoader.load(),
				new StaticListableBeanFactory().getBeanProvider(Clock.class));
		this.service = new MonthlyStateService(this.states, this.usages, this.selections, this.userCards, products,
				clock);
		given(this.states.saveAndFlush(any())).willAnswer(invocation -> {
			MonthlyStateEntity entity = invocation.getArgument(0);
			if (entity.getMonthlyStateId() == null) {
				ReflectionTestUtils.setField(entity, "monthlyStateId", 77L);
			}
			return entity;
		});
		given(this.usages.saveAllAndFlush(any())).willAnswer(invocation -> {
			Iterable<BucketUsageEntity> rows = invocation.getArgument(0);
			rows.forEach(this.savedUsages::add);
			return this.savedUsages;
		});
		given(this.selections.saveAllAndFlush(any())).willAnswer(invocation -> {
			Iterable<ServiceSelectionEntity> rows = invocation.getArgument(0);
			rows.forEach(this.savedSelections::add);
			return this.savedSelections;
		});
		given(this.usages.findByMonthlyStateIdOrderBySeqAsc(anyLong())).willAnswer(invocation -> this.savedUsages);
		given(this.selections.findByMonthlyStateIdOrderBySeqAsc(anyLong()))
			.willAnswer(invocation -> this.savedSelections);
	}

	@Test
	void monthNeverEnteredIsAllUnknownNotZero() {
		holds(confirmed("uc-11", "09271"));
		given(this.states.findByUserIdAndUserCardIdAndStateMonth(1L, 11L, "2026-11")).willReturn(Optional.empty());

		MonthlyStateView view = this.service.get(ME, "uc-11", NOVEMBER);

		assertThat(view.snapshotAt()).isNull();
		assertThat(view.previousMonthPerformanceWon()).isEqualTo(StateValue.unknown());
		assertThat(view.previousMonthPerformanceWon().value()).isNull();
		assertThat(view.previousMonthPerformanceWon().usable()).isFalse();
		assertThat(view.previousMonthPerformanceWon().reason()).isEqualTo(StateReason.NOT_ENTERED);
		assertThat(view.limitBucketUsages()).isEmpty();
		assertThat(view.otherBucketsUsedWon().source()).isEqualTo(StateSource.UNKNOWN);
		assertThat(view.selectedServices()).isEmpty();
		assertThat(view.serviceSelectionStatus()).isEqualTo(ServiceSelectionStatus.UNKNOWN);
		assertThat(view.noServiceSelection()).isNull();
		assertThat(view.registeredOn().source()).isEqualTo(StateSource.UNKNOWN);
		assertThat(view.afterSnapshot()).isNull();
	}

	@Test
	void missingOrOtherUsersCardIsNotFound() {
		holds(confirmed("uc-11", "09271"));

		assertThatThrownBy(() -> this.service.get(ME, "uc-12", NOVEMBER))
			.isInstanceOf(MonthlyStateCardNotFoundException.class);
		assertThatThrownBy(() -> this.service.replace(ME, "uc-12", NOVEMBER, previousOnly(-1000)))
			.isInstanceOf(MonthlyStateCardNotFoundException.class);
		verify(this.states, never()).insertIfAbsent(any(), any(), any(), any());
		verify(this.states, never()).saveAndFlush(any());
	}

	@Test
	void replaceStoresManualValuesWithSaveTimeAndKeepsNegativePerformance() {
		holds(confirmed("uc-11", "09271"));
		MonthlyStateInput input = new MonthlyStateInput(new MonthlyStateInput.Amount(-12000, OCTOBER),
				List.of(new MonthlyStateInput.BucketUsage("STORE", 0, NOVEMBER_PERIOD),
						new MonthlyStateInput.BucketUsage("FUEL", 4000, NOVEMBER_PERIOD)),
				new MonthlyStateInput.Amount(1500, NOVEMBER_PERIOD),
				List.of(new MonthlyStateInput.Selection("STORE", LocalDate.of(2026, 10, 15)),
						new MonthlyStateInput.Selection("FUEL", null)),
				null, LocalDate.of(2026, 10, 2));

		MonthlyStateView view = this.service.replace(ME, "uc-11", NOVEMBER, input);

		assertThat(view.snapshotAt()).isEqualTo(NOW);
		assertThat(view.previousMonthPerformanceWon()).isEqualTo(StateValue.manual(-12000L, OCTOBER, NOW));
		assertThat(view.previousMonthPerformanceWon().usable()).isTrue();
		assertThat(view.previousMonthPerformanceWon().reason()).isNull();
		assertThat(view.limitBucketUsages()).containsExactly(
				new MonthlyStateView.BucketUsage("STORE", StateValue.manual(0L, NOVEMBER_PERIOD, NOW)),
				new MonthlyStateView.BucketUsage("FUEL", StateValue.manual(4000L, NOVEMBER_PERIOD, NOW)));
		assertThat(view.otherBucketsUsedWon()).isEqualTo(StateValue.manual(1500L, NOVEMBER_PERIOD, NOW));
		assertThat(view.selectedServices()).containsExactly(
				new MonthlyStateView.Selection("STORE", LocalDate.of(2026, 10, 15), NOW),
				new MonthlyStateView.Selection("FUEL", null, NOW));
		assertThat(view.selectedServices().get(1).usable()).isFalse();
		assertThat(view.selectedServices().get(1).reason()).isEqualTo(StateReason.NOT_ENTERED);
		assertThat(view.serviceSelectionStatus()).isEqualTo(ServiceSelectionStatus.SELECTED);
		LocalDate registered = LocalDate.of(2026, 10, 2);
		assertThat(view.registeredOn())
			.isEqualTo(StateValue.manual(registered, new DatePeriod(registered, registered), NOW));
		assertThat(view.afterSnapshot()).isNull();
		verify(this.states).insertIfAbsent(1L, 11L, "2026-11", NOW);
		verify(this.usages).deleteByMonthlyStateId(77L);
		verify(this.selections).deleteByMonthlyStateId(77L);
	}

	@Test
	void omittedItemsBecomeUnknownOnReplace() {
		holds(confirmed("uc-11", "09271"));
		MonthlyStateEntity existing = new MonthlyStateEntity(1L, 11L, "2026-11");
		existing.replace("09271", NOW.minusDays(1), 5000L, OCTOBER.from(), OCTOBER.to(), 100L, NOVEMBER_PERIOD.from(),
				NOVEMBER_PERIOD.to(), LocalDate.of(2026, 10, 2), true, LocalDate.of(2026, 11, 1));
		ReflectionTestUtils.setField(existing, "monthlyStateId", 5L);
		given(this.states.findForUpdateByUserIdAndUserCardIdAndStateMonth(1L, 11L, "2026-11"))
			.willReturn(Optional.of(existing));

		MonthlyStateView view = this.service.replace(ME, "uc-11", NOVEMBER, previousOnly(0));

		assertThat(view.previousMonthPerformanceWon()).isEqualTo(StateValue.manual(0L, OCTOBER, NOW));
		assertThat(view.otherBucketsUsedWon()).isEqualTo(StateValue.unknown());
		assertThat(view.registeredOn()).isEqualTo(StateValue.unknown());
		assertThat(view.serviceSelectionStatus()).isEqualTo(ServiceSelectionStatus.UNKNOWN);
		assertThat(view.noServiceSelection()).isNull();
		assertThat(existing.isNoServiceSelection()).isFalse();
		verify(this.usages).deleteByMonthlyStateId(5L);
	}

	@Test
	void noServiceSelectionIsStoredForCardWithSelectionServices() {
		holds(confirmed("uc-11", "09271"));
		MonthlyStateInput input = new MonthlyStateInput(null, null, null, null,
				new MonthlyStateInput.NoSelection(LocalDate.of(2026, 11, 1)), null);

		MonthlyStateView view = this.service.replace(ME, "uc-11", NOVEMBER, input);

		assertThat(view.serviceSelectionStatus()).isEqualTo(ServiceSelectionStatus.NONE);
		assertThat(view.noServiceSelection())
			.isEqualTo(new MonthlyStateView.NoSelection(LocalDate.of(2026, 11, 1), NOW));
		assertThat(view.noServiceSelection().usable()).isTrue();
	}

	@Test
	void unconfirmedMappingRejectsBucketsAndSelectionsButAcceptsOtherValues() {
		holds(withStatus("uc-21", MappingStatus.NOT_SUPPORTED), withStatus("uc-22", MappingStatus.UNMAPPED),
				withStatus("uc-23", MappingStatus.CANDIDATE));
		List<MonthlyStateInput> ruleInputs = List.of(
				new MonthlyStateInput(null, List.of(new MonthlyStateInput.BucketUsage("FUEL", 0, NOVEMBER_PERIOD)),
						null, null, null, null),
				new MonthlyStateInput(null, null, null, List.of(new MonthlyStateInput.Selection("FUEL", null)), null,
						null),
				new MonthlyStateInput(null, null, null, null, new MonthlyStateInput.NoSelection(null), null));
		for (String id : List.of("uc-21", "uc-22", "uc-23")) {
			for (MonthlyStateInput input : ruleInputs) {
				assertThatThrownBy(() -> this.service.replace(ME, id, NOVEMBER, input))
					.isInstanceOf(MappingRequiredException.class);
			}
		}
		verify(this.states, never()).saveAndFlush(any());

		MonthlyStateInput withoutRule = new MonthlyStateInput(new MonthlyStateInput.Amount(-500, OCTOBER), List.of(),
				new MonthlyStateInput.Amount(300, NOVEMBER_PERIOD), List.of(), null, LocalDate.of(2026, 9, 1));
		MonthlyStateView view = this.service.replace(ME, "uc-21", NOVEMBER, withoutRule);

		assertThat(view.previousMonthPerformanceWon().value()).isEqualTo(-500L);
		assertThat(view.otherBucketsUsedWon().value()).isEqualTo(300L);
		assertThat(view.registeredOn().value()).isEqualTo(LocalDate.of(2026, 9, 1));
	}

	@Test
	void virtualPortfolioCardUsesItsProductRuleAndCanWrite() {
		holds(new UserCardView("uc-31", "올라운드 티타늄카드", HoldingSource.SYNTHETIC, "09271", null, false,
				RecommendationEligibility.of(HoldingSource.SYNTHETIC, null, false), NOW));
		MonthlyStateInput input = new MonthlyStateInput(null,
				List.of(new MonthlyStateInput.BucketUsage("FUEL", 1000, NOVEMBER_PERIOD)), null, null, null, null);

		MonthlyStateView view = this.service.replace(new CurrentUser(2L, DemoUser.VIRTUAL_PORTFOLIO), "uc-31",
				NOVEMBER, input);

		assertThat(view.limitBucketUsages()).extracting(MonthlyStateView.BucketUsage::limitBucketId)
			.containsExactly("FUEL");
	}

	@Test
	void idsOutsideTheCardRuleAreRejectedWithFieldPaths() {
		holds(confirmed("uc-11", "09271"));
		MonthlyStateInput input = new MonthlyStateInput(null,
				List.of(new MonthlyStateInput.BucketUsage("FUEL", 0, NOVEMBER_PERIOD),
						new MonthlyStateInput.BucketUsage("GREAT", 0, NOVEMBER_PERIOD)),
				null, List.of(new MonthlyStateInput.Selection("BASE", null)), null, null);

		assertThatThrownBy(() -> this.service.replace(ME, "uc-11", NOVEMBER, input))
			.isInstanceOfSatisfying(RuleMismatchException.class,
					ex -> assertThat(ex.violations()).containsExactly(
							new RuleMismatchException.Violation("limitBucketUsages[1].limitBucketId",
									"카드 규칙에 없는 값입니다."),
							new RuleMismatchException.Violation("selectedServices[0].serviceId",
									"카드 규칙에 없는 값입니다.")));
		verify(this.states, never()).saveAndFlush(any());
	}

	@Test
	void noServiceSelectionOnCardWithoutSelectionServicesIsRejected() {
		holds(confirmed("uc-12", "09174"));
		MonthlyStateInput input = new MonthlyStateInput(null, null, null, null,
				new MonthlyStateInput.NoSelection(LocalDate.of(2026, 11, 1)), null);

		assertThatThrownBy(() -> this.service.replace(ME, "uc-12", NOVEMBER, input))
			.isInstanceOfSatisfying(RuleMismatchException.class,
					ex -> assertThat(ex.violations()).containsExactly(
							new RuleMismatchException.Violation("noServiceSelection", "선택형 서비스가 없는 카드입니다.")));
	}

	@Test
	void monthWithoutApplicableRuleRejectsRuleItems() {
		// 09174 규칙은 2026-07-14부터 적용된다. 2026-06은 1일·말일 모두 규칙이 없다.
		holds(confirmed("uc-12", "09174"));
		MonthlyStateInput input = new MonthlyStateInput(null,
				List.of(new MonthlyStateInput.BucketUsage("GREAT", 0, OCTOBER)), null, null, null, null);

		assertThatThrownBy(() -> this.service.replace(ME, "uc-12", YearMonth.of(2026, 6), input))
			.isInstanceOf(RuleMismatchException.class);
		// 규칙이 생기는 달(7월 말일 기준)은 받는다.
		assertThat(this.service.replace(ME, "uc-12", YearMonth.of(2026, 7), input).limitBucketUsages()).hasSize(1);
	}

	@Test
	void getShowsStoredStateWithAfterSnapshotOfZeroWhileNoSimulatedPaymentsExist() {
		holds(confirmed("uc-11", "09271"));
		MonthlyStateEntity stored = storedFor("09271");
		given(this.states.findByUserIdAndUserCardIdAndStateMonth(1L, 11L, "2026-11")).willReturn(Optional.of(stored));
		this.savedUsages.add(new BucketUsageEntity(9L, 0, "FUEL", 4000, NOVEMBER_PERIOD.from(), NOVEMBER_PERIOD.to()));
		this.savedSelections.add(new ServiceSelectionEntity(9L, 0, "STORE", LocalDate.of(2026, 10, 15)));

		MonthlyStateView view = this.service.get(ME, "uc-11", NOVEMBER);

		assertThat(view.snapshotAt()).isEqualTo(NOW);
		assertThat(view.limitBucketUsages()).hasSize(1);
		assertThat(view.selectedServices()).hasSize(1);
		assertThat(view.afterSnapshot()).isEqualTo(new MonthlyStateView.AfterSnapshot(0, 0, 0));
	}

	@Test
	void mappingChangedAfterSaveHidesBucketAndSelectionValuesOnly() {
		holds(confirmed("uc-11", "09174"));
		MonthlyStateEntity stored = storedFor("09271");
		given(this.states.findByUserIdAndUserCardIdAndStateMonth(1L, 11L, "2026-11")).willReturn(Optional.of(stored));
		this.savedUsages.add(new BucketUsageEntity(9L, 0, "FUEL", 4000, NOVEMBER_PERIOD.from(), NOVEMBER_PERIOD.to()));
		this.savedSelections.add(new ServiceSelectionEntity(9L, 0, "STORE", LocalDate.of(2026, 10, 15)));

		MonthlyStateView view = this.service.get(ME, "uc-11", NOVEMBER);

		assertThat(view.limitBucketUsages()).isEmpty();
		assertThat(view.selectedServices()).isEmpty();
		assertThat(view.serviceSelectionStatus()).isEqualTo(ServiceSelectionStatus.UNKNOWN);
		assertThat(view.previousMonthPerformanceWon().value()).isEqualTo(5000L);
		assertThat(view.registeredOn().value()).isEqualTo(LocalDate.of(2026, 10, 2));
	}

	private MonthlyStateEntity storedFor(String productId) {
		MonthlyStateEntity stored = new MonthlyStateEntity(1L, 11L, "2026-11");
		stored.replace(productId, NOW, 5000L, OCTOBER.from(), OCTOBER.to(), null, null, null, LocalDate.of(2026, 10, 2),
				false, null);
		ReflectionTestUtils.setField(stored, "monthlyStateId", 9L);
		return stored;
	}

	private void holds(UserCardView... cards) {
		given(this.userCards.list(ME)).willReturn(List.of(cards));
		given(this.userCards.list(new CurrentUser(2L, DemoUser.VIRTUAL_PORTFOLIO))).willReturn(List.of(cards));
		// insertIfAbsent가 만든 빈 행을 잠가 읽는 상황
		given(this.states.findForUpdateByUserIdAndUserCardIdAndStateMonth(anyLong(), anyLong(), anyString()))
			.willAnswer(invocation -> Optional.of(new MonthlyStateEntity(invocation.getArgument(0),
					invocation.getArgument(1), invocation.getArgument(2))));
	}

	private static MonthlyStateInput previousOnly(long value) {
		return new MonthlyStateInput(new MonthlyStateInput.Amount(value, OCTOBER), null, null, null, null, null);
	}

	private static UserCardView confirmed(String id, String productId) {
		return new UserCardView(id, "카드", HoldingSource.MANUAL, null,
				new UserCardMapping(MappingStatus.USER_CONFIRMED, productId, ConfirmationMethod.MANUAL_SELECTION, NOW),
				false, RecommendationEligibility.of(HoldingSource.MANUAL, MappingStatus.USER_CONFIRMED, false), NOW);
	}

	private static UserCardView withStatus(String id, MappingStatus status) {
		return new UserCardView(id, "카드", HoldingSource.MANUAL, null,
				new UserCardMapping(status, null, ConfirmationMethod.MANUAL_SELECTION, NOW), false,
				RecommendationEligibility.of(HoldingSource.MANUAL, status, false), NOW);
	}

}
