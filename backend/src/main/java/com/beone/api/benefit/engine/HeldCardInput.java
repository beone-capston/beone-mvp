package com.beone.api.benefit.engine;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

import com.beone.api.benefit.card.CardId;

/**
 * 보유 카드 한 장과 그 달의 상태.
 *
 * <p>가상 포트폴리오 카드는 규칙 카드 자체라 매핑 대상이 아니다(D-15). {@code holdingSource=SYNTHETIC}이면
 * {@code mappingStatus}가 null이고 {@code cardProductId}가 필수다.
 *
 * @param userCardId 엔진이 해석하지 않고 결과에 그대로 돌려주는 ID
 * @param mappingStatus {@code UNMAPPED}·{@code CANDIDATE}·{@code USER_CONFIRMED}·{@code NOT_SUPPORTED}
 * @param cardProductId 규칙 카드 ID. {@code USER_CONFIRMED}면 필수이고 그 밖의 매핑 상태에서는 null
 * @param candidateCardProductId 확인할 수 있는 후보. {@code CANDIDATE}에서만 있을 수 있다
 * @param excludedByUser 사용자가 추천에서 뺀 카드면 참
 * @param holdingSource {@code MANUAL}·{@code CODEF}·{@code SYNTHETIC}
 * @param registeredOn 카드 사용 등록일
 * @param monthlyState 그 달의 상태
 */
public record HeldCardInput(String userCardId, String mappingStatus, String cardProductId,
		String candidateCardProductId, boolean excludedByUser, String holdingSource, StateInput<LocalDate> registeredOn,
		MonthlyStateInput monthlyState) {

	private static final Set<String> MAPPING_STATUSES = Set.of("UNMAPPED", "CANDIDATE", "USER_CONFIRMED", "NOT_SUPPORTED");

	public HeldCardInput {
		EngineChecks.requireToken(userCardId, "userCardId");
		EngineChecks.requireOneOf(Set.of("MANUAL", "CODEF", "SYNTHETIC"), holdingSource, "holdingSource");
		if (cardProductId != null) {
			CardId.of(cardProductId);
		}
		if (candidateCardProductId != null) {
			CardId.of(candidateCardProductId);
		}
		if ("SYNTHETIC".equals(holdingSource)) {
			if (mappingStatus != null) {
				throw new IllegalArgumentException("a synthetic card is not a mapping target");
			}
			if (cardProductId == null) {
				throw new IllegalArgumentException("a synthetic card needs cardProductId");
			}
		}
		else {
			EngineChecks.requireOneOf(MAPPING_STATUSES, mappingStatus, "mappingStatus");
			if ("USER_CONFIRMED".equals(mappingStatus) != (cardProductId != null)) {
				throw new IllegalArgumentException("cardProductId is present exactly when the mapping is USER_CONFIRMED");
			}
		}
		if (candidateCardProductId != null && !"CANDIDATE".equals(mappingStatus)) {
			throw new IllegalArgumentException("candidateCardProductId is only for a CANDIDATE mapping");
		}
		Objects.requireNonNull(registeredOn, "registeredOn");
		Objects.requireNonNull(monthlyState, "monthlyState");
	}

	/** 규칙 카드가 정해져 계산 대상이 될 수 있으면 참. */
	boolean hasRuleCard() {
		return cardProductId != null;
	}

}
