package com.beone.api.monthlystate.application;

/**
 * 출처 충돌 계산 기준 선택 요청(docs/api/monthly-states.md PUT source-selections). 형식 검증은 웹 계층이 끝낸 값이다.
 *
 * @param item 항목. {@code previousMonthPerformanceWon}, {@code otherBucketsUsedWon}, {@code limitBucketUsage:{limitBucketId}},
 * {@code registeredOn}
 * @param period 기준 기간
 * @param selectedSource 고른 출처. {@link StateSource#MANUAL}·{@link StateSource#CODEF}·{@link StateSource#INFERRED}
 * @param conflictVersion 월간 상태 조회 {@code conflicts[]}의 충돌 버전
 */
public record SourceSelection(String item, DatePeriod period, StateSource selectedSource, String conflictVersion) {
}
