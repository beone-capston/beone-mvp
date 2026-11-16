package com.beone.api.codef.sync;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 승인 행의 사용일자·사용일시 해석(결정 R4). 공급자 형식 문서가 없어 제안 형식(yyyyMMdd·HHmmss)으로만 읽는다. 제안이며 실계정 관측 전이다.
 *
 * <p>
 * 사용일자는 정확히 8자리 숫자이고 실제 있는 날짜일 때만, 사용일시는 정확히 6자리 숫자이고 유효한 시각(00:00:00~23:59:59)일 때만 값이 있다. 그 밖에는 추정하지
 * 않고 해석 실패로 남긴다. 원문 문자열은 기존 행 분류({@link CodefApprovalRowView})에 그대로 남는다.
 *
 * @param date 사용일자. 해석하지 못하면 빈 값
 * @param dateState 사용일자 해석 상태
 * @param time 사용일시. 해석하지 못하면 빈 값
 * @param timeState 사용일시 해석 상태
 */
public record CodefUsedDateTime(Optional<LocalDate> date, ParseState dateState, Optional<LocalTime> time,
		ParseState timeState) {

	private static final Pattern EIGHT_DIGITS = Pattern.compile("[0-9]{8}");

	private static final Pattern SIX_DIGITS = Pattern.compile("[0-9]{6}");

	private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("uuuuMMdd")
		.withResolverStyle(ResolverStyle.STRICT);

	private static final DateTimeFormatter HHMMSS = DateTimeFormatter.ofPattern("HHmmss")
		.withResolverStyle(ResolverStyle.STRICT);

	/**
	 * 값과 상태가 맞는지 확인한다.
	 *
	 * @throws NullPointerException 값이 null인 경우
	 * @throws IllegalArgumentException 값이 있는데 상태가 해석 완료가 아니거나, 그 반대인 경우
	 */
	public CodefUsedDateTime {
		Objects.requireNonNull(date, "date");
		Objects.requireNonNull(dateState, "dateState");
		Objects.requireNonNull(time, "time");
		Objects.requireNonNull(timeState, "timeState");
		if (date.isPresent() != (dateState == ParseState.PARSED)) {
			throw new IllegalArgumentException("date is present exactly when dateState is PARSED");
		}
		if (time.isPresent() != (timeState == ParseState.PARSED)) {
			throw new IllegalArgumentException("time is present exactly when timeState is PARSED");
		}
	}

	/**
	 * 사용일자·사용일시 원문을 해석한다. 공백을 지우거나 자릿수를 채우지 않는다.
	 *
	 * @param usedDate 사용일자 원문
	 * @param usedTime 사용일시 원문
	 * @return 해석 결과
	 * @throws NullPointerException 입력이 null인 경우
	 */
	public static CodefUsedDateTime parse(Optional<String> usedDate, Optional<String> usedTime) {
		Objects.requireNonNull(usedDate, "usedDate");
		Objects.requireNonNull(usedTime, "usedTime");
		Optional<LocalDate> date = usedDate.filter(text -> EIGHT_DIGITS.matcher(text).matches())
			.flatMap(text -> parseOrEmpty(() -> LocalDate.parse(text, YYYYMMDD)));
		Optional<LocalTime> time = usedTime.filter(text -> SIX_DIGITS.matcher(text).matches())
			.flatMap(text -> parseOrEmpty(() -> LocalTime.parse(text, HHMMSS)));
		return new CodefUsedDateTime(date, state(usedDate, date), time, state(usedTime, time));
	}

	/**
	 * 날짜와 시각이 모두 해석됐는지.
	 *
	 * @return 둘 다 해석됐으면 참
	 */
	public boolean complete() {
		return dateState == ParseState.PARSED && timeState == ParseState.PARSED;
	}

	private static <T> Optional<T> parseOrEmpty(Supplier<T> parser) {
		try {
			return Optional.of(parser.get());
		}
		catch (DateTimeParseException invalid) {
			// 예외 문구에 원문이 들어가므로 전달하지 않는다.
			return Optional.empty();
		}
	}

	private static ParseState state(Optional<String> text, Optional<?> parsed) {
		if (text.isEmpty()) {
			return ParseState.ABSENT;
		}
		return parsed.isPresent() ? ParseState.PARSED : ParseState.UNPARSED;
	}

	/** 해석 상태. */
	public enum ParseState {

		/** 제안 형식으로 읽혔다. */
		PARSED,

		/** 키 누락·빈 문자열. */
		ABSENT,

		/** 값은 있지만 제안 형식이 아니거나 실제 있는 날짜·시각이 아니다. 추정하지 않는다. */
		UNPARSED

	}

}
