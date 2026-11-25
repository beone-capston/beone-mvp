package com.beone.api.codef.sync;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 승인내역 응답 하나의 CODEF 원장 후보 판정 결과. 행 순서는 응답 순서와 같다.
 *
 * <p>
 * 원장이 "완전"하다고 선언하지 않는다(R7). 불완전 행만 알려 준다. 저장·도메인 원장 연결은 하지 않는다(R8).
 *
 * @param rows 행마다의 판정
 */
public record CodefLedgerCandidates(List<CodefLedgerCandidateRow> rows) {

	/**
	 * 행을 불변 복사한다.
	 *
	 * @throws NullPointerException 값이 null인 경우
	 */
	public CodefLedgerCandidates {
		rows = List.copyOf(rows);
	}

	/**
	 * R1로 보유카드에 붙은 불완전 행의 표시 범위. 카드 미연결 행은 어느 카드에도 표시하지 않으므로 여기에 없다.
	 *
	 * @return 응답 순서대로의 표시 범위
	 */
	public List<CodefIncompleteMark> incompleteMarks() {
		return rows.stream()
			.filter(row -> row.decision() instanceof CodefLedgerCandidateRow.Decision.Incomplete
					&& row.card() instanceof CodefCardLink.Linked)
			.map(row -> new CodefIncompleteMark(((CodefCardLink.Linked) row.card()).cardName(), row.usedAt().date(),
					((CodefLedgerCandidateRow.Decision.Incomplete) row.decision()).reason(), row.position()))
			.toList();
	}

	/**
	 * 받아들인 행의 키와 종류. 다음 재조회의 이전 기록으로 쓸 수 있다. 저장은 호출자가 한다.
	 *
	 * @return 응답 순서를 유지한 불변 맵
	 */
	public Map<CodefApprovalKey, CodefLedgerRowKind> acceptedKinds() {
		Map<CodefApprovalKey, CodefLedgerRowKind> kinds = new LinkedHashMap<>();
		for (CodefLedgerCandidateRow row : rows) {
			if (row.decision() instanceof CodefLedgerCandidateRow.Decision.Accepted accepted) {
				kinds.put(accepted.key(), accepted.kind());
			}
		}
		return Collections.unmodifiableMap(kinds);
	}

	/**
	 * 위치로 행을 찾는다.
	 *
	 * @param position 응답 안의 위치
	 * @return 그 위치의 행
	 * @throws IndexOutOfBoundsException 범위 밖인 경우
	 */
	public CodefLedgerCandidateRow row(int position) {
		Objects.checkIndex(position, rows.size());
		return rows.get(position);
	}

}
