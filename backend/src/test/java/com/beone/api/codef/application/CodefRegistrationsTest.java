package com.beone.api.codef.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;

import com.beone.api.codef.CodefResultCategory;
import org.junit.jupiter.api.Test;

/**
 * 결과 분류 → 등록 결과 대응표(관리자 판단 P1). 기대값은 docs/domains/codef.md 결과 코드 분류 표와 관리자 판단에서 썼다: 성공만 등록,
 * CF-12108만 카드소지확인, 나머지는 모두 사용 불가이고 어떤 실코드도 로그인 실패가 되지 않는다.
 */
class CodefRegistrationsTest {

	@Test
	void successWithConnectedIdIsRegisteredAndHidesTheIdInItsText() {
		CodefRegistration registration = CodefRegistrations.fromCategory(CodefResultCategory.SUCCESS, "cid-1");

		assertThat(registration).isEqualTo(new CodefRegistration.Registered("cid-1"));
		assertThat(registration.toString()).doesNotContain("cid-1");
	}

	@Test
	void successWithoutConnectedIdIsUnavailable() {
		assertThat(CodefRegistrations.fromCategory(CodefResultCategory.SUCCESS, null))
			.isInstanceOf(CodefRegistration.Unavailable.class);
		assertThat(CodefRegistrations.fromCategory(CodefResultCategory.SUCCESS, " "))
			.isInstanceOf(CodefRegistration.Unavailable.class);
	}

	@Test
	void cardVerificationIsTheOnlyNonSuccessWithItsOwnOutcome() {
		assertThat(CodefRegistrations.fromCategory(CodefResultCategory.CARD_VERIFICATION_NOT_SUPPORTED, "cid-1"))
			.isInstanceOf(CodefRegistration.CardVerificationRequired.class);
	}

	@Test
	void everyOtherCategoryIsUnavailableAndNoneBecomesLoginRejected() {
		EnumSet<CodefResultCategory> others = EnumSet.complementOf(
				EnumSet.of(CodefResultCategory.SUCCESS, CodefResultCategory.CARD_VERIFICATION_NOT_SUPPORTED));
		assertThat(others).contains(CodefResultCategory.ADDITIONAL_AUTHENTICATION_REQUIRED,
				CodefResultCategory.ACCESS_CONFIGURATION_ERROR, CodefResultCategory.UNCLASSIFIED);
		for (CodefResultCategory category : others) {
			assertThat(CodefRegistrations.fromCategory(category, "cid-1")).as(category.name())
				.isInstanceOf(CodefRegistration.Unavailable.class);
		}
	}

}
