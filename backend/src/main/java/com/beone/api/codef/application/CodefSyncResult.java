package com.beone.api.codef.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.beone.api.codef.sync.CodefIncompleteReason;

/**
 * 동기화 한 번의 결과(docs/api/codef.md). 실패한 대상의 건수는 {@code null}이며 0건과 구분한다.
 * @param syncId 동기화 기록 번호
 * @param syncedAt 동기화 시각(서울)
 * @param heldCards 보유카드 결과
 * @param approvals 승인내역 결과
 * @param performances 카드사 조회 실적(참고)
 * @param partialFailures 실패한 대상. 없으면 빈 목록
 */
public record CodefSyncResult(long syncId, LocalDateTime syncedAt, HeldCards heldCards, Approvals approvals,
		List<PerformanceItem> performances, List<CodefSyncTarget> partialFailures) {

	/**
	 * 목록을 불변 복사한다.
	 * @throws NullPointerException 값이 null인 경우
	 */
	public CodefSyncResult {
		Objects.requireNonNull(syncedAt, "syncedAt");
		Objects.requireNonNull(heldCards, "heldCards");
		Objects.requireNonNull(approvals, "approvals");
		performances = List.copyOf(performances);
		partialFailures = List.copyOf(partialFailures);
	}

	/**
	 * 보유카드 결과.
	 * @param fetched 응답 카드 수. 조회 실패면 {@code null}
	 * @param newCandidates 이번에 새로 만든 보유 카드
	 */
	public record HeldCards(Integer fetched, List<NewCandidate> newCandidates) {

		/** 목록을 불변 복사한다. */
		public HeldCards {
			newCandidates = List.copyOf(newCandidates);
		}

	}

	/**
	 * 새로 만든 보유 카드. 후보 상품이 있으면 매핑은 {@code CANDIDATE}, 없으면 {@code UNMAPPED}다(#268).
	 * @param userCardId 보유 카드 번호
	 * @param cardName CODEF 카드명 원문
	 * @param candidateCardProductId 이름 사전으로 둔 후보 상품 ID. 없으면 {@code null}
	 */
	public record NewCandidate(long userCardId, String cardName, String candidateCardProductId) {
	}

	/**
	 * 승인내역 결과.
	 * @param imported 새로 기록했거나 전액 취소로 갱신한 행 수. 조회 실패면 {@code null}
	 * @param skippedDuplicates 이미 같은 키·같은 종류로 기록된 행 수. 조회 실패면 {@code null}
	 * @param incompleteRows 저장하지 않은 행
	 */
	public record Approvals(Integer imported, Integer skippedDuplicates, List<IncompleteRow> incompleteRows) {

		/** 목록을 불변 복사한다. */
		public Approvals {
			incompleteRows = List.copyOf(incompleteRows);
		}

	}

	/**
	 * 저장하지 않은 승인 행. 카드번호·승인번호 원문은 담지 않는다.
	 * @param userCardId 붙은 보유 카드 번호. 붙지 않았으면 {@code null}
	 * @param approvalNoPresent 응답에 승인번호가 있었는지
	 * @param reason 불완전 사유
	 */
	public record IncompleteRow(Long userCardId, boolean approvalNoPresent, CodefIncompleteReason reason) {

		/**
		 * 값을 확인한다.
		 * @throws NullPointerException 사유가 null인 경우
		 */
		public IncompleteRow {
			Objects.requireNonNull(reason, "reason");
		}

	}

	/**
	 * 실적현황 하나의 참고값. 확인하지 못한 값은 {@code null}이다. 계산에 쓰지 않는다(D-16).
	 * @param userCardId 붙은 보유 카드 번호. 붙지 않았으면 {@code null}
	 * @param periodFrom 이용금액 반영기간 시작일
	 * @param periodTo 이용금액 반영기간 종료일
	 * @param currentUseWon 현재이용금액
	 * @param fetchedAt 조회 시각(서울)
	 */
	public record PerformanceItem(Long userCardId, LocalDate periodFrom, LocalDate periodTo, Long currentUseWon,
			LocalDateTime fetchedAt) {

		/**
		 * 값을 확인한다.
		 * @throws NullPointerException 조회 시각이 null인 경우
		 */
		public PerformanceItem {
			Objects.requireNonNull(fetchedAt, "fetchedAt");
		}

	}

}
