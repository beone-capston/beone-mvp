package com.beone.ruledraft.model;

import com.beone.ruledraft.source.SourceDocument;

/**
 * 모델에 넘기는 초안 요청.
 *
 * @param cardId 카드 식별자(규칙 디렉터리 이름, 예: 01664)
 * @param source 쪽 단위 원문
 * @param instructions 지시문(출력 형식·허용 키 목록 포함)
 */
public record DraftRequest(String cardId, SourceDocument source, String instructions) {
}
