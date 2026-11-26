package com.beone.api.codef.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * CODEF 보유카드 카드명 → 규칙 카드 상품 후보 사전(D-15 2026-11-26 변경, #268).
 *
 * <p>카드사 카드명이 사전 표기와 정확히 같을 때만 후보 상품을 낸다. 부분 일치·대소문자 무시·유사도는 쓰지 않는다. 후보는 확정이 아니며
 * 사용자가 매핑 변경 API로 확인해야 {@code USER_CONFIRMED}가 된다. 동일 계열·변형 카드(예: "펭수 노리", "위글위글 첵첵")는 다른
 * 상품이라 넣지 않는다.
 *
 * <p>근거 표기: {@link Basis#OBSERVED}는 2026-11-26 비공개 실연동 동기화 응답의 카드명 표기, {@link Basis#ASSUMED}는 KB 상품
 * 페이지(교통카드 기능 옵션 등)로부터의 추정이다. 사용자 식별 정보·카드번호·금액은 담지 않는다.
 */
public final class CodefCardNameDictionary {

	/** 표기 근거. */
	public enum Basis {

		/** 2026-11-26 비공개 실연동 동기화 응답에서 본 카드명 표기. */
		OBSERVED,

		/** KB 상품 페이지로부터 추정한 표기. 실제 응답에서 아직 보지 않았다. */
		ASSUMED

	}

	/**
	 * 사전 항목 하나.
	 * @param cardProductId 규칙 카드 상품 ID(선행 0 보존)
	 * @param cardName 카드사 카드명 표기(앞뒤 공백 없음)
	 * @param basis 표기 근거
	 */
	public record Entry(String cardProductId, String cardName, Basis basis) {

		public Entry {
			Objects.requireNonNull(cardProductId, "cardProductId");
			Objects.requireNonNull(cardName, "cardName");
			Objects.requireNonNull(basis, "basis");
			if (cardName.isEmpty() || !cardName.equals(cardName.strip())) {
				throw new IllegalArgumentException("cardName must be non-empty and stripped");
			}
		}

	}

	/** 사전 항목. 상품 ID는 {@code SupportedCardProducts}의 4종과 같다. */
	static final List<Entry> ENTRIES = List.of(
			new Entry("09271", "올라운드 티타늄카드", Basis.OBSERVED),
			new Entry("01664", "nori 체크카드(RF)", Basis.OBSERVED),
			new Entry("01664", "nori 체크카드", Basis.ASSUMED),
			new Entry("01664", "노리 체크카드", Basis.ASSUMED),
			new Entry("01664", "노리 체크카드(RF)", Basis.ASSUMED),
			new Entry("09174", "청춘대로 톡톡카드", Basis.ASSUMED),
			new Entry("09174", "청춘대로 톡톡카드(RF)", Basis.ASSUMED),
			new Entry("01914", "첵첵 체크카드", Basis.ASSUMED),
			new Entry("01914", "첵첵 체크카드(RF)", Basis.ASSUMED));

	private static final Map<String, String> PRODUCT_BY_NAME = index(ENTRIES);

	private CodefCardNameDictionary() {
	}

	/**
	 * 카드사 카드명에 맞는 후보 상품 ID를 찾는다.
	 *
	 * <p>앞뒤 공백을 뗀 카드명을 사전 표기와 정확히 비교한다. 일치하지 않고 카드명이 {@code 카드명|발급사} 형식이면 첫 {@code |} 앞
	 * 부분(앞뒤 공백 제거)으로 한 번 더 정확히 비교한다. 보유카드 카드명({@code resCardName})은 {@code 카드명|발급사} 형식일 수
	 * 있다고 적혀 있어서다({@link com.beone.api.codef.CodefHeldCard}). 이 분리는 후보 대조에만 쓰고 저장하는 카드명 원문과 카드
	 * 연결(R1)은 나누지 않은 그대로다.
	 * @param codefCardName CODEF 카드명 원문. {@code null}이면 후보 없음
	 * @return 후보 상품 ID. 없으면 빈 값
	 */
	public static Optional<String> candidateFor(String codefCardName) {
		if (codefCardName == null) {
			return Optional.empty();
		}
		String name = codefCardName.strip();
		String product = PRODUCT_BY_NAME.get(name);
		if (product == null) {
			int bar = name.indexOf('|');
			if (bar >= 0) {
				product = PRODUCT_BY_NAME.get(name.substring(0, bar).strip());
			}
		}
		return Optional.ofNullable(product);
	}

	/**
	 * 사전 항목 목록.
	 * @return 불변 목록
	 */
	public static List<Entry> entries() {
		return ENTRIES;
	}

	// 같은 표기가 두 상품에 걸리면(같은 상품 안의 중복 포함) 사전을 쓸 수 없으므로 시작 시점에 실패시킨다.
	static Map<String, String> index(List<Entry> entries) {
		Map<String, String> byName = new HashMap<>();
		for (Entry entry : entries) {
			if (byName.putIfAbsent(entry.cardName(), entry.cardProductId()) != null) {
				throw new IllegalStateException("duplicate card name in dictionary");
			}
		}
		return Map.copyOf(byName);
	}

}
