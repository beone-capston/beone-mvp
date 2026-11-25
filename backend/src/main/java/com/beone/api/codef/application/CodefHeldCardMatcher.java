package com.beone.api.codef.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.beone.api.codef.sync.CodefHeldCardCandidate;

/**
 * CODEF 보유카드를 이 사용자의 보유 카드와 맞추는 순수 함수(관리자 판단 P8·Q1·Q2). 저장·로그가 없다.
 *
 * <p>카드명이 없거나 앞뒤 공백을 뗀 표시 이름이 1~30자가 아니면(원문 255자 초과 포함) 건너뛴다. 이름을 자르거나 지어내지 않는다. 나머지는
 * (카드명 원문, 발급일자 원문) 묶음으로 센다. 발급일자는 둘 다 없음도 같은 값이다. 같은 묶음이 응답에 n장, 해제하지 않은 기존 CODEF 카드에
 * m장이면 m=0일 때 n장 모두 새 카드, n=1·m=1이면 그 기존 카드, 그 밖은 같은 카드를 정할 수 없어 건너뛴다(중복 누적 방지).
 */
public final class CodefHeldCardMatcher {

	/** 표시 이름 최대 글자 수(docs/api/cards.md). */
	static final int DISPLAY_NAME_MAX = 30;

	/** 저장하는 카드명 원문 최대 글자 수({@code codef_held_cards.card_name}). */
	static final int CARD_NAME_MAX = 255;

	private CodefHeldCardMatcher() {
	}

	/**
	 * 응답 카드마다 처리 방법을 정한다.
	 * @param heldCards 응답 순서의 보유카드
	 * @param existing 해제하지 않은 기존 CODEF 카드의 대조 정보
	 * @return 응답 순서대로의 처리 방법
	 */
	public static List<Resolution> match(List<CodefHeldCardCandidate> heldCards, List<ExistingCard> existing) {
		Objects.requireNonNull(heldCards, "heldCards");
		Objects.requireNonNull(existing, "existing");
		Map<Identity, Integer> responseCounts = new HashMap<>();
		Map<Identity, List<Long>> existingIds = new HashMap<>();
		for (CodefHeldCardCandidate card : heldCards) {
			identity(card).ifPresent(identity -> responseCounts.merge(identity, 1, Integer::sum));
		}
		for (ExistingCard card : existing) {
			existingIds.computeIfAbsent(new Identity(card.cardName(), card.issueDate()), ignored -> new ArrayList<>())
				.add(card.userCardId());
		}
		List<Resolution> resolutions = new ArrayList<>(heldCards.size());
		for (CodefHeldCardCandidate card : heldCards) {
			resolutions.add(resolve(card, responseCounts, existingIds));
		}
		return List.copyOf(resolutions);
	}

	private static Resolution resolve(CodefHeldCardCandidate card, Map<Identity, Integer> responseCounts,
			Map<Identity, List<Long>> existingIds) {
		if (card.cardName().isEmpty()) {
			return new Resolution.Skipped(SkipReason.CARD_NAME_ABSENT);
		}
		Optional<Identity> identity = identity(card);
		if (identity.isEmpty()) {
			return new Resolution.Skipped(SkipReason.DISPLAY_NAME_INVALID);
		}
		int responseCount = responseCounts.get(identity.get());
		List<Long> matches = existingIds.getOrDefault(identity.get(), List.of());
		if (matches.isEmpty()) {
			return new Resolution.Create(identity.get().cardName(), displayName(identity.get().cardName()),
					identity.get().issueDate());
		}
		if (responseCount == 1 && matches.size() == 1) {
			return new Resolution.Existing(matches.getFirst());
		}
		return new Resolution.Skipped(SkipReason.AMBIGUOUS);
	}

	// 표시 이름 규칙을 지키는 카드만 묶음 키를 갖는다
	private static Optional<Identity> identity(CodefHeldCardCandidate card) {
		return card.cardName()
			.filter(name -> name.codePointCount(0, name.length()) <= CARD_NAME_MAX)
			.filter(name -> {
				String stripped = name.strip();
				int length = stripped.codePointCount(0, stripped.length());
				return length >= 1 && length <= DISPLAY_NAME_MAX;
			})
			.map(name -> new Identity(name, card.issueDate().orElse(null)));
	}

	private static String displayName(String cardName) {
		return cardName.strip();
	}

	/**
	 * 해제하지 않은 기존 CODEF 카드의 대조 정보.
	 * @param userCardId 보유 카드 ID
	 * @param cardName 카드명 원문
	 * @param issueDate 발급일자 원문. 없으면 {@code null}
	 */
	public record ExistingCard(long userCardId, String cardName, String issueDate) {

		/**
		 * 값을 확인한다.
		 * @throws NullPointerException 카드명이 null인 경우
		 */
		public ExistingCard {
			Objects.requireNonNull(cardName, "cardName");
		}

	}

	/** 응답 카드 하나의 처리 방법. */
	public sealed interface Resolution {

		/**
		 * 새 보유 카드를 만든다.
		 * @param cardName 카드명 원문
		 * @param displayName 앞뒤 공백을 뗀 표시 이름
		 * @param issueDate 발급일자 원문. 없으면 {@code null}
		 */
		record Create(String cardName, String displayName, String issueDate) implements Resolution {
		}

		/**
		 * 기존 보유 카드와 같은 카드다.
		 * @param userCardId 기존 보유 카드 ID
		 */
		record Existing(long userCardId) implements Resolution {
		}

		/**
		 * 보유 카드로 만들지 않는다. 이 카드에 붙는 승인·실적은 카드 미연결과 같게 다룬다.
		 * @param reason 건너뛴 이유
		 */
		record Skipped(SkipReason reason) implements Resolution {
		}

	}

	/** 건너뛴 이유. */
	public enum SkipReason {

		/** 카드명이 없다. */
		CARD_NAME_ABSENT,

		/** 표시 이름 규칙(앞뒤 공백을 뗀 1~30자)에 맞지 않는다. */
		DISPLAY_NAME_INVALID,

		/** 같은 카드명·발급일자의 카드가 응답이나 기존 카드에 여럿이라 같은 카드를 정할 수 없다. */
		AMBIGUOUS

	}

	private record Identity(String cardName, String issueDate) {
	}

}
