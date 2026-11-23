package com.beone.api.recommendation.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 추천 계산 응답 본문(docs/api/recommendations.md). 계산할 때 한 번 만들어 그대로 저장하고, 조회는 다시 계산하지 않고 이 값을 돌려준다.
 *
 * <p>엔진 출력의 값을 옮기기만 한다. 금액 {@code null}·{@code NEEDS_CONFIRMATION}을 0으로 바꾸지 않는다. 시점은 {@code +09:00}이 붙은
 * 서울 시각 문자열이다.
 *
 * @param recommendationId 추천 ID
 * @param orderId 주문 ID
 * @param calculatedAt 계산 시각
 * @param decisionDate 판정일(주문일, D-23)
 * @param selectionMode {@code AUTO_RECOMMEND}·{@code FIXED_REPRESENTATIVE}
 * @param outcome {@code SELECTED}·{@code NOT_DETERMINED}
 * @param conditional 조건부 추천
 * @param selected 고른 카드. {@code NOT_DETERMINED}면 {@code null}
 * @param comparedCards 비교한 카드
 * @param excludedCards 제외한 카드
 * @param fixedCardComparison 대표 카드 고정일 때의 비교. 자동 추천이면 {@code null}
 * @param inputSnapshot 계산에 쓴 입력. {@code NOT_DETERMINED}면 {@code null}(계약 예시, 결정 27 B2-6)
 * @param notes 안내 문구
 */
public record RecommendationDocument(String recommendationId, String orderId, String calculatedAt,
		LocalDate decisionDate, String selectionMode, String outcome, boolean conditional, Selected selected,
		List<ComparedCard> comparedCards, List<ExcludedCard> excludedCards, FixedCardComparison fixedCardComparison,
		InputSnapshot inputSnapshot, List<String> notes) {

	/**
	 * 고른 카드.
	 *
	 * @param userCardId 보유 카드 ID
	 * @param cardProductId 규칙 카드 ID
	 * @param basis 고른 근거
	 */
	public record Selected(String userCardId, String cardProductId, Basis basis) {
	}

	/**
	 * 고른 근거(D-12). 비교 카드가 1장이면 {@code decidedBy}·{@code gapToRunnerUpWon}이 {@code null}이다.
	 *
	 * @param decidedBy {@code SelectionBasis} 이름
	 * @param arbitraryTieBreak 임의 규칙으로 정했으면 참
	 * @param tiedUserCardIds 동률 카드
	 * @param gapToRunnerUpWon 차선 카드와의 차액
	 */
	public record Basis(String decidedBy, boolean arbitraryTieBreak, List<String> tiedUserCardIds,
			Long gapToRunnerUpWon) {
	}

	/**
	 * 비교한 카드.
	 *
	 * @param userCardId 보유 카드 ID
	 * @param cardProductId 규칙 카드 ID
	 * @param displayName 보유 카드 표시 이름
	 * @param mapping 매핑. 가상 포트폴리오 카드는 매핑 대상이 아니라 {@code null}(결정 27 B2-7)
	 * @param applicationStatus 카드 상태
	 * @param appliedRule 계산에 쓴 규칙
	 * @param currentOrderBenefit 현재 주문 혜택
	 * @param monthlyTotalBenefit 월간 합계(1차는 현재 주문 혜택과 같음)
	 * @param services 서비스 결과
	 * @param noBenefitReason 확정 0원 사유
	 * @param policyNotes 약관 근거 없는 사용자 결정 표시
	 * @param pendingReasons 금액을 확정하지 못한 사유
	 */
	public record ComparedCard(String userCardId, String cardProductId, String displayName, Mapping mapping,
			String applicationStatus, AppliedRule appliedRule, Amount currentOrderBenefit, Amount monthlyTotalBenefit,
			List<Service> services, String noBenefitReason, List<String> policyNotes,
			List<PendingReason> pendingReasons) {
	}

	/**
	 * 매핑 표시(D-15).
	 *
	 * @param status 매핑 상태
	 * @param note 사용자 확인 매핑 표시
	 */
	public record Mapping(String status, String note) {
	}

	/**
	 * 규칙 버전·검증 상태·공식 근거.
	 *
	 * @param ruleVersion 규칙 버전
	 * @param verificationStatus 검증 상태
	 * @param officialSources 공식 근거
	 */
	public record AppliedRule(String ruleVersion, String verificationStatus, List<OfficialSource> officialSources) {
	}

	/**
	 * 공식 근거 하나.
	 *
	 * @param uri 문서 주소
	 * @param locator 문서 안 위치
	 */
	public record OfficialSource(String uri, String locator) {
	}

	/**
	 * 금액과 상태. 미확정이면 {@code amountWon}이 {@code null}이다.
	 *
	 * @param amountWon 원 단위 금액
	 * @param status {@code CONFIRMED}·{@code NEEDS_CONFIRMATION}
	 */
	public record Amount(Long amountWon, String status) {
	}

	/**
	 * 서비스 결과.
	 *
	 * @param serviceId 서비스
	 * @param applicationStatus 적용 상태
	 * @param candidateWon 단독 후보 금액
	 * @param contribution 기여액
	 * @param adoption 채택 여부
	 * @param limits 체인의 한도 버킷
	 * @param limitReason 한도 판정 사유
	 * @param usageBasis 한도가 있으면 {@code USER_ENTERED}
	 * @param mayDifferFromActual 한도가 있으면 참
	 * @param usageNote 한도 표시 문구
	 * @param fractionalWonTruncated 원 미만을 버렸으면 참
	 * @param fractionalWonBasis 버렸을 때의 근거
	 * @param unevaluatedExclusions 판정하지 못한 제외 조건(D-20)
	 */
	public record Service(String serviceId, String applicationStatus, Long candidateWon, Amount contribution,
			String adoption, List<Limit> limits, String limitReason, String usageBasis, boolean mayDifferFromActual,
			String usageNote, boolean fractionalWonTruncated, FractionalWonBasis fractionalWonBasis,
			List<String> unevaluatedExclusions) {
	}

	/**
	 * 한도 버킷 한 줄.
	 *
	 * @param limitBucketId 버킷
	 * @param parentLimitBucketId 상위 버킷
	 * @param limitWon 그 실적 구간의 한도
	 * @param usedBeforeWon 입력 사용액과 출처
	 * @param remainingAfterWon 이 주문 뒤 남은 한도
	 */
	public record Limit(String limitBucketId, String parentLimitBucketId, Long limitWon, UsedBefore usedBeforeWon,
			Long remainingAfterWon) {
	}

	/**
	 * 입력 사용액.
	 *
	 * @param value 사용액. 모르면 {@code null}
	 * @param source 출처
	 */
	public record UsedBefore(Long value, String source) {
	}

	/**
	 * 원 미만 처리 근거.
	 *
	 * @param kind 근거 종류
	 * @param decision 결정
	 */
	public record FractionalWonBasis(String kind, String decision) {
	}

	/**
	 * 금액을 확정하지 못한 사유.
	 *
	 * @param kind {@code PendingReason.Kind} 이름
	 * @param serviceIds 관련 서비스
	 * @param detail 설명
	 */
	public record PendingReason(String kind, List<String> serviceIds, String detail) {
	}

	/**
	 * 제외한 카드.
	 *
	 * @param userCardId 보유 카드 ID
	 * @param cardProductId 규칙 카드 ID
	 * @param reason 제외 사유
	 * @param detail 설명
	 * @param candidateCardProductId 확인할 수 있는 후보
	 * @param monthlyTotalBenefit 항상 미확정
	 * @param applicationStatus 계산한 카드의 상태
	 * @param appliedRule 규칙으로 계산했지만 금액이 미확정인 카드의 규칙
	 * @param pendingReasons 금액을 확정하지 못한 사유
	 */
	public record ExcludedCard(String userCardId, String cardProductId, String reason, String detail,
			String candidateCardProductId, Amount monthlyTotalBenefit, String applicationStatus, AppliedRule appliedRule,
			List<PendingReason> pendingReasons) {
	}

	/**
	 * 대표 카드 고정 비교.
	 *
	 * @param representativeUserCardId 대표 카드
	 * @param optimalUserCardId 월간 합계 기준 고른 카드. 비교 카드가 없으면 {@code null}
	 * @param differenceWon 차이. 대표 카드 금액이 미확정이면 {@code null}
	 */
	public record FixedCardComparison(String representativeUserCardId, String optimalUserCardId, Long differenceWon) {
	}

	/**
	 * 계산에 쓴 입력.
	 *
	 * @param order 주문
	 * @param representativeUserCardId 대표 카드
	 * @param monthlyStateSnapshotAt 보유 카드별 월간 상태 스냅샷 시각. 입력이 없는 카드는 키가 없다
	 */
	public record InputSnapshot(OrderSnapshot order, String representativeUserCardId,
			Map<String, String> monthlyStateSnapshotAt) {
	}

	/**
	 * 주문 입력.
	 *
	 * @param amountWon 결제 금액
	 * @param orderDate 주문일
	 * @param merchantClass 업종
	 * @param paymentMethod 결제 방식
	 */
	public record OrderSnapshot(long amountWon, LocalDate orderDate, String merchantClass, String paymentMethod) {
	}

}
