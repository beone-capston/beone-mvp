package com.beone.api.codef.sync;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

import com.beone.api.codef.CodefAmount;
import com.beone.api.codef.CodefCardPerformance;
import com.beone.api.codef.CodefCardPerformance.PerformanceMet;
import com.beone.api.codef.CodefList;

/**
 * 카드 한 장의 "카드사 조회 실적(참고)"(D-16). 화면에 참고로 보여 줄 값이며 계산에 쓰지 않는다.
 *
 * <p>
 * 실적조회 금액은 전월 실적에 자동으로 넣지 않는다. 그래서 {@link #usedInCalculation()}은 항상 거짓이고, 엔진 입력({@code StateInput}·{@code StateValue})으로
 * 바꾸는 메서드도 두지 않는다. 사용자가 이 값을 보고 직접 입력하면 그 값은 출처 {@code MANUAL}로 따로 저장한다(업무 API 담당). 혜택·실적현황이 여러 개여도
 * 하나를 고르지 않고 모두 담는다. 반영기간은 문서 형식(yyyyMMdd)의 날짜로만 읽고 BeONE 실적 기간으로 바꾸지 않는다. 승인·보유카드와의 카드 연결(R1)은
 * 하지 않는다.
 *
 * @param cardName 카드명. 상품 식별에 쓰지 않는다
 * @param cardType 소지자구분. KB는 제공하지 않는다(실적조회!H14)
 * @param cardCompany 발급사. KB는 제공하지 않는다(실적조회!H15)
 * @param benefits 혜택 목록. 누락·형식 오류는 0건과 구분한다
 * @param checkedAt 조회 시각(D-16의 참고 조회 시각)
 */
public record CodefPerformanceReference(Optional<String> cardName, Optional<String> cardType,
		Optional<String> cardCompany, CodefList<BenefitReference> benefits, Instant checkedAt) {

	private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("uuuuMMdd")
		.withResolverStyle(ResolverStyle.STRICT);

	/** {@code uuuu}는 부호 있는 연도·더 긴 연도도 받으므로 파싱 전에 정확히 8자리 숫자인지 먼저 본다. */
	private static final Pattern EIGHT_DIGITS = Pattern.compile("[0-9]{8}");

	/**
	 * null을 거부한다.
	 *
	 * @throws NullPointerException 값이 null인 경우
	 */
	public CodefPerformanceReference {
		Objects.requireNonNull(cardName, "cardName");
		Objects.requireNonNull(cardType, "cardType");
		Objects.requireNonNull(cardCompany, "cardCompany");
		Objects.requireNonNull(benefits, "benefits");
		Objects.requireNonNull(checkedAt, "checkedAt");
	}

	/**
	 * 해석된 실적조회 카드 한 장을 참고값으로 옮긴다.
	 *
	 * @param card 해석된 실적조회 카드
	 * @param checkedAt 조회 시각
	 * @return 참고값
	 * @throws NullPointerException 입력이 null인 경우
	 */
	public static CodefPerformanceReference from(CodefCardPerformance card, Instant checkedAt) {
		Objects.requireNonNull(card, "card");
		return new CodefPerformanceReference(card.cardName(), card.cardType(), card.cardCompany(),
				mapList(card.benefits(), BenefitReference::from), checkedAt);
	}

	/**
	 * 계산에 쓰이는지.
	 *
	 * @return 항상 거짓(D-16)
	 */
	public boolean usedInCalculation() {
		return false;
	}

	private static <S, T> CodefList<T> mapList(CodefList<S> list, Function<S, T> convert) {
		return switch (list) {
			case CodefList.Items<S> items -> new CodefList.Items<>(items.shape(),
					items.items().stream().map(convert).toList());
			case CodefList.Missing<S> missing -> new CodefList.Missing<>();
			case CodefList.Malformed<S> malformed -> new CodefList.Malformed<>(malformed.reason());
		};
	}

	/**
	 * 혜택 하나의 참고값. 상세내역({@code resDetailList})은 옮기지 않는다.
	 *
	 * @param type 혜택구분. KB는 제공하지 않는다(실적조회!H18)
	 * @param benefitName 혜택명. KB는 제공하지 않는다(실적조회!H19)
	 * @param performances 실적현황 목록. 누락·형식 오류는 0건과 구분한다
	 */
	public record BenefitReference(Optional<String> type, Optional<String> benefitName,
			CodefList<PerformanceEntry> performances) {

		/**
		 * null을 거부한다.
		 *
		 * @throws NullPointerException 값이 null인 경우
		 */
		public BenefitReference {
			Objects.requireNonNull(type, "type");
			Objects.requireNonNull(benefitName, "benefitName");
			Objects.requireNonNull(performances, "performances");
		}

		static BenefitReference from(CodefCardPerformance.Benefit benefit) {
			return new BenefitReference(benefit.type(), benefit.benefitName(),
					mapList(benefit.performances(), PerformanceEntry::from));
		}

	}

	/**
	 * 실적현황 하나의 참고값. 기준이용금액 = 현재이용금액 + 필요실적금액 관계를 검증하거나 빈 값을 계산해 채우지 않는다.
	 *
	 * @param currentUse 현재이용금액
	 * @param requiredPerformance 필요실적금액. KB는 옵션이다(실적조회!H25)
	 * @param standardUse 기준이용금액
	 * @param startDate 이용금액 반영기간 시작일
	 * @param endDate 이용금액 반영기간 종료일. 당월 조회면 조회일 전날일 수 있으며 말일로 바꾸지 않는다(실적조회!H27 주석)
	 * @param coverageCriteria 제공기준 문구. KB는 제공하지 않는다(실적조회!H28)
	 * @param met 실적 충족여부. KB는 옵션이며 빈 값은 부족이 아니다(실적조회!H22)
	 */
	public record PerformanceEntry(ReferenceAmount currentUse, ReferenceAmount requiredPerformance,
			ReferenceAmount standardUse, ReferenceDate startDate, ReferenceDate endDate,
			Optional<String> coverageCriteria, PerformanceMet met) {

		/**
		 * null을 거부한다.
		 *
		 * @throws NullPointerException 값이 null인 경우
		 */
		public PerformanceEntry {
			Objects.requireNonNull(currentUse, "currentUse");
			Objects.requireNonNull(requiredPerformance, "requiredPerformance");
			Objects.requireNonNull(standardUse, "standardUse");
			Objects.requireNonNull(startDate, "startDate");
			Objects.requireNonNull(endDate, "endDate");
			Objects.requireNonNull(coverageCriteria, "coverageCriteria");
			Objects.requireNonNull(met, "met");
		}

		static PerformanceEntry from(CodefCardPerformance.PerformanceStatus status) {
			return new PerformanceEntry(ReferenceAmount.from(status.currentUse()),
					ReferenceAmount.from(status.requiredPerformance()), ReferenceAmount.from(status.standardUse()),
					ReferenceDate.from(status.startDate()), ReferenceDate.from(status.endDate()),
					status.coverageCriteria(), status.met());
		}

	}

	/**
	 * 참고 금액. 값과 미확인 이유 중 정확히 하나만 있다. 미확인을 0원으로 바꾸지 않는다.
	 *
	 * @param value 원문 정수 금액. 미확인이면 null
	 * @param unknownReason 미확인 이유. 값이 있으면 null
	 */
	public record ReferenceAmount(Long value, UnknownReason unknownReason) {

		/**
		 * 값과 이유가 정확히 하나만 있는지 확인한다.
		 *
		 * @throws IllegalArgumentException 둘 다 있거나 둘 다 없는 경우
		 */
		public ReferenceAmount {
			if ((value == null) == (unknownReason == null)) {
				throw new IllegalArgumentException("exactly one of value and unknownReason is needed");
			}
		}

		/**
		 * 확인된 금액.
		 *
		 * @param value 원문 정수 금액
		 * @return 참고 금액
		 */
		public static ReferenceAmount of(long value) {
			return new ReferenceAmount(value, null);
		}

		static ReferenceAmount from(CodefAmount amount) {
			return switch (amount) {
				case CodefAmount.Parsed(long parsed) -> of(parsed);
				case CodefAmount.Absent absent -> new ReferenceAmount(null, UnknownReason.ABSENT);
				case CodefAmount.Unparsed unparsed -> new ReferenceAmount(null, UnknownReason.UNPARSED);
			};
		}

	}

	/**
	 * 참고 날짜. 정확히 8자리 yyyyMMdd이고 실제 있는 날짜일 때만 값이 있다. 값과 미확인 이유 중 정확히 하나만 있다.
	 *
	 * @param value 날짜. 미확인이면 null
	 * @param unknownReason 미확인 이유. 값이 있으면 null
	 */
	public record ReferenceDate(LocalDate value, UnknownReason unknownReason) {

		/**
		 * 값과 이유가 정확히 하나만 있는지 확인한다.
		 *
		 * @throws IllegalArgumentException 둘 다 있거나 둘 다 없는 경우
		 */
		public ReferenceDate {
			if ((value == null) == (unknownReason == null)) {
				throw new IllegalArgumentException("exactly one of value and unknownReason is needed");
			}
		}

		/**
		 * 확인된 날짜.
		 *
		 * @param value 날짜
		 * @return 참고 날짜
		 * @throws NullPointerException 날짜가 null인 경우
		 */
		public static ReferenceDate of(LocalDate value) {
			return new ReferenceDate(Objects.requireNonNull(value, "value"), null);
		}

		static ReferenceDate from(Optional<String> text) {
			if (text.isEmpty()) {
				return new ReferenceDate(null, UnknownReason.ABSENT);
			}
			if (!EIGHT_DIGITS.matcher(text.get()).matches()) {
				return new ReferenceDate(null, UnknownReason.UNPARSED);
			}
			try {
				return of(LocalDate.parse(text.get(), YYYYMMDD));
			}
			catch (DateTimeParseException invalid) {
				// 예외 문구에 원문이 들어가므로 전달하지 않는다.
				return new ReferenceDate(null, UnknownReason.UNPARSED);
			}
		}

	}

	/** 참고값을 확인하지 못한 이유. */
	public enum UnknownReason {

		/** 키 누락·빈 문자열("미제공시 빈칸"). */
		ABSENT,

		/** 값은 있지만 문서 형식(정수 금액, yyyyMMdd 날짜)으로 읽히지 않았다. 원문은 보관하지 않는다. */
		UNPARSED

	}

}
