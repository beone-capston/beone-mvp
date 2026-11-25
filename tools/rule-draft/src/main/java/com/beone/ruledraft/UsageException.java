package com.beone.ruledraft;

/** 명령 인자·실행 조건 오류(종료 코드 2). */
final class UsageException extends Exception {

	UsageException(String message) {
		super(message);
	}

}
