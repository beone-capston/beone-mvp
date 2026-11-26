package com.beone.ruledraft.model;

/**
 * 규칙 초안을 만드는 AI 어댑터. 제공자마다 구현을 따로 둔다.
 *
 * <p>반환값은 모델이 낸 텍스트 그대로다. JSON 해석·스키마 검사·근거 검사는 호출 쪽이 한다.
 */
public interface RuleDraftModel {

	String draft(DraftRequest request) throws ModelException;

	/** 출력 메타데이터에 남길 설명(제공자·모델 이름). 키는 넣지 않는다. */
	String describe();

}
