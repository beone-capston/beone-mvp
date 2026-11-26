package com.beone.api.codef;

import java.util.Objects;
import java.util.Optional;

/**
 * CODEF 공통 결과({@code result})에서 보관하는 값. {@code message}·{@code extraMessage}는 기관 문구가 그대로 담길 수 있어 보관하지 않는다.
 * {@code extraMessage}는 내용 없이 값이 있었는지만 남긴다(실패 원인 관측용).
 *
 * @param code 결과 코드. 누락·빈 문자열·문자열이 아닌 값이면 빈 값
 * @param transactionId CODEF 트랜잭션 아이디. 누락·빈 문자열이면 빈 값
 * @param category 결과 코드 분류. {@code data} 해석 결과와 독립이다
 * @param extraMessagePresent {@code result.extraMessage}에 비어 있지 않은 값이 있었는지. 내용은 보관하지 않는다
 */
public record CodefResult(Optional<String> code, Optional<String> transactionId, CodefResultCategory category,
		boolean extraMessagePresent) {

	/**
	 * null을 거부한다.
	 *
	 * @throws NullPointerException 값이 null인 경우
	 */
	public CodefResult {
		Objects.requireNonNull(code, "code");
		Objects.requireNonNull(transactionId, "transactionId");
		Objects.requireNonNull(category, "category");
	}

	/**
	 * {@code extraMessage} 유무를 모르는 결과(본문을 읽지 못한 경우 등). 유무는 {@code false}로 둔다.
	 * @param code 결과 코드
	 * @param transactionId 트랜잭션 아이디
	 * @param category 결과 코드 분류
	 */
	public CodefResult(Optional<String> code, Optional<String> transactionId, CodefResultCategory category) {
		this(code, transactionId, category, false);
	}

}
