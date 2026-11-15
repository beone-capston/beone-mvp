/**
 * 업무 API가 혜택 계산 엔진을 부르는 단일 입구(D-22).
 *
 * <p>업무 코드는 이 패키지만 import한다. {@code benefit}의 다른 패키지는 쓰지 않으며, 카드 상품 조회의
 * {@code RuleCatalog} 읽기만 예외다. 값은 문자열·정수·날짜·null로 주고받고, 내부 타입으로의 변환은 엔진이 한다.
 *
 * <p>구현체와 빈은 아직 없다. 입력·출력 계약과 생성 시 검증만 정의한다.
 */
package com.beone.api.benefit.engine;
