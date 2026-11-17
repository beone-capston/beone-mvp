package com.beone.api.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

import com.beone.api.benefit.engine.BucketUsageInput;
import com.beone.api.benefit.engine.HeldCardInput;
import com.beone.api.benefit.engine.MonthlyStateInput;
import com.beone.api.benefit.engine.OrderInput;
import com.beone.api.benefit.engine.PolicyEvidenceInput;
import com.beone.api.benefit.engine.ServiceSelectionInput;
import com.beone.api.benefit.engine.StateInput;
import com.beone.api.card.application.ConfirmationMethod;
import com.beone.api.card.application.HoldingSource;
import com.beone.api.card.application.MappingStatus;
import com.beone.api.card.application.RecommendationEligibility;
import com.beone.api.card.application.UserCardMapping;
import com.beone.api.card.application.UserCardView;
import com.beone.api.monthlystate.application.DatePeriod;
import com.beone.api.monthlystate.application.MonthlyStateView;
import com.beone.api.monthlystate.application.ServiceSelectionStatus;
import com.beone.api.monthlystate.application.StateValue;
import com.beone.api.order.application.OrderDetail;
import com.beone.api.order.application.OrderStatus;
import com.beone.api.order.application.PaymentMethod;
import org.junit.jupiter.api.Test;

/**
 * 업무 조회 결과를 엔진 입력으로 옮기는 규칙(docs/api/recommendations.md 엔진 입력 절, 결정 27 B2)을 확인한다.
 */
class EngineInputAssemblerTest {

	private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 3, 10, 20, 0);

	private static final OffsetDateTime AT_SEOUL = OffsetDateTime.of(AT, ZoneOffset.ofHours(9));

	private static final DatePeriod OCTOBER = new DatePeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

	private static final DatePeriod NOVEMBER = new DatePeriod(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

	@Test
	void orderIsPassedAsIs() {
		OrderDetail order = new OrderDetail("ord-5001", 10000, LocalDate.of(2026, 11, 3), LocalTime.of(10, 40), "카페",
				"STARBUCKS_STORE", PaymentMethod.DIRECT, OrderStatus.CREATED, AT);

		assertThat(EngineInputAssembler.order(order)).isEqualTo(new OrderInput("ord-5001", "카페", 10000,
				LocalDate.of(2026, 11, 3), LocalTime.of(10, 40), "STARBUCKS_STORE", "DIRECT"));
	}

	@Test
	void unknownStaysNotEnteredAndManualKeepsPeriodAndSeoulTime() {
		assertThat(EngineInputAssembler.state(StateValue.unknown())).isEqualTo(StateInput.notEntered());
		assertThat(EngineInputAssembler.state(StateValue.manual(-12000L, OCTOBER, AT)))
			.isEqualTo(StateInput.manual(-12000L, OCTOBER.from(), OCTOBER.to(), AT_SEOUL));
	}

	@Test
	void selectedServicesWithoutEffectiveFromStayUsableForTheEngineToJudge() {
		MonthlyStateView state = new MonthlyStateView("uc-1", YearMonth.of(2026, 11), AT,
				StateValue.manual(523000L, OCTOBER, AT),
				List.of(new MonthlyStateView.BucketUsage("STORE", StateValue.manual(0L, NOVEMBER, AT))),
				StateValue.unknown(),
				List.of(new MonthlyStateView.Selection("STORE", LocalDate.of(2026, 10, 15), AT),
						new MonthlyStateView.Selection("FUEL", null, AT)),
				ServiceSelectionStatus.SELECTED, null, StateValue.unknown(), null);

		MonthlyStateInput input = EngineInputAssembler.monthlyState(state);

		assertThat(input).isEqualTo(new MonthlyStateInput(
				StateInput.manual(523000L, OCTOBER.from(), OCTOBER.to(), AT_SEOUL),
				List.of(new BucketUsageInput("STORE", StateInput.manual(0L, NOVEMBER.from(), NOVEMBER.to(), AT_SEOUL))),
				StateInput.notEntered(), "SELECTED",
				List.of(new ServiceSelectionInput("STORE", LocalDate.of(2026, 10, 15), "MANUAL", AT_SEOUL, true, null),
						new ServiceSelectionInput("FUEL", null, "MANUAL", AT_SEOUL, true, null))));
	}

	@Test
	void noSelectionBecomesOneUnnamedEntryAndUnknownBecomesEmpty() {
		MonthlyStateView none = new MonthlyStateView("uc-1", YearMonth.of(2026, 11), AT, StateValue.unknown(),
				List.of(), StateValue.unknown(), List.of(), ServiceSelectionStatus.NONE,
				new MonthlyStateView.NoSelection(LocalDate.of(2026, 11, 1), AT), StateValue.unknown(), null);

		assertThat(EngineInputAssembler.monthlyState(none).serviceSelections())
			.containsExactly(new ServiceSelectionInput(null, LocalDate.of(2026, 11, 1), "MANUAL", AT_SEOUL, true, null));
		assertThat(EngineInputAssembler.monthlyState(empty()).serviceSelectionStatus()).isEqualTo("UNKNOWN");
		assertThat(EngineInputAssembler.monthlyState(empty()).serviceSelections()).isEmpty();
	}

	@Test
	void confirmedCardUsesMappingProductRegisteredOnFromStateAndUndeterminedEvidence() {
		LocalDate registered = LocalDate.of(2026, 1, 1);
		MonthlyStateView state = new MonthlyStateView("uc-1", YearMonth.of(2026, 11), AT, StateValue.unknown(),
				List.of(), StateValue.unknown(), List.of(), ServiceSelectionStatus.UNKNOWN, null,
				StateValue.manual(registered, new DatePeriod(registered, registered), AT), null);

		HeldCardInput input = EngineInputAssembler.heldCard(card("uc-1", MappingStatus.USER_CONFIRMED, "09174", true),
				state);

		assertThat(input.mappingStatus()).isEqualTo("USER_CONFIRMED");
		assertThat(input.cardProductId()).isEqualTo("09174");
		assertThat(input.candidateCardProductId()).isNull();
		assertThat(input.excludedByUser()).isTrue();
		assertThat(input.holdingSource()).isEqualTo("MANUAL");
		assertThat(input.registeredOn()).isEqualTo(StateInput.manual(registered, registered, registered, AT_SEOUL));
		assertThat(input.policyEvidence()).isEqualTo(PolicyEvidenceInput.undetermined());
	}

	@Test
	void unconfirmedCardHasNoProductAndSyntheticCardHasNoMapping() {
		HeldCardInput unmapped = EngineInputAssembler.heldCard(card("uc-2", MappingStatus.NOT_SUPPORTED, null, false),
				empty());
		HeldCardInput synthetic = EngineInputAssembler.heldCard(new UserCardView("uc-3", "올라운드 티타늄카드",
				HoldingSource.SYNTHETIC, "09271", null, false,
				RecommendationEligibility.of(HoldingSource.SYNTHETIC, null, false), AT), empty());

		assertThat(unmapped.mappingStatus()).isEqualTo("NOT_SUPPORTED");
		assertThat(unmapped.cardProductId()).isNull();
		assertThat(synthetic.mappingStatus()).isNull();
		assertThat(synthetic.cardProductId()).isEqualTo("09271");
		assertThat(synthetic.holdingSource()).isEqualTo("SYNTHETIC");
		assertThat(synthetic.registeredOn()).isEqualTo(StateInput.notEntered());
	}

	private static MonthlyStateView empty() {
		return new MonthlyStateView("uc-1", YearMonth.of(2026, 11), null, StateValue.unknown(), List.of(),
				StateValue.unknown(), List.of(), ServiceSelectionStatus.UNKNOWN, null, StateValue.unknown(), null);
	}

	static UserCardView card(String id, MappingStatus status, String productId, boolean excluded) {
		return new UserCardView(id, "카드", HoldingSource.MANUAL, null,
				new UserCardMapping(status, productId, ConfirmationMethod.MANUAL_SELECTION, AT), excluded,
				RecommendationEligibility.of(HoldingSource.MANUAL, status, excluded), AT);
	}

}
