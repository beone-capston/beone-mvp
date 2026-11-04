package com.beone.api.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 실패 응답의 검증 상세 한 건.
 *
 * <p>요청 전체(객체·교차 파라미터) 오류는 {@code field}가 {@code null}이다. 전역 Jackson 포함 설정과
 * 관계없이 {@code "field": null}을 직렬화하도록 필드 단위로 고정한다. {@code message}에는 입력 원문을 넣지 않는다.
 */
public record FieldViolation(@JsonInclude(JsonInclude.Include.ALWAYS) String field, String message) {

}
