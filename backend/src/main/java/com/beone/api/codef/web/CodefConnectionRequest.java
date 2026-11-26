package com.beone.api.codef.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * {@code POST /api/v1/me/codef/connection} 요청 본문(docs/api/codef.md).
 *
 * <p>필수 4필드다. 기관은 KB국민카드 {@code 0301}, 로그인 방식은 아이디 로그인 {@code ID}만 받는다. 검증 오류 문구는 고정 문구라
 * 입력값이 들어가지 않는다. 아이디는 제어·서식 문자와 앞뒤 공백을, 비밀번호는 제어 문자만 CODEF 호출 전에 거절한다({@link CodefLoginText},
 * {@link NoControlCharacters}). 아이디·비밀번호는 문자열 표현에 넣지 않는다. 비밀번호는 {@code char[]}로 받아 서비스가 쓰고 지운다.
 */
public class CodefConnectionRequest {

	static final String REQUIRED = "필수 값입니다.";

	static final String NOT_ALLOWED = "허용되지 않는 값입니다.";

	static final String INVALID_CHARACTER = "사용할 수 없는 문자가 있습니다.";

	@NotNull(message = REQUIRED)
	@Pattern(regexp = "0301", message = NOT_ALLOWED)
	@JsonDeserialize(using = StrictTextDeserializer.class)
	private String organization;

	@NotNull(message = REQUIRED)
	@Pattern(regexp = "ID", message = NOT_ALLOWED)
	@JsonDeserialize(using = StrictTextDeserializer.class)
	private String loginType;

	@NotNull(message = REQUIRED)
	@CodefLoginText
	@JsonDeserialize(using = StrictTextDeserializer.class)
	private String loginId;

	@NotNull(message = REQUIRED)
	@NoControlCharacters
	@JsonDeserialize(using = PasswordDeserializer.class)
	private char[] password;

	public void setOrganization(String organization) {
		this.organization = organization;
	}

	public void setLoginType(String loginType) {
		this.loginType = loginType;
	}

	public void setLoginId(String loginId) {
		this.loginId = loginId;
	}

	public void setPassword(char[] password) {
		this.password = password;
	}

	String loginId() {
		return this.loginId;
	}

	char[] password() {
		return this.password;
	}

	@Override
	public String toString() {
		return "CodefConnectionRequest[organization=" + this.organization + ", loginType=" + this.loginType
				+ ", loginId=<hidden>, password=<hidden>]";
	}

}
