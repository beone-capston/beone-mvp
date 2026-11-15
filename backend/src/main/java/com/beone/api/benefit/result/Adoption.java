package com.beone.api.benefit.result;

/**
 * 후보 서비스를 카드의 최종 금액에 넣었는지(D-13). 후보 조건을 충족했는지와는 별개다.
 *
 * <p>
 * 어떤 근거로 각 값을 고르는지는 후속 계산기의 책임이며, 이 타입은 결과만 담는다.
 */
public enum Adoption {

	/** 최종 카드 금액에 넣음. */
	ADOPTED,

	/** 최종 카드 금액에 넣지 않음. 기여액은 확정 0원이다. */
	NOT_ADOPTED,

	/** 근거가 부족해 넣었는지 정할 수 없음. */
	UNDETERMINED

}
