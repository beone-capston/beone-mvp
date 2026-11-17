package com.beone.api.monthlystate.application;

/**
 * 매핑이 확인되지 않은 카드에 한도 묶음 사용액·택1 선택을 보냈을 때 던진다(D-16). 어떤 규칙의 ID인지 알 수 없기 때문이다.
 *
 * <p>웹 계층의 기능별 처리기가 409 {@code USER_CARD_MAPPING_REQUIRED}로 바꾼다.
 */
public class MappingRequiredException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public MappingRequiredException() {
		super(null, null, false, false);
	}

}
