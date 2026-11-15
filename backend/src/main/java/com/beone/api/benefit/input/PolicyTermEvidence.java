package com.beone.api.benefit.input;

import java.util.Objects;
import java.util.Optional;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.rule.TermTrigger;
import com.beone.api.benefit.rule.TriggerEvidence;

/**
 * 카드 한 장의 실적 정책 조건부 조건(취소 귀속, 할인 매출 실적 제외)에 대한 발생 근거(#35, U4). 기본값이 없는 필수 입력이다.
 *
 * <p>
 * 호출자 책임. 결정기는 아래를 검증하지 않는다.
 * <ul>
 * <li>근거는 이 카드의 전월 실적 기간에 대한 것이다. 기간이 맞는지 확인하지 않으며, 기간 보증만으로 정책이 해결됐다고 보지도 않는다.</li>
 * <li>근거가 없거나 확인하지 않았으면 {@link TriggerEvidence#UNDETERMINED}를 쓴다. {@code CONFIRMED_ABSENT}를 추정하지 않는다.</li>
 * </ul>
 * 취소 근거는 {@link Ledger#cancellationEvidence}로 만들 수 있다. 할인 매출 근거를 만드는 코드는 아직 없다.
 *
 * @param cardId 근거를 확인한 카드. 규칙 선택 결과의 카드와 같아야 한다(결정기가 확인)
 * @param cancellation {@link TermTrigger#CANCELLATION_AFFECTING_PERFORMANCE_WINDOW} 발생 근거
 * @param benefitedSales {@link TermTrigger#BENEFITED_SALE_IN_PERFORMANCE_WINDOW} 발생 근거
 */
public record PolicyTermEvidence(CardId cardId, TriggerEvidence cancellation, TriggerEvidence benefitedSales) {

	/**
	 * 세 값을 모두 받는다. 기본값으로 채우지 않는다.
	 *
	 * @throws NullPointerException 값이 null인 경우
	 */
	public PolicyTermEvidence {
		Objects.requireNonNull(cardId, "cardId");
		Objects.requireNonNull(cancellation, "cancellation");
		Objects.requireNonNull(benefitedSales, "benefitedSales");
	}

	/**
	 * 트리거에 해당하는 근거.
	 *
	 * @param trigger 조건의 영향 상황
	 * @return 취소 귀속·할인 매출 실적 제외 트리거면 그 근거, 이 입력이 다루지 않는 트리거면 빈 값
	 */
	public Optional<TriggerEvidence> evidenceFor(TermTrigger trigger) {
		Objects.requireNonNull(trigger, "trigger");
		return switch (trigger) {
			case CANCELLATION_AFFECTING_PERFORMANCE_WINDOW -> Optional.of(cancellation);
			case BENEFITED_SALE_IN_PERFORMANCE_WINDOW -> Optional.of(benefitedSales);
			default -> Optional.empty();
		};
	}

}
