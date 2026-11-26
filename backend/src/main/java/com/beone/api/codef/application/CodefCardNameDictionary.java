package com.beone.api.codef.application;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * CODEF 보유카드 카드명 → 규칙 카드 상품 후보 사전(D-15 2026-11-26 변경, #268).
 *
 * <p>카드사 카드명이 사전 표기와 정확히 같을 때만 후보 상품을 낸다. 부분 일치·대소문자 무시·유사도는 쓰지 않는다. 후보는 확정이 아니며
 * 사용자가 매핑 변경 API로 확인해야 {@code USER_CONFIRMED}가 된다. 동일 계열·변형 카드(예: "펭수 노리", "위글위글 첵첵")는 다른
 * 상품이라 넣지 않는다.
 *
 * <p>근거 표기: {@link Basis#OBSERVED}는 비공개 실연동 동기화 응답(2026-11-26 보유카드, 2026-11-27 재동기화 실적·승인)의 카드명 표기,
 * {@link Basis#ASSUMED}는 KB 상품 페이지(교통카드 기능 옵션 등)로부터의 추정이다. 항목마다 그 표기를 본 출처({@link Source})를 적는다.
 * KB는 API마다 같은 카드의 카드명을 다르게 준다(예: 보유카드 "nori 체크카드(RF)", 실적 "KB국민 노리체크카드", 승인 "KB국민 nori 체크카드(RF)"). 그래서 승인·실적 행을
 * 보유카드에 연결할 때도 이 사전으로 상품 단위 연결을 한다(#270). 지원 상품 4종이 아닌 카드(예: "KB Youth Club 체크카드",
 * "KB국민 나라사랑체크카드(일반형 RF)")는 넣지 않는다.
 * 사용자 식별 정보·카드번호·금액은 담지 않는다.
 */
public final class CodefCardNameDictionary {

	/** 표기 근거. */
	public enum Basis {

		/** 2026-11-26 비공개 실연동 동기화 응답에서 본 카드명 표기. */
		OBSERVED,

		/** KB 상품 페이지로부터 추정한 표기. 실제 응답에서 아직 보지 않았다. */
		ASSUMED

	}

	/** 표기를 본 출처. */
	public enum Source {

		/** CODEF 보유카드 조회 응답. */
		HELD_CARDS,

		/** CODEF 실적조회 응답. */
		PERFORMANCES,

		/** CODEF 승인내역 응답. */
		APPROVALS,

		/** KB 상품 페이지로부터의 추정. */
		PRODUCT_PAGE

	}

	/**
	 * 사전 항목 하나.
	 * @param cardProductId 규칙 카드 상품 ID(선행 0 보존)
	 * @param cardName 카드사 카드명 표기(앞뒤 공백 없음)
	 * @param basis 표기 근거
	 * @param sources 표기를 본 출처. {@code OBSERVED}면 CODEF 응답만, {@code ASSUMED}면 상품 페이지 추정만
	 */
	public record Entry(String cardProductId, String cardName, Basis basis, Set<Source> sources) {

		public Entry {
			Objects.requireNonNull(cardProductId, "cardProductId");
			Objects.requireNonNull(cardName, "cardName");
			Objects.requireNonNull(basis, "basis");
			Objects.requireNonNull(sources, "sources");
			if (cardName.isEmpty() || !cardName.equals(cardName.strip())) {
				throw new IllegalArgumentException("cardName must be non-empty and stripped");
			}
			sources = Set.copyOf(sources);
			boolean assumed = sources.equals(Set.of(Source.PRODUCT_PAGE));
			if (sources.isEmpty() || (basis == Basis.ASSUMED) != assumed
					|| (basis == Basis.OBSERVED && sources.contains(Source.PRODUCT_PAGE))) {
				throw new IllegalArgumentException("sources must match the basis");
			}
		}

		private static Entry observed(String cardProductId, String cardName, Source first, Source... rest) {
			return new Entry(cardProductId, cardName, Basis.OBSERVED, EnumSet.of(first, rest));
		}

		private static Entry assumed(String cardProductId, String cardName) {
			return new Entry(cardProductId, cardName, Basis.ASSUMED, Set.of(Source.PRODUCT_PAGE));
		}

	}

	/** 사전 항목. 상품 ID는 {@code SupportedCardProducts}의 4종과 같다. */
	static final List<Entry> ENTRIES = List.of(
			// 보유카드(2026-11-26)·실적(2026-11-27) 응답에서 같은 표기
			Entry.observed("09271", "올라운드 티타늄카드", Source.HELD_CARDS, Source.PERFORMANCES),
			// 승인 응답(2026-11-27 재동기화, #272)
			Entry.observed("09271", "KB국민 올라운드 티타늄카드", Source.APPROVALS),
			// 보유카드 응답(2026-11-26)
			Entry.observed("01664", "nori 체크카드(RF)", Source.HELD_CARDS),
			// 실적 응답(2026-11-27 재동기화, #270)
			Entry.observed("01664", "KB국민 노리체크카드", Source.PERFORMANCES),
			// 승인 응답(2026-11-27 재동기화, #272)
			Entry.observed("01664", "KB국민 nori 체크카드(RF)", Source.APPROVALS),
			Entry.assumed("01664", "nori 체크카드"),
			Entry.assumed("01664", "노리 체크카드"),
			Entry.assumed("01664", "노리 체크카드(RF)"),
			Entry.assumed("09174", "청춘대로 톡톡카드"),
			Entry.assumed("09174", "청춘대로 톡톡카드(RF)"),
			Entry.assumed("01914", "첵첵 체크카드"),
			Entry.assumed("01914", "첵첵 체크카드(RF)"));

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
