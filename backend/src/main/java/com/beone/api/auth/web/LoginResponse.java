package com.beone.api.auth.web;

/**
 * 로그인 성공 응답(docs/api/auth.md). 세션 ID는 본문에 넣지 않는다.
 *
 * @param userId 회원 ID({@code usr-} 접두어)
 * @param email 정규화한 이메일
 */
public record LoginResponse(String userId, String email) {

	@Override
	public String toString() {
		return "LoginResponse[userId=" + this.userId + ", email=<hidden>]";
	}

}
