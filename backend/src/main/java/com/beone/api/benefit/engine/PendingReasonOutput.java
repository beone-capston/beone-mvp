package com.beone.api.benefit.engine;

import java.util.List;

import com.beone.api.benefit.result.PendingReason;

/**
 * 카드 금액을 확정하지 못한 사유.
 *
 * @param kind {@link PendingReason.Kind} 이름
 * @param serviceIds 관련 서비스
 * @param detail 설명. 없을 수 있다
 */
public record PendingReasonOutput(String kind, List<String> serviceIds, String detail) {

	public PendingReasonOutput {
		EngineChecks.requireEnumName(PendingReason.Kind.class, kind, "kind");
		serviceIds = EngineChecks.copy(serviceIds, "serviceIds");
		EngineChecks.requireOptionalText(detail, "detail");
	}

}
