package com.beone.api.benefit.result;

/**
 * 서비스 후보 취합(#33)이 판정하지 않는 서비스의 종류.
 */
public enum UnsupportedReason {

	/** 한도 버킷이 있는 서비스. */
	HAS_LIMIT_BUCKETS,

	/** 사용자가 선택해야 적용되는 선택형 서비스. */
	REQUIRES_SELECTION

}
