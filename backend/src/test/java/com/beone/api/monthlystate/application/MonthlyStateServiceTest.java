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
import java.util.concurrent.atomic.AtomicInteger;

import com.beone.api.benefit.rule.loading.RuleCatalogLoader;
import com.beone.api.card.application.CardProductService;
import com.beone.api.card.application.ConfirmationMethod;
import com.beone.api.card.application.HoldingSource;
import com.beone.api.card.application.MappingStatus;
import com.beone.api.card.application.RecommendationEligibility;
import com.beone.api.card.application.UserCardMapping;
import com.beone.api.card.application.UserCardService;
import com.beone.api.card.application.UserCardView;
import com.beone.api.codef.application.CodefPerformanceReferenceQuery;
import com.beone.api.ledger.application.LedgerActivity;
import com.beone.api.ledger.application.LedgerEntryType;
import com.beone.api.ledger.application.LedgerQueryService;
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

	private final LedgerQueryService ledger = mock(LedgerQueryService.class);

	// 기본은 참고값 없음(Mockito 기본 응답이 빈 Optional)
	private final CodefPerformanceReferenceQuery codef = mock(CodefPerformanceReferenceQuery.class);

	private final List<BucketUsageEntity> savedUsages = new ArrayList<>();

	private final List<ServiceSelectionEntity> savedSelections = new ArrayList<>();

	private MonthlyStateService service;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-11-17T01:30:00Z"), ZoneOffset.UTC);
		CardProductService products = new CardProductService(RuleCatalogLoader.load(),
				new StaticListableBeanFactory().getBeanProvider(Clock.class));
		this.service = new MonthlyStateService(this.states, this.usages, this.selections, this.userCards, products,
				this.ledger, this.codef, clock);
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
	void afterSnapshotSummarizesLedgerActivityAfterTheSnapshotWithoutChangingStateValues() {
		// docs/api/monthly-states.md 109행: 화면 표시용 요약. 승인 금액은 승인 합계이고 취소는 건수로만 센다(관리자 승인 규칙)
		holds(confirmed("uc-11", "09271"));
		MonthlyStateEntity existing = new MonthlyStateEntity(1L, 11L, "2026-11");
		existing.replace("09271", NOW, 5000L, OCTOBER.from(), OCTOBER.to(), null, null, null, null, false, null);
		ReflectionTestUtils.setField(existing, "monthlyStateId", 5L);
		given(this.states.findByUserIdAndUserCardIdAndStateMonth(1L, 11L, "2026-11")).willReturn(Optional.of(existing));
		given(this.ledger.activityAfter(ME, "uc-11", NOVEMBER, NOW)).willReturn(List.of(
				new LedgerActivity(LedgerEntryType.APPROVAL, 30000, List.of()),
				new LedgerActivity(LedgerEntryType.APPROVAL, 12000, List.of()),
				new LedgerActivity(LedgerEntryType.CANCELLATION, -4000, List.of())));

		MonthlyStateView view = this.service.get(ME, "uc-11", NOVEMBER);

		assertThat(view.afterSnapshot()).isEqualTo(new MonthlyStateView.AfterSnapshot(2, 42000, 1));
		assertThat(view.previousMonthPerformanceWon()).isEqualTo(StateValue.manual(5000L, OCTOBER, NOW));
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
	void saveTimeIsReadAfterTheRowLockIsAcquired() {
		// 읽을 때마다 1초씩 가는 시계: 첫 읽기는 행 확보, 둘째 읽기가 잠금 뒤 저장 시각이어야 한다.
		AtomicInteger clockReads = new AtomicInteger();
		Clock ticking = new Clock() {

			private Instant next = Instant.parse("2026-11-17T01:30:00Z");

			@Override
			public ZoneOffset getZone() {
				return ZoneOffset.UTC;
			}

			@Override
			public Clock withZone(java.time.ZoneId zone) {
				return this;
			}

			@Override
			public Instant instant() {
				clockReads.incrementAndGet();
				Instant current = this.next;
				this.next = current.plusSeconds(1);
				return current;
			}

		};
		MonthlyStateService service = new MonthlyStateService(this.states, this.usages, this.selections,
				this.userCards, new CardProductService(RuleCatalogLoader.load(),
						new StaticListableBeanFactory().getBeanProvider(Clock.class)),
				this.ledger, this.codef, ticking);
		holds(confirmed("uc-11", "09271"));
		AtomicInteger readsWhenLocking = new AtomicInteger(-1);
		given(this.states.findForUpdateByUserIdAndUserCardIdAndStateMonth(1L, 11L, "2026-11")).willAnswer(invocation -> {
			readsWhenLocking.set(clockReads.get());
			return Optional.of(new MonthlyStateEntity(1L, 11L, "2026-11"));
		});

		MonthlyStateView view = service.replace(ME, "uc-11", NOVEMBER, previousOnly(1000));

		// 잠금 조회 시점에는 행 확보용 한 번만 읽었고, 저장 시각은 그 뒤에 읽었다.
		assertThat(readsWhenLocking.get()).isEqualTo(1);
		assertThat(clockReads.get()).isEqualTo(2);

		// Clock.withZone이 자기 자신을 돌려주므로 UTC 그대로 읽힌다(서울 변환은 다른 테스트가 확인).
		verify(this.states).insertIfAbsent(1L, 11L, "2026-11", LocalDateTime.of(2026, 11, 17, 1, 30, 0));
		assertThat(view.snapshotAt()).isEqualTo(LocalDateTime.of(2026, 11, 17, 1, 30, 1));
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

	@Test
	void sourceSelectionHasNoConflictForAnyHeldCardAndWritesNothing() {
		// 관리자 판단 P10·D-16: 월간 상태는 MANUAL만 저장하고 CODEF 실적은 참고값, INFERRED는 보류라 충돌이 없다.
		// 수동 값이 있어도, 매핑 전 카드여도, CODEF 카드여도, 가상 포트폴리오여도 같다
		holds(confirmed("uc-11", "09271"), withStatus("uc-12", MappingStatus.UNMAPPED),
				new UserCardView("uc-13", "합성 CODEF 카드", HoldingSource.CODEF, null,
						new UserCardMapping(MappingStatus.UNMAPPED, null, null, null), false,
						RecommendationEligibility.of(HoldingSource.CODEF, MappingStatus.UNMAPPED, false), NOW),
				new UserCardView("uc-31", "올라운드 티타늄카드", HoldingSource.SYNTHETIC, "09271", null, false,
						RecommendationEligibility.of(HoldingSource.SYNTHETIC, null, false), NOW));
		given(this.states.findByUserIdAndUserCardIdAndStateMonth(1L, 11L, "2026-11"))
			.willReturn(Optional.of(storedFor("09271")));
		SourceSelection selection = new SourceSelection("previousMonthPerformanceWon", OCTOBER, StateSource.CODEF,
				"cf-v1");

		for (String card : List.of("uc-11", "uc-12", "uc-13")) {
			assertThatThrownBy(() -> this.service.selectSource(ME, card, NOVEMBER, selection)).as(card)
				.isInstanceOf(SourceConflictNotFoundException.class);
		}
		assertThatThrownBy(() -> this.service.selectSource(new CurrentUser(2L, DemoUser.VIRTUAL_PORTFOLIO), "uc-31",
				NOVEMBER, selection))
			.isInstanceOf(SourceConflictNotFoundException.class);
		verify(this.states, never()).insertIfAbsent(any(), any(), any(), any());
		verify(this.states, never()).saveAndFlush(any());
		verify(this.usages, never()).saveAllAndFlush(any());
		verify(this.selections, never()).saveAllAndFlush(any());
	}

	@Test
	void sourceSelectionForMissingOrOtherUsersCardIsNotFound() {
		holds(confirmed("uc-11", "09271"));

		assertThatThrownBy(() -> this.service.selectSource(ME, "uc-12", NOVEMBER,
				new SourceSelection("registeredOn", OCTOBER, StateSource.MANUAL, "cf-v1")))
			.isInstanceOf(MonthlyStateCardNotFoundException.class);
	}

	// D-16 CODEF 실적 참고값(사용자 결정 2026-11-27, #281). 참고값 금액은 합성 값이다.

	private static final LocalDateTime FETCHED = LocalDateTime.of(2026, 11, 4, 9, 1, 0);

	private static CodefPerformanceReferenceQuery.Reference reference(long id, long cardId, DatePeriod period,
			Long won) {
		return new CodefPerformanceReferenceQuery.Reference(id, 40L + id, cardId, period.from(), period.to(), won,
				FETCHED.plusHours(id));
	}

	private void latestReference(DatePeriod period, CodefPerformanceReferenceQuery.Reference reference) {
		given(this.codef.latest(1L, 11L, period.from(), period.to())).willReturn(Optional.ofNullable(reference));
	}

	private void storedPrevious(long value, CodefPerformanceReferenceQuery.Reference referenced) {
		MonthlyStateEntity entity = new MonthlyStateEntity(1L, 11L, "2026-11");
		entity.replace("09271", NOW, value, OCTOBER.from(), OCTOBER.to(), null, null, null, null, false, null);
		if (referenced != null) {
			entity.referPreviousMonthToCodef(referenced.referenceId(), referenced.syncId(), referenced.currentUseWon(),
					referenced.periodFrom(), referenced.periodTo(), referenced.fetchedAt());
		}
		ReflectionTestUtils.setField(entity, "monthlyStateId", 77L);
		given(this.states.findByUserIdAndUserCardIdAndStateMonth(1L, 11L, "2026-11")).willReturn(Optional.of(entity));
	}

	@Test
	void previousMonthReferenceIsShownForPrefillButNotUsed() {
		holds(confirmed("uc-11", "09271"));
		given(this.states.findByUserIdAndUserCardIdAndStateMonth(1L, 11L, "2026-11")).willReturn(Optional.empty());
		latestReference(OCTOBER, reference(5, 11, OCTOBER, 518_000L));

		MonthlyStateView view = this.service.get(ME, "uc-11", NOVEMBER);

		// 참고값은 미리 채우기용으로만 나오고, 전월 실적은 여전히 미입력이다
		assertThat(view.codefReference()).isEqualTo(new MonthlyStateView.CodefReference(5, OCTOBER, 518_000L,
				FETCHED.plusHours(5)));
		assertThat(view.previousMonthPerformanceWon()).isEqualTo(StateValue.unknown());
		assertThat(view.previousMonthPerformanceWon().reason()).isEqualTo(StateReason.NOT_ENTERED);
		assertThat(view.conflicts()).isEmpty();
	}

	@Test
	void referenceWithUnknownAmountStaysNullAndDoesNotConflict() {
		holds(confirmed("uc-11", "09271"));
		storedPrevious(500_000, null);
		latestReference(OCTOBER, reference(5, 11, OCTOBER, null));

		MonthlyStateView view = this.service.get(ME, "uc-11", NOVEMBER);

		assertThat(view.codefReference().currentUseWon()).isNull();
		assertThat(view.previousMonthPerformanceWon().usable()).isTrue();
		assertThat(view.conflicts()).isEmpty();
	}

	@Test
	void manualValueWithoutReferenceConflictsWithADifferentReference() {
		// Q1: 참고 없이 입력한 값도 같은 기준 기간의 다른 참고값과 다르면 충돌이고, Q2: 계산에서 빠진다
		holds(confirmed("uc-11", "09271"));
		storedPrevious(500_000, null);
		latestReference(OCTOBER, reference(5, 11, OCTOBER, 518_000L));

		MonthlyStateView view = this.service.get(ME, "uc-11", NOVEMBER);

		StateValue<Long> previous = view.previousMonthPerformanceWon();
		assertThat(previous.value()).isEqualTo(500_000L);
		assertThat(previous.source()).isEqualTo(StateSource.MANUAL);
		assertThat(previous.usable()).isFalse();
		assertThat(previous.reason()).isEqualTo(StateReason.SOURCE_CONFLICT);
		assertThat(view.conflicts()).containsExactly(new MonthlyStateView.Conflict("previousMonthPerformanceWon",
				OCTOBER, "cf-5-20261117103000",
				List.of(new MonthlyStateView.Candidate(StateSource.MANUAL, 500_000L, OCTOBER, NOW),
						new MonthlyStateView.Candidate(StateSource.CODEF, 518_000L, OCTOBER, FETCHED.plusHours(5)))));
	}

	@Test
	void sameValueOrDifferentPeriodIsNotAConflict() {
		holds(confirmed("uc-11", "09271"));
		storedPrevious(518_000, null);
		latestReference(OCTOBER, reference(5, 11, OCTOBER, 518_000L));

		assertThat(this.service.get(ME, "uc-11", NOVEMBER).conflicts()).isEmpty();

		// 기준 기간이 다른 값: 그 기간의 참고값이 없으므로 충돌이 아니다
		MonthlyStateEntity entity = new MonthlyStateEntity(1L, 11L, "2026-11");
		entity.replace("09271", NOW, 1L, LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 14), null, null, null, null,
				false, null);
		given(this.states.findByUserIdAndUserCardIdAndStateMonth(1L, 11L, "2026-11")).willReturn(Optional.of(entity));

		MonthlyStateView shifted = this.service.get(ME, "uc-11", NOVEMBER);
		assertThat(shifted.conflicts()).isEmpty();
		assertThat(shifted.previousMonthPerformanceWon().usable()).isTrue();
	}

	@Test
	void valueSavedFromThatReferenceIsNotAConflictUntilANewerReferenceDiffers() {
		holds(confirmed("uc-11", "09271"));
		CodefPerformanceReferenceQuery.Reference seen = reference(5, 11, OCTOBER, 518_000L);
		// 사용자는 518,000원을 보고 510,000원으로 확인했다
		storedPrevious(510_000, seen);
		latestReference(OCTOBER, seen);

		MonthlyStateView same = this.service.get(ME, "uc-11", NOVEMBER);
		assertThat(same.conflicts()).isEmpty();
		assertThat(same.previousMonthPerformanceWon().usable()).isTrue();
		assertThat(same.referencedCodef()).isEqualTo(new MonthlyStateView.ReferencedCodef(5L, 45L, OCTOBER, 518_000L,
				FETCHED.plusHours(5)));

		// 재조회로 다른 값이 오면 이전 확인을 그대로 쓰지 않고 다시 충돌로 드러낸다
		latestReference(OCTOBER, reference(6, 11, OCTOBER, 530_000L));
		MonthlyStateView changed = this.service.get(ME, "uc-11", NOVEMBER);
		assertThat(changed.previousMonthPerformanceWon().reason()).isEqualTo(StateReason.SOURCE_CONFLICT);
		assertThat(changed.conflicts()).hasSize(1);
		assertThat(changed.referencedCodef().referenceId()).isEqualTo(5L);

		// 새 참고값이 사용자 값과 같으면 충돌이 아니다
		latestReference(OCTOBER, reference(7, 11, OCTOBER, 510_000L));
		assertThat(this.service.get(ME, "uc-11", NOVEMBER).conflicts()).isEmpty();
	}

	@Test
	void replaceWithReferenceStoresManualValueAndTheReferenceSnapshot() {
		holds(confirmed("uc-11", "09271"));
		CodefPerformanceReferenceQuery.Reference seen = reference(5, 11, OCTOBER, 518_000L);
		given(this.codef.find(1L, 5L)).willReturn(Optional.of(seen));
		latestReference(OCTOBER, seen);

		MonthlyStateView view = this.service.replace(ME, "uc-11", NOVEMBER, new MonthlyStateInput(
				new MonthlyStateInput.Amount(510_000, OCTOBER, 5L), null, null, null, null, null));

		// Q3: 값은 사용자가 정한 MANUAL이고, 참고한 값이 같이 남으며 충돌이 아니다
		assertThat(view.previousMonthPerformanceWon()).isEqualTo(StateValue.manual(510_000L, OCTOBER, NOW));
		assertThat(view.referencedCodef()).isEqualTo(new MonthlyStateView.ReferencedCodef(5L, 45L, OCTOBER, 518_000L,
				FETCHED.plusHours(5)));
		assertThat(view.codefReference().referenceId()).isEqualTo(5L);
		assertThat(view.conflicts()).isEmpty();
	}

	@Test
	void replaceWithoutReferenceClearsAPreviousReferenceSnapshot() {
		holds(confirmed("uc-11", "09271"));
		MonthlyStateEntity locked = new MonthlyStateEntity(1L, 11L, "2026-11");
		locked.replace("09271", NOW, 1L, OCTOBER.from(), OCTOBER.to(), null, null, null, null, false, null);
		locked.referPreviousMonthToCodef(5, 45, 518_000L, OCTOBER.from(), OCTOBER.to(), FETCHED);
		given(this.states.findForUpdateByUserIdAndUserCardIdAndStateMonth(1L, 11L, "2026-11"))
			.willReturn(Optional.of(locked));

		MonthlyStateView view = this.service.replace(ME, "uc-11", NOVEMBER, previousOnly(2000));

		assertThat(view.referencedCodef()).isNull();
		assertThat(locked.getPreviousMonthCodefFetchedAt()).isNull();
		assertThat(locked.getPreviousMonthCodefReferenceId()).isNull();
	}

	@Test
	void referenceOfAnotherCardOrPeriodOrUserIsRejectedBeforeSaving() {
		holds(confirmed("uc-11", "09271"));
		given(this.codef.find(1L, 5L)).willReturn(Optional.of(reference(5, 12, OCTOBER, 518_000L)));
		given(this.codef.find(1L, 6L)).willReturn(Optional.of(reference(6, 11, NOVEMBER_PERIOD, 518_000L)));
		// 7은 다른 사용자의 참고값이라 이 사용자로는 찾지 못한다(기본 빈 값)

		for (long id : new long[] { 5L, 6L, 7L }) {
			assertThatThrownBy(() -> this.service.replace(ME, "uc-11", NOVEMBER, new MonthlyStateInput(
					new MonthlyStateInput.Amount(510_000, OCTOBER, id), null, null, null, null, null)))
				.as("reference %d", id)
				.isInstanceOfSatisfying(RuleMismatchException.class, ex -> assertThat(ex.violations())
					.extracting(RuleMismatchException.Violation::field)
					.containsExactly("previousMonthPerformanceWon.referencedCodefId"));
		}
		verify(this.states, never()).insertIfAbsent(any(), any(), any(), any());
		verify(this.states, never()).saveAndFlush(any());
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
				// CANDIDATE는 후보 상품을 함께 가져야 한다(#268)
				new UserCardMapping(status, null, ConfirmationMethod.MANUAL_SELECTION, NOW,
						(status == MappingStatus.CANDIDATE) ? "09174" : null),
				false,
				RecommendationEligibility.of(HoldingSource.MANUAL, status, false), NOW);
	}

}
