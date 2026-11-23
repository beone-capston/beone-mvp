package com.beone.api.codef.sync;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import com.beone.api.codef.CodefResult;
import com.beone.api.codef.CodefResultCategory;
import com.beone.api.codef.transport.CodefCallResult;

/**
 * 조회 대상 하나를 쓸 수 없는 이유. 공개 오류 코드({@code CODEF_RECONNECT_REQUIRED} 등)로 바꾸지 않은 중립값이며, 그 대응은 HTTP 계층(팀원 담당)이 정한다.
 * 어느 이유도 요청·응답 원문이나 기관 문구를 담지 않는다.
 */
public sealed interface CodefUnusableReason {

	/**
	 * 상품 응답을 받지 못했다. 전송 계층의 실패 단계와 이유를 그대로 옮긴다.
	 *
	 * @param stage 실패한 단계
	 * @param reason 실패 이유
	 * @param httpStatus 토큰 요청이 거절된 경우의 HTTP 상태. 없으면 빈 값
	 */
	record CallFailed(CodefCallResult.Stage stage, CodefCallResult.Reason reason, OptionalInt httpStatus)
			implements CodefUnusableReason {

		/**
		 * null을 거부한다.
		 *
		 * @throws NullPointerException 값이 null인 경우
		 */
		public CallFailed {
			Objects.requireNonNull(stage, "stage");
			Objects.requireNonNull(reason, "reason");
			Objects.requireNonNull(httpStatus, "httpStatus");
		}

	}

	/** 상품 API가 HTTP 401로 답했다. 본문은 해석하지 않았다. */
	record HttpUnauthorized() implements CodefUnusableReason {
	}

	/**
	 * 상품 API가 200·401이 아닌 상태로 답했다. 본문은 해석하지 않았다.
	 *
	 * @param status HTTP 상태 코드
	 */
	record HttpFailed(int status) implements CodefUnusableReason {
	}

	/**
	 * HTTP 200 본문을 해석했지만 쓸 수 없다. 결과 코드가 성공이 아니거나(추가 인증 필요·카드소지확인·미분류 등), 성공이어도 {@code data}가 항목 목록이
	 * 아니다.
	 *
	 * @param result 공통 결과(결과 코드·트랜잭션 아이디·분류)
	 * @param data {@code data} 해석 상태
	 * @param malformedReason {@code data}가 형식 오류일 때 기존 해석기가 준 고정 문구. 그 밖에는 빈 값
	 */
	record ResponseNotUsable(CodefResult result, DataState data, Optional<String> malformedReason)
			implements CodefUnusableReason {

		/**
		 * 값을 확인한다.
		 *
		 * @throws NullPointerException 값이 null인 경우
		 * @throws IllegalArgumentException 성공 결과와 항목 목록의 조합(쓸 수 있는 경우)이거나, 형식 오류 여부와 사유가 맞지 않는 경우
		 */
		public ResponseNotUsable {
			Objects.requireNonNull(result, "result");
			Objects.requireNonNull(data, "data");
			Objects.requireNonNull(malformedReason, "malformedReason");
			if (result.category() == CodefResultCategory.SUCCESS && data == DataState.ITEMS) {
				throw new IllegalArgumentException("a success result with items is usable");
			}
			if ((data == DataState.MALFORMED) != malformedReason.isPresent()) {
				throw new IllegalArgumentException("malformedReason is present exactly when data is malformed");
			}
		}

		/**
		 * 추가 인증이 필요한 응답인지. 추가 인증 2차 요청은 범위 밖이므로 이 대상은 지원하지 않음으로 남는다.
		 *
		 * @return 결과 분류가 추가 인증 필요이면 참
		 */
		public boolean additionalAuthenticationRequired() {
			return result.category() == CodefResultCategory.ADDITIONAL_AUTHENTICATION_REQUIRED;
		}

	}

	/** 응답 {@code data}의 해석 상태. */
	enum DataState {

		/** 객체 또는 배열로 받은 항목(0건 포함). */
		ITEMS,

		/** 키 누락. 0건이 아니다. */
		MISSING,

		/** 문서 규칙에 맞지 않는 형태. 0건이 아니다. */
		MALFORMED

	}

}
