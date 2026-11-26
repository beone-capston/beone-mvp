package com.beone.ruledraft.check;

import java.util.List;
import java.util.Map;

/**
 * 근거 검사 결과.
 *
 * @param verified 원문에서 확인된 근거(경로 → 근거)
 * @param missing 확인된 근거가 자기·조상 경로 어디에도 없는 값 경로
 * @param unverified 확인하지 못한 근거와 사유(경로: 사유)
 */
public record EvidenceReport(Map<String, Evidence> verified, List<String> missing, List<String> unverified) {

	public boolean complete() {
		return missing.isEmpty() && unverified.isEmpty();
	}

}
