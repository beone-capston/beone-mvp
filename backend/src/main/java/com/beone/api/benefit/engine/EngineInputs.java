package com.beone.api.benefit.engine;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.card.LimitBucketId;
import com.beone.api.benefit.card.MerchantClass;
import com.beone.api.benefit.card.PaymentMethod;
import com.beone.api.benefit.card.ServiceId;
import com.beone.api.benefit.input.CardMonthlyState;
import com.beone.api.benefit.input.Order;
import com.beone.api.benefit.input.PolicyTermEvidence;
import com.beone.api.benefit.input.ServiceSelection;
import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.rule.TriggerEvidence;
import com.beone.api.benefit.state.BasisPeriod;
import com.beone.api.benefit.state.StateSource;
import com.beone.api.benefit.state.StateValue;

/**
 * 입구 입력을 내부 타입으로 바꾸고 기준 기간을 판단한다(D-22).
 *
 * <p>
 * 기준 기간이 맞지 않거나, {@code usable=false}이거나, 값이 없으면 {@code UNKNOWN}으로 바꾼다. 0원으로 바꾸지 않는다.
 * <ul>
 * <li>전월 실적: 주문월의 전월 1일~말일. 규칙 4종의 실적 기간은 모두 전월이다({@code PREVIOUS_CALENDAR_MONTH}).</li>
 * <li>버킷 사용액과 다른 버킷 사용액: 주문월 1일~말일.</li>
 * <li>사용 등록일: 등록일 하루(D-23 14번).</li>
 * <li>택1 선택: 기간 대신 적용일을 본다. 적용일이 없거나 쓸 수 없으면 {@code UNKNOWN}이다(D-16).</li>
 * </ul>
 * 음수 전월 실적은 약관 근거가 없어 {@code UNKNOWN}(확인 필요)으로 둔다(D-17, D-22).
 */
final class EngineInputs {

	private EngineInputs() {
	}

	static Order order(OrderInput input) {
		return new Order(Optional.ofNullable(input.orderId()), Optional.ofNullable(input.merchantName()),
				Won.of(input.amountWon()), input.date(), Optional.ofNullable(input.time()),
				MerchantClass.of(input.merchantClass()), PaymentMethod.valueOf(input.paymentMethod()));
	}

	static StateValue<LocalDate> registeredAt(StateInput<LocalDate> input) {
		if (input.value() == null) {
			return unknown(input);
		}
		return known(input, input.value(), BasisPeriod.day(input.value()));
	}

	static CardMonthlyState monthlyState(CardId cardId, MonthlyStateInput input, LocalDate orderDate) {
		YearMonth orderMonth = YearMonth.from(orderDate);
		BasisPeriod month = BasisPeriod.month(orderMonth);
		BasisPeriod priorMonth = BasisPeriod.month(orderMonth.minusMonths(1));

		StateValue<Won> prior = won(input.previousMonthPerformanceWon(), priorMonth);
		if (prior.isKnown() && prior.knownValue().isNegative()) {
			prior = StateValue.unknown(prior.basisPeriod().orElse(null), prior.refreshedAt().orElse(null));
		}
		Map<LimitBucketId, StateValue<Won>> used = new HashMap<>();
		for (BucketUsageInput usage : input.limitBucketUsages()) {
			used.put(LimitBucketId.of(usage.limitBucketId()), won(usage.usedWon(), month));
		}
		return new CardMonthlyState(cardId, Optional.of(prior), used, won(input.otherBucketsUsedWon(), month),
				selections(input.serviceSelections()));
	}

	static PolicyTermEvidence evidence(CardId cardId, PolicyEvidenceInput input) {
		return new PolicyTermEvidence(cardId, TriggerEvidence.valueOf(input.cancellation()),
				TriggerEvidence.valueOf(input.benefitedSales()));
	}

	private static List<StateValue<ServiceSelection>> selections(List<ServiceSelectionInput> inputs) {
		List<StateValue<ServiceSelection>> values = new ArrayList<>();
		for (ServiceSelectionInput input : inputs) {
			if (!input.usable() || input.effectiveFrom() == null) {
				values.add(StateValue.unknown(null, input.updatedAt()));
				continue;
			}
			Optional<ServiceId> service = Optional.ofNullable(input.serviceId()).map(ServiceId::of);
			ServiceSelection selection = new ServiceSelection(service, input.effectiveFrom());
			BasisPeriod period = BasisPeriod.day(input.effectiveFrom());
			values.add(switch (input.source()) {
				case "MANUAL" -> StateValue.manual(selection, period, input.updatedAt());
				case "CODEF" -> StateValue.codef(selection, period, input.updatedAt());
				default -> StateValue.synthetic(selection, period);
			});
		}
		return values;
	}

	private static StateValue<Won> won(StateInput<Long> input, BasisPeriod expected) {
		if (input.value() == null) {
			return unknown(input);
		}
		return known(input, Won.of(input.value()), expected);
	}

	/**
	 * 쓸 수 있고 기준 기간이 맞는 값만 출처 그대로 옮긴다. 나머지는 기간과 시각만 남긴 {@code UNKNOWN}이다.
	 */
	private static <T, V> StateValue<V> known(StateInput<T> input, V value, BasisPeriod expected) {
		BasisPeriod period = BasisPeriod.of(input.periodFrom(), input.periodTo());
		if (!input.usable() || !period.equals(expected)) {
			return StateValue.unknown(period, input.updatedAt());
		}
		Function<V, StateValue<V>> factory = switch (StateSource.valueOf(input.source())) {
			case MANUAL -> known -> StateValue.manual(known, period, input.updatedAt());
			case CODEF -> known -> StateValue.codef(known, period, input.updatedAt());
			case SYNTHETIC -> known -> StateValue.synthetic(known, period);
			case INFERRED, UNKNOWN -> throw new IllegalStateException("StateInput rejects " + input.source() + " with a value");
		};
		return factory.apply(value);
	}

	private static <T, V> StateValue<V> unknown(StateInput<T> input) {
		BasisPeriod period = input.periodFrom() == null ? null : BasisPeriod.of(input.periodFrom(), input.periodTo());
		OffsetDateTime updatedAt = input.updatedAt();
		return StateValue.unknown(period, updatedAt);
	}

}
