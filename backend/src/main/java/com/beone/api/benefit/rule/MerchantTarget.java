package com.beone.api.benefit.rule;

import java.util.Objects;
import java.util.Set;

import com.beone.api.benefit.card.MerchantClass;

/**
 * 제외 조건을 적용하기 전 혜택 서비스의 대상 가맹점.
 */
public sealed interface MerchantTarget {

	static AllMerchants allMerchants() {
		return new AllMerchants();
	}

	static SpecificClasses only(Set<MerchantClass> classes) {
		return new SpecificClasses(classes);
	}

	/** 모든 가맹점(예: 09271 기본 적립). */
	record AllMerchants() implements MerchantTarget {
	}

	/** 나열한 업종만. */
	record SpecificClasses(Set<MerchantClass> classes) implements MerchantTarget {

		public SpecificClasses {
			Objects.requireNonNull(classes, "classes");
			if (classes.isEmpty()) {
				throw new IllegalArgumentException("a specific merchant target needs at least one class");
			}
			classes = Set.copyOf(classes);
		}

	}

}
