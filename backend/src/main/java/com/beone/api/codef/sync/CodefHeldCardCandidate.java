package com.beone.api.codef.sync;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

import com.beone.api.codef.CodefHeldCard;
import com.beone.api.codef.CodefHeldCard.Dormancy;

/**
 * CODEF 보유카드 한 장의 매핑 전 후보(D-15·D-16). 규칙 카드와 연결하지 않은 상태이며 계산에 쓰지 않는다.
 *
 * <p>
 * 보유 출처는 {@code CODEF}, 매핑 상태는 {@code UNMAPPED}로 고정한다. 두 값은 엔진 입력 {@code HeldCardInput}의 같은 이름 토큰과 같지만, 이 타입은
 * {@code benefit} 패키지에 의존하지 않고 엔진 입력을 만들지도 않는다. 카드명으로 후보 상품을 정하는 일(R3)과 사용자 카드 ID 부여(DB, 팀원 담당)는 하지
 * 않는다. 카드번호·유효기간·사용자명은 기존 해석기가 이미 옮기지 않는다.
 *
 * @param cardName 카드명. 후보를 보여 주는 근거로만 쓴다. "카드명|발급사" 형식이어도 나누지 않는다
 * @param dormant 휴면 여부. 휴면 카드도 목록에서 빼지 않는다
 * @param cardType 카드구분. 기관 표기 그대로이며 KB는 제공하지 않는다(보유카드!H12)
 * @param issueDate 발급일자. 형식이 문서에 없어 문자열 그대로이며 KB는 제공하지 않는다(보유카드!H14)
 * @param state 카드상태. 기관 표기 그대로이며 KB는 제공하지 않는다(보유카드!H17)
 */
public record CodefHeldCardCandidate(Optional<String> cardName, Dormancy dormant, Optional<String> cardType,
		Optional<String> issueDate, Optional<String> state) {

	/** 보유 출처. 엔진 입력의 {@code holdingSource} 토큰과 같다. */
	public static final String HOLDING_SOURCE = "CODEF";

	/** 매핑 상태. 엔진 입력의 {@code mappingStatus} 토큰과 같다(D-15). */
	public static final String MAPPING_STATUS = "UNMAPPED";

	/**
	 * null을 거부한다. 값 없음은 빈 {@code Optional}·{@code UNKNOWN}으로 표현한다.
	 *
	 * @throws NullPointerException 값이 null인 경우
	 */
	public CodefHeldCardCandidate {
		Objects.requireNonNull(cardName, "cardName");
		Objects.requireNonNull(dormant, "dormant");
		Objects.requireNonNull(cardType, "cardType");
		Objects.requireNonNull(issueDate, "issueDate");
		Objects.requireNonNull(state, "state");
	}

	/**
	 * 해석된 보유카드 한 장을 후보로 옮긴다. 값을 고치거나 채우지 않는다.
	 *
	 * @param card 해석된 보유카드
	 * @return 매핑 전 후보
	 * @throws NullPointerException 입력이 null인 경우
	 */
	public static CodefHeldCardCandidate from(CodefHeldCard card) {
		Objects.requireNonNull(card, "card");
		return new CodefHeldCardCandidate(card.cardName(), card.dormant(), card.cardType(), card.issueDate(),
				card.state());
	}

	/**
	 * 보유 출처.
	 *
	 * @return 항상 {@value #HOLDING_SOURCE}
	 */
	public String holdingSource() {
		return HOLDING_SOURCE;
	}

	/**
	 * 매핑 상태.
	 *
	 * @return 항상 {@value #MAPPING_STATUS}
	 */
	public String mappingStatus() {
		return MAPPING_STATUS;
	}

	/**
	 * 카드 사용 등록일. 발급일로 등록일을 추정하지 않으며(D-16 등록일은 수동 입력), KB는 발급일자도 제공하지 않으므로 항상 미확인이다.
	 *
	 * @return 항상 빈 값
	 */
	public Optional<LocalDate> registeredOn() {
		return Optional.empty();
	}

}
