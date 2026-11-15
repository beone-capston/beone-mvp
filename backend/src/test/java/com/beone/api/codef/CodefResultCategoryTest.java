package com.beone.api.codef;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 결과 코드 분류. 기대값은 CODEF 공식 문서 사본(오류코드 검색 "만료"·"통신"·"토큰", 카드 API 3개 문서의 추가 인증·입력부 특이사항),
 * 성공 코드 별첨(오류코드 페이지의 "CF-00000 성공" 행), 공식 오류 목록 전체 다운로드 원본(Aside 수집, 관찰 2026-11-01T08:16:27Z)에서 먼저
 * 정했다.
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
			"CF-12108, CARD_VERIFICATION_NOT_SUPPORTED" })
	void classifiesDocumentedCodes(String code, CodefResultCategory expected) {
		assertThat(CodefResultCategory.classify(Optional.of(code), false)).isEqualTo(expected);
		// continue2Way는 CF-03002에서만 의미가 있다.
		assertThat(CodefResultCategory.classify(Optional.of(code), true)).isEqualTo(expected);
	}

	@Test
	void cardVerificationIsOnlyCardPossessionCheck() {
		// 공식 목록 249행: CF-12108 "조회에 실패했습니다. 대상기관 사이트에서 카드소지확인이 필요합니다."
		assertThat(CodefResultCategory.classify(Optional.of("CF-12108"), false))
			.isEqualTo(CodefResultCategory.CARD_VERIFICATION_NOT_SUPPORTED);
	}

	@ParameterizedTest
	@ValueSource(strings = { "CF-12401", "CF-12834" })
	void loginParameterAndAccountLockCodesAreUnclassified(String code) {
		// 기대값 변경(#47): #42는 현대카드 카드 비밀번호 사례를 근거로 두 코드를 카드 확인 경로 지원 보류로 분류했다. 공식 목록의 설명은
		// 270행 CF-12401 "로그인 파라미터가 누락되었습니다.", 315행 CF-12834 "계정 잠김 해제가 필요합니다. 확인 후 거래하시기 바랍니다."로
		// 카드 확인 경로에 한정되지 않아, 사용자 결정(2026-11-01)에 따라 미분류로 정정한다.
		assertThat(CodefResultCategory.classify(Optional.of(code), false)).isEqualTo(CodefResultCategory.UNCLASSIFIED);
		assertThat(CodefResultCategory.classify(Optional.of(code), true)).isEqualTo(CodefResultCategory.UNCLASSIFIED);
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
