package com.beone.api.codef;

import java.util.Objects;
import java.util.Optional;

import tools.jackson.databind.JsonNode;

/**
 * CODEF 계정 등록·삭제 응답의 {@code data}에서 보관하는 값. connectedId만 읽는다.
 *
 * <p>응답 형식({@code data.connectedId})은 비공개 실연동 전 근거 확인 대상이다. 저장소에는 계정 API의 공식 문서 사본이 아직 없다.
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
