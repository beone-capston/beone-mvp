package com.beone.api.auth.web;

/**
 * 가입·인증메일 재요청의 202 본문(docs/api/auth.md). 계정 유무·인증 상태·실제 발송 여부와 관계없이 같다.
 *
 * @param message 안내 문구
 */
public record AcceptedResponse(String message) {

	/** 사본의 안내 문구. */
	static final AcceptedResponse MAIL_REQUEST = new AcceptedResponse("처리 가능한 요청이면 이메일로 안내합니다.");

}
