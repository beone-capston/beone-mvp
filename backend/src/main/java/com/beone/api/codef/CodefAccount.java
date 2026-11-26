package com.beone.api.codef;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;

/**
 * CODEF 계정 등록·삭제 응답의 {@code data}에서 보관하는 값. connectedId와 {@code errorList} 항목의 코드만 읽는다.
 *
 * <p>{@code data}에 {@code connectedId}·{@code successList}·{@code errorList}가 있다는 것은 공식 SDK {@code codef-io/easycodef-java}(커밋
 * {@code ea96123}) README의 응답 예시와 같다. {@code successList}는 읽지 않는다. {@code errorList}에서는 항목별 {@code code}만 읽고
 * 메시지·기관 문구·로그인 입력은 읽지 않는다. 코드는 {@code CF-}와 숫자 5자리 형식만 그대로 두고, 그 밖의 값(누락·문자열 아님·다른 형식)은
 * {@value #OTHER_CODE}로 바꿔 응답 값이 로그에 그대로 실리지 않게 한다. 보관하는 코드는 앞에서부터 {@value #MAX_ERROR_CODES}개까지다.
 * 문자열 표현에 connectedId를 넣지 않는다.
 *
 * @param connectedId 연결 식별값. 누락·빈 문자열이면 빈 값
 * @param errorCodes {@code errorList} 항목 코드(형식 검사·치환 뒤, 최대 {@value #MAX_ERROR_CODES}개). 목록이 없으면 빈 목록
 * @param errorCount {@code errorList} 배열의 실제 항목 수. 배열이 아니거나 없으면 0
 */
public record CodefAccount(Optional<String> connectedId, List<String> errorCodes, int errorCount) {

	/** 형식에 맞지 않는 {@code errorList} 코드를 대신하는 값. */
	public static final String OTHER_CODE = "OTHER";

	/** 보관하는 {@code errorList} 코드 수 상한. */
	public static final int MAX_ERROR_CODES = 5;

	private static final Pattern CODE = Pattern.compile("CF-\\d{5}");

	public CodefAccount {
		Objects.requireNonNull(connectedId, "connectedId");
		errorCodes = List.copyOf(Objects.requireNonNull(errorCodes, "errorCodes"));
		if (errorCodes.size() > MAX_ERROR_CODES) {
			throw new IllegalArgumentException("errorCodes exceeds " + MAX_ERROR_CODES);
		}
		if (errorCount < errorCodes.size()) {
			throw new IllegalArgumentException("errorCount must not be less than errorCodes size");
		}
	}

	/**
	 * {@code errorList}가 없는 계정 응답.
	 * @param connectedId 연결 식별값
	 */
	public CodefAccount(Optional<String> connectedId) {
		this(connectedId, List.of(), 0);
	}

	static CodefAccount read(JsonNode node) {
		JsonNode errorList = node.get("errorList");
		List<String> codes = new ArrayList<>();
		int count = 0;
		if (errorList != null && errorList.isArray()) {
			for (JsonNode element : errorList.values()) {
				count++;
				if (codes.size() < MAX_ERROR_CODES) {
					codes.add(element.isObject() ? safeCode(CodefFields.text(element, "code")) : OTHER_CODE);
				}
			}
		}
		return new CodefAccount(CodefFields.text(node, "connectedId"), codes, count);
	}

	/**
	 * 로그·분류에 써도 되는 코드로 바꾼다.
	 * @param code 응답의 코드 값
	 * @return {@code CF-}와 숫자 5자리면 그 값, 아니면 {@value #OTHER_CODE}
	 */
	static String safeCode(Optional<String> code) {
		return code.filter(value -> CODE.matcher(value).matches()).orElse(OTHER_CODE);
	}

	@Override
	public String toString() {
		return "CodefAccount[connectedId=" + (connectedId.isPresent() ? "<hidden>" : "<absent>") + ", errorCodes="
				+ errorCodes + ", errorCount=" + errorCount + "]";
	}

}
