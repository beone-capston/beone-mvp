package com.beone.ruledraft.check;

/**
 * 필드 하나의 원문 근거.
 *
 * @param page 원문 쪽 번호(1부터)
 * @param quote 원문 인용문
 */
public record Evidence(int page, String quote) {
}
