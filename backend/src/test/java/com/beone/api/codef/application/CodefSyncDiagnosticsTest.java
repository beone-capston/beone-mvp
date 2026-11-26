package com.beone.api.codef.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.OptionalInt;

import com.beone.api.codef.CodefAmount;
import com.beone.api.codef.CodefAmountShape;
import com.beone.api.codef.CodefApproval;
import com.beone.api.codef.CodefApproval.CancelStatus;
import com.beone.api.codef.CodefApproval.DomesticForeign;
import com.beone.api.codef.CodefApproval.PaymentType;
import com.beone.api.codef.CodefCancellationForm;
import com.beone.api.codef.CodefCardResponseParser;
import com.beone.api.codef.CodefList;
import com.beone.api.codef.sync.CodefApprovalFingerprint;
import com.beone.api.codef.sync.CodefApprovalKey;
import com.beone.api.codef.sync.CodefCardLink;
import com.beone.api.codef.sync.CodefIncompleteDetail;
import com.beone.api.codef.sync.CodefIncompleteReason;
import com.beone.api.codef.sync.CodefLedgerCandidateRow;
import com.beone.api.codef.sync.CodefLedgerCandidateRow.Decision;
import com.beone.api.codef.sync.CodefLedgerRowKind;
import com.beone.api.codef.sync.CodefRecheck;
import com.beone.api.codef.sync.CodefUsedDateTime;
import org.junit.jupiter.api.Test;

/**
 * 동기화 진단 로그 한 줄(#270). 승인 불완전 행의 실제 분기별 개수, 미연결 카드명 고유값·개수를 담고, 카드번호·승인번호·금액·일시는 담지 않으며 카드명은 형식을
 * 제한한다. 카드명은 공개 상품명이고 값은 합성이다.
 */
class CodefSyncDiagnosticsTest {

	private static final String NORI = "nori 체크카드(RF)";

	private static final CodefUsedDateTime USED_AT = CodefUsedDateTime.parse(Optional.of("20261015"),
			Optional.of("101500"));

	private static final String FINGERPRINT = "a".repeat(64);

	private static CodefLedgerCandidateRow accepted(int position, String heldCardName) {
		CodefApprovalKey key = new CodefApprovalKey(heldCardName, new CodefApprovalFingerprint(FINGERPRINT),
				USED_AT.date().orElseThrow(), USED_AT.time().orElseThrow(), 4321L);
		return new CodefLedgerCandidateRow(position, new CodefCardLink.Linked(heldCardName), USED_AT,
				new CodefCancellationForm.NormalRow(),
				new Decision.Accepted(CodefLedgerRowKind.APPROVAL, key, CodefRecheck.NEW, OptionalInt.empty()),
				Optional.empty());
	}

	private static CodefLedgerCandidateRow unlinked(int position, CodefCardLink.Reason reason) {
		return new CodefLedgerCandidateRow(position, new CodefCardLink.NotLinked(reason), USED_AT,
				new CodefCancellationForm.NormalRow(), new Decision.Incomplete(CodefIncompleteReason.UNDETERMINED_ROW),
				Optional.of(CodefIncompleteDetail.CARD_NOT_LINKED));
	}

	private static CodefLedgerCandidateRow incomplete(int position, CodefIncompleteDetail detail) {
		return new CodefLedgerCandidateRow(position, new CodefCardLink.Linked(NORI), USED_AT,
				new CodefCancellationForm.NormalRow(), new Decision.Incomplete(CodefIncompleteReason.UNDETERMINED_ROW),
				Optional.of(detail));
	}

	private static CodefApproval approval(CodefAmount used, CodefAmountShape usedShape, CodefAmount canceled,
			CodefAmountShape cancelShape) {
		return new CodefApproval(Optional.empty(), Optional.empty(), Optional.of(NORI), Optional.empty(),
				Optional.empty(), used, new CodefAmount.Absent(), Optional.empty(), PaymentType.UNKNOWN,
				DomesticForeign.UNKNOWN, CancelStatus.NORMAL, canceled, Optional.empty(), usedShape, cancelShape);
	}

	@Test
	void amountShapesAreCountedPerBranchAndField() {
		CodefAmountShape comma = new CodefAmountShape("digits+comma", "4-6");
		CodefAmountShape plain = new CodefAmountShape("digits", "4-6");
		CodefSyncDiagnostics diagnostics = new CodefSyncDiagnostics();
		// 이용금액 해석 실패 3행(쉼표 2, JSON 숫자 1), 취소금액만 해석 실패 1행, 둘 다 실패 1행
		diagnostics.approval(approval(new CodefAmount.Unparsed(), comma, new CodefAmount.Absent(),
				new CodefAmountShape("empty", "0")), incomplete(0, CodefIncompleteDetail.FORM_AMOUNT_NOT_PARSED), false);
		diagnostics.approval(approval(new CodefAmount.Unparsed(), new CodefAmountShape("digits+comma", "7-9"),
				new CodefAmount.Absent(), new CodefAmountShape("missing", "none")),
				incomplete(1, CodefIncompleteDetail.FORM_AMOUNT_NOT_PARSED), false);
		diagnostics.approval(approval(new CodefAmount.Absent(), new CodefAmountShape("jsonNumber+digits", "4-6"),
				new CodefAmount.Absent(), new CodefAmountShape("missing", "none")),
				incomplete(2, CodefIncompleteDetail.FORM_AMOUNT_NOT_PARSED), false);
		diagnostics.approval(approval(new CodefAmount.Parsed(5_000), plain, new CodefAmount.Unparsed(),
				new CodefAmountShape("digits+dot", "4-6")), incomplete(3, CodefIncompleteDetail.FORM_AMOUNT_NOT_PARSED),
				false);
		diagnostics.approval(approval(new CodefAmount.Unparsed(), new CodefAmountShape("digits+currency", "1-3"),
				new CodefAmount.Unparsed(), new CodefAmountShape("other", "10+")),
				incomplete(4, CodefIncompleteDetail.FORM_AMOUNT_NOT_PARSED), false);
		diagnostics.approval(approval(new CodefAmount.Absent(), new CodefAmountShape("empty", "0"),
				new CodefAmount.Absent(), new CodefAmountShape("missing", "none")),
				incomplete(5, CodefIncompleteDetail.KRW_USED_AMOUNT_NOT_PARSED), false);
		// 금액 계열이 아닌 분기·건너뛴 카드 행은 형태를 세지 않는다
		diagnostics.approval(approval(new CodefAmount.Unparsed(), comma, new CodefAmount.Absent(),
				new CodefAmountShape("empty", "0")), unlinked(6, CodefCardLink.Reason.ROW_NAME_NOT_IN_DICTIONARY),
				false);
		diagnostics.approval(approval(new CodefAmount.Unparsed(), comma, new CodefAmount.Absent(),
				new CodefAmountShape("empty", "0")), accepted(7, NORI), true);
		diagnostics.approvalsJudged(0);

		assertThat(diagnostics.line(4)).isEqualTo("codef sync diagnostics syncId=4 approvals={rows=8, imported=0, "
				+ "linkedByDictionary=0, incomplete={CARD_NOT_LINKED.ROW_NAME_NOT_IN_DICTIONARY=1, "
				+ "FORM_AMOUNT_NOT_PARSED=5, HELD_CARD_SKIPPED=1, KRW_USED_AMOUNT_NOT_PARSED=1}, "
				+ "unlinkedCardNames={\"nori 체크카드(RF)\"=2}, amountShapes={"
				+ "FORM_AMOUNT_NOT_PARSED.resCancelAmount={form={digits+dot=1, other=1}, length={4-6=1, 10+=1}}, "
				+ "FORM_AMOUNT_NOT_PARSED.resUsedAmount={form={digits+comma=2, digits+currency=1, jsonNumber+digits=1}, "
				+ "length={1-3=1, 4-6=2, 7-9=1}}, "
				+ "KRW_USED_AMOUNT_NOT_PARSED.resUsedAmount={form={empty=1}, length={0=1}}}} performances=NOT_FETCHED");
	}

	@Test
	void lineWithoutAmountRowsKeepsThePreviousFormat() {
		CodefSyncDiagnostics diagnostics = new CodefSyncDiagnostics();
		diagnostics.approval(approval(new CodefAmount.Parsed(5_000), CodefAmountShape.NOT_CAPTURED,
				new CodefAmount.Absent(), CodefAmountShape.NOT_CAPTURED),
				incomplete(0, CodefIncompleteDetail.KRW_CURRENCY_NOT_KRW), false);
		diagnostics.approvalsJudged(0);

		assertThat(diagnostics.line(2)).isEqualTo("codef sync diagnostics syncId=2 approvals={rows=1, imported=0, "
				+ "linkedByDictionary=0, incomplete={KRW_CURRENCY_NOT_KRW=1}, unlinkedCardNames={}} "
				+ "performances=NOT_FETCHED");
	}

	@Test
	void amountTextsNeverReachTheLine() {
		// 파서를 거친 합성 금액 원문. 형태만 남고 원문·숫자는 줄에 없어야 한다
		String body = """
				{"result":{"code":"CF-00000","transactionId":"SYN-TX"},"data":[
				{"resUsedAmount":"SYN-9,876.5원","resCancelYN":"0"},
				{"resUsedAmount":"7,654","resCancelYN":"0"},
				{"resUsedAmount":"4321","resCancelYN":"1","resCancelAmount":"KRW 3210"}]}
				""";
		java.util.List<CodefApproval> approvals = ((CodefList.Items<CodefApproval>) CodefCardResponseParser
			.parseApprovals(body)
			.data()).items();
		CodefSyncDiagnostics diagnostics = new CodefSyncDiagnostics();
		for (int index = 0; index < approvals.size(); index++) {
			diagnostics.approval(approvals.get(index), incomplete(index, CodefIncompleteDetail.FORM_AMOUNT_NOT_PARSED),
					false);
		}
		diagnostics.approvalsJudged(0);

		String line = diagnostics.line(8);

		assertThat(line).contains("FORM_AMOUNT_NOT_PARSED.resUsedAmount={form={digits+comma=1, "
				+ "digits+comma+minus+dot+currency+other=1}, length={4-6=1, 10+=1}}")
			.contains("FORM_AMOUNT_NOT_PARSED.resCancelAmount={form={digits+currency+space=1}, length={7-9=1}}");
		for (String forbidden : new String[] { "SYN", "9,876", "876", "7,654", "654", "4321", "3210", "KRW", "원" }) {
			assertThat(line).doesNotContain(forbidden);
		}
	}

	@Test
	void lineCountsIncompleteBranchesAndUnlinkedNames() {
		CodefSyncDiagnostics diagnostics = new CodefSyncDiagnostics();
		diagnostics.approval(Optional.of(NORI), accepted(0, NORI), false);
		diagnostics.approval(Optional.of("KB국민 노리체크카드"), accepted(1, NORI), false);
		diagnostics.approval(Optional.of("KB Youth Club 체크카드"),
				unlinked(2, CodefCardLink.Reason.ROW_NAME_NOT_IN_DICTIONARY), false);
		diagnostics.approval(Optional.of("KB Youth Club 체크카드"),
				unlinked(3, CodefCardLink.Reason.ROW_NAME_NOT_IN_DICTIONARY), false);
		diagnostics.approval(Optional.of("첵첵 체크카드"), unlinked(4, CodefCardLink.Reason.NO_HELD_CARD_FOR_PRODUCT),
				false);
		diagnostics.approval(Optional.empty(), unlinked(5, CodefCardLink.Reason.ROW_CARD_NAME_ABSENT), false);
		diagnostics.approval(Optional.of(NORI), incomplete(6, CodefIncompleteDetail.KRW_CURRENCY_NOT_KRW), false);
		diagnostics.approval(Optional.of("건너뛴 카드"), accepted(7, "건너뛴 카드"), true);
		diagnostics.approvalsJudged(2);
		diagnostics.performancesJudged();
		diagnostics.performance(Optional.of("KB국민 노리체크카드"), new CodefCardLink.Linked(NORI), true);
		diagnostics.performance(Optional.of("KB Youth Club 체크카드"),
				new CodefCardLink.NotLinked(CodefCardLink.Reason.ROW_NAME_NOT_IN_DICTIONARY), true);

		assertThat(diagnostics.line(41)).isEqualTo("codef sync diagnostics syncId=41 approvals={rows=8, imported=2, "
				+ "linkedByDictionary=1, incomplete={CARD_NOT_LINKED.NO_HELD_CARD_FOR_PRODUCT=1, "
				+ "CARD_NOT_LINKED.ROW_CARD_NAME_ABSENT=1, CARD_NOT_LINKED.ROW_NAME_NOT_IN_DICTIONARY=2, "
				+ "HELD_CARD_SKIPPED=1, KRW_CURRENCY_NOT_KRW=1}, "
				+ "unlinkedCardNames={\"KB Youth Club 체크카드\"=2, \"건너뛴 카드\"=1, \"첵첵 체크카드\"=1}} "
				+ "performances={cards=2, linkedByDictionary=1, incomplete={CARD_NOT_LINKED.ROW_NAME_NOT_IN_DICTIONARY=1}, "
				+ "unlinkedCardNames={\"KB Youth Club 체크카드\"=1}}");
	}

	@Test
	void namesThatLookTheSameAfterSanitizingStaySeparate() {
		// 원본으로 세고 출력할 때만 바꾸므로, 앞 40자가 같은 서로 다른 이름과 "A\nB"·"A?B"는 합쳐지지 않는다(#272)
		String prefix = "가".repeat(CodefSyncDiagnostics.NAME_MAX_CODE_POINTS);
		CodefSyncDiagnostics diagnostics = new CodefSyncDiagnostics();
		CodefCardLink notLinked = new CodefCardLink.NotLinked(CodefCardLink.Reason.ROW_NAME_NOT_IN_DICTIONARY);
		diagnostics.performance(Optional.of(prefix + "하나"), notLinked, true);
		diagnostics.performance(Optional.of(prefix + "둘"), notLinked, true);
		diagnostics.performance(Optional.of("A\nB"), notLinked, true);
		diagnostics.performance(Optional.of("A?B"), notLinked, true);
		diagnostics.performance(Optional.of("A?B"), notLinked, true);
		diagnostics.performancesJudged();

		String line = diagnostics.line(9);

		// 개수 내림차순, 같으면 원본 카드명 순서. 원본 "A\nB"('\n'=10)는 한글로 시작하는 두 이름보다 앞이다
		assertThat(line).contains("unlinkedCardNames={\"A?B\"=2, \"A?B\"=1, \"" + prefix + "...\"=1, \"" + prefix
				+ "...\"=1}");
	}

	@Test
	void namesOverTheCapAreChosenByTheOriginalNames() {
		// 상한 밖으로 밀리는 항목도 원본 기준으로 정해진다: 표시가 같아도 원본이 다르면 각각 센다
		CodefSyncDiagnostics diagnostics = new CodefSyncDiagnostics();
		CodefCardLink notLinked = new CodefCardLink.NotLinked(CodefCardLink.Reason.ROW_NAME_NOT_IN_DICTIONARY);
		for (int index = 0; index < CodefSyncDiagnostics.NAMES_MAX; index++) {
			diagnostics.performance(Optional.of("X\u0000" + (char) ('a' + index)), notLinked, true);
		}
		diagnostics.performance(Optional.of("X?a"), notLinked, true);
		diagnostics.performancesJudged();

		// 원본 21개가 모두 다르므로 하나가 상한 밖이다. "X?a"('?'=63)는 "X\u0000…"(0)보다 뒤라 밀려난다
		assertThat(diagnostics.line(10)).contains(", +1 more}").contains("\"X?a\"=1");
	}

	@Test
	void linkedPerformanceWithoutAHeldCardIdIsCountedAsSkipped() {
		// 연결기는 보유카드 이름을 줬지만 그 보유카드가 동기화에서 건너뛰어져 보유 카드 ID가 없다(#272)
		CodefSyncDiagnostics diagnostics = new CodefSyncDiagnostics();
		diagnostics.performance(Optional.of("KB국민 노리체크카드"), new CodefCardLink.Linked(NORI), false);
		diagnostics.performance(Optional.of(NORI), new CodefCardLink.Linked(NORI), false);
		diagnostics.performance(Optional.of(NORI), new CodefCardLink.Linked(NORI), true);
		diagnostics.performancesJudged();

		assertThat(diagnostics.line(11)).isEqualTo("codef sync diagnostics syncId=11 approvals=NOT_FETCHED "
				+ "performances={cards=3, linkedByDictionary=0, incomplete={HELD_CARD_SKIPPED=2}, "
				+ "unlinkedCardNames={\"KB국민 노리체크카드\"=1, \"nori 체크카드(RF)\"=1}}");
	}

	@Test
	void skippedApprovalRowIsNotCountedAsLinked() {
		CodefSyncDiagnostics diagnostics = new CodefSyncDiagnostics();
		diagnostics.approval(Optional.of("KB국민 노리체크카드"), accepted(0, NORI), true);
		diagnostics.approvalsJudged(0);

		assertThat(diagnostics.line(12)).contains("approvals={rows=1, imported=0, linkedByDictionary=0, "
				+ "incomplete={HELD_CARD_SKIPPED=1}, unlinkedCardNames={\"KB국민 노리체크카드\"=1}}");
	}

	@Test
	void targetsThatWereNotJudgedAreMarked() {
		assertThat(new CodefSyncDiagnostics().line(7))
			.isEqualTo("codef sync diagnostics syncId=7 approvals=NOT_FETCHED performances=NOT_FETCHED");
	}

	@Test
	void lineCarriesNoAmountFingerprintOrDateTime() {
		CodefSyncDiagnostics diagnostics = new CodefSyncDiagnostics();
		diagnostics.approval(Optional.of(NORI), accepted(0, NORI), false);
		diagnostics.approval(Optional.of(NORI), incomplete(1, CodefIncompleteDetail.USED_DATE_TIME_NOT_PARSED), false);
		diagnostics.approvalsJudged(1);

		String line = diagnostics.line(3);

		assertThat(line).doesNotContain(FINGERPRINT).doesNotContain("4321").doesNotContain("20261015")
			.doesNotContain("2026-10-15").doesNotContain("10:15").doesNotContain("101500");
		// 연결된 행의 카드명은 남기지 않는다
		assertThat(line).doesNotContain(NORI);
	}

	@Test
	void cardNamesAreSanitizedAndCapped() {
		assertThat(CodefSyncDiagnostics.safeName("카드\n가짜 로그 줄")).isEqualTo("카드?가짜 로그 줄");
		assertThat(CodefSyncDiagnostics.safeName("a\"b\\c\u0000d‮e f\tg")).isEqualTo("a?b?c?d?e?f?g");
		assertThat(CodefSyncDiagnostics.safeName("가".repeat(45))).isEqualTo("가".repeat(40) + "...");
		assertThat(CodefSyncDiagnostics.safeName("KB국민 노리체크카드")).isEqualTo("KB국민 노리체크카드");

		CodefSyncDiagnostics diagnostics = new CodefSyncDiagnostics();
		for (int index = 0; index < CodefSyncDiagnostics.NAMES_MAX + 3; index++) {
			diagnostics.performance(Optional.of("카드" + (char) ('A' + index)),
					new CodefCardLink.NotLinked(CodefCardLink.Reason.ROW_NAME_NOT_IN_DICTIONARY), true);
		}
		diagnostics.performance(Optional.of("주입\r\ncodef sync diagnostics syncId=999"),
				new CodefCardLink.NotLinked(CodefCardLink.Reason.ROW_NAME_NOT_IN_DICTIONARY), true);
		diagnostics.performancesJudged();

		String line = diagnostics.line(5);
		assertThat(line).doesNotContain("\n").doesNotContain("\r").contains(", +4 more}");
		// 줄바꿈이 치환되어 끼워 넣은 문장은 같은 줄의 따옴표 안 카드명으로만 남는다
		assertThat(line).startsWith("codef sync diagnostics syncId=5 ")
			.contains("\"주입??codef sync diagnostics syncId=999\"=1");
	}

}
