package com.beone.ruledraft.review;

import com.beone.ruledraft.check.Evidence;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 값 경로 하나의 비교 결과.
 *
 * @param path JSON Pointer 경로
 * @param status 최종 분류
 * @param comparison 근거와 상관없는 값 비교 결과. {@link CompareStatus#NO_EVIDENCE}가 아니면 status와 같다
 * @param draft 초안 값. 초안에 경로가 없으면 null
 * @param manual 수동 규칙 값. 수동 규칙에 경로가 없으면 null
 * @param evidence 이 값을 덮는 확인된 근거(자기 또는 가장 가까운 조상). 없으면 null
 * @param evidencePath 그 근거가 달린 경로. 없으면 null
 */
public record CompareRow(String path, CompareStatus status, CompareStatus comparison, JsonNode draft, JsonNode manual,
		Evidence evidence, String evidencePath) {
}
