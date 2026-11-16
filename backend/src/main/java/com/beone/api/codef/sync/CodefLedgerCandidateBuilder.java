package com.beone.api.codef.sync;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import com.beone.api.codef.CodefAmount;
import com.beone.api.codef.CodefApproval;
import com.beone.api.codef.CodefCancellationForm;
import com.beone.api.codef.sync.CodefApprovalRowView.KrwAmountAvailability;
import com.beone.api.codef.sync.CodefLedgerCandidateRow.Decision;
import com.beone.api.codef.sync.CodefUsedDateTime.ParseState;

/**
 * 승인내역 응답 하나를 CODEF 원장 후보 행으로 판정하는 순수 함수(결정 R1·R2·R4, D-17). 같은 입력이면 같은 결과를 돌려주고, 저장·로그·네트워크가 없다.
 *
 * <p>
 * 판정 순서(행마다 처음 걸린 사유 하나만 남긴다):
 * <ol>
 * <li>카드 미연결(R1) → {@link CodefIncompleteReason#UNDETERMINED_ROW}</li>
 * <li>형태: 부분취소 코드, Type1 취소금액 불일치 → {@link CodefIncompleteReason#PARTIAL_CANCELLATION_SUSPECTED}. 그 밖의 판단 불가 형태, KB 밖 기관 →
 * {@link CodefIncompleteReason#UNDETERMINED_ROW}</li>
 * <li>원화 금액을 만들 수 없음(통화·국내외·금액) → {@link CodefIncompleteReason#UNDETERMINED_ROW}</li>
 * <li>승인번호 또는 사용일자 없음(R2) → {@link CodefIncompleteReason#DUPLICATE_KEY_AMBIGUOUS}</li>
 * <li>사용일자 해석 실패, 사용일시 없음·해석 실패(R4) → {@link CodefIncompleteReason#UNDETERMINED_ROW}</li>
 * <li>한 응답 안 같은 키 둘 이상(R2) → 해당 행 모두 {@link CodefIncompleteReason#DUPLICATE_KEY_AMBIGUOUS}</li>
 * <li>정상·Type1 행의 재조회 대조. 정상 → Type1 밖의 종류 변화는 {@link CodefIncompleteReason#UNDETERMINED_ROW}</li>
 * <li>Type2 짝짓기(D-17): 같은 카드·같은 승인번호 지문의 정상 행이 이 응답에 정확히 하나이고 금액 절댓값이 같을 때만 받아들인다. 없으면
 * {@link CodefIncompleteReason#NO_MATCHING_APPROVAL}, 여럿이면 {@link CodefIncompleteReason#MULTIPLE_MATCHING_APPROVALS}, 취소가 작으면
 * {@link CodefIncompleteReason#PARTIAL_CANCELLATION_SUSPECTED}, 취소가 크거나 짝 정상 행이 받아들여지지 않았거나 한 정상 행을 취소 행 여럿이
 * 가리키면 {@link CodefIncompleteReason#UNDETERMINED_ROW}. 그 뒤 재조회 대조</li>
 * </ol>
 * 짝은 이번 응답 안에서만 찾고, 이전 기록이나 조회 기간 밖으로 넓히지 않는다. 원문 승인번호는 지문 함수에 넘기기만 하고 어디에도 남기지 않는다.
 */
public final class CodefLedgerCandidateBuilder {

	private CodefLedgerCandidateBuilder() {
	}

	/**
	 * 승인내역 응답 하나를 판정한다.
	 *
	 * @param organizationCode 응답을 받은 기관 코드. KB({@code 0301}) 밖은 취소 표현 근거가 없어 모든 행이 판단 불가다
	 * @param linker 같은 동기화의 보유카드로 만든 카드 연결기
	 * @param approvals 쓸 수 있는 승인내역 응답의 행 목록(응답 순서)
	 * @param fingerprinter 승인번호 지문 함수
	 * @param previous 이전 기록: 받아들였던 키와 종류. 처음이면 빈 맵
	 * @return 판정 결과
	 * @throws NullPointerException 입력이 null이거나 지문 함수가 null을 돌려준 경우
	 * @throws IllegalStateException 지문 함수가 승인번호 원문을 그대로 돌려준 경우
	 */
	public static CodefLedgerCandidates build(String organizationCode, CodefCardLinker linker,
			List<CodefApproval> approvals, CodefApprovalFingerprinter fingerprinter,
			Map<CodefApprovalKey, CodefLedgerRowKind> previous) {
		Objects.requireNonNull(organizationCode, "organizationCode");
		Objects.requireNonNull(linker, "linker");
		Objects.requireNonNull(fingerprinter, "fingerprinter");
		List<CodefApproval> rows = List.copyOf(approvals);
		Map<CodefApprovalKey, CodefLedgerRowKind> recorded = Map.copyOf(previous);

		int size = rows.size();
		List<Draft> drafts = new ArrayList<>(size);
		for (int position = 0; position < size; position++) {
			drafts.add(draft(position, organizationCode, linker, rows.get(position), fingerprinter));
		}
		rejectDuplicateKeys(drafts);
		for (Draft draft : drafts) {
			if (draft.accepted() && draft.kind != CodefLedgerRowKind.CANCELLATION) {
				recheck(draft, recorded);
			}
		}
		pairCancellations(drafts);
		for (Draft draft : drafts) {
			if (draft.accepted() && draft.kind == CodefLedgerRowKind.CANCELLATION) {
				recheck(draft, recorded);
			}
		}
		return new CodefLedgerCandidates(drafts.stream().map(Draft::toRow).toList());
	}

	private static Draft draft(int position, String organizationCode, CodefCardLinker linker, CodefApproval approval,
			CodefApprovalFingerprinter fingerprinter) {
		CodefCardLink card = linker.link(approval.cardName());
		CodefUsedDateTime usedAt = CodefUsedDateTime.parse(approval.usedDate(), approval.usedTime());
		CodefApprovalRowView view = CodefApprovalRowView.describe(organizationCode, approval);
		Optional<CodefApprovalFingerprint> fingerprint = card instanceof CodefCardLink.Linked
				? approval.approvalNo().map(number -> fingerprint(fingerprinter, number)) : Optional.empty();
		Draft draft = new Draft(position, card, usedAt, view.form(), fingerprint, usedAmount(approval));
		draft.reason = rowReason(approval, card, view, usedAt);
		if (draft.reason == null) {
			draft.kind = kindOf(view.form());
			draft.key = new CodefApprovalKey(((CodefCardLink.Linked) card).cardName(), fingerprint.orElseThrow(),
					usedAt.date().orElseThrow(), usedAt.time().orElseThrow(), draft.usedAmount);
		}
		return draft;
	}

	private static CodefApprovalFingerprint fingerprint(CodefApprovalFingerprinter fingerprinter, String number) {
		CodefApprovalFingerprint fingerprint = Objects.requireNonNull(fingerprinter.fingerprint(number), "fingerprint");
		if (fingerprint.value().equals(number)) {
			// 원문이 그대로 남지 않게 막는다. 문구에 원문을 넣지 않는다.
			throw new IllegalStateException("the fingerprint must differ from the approval number");
		}
		return fingerprint;
	}

	private static CodefIncompleteReason rowReason(CodefApproval approval, CodefCardLink card,
			CodefApprovalRowView view, CodefUsedDateTime usedAt) {
		if (card instanceof CodefCardLink.NotLinked) {
			return CodefIncompleteReason.UNDETERMINED_ROW;
		}
		CodefIncompleteReason formReason = formReason(approval, view.form());
		if (formReason != null) {
			return formReason;
		}
		if (view.krwAmount() != KrwAmountAvailability.AVAILABLE) {
			return CodefIncompleteReason.UNDETERMINED_ROW;
		}
		if (approval.approvalNo().isEmpty() || usedAt.dateState() == ParseState.ABSENT) {
			return CodefIncompleteReason.DUPLICATE_KEY_AMBIGUOUS;
		}
		if (!usedAt.complete()) {
			return CodefIncompleteReason.UNDETERMINED_ROW;
		}
		return null;
	}

	private static CodefIncompleteReason formReason(CodefApproval approval, CodefCancellationForm form) {
		return switch (form) {
			case CodefCancellationForm.NormalRow normal -> null;
			case CodefCancellationForm.Type1Canceled type1 -> null;
			case CodefCancellationForm.Type2CancelRow type2 -> null;
			case CodefCancellationForm.Undetermined undetermined ->
				undetermined.reason() == CodefCancellationForm.Reason.PARTIAL_CANCEL_NOT_DISTINGUISHED
						|| type1AmountMismatch(approval) ? CodefIncompleteReason.PARTIAL_CANCELLATION_SUSPECTED
								: CodefIncompleteReason.UNDETERMINED_ROW;
			case CodefCancellationForm.OrganizationNotDocumented notDocumented ->
				CodefIncompleteReason.UNDETERMINED_ROW;
		};
	}

	/** D-17 Type1: 취소 "1"이고 양수 이용금액인데 취소금액이 다르면 부분취소일 수 있다. */
	private static boolean type1AmountMismatch(CodefApproval approval) {
		return approval.cancelStatus() == CodefApproval.CancelStatus.CANCELED
				&& approval.usedAmount() instanceof CodefAmount.Parsed(long used) && used > 0
				&& approval.cancelAmount() instanceof CodefAmount.Parsed(long canceled) && canceled != used;
	}

	private static CodefLedgerRowKind kindOf(CodefCancellationForm form) {
		return switch (form) {
			case CodefCancellationForm.NormalRow normal -> CodefLedgerRowKind.APPROVAL;
			case CodefCancellationForm.Type1Canceled type1 -> CodefLedgerRowKind.APPROVAL_FULLY_CANCELED;
			case CodefCancellationForm.Type2CancelRow type2 -> CodefLedgerRowKind.CANCELLATION;
			default -> throw new IllegalStateException("only accepted forms have a kind");
		};
	}

	private static long usedAmount(CodefApproval approval) {
		return approval.usedAmount() instanceof CodefAmount.Parsed(long used) ? used : 0L;
	}

	private static void rejectDuplicateKeys(List<Draft> drafts) {
		Map<CodefApprovalKey, Integer> counts = new HashMap<>();
		for (Draft draft : drafts) {
			if (draft.accepted()) {
				counts.merge(draft.key, 1, Integer::sum);
			}
		}
		for (Draft draft : drafts) {
			if (draft.accepted() && counts.get(draft.key) > 1) {
				draft.reject(CodefIncompleteReason.DUPLICATE_KEY_AMBIGUOUS);
			}
		}
	}

	private static void recheck(Draft draft, Map<CodefApprovalKey, CodefLedgerRowKind> recorded) {
		CodefLedgerRowKind before = recorded.get(draft.key);
		if (before == null) {
			draft.recheck = CodefRecheck.NEW;
		}
		else if (before == draft.kind) {
			draft.recheck = CodefRecheck.ALREADY_RECORDED;
		}
		else if (before == CodefLedgerRowKind.APPROVAL && draft.kind == CodefLedgerRowKind.APPROVAL_FULLY_CANCELED) {
			draft.recheck = CodefRecheck.CANCELED_SINCE_RECORDED;
		}
		else {
			// 정상 → Type1 밖의 변화는 모순으로 보고 넣지 않는다.
			draft.reject(CodefIncompleteReason.UNDETERMINED_ROW);
		}
	}

	private static void pairCancellations(List<Draft> drafts) {
		Map<Integer, List<Draft>> claims = new HashMap<>();
		for (Draft cancel : drafts) {
			if (!cancel.accepted() || cancel.kind != CodefLedgerRowKind.CANCELLATION) {
				continue;
			}
			List<Draft> matches = drafts.stream().filter(normal -> sameApproval(cancel, normal)).toList();
			if (matches.isEmpty()) {
				cancel.reject(CodefIncompleteReason.NO_MATCHING_APPROVAL);
				continue;
			}
			if (matches.size() > 1) {
				cancel.reject(CodefIncompleteReason.MULTIPLE_MATCHING_APPROVALS);
				continue;
			}
			Draft normal = matches.getFirst();
			int comparison = compareAbsolute(cancel.usedAmount, normal.usedAmount);
			if (comparison < 0) {
				cancel.reject(CodefIncompleteReason.PARTIAL_CANCELLATION_SUSPECTED);
			}
			else if (comparison > 0 || !normal.accepted()) {
				cancel.reject(CodefIncompleteReason.UNDETERMINED_ROW);
			}
			else {
				cancel.pairedPosition = OptionalInt.of(normal.position);
				claims.computeIfAbsent(normal.position, ignored -> new ArrayList<>()).add(cancel);
			}
		}
		for (List<Draft> claimants : claims.values()) {
			if (claimants.size() > 1) {
				// 정상 행 하나에 전액 취소가 둘 이상이면 이중 차감이 되므로 넣지 않는다.
				claimants.forEach(cancel -> cancel.reject(CodefIncompleteReason.UNDETERMINED_ROW));
			}
		}
	}

	/** 같은 카드·같은 승인번호 지문의 정상 행인지. 정상 행 자체가 불완전이어도 짝 개수에는 센다. */
	private static boolean sameApproval(Draft cancel, Draft normal) {
		return normal.form instanceof CodefCancellationForm.NormalRow && normal.card.equals(cancel.card)
				&& normal.fingerprint.isPresent() && normal.fingerprint.equals(cancel.fingerprint);
	}

	/** 음수 취소 금액의 절댓값과 양수 승인 금액을 비교한다. {@code Long.MIN_VALUE}의 절댓값은 어떤 승인 금액보다 크다. */
	private static int compareAbsolute(long negativeCancel, long approval) {
		if (negativeCancel == Long.MIN_VALUE) {
			return 1;
		}
		return Long.compare(-negativeCancel, approval);
	}

	/** 판정 중의 행 상태. 이 클래스 안에서만 바꾼다. */
	private static final class Draft {

		private final int position;

		private final CodefCardLink card;

		private final CodefUsedDateTime usedAt;

		private final CodefCancellationForm form;

		private final Optional<CodefApprovalFingerprint> fingerprint;

		private final long usedAmount;

		private CodefIncompleteReason reason;

		private CodefLedgerRowKind kind;

		private CodefApprovalKey key;

		private CodefRecheck recheck;

		private OptionalInt pairedPosition = OptionalInt.empty();

		private Draft(int position, CodefCardLink card, CodefUsedDateTime usedAt, CodefCancellationForm form,
				Optional<CodefApprovalFingerprint> fingerprint, long usedAmount) {
			this.position = position;
			this.card = card;
			this.usedAt = usedAt;
			this.form = form;
			this.fingerprint = fingerprint;
			this.usedAmount = usedAmount;
		}

		private boolean accepted() {
			return reason == null;
		}

		private void reject(CodefIncompleteReason incomplete) {
			if (reason == null) {
				reason = incomplete;
			}
		}

		private CodefLedgerCandidateRow toRow() {
			Decision decision = accepted() ? new Decision.Accepted(kind, key, recheck, pairedPosition)
					: new Decision.Incomplete(reason);
			return new CodefLedgerCandidateRow(position, card, usedAt, form, decision);
		}

	}

}
