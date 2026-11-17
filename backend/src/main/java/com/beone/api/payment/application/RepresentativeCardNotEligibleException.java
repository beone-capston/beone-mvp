package com.beone.api.payment.application;

/**
 * 대표 카드 고정인데 대표 카드가 없거나 해제됐거나 매핑이 확인되지 않음(409 REPRESENTATIVE_CARD_NOT_ELIGIBLE).
 */
public class RepresentativeCardNotEligibleException extends RuntimeException {

	public RepresentativeCardNotEligibleException() {
		super(null, null, false, false);
	}

}
