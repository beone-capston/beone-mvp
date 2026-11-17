package com.beone.api.codef.application;

import com.beone.api.codef.CodefResultCategory;

/**
 * CODEF 결과 분류 → 계정 등록 결과 대응표(관리자 판단 P1, docs/domains/codef.md 결과 코드 분류).
 *
 * <p>근거 없는 코드를 로그인 실패나 재연결로 묶지 않는다. 결과 분류에는 로그인 실패 분류가 없으므로(#47에서 {@code CF-12401}·
 * {@code CF-12834}를 미분류로 정정) 어떤 실코드도 {@link CodefRegistration.LoginRejected}가 되지 않는다. 추가 인증(2-way)은 범위
 * 밖이라 쓸 수 없음으로 둔다.
 */
public final class CodefRegistrations {

	private CodefRegistrations() {
	}

	/**
	 * 결과 분류를 등록 결과로 바꾼다.
	 * @param category 결과 코드 분류
	 * @param connectedId 성공 응답의 connectedId. 성공인데 비어 있으면 쓸 수 없음으로 본다
	 * @return 등록 결과
	 */
	public static CodefRegistration fromCategory(CodefResultCategory category, String connectedId) {
		return switch (category) {
			case SUCCESS -> (connectedId == null || connectedId.isBlank()) ? new CodefRegistration.Unavailable()
					: new CodefRegistration.Registered(connectedId);
			case CARD_VERIFICATION_NOT_SUPPORTED -> new CodefRegistration.CardVerificationRequired();
			case ADDITIONAL_AUTHENTICATION_REQUIRED, OAUTH_TOKEN_ERROR, CERTIFICATE_EXPIRED_OR_REVOKED,
					COMMUNICATION_FAILURE, REQUEST_FORMAT_ERROR, ACCESS_CONFIGURATION_ERROR, UNCLASSIFIED ->
				new CodefRegistration.Unavailable();
		};
	}

}
