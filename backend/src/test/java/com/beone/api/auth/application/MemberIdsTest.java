package com.beone.api.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * 회원 ID 형식을 확인한다. 기대값: {@code usr-} + 소문자 16진수 32자, 총 36자(관리자 판단 결정 28).
 */
class MemberIdsTest {

	@Test
	void idIsPrefixPlus32LowercaseHexAndRandom() {
		Set<String> ids = new HashSet<>();
		for (int i = 0; i < 100; i++) {
			String id = MemberIds.newId();
			assertThat(id).hasSize(36).matches("usr-[0-9a-f]{32}");
			ids.add(id);
		}
		assertThat(ids).hasSize(100);
	}

}
