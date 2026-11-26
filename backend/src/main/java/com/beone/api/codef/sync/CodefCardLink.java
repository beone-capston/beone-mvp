package com.beone.api.codef.sync;

import java.util.Objects;

/**
 * 승인·실적 한 행을 같은 동기화의 보유카드에 붙인 결과(결정 R1). 연결은 카드명 문자열이 같거나(정확 일치) 이름 사전으로 같은 상품이 되는 보유카드가 한 장뿐이라는
 * 뜻일 뿐, 같은 실물 카드나 공식 상품(SKU)의 입증이 아니다.
 */
public sealed interface CodefCardLink {

	/**
	 * 카드명이 정확히 같은 보유카드가 정확히 한 장이거나, 정확 일치가 없고 이름 사전으로 같은 상품이 되는 보유카드가 정확히 한 장이다.
	 *
	 * @param cardName 연결된 보유카드의 카드명. 정확 일치면 행의 카드명과 같고, 이름 사전 연결이면 다를 수 있다
	 */
	record Linked(String cardName) implements CodefCardLink {

		/**
		 * null을 거부한다.
		 *
		 * @throws NullPointerException 카드명이 null인 경우
		 */
		public Linked {
			Objects.requireNonNull(cardName, "cardName");
		}

	}

	/**
	 * 연결하지 않았다. 이런 승인 행은 판단 불가({@link CodefIncompleteReason#UNDETERMINED_ROW})이고, 실적은 카드 미연결이다.
	 *
	 * @param reason 연결하지 않은 이유
	 */
	record NotLinked(Reason reason) implements CodefCardLink {

		/**
		 * null을 거부한다.
		 *
		 * @throws NullPointerException 이유가 null인 경우
		 */
		public NotLinked {
			Objects.requireNonNull(reason, "reason");
		}

	}

	/** 연결하지 않은 이유. */
	enum Reason {

		/** 행에 카드명이 없다. */
		ROW_CARD_NAME_ABSENT,

		/** 같은 동기화의 보유카드 조회 결과를 쓸 수 없다. 비교할 보유카드가 없다. */
		HELD_CARDS_UNAVAILABLE,

		/** 카드명이 정확히 같은 보유카드가 없다(이름 사전 없이 만든 연결기). 공백·대소문자·"카드명|발급사" 차이를 고치지 않는다. */
		NO_MATCHING_HELD_CARD,

		/** 카드명이 정확히 같은 보유카드가 두 장 이상이다. */
		MULTIPLE_MATCHING_HELD_CARDS,

		/** 정확 일치가 없고, 행의 카드명이 이름 사전에 없다. */
		ROW_NAME_NOT_IN_DICTIONARY,

		/** 정확 일치가 없고, 행의 카드명은 이름 사전에 있지만 같은 상품이 되는 보유카드가 없다. */
		NO_HELD_CARD_FOR_PRODUCT,

		/** 정확 일치가 없고, 이름 사전으로 같은 상품이 되는 보유카드가 두 장 이상이다. */
		MULTIPLE_HELD_CARDS_FOR_PRODUCT

	}

}
