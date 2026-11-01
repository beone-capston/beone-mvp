package com.beone.api.benefit.primitive;

import java.time.LocalDate;
import java.util.Objects;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.input.CardMonthlyState;
import com.beone.api.benefit.input.Order;
import com.beone.api.benefit.input.PolicyTermEvidence;
import com.beone.api.benefit.result.OrderBenefit;
import com.beone.api.benefit.rule.RuleCatalog;

/**
 * 카드 한 장·주문 한 건에 대해 규칙 선택부터 주문 혜택 결정까지 잇는 내부 진입점(#38).
 *
 * <p>
 * {@link RuleCatalog#select}로 판정일의 규칙을 고른 뒤 그 결과를 그대로 {@link OrderBenefitDecider#decide}에 넘긴다. 규칙 선택 순서와
 * 결정 정책(D-13)은 두 단계의 것을 그대로 따르며, 이 클래스는 판단을 더하지 않는다.
 *
 * <p>
 * 판정일은 호출자가 정한다. 주문일·결제일이나 카드별 거래 기준일(D-03)에서 판정일을 정하는 규칙은 확정되지 않았으므로 여기서 만들지 않으며,
 * 주문의 날짜로 판정일을 대신하지도 않는다.
 */
public final class OrderBenefitResolver {

	private OrderBenefitResolver() {
	}

	/**
	 * 판정일에 적용할 규칙을 골라 카드 한 장의 주문 혜택을 정한다.
	 *
	 * @param catalog 규칙 버전 목록
	 * @param cardId 판정할 카드
	 * @param decisionDate 규칙 버전을 고를 판정일. 호출자가 정한다
	 * @param order 현재 주문
	 * @param state 같은 카드의 월간 상태
	 * @param evidence 같은 카드의 취소 귀속·할인 매출 근거. 실적 기간 적합성은 호출자 책임이다
	 * @return 쓸 수 있는 규칙이 없으면 {@link OrderBenefit.Unavailable}, 아니면 {@link OrderBenefit.Decided}
	 * @throws NullPointerException 입력이 null인 경우
	 * @throws IllegalArgumentException 상태·근거의 카드가 판정할 카드와 다르거나, 결정기·후보 취합기가 거부하는 입력인 경우
	 * @throws IllegalStateException 결정기가 지원 범위 서비스에서 나올 수 없는 한도 소진 후보를 받은 경우
	 */
	public static OrderBenefit resolve(RuleCatalog catalog, CardId cardId, LocalDate decisionDate, Order order,
			CardMonthlyState state, PolicyTermEvidence evidence) {
		Objects.requireNonNull(catalog, "catalog");
		// 카드 불일치는 규칙 선택 결과와 관계없이 결정기·취합기의 입력 검사가 거부한다.
		return OrderBenefitDecider.decide(catalog.select(cardId, decisionDate), order, state, evidence);
	}

}
