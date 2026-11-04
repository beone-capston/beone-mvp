package com.beone.api.codef;

import java.util.Objects;
import java.util.Optional;

import tools.jackson.databind.JsonNode;

/**
 * 실적조회 API({@code /v1/kr/card/p/account/result-check-list}, 문서 갱신일 2026-10-19) 출력 항목 중 비민감 필드.
 *
 * <p>
 * 카드 하나의 혜택 목록과 혜택별 상세·실적현황을 공식 예시 구조대로 중첩해 담는다. 카드번호({@code resCardNo}), 혜택 바로가기 링크, 세션은
 * 옮기지 않는다. 실적 기간·충족 여부를 BeONE 상태값으로 확정하지 않는다(D-04). 기관마다 전월·당월 제공 범위가 다르다는 문서 설명이 있다.
 *
 * @param cardName 카드명({@code resCardName}). 실제 상품 식별에 쓰지 않는다(D-01)
 * @param cardType 소지자구분({@code resCardType})
 * @param cardCompany 발급사({@code resCardCompany})
 * @param benefits 카드 혜택 목록({@code resCardBenefitList})
 */
public record CodefCardPerformance(Optional<String> cardName, Optional<String> cardType, Optional<String> cardCompany,
		CodefList<Benefit> benefits) {

	/**
	 * null을 거부한다.
	 *
	 * @throws NullPointerException 값이 null인 경우
	 */
	public CodefCardPerformance {
		Objects.requireNonNull(cardName, "cardName");
		Objects.requireNonNull(cardType, "cardType");
		Objects.requireNonNull(cardCompany, "cardCompany");
		Objects.requireNonNull(benefits, "benefits");
	}

	static CodefCardPerformance read(JsonNode item) {
		return new CodefCardPerformance(CodefFields.text(item, "resCardName"), CodefFields.text(item, "resCardType"),
				CodefFields.text(item, "resCardCompany"),
				CodefFields.list(item.get("resCardBenefitList"), Benefit::read, true));
	}

	/**
	 * 혜택 하나.
	 *
	 * @param type 혜택구분({@code resType})
	 * @param benefitName 대표 카드 혜택명({@code resCardBenefitName})
	 * @param businessTypes 업태(업종)({@code resBusinessTypes})
	 * @param details 상세내역({@code resDetailList})
	 * @param performances 카드 실적현황({@code resCardPerformanceList})
	 */
	public record Benefit(Optional<String> type, Optional<String> benefitName, Optional<String> businessTypes,
			CodefList<BenefitDetail> details, CodefList<PerformanceStatus> performances) {

		/**
		 * null을 거부한다.
		 *
		 * @throws NullPointerException 값이 null인 경우
		 */
		public Benefit {
			Objects.requireNonNull(type, "type");
			Objects.requireNonNull(benefitName, "benefitName");
			Objects.requireNonNull(businessTypes, "businessTypes");
			Objects.requireNonNull(details, "details");
			Objects.requireNonNull(performances, "performances");
		}

		static Benefit read(JsonNode item) {
			return new Benefit(CodefFields.text(item, "resType"), CodefFields.text(item, "resCardBenefitName"),
					CodefFields.text(item, "resBusinessTypes"),
					CodefFields.list(item.get("resDetailList"), BenefitDetail::read, true),
					CodefFields.list(item.get("resCardPerformanceList"), PerformanceStatus::read, true));
		}

	}

	/**
	 * 혜택 상세 하나.
	 *
	 * @param benefitName 카드 혜택명({@code resCardBenefitName})
	 * @param performanceConditions 실적조건({@code resPerformanceConditions}). 문구 그대로
	 * @param businessTypes 업태(업종)({@code resBusinessTypes})
	 */
	public record BenefitDetail(Optional<String> benefitName, Optional<String> performanceConditions,
			Optional<String> businessTypes) {

		/**
		 * null을 거부한다.
		 *
		 * @throws NullPointerException 값이 null인 경우
		 */
		public BenefitDetail {
			Objects.requireNonNull(benefitName, "benefitName");
			Objects.requireNonNull(performanceConditions, "performanceConditions");
			Objects.requireNonNull(businessTypes, "businessTypes");
		}

		static BenefitDetail read(JsonNode item) {
			return new BenefitDetail(CodefFields.text(item, "resCardBenefitName"),
					CodefFields.text(item, "resPerformanceConditions"), CodefFields.text(item, "resBusinessTypes"));
		}

	}

	/**
	 * 실적현황 하나. 문서상 기준이용금액 = 현재이용금액 + 필요실적금액이지만 그 관계를 검증하거나 빈 값을 계산해 채우지 않는다.
	 *
	 * @param currentUse 현재이용금액({@code resCurrentUseAmt})
	 * @param requiredPerformance 필요실적금액({@code resRequiredPerformanceAmt})
	 * @param standardUse 기준이용금액({@code resStandardUseAmt})
	 * @param startDate 이용금액 반영기간 시작일({@code commStartDate}, 문서 형식 yyyyMMdd). 문자열 그대로
	 * @param endDate 이용금액 반영기간 종료일({@code commEndDate}, 문서 형식 yyyyMMdd). 문자열 그대로
	 * @param coverageCriteria 제공기준({@code resCoverageCriteria}). 문구 그대로
	 * @param met 실적 충족여부({@code resMeetPerformanceYN})
	 */
	public record PerformanceStatus(CodefAmount currentUse, CodefAmount requiredPerformance, CodefAmount standardUse,
			Optional<String> startDate, Optional<String> endDate, Optional<String> coverageCriteria, PerformanceMet met) {

		/**
		 * null을 거부한다.
		 *
		 * @throws NullPointerException 값이 null인 경우
		 */
		public PerformanceStatus {
			Objects.requireNonNull(currentUse, "currentUse");
			Objects.requireNonNull(requiredPerformance, "requiredPerformance");
			Objects.requireNonNull(standardUse, "standardUse");
			Objects.requireNonNull(startDate, "startDate");
			Objects.requireNonNull(endDate, "endDate");
			Objects.requireNonNull(coverageCriteria, "coverageCriteria");
			Objects.requireNonNull(met, "met");
		}

		static PerformanceStatus read(JsonNode item) {
			return new PerformanceStatus(CodefFields.amount(item, "resCurrentUseAmt"),
					CodefFields.amount(item, "resRequiredPerformanceAmt"), CodefFields.amount(item, "resStandardUseAmt"),
					CodefFields.text(item, "commStartDate"), CodefFields.text(item, "commEndDate"),
					CodefFields.text(item, "resCoverageCriteria"),
					CodefFields.code(item, "resMeetPerformanceYN", PerformanceMet.values(), PerformanceMet::code,
							PerformanceMet.UNKNOWN));
		}

	}

	/** 실적 충족여부. 문서 값 "0"·"1". */
	public enum PerformanceMet {

		/** "0": 부족. */
		SHORT("0"),

		/** "1": 충족. */
		MET("1"),

		/** 빈 값 또는 문서 목록 밖의 값. */
		UNKNOWN(null);

		private final String code;

		PerformanceMet(String code) {
			this.code = code;
		}

		String code() {
			return code;
		}

	}

}
