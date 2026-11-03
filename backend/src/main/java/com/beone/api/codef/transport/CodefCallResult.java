package com.beone.api.codef.transport;

import java.util.Objects;
import java.util.OptionalInt;

import com.beone.api.codef.CodefProductResponse;

/**
 * 상품 호출 한 번의 결과. 어느 결과도 요청·응답 원문이나 예외 메시지를 담지 않는다.
 * 실패해도 수동 입력 경로는 그대로 유지한다(호출자 책임).
 *
 * @param <T> 응답 항목 타입
 */
public sealed interface CodefCallResult<T> {

	/** 상품 API가 HTTP 응답을 돌려줬다. 401과 서버 오류도 여기에 든다. */
	record Answered<T>(CodefProductResponse<T> response) implements CodefCallResult<T> {

		public Answered {
			Objects.requireNonNull(response, "response");
		}

	}

	/** 상품 응답을 받지 못했다. 자동으로 다시 보내지 않는다. */
	record Failed<T>(Stage stage, Reason reason, OptionalInt httpStatus) implements CodefCallResult<T> {

		public Failed {
			Objects.requireNonNull(stage, "stage");
			Objects.requireNonNull(reason, "reason");
			Objects.requireNonNull(httpStatus, "httpStatus");
		}

	}

	/** 실패한 단계. */
	enum Stage {

		TOKEN, PRODUCT

	}

	/** 실패 이유. */
	enum Reason {

		/** 키가 설정되지 않아 토큰 발급도 시도하지 않았다. */
		CREDENTIALS_MISSING,

		/** 관측 1회의 호출 상한을 넘어 호출하지 않았다. */
		CALL_LIMIT_REACHED,

		/** 시간 안에 응답을 다 받지 못했다. 상대가 처리했는지 알 수 없다. */
		TIMEOUT,

		/** 연결이 안 되거나 끊겼다. 상대가 처리했는지 알 수 없다. */
		CONNECTION_FAILED,

		/** 대기 중인 스레드가 중단됐다. */
		INTERRUPTED,

		/** 토큰 요청이 200이 아닌 상태로 끝났다. */
		TOKEN_REJECTED,

		/** 토큰 응답을 읽지 못했다. */
		TOKEN_UNREADABLE

	}

}
