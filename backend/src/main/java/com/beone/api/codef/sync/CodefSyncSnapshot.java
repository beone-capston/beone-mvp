package com.beone.api.codef.sync;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import com.beone.api.codef.CodefApproval;
import com.beone.api.codef.CodefCardPerformance;
import com.beone.api.codef.CodefHeldCard;
import com.beone.api.codef.transport.CodefCallResult;

/**
 * 동기화 한 번의 세 조회 대상(보유카드·승인내역·실적조회) 결과 묶음. 대상마다 따로 판정하므로 하나가 실패해도 나머지는 그대로다.
 *
 * <p>
 * CODEF를 호출하지 않는다. 전송 계층이 돌려준 {@link CodefCallResult}를 받아 판정·변환만 한다(합성 응답으로만 시험, 실계정 관측 없음). 저장, 카드 연결(R1),
 * 짝짓기·중복 키(R2), 후보 상품 산출(R3), 승인 날짜 해석(R4), 공개 오류 코드 대응(R5), 도메인 원장 연결(R8)은 하지 않는다.
 *
 * @param organizationCode 응답을 받은 기관 코드
 * @param checkedAt 조회 시각. 세 대상이 같은 값을 쓴다
 * @param heldCards 보유카드 → 매핑 전 후보
 * @param approvals 승인내역 → 행 형태 분류
 * @param performances 실적조회 → 카드사 조회 실적(참고)
 */
public record CodefSyncSnapshot(String organizationCode, Instant checkedAt,
		CodefTargetOutcome<CodefHeldCardCandidate> heldCards, CodefTargetOutcome<CodefApprovalRowView> approvals,
		CodefTargetOutcome<CodefPerformanceReference> performances) {

	/**
	 * null을 거부한다.
	 *
	 * @throws NullPointerException 값이 null인 경우
	 */
	public CodefSyncSnapshot {
		Objects.requireNonNull(organizationCode, "organizationCode");
		Objects.requireNonNull(checkedAt, "checkedAt");
		Objects.requireNonNull(heldCards, "heldCards");
		Objects.requireNonNull(approvals, "approvals");
		Objects.requireNonNull(performances, "performances");
	}

	/**
	 * 세 호출 결과를 판정해 묶는다. 조회 시각은 시계에서 한 번만 읽는다.
	 *
	 * @param organizationCode 응답을 받은 기관 코드. 정확히 일치해야 하며 고치지 않는다
	 * @param clock 조회 시각을 정할 시계
	 * @param heldCards 보유카드 호출 결과
	 * @param approvals 승인내역 호출 결과
	 * @param performances 실적조회 호출 결과
	 * @return 묶음
	 * @throws NullPointerException 입력이 null인 경우
	 */
	public static CodefSyncSnapshot assemble(String organizationCode, Clock clock,
			CodefCallResult<CodefHeldCard> heldCards, CodefCallResult<CodefApproval> approvals,
			CodefCallResult<CodefCardPerformance> performances) {
		Objects.requireNonNull(organizationCode, "organizationCode");
		Objects.requireNonNull(clock, "clock");
		Instant checkedAt = clock.instant();
		return new CodefSyncSnapshot(organizationCode, checkedAt,
				CodefTargetOutcome.judge(heldCards, checkedAt, CodefHeldCardCandidate::from),
				CodefTargetOutcome.judge(approvals, checkedAt,
						approval -> CodefApprovalRowView.describe(organizationCode, approval)),
				CodefTargetOutcome.judge(performances, checkedAt,
						card -> CodefPerformanceReference.from(card, checkedAt)));
	}

}
