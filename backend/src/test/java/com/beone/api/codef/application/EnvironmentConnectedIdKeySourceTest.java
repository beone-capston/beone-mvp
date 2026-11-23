package com.beone.api.codef.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;

import org.junit.jupiter.api.Test;

/**
 * 환경 변수 값 해석. 32바이트 Base64만 키이고 나머지는 키 없음이다(고정 기본값 없음).
 */
class EnvironmentConnectedIdKeySourceTest {

	@Test
	void onlyBase64Of32BytesIsAKey() {
		String valid = Base64.getEncoder().encodeToString(new byte[32]);

		assertThat(EnvironmentConnectedIdKeySource.parse(valid)).hasValueSatisfying(key -> assertThat(key).hasSize(32));
		assertThat(EnvironmentConnectedIdKeySource.parse(null)).isEmpty();
		assertThat(EnvironmentConnectedIdKeySource.parse("")).isEmpty();
		assertThat(EnvironmentConnectedIdKeySource.parse("not base64!")).isEmpty();
		assertThat(EnvironmentConnectedIdKeySource.parse(Base64.getEncoder().encodeToString(new byte[16]))).isEmpty();
	}

	@Test
	void variableNameIsDocumented() {
		assertThat(EnvironmentConnectedIdKeySource.VARIABLE).isEqualTo("BEONE_CODEF_CONNECTED_ID_KEY");
	}

}
