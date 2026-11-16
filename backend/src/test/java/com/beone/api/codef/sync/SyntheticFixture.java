package com.beone.api.codef.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.beone.api.codef.CodefApproval;
import com.beone.api.codef.CodefCardPerformance;
import com.beone.api.codef.CodefHeldCard;
import com.beone.api.codef.CodefProductResponseReader;
import com.beone.api.codef.transport.CodefCallResult;

/**
 * {@code src/test/resources/codef/sync/}의 합성 응답을 읽어 기존 전송 단계 해석기에 통과시킨다. 실제 HTTP·CODEF 호출은 없다.
 *
 * <p>
 * 모든 fixture는 최상위 {@code _fixture}에 {@code synthetic=true}, {@code observed=false}, 시트 셀 근거({@code basis})를 가져야 한다. 공식 시트 기준이며
 * 실계정 관측이 아니다.
 */
final class SyntheticFixture {

	static final Instant CHECKED_AT = Instant.parse("2026-11-16T03:00:00Z");

	static final Clock CLOCK = Clock.fixed(CHECKED_AT, ZoneOffset.UTC);

	static final String KB = "0301";

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private SyntheticFixture() {
	}

	/** fixture 본문을 읽고 합성 표시를 확인한다. */
	static String body(String name) {
		String body;
		try (InputStream in = SyntheticFixture.class.getResourceAsStream("/codef/sync/" + name)) {
			assertThat(in).as("fixture %s", name).isNotNull();
			body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException failure) {
			throw new UncheckedIOException(failure);
		}
		JsonNode marker = MAPPER.readTree(body).get("_fixture");
		assertThat(marker).as("_fixture of %s", name).isNotNull();
		assertThat(marker.get("synthetic").booleanValue()).isTrue();
		assertThat(marker.get("observed").booleanValue()).isFalse();
		assertThat(marker.get("basis").stringValue()).isNotBlank();
		return body;
	}

	static CodefCallResult<CodefHeldCard> heldCards(String name) {
		return new CodefCallResult.Answered<>(CodefProductResponseReader.readHeldCards(200, body(name)));
	}

	static CodefCallResult<CodefApproval> approvals(String name) {
		return new CodefCallResult.Answered<>(CodefProductResponseReader.readApprovals(200, body(name)));
	}

	static CodefCallResult<CodefCardPerformance> performances(String name) {
		return new CodefCallResult.Answered<>(CodefProductResponseReader.readCardPerformances(200, body(name)));
	}

}
