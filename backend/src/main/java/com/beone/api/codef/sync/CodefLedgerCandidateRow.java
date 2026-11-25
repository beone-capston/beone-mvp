package com.beone.api.codef.sync;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import com.beone.api.codef.CodefCancellationForm;

/**
 * 승인내역 한 행의 CODEF 원장 후보 판정. 응답 안 위치, 카드 연결(R1), 날짜·시각 해석(R4), 기존 취소 표현 형태, 받아들임/불완전 판정을 담는다.
 *
 * <p>
 * 원문 승인번호·카드번호는 담지 않는다. 받아들인 행만 중복 판단 키(지문 포함)를 가진다. 이 타입은 도메인 원장({@code LedgerTransaction})이 아니며 저장되지도
 * 않는다(R8).
 *
 * @param position 승인내역 응답 안의 위치(0부터)
 * @param card 보유카드 연결 결과
 * @param usedAt 사용일자·사용일시 해석 결과
 * @param form 기존 취소 표현 형태
 * @param decision 받아들임 또는 불완전
 */
public record CodefLedgerCandidateRow(int position, CodefCardLink card, CodefUsedDateTime usedAt,
		CodefCancellationForm form, Decision decision) {

	/**
	 * 값을 확인한다.
	 *
	 * @throws NullPointerException 값이 null인 경우
	 * @throws IllegalArgumentException 위치가 음수이거나, 받아들인 행인데 카드가 연결되지 않았거나 날짜·시각이 해석되지 않은 경우
	 */
	public CodefLedgerCandidateRow {
		Objects.requireNonNull(card, "card");
		Objects.requireNonNull(usedAt, "usedAt");
		Objects.requireNonNull(form, "form");
		Objects.requireNonNull(decision, "decision");
		if (position < 0) {
			throw new IllegalArgumentException("position must not be negative");
		}
		if (decision instanceof Decision.Accepted && !(card instanceof CodefCardLink.Linked && usedAt.complete())) {
			throw new IllegalArgumentException("an accepted row needs a linked card and a parsed date and time");
		}
	}

	/** 행 판정. */
	public sealed interface Decision {

		/**
		 * 원장 후보로 받아들였다.
		 *
		 * @param kind 행 종류
		 * @param key 중복 판단 키
		 * @param recheck 이전 기록과 맞춘 결과
		 * @param pairedApprovalPosition Type2 취소 행이면 짝지은 정상 행의 응답 안 위치. 그 밖에는 빈 값
		 */
		record Accepted(CodefLedgerRowKind kind, CodefApprovalKey key, CodefRecheck recheck,
				OptionalInt pairedApprovalPosition) implements Decision {

			/**
			 * 값을 확인한다.
			 *
			 * @throws NullPointerException 값이 null인 경우
			 * @throws IllegalArgumentException 짝 위치가 Type2 취소 행에만 있지 않은 경우
			 */
			public Accepted {
				Objects.requireNonNull(kind, "kind");
				Objects.requireNonNull(key, "key");
				Objects.requireNonNull(recheck, "recheck");
				Objects.requireNonNull(pairedApprovalPosition, "pairedApprovalPosition");
				if ((kind == CodefLedgerRowKind.CANCELLATION) != pairedApprovalPosition.isPresent()) {
					throw new IllegalArgumentException("pairedApprovalPosition is present exactly for a cancellation");
				}
			}

			/**
			 * 취소의 귀속월 상태.
			 *
			 * @return 취소를 담은 행이면 항상 확인 필요, 정상 승인 행이면 빈 값
			 */
			public Optional<CodefAttributionMonth> cancellationAttribution() {
				return kind.includesCancellation() ? Optional.of(CodefAttributionMonth.NEEDS_CONFIRMATION)
						: Optional.empty();
			}

		}

		/**
		 * 받아들이지 않고 불완전으로 둔다.
		 *
		 * @param reason 불완전 사유
		 */
		record Incomplete(CodefIncompleteReason reason) implements Decision {

			/**
			 * null을 거부한다.
			 *
			 * @throws NullPointerException 이유가 null인 경우
			 */
			public Incomplete {
				Objects.requireNonNull(reason, "reason");
			}

		}

	}

}
