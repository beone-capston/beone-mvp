package com.beone.api.codef;

import java.util.Objects;

/**
 * 승인내역 한 건이 공급자가 설명한 취소 표현 중 어느 형태와 맞는지에 대한 기술(#45).
 *
 * <p>
 * 근거는 카드 명세 시트 {@code 참고 승인내역 취소건}의 C3:D9·C4/D4 주석·B14:K15와 {@code 승인내역}의 H42 주석·H51·B52다(공식 명세, 시트 B1은
 * "참고용이며 실제 응답과 일치하지 않을 수 있음"이라고 적는다). 형태 기술일 뿐이며 실승인금액·승인 건과 취소 건의 매칭·원장 반영·실적 귀속·한도
 * 복원의 근거가 아니다. 그래서 금액을 담지 않고, 금액을 계산하는 메서드도 두지 않는다.
 */
public sealed interface CodefCancellationForm {

	/**
	 * type1 취소 형태와 일치: 한 건에 양수 이용금액과 같은 금액의 취소금액이 함께 나오고 취소여부가 "1"이다(취소건!C14·I14, C6 "취소인 경우
	 * 승인금액"). KB에서는 승인취소가 type1이다(취소건!C3·C9).
	 */
	record Type1Canceled() implements CodefCancellationForm {
	}

	/**
	 * type2 취소 행 형태와 일치: 이용금액이 음수이고 취소금액이 빈 값이며 취소여부가 "1"이다(취소건!C15·I15, 승인내역!B52). KB에서는
	 * 취소전표매입이 type2다(취소건!D3·D5·D9). 같은 거래의 승인 행은 따로 있으며 이 단계는 둘을 짝짓지 않는다.
	 */
	record Type2CancelRow() implements CodefCancellationForm {
	}

	/**
	 * 정상 행 형태와 일치: 취소여부 "0", 양수 이용금액, 빈 취소금액(취소건!I15의 승인건). 정상 행만으로는 type1(전표미매입)과 type2(전표매입)를
	 * 구분할 수 없다(취소건!C4 주석).
	 */
	record NormalRow() implements CodefCancellationForm {
	}

	/**
	 * 판단 불가. 이유만 남긴다.
	 *
	 * @param reason 판단할 수 없는 이유
	 */
	record Undetermined(Reason reason) implements CodefCancellationForm {

		/**
		 * null을 거부한다.
		 *
		 * @throws NullPointerException 이유가 null인 경우
		 */
		public Undetermined {
			Objects.requireNonNull(reason, "reason");
		}

	}

	/** 그 기관의 취소 표현 근거를 아직 정리하지 않았다. KB(기관코드 {@code 0301})만 정리했다. */
	record OrganizationNotDocumented() implements CodefCancellationForm {
	}

	/** 판단할 수 없는 이유. */
	enum Reason {

		/** 취소여부가 값 없음이거나 문서의 값 목록 밖이다. */
		CANCEL_STATUS_UNKNOWN,

		/** 이용금액이 없거나 해석되지 않았거나, 취소금액이 해석되지 않았다. */
		AMOUNT_NOT_PARSED,

		/** KB는 부분취소를 구분하지 않는다고 설명되어 있는데 부분취소("2")가 왔다(취소건!C7·D7 "X", 승인내역!H42 주석). */
		PARTIAL_CANCEL_NOT_DISTINGUISHED,

		/** 취소 표현 설명에 없는 취소여부다(거절 "3"). */
		STATUS_NOT_DESCRIBED,

		/** 취소여부와 금액 모양의 조합이 문서 예시와 다르다(예: 음수 이용금액인데 정상 "0"). */
		SHAPE_MISMATCH

	}

}
