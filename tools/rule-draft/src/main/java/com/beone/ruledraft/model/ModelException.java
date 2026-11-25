package com.beone.ruledraft.model;

/**
 * 모델 호출·응답 실패. 메시지에 키·요청 본문을 넣지 않는다.
 */
public class ModelException extends Exception {

	public ModelException(String message) {
		super(message);
	}

	public ModelException(String message, Throwable cause) {
		super(message, cause);
	}

}
