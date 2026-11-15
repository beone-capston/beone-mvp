package com.beone.api.codef;

import java.util.Map;
import java.util.Optional;

/**
 * CODEF 공통 결과 코드({@code result.code})의 분류(#41).
 *
 * <p>
 * 공식 문서 사본(Aside의 브라우저로 공식 공개 문서를 렌더링해 수집, 2026-10-31T12:00:47Z)과 성공 코드 별첨(Aside의 브라우저로 공식
 * 오류코드 페이지에서 {@code CF-00000}을 검색해 확인한 행, 관찰 2026-11-01T07:10:00Z)에 설명이 있는 코드만 분류한다. 그 밖의 코드와 코드
 * 누락은 모두 {@link #UNCLASSIFIED}다. 분류는 결과 코드만의 판단이며 {@code data}를 쓸 수 있다는 뜻이 아니다. 어떤 분류든 CODEF 결과를
 * 쓰지 못하는 경우 수동 입력 경로는 그대로 유지한다(호출자 책임).
 */
public enum CodefResultCategory {

	/**
	 * 성공: {@code CF-00000}과 정확히 일치할 때만(별첨의 공식 행 "CF-00000 성공"). 메시지 문자열·HTTP 상태로 추정하지 않는다. 결과 코드의
	 * 분류일 뿐이며 {@code data}의 형식·값이 쓸 수 있는 상태라는 뜻도, 실제 응답 검증이나 상태값·상품 매핑의 근거도 아니다.
	 */
	SUCCESS,

	/** 추가 인증 필요. {@code CF-03002}이면서 {@code data.continue2Way}가 {@code true}일 때만(카드 API 3개 문서의 추가인증 출력부). */
	ADDITIONAL_AUTHENTICATION_REQUIRED,

	/** OAuth 토큰 오류: {@code CF-09990}~{@code CF-09994}, {@code CF-00026}(오류코드 검색 "토큰"). */
	OAUTH_TOKEN_ERROR,

	/** 인증서 만료·폐기: {@code CF-12806}, {@code CF-12808}(오류코드 검색 "만료"). */
	CERTIFICATE_EXPIRED_OR_REVOKED,

	/** 통신 실패·시간 초과·차단: {@code CF-09980}, {@code CF-09981}, {@code CF-12200}, {@code CF-12202}, {@code CF-12203}(오류코드 검색 "통신"). */
	COMMUNICATION_FAILURE,

	/** 요청 형식 오류: {@code CF-00405}, {@code CF-11301}~{@code CF-11306}(오류코드 검색 "통신"). */
	REQUEST_FORMAT_ERROR,

	/**
	 * 요청 도메인·허용 IP·연결 계정 오류: {@code CF-00004}, {@code CF-00005}, {@code CF-00013}, {@code CF-00017}, {@code CF-04015},
	 * {@code CF-04038}(오류코드 검색 "토큰").
	 */
	ACCESS_CONFIGURATION_ERROR,

	/**
	 * 카드소지확인 경로: {@code CF-12108}(공식 오류 목록 249행 "조회에 실패했습니다. 대상기관 사이트에서 카드소지확인이 필요합니다."). 카드소지확인에는
	 * 카드번호·카드 비밀번호 입력이 필요하고 BeONE은 이 입력을 수집하지 않으므로 지원 보류다.
	 */
	CARD_VERIFICATION_NOT_SUPPORTED,

	/**
	 * 위 분류에 넣지 않은 모든 코드(공식 목록에 있지만 분류하지 않은 코드 포함, 예: 270행 {@code CF-12401} "로그인 파라미터가 누락되었습니다.", 315행
	 * {@code CF-12834} "계정 잠김 해제가 필요합니다."), 코드 누락, {@code continue2Way}가 {@code true}가 아닌 {@code CF-03002}. 의미를 추정하지
	 * 않고 확인 필요로 둔다.
	 */
	UNCLASSIFIED;

	private static final Map<String, CodefResultCategory> BY_CODE = Map.ofEntries(Map.entry("CF-00000", SUCCESS),
			Map.entry("CF-09990", OAUTH_TOKEN_ERROR), Map.entry("CF-09991", OAUTH_TOKEN_ERROR),
			Map.entry("CF-09992", OAUTH_TOKEN_ERROR), Map.entry("CF-09993", OAUTH_TOKEN_ERROR),
			Map.entry("CF-09994", OAUTH_TOKEN_ERROR), Map.entry("CF-00026", OAUTH_TOKEN_ERROR),
			Map.entry("CF-12806", CERTIFICATE_EXPIRED_OR_REVOKED), Map.entry("CF-12808", CERTIFICATE_EXPIRED_OR_REVOKED),
			Map.entry("CF-09980", COMMUNICATION_FAILURE), Map.entry("CF-09981", COMMUNICATION_FAILURE),
			Map.entry("CF-12200", COMMUNICATION_FAILURE), Map.entry("CF-12202", COMMUNICATION_FAILURE),
			Map.entry("CF-12203", COMMUNICATION_FAILURE),
			Map.entry("CF-00405", REQUEST_FORMAT_ERROR), Map.entry("CF-11301", REQUEST_FORMAT_ERROR),
			Map.entry("CF-11302", REQUEST_FORMAT_ERROR), Map.entry("CF-11303", REQUEST_FORMAT_ERROR),
			Map.entry("CF-11304", REQUEST_FORMAT_ERROR), Map.entry("CF-11305", REQUEST_FORMAT_ERROR),
			Map.entry("CF-11306", REQUEST_FORMAT_ERROR),
			Map.entry("CF-00004", ACCESS_CONFIGURATION_ERROR), Map.entry("CF-00005", ACCESS_CONFIGURATION_ERROR),
			Map.entry("CF-00013", ACCESS_CONFIGURATION_ERROR), Map.entry("CF-00017", ACCESS_CONFIGURATION_ERROR),
			Map.entry("CF-04015", ACCESS_CONFIGURATION_ERROR), Map.entry("CF-04038", ACCESS_CONFIGURATION_ERROR),
			Map.entry("CF-12108", CARD_VERIFICATION_NOT_SUPPORTED));

	private static final String ADDITIONAL_AUTHENTICATION_CODE = "CF-03002";

	/**
	 * 결과 코드를 분류한다. 코드 문자열은 정확히 일치할 때만 분류하며 대소문자·공백을 고치지 않는다.
	 *
	 * @param code 결과 코드. 없으면 빈 값
	 * @param continue2Way {@code data.continue2Way}가 불리언 {@code true}인지
	 * @return 분류. 분류 표에 없는 코드는 {@link #UNCLASSIFIED}
	 */
	public static CodefResultCategory classify(Optional<String> code, boolean continue2Way) {
		if (code.isEmpty()) {
			return UNCLASSIFIED;
		}
		if (code.get().equals(ADDITIONAL_AUTHENTICATION_CODE)) {
			// 문서는 continue2Way 값이 있고 true인지로 추가 인증을 판단하라고 한다. 그 밖의 CF-03002 의미는 사본에 없다.
			return continue2Way ? ADDITIONAL_AUTHENTICATION_REQUIRED : UNCLASSIFIED;
		}
		return BY_CODE.getOrDefault(code.get(), UNCLASSIFIED);
	}

}
