package com.beone.api.codef.application;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.beone.api.codef.sync.CodefCardLink;
import com.beone.api.codef.sync.CodefIncompleteDetail;
import com.beone.api.codef.sync.CodefLedgerCandidateRow;

/**
 * 동기화 한 번의 진단 집계(#270, 2026-11-27 사용자 결정). 동기화가 끝나면 INFO 로그 한 줄로 남긴다.
 *
 * <p>담는 것: 승인 행 수·받아들인 수, 불완전 행의 실제 판정 분기별 개수(카드 미연결은 연결하지 않은 이유별), 이름 사전으로 연결한 행 수, 미연결 행의 카드명
 * 고유값과 개수. 실적도 같은 방식이다. 카드명은 공개 상품명이라 남기되, 로그 주입을 막으려고 제어·서식 문자와 따옴표·역슬래시를 {@code ?}로 바꾸고
 * {@value #NAME_MAX_CODE_POINTS}자에서 자르며, 고유값은 {@value #NAMES_MAX}개까지만 적는다. 고유값은 원본 카드명으로 세고 정렬·상한도 원본 기준이며,
 * 바꾸기는 출력할 때만 한다. 그래서 표시 문자열이 같아 보여도 원본이 다르면 다른 항목이다(#272).
 *
 * <p>연결기가 보유카드 이름을 돌려줬어도 그 보유카드 ID가 없으면(동기화에서 건너뛴 보유카드) 실제로 붙지 않았으므로 미연결로 세고 사유는
 * {@value #HELD_CARD_SKIPPED}다. 승인과 실적이 같다(#272).
 *
 * <p>담지 않는 것: 카드번호, 승인번호(원문·지문), 금액, 가맹점, 사용일시, connectedId, 사용자 ID. API 응답의 공개 사유 코드는 바꾸지 않는다.
 */
final class CodefSyncDiagnostics {

	static final int NAME_MAX_CODE_POINTS = 40;

	static final int NAMES_MAX = 20;

	/** 건너뛴 CODEF 카드(Q2)에 붙은 행. 판정은 받아들였지만 붙일 보유 카드가 없다. */
	static final String HELD_CARD_SKIPPED = "HELD_CARD_SKIPPED";

	private final Part approvals = new Part();

	private final Part performances = new Part();

	private boolean approvalsFetched;

	private boolean performancesFetched;

	private int approvalsImported;

	/**
	 * 승인내역을 판정했음을 알린다. 부르지 않으면 로그에 {@code NOT_FETCHED}로 남는다.
	 * @param imported 새로 넣거나 전액 취소로 바꾼 행 수
	 */
	void approvalsJudged(int imported) {
		this.approvalsFetched = true;
		this.approvalsImported = imported;
	}

	/**
	 * 승인 행 하나를 센다.
	 * @param rowCardName 승인 행의 카드명
	 * @param row 판정 결과
	 * @param heldCardSkipped 받아들였지만 붙일 보유 카드가 없어 불완전으로 둔 행인지
	 */
	void approval(Optional<String> rowCardName, CodefLedgerCandidateRow row, boolean heldCardSkipped) {
		this.approvals.rows++;
		this.approvals.countLink(rowCardName, row.card(), !heldCardSkipped);
		if (heldCardSkipped) {
			this.approvals.incomplete.merge(HELD_CARD_SKIPPED, 1, Integer::sum);
			return;
		}
		row.incompleteDetail().ifPresent(detail -> this.approvals.incomplete.merge(key(detail, row.card()), 1,
				Integer::sum));
	}

	/** 실적을 판정했음을 알린다. 부르지 않으면 로그에 {@code NOT_FETCHED}로 남는다. */
	void performancesJudged() {
		this.performancesFetched = true;
	}

	/**
	 * 실적 참고값 하나(카드 한 장)를 센다.
	 * @param rowCardName 실적의 카드명
	 * @param link 연결 결과
	 * @param heldCardFound 연결된 보유카드의 보유 카드 ID가 있는지. 미연결이면 의미 없다
	 */
	void performance(Optional<String> rowCardName, CodefCardLink link, boolean heldCardFound) {
		this.performances.rows++;
		boolean attached = link instanceof CodefCardLink.Linked && heldCardFound;
		this.performances.countLink(rowCardName, link, attached);
		if (link instanceof CodefCardLink.NotLinked notLinked) {
			this.performances.incomplete.merge("CARD_NOT_LINKED." + notLinked.reason(), 1, Integer::sum);
		}
		else if (!attached) {
			this.performances.incomplete.merge(HELD_CARD_SKIPPED, 1, Integer::sum);
		}
	}

	/**
	 * 로그 한 줄을 만든다.
	 * @param syncId 동기화 ID
	 * @return 로그 문장
	 */
	String line(long syncId) {
		String approvalPart = this.approvalsFetched
				? "{rows=" + this.approvals.rows + ", imported=" + this.approvalsImported + ", " + this.approvals.describe()
						+ "}"
				: "NOT_FETCHED";
		String performancePart = this.performancesFetched
				? "{cards=" + this.performances.rows + ", " + this.performances.describe() + "}" : "NOT_FETCHED";
		return "codef sync diagnostics syncId=" + syncId + " approvals=" + approvalPart + " performances="
				+ performancePart;
	}

	private static String key(CodefIncompleteDetail detail, CodefCardLink card) {
		if (detail == CodefIncompleteDetail.CARD_NOT_LINKED && card instanceof CodefCardLink.NotLinked notLinked) {
			return detail + "." + notLinked.reason();
		}
		return detail.name();
	}

	/**
	 * 카드명을 로그에 안전한 형태로 바꾼다. 제어·서식·줄 구분 문자와 짝 없는 서로게이트, 따옴표·역슬래시는 {@code ?}로 바꾸고 길이를 자른다.
	 * @param name 카드명
	 * @return 바꾼 카드명
	 */
	static String safeName(String name) {
		StringBuilder safe = new StringBuilder();
		int[] codePoints = name.codePoints().toArray();
		int limit = Math.min(codePoints.length, NAME_MAX_CODE_POINTS);
		for (int index = 0; index < limit; index++) {
			int codePoint = codePoints[index];
			safe.appendCodePoint(unsafe(codePoint) ? '?' : codePoint);
		}
		if (codePoints.length > NAME_MAX_CODE_POINTS) {
			safe.append("...");
		}
		return safe.toString();
	}

	private static boolean unsafe(int codePoint) {
		int type = Character.getType(codePoint);
		return codePoint == '"' || codePoint == '\\' || type == Character.CONTROL || type == Character.FORMAT
				|| type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR
				|| type == Character.SURROGATE || type == Character.UNASSIGNED || type == Character.PRIVATE_USE;
	}

	/** 승인 또는 실적 한쪽의 집계. */
	private static final class Part {

		private int rows;

		private int linkedByDictionary;

		private final Map<String, Integer> incomplete = new TreeMap<>();

		private final Map<String, Integer> unlinkedNames = new TreeMap<>();

		// attached: 연결됐고 붙일 보유 카드 ID도 있는지. 아니면 미연결로 센다
		private void countLink(Optional<String> rowCardName, CodefCardLink link, boolean attached) {
			if (attached && link instanceof CodefCardLink.Linked linked) {
				// 정확 일치면 행 이름과 연결된 보유카드 이름이 같다. 다르면 이름 사전으로 연결한 것이다
				if (rowCardName.filter(name -> !name.equals(linked.cardName())).isPresent()) {
					this.linkedByDictionary++;
				}
				return;
			}
			// 원본 카드명으로 센다. 로그용 바꾸기(safeName)는 출력할 때만 한다
			rowCardName.ifPresent(name -> this.unlinkedNames.merge(name, 1, Integer::sum));
		}

		private String describe() {
			List<Map.Entry<String, Integer>> names = this.unlinkedNames.entrySet()
				.stream()
				.sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
					.thenComparing(Map.Entry.comparingByKey()))
				.toList();
			String shown = names.stream()
				.limit(NAMES_MAX)
				.map(entry -> "\"" + safeName(entry.getKey()) + "\"=" + entry.getValue())
				.collect(Collectors.joining(", "));
			String more = names.size() > NAMES_MAX ? ", +" + (names.size() - NAMES_MAX) + " more" : "";
			return "linkedByDictionary=" + this.linkedByDictionary + ", incomplete=" + this.incomplete
					+ ", unlinkedCardNames={" + shown + more + "}";
		}

	}

}
