package com.beone.api.benefit.primitive;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.rule.BenefitService;
import com.beone.api.benefit.rule.LimitBucket;
import com.beone.api.benefit.rule.LimitTier;
import com.beone.api.benefit.rule.ServiceTier;

/**
 * 확인된 전월 실적으로 실적 구간을 고른다. 구간의 최소 실적 "이상"이면 그 구간에 든다({@link ServiceTier}, {@link LimitTier}).
 *
 * <p>
 * 실적 값의 출처·기준 기간이 이 카드의 실적 기간에 맞는지는 판정하지 않는다(D-04 미결). 모르는 실적을 0원으로 넣지 않는다.
 * 가장 낮은 구간에도 못 미치면 빈 값이다. 입력 실적만으로 어느 구간에도 들지 않는다는 뜻이며, 신규 카드 유예(D-03)의 적용 여부는
 * 호출자가 판단한다.
 * 취소가 승인보다 많아 생기는 음수 실적의 처리 근거가 없으므로 음수 실적은 거부한다.
 */
public final class PerformanceTiers {

	private PerformanceTiers() {
	}

	/**
	 * 서비스의 계산 방식 구간. 고른 구간의 계산식이 미확정({@code RuleTerm.Unresolved})일 수 있으므로 호출자가 확인한다.
	 */
	public static Optional<ServiceTier> serviceTier(BenefitService service, Won performance) {
		Objects.requireNonNull(service, "service");
		return highestReached(service.tiers(), ServiceTier::minimumPerformance, performance);
	}

	/**
	 * 한도 버킷의 월 한도 구간.
	 */
	public static Optional<LimitTier> limitTier(LimitBucket bucket, Won performance) {
		Objects.requireNonNull(bucket, "bucket");
		return highestReached(bucket.tiers(), LimitTier::minimumPerformance, performance);
	}

	// 구간은 모델 생성 시 최소 실적 엄격 오름차순으로 검증되어 있다.
	private static <T> Optional<T> highestReached(List<T> tiers, Function<T, Won> minimum, Won performance) {
		Objects.requireNonNull(performance, "performance");
		if (performance.isNegative()) {
			throw new IllegalArgumentException("negative performance has no confirmed tier rule: " + performance.value());
		}
		T reached = null;
		for (T tier : tiers) {
			if (performance.compareTo(minimum.apply(tier)) < 0) {
				break;
			}
			reached = tier;
		}
		return Optional.ofNullable(reached);
	}

}
