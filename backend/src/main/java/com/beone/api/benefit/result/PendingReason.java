package com.beone.api.benefit.result;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import com.beone.api.benefit.card.ServiceId;

/**
 * 단일 주문 카드 금액을 확정하지 못한 사유 하나(#35, D-13). 이 사유가 하나라도 있으면 카드 금액은 {@code 확인 필요}다.
 *
 * <p>
 * 생성 시 서비스 수(쌍 단위 사유는 2개, 합계 초과는 2개 이상, 나머지는 1개), 서비스 ID 중복과 오름차순({@code String.compareTo}), 공백 설명을 확인한다.
 *
 * @param kind 사유 종류
 * @param services 사유에 해당하는 서비스. 서비스 ID 오름차순이다
 * @param detail 사람이 읽는 설명. 앞 단계가 준 사유 원문을 바꾸지 않고 포함한다
 */
public record PendingReason(Kind kind, List<ServiceId> services, String detail) {

	/** 서비스 ID를 {@code String.compareTo} 순서(대소문자 구분, UTF-16 코드 단위)로 비교한다. 숫자·로캘 정렬을 하지 않는다. */
	public static final Comparator<ServiceId> SERVICE_ORDER = Comparator.comparing(ServiceId::value);

	/** 종류 선언 순서, 같은 종류면 서비스 ID 목록의 사전식 순서. 결정기가 사유를 내놓는 순서다. */
	public static final Comparator<PendingReason> ORDER = Comparator.comparing(PendingReason::kind)
		.thenComparing(PendingReason::services, PendingReason::compareServices);

	/**
	 * 사유 종류. 선언 순서가 사유 목록의 정렬 순서다.
	 */
	public enum Kind {

		/** 후보 금액을 확정하지 못한 서비스가 있음({@link ServiceNeedingConfirmation}). 서비스 1개. */
		NEEDS_CONFIRMATION,

		/** 이번 기능이 판정하지 않는 서비스(한도·선택형)가 있음({@link UnsupportedService}). 서비스 1개. */
		UNSUPPORTED,

		/** 취소 귀속·할인 매출 실적 제외 조건이 이 서비스의 실적 충족·구간·후보 금액을 바꿀 수 있거나 영향이 불명확함. 서비스 1개. */
		PERFORMANCE_POLICY,

		/** 양수 후보 두 서비스의 중복 관계가 미확정이거나 한쪽에만 적혀 있음. 서비스 쌍. */
		STACKING_UNRESOLVED,

		/** 양수 후보 두 서비스의 중복 허용이 확정됐지만 그 주문이 합산 예외(D-13 #35 8번) 조건을 만족하지 않음. 서비스 쌍. */
		STACKING_CONFIRMED,

		/** 합산 조건을 만족한 서비스의 기여액 합계가 결제 금액을 넘음(D-13 #35 8번). 합산 대상 전체, 서비스 2개 이상. */
		STACKING_EXCEEDS_PAYMENT;

		/**
		 * 이 종류의 사유가 가리킬 수 있는 서비스 수인지 확인한다.
		 *
		 * @param count 서비스 수
		 * @return 허용하는 수이면 참
		 */
		boolean allowsServiceCount(int count) {
			return switch (this) {
				case NEEDS_CONFIRMATION, UNSUPPORTED, PERFORMANCE_POLICY -> count == 1;
				case STACKING_UNRESOLVED, STACKING_CONFIRMED -> count == 2;
				case STACKING_EXCEEDS_PAYMENT -> count >= 2;
			};
		}

		private String serviceCountText() {
			return switch (this) {
				case NEEDS_CONFIRMATION, UNSUPPORTED, PERFORMANCE_POLICY -> "exactly 1";
				case STACKING_UNRESOLVED, STACKING_CONFIRMED -> "exactly 2";
				case STACKING_EXCEEDS_PAYMENT -> "at least 2";
			};
		}

	}

	/**
	 * 서비스 수·중복·순서와 설명을 확인한다.
	 *
	 * @throws IllegalArgumentException 서비스 수·중복·순서가 맞지 않거나 설명이 비어 있는 경우
	 */
	public PendingReason {
		Objects.requireNonNull(kind, "kind");
		services = List.copyOf(services);
		if (!kind.allowsServiceCount(services.size())) {
			throw new IllegalArgumentException(kind + " needs " + kind.serviceCountText() + " service(s): " + services);
		}
		if (new HashSet<>(services).size() != services.size()) {
			throw new IllegalArgumentException(kind + " lists a service twice: " + services);
		}
		for (int i = 1; i < services.size(); i++) {
			if (SERVICE_ORDER.compare(services.get(i - 1), services.get(i)) > 0) {
				throw new IllegalArgumentException(kind + " services must be in ascending id order: " + services);
			}
		}
		if (detail == null || detail.isBlank()) {
			throw new IllegalArgumentException("detail is required for " + kind + " " + services);
		}
	}

	/**
	 * 서비스 ID 목록을 앞에서부터 {@link #SERVICE_ORDER}로 비교하고, 앞부분이 모두 같으면 짧은 목록을 앞에 둔다. {@link #ORDER}가 같은
	 * 종류의 사유를 정렬할 때 쓴다.
	 *
	 * @param left 왼쪽 서비스 ID 목록
	 * @param right 오른쪽 서비스 ID 목록
	 * @return 왼쪽이 앞이면 음수, 같으면 0, 뒤면 양수
	 */
	private static int compareServices(List<ServiceId> left, List<ServiceId> right) {
		for (int i = 0; i < Math.min(left.size(), right.size()); i++) {
			int compared = SERVICE_ORDER.compare(left.get(i), right.get(i));
			if (compared != 0) {
				return compared;
			}
		}
		return Integer.compare(left.size(), right.size());
	}

}
