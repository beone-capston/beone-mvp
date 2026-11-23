package com.beone.api.user.application;

/**
 * 시연 사용자 구분(D-09). 이름이 곧 {@code X-BeONE-Demo-User} 헤더 값과 응답 {@code demoUser} 값이다.
 *
 * <p>선언 순서가 시연 사용자 목록의 응답 순서다.
 */
public enum DemoUser {

	/** 실제 보유 카드(수동·CODEF)를 쓰는 사용자. */
	MY_CARDS,

	/** 시드 데이터로 고정한 가상 포트폴리오 사용자. */
	VIRTUAL_PORTFOLIO

}
