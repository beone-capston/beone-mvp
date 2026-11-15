package com.beone.api.benefit.primitive;

import java.util.Objects;

import com.beone.api.benefit.card.MerchantClass;
import com.beone.api.benefit.card.PaymentMethod;
import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.rule.BenefitService;
import com.beone.api.benefit.rule.MerchantTarget;

/**
 * 서비스 하나의 개별 적용 조건 판정. 조건마다 따로 판정하며, 여러 조건이 동시에 미충족일 때 어느 사유를 우선할지는 정하지 않는다
 * (카드·서비스 사유 우선순위는 D-13 미결).
 *
 * <p>
 * 입력값은 이미 확인된 값이어야 한다. 업종·결제 방식을 이름으로 추정하거나 모르는 금액을 0원으로 넣지 않는다.
 */
public final class ServiceConditions {

	private ServiceConditions() {
	}

	/**
	 * 거래 금액이 건당 최소 금액 이상이면 참. 최소 금액과 같아도 충족한다(D-10 "건당 5만원 이상", 사례집 C13 "정확히 5만원도 해당").
	 * 취소(음수 금액)의 혜택 처리는 정해지지 않았으므로 음수 금액은 거부한다.
	 */
	public static boolean meetsMinimumAmount(BenefitService service, Won transactionAmount) {
		Objects.requireNonNull(service, "service");
		Objects.requireNonNull(transactionAmount, "transactionAmount");
		if (transactionAmount.isNegative()) {
			throw new IllegalArgumentException("transaction amount must not be negative: " + transactionAmount.value());
		}
		return transactionAmount.compareTo(service.minimumAmount()) >= 0;
	}

	/**
	 * 결제 방식이 서비스가 나열한 대상 결제 방식에 포함되면 참.
	 */
	public static boolean acceptsPaymentMethod(BenefitService service, PaymentMethod paymentMethod) {
		Objects.requireNonNull(service, "service");
		Objects.requireNonNull(paymentMethod, "paymentMethod");
		return service.paymentMethods().contains(paymentMethod);
	}

	/**
	 * 업종이 제외 조건을 적용하기 전의 대상 가맹점에 들면 참. 제외 업종 여부는 {@link #isExcludedClass}로 따로 판정한다.
	 */
	public static boolean isTargetClass(BenefitService service, MerchantClass merchantClass) {
		Objects.requireNonNull(service, "service");
		Objects.requireNonNull(merchantClass, "merchantClass");
		return switch (service.target()) {
			case MerchantTarget.AllMerchants all -> true;
			case MerchantTarget.SpecificClasses specific -> specific.classes().contains(merchantClass);
		};
	}

	/**
	 * 업종이 서비스의 제외 업종이면 참.
	 */
	public static boolean isExcludedClass(BenefitService service, MerchantClass merchantClass) {
		Objects.requireNonNull(service, "service");
		Objects.requireNonNull(merchantClass, "merchantClass");
		return service.excludedClasses().contains(merchantClass);
	}

}
