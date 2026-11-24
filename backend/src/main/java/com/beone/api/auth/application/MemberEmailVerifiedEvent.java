package com.beone.api.auth.application;

import java.util.Objects;

/**
 * 회원이 이메일 인증을 마쳤다. 인증 트랜잭션 안에서 동기로 발행한다. 업무 장부 생성(user 패키지)이 받는다(결정 37 D1a).
 *
 * @param memberId 회원 ID
 */
public record MemberEmailVerifiedEvent(String memberId) {

	public MemberEmailVerifiedEvent {
		Objects.requireNonNull(memberId, "memberId");
	}

}
