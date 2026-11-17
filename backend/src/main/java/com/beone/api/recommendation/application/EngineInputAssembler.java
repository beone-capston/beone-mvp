package com.beone.api.recommendation.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import com.beone.api.benefit.engine.BucketUsageInput;
import com.beone.api.benefit.engine.HeldCardInput;
import com.beone.api.benefit.engine.MonthlyStateInput;
import com.beone.api.benefit.engine.OrderInput;
import com.beone.api.benefit.engine.PolicyEvidenceInput;
import com.beone.api.benefit.engine.ServiceSelectionInput;
import com.beone.api.benefit.engine.StateInput;
import com.beone.api.card.application.HoldingSource;
import com.beone.api.card.application.MappingStatus;
import com.beone.api.card.application.UserCardView;
import com.beone.api.monthlystate.application.MonthlyStateView;
import com.beone.api.monthlystate.application.StateSource;
import com.beone.api.monthlystate.application.StateValue;
import com.beone.api.order.application.OrderDetail;

/**
 * 업무 패키지의 조회 결과를 엔진 입구({@code benefit.engine}) 입력으로 옮긴다. 값을 판단하거나 고치지 않고 모양만 바꾼다.
 *
 * <p>정한 것(docs/domains/recommendation.md):
 * <ul>
 * <li>{@code policyEvidence}는 항상 {@code UNDETERMINED}(계약 "정해지기 전에는 UNDETERMINED", 결정 27 B2)</li>
 * <li>카드 사용 등록일은 월간 상태의 {@code registeredOn}(계약 "코드 HeldCard.registeredAt")</li>
 * <li>적용일 없는 선택은 엔진 계약대로 {@code usable=true}·{@code effectiveFrom=null}로 넘긴다. 엔진이 {@code 확인 필요}로 처리한다</li>
 * <li>기준 기간은 그대로 넘기고 {@code PERIOD_MISMATCH}를 넘기지 않는다(D-22)</li>
 * </ul>
 */
final class EngineInputAssembler {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private EngineInputAssembler() {
	}

	static OrderInput order(OrderDetail order) {
		return new OrderInput(order.orderId(), order.merchantName(), order.amountWon(), order.orderDate(),
				order.orderTime(), order.merchantClass(), order.paymentMethod().name());
	}

	static HeldCardInput heldCard(UserCardView card, MonthlyStateView state) {
		boolean synthetic = card.holdingSource() == HoldingSource.SYNTHETIC;
		String mappingStatus = synthetic ? null : card.mapping().status().name();
		return new HeldCardInput(card.userCardId(), mappingStatus, ruleProductOf(card), null,
				card.excludedFromRecommendation(), card.holdingSource().name(), state(state.registeredOn()),
				monthlyState(state), PolicyEvidenceInput.undetermined());
	}

	/** 계산 규칙의 상품: 가상 포트폴리오는 카드 상품, 그 밖에는 {@code USER_CONFIRMED} 매핑 상품. 없으면 {@code null}. */
	static String ruleProductOf(UserCardView card) {
		if (card.holdingSource() == HoldingSource.SYNTHETIC) {
			return card.cardProductId();
		}
		return (card.mapping().status() == MappingStatus.USER_CONFIRMED) ? card.mapping().cardProductId() : null;
	}

	static MonthlyStateInput monthlyState(MonthlyStateView state) {
		List<BucketUsageInput> usages = state.limitBucketUsages()
			.stream()
			.map(usage -> new BucketUsageInput(usage.limitBucketId(), state(usage.usage())))
			.toList();
		List<ServiceSelectionInput> selections = switch (state.serviceSelectionStatus()) {
			case UNKNOWN -> List.of();
			case SELECTED -> state.selectedServices()
				.stream()
				.map(selection -> new ServiceSelectionInput(selection.serviceId(), selection.effectiveFrom(), "MANUAL",
						seoul(selection.updatedAt()), true, null))
				.toList();
			case NONE -> List.of(new ServiceSelectionInput(null, state.noServiceSelection().effectiveFrom(), "MANUAL",
					seoul(state.noServiceSelection().updatedAt()), true, null));
		};
		return new MonthlyStateInput(state(state.previousMonthPerformanceWon()), usages,
				state(state.otherBucketsUsedWon()), state.serviceSelectionStatus().name(), selections);
	}

	static <T> StateInput<T> state(StateValue<T> value) {
		if (value.source() == StateSource.UNKNOWN) {
			return StateInput.notEntered();
		}
		if (value.source() != StateSource.MANUAL) {
			// 1차 월간 상태는 MANUAL·UNKNOWN만 만든다. 다른 출처가 생기면 변환 규칙을 먼저 정해야 한다.
			throw new IllegalStateException("unsupported monthly state source " + value.source());
		}
		LocalDate from = value.period().from();
		LocalDate to = value.period().to();
		return StateInput.manual(value.value(), from, to, seoul(value.updatedAt()));
	}

	private static OffsetDateTime seoul(LocalDateTime seoulTime) {
		return seoulTime.atZone(SEOUL).toOffsetDateTime();
	}

}
