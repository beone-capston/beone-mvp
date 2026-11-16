package com.beone.api.codef.sync;

/**
 * 원문 승인번호를 지문(HMAC)으로 바꾸는 함수. 원장 후보 생성기는 이 함수를 주입받기만 하고 키를 다루지 않는다(결정 R2).
 *
 * <p>
 * 운영 구현체, 키 이름, 키 공급 방식(환경 변수·사용자 입력 등), 키 회전은 정하지 않았다(미결). 테스트는 테스트 코드 안의 합성 키로 만든 구현만 쓴다. 구현은
 * 같은 키·같은 입력에 같은 값을 돌려줘야 하고, 원문 승인번호를 그대로 돌려주거나 로그·예외 문구에 남기면 안 된다.
 */
@FunctionalInterface
public interface CodefApprovalFingerprinter {

	/**
	 * 승인번호 원문의 지문을 만든다.
	 *
	 * @param approvalNumber 응답의 승인번호 원문. 공백·자릿수를 고치지 않은 값
	 * @return 지문
	 */
	CodefApprovalFingerprint fingerprint(String approvalNumber);

}
