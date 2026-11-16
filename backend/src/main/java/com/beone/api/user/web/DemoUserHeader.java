package com.beone.api.user.web;

import com.beone.api.user.application.DemoUser;

/**
 * 시연 사용자 헤더 {@code X-BeONE-Demo-User}의 값 판정.
 *
 * <p>값은 {@code MY_CARDS}·{@code VIRTUAL_PORTFOLIO}와 정확히 같아야 한다. 대소문자·앞뒤 공백을 고치지 않고,
 * 헤더가 없거나 비었거나 여러 개면 잘못된 요청이다(docs/api.md 업무 API 공통 규약).
 */
public final class DemoUserHeader {

	/** 헤더 이름. */
	public static final String NAME = "X-BeONE-Demo-User";

	private DemoUserHeader() {
	}

	/**
	 * 요청에 담긴 헤더 값들로 시연 사용자를 정한다.
	 * @param values 같은 이름의 헤더 값 전체. 헤더가 없으면 {@code null}
	 * @return 시연 사용자 구분
	 * @throws InvalidDemoUserHeaderException 헤더가 없거나 하나가 아니거나 허용 값이 아닐 때
	 */
	public static DemoUser parse(String[] values) {
		if (values == null || values.length != 1 || values[0] == null) {
			throw new InvalidDemoUserHeaderException();
		}
		for (DemoUser demoUser : DemoUser.values()) {
			if (demoUser.name().equals(values[0])) {
				return demoUser;
			}
		}
		throw new InvalidDemoUserHeaderException();
	}

}
