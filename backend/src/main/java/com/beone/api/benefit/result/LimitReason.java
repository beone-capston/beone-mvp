package com.beone.api.benefit.result;

/**
 * 한도 입력 때문에 서비스를 판정하지 못한 사유(D-18 K1).
 */
public enum LimitReason {

	/** 체인 안 어느 버킷의 입력 사용액이 한도보다 크다. 입력이 규칙과 모순된다. */
	USAGE_EXCEEDS_LIMIT

}
