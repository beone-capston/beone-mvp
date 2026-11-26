package com.beone.ruledraft.review;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 사람이 초안에서 고친 항목 하나.
 *
 * @param path JSON Pointer 경로
 * @param before 초안 값. 초안에 경로가 없었으면 null
 * @param after 고친 값. 값을 없앴으면 null
 * @param reason 수정 사유
 */
public record ReviewEdit(String path, JsonNode before, JsonNode after, String reason) {
}
