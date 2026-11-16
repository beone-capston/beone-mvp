package com.beone.api.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 허용 목록이 rules/merchant-classes.txt 기준인지 확인한다(D-23). 기대 코드는 docs/api/orders.md merchantClass 예시에서 옮겼다.
 */
class MerchantClassCatalogTest {

	private final MerchantClassCatalog catalog = new MerchantClassCatalog();

	@ParameterizedTest
	@ValueSource(strings = { "STARBUCKS_STORE", "CU_STORE", "GS25_STORE", "TELECOM_SKT", "ORDINARY_DOMESTIC", "TAX" })
	void contractExamplesAreAllowed(String code) {
		assertThat(this.catalog.isAllowed(code)).isTrue();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "starbucks_store", " STARBUCKS_STORE", "STARBUCKS_STORE ", "STARBUCKS", "UNKNOWN_CLASS",
			"# 규칙 데이터 49개" })
	void otherValuesAreRejected(String code) {
		assertThat(this.catalog.isAllowed(code)).isFalse();
	}

}
