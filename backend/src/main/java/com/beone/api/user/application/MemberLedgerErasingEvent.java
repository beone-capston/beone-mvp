package com.beone.api.user.application;

import java.util.Objects;

/**
 * 탈퇴하는 회원의 업무 장부를 지우기 직전이다. 탈퇴 트랜잭션 안에서 동기로 발행한다. 장부 행이 아직 있으므로 받는 쪽은 지워질 값을 읽을
 * 수 있다(CODEF 연결 해제 준비). user가 업무 패키지를 알지 않도록 이벤트로 알린다.
 *
 * @param userId 지울 장부 ID
 * @param memberRef 로그용 회원 참조값(회원 ID SHA-256 앞 12자)
 */
public record MemberLedgerErasingEvent(long userId, String memberRef) {

	public MemberLedgerErasingEvent {
		Objects.requireNonNull(memberRef, "memberRef");
	}

}
