package com.beone.api.codef.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.beone.api.card.application.UserCardId;
import com.beone.api.codef.application.CodefSyncResult;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * {@code POST /api/v1/me/codef/syncs} 응답 {@code data}(docs/api/codef.md). 값이 없는 필드도 {@code null}로 내보낸다. 실패한 대상의 건수는
 * {@code null}이며 0건과 구분한다. 카드번호·승인번호 원문은 담지 않는다.
 * @param syncId 동기화 ID({@code sync-} 접두어)
 * @param syncedAt 동기화 시각
 * @param heldCards 보유카드 결과
 * @param approvals 승인내역 결과
 * @param performances 카드사 조회 실적(참고)
 * @param partialFailures 실패한 대상
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CodefSyncResponse(String syncId, String syncedAt, HeldCards heldCards, Approvals approvals,
		List<Performance> performances, List<PartialFailure> partialFailures) {

	/** 새 CODEF 카드의 매핑 상태. 상품 후보를 정하지 않는다(결정 20 R3, 관리자 판단 K1). */
	static final String UNMAPPED = "UNMAPPED";

	/** 실적 참고값 표시 이름(D-16). */
	static final String PERFORMANCE_LABEL = "카드사 조회 실적(참고)";

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	// Jackson 기본 출력은 초가 0이면 초를 생략하므로 직접 만든다(docs/api.md 값 형식).
	private static final DateTimeFormatter INSTANT = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssXXX");

	static CodefSyncResponse from(CodefSyncResult result) {
		CodefSyncResult.HeldCards held = result.heldCards();
		CodefSyncResult.Approvals approvals = result.approvals();
		return new CodefSyncResponse("sync-" + result.syncId(), seoulInstant(result.syncedAt()),
				new HeldCards(held.fetched(),
						held.newCandidates()
							.stream()
							.map(card -> new NewCandidate(userCardId(card.userCardId()), card.cardName(), null,
									UNMAPPED))
							.toList()),
				new Approvals(approvals.imported(), approvals.skippedDuplicates(),
						approvals.incompleteRows()
							.stream()
							.map(row -> new IncompleteRow(userCardId(row.userCardId()), row.approvalNoPresent(),
									row.reason().name()))
							.toList()),
				result.performances()
					.stream()
					.map(item -> new Performance(userCardId(item.userCardId()), PERFORMANCE_LABEL,
							new Period(date(item.periodFrom()), date(item.periodTo())), item.currentUseWon(),
							seoulInstant(item.fetchedAt()), false))
					.toList(),
				result.partialFailures()
					.stream()
					.map(target -> new PartialFailure(target.name(), CodefErrorCode.CODEF_UNAVAILABLE.code(),
							CodefErrorCode.CODEF_UNAVAILABLE.message()))
					.toList());
	}

	private static String userCardId(Long id) {
		return (id == null) ? null : UserCardId.format(id);
	}

	private static String date(LocalDate value) {
		return (value == null) ? null : value.toString();
	}

	private static String seoulInstant(LocalDateTime seoulTime) {
		return INSTANT.format(seoulTime.atZone(SEOUL));
	}

	/**
	 * 보유카드 결과.
	 * @param fetched 응답 카드 수. 조회 실패면 {@code null}
	 * @param newCandidates 새로 만든 보유 카드
	 */
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record HeldCards(Integer fetched, List<NewCandidate> newCandidates) {
	}

	/**
	 * 새로 만든 보유 카드.
	 * @param userCardId 보유 카드 ID
	 * @param cardName CODEF 카드명
	 * @param candidateCardProductId 상품 후보. 항상 {@code null}(R3)
	 * @param mappingStatus 항상 {@code UNMAPPED}
	 */
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record NewCandidate(String userCardId, String cardName, String candidateCardProductId,
			String mappingStatus) {
	}

	/**
	 * 승인내역 결과.
	 * @param imported 새로 기록했거나 전액 취소로 갱신한 행 수. 조회 실패면 {@code null}
	 * @param skippedDuplicates 이미 기록된 행 수. 조회 실패면 {@code null}
	 * @param incompleteRows 저장하지 않은 행
	 */
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record Approvals(Integer imported, Integer skippedDuplicates, List<IncompleteRow> incompleteRows) {
	}

	/**
	 * 저장하지 않은 승인 행.
	 * @param userCardId 붙은 보유 카드 ID. 없으면 {@code null}
	 * @param approvalNoPresent 응답에 승인번호가 있었는지
	 * @param reason 불완전 사유
	 */
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record IncompleteRow(String userCardId, boolean approvalNoPresent, String reason) {
	}

	/**
	 * 카드사 조회 실적(참고).
	 * @param userCardId 붙은 보유 카드 ID. 없으면 {@code null}
	 * @param label 표시 이름
	 * @param period 이용금액 반영기간
	 * @param currentUseWon 현재이용금액. 모르면 {@code null}
	 * @param fetchedAt 조회 시각
	 * @param usedInCalculation 항상 {@code false}(D-16)
	 */
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record Performance(String userCardId, String label, Period period, Long currentUseWon, String fetchedAt,
			boolean usedInCalculation) {
	}

	/**
	 * 기간. 모르는 날짜는 {@code null}이다.
	 * @param from 시작일
	 * @param to 종료일
	 */
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record Period(String from, String to) {
	}

	/**
	 * 부분 실패 항목.
	 * @param target 실패한 대상
	 * @param code 공개 오류 코드
	 * @param message 공개 문구
	 */
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record PartialFailure(String target, String code, String message) {
	}

}
