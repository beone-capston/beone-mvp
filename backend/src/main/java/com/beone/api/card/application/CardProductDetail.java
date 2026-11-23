package com.beone.api.card.application;

import java.time.LocalDate;
import java.util.List;

import com.beone.api.benefit.rule.BenefitKind;
import com.beone.api.benefit.rule.CancellationAttribution;
import com.beone.api.benefit.rule.LimitDeductionOrder;
import com.beone.api.benefit.rule.LimitPeriod;
import com.beone.api.benefit.rule.PerformanceWindow;
import com.beone.api.benefit.rule.VerificationStatus;

/**
 * 카드 상품 상세·규칙 요약(docs/api/products.md {@code GET /api/v1/card-products/{cardProductId}}).
 *
 * <p>판정일에 유효한 {@code VERIFIED} 규칙이 없으면 {@code rule}은 {@code null}이고 {@code services}·
 * {@code limitBuckets}는 빈 배열이다. 이유는 {@code ruleStatus}로 준다.
 *
 * @param cardProductId 선행 0을 보존하는 공식 상품 식별자
 * @param cardName 상품 이름
 * @param asOf 규칙 유효성 판단 날짜
 * @param ruleStatus 판정일 기준 규칙 상태
 * @param rule 판정일에 유효한 {@code VERIFIED} 규칙. 없으면 {@code null}
 * @param services 규칙의 서비스. 규칙 데이터 순서
 * @param limitBuckets 규칙의 한도 묶음. 규칙 데이터 순서
 */
public record CardProductDetail(String cardProductId, String cardName, LocalDate asOf, RuleStatus ruleStatus,
		Rule rule, List<Service> services, List<LimitBucket> limitBuckets) {

	public CardProductDetail {
		services = List.copyOf(services);
		limitBuckets = List.copyOf(limitBuckets);
	}

	/**
	 * 선택된 규칙의 요약. 미확정 조건은 {@code null}이며 확인 필요를 뜻한다.
	 *
	 * @param ruleVersion 규칙 버전
	 * @param verificationStatus 검증 상태. 선택된 규칙은 항상 {@code VERIFIED}
	 * @param validFrom 적용 시작일
	 * @param validTo 적용 종료일. 발표된 종료일이 없으면 {@code null}
	 * @param performanceWindow 전월 실적 산정 기간. 미확정이면 {@code null}
	 * @param cancellationAttribution 취소 귀속 모델. 미확정이면 {@code null}(D-17)
	 * @param limitDeductionOrder 한도 차감 순서. 미확정이면 {@code null}(D-18). 단일 주문 계산에는 쓰지 않는다
	 */
	public record Rule(String ruleVersion, VerificationStatus verificationStatus, LocalDate validFrom,
			LocalDate validTo, PerformanceWindow performanceWindow, CancellationAttribution cancellationAttribution,
			LimitDeductionOrder limitDeductionOrder) {
	}

	/**
	 * 혜택 서비스 하나.
	 *
	 * @param serviceId 규칙 안의 서비스 식별자
	 * @param name 규칙의 표시 이름. 없으면 서비스 식별자 그대로(결정 27 A1-4)
	 * @param benefitKind 포인트 적립 또는 청구 할인
	 * @param supportStatus 계산 지원 범위(D-18 K1)
	 * @param limitBucketIds 서비스가 직접 차감하는 한도 묶음. 상위 한도는 {@code parentLimitBucketId}로 따라간다
	 * @param requiresSelection 선택형이면 참
	 */
	public record Service(String serviceId, String name, BenefitKind benefitKind, SupportStatus supportStatus,
			List<String> limitBucketIds, boolean requiresSelection) {

		public Service {
			limitBucketIds = List.copyOf(limitBucketIds);
		}

	}

	/**
	 * 월 혜택 한도 묶음 하나.
	 *
	 * @param limitBucketId 한도 묶음 식별자
	 * @param tiers 전월 실적 구간별 월 한도. 최소 실적 오름차순
	 * @param parentLimitBucketId 통합 한도의 상위 묶음. 없으면 {@code null}
	 * @param period 한도 기간(규칙 정책 값). 미확정이면 {@code null}
	 * @param sharedByServiceIds 이 묶음을 직접 적은 서비스(결정 27 A1-6)
	 */
	public record LimitBucket(String limitBucketId, List<Tier> tiers, String parentLimitBucketId, LimitPeriod period,
			List<String> sharedByServiceIds) {

		public LimitBucket {
			tiers = List.copyOf(tiers);
			sharedByServiceIds = List.copyOf(sharedByServiceIds);
		}

	}

	/**
	 * 전월 실적 구간 하나(코드 {@code LimitTier}).
	 *
	 * @param minimumPerformanceWon 이 구간의 최소 전월 실적(원)
	 * @param monthlyCapWon 월 한도액(원)
	 */
	public record Tier(long minimumPerformanceWon, long monthlyCapWon) {
	}

}
