package com.beone.api.codef.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;

import org.junit.jupiter.api.Test;

/**
 * 승인번호 HMAC 키 환경 변수 해석. 32바이트 Base64만 키이고 나머지는 키 없음이다(P6, 고정 기본값 없음).
 */
class EnvironmentApprovalFingerprintKeySourceTest {

	@Test
	void onlyBase64Of32BytesIsAKey() {
		String valid = Base64.getEncoder().encodeToString(new byte[32]);

		assertThat(EnvironmentApprovalFingerprintKeySource.parse(valid))
			.hasValueSatisfying(key -> assertThat(key).hasSize(32));
		assertThat(EnvironmentApprovalFingerprintKeySource.parse(null)).isEmpty();
		assertThat(EnvironmentApprovalFingerprintKeySource.parse("")).isEmpty();
		assertThat(EnvironmentApprovalFingerprintKeySource.parse("not base64!")).isEmpty();
		assertThat(EnvironmentApprovalFingerprintKeySource.parse(Base64.getEncoder().encodeToString(new byte[31])))
			.isEmpty();
	}

	@Test
	void variableNameIsDocumentedAndDiffersFromTheConnectedIdKey() {
		assertThat(EnvironmentApprovalFingerprintKeySource.VARIABLE).isEqualTo("BEONE_CODEF_APPROVAL_HMAC_KEY")
			.isNotEqualTo(EnvironmentConnectedIdKeySource.VARIABLE);
	}

}
