package com.beone.api.ledger.application;

import java.util.List;

/**
 * 원장 조회 결과(docs/api/ledger.md 응답 {@code data}). 외부 ID는 문자열로 바꿔 담는다.
 *
 * @param month 조회한 달({@code YYYY-MM})
 * @param items 원장 행. 거래일 오름차순, 같은 날은 {@code SIMULATION}(행 ID 순) → {@code CODEF}(사용일시·행 ID 순)
 * @param coverage 카드·출처별 완전성. 카드 순서대로, 같은 카드 안에서는 {@code SIMULATION} → {@code CODEF}. 카드 미연결 CODEF
 *     불완전 행이 있으면 끝에 {@code userCardId=null} 항목 하나
 */
public record LedgerMonthView(String month, List<Item> items, List<Coverage> coverage) {

	public LedgerMonthView {
		items = List.copyOf(items);
		coverage = List.copyOf(coverage);
	}

	/**
	 * 원장 행 한 건. 모르는 값은 {@code null}로 둔다(시각을 00:00으로 만들지 않음).
	 *
	 * @param ledgerTransactionId 원장 행 ID. 모의 행 {@code lt-}+번호, CODEF 행 {@code car-}+번호(#289)
	 * @param userCardId 보유 카드 ID({@code uc-}+번호)
	 * @param source {@code SIMULATION} / {@code CODEF}
	 * @param entryType {@code APPROVAL} / {@code CANCELLATION}
	 * @param amountWon 승인은 양수, 취소는 음수. 행 종류는 {@code entryType}으로 판단한다
	 * @param transactionDate 거래일 {@code YYYY-MM-DD}
	 * @param transactionTime 거래 시각 {@code HH:mm:ss}, 없으면 {@code null}
	 * @param merchantClass 가맹점 분류
	 * @param paymentMethod 결제 방식
	 * @param transactionNature {@code ORDINARY} / {@code TAX} / {@code null}. 1차 모의 행은 {@code null}
	 * @param merchantName 가맹점 이름, 없으면 {@code null}
	 * @param originalTransactionId 취소 행이 가리키는 원 승인 행 ID. 승인 행은 {@code null}
	 * @param simulatedPaymentId 모의 결제 ID. CODEF 행은 {@code null}
	 * @param attributionMonth 귀속월과 확정 여부
	 * @param syncId CODEF 행을 처음 저장한 동기화 ID({@code sync-}+번호). 모의 행은 {@code null}(#289)
	 * @param fullyCanceled CODEF 승인 행이 전액 취소(Type1, 승인·취소가 한 행)인지. 모의 행은 {@code null}(#289)
	 */
	public record Item(String ledgerTransactionId, String userCardId, String source, String entryType, long amountWon,
			String transactionDate, String transactionTime, String merchantClass, String paymentMethod,
			String transactionNature, String merchantName, String originalTransactionId, String simulatedPaymentId,
			AttributionMonth attributionMonth, String syncId, Boolean fullyCanceled) {

		/**
		 * 모의 행. CODEF 전용 필드는 {@code null}이다.
		 */
		public Item(String ledgerTransactionId, String userCardId, String source, String entryType, long amountWon,
				String transactionDate, String transactionTime, String merchantClass, String paymentMethod,
				String transactionNature, String merchantName, String originalTransactionId, String simulatedPaymentId,
				AttributionMonth attributionMonth) {
			this(ledgerTransactionId, userCardId, source, entryType, amountWon, transactionDate, transactionTime,
					merchantClass, paymentMethod, transactionNature, merchantName, originalTransactionId,
					simulatedPaymentId, attributionMonth, null, null);
		}

	}

	/**
	 * 행이 실적에 잡히는 달과 그 확정 여부.
	 *
	 * @param value {@code YYYY-MM}. 정하지 못했으면 {@code null}(CODEF 취소 행, D-17)
	 * @param status {@code CONFIRMED} / {@code NEEDS_CONFIRMATION}
	 */
	public record AttributionMonth(String value, String status) {
	}

	/**
	 * 한 카드·한 출처의 그 달 원장이 빠짐없이 확보됐는지. 출처를 합쳐 계산하지 않는다.
	 *
	 * @param userCardId 보유 카드 ID. 카드 미연결 CODEF 불완전 행 항목은 {@code null}
	 * @param source 출처
	 * @param period 판단한 기간
	 * @param complete 모든 거래를 확보했다고 선언할 수 있으면 {@code true}
	 * @param incompleteReasons 불완전 사유 코드. {@code SIMULATION}은 항상 비고, {@code CODEF}는 동기화 판정 사유와
	 *     {@code PERIOD_NOT_COVERED}(#289)
	 */
	public record Coverage(String userCardId, String source, Period period, boolean complete,
			List<String> incompleteReasons) {

		public Coverage {
			incompleteReasons = List.copyOf(incompleteReasons);
		}

	}

	/**
	 * 완전성을 판단한 기간(조회한 달의 1일~말일).
	 *
	 * @param from 시작일 {@code YYYY-MM-DD}
	 * @param to 끝일 {@code YYYY-MM-DD}
	 */
	public record Period(String from, String to) {
	}

}
