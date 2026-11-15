package com.beone.api.codef;

/**
 * CODEF 금액 문자열의 해석. 빈 값은 0원이 아니며, 정수로 읽을 수 있을 때만 금액으로 쓴다.
 */
public sealed interface CodefAmount {

	/** 키 누락·빈 문자열("미제공시 빈칸"). 0원이 아니다. */
	record Absent() implements CodefAmount {
	}

	/**
	 * 부호 있는 정수로 읽힌 금액. 음수는 문서상 KB 취소전표매입에서 나올 수 있지만 부호로 취소 여부를 해석하지 않는다.
	 *
	 * @param value 원문 정수 값. 통화 단위는 같은 항목의 통화 코드를 따른다
	 */
	record Parsed(long value) implements CodefAmount {
	}

	/** 값은 있지만 정수가 아니거나 범위를 넘는 경우. 원문은 보관하지 않는다. */
	record Unparsed() implements CodefAmount {
	}

}
