package com.beone.api.codef.sync;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * 같은 동기화의 보유카드 목록으로 승인·실적 행의 카드를 정한다(결정 R1, 2026-11-27 사용자 결정으로 이름 사전 단계 추가).
 *
 * <p>
 * 1단계: 행의 카드명과 문자열로 정확히 같은 보유카드가 정확히 한 장이면 연결한다. 공백 정리·대소문자 무시·"카드명|발급사" 분리 같은 정규화는 하지 않는다. 같은
 * 이름의 카드를 두 장 가진 사용자는 자동으로 연결되지 않는다.
 *
 * <p>
 * 2단계(이름 사전이 주어졌을 때만): 1단계에서 같은 이름의 보유카드가 없으면, 행 카드명을 이름 사전으로 상품 ID로 바꾸고 같은 동기화의 보유카드 중 사전으로 같은
 * 상품이 되는 카드가 정확히 한 장일 때만 그 카드에 연결한다. KB가 API마다 같은 카드의 카드명을 다르게 주기 때문이다(예: 보유카드 "nori 체크카드(RF)", 실적
 * "KB국민 노리체크카드"). 이름 사전 조회 규칙(앞뒤 공백 제거 정확 일치, "카드명|발급사"의 앞부분 1회 재비교) 밖의 정규화·부분 일치는 하지 않는다. 이 연결도
 * 같은 실물 카드의 입증이 아니며, 후보 상품을 확정하지 않는다.
 */
public final class CodefCardLinker {

	private final boolean heldCardsAvailable;

	private final Map<String, Integer> heldCardNameCounts;

	private final Optional<Function<String, Optional<String>>> productOf;

	private final Map<String, Integer> heldCardProductCounts;

	private final Map<String, String> heldCardNameByProduct;

	private CodefCardLinker(boolean heldCardsAvailable, Map<String, Integer> heldCardNameCounts,
			Optional<Function<String, Optional<String>>> productOf, Map<String, Integer> heldCardProductCounts,
			Map<String, String> heldCardNameByProduct) {
		this.heldCardsAvailable = heldCardsAvailable;
		this.heldCardNameCounts = Map.copyOf(heldCardNameCounts);
		this.productOf = productOf;
		this.heldCardProductCounts = Map.copyOf(heldCardProductCounts);
		this.heldCardNameByProduct = Map.copyOf(heldCardNameByProduct);
	}

	/**
	 * 같은 동기화의 보유카드 판정 결과로 만든다. 정확 일치만 쓴다. 쓸 수 없는 결과면 어떤 행도 연결하지 않는다.
	 *
	 * @param heldCards 보유카드 판정 결과
	 * @return 연결기
	 * @throws NullPointerException 입력이 null인 경우
	 */
	public static CodefCardLinker of(CodefTargetOutcome<CodefHeldCardCandidate> heldCards) {
		return create(heldCards, Optional.empty());
	}

	/**
	 * 정확 일치 뒤 이름 사전 상품 단위 연결까지 쓰는 연결기를 만든다.
	 *
	 * @param heldCards 보유카드 판정 결과
	 * @param productOf 카드사 카드명 → 이름 사전의 상품 ID. 사전에 없으면 빈 값
	 * @return 연결기
	 * @throws NullPointerException 입력이 null인 경우
	 */
	public static CodefCardLinker of(CodefTargetOutcome<CodefHeldCardCandidate> heldCards,
			Function<String, Optional<String>> productOf) {
		return create(heldCards, Optional.of(Objects.requireNonNull(productOf, "productOf")));
	}

	private static CodefCardLinker create(CodefTargetOutcome<CodefHeldCardCandidate> heldCards,
			Optional<Function<String, Optional<String>>> productOf) {
		Objects.requireNonNull(heldCards, "heldCards");
		return switch (heldCards) {
			case CodefTargetOutcome.Usable<CodefHeldCardCandidate> usable -> {
				Map<String, Integer> names = new HashMap<>();
				Map<String, Integer> products = new HashMap<>();
				Map<String, String> nameByProduct = new HashMap<>();
				for (CodefHeldCardCandidate card : usable.items()) {
					card.cardName().ifPresent(name -> {
						names.merge(name, 1, Integer::sum);
						productOf.flatMap(dictionary -> dictionary.apply(name)).ifPresent(product -> {
							products.merge(product, 1, Integer::sum);
							nameByProduct.put(product, name);
						});
					});
				}
				yield new CodefCardLinker(true, names, productOf, products, nameByProduct);
			}
			case CodefTargetOutcome.Unusable<CodefHeldCardCandidate> unusable ->
				new CodefCardLinker(false, Map.of(), productOf, Map.of(), Map.of());
		};
	}

	/**
	 * 행의 카드명으로 보유카드를 정한다.
	 *
	 * @param rowCardName 승인·실적 행의 카드명
	 * @return 연결 결과. 연결되면 그 보유카드의 카드명을 담는다(이름 사전 연결이면 행의 카드명과 다를 수 있다)
	 * @throws NullPointerException 입력이 null인 경우
	 */
	public CodefCardLink link(Optional<String> rowCardName) {
		Objects.requireNonNull(rowCardName, "rowCardName");
		if (rowCardName.isEmpty()) {
			return new CodefCardLink.NotLinked(CodefCardLink.Reason.ROW_CARD_NAME_ABSENT);
		}
		if (!heldCardsAvailable) {
			return new CodefCardLink.NotLinked(CodefCardLink.Reason.HELD_CARDS_UNAVAILABLE);
		}
		int count = heldCardNameCounts.getOrDefault(rowCardName.get(), 0);
		if (count > 1) {
			return new CodefCardLink.NotLinked(CodefCardLink.Reason.MULTIPLE_MATCHING_HELD_CARDS);
		}
		if (count == 1) {
			return new CodefCardLink.Linked(rowCardName.get());
		}
		if (productOf.isEmpty()) {
			return new CodefCardLink.NotLinked(CodefCardLink.Reason.NO_MATCHING_HELD_CARD);
		}
		return linkByProduct(rowCardName.get());
	}

	// 정확 일치가 없을 때만 온다. 같은 상품이 되는 보유카드가 정확히 한 장이면 그 카드의 이름으로 연결한다.
	// 그 이름은 보유카드 안에서 하나뿐이다: 같은 이름의 카드가 또 있으면 같은 상품이 되어 상품 개수가 2 이상이기 때문이다.
	private CodefCardLink linkByProduct(String rowCardName) {
		Optional<String> product = productOf.orElseThrow().apply(rowCardName);
		if (product.isEmpty()) {
			return new CodefCardLink.NotLinked(CodefCardLink.Reason.ROW_NAME_NOT_IN_DICTIONARY);
		}
		int count = heldCardProductCounts.getOrDefault(product.get(), 0);
		if (count == 0) {
			return new CodefCardLink.NotLinked(CodefCardLink.Reason.NO_HELD_CARD_FOR_PRODUCT);
		}
		if (count > 1) {
			return new CodefCardLink.NotLinked(CodefCardLink.Reason.MULTIPLE_HELD_CARDS_FOR_PRODUCT);
		}
		return new CodefCardLink.Linked(heldCardNameByProduct.get(product.get()));
	}

	/**
	 * 실적 참고값마다 카드를 정한다. 연결하지 못한 실적은 카드 미연결로 남고, 참고값 자체는 바꾸지 않는다.
	 *
	 * @param references 실적 참고값 목록
	 * @return 입력 순서대로의 연결 결과
	 * @throws NullPointerException 입력이 null인 경우
	 */
	public List<CodefPerformanceLink> linkPerformances(List<CodefPerformanceReference> references) {
		Objects.requireNonNull(references, "references");
		return references.stream()
			.map(reference -> new CodefPerformanceLink(reference, link(reference.cardName())))
			.toList();
	}

}
