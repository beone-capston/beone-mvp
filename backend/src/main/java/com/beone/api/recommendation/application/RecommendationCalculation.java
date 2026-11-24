package com.beone.api.recommendation.application;

import java.util.List;
import java.util.Objects;

/**
 * 모의 승인 직전 재계산의 결과. 추천 응답 본문과, 엔진이 낸 카드별 한도 버킷 차감액을 함께 담는다(D-18 K1, D-22).
 *
 * <p>모의 결제(payment)가 승인 카드를 정한 뒤 그 카드의 차감액만 원장에 저장한다. 차감액을 모르면 {@code deductionWon}이
 * {@code null}이며 0으로 바꾸지 않는다.
 *
 * @param document 저장한 추천 응답 본문(새 추천 ID)
 * @param bucketDeductions 계산에 넘긴 모든 카드의 버킷별 차감액
 */
public record RecommendationCalculation(RecommendationDocument document, List<BucketDeduction> bucketDeductions) {

	public RecommendationCalculation {
		Objects.requireNonNull(document, "document");
		bucketDeductions = List.copyOf(bucketDeductions);
	}

	/**
	 * 한 카드의 차감액만 계산 순서대로 돌려준다.
	 * @param userCardId 보유 카드 외부 ID
	 * @return 그 카드의 차감액 목록
	 */
	public List<BucketDeduction> deductionsOf(String userCardId) {
		return this.bucketDeductions.stream().filter(deduction -> deduction.userCardId().equals(userCardId)).toList();
	}

	/**
	 * 그 카드로 승인했을 때 한도 버킷 하나에 더해지는 사용액(엔진 {@code BucketUsageEffect}를 옮긴 값).
	 *
	 * @param userCardId 보유 카드 ID
	 * @param limitBucketId 한도 버킷
	 * @param deductionWon 차감액. 모르면 {@code null}
	 */
	public record BucketDeduction(String userCardId, String limitBucketId, Long deductionWon) {

		public BucketDeduction {
			Objects.requireNonNull(userCardId, "userCardId");
			Objects.requireNonNull(limitBucketId, "limitBucketId");
		}

	}

}
