package com.beone.api.codef;

import java.util.List;
import java.util.Objects;

/**
 * CODEF 목록 값의 해석. 문서는 "단건의 경우 객체형, 다건의 경우 리스트형"이라고 하므로 객체는 1건, 배열은 N건, 빈 배열은 0건이다.
 * 0건과 누락·형식 오류를 구분한다.
 *
 * @param <T> 항목 타입
 */
public sealed interface CodefList<T> {

	/**
	 * 객체 또는 배열로 받은 항목.
	 *
	 * @param <T> 항목 타입
	 * @param shape 받은 형태
	 * @param items 항목. 객체형이면 정확히 1건, 배열형이면 0건 이상
	 */
	record Items<T>(Shape shape, List<T> items) implements CodefList<T> {

		/**
		 * 형태와 건수를 확인하고 항목을 불변 복사한다.
		 *
		 * @throws IllegalArgumentException 객체형인데 항목이 1건이 아닌 경우
		 */
		public Items {
			Objects.requireNonNull(shape, "shape");
			items = List.copyOf(items);
			if (shape == Shape.SINGLE_OBJECT && items.size() != 1) {
				throw new IllegalArgumentException("single object shape needs exactly one item");
			}
		}

	}

	/**
	 * 키가 없거나, 중첩 목록이 빈 문자열인 경우. 0건이 아니라 값 없음(미확인)이다.
	 *
	 * @param <T> 항목 타입
	 */
	record Missing<T>() implements CodefList<T> {
	}

	/**
	 * null, 원시값, 객체가 아닌 배열 원소처럼 문서 규칙에 맞지 않는 형태. 0건이 아니다.
	 *
	 * @param <T> 항목 타입
	 * @param reason 형식 오류 종류. 응답 원문을 담지 않는 고정 문구다
	 */
	record Malformed<T>(String reason) implements CodefList<T> {

		/**
		 * null을 거부한다.
		 *
		 * @throws NullPointerException 사유가 null인 경우
		 */
		public Malformed {
			Objects.requireNonNull(reason, "reason");
		}

	}

	/** 받은 목록의 형태. */
	enum Shape {

		/** 객체 하나(단건). */
		SINGLE_OBJECT,

		/** 배열(다건, 0건 포함). */
		ARRAY

	}

}
