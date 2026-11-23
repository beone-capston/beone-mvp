package com.beone.api.auth.web;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 회원 프로필에서 세션 검사를 통과한 요청의 회원 ID. {@link MemberApiInterceptor}가 요청 속성에 담고, 현재 사용자 결정 지점(user
 * 패키지)이 읽는다(결정 37 D4). 요청 헤더·본문의 값은 쓰지 않는다.
 */
public final class AuthenticatedMember {

	/** 요청 속성 이름. 클라이언트가 정할 수 없는 서버 내부 값이다. */
	static final String ATTRIBUTE = AuthenticatedMember.class.getName() + ".memberId";

	private AuthenticatedMember() {
	}

	/**
	 * 세션 검사를 통과한 요청의 회원 ID.
	 * @param request 요청
	 * @return 회원 ID, 검사를 거치지 않은 요청이면 빈 값
	 */
	public static Optional<String> memberIdOf(HttpServletRequest request) {
		return (request.getAttribute(ATTRIBUTE) instanceof String memberId) ? Optional.of(memberId) : Optional.empty();
	}

	static void set(HttpServletRequest request, String memberId) {
		request.setAttribute(ATTRIBUTE, memberId);
	}

}
