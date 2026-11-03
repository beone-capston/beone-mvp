package com.beone.api.benefit.rule.loading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.beone.api.benefit.card.MerchantClass;

class MerchantClassAllowlistTest {

	@Test
	void skipsCommentsAndBlankLines() {
		assertThat(MerchantClassAllowlist.parse("# 주석\n\nTAX\r\n  TAXI  \n")).containsExactlyInAnyOrder(MerchantClass.of("TAX"),
				MerchantClass.of("TAXI"));
	}

	@Test
	void rejectsDuplicatesAndEmptyList() {
		assertThatThrownBy(() -> MerchantClassAllowlist.parse("TAX\nTAX\n")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> MerchantClassAllowlist.parse("# 주석만\n")).isInstanceOf(IllegalArgumentException.class);
	}

}
