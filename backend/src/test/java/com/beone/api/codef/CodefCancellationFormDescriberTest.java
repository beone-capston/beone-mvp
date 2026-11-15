package com.beone.api.codef;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.beone.api.codef.CodefApproval.CancelStatus;
import com.beone.api.codef.CodefApproval.DomesticForeign;
import com.beone.api.codef.CodefApproval.PaymentType;
import com.beone.api.codef.CodefCancellationForm.Reason;

/**
 * 승인내역 한 건의 취소 표현 형태 기술. 모든 값은 가상이다. 기대값은 카드 명세 시트 {@code 참고 승인내역 취소건}의 B14:K15 예시(I14·I15)와
 * C3:D9, {@code 승인내역}의 H42 주석·H51·B52에서 먼저 정했다.
 */
class CodefCancellationFormDescriberTest {

	private static final String KB = "0301";

	/** 시트 예시 형식의 합성 승인내역 한 건을 기존 파서로 해석한다. */
	private static CodefApproval parsed(String usedAmount, String cancelYn, String cancelAmount) {
		String body = """
				{"result":{"code":"CF-00000"},"data":{"resUsedDate":"20261015","resCardName":"SYN CARD",
				"resMemberStoreName":"SYN STORE","resUsedAmount":"%s","resCancelYN":"%s","resCancelAmount":"%s",
				"resApprovalNo":"SYN-APPROVAL-SECRET"}}
				""".formatted(usedAmount, cancelYn, cancelAmount);
		CodefList<CodefApproval> data = CodefCardResponseParser.parseApprovals(body).data();
		return ((CodefList.Items<CodefApproval>) data).items().get(0);
	}

	private static CodefApproval approval(CodefAmount used, CancelStatus status, CodefAmount canceled) {
		return new CodefApproval(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
				used, new CodefAmount.Absent(), Optional.empty(), PaymentType.UNKNOWN, DomesticForeign.UNKNOWN, status,
				canceled, Optional.empty());
	}

	private static CodefCancellationForm undetermined(Reason reason) {
		return new CodefCancellationForm.Undetermined(reason);
	}

	@Nested
	class KbSheetExamples {

		@Test
		void type1FullCancellation() {
			// 취소건!I14: 이용 5000, 취소 1, 취소금액 5000 (KB 승인취소는 type1, 취소건!C9).
			assertThat(CodefCancellationFormDescriber.describe(KB, parsed("5000", "1", "5000")))
				.isEqualTo(new CodefCancellationForm.Type1Canceled());
		}

		@Test
		void type2ApprovalRowIsANormalRow() {
			// 취소건!I15 승인건: 이용 5000, 정상 0, 취소금액 빈 값. 정상 행만으로 type1·type2를 가르지 못한다(취소건!C4 주석).
			assertThat(CodefCancellationFormDescriber.describe(KB, parsed("5000", "0", "")))
				.isEqualTo(new CodefCancellationForm.NormalRow());
		}

		@Test
		void type2CancelRowKeepsTheCancelAmountAbsent() {
			// 취소건!I15 취소건·승인내역!B52: 이용 -5000, 취소 1, 취소금액 빈 값 (KB 취소전표매입은 type2, 취소건!D9).
			CodefApproval row = parsed("-5000", "1", "");

			assertThat(CodefCancellationFormDescriber.describe(KB, row))
				.isEqualTo(new CodefCancellationForm.Type2CancelRow());
			assertThat(row.cancelAmount()).isEqualTo(new CodefAmount.Absent());
		}

		@Test
		void partialCancellationIsNotDistinguishedForKb() {
			// 취소건!C7·D7 "X", 승인내역!H42 주석: KB는 부분취소를 구분하지 않는다. 취소건!K14 예시 모양이어도 판단하지 않는다.
			assertThat(CodefCancellationFormDescriber.describe(KB, parsed("5000", "2", "3000")))
				.isEqualTo(undetermined(Reason.PARTIAL_CANCEL_NOT_DISTINGUISHED));
			assertThat(CodefCancellationFormDescriber.describe(KB, parsed("-3000", "2", "")))
				.isEqualTo(undetermined(Reason.PARTIAL_CANCEL_NOT_DISTINGUISHED));
		}

	}

	@Nested
	class Undetermined {

		@Test
		void negativeAmountWithNormalStatusIsNotACancellation() {
			assertThat(CodefCancellationFormDescriber.describe(KB, parsed("-5000", "0", "")))
				.isEqualTo(undetermined(Reason.SHAPE_MISMATCH));
		}

		@ParameterizedTest
		@ValueSource(strings = { "", "Y", "01", "9" })
		void negativeAmountWithUnknownStatusIsUndetermined(String cancelYn) {
			assertThat(CodefCancellationFormDescriber.describe(KB, parsed("-5000", cancelYn, "")))
				.isEqualTo(undetermined(Reason.CANCEL_STATUS_UNKNOWN));
		}

		@Test
		void rejectedStatusIsNotDescribedByTheCancellationSheet() {
			assertThat(CodefCancellationFormDescriber.describe(KB, parsed("5000", "3", "")))
				.isEqualTo(undetermined(Reason.STATUS_NOT_DESCRIBED));
		}

		@Test
		void unparsedCancelAmountOrAbsentUsedAmountIsUndetermined() {
			assertThat(CodefCancellationFormDescriber.describe(KB, parsed("", "1", "5,000")))
				.isEqualTo(undetermined(Reason.AMOUNT_NOT_PARSED));
			assertThat(CodefCancellationFormDescriber
				.describe(KB, approval(new CodefAmount.Absent(), CancelStatus.CANCELED, new CodefAmount.Parsed(5_000))))
				.isEqualTo(undetermined(Reason.AMOUNT_NOT_PARSED));
			assertThat(CodefCancellationFormDescriber
				.describe(KB, approval(new CodefAmount.Unparsed(), CancelStatus.NORMAL, new CodefAmount.Absent())))
				.isEqualTo(undetermined(Reason.AMOUNT_NOT_PARSED));
			assertThat(CodefCancellationFormDescriber
				.describe(KB, approval(new CodefAmount.Parsed(5_000), CancelStatus.CANCELED, new CodefAmount.Unparsed())))
				.isEqualTo(undetermined(Reason.AMOUNT_NOT_PARSED));
		}

		@Test
		void shapesOutsideTheExamplesAreMismatches() {
			// 취소인데 취소금액이 승인금액과 다름, 양수인데 취소금액 빈 값, 음수인데 취소금액 있음, 정상인데 취소금액 있음, 0원 정상.
			for (CodefApproval row : List.of(parsed("5000", "1", "3000"), parsed("5000", "1", ""),
					parsed("-5000", "1", "5000"), parsed("5000", "0", "5000"), parsed("0", "0", ""),
					parsed("0", "1", "0"))) {
				assertThat(CodefCancellationFormDescriber.describe(KB, row)).isEqualTo(undetermined(Reason.SHAPE_MISMATCH));
			}
		}

	}

	@Nested
	class Organizations {

		@ParameterizedTest
		@ValueSource(strings = { "0302", "0306", "0311", "301", " 0301", "0301 ", "", "KB" })
		void otherOrganizationsAreNotDocumented(String organization) {
			assertThat(CodefCancellationFormDescriber.describe(organization, parsed("5000", "1", "5000")))
				.isEqualTo(new CodefCancellationForm.OrganizationNotDocumented());
		}

		@Test
		void rejectsNullInputs() {
			assertThatThrownBy(() -> CodefCancellationFormDescriber.describe(null, parsed("5000", "0", "")))
				.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> CodefCancellationFormDescriber.describe(KB, null))
				.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> new CodefCancellationForm.Undetermined(null))
				.isInstanceOf(NullPointerException.class);
		}

	}

	@Nested
	class Shape {

		@Test
		void resultsCarryNoAmountsAndOfferNoAmountCalculation() {
			// 실승인금액(취소건!C14 "API에서 제공하는 값이 아님")이나 합계를 만들지 않는다: 결과에 금액 성분·금액 반환 메서드가 없다.
			for (Class<?> type : CodefCancellationForm.class.getPermittedSubclasses()) {
				RecordComponent[] components = type.getRecordComponents();
				assertThat(Arrays.stream(components).map(RecordComponent::getType))
					.as(type.getSimpleName())
					.allMatch(componentType -> componentType == Reason.class);
				assertThat(Arrays.stream(type.getDeclaredMethods()).map(Method::getReturnType))
					.as(type.getSimpleName())
					.noneMatch(returnType -> returnType == long.class || returnType == CodefAmount.class);
			}
			assertThat(Arrays.stream(CodefCancellationFormDescriber.class.getDeclaredMethods())
				.filter(method -> Modifier.isPublic(method.getModifiers()))
				.map(Method::getName)).containsExactly("describe");
		}

		@Test
		void resultsDoNotEchoTheApproval() {
			CodefApproval row = parsed("5000", "1", "5000");
			for (CodefCancellationForm form : List.of(CodefCancellationFormDescriber.describe(KB, row),
					CodefCancellationFormDescriber.describe("0302", row),
					CodefCancellationFormDescriber.describe(KB, parsed("-5000", "0", "")))) {
				assertThat(form.toString()).doesNotContain("SYN-APPROVAL-SECRET", "SYN STORE", "SYN CARD", "5000");
			}
		}

	}

}
