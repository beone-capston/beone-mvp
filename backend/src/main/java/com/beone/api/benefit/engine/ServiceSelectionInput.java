package com.beone.api.benefit.engine;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;

/**
 * 택1 선택 하나.
 *
 * @param serviceId 고른 서비스. 선택 없음({@code NONE})이면 null
 * @param effectiveFrom 적용 시작일. 없으면 엔진이 {@code 확인 필요}로 처리한다(D-16)
 * @param source {@code MANUAL}·{@code CODEF}·{@code SYNTHETIC}
 * @param updatedAt 입력·조회 시각. {@code SYNTHETIC}이 아니면 필수
 * @param usable 계산에 쓸 수 있으면 참
 * @param reason {@code usable=false}의 사유. {@code SOURCE_CONFLICT}만 받는다
 */
public record ServiceSelectionInput(String serviceId, LocalDate effectiveFrom, String source,
		OffsetDateTime updatedAt, boolean usable, String reason) {

	public ServiceSelectionInput {
		EngineChecks.requireOptionalToken(serviceId, "serviceId");
		EngineChecks.requireOneOf(Set.of("MANUAL", "CODEF", "SYNTHETIC"), source, "source");
		if (updatedAt == null && !"SYNTHETIC".equals(source)) {
			throw new IllegalArgumentException("updatedAt is required for source " + source);
		}
		if (usable && reason != null) {
			throw new IllegalArgumentException("a usable selection must not carry a reason");
		}
		if (!usable) {
			EngineChecks.requireOneOf(Set.of("SOURCE_CONFLICT"), reason, "reason");
		}
	}

}
