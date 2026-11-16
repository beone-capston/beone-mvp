package com.beone.api.codef.sync;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 같은 동기화의 보유카드 목록으로 승인·실적 행의 카드를 정한다(결정 R1, 관측 전 후보 처리).
 *
 * <p>
 * 행의 카드명과 문자열로 정확히 같은 보유카드가 정확히 한 장일 때만 연결한다. 공백 정리·대소문자 무시·"카드명|발급사" 분리 같은 정규화는 하지 않는다. 같은
 * 이름의 카드를 두 장 가진 사용자는 자동으로 연결되지 않는다. 카드명으로 후보 상품을 정하는 일(R3)은 하지 않는다.
 */
public final class CodefCardLinker {

	private final boolean heldCardsAvailable;

	private final Map<String, Integer> heldCardNameCounts;

	private CodefCardLinker(boolean heldCardsAvailable, Map<String, Integer> heldCardNameCounts) {
		this.heldCardsAvailable = heldCardsAvailable;
		this.heldCardNameCounts = Map.copyOf(heldCardNameCounts);
	}

	/**
	 * 같은 동기화의 보유카드 판정 결과로 만든다. 쓸 수 없는 결과면 어떤 행도 연결하지 않는다.
	 *
	 * @param heldCards 보유카드 판정 결과
	 * @return 연결기
	 * @throws NullPointerException 입력이 null인 경우
	 */
	public static CodefCardLinker of(CodefTargetOutcome<CodefHeldCardCandidate> heldCards) {
		Objects.requireNonNull(heldCards, "heldCards");
		return switch (heldCards) {
			case CodefTargetOutcome.Usable<CodefHeldCardCandidate> usable -> {
				Map<String, Integer> counts = new HashMap<>();
				for (CodefHeldCardCandidate card : usable.items()) {
					card.cardName().ifPresent(name -> counts.merge(name, 1, Integer::sum));
				}
				yield new CodefCardLinker(true, counts);
			}
			case CodefTargetOutcome.Unusable<CodefHeldCardCandidate> unusable -> new CodefCardLinker(false, Map.of());
		};
	}

	/**
	 * 행의 카드명으로 보유카드를 정한다.
	 *
	 * @param rowCardName 승인·실적 행의 카드명
	 * @return 연결 결과
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
		if (count == 0) {
			return new CodefCardLink.NotLinked(CodefCardLink.Reason.NO_MATCHING_HELD_CARD);
		}
		if (count > 1) {
			return new CodefCardLink.NotLinked(CodefCardLink.Reason.MULTIPLE_MATCHING_HELD_CARDS);
		}
		return new CodefCardLink.Linked(rowCardName.get());
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
