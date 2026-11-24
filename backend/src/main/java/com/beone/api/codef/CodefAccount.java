package com.beone.api.codef;

import java.util.Objects;
import java.util.Optional;

import tools.jackson.databind.JsonNode;

/**
 * CODEF 계정 등록·삭제 응답의 {@code data}에서 보관하는 값. connectedId만 읽는다.
 *
 * <p>{@code data}에 {@code connectedId}가 있다는 것은 공식 SDK {@code codef-io/easycodef-java}(커밋 {@code ea96123}) README의 응답 예시와 같다. {@code successList}·{@code errorList}는
 * 읽지 않는다.
 * 문자열 표현에 connectedId를 넣지 않는다.
 *
 * @param connectedId 연결 식별값. 누락·빈 문자열이면 빈 값
 */
public record CodefAccount(Optional<String> connectedId) {

	public CodefAccount {
		Objects.requireNonNull(connectedId, "connectedId");
	}

	static CodefAccount read(JsonNode node) {
		return new CodefAccount(CodefFields.text(node, "connectedId"));
	}

	@Override
	public String toString() {
		return "CodefAccount[connectedId=" + (connectedId.isPresent() ? "<hidden>" : "<absent>") + "]";
	}

}
