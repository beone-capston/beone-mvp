package com.beone.api.codef;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 결과 코드 분류. 기대값은 CODEF 공식 문서 사본(오류코드 검색 "만료"·"통신"·"토큰", 카드 API 3개 문서의 추가 인증·입력부 특이사항)과
 * 성공 코드 별첨(오류코드 페이지의 "CF-00000 성공" 행)에서 먼저 정했다.
 */
class CodefResultCategoryTest {

	@ParameterizedTest
	@CsvSource({ "CF-00000, SUCCESS",
			"CF-09990, OAUTH_TOKEN_ERROR", "CF-09991, OAUTH_TOKEN_ERROR", "CF-09992, OAUTH_TOKEN_ERROR",
			"CF-09993, OAUTH_TOKEN_ERROR", "CF-09994, OAUTH_TOKEN_ERROR", "CF-00026, OAUTH_TOKEN_ERROR",
			"CF-12806, CERTIFICATE_EXPIRED_OR_REVOKED", "CF-12808, CERTIFICATE_EXPIRED_OR_REVOKED",
			"CF-09980, COMMUNICATION_FAILURE", "CF-09981, COMMUNICATION_FAILURE", "CF-12200, COMMUNICATION_FAILURE",
			"CF-12202, COMMUNICATION_FAILURE", "CF-12203, COMMUNICATION_FAILURE",
			"CF-00405, REQUEST_FORMAT_ERROR", "CF-11301, REQUEST_FORMAT_ERROR", "CF-11302, REQUEST_FORMAT_ERROR",
			"CF-11303, REQUEST_FORMAT_ERROR", "CF-11304, REQUEST_FORMAT_ERROR", "CF-11305, REQUEST_FORMAT_ERROR",
			"CF-11306, REQUEST_FORMAT_ERROR",
			"CF-00004, ACCESS_CONFIGURATION_ERROR", "CF-00005, ACCESS_CONFIGURATION_ERROR",
			"CF-00013, ACCESS_CONFIGURATION_ERROR", "CF-00017, ACCESS_CONFIGURATION_ERROR",
			"CF-04015, ACCESS_CONFIGURATION_ERROR", "CF-04038, ACCESS_CONFIGURATION_ERROR",
			"CF-12108, CARD_VERIFICATION_NOT_SUPPORTED", "CF-12401, CARD_VERIFICATION_NOT_SUPPORTED",
			"CF-12834, CARD_VERIFICATION_NOT_SUPPORTED" })
	void classifiesDocumentedCodes(String code, CodefResultCategory expected) {
		assertThat(CodefResultCategory.classify(Optional.of(code), false)).isEqualTo(expected);
		// continue2Way는 CF-03002에서만 의미가 있다.
		assertThat(CodefResultCategory.classify(Optional.of(code), true)).isEqualTo(expected);
	}

	@Test
	void additionalAuthenticationNeedsContinue2WayTrue() {
		assertThat(CodefResultCategory.classify(Optional.of("CF-03002"), true))
			.isEqualTo(CodefResultCategory.ADDITIONAL_AUTHENTICATION_REQUIRED);
		// 문서는 continue2Way 값이 있고 true인지로 판단하라고 하며, 그 밖의 CF-03002 의미는 사본에 없다.
		assertThat(CodefResultCategory.classify(Optional.of("CF-03002"), false))
			.isEqualTo(CodefResultCategory.UNCLASSIFIED);
	}

	@Test
	void successIsOnlyTheExactCode() {
		assertThat(CodefResultCategory.classify(Optional.of("CF-00000"), false)).isEqualTo(CodefResultCategory.SUCCESS);
	}

	@ParameterizedTest
	@ValueSource(strings = { " CF-00000", "CF-00000 ", "cf-00000", "CF-0000", "CF-000000", "", "성공" })
	void nearSuccessCodesAreUnclassified(String code) {
		// 공백·대소문자·자릿수가 다르거나 메시지 문구이면 성공으로 추정하지 않는다.
		assertThat(CodefResultCategory.classify(Optional.of(code), false)).isEqualTo(CodefResultCategory.UNCLASSIFIED);
	}

	@ParameterizedTest
	@ValueSource(strings = { "CF-13316", "CF-12040", "CF-3101", "cf-09992", " CF-09992", "UNKNOWN" })
	void codesOutsideTheTableAreUnclassified(String code) {
		// CF-13316(만료된 발급번호)·CF-12040(우리카드 카드목록 없음)은 사본에 있지만 이번 분류 표 밖이다.
		assertThat(CodefResultCategory.classify(Optional.of(code), false)).isEqualTo(CodefResultCategory.UNCLASSIFIED);
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void missingCodeIsUnclassified(boolean continue2Way) {
		assertThat(CodefResultCategory.classify(Optional.empty(), continue2Way))
			.isEqualTo(CodefResultCategory.UNCLASSIFIED);
	}

}
