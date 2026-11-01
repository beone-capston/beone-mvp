package com.beone.api.codef;

import java.util.Objects;
import java.util.Optional;

import tools.jackson.databind.JsonNode;

/**
 * 보유카드 API({@code /v1/kr/card/p/account/card-list}, 문서 갱신일 2026-10-19) 출력 항목 중 BeONE에 필요한 비민감 필드.
 *
 * <p>
 * 보유 카드 후보 표시와 신규 발급 유예 확인에 필요한 카드명·카드구분·휴면 여부·발급일자·상태만 담는다. 카드번호({@code resCardNo}),
 * 유효기간({@code resValidPeriod}), 세션({@code commSession})은 옮기지 않는다. 이미지링크와 형식이 기관마다 다른 교통카드 여부는 현재 쓰지
 * 않아 옮기지 않는다. 카드명으로 실제 상품을 확정하지 않는다(D-01).
 *
 * @param cardName 카드명({@code resCardName}). "카드명|발급사" 형식일 수 있으며 나누지 않는다
 * @param cardType 카드구분({@code resCardType}). 기관 표기 그대로
 * @param dormant 휴면카드여부({@code resSleepYN})
 * @param issueDate 발급일자({@code resIssueDate}). 형식이 문서에 없어 문자열 그대로
 * @param state 카드상태({@code resState}). 기관 표기 그대로
 */
public record CodefHeldCard(Optional<String> cardName, Optional<String> cardType, Dormancy dormant,
		Optional<String> issueDate, Optional<String> state) {

	/**
	 * null을 거부한다. 값 없음은 빈 {@code Optional}·{@code UNKNOWN}으로 표현한다.
	 *
	 * @throws NullPointerException 값이 null인 경우
	 */
	public CodefHeldCard {
		Objects.requireNonNull(cardName, "cardName");
		Objects.requireNonNull(cardType, "cardType");
		Objects.requireNonNull(dormant, "dormant");
		Objects.requireNonNull(issueDate, "issueDate");
		Objects.requireNonNull(state, "state");
	}

	static CodefHeldCard read(JsonNode item) {
		return new CodefHeldCard(CodefFields.text(item, "resCardName"), CodefFields.text(item, "resCardType"),
				CodefFields.code(item, "resSleepYN", Dormancy.values(), Dormancy::code, Dormancy.UNKNOWN),
				CodefFields.text(item, "resIssueDate"), CodefFields.text(item, "resState"));
	}

	/** 휴면카드여부. 문서 값 "Y"·"N". */
	public enum Dormancy {

		/** "Y": 휴면. */
		DORMANT("Y"),

		/** "N": 휴면 아님. */
		NOT_DORMANT("N"),

		/** 빈 값 또는 문서 목록 밖의 값. */
		UNKNOWN(null);

		private final String code;

		Dormancy(String code) {
			this.code = code;
		}

		String code() {
			return code;
		}

	}

}
