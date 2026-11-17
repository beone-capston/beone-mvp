package com.beone.api.codef.application;

import java.util.Objects;

/**
 * CODEF 계정 등록 요청의 결과. 게이트웨이가 돌려주고 서비스가 공개 오류 코드로 바꾼다.
 *
 * <p>실 CODEF 결과 분류와의 대응은 {@link CodefRegistrations#fromCategory}에 있다. {@link LoginRejected}는 근거 있는 결과 코드가
 * 확인되기 전까지 실코드에서 나오지 않고 가짜 구현만 돌려준다(관리자 판단 P1).
 */
public sealed interface CodefRegistration {

	/**
	 * 등록 성공. connectedId는 서버에만 두며 문자열 표현에 넣지 않는다.
	 * @param connectedId CODEF가 돌려준 연결 식별값
	 */
	record Registered(String connectedId) implements CodefRegistration {

		public Registered {
			Objects.requireNonNull(connectedId, "connectedId");
			if (connectedId.isBlank()) {
				throw new IllegalArgumentException("connectedId must not be blank");
			}
		}

		@Override
		public String toString() {
			return "Registered[connectedId=<hidden>]";
		}

	}

	/** 카드사 로그인 거절. 실코드 대응은 아직 없다. */
	record LoginRejected() implements CodefRegistration {
	}

	/** 카드소지확인 필요(CF-12108). 지원 보류 경로다. */
	record CardVerificationRequired() implements CodefRegistration {
	}

	/** 그 밖의 모든 실패: 미분류·전송 실패·시간 초과·추가 인증 필요 등. */
	record Unavailable() implements CodefRegistration {
	}

}
