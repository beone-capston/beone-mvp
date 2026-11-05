package com.beone.api.benefit.engine;

import static com.beone.api.benefit.engine.EngineTestData.NOW;
import static com.beone.api.benefit.engine.EngineTestData.ORDER_DATE;
import static com.beone.api.benefit.engine.EngineTestData.confirmed;
import static com.beone.api.benefit.engine.EngineTestData.emptyState;
import static com.beone.api.benefit.engine.EngineTestData.order;
import static com.beone.api.benefit.engine.EngineTestData.unmapped;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class EngineRequestTest {

	@Test
	void acceptsUnconfirmedCardsAndNoCards() {
		EngineRequest request = new EngineRequest(ORDER_DATE, order(), "AUTO_RECOMMEND", null,
				List.of(confirmed("uc-1", "09174"), unmapped("uc-2"), unmapped("uc-3")));
		assertThat(request.cards()).hasSize(3);
		assertThat(new EngineRequest(ORDER_DATE, order(), "AUTO_RECOMMEND", null, List.of()).cards()).isEmpty();
	}

	@Test
	void decisionDateMustBeTheOrderDate() {
		assertThatThrownBy(() -> new EngineRequest(ORDER_DATE.plusDays(1), order(), "AUTO_RECOMMEND", null, List.of()))
			.hasMessageContaining("order date");
	}

	@Test
	void rejectsSameUserCardOrSameProductTwice() {
		assertThatThrownBy(() -> new EngineRequest(ORDER_DATE, order(), "AUTO_RECOMMEND", null,
				List.of(unmapped("uc-1"), unmapped("uc-1"))))
			.hasMessageContaining("userCardId");
		assertThatThrownBy(() -> new EngineRequest(ORDER_DATE, order(), "AUTO_RECOMMEND", null,
				List.of(confirmed("uc-1", "09174"), confirmed("uc-2", "09174"))))
			.hasMessageContaining("cardProductId");
	}

	@Test
	void representativeCardMustBeHeldAndRequiredWhenFixed() {
		assertThatThrownBy(() -> new EngineRequest(ORDER_DATE, order(), "FIXED_REPRESENTATIVE", null,
				List.of(confirmed("uc-1", "09174"))))
			.hasMessageContaining("representative");
		assertThatThrownBy(() -> new EngineRequest(ORDER_DATE, order(), "AUTO_RECOMMEND", "uc-9",
				List.of(confirmed("uc-1", "09174"))))
			.hasMessageContaining("not held");
	}

	@Test
	void rejectsUnknownSelectionMode() {
		assertThatThrownBy(() -> new EngineRequest(ORDER_DATE, order(), "BEST", null, List.of()))
			.hasMessageContaining("selectionMode");
	}

	@Test
	void orderRejectsNonPositiveAmountAndUnknownPaymentMethod() {
		assertThatThrownBy(() -> new OrderInput(null, null, 0, ORDER_DATE, null, "STARBUCKS_STORE", "DIRECT"))
			.hasMessageContaining("positive");
		assertThatThrownBy(() -> new OrderInput(null, null, 1, ORDER_DATE, null, "STARBUCKS_STORE", "CASH"))
			.hasMessageContaining("paymentMethod");
		assertThatThrownBy(() -> new OrderInput(null, null, 1, ORDER_DATE, null, "BAD CLASS", "DIRECT"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void cardProductFollowsTheMappingStatus() {
		assertThatThrownBy(() -> new HeldCardInput("uc", "USER_CONFIRMED", null, null, false, "MANUAL",
				StateInput.notEntered(), emptyState()))
			.hasMessageContaining("USER_CONFIRMED");
		assertThatThrownBy(() -> new HeldCardInput("uc", "UNMAPPED", "09174", null, false, "MANUAL",
				StateInput.notEntered(), emptyState()))
			.hasMessageContaining("USER_CONFIRMED");
		assertThatThrownBy(() -> new HeldCardInput("uc", "UNMAPPED", null, "09174", false, "MANUAL",
				StateInput.notEntered(), emptyState()))
			.hasMessageContaining("CANDIDATE");
		assertThatThrownBy(() -> new HeldCardInput("uc", "DONE", null, null, false, "MANUAL", StateInput.notEntered(),
				emptyState()))
			.hasMessageContaining("mappingStatus");
	}

	@Test
	void syntheticCardHasNoMappingButNeedsProduct() {
		HeldCardInput card = new HeldCardInput("uc", null, "09174", null, false, "SYNTHETIC", StateInput.notEntered(),
				emptyState());
		assertThat(card.mappingStatus()).isNull();
		assertThatThrownBy(() -> new HeldCardInput("uc", "USER_CONFIRMED", "09174", null, false, "SYNTHETIC",
				StateInput.notEntered(), emptyState()))
			.hasMessageContaining("mapping target");
		assertThatThrownBy(() -> new HeldCardInput("uc", null, null, null, false, "SYNTHETIC", StateInput.notEntered(),
				emptyState()))
			.hasMessageContaining("cardProductId");
		assertThatThrownBy(() -> new HeldCardInput("uc", null, "09174", null, false, "MANUAL", StateInput.notEntered(),
				emptyState()))
			.hasMessageContaining("mappingStatus");
	}

	@Test
	void negativePerformanceIsAcceptedButNegativeUsageIsNot() {
		StateInput<Long> negative = StateInput.manual(-1L, ORDER_DATE.withDayOfMonth(1), ORDER_DATE, NOW);
		assertThat(new MonthlyStateInput(negative, List.of(), StateInput.notEntered(), "UNKNOWN", List.of())
			.previousMonthPerformanceWon()
			.value()).isEqualTo(-1L);
		assertThatThrownBy(() -> new BucketUsageInput("GREAT", negative)).hasMessageContaining("negative");
		assertThatThrownBy(() -> new MonthlyStateInput(StateInput.notEntered(), List.of(), negative, "UNKNOWN", List.of()))
			.hasMessageContaining("negative");
	}

	@Test
	void rejectsDuplicatedBucketUsage() {
		BucketUsageInput usage = new BucketUsageInput("GREAT", StateInput.notEntered());
		assertThatThrownBy(() -> new MonthlyStateInput(StateInput.notEntered(), List.of(usage, usage),
				StateInput.notEntered(), "UNKNOWN", List.of()))
			.hasMessageContaining("limitBucketId");
	}

	@Test
	void selectionsMustMatchTheStatus() {
		ServiceSelectionInput named = new ServiceSelectionInput("FUEL", ORDER_DATE, "MANUAL", NOW, true, null);
		ServiceSelectionInput none = new ServiceSelectionInput(null, ORDER_DATE, "MANUAL", NOW, true, null);
		assertThat(selectionState("SELECTED", named).serviceSelections()).hasSize(1);
		assertThat(selectionState("NONE", none).serviceSelections()).hasSize(1);
		assertThatThrownBy(() -> selectionState("UNKNOWN", named)).hasMessageContaining("status");
		assertThatThrownBy(() -> selectionState("SELECTED", none)).hasMessageContaining("status");
		assertThatThrownBy(() -> selectionState("NONE", named)).hasMessageContaining("status");
		assertThatThrownBy(() -> selectionState("OTHER", named)).hasMessageContaining("serviceSelectionStatus");
	}

	@Test
	void missingEffectiveFromIsLeftForTheEngineToMarkUnconfirmed() {
		assertThat(new ServiceSelectionInput("FUEL", null, "MANUAL", NOW, true, null).effectiveFrom()).isNull();
	}

	private static MonthlyStateInput selectionState(String status, ServiceSelectionInput selection) {
		return new MonthlyStateInput(StateInput.notEntered(), List.of(), StateInput.notEntered(), status, List.of(selection));
	}

}
