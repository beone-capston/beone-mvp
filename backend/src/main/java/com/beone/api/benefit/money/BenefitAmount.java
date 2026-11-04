package com.beone.api.benefit.money;

import java.util.Objects;

/**
 * 혜택 금액. 확정된 원 단위 금액이거나 {@code 확인 필요}다.
 *
 * <p>
 * {@link NeedsConfirmation}은 {@code Confirmed(0)}과 같지 않으며, 0원으로 바꿔 주는 메서드를 두지 않는다(D-05).
 */
public sealed interface BenefitAmount {

	static Confirmed confirmed(long won) {
		return new Confirmed(Won.of(won));
	}

	static NeedsConfirmation needsConfirmation(String reason) {
		return new NeedsConfirmation(reason);
	}

	boolean isConfirmed();

	/**
	 * 확정 금액을 반환한다.
	 * @throws IllegalStateException 아직 확인이 필요한 금액인 경우
	 */
	Won confirmedWon();

	record Confirmed(Won won) implements BenefitAmount {

		public Confirmed {
			Objects.requireNonNull(won, "won");
			if (won.isNegative()) {
				throw new IllegalArgumentException("benefit amount must not be negative: " + won.value());
			}
		}

		@Override
		public boolean isConfirmed() {
			return true;
		}

		@Override
		public Won confirmedWon() {
			return won;
		}

	}

	record NeedsConfirmation(String reason) implements BenefitAmount {

		public NeedsConfirmation {
			if (reason == null || reason.isBlank()) {
				throw new IllegalArgumentException("reason is required for an unconfirmed amount");
			}
		}

		@Override
		public boolean isConfirmed() {
			return false;
		}

		@Override
		public Won confirmedWon() {
			throw new IllegalStateException("amount needs confirmation: " + reason);
		}

	}

}
