package com.beone.api.codef.sync;

import static com.beone.api.codef.sync.SyntheticFixture.CHECKED_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.beone.api.codef.CodefHeldCard;
import com.beone.api.codef.CodefList;
import com.beone.api.codef.CodefProductResponse;
import com.beone.api.codef.CodefResult;
import com.beone.api.codef.CodefResultCategory;
import com.beone.api.codef.sync.CodefUnusableReason.DataState;
import com.beone.api.codef.transport.CodefCallResult;

/**
 * 조회 대상 하나의 결과 판정. 공식 시트 기준, 실계정 관측 없음. 기대값은 구현 전에 정했다.
 *
 * <p>
 * 사용할 수 있는 경우는 응답을 받았고({@code Answered}) HTTP 200으로 해석됐으며({@code Interpreted}) 결과 코드가 성공({@code CF-00000})이고 {@code data}가
 * 항목 목록({@code Items})일 때뿐이다. 그 밖에는 이유를 보존한 채 쓸 수 없음이다.
 */
class CodefTargetOutcomeTest {

	private static CodefTargetOutcome<CodefHeldCard> judgeFixture(String name) {
		return CodefTargetOutcome.judge(SyntheticFixture.heldCards(name), CHECKED_AT, Function.identity());
	}

	private static CodefTargetOutcome<CodefHeldCard> judge(CodefCallResult<CodefHeldCard> call) {
		return CodefTargetOutcome.judge(call, CHECKED_AT, Function.identity());
	}

	private static CodefUnusableReason.ResponseNotUsable notUsable(CodefTargetOutcome<?> outcome) {
		assertThat(outcome).isInstanceOf(CodefTargetOutcome.Unusable.class);
		CodefUnusableReason reason = ((CodefTargetOutcome.Unusable<?>) outcome).reason();
		assertThat(reason).isInstanceOf(CodefUnusableReason.ResponseNotUsable.class);
		return (CodefUnusableReason.ResponseNotUsable) reason;
	}

	@Test
	void successWithItemsIsUsableAndKeepsResultAndCheckedAt() {
		CodefTargetOutcome<CodefHeldCard> outcome = judgeFixture("held-cards-kb.json");

		assertThat(outcome).isInstanceOf(CodefTargetOutcome.Usable.class);
		CodefTargetOutcome.Usable<CodefHeldCard> usable = (CodefTargetOutcome.Usable<CodefHeldCard>) outcome;
		assertThat(usable.checkedAt()).isEqualTo(CHECKED_AT);
		assertThat(usable.result()).isEqualTo(new CodefResult(Optional.of("CF-00000"), Optional.of("SYN-TX-HELD-0001"),
				CodefResultCategory.SUCCESS));
		assertThat(usable.shape()).isEqualTo(CodefList.Shape.ARRAY);
		assertThat(usable.items()).hasSize(3);
	}

	@Test
	void successWithEmptyArrayIsUsableWithZeroItems() {
		// 빈 배열은 정상적인 0건이다. 누락·형식 오류와 구분한다.
		CodefTargetOutcome<CodefHeldCard> outcome = judgeFixture("envelope-success-data-empty.json");

		assertThat(outcome).isInstanceOf(CodefTargetOutcome.Usable.class);
		assertThat(((CodefTargetOutcome.Usable<CodefHeldCard>) outcome).items()).isEmpty();
	}

	@Test
	void successWithMissingDataIsNotUsableAndNotZeroItems() {
		CodefUnusableReason.ResponseNotUsable reason = notUsable(judgeFixture("envelope-success-data-missing.json"));

		assertThat(reason.result().category()).isEqualTo(CodefResultCategory.SUCCESS);
		assertThat(reason.result().transactionId()).contains("SYN-TX-ENV-0001");
		assertThat(reason.data()).isEqualTo(DataState.MISSING);
		assertThat(reason.malformedReason()).isEmpty();
	}

	@Test
	void successWithMalformedDataIsNotUsableAndKeepsTheFixedReason() {
		CodefUnusableReason.ResponseNotUsable reason = notUsable(judgeFixture("envelope-success-data-malformed.json"));

		assertThat(reason.result().category()).isEqualTo(CodefResultCategory.SUCCESS);
		assertThat(reason.data()).isEqualTo(DataState.MALFORMED);
		assertThat(reason.malformedReason()).contains("list is null");
	}

	@Test
	void additionalAuthenticationIsNotUsableAndNotHiddenAsAFailure() {
		// CF-03002 + continue2Way 불리언 true. 2차 요청은 범위 밖이라 쓸 수 없음으로 두되 추가 인증 필요를 그대로 드러낸다.
		CodefUnusableReason.ResponseNotUsable reason = notUsable(judgeFixture("envelope-additional-auth-true.json"));

		assertThat(reason.result().category()).isEqualTo(CodefResultCategory.ADDITIONAL_AUTHENTICATION_REQUIRED);
		assertThat(reason.additionalAuthenticationRequired()).isTrue();
		assertThat(reason.data()).isEqualTo(DataState.ITEMS);
		assertThat(reason.toString()).doesNotContain("SYN-JTI-0000");
	}

	@Test
	void additionalAuthenticationCodeWithoutBooleanTrueIsUnclassified() {
		for (String name : List.of("envelope-additional-auth-false.json", "envelope-additional-auth-string.json")) {
			CodefUnusableReason.ResponseNotUsable reason = notUsable(judgeFixture(name));

			assertThat(reason.result().code()).as(name).contains("CF-03002");
			assertThat(reason.result().category()).as(name).isEqualTo(CodefResultCategory.UNCLASSIFIED);
			assertThat(reason.additionalAuthenticationRequired()).as(name).isFalse();
		}
	}

	@Test
	void cardVerificationIsNotUsable() {
		CodefUnusableReason.ResponseNotUsable reason = notUsable(judgeFixture("envelope-card-verification.json"));

		assertThat(reason.result().code()).contains("CF-12108");
		assertThat(reason.result().category()).isEqualTo(CodefResultCategory.CARD_VERIFICATION_NOT_SUPPORTED);
	}

	@Test
	void unclassifiedCodeIsNotUsableAndKeepsTheCode() {
		CodefUnusableReason.ResponseNotUsable reason = notUsable(judgeFixture("envelope-unclassified.json"));

		assertThat(reason.result().code()).contains("CF-12872");
		assertThat(reason.result().category()).isEqualTo(CodefResultCategory.UNCLASSIFIED);
	}

	@Test
	void invalidUrlEncodingIsNotUsableAndMalformed() {
		// 디코딩 실패는 기존 전송 단계가 미분류 + 형식 오류로 돌려준다. 결과 코드는 읽지 않았으므로 비어 있다.
		CodefUnusableReason.ResponseNotUsable reason = notUsable(judgeFixture("envelope-url-encoding-error.json"));

		assertThat(reason.result().code()).isEmpty();
		assertThat(reason.result().category()).isEqualTo(CodefResultCategory.UNCLASSIFIED);
		assertThat(reason.data()).isEqualTo(DataState.MALFORMED);
		assertThat(reason.malformedReason()).contains("response body is not valid URL encoding");
	}

	@Test
	void unauthorizedIsNotUsable() {
		CodefTargetOutcome<CodefHeldCard> outcome = judge(
				new CodefCallResult.Answered<>(new CodefProductResponse.Unauthorized<>()));

		assertThat(outcome).isEqualTo(new CodefTargetOutcome.Unusable<CodefHeldCard>(CHECKED_AT,
				new CodefUnusableReason.HttpUnauthorized()));
	}

	@Test
	void httpFailureIsNotUsableAndKeepsTheStatus() {
		CodefTargetOutcome<CodefHeldCard> outcome = judge(
				new CodefCallResult.Answered<>(new CodefProductResponse.HttpFailure<>(500)));

		assertThat(outcome).isEqualTo(
				new CodefTargetOutcome.Unusable<CodefHeldCard>(CHECKED_AT, new CodefUnusableReason.HttpFailed(500)));
	}

	@Test
	void callFailuresAreNotUsableAndKeepStageAndReason() {
		List<CodefCallResult.Failed<CodefHeldCard>> failures = List.of(
				new CodefCallResult.Failed<>(CodefCallResult.Stage.TOKEN, CodefCallResult.Reason.CREDENTIALS_MISSING,
						OptionalInt.empty()),
				new CodefCallResult.Failed<>(CodefCallResult.Stage.PRODUCT, CodefCallResult.Reason.CALL_LIMIT_REACHED,
						OptionalInt.empty()),
				new CodefCallResult.Failed<>(CodefCallResult.Stage.PRODUCT, CodefCallResult.Reason.TIMEOUT,
						OptionalInt.empty()));
		for (CodefCallResult.Failed<CodefHeldCard> failure : failures) {
			assertThat(judge(failure)).isEqualTo(new CodefTargetOutcome.Unusable<CodefHeldCard>(CHECKED_AT,
					new CodefUnusableReason.CallFailed(failure.stage(), failure.reason(), failure.httpStatus())));
		}
	}

	@Test
	void responseNotUsableRejectsAUsableCombination() {
		CodefResult success = new CodefResult(Optional.of("CF-00000"), Optional.empty(), CodefResultCategory.SUCCESS);

		assertThatThrownBy(() -> new CodefUnusableReason.ResponseNotUsable(success, DataState.ITEMS, Optional.empty()))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CodefUnusableReason.ResponseNotUsable(success, DataState.MISSING,
				Optional.of("list is null")))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CodefUnusableReason.ResponseNotUsable(success, DataState.MALFORMED, Optional.empty()))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
