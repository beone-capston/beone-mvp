package com.beone.api.monthlystate.application;

import java.util.List;
import java.util.Objects;

/**
 * 카드 규칙에 없는 한도 묶음·서비스 ID를 보냈거나, 선택형 서비스가 없는 카드에 선택 없음을 보냈을 때 던진다.
 *
 * <p>웹 계층의 기능별 처리기가 400 {@code INVALID_REQUEST}로 바꾸고 {@link #violations()}를 {@code errors}에 담는다. 문구에
 * 입력값을 넣지 않는다.
 */
public class RuleMismatchException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final transient List<Violation> violations;

	public RuleMismatchException(List<Violation> violations) {
		super(null, null, false, false);
		this.violations = List.copyOf(violations);
	}

	/**
	 * 위반 목록. 비어 있지 않다.
	 * @return 위반 목록
	 */
	public List<Violation> violations() {
		return this.violations;
	}

	/**
	 * 위반 하나.
	 *
	 * @param field 요청 필드 경로(예: {@code limitBucketUsages[0].limitBucketId})
	 * @param message 고정 문구
	 */
	public record Violation(String field, String message) {

		public Violation {
			Objects.requireNonNull(field, "field");
			Objects.requireNonNull(message, "message");
		}

	}

}
