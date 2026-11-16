package com.beone.api.codef.sync;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import com.beone.api.codef.CodefList;
import com.beone.api.codef.CodefProductResponse;
import com.beone.api.codef.CodefResponse;
import com.beone.api.codef.CodefResult;
import com.beone.api.codef.CodefResultCategory;
import com.beone.api.codef.sync.CodefUnusableReason.DataState;
import com.beone.api.codef.transport.CodefCallResult;

/**
 * 조회 대상(보유카드·승인내역·실적조회) 하나의 결과 판정. 대상끼리는 서로 영향을 주지 않는다(부분 실패).
 *
 * <p>
 * 쓸 수 있는 경우는 응답을 받았고 HTTP 200 본문을 해석했으며 결과 코드가 성공({@code CF-00000})이고 {@code data}가 항목 목록일 때뿐이다. 그 밖에는
 * {@link CodefUnusableReason}에 이유를 보존한다. 성공이어도 {@code data} 누락·형식 오류는 0건으로 바꾸지 않는다. 쓸 수 없어도 수동 입력 경로는
 * 그대로 유지한다(호출자 책임).
 *
 * @param <T> 변환된 항목 타입
 */
public sealed interface CodefTargetOutcome<T> {

	/**
	 * 조회 시각. CODEF가 갱신 시각을 주지 않으므로 묶음을 만든 쪽이 주입받은 시계로 정한다(D-04).
	 *
	 * @return 조회 시각
	 */
	Instant checkedAt();

	/**
	 * 쓸 수 있는 결과.
	 *
	 * @param <T> 변환된 항목 타입
	 * @param checkedAt 조회 시각
	 * @param result 공통 결과. 분류는 항상 성공이다
	 * @param shape 받은 목록 형태
	 * @param items 변환된 항목. 0건일 수 있다
	 */
	record Usable<T>(Instant checkedAt, CodefResult result, CodefList.Shape shape, List<T> items)
			implements CodefTargetOutcome<T> {

		/**
		 * 값을 확인하고 항목을 불변 복사한다.
		 *
		 * @throws NullPointerException 값이 null인 경우
		 * @throws IllegalArgumentException 결과 분류가 성공이 아닌 경우
		 */
		public Usable {
			Objects.requireNonNull(checkedAt, "checkedAt");
			Objects.requireNonNull(result, "result");
			Objects.requireNonNull(shape, "shape");
			items = List.copyOf(items);
			if (result.category() != CodefResultCategory.SUCCESS) {
				throw new IllegalArgumentException("a usable outcome needs a success result");
			}
		}

	}

	/**
	 * 쓸 수 없는 결과.
	 *
	 * @param <T> 변환된 항목 타입
	 * @param checkedAt 조회 시각
	 * @param reason 쓸 수 없는 이유
	 */
	record Unusable<T>(Instant checkedAt, CodefUnusableReason reason) implements CodefTargetOutcome<T> {

		/**
		 * null을 거부한다.
		 *
		 * @throws NullPointerException 값이 null인 경우
		 */
		public Unusable {
			Objects.requireNonNull(checkedAt, "checkedAt");
			Objects.requireNonNull(reason, "reason");
		}

	}

	/**
	 * 전송 계층의 호출 결과 하나를 판정하고, 쓸 수 있으면 항목마다 변환한다.
	 *
	 * @param <S> 해석된 응답 항목 타입
	 * @param <T> 변환된 항목 타입
	 * @param call 호출 결과
	 * @param checkedAt 조회 시각
	 * @param convert 항목 하나의 변환. 쓸 수 있을 때만 부른다
	 * @return 판정 결과
	 * @throws NullPointerException 입력이 null인 경우
	 */
	static <S, T> CodefTargetOutcome<T> judge(CodefCallResult<S> call, Instant checkedAt,
			Function<? super S, ? extends T> convert) {
		Objects.requireNonNull(call, "call");
		Objects.requireNonNull(checkedAt, "checkedAt");
		Objects.requireNonNull(convert, "convert");
		return switch (call) {
			case CodefCallResult.Failed<S> failed -> new Unusable<>(checkedAt,
					new CodefUnusableReason.CallFailed(failed.stage(), failed.reason(), failed.httpStatus()));
			case CodefCallResult.Answered<S> answered -> switch (answered.response()) {
				case CodefProductResponse.Unauthorized<S> unauthorized -> new Unusable<>(checkedAt,
						new CodefUnusableReason.HttpUnauthorized());
				case CodefProductResponse.HttpFailure<S> failure -> new Unusable<>(checkedAt,
						new CodefUnusableReason.HttpFailed(failure.status()));
				case CodefProductResponse.Interpreted<S> interpreted -> judgeBody(interpreted.response(), checkedAt,
						convert);
			};
		};
	}

	private static <S, T> CodefTargetOutcome<T> judgeBody(CodefResponse<S> response, Instant checkedAt,
			Function<? super S, ? extends T> convert) {
		CodefResult result = response.result();
		return switch (response.data()) {
			case CodefList.Items<S> items when result.category() == CodefResultCategory.SUCCESS -> new Usable<>(checkedAt,
					result, items.shape(), items.items().stream().<T>map(convert).toList());
			case CodefList.Items<S> items -> new Unusable<>(checkedAt,
					new CodefUnusableReason.ResponseNotUsable(result, DataState.ITEMS, Optional.empty()));
			case CodefList.Missing<S> missing -> new Unusable<>(checkedAt,
					new CodefUnusableReason.ResponseNotUsable(result, DataState.MISSING, Optional.empty()));
			case CodefList.Malformed<S> malformed -> new Unusable<>(checkedAt, new CodefUnusableReason.ResponseNotUsable(
					result, DataState.MALFORMED, Optional.of(malformed.reason())));
		};
	}

}
