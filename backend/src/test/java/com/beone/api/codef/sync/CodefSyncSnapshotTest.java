package com.beone.api.codef.sync;

import static com.beone.api.codef.sync.SyntheticFixture.CHECKED_AT;
import static com.beone.api.codef.sync.SyntheticFixture.CLOCK;
import static com.beone.api.codef.sync.SyntheticFixture.KB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

import com.beone.api.codef.CodefCancellationForm;
import com.beone.api.codef.CodefCardPerformance;
import com.beone.api.codef.CodefHeldCard;
import com.beone.api.codef.CodefProductResponse;
import com.beone.api.codef.transport.CodefCallResult;

/**
 * 세 조회 대상의 묶음. 공식 시트 기준, 실계정 관측 없음. 대상 하나의 실패가 다른 대상에 영향을 주지 않는지(부분 실패), 계산 입력으로 가는 경로가 없는지를
 * 확인한다.
 */
class CodefSyncSnapshotTest {

	private static final List<Class<?>> PUBLIC_TYPES = List.of(CodefSyncSnapshot.class, CodefTargetOutcome.class,
			CodefTargetOutcome.Usable.class, CodefTargetOutcome.Unusable.class, CodefUnusableReason.class,
			CodefUnusableReason.CallFailed.class, CodefUnusableReason.HttpUnauthorized.class,
			CodefUnusableReason.HttpFailed.class, CodefUnusableReason.ResponseNotUsable.class,
			CodefHeldCardCandidate.class, CodefApprovalRowView.class, CodefPerformanceReference.class,
			CodefPerformanceReference.BenefitReference.class, CodefPerformanceReference.PerformanceEntry.class,
			CodefPerformanceReference.ReferenceAmount.class, CodefPerformanceReference.ReferenceDate.class);

	private static CodefSyncSnapshot allAnswered() {
		return CodefSyncSnapshot.assemble(KB, CLOCK, SyntheticFixture.heldCards("held-cards-kb.json"),
				SyntheticFixture.approvals("approvals-kb.json"), SyntheticFixture.performances("performances-kb.json"));
	}

	@Test
	void allThreeTargetsAreUsableWithOneCheckedAt() {
		CodefSyncSnapshot snapshot = allAnswered();

		assertThat(snapshot.organizationCode()).isEqualTo(KB);
		assertThat(snapshot.checkedAt()).isEqualTo(CHECKED_AT);
		assertThat(snapshot.heldCards()).isInstanceOf(CodefTargetOutcome.Usable.class);
		assertThat(snapshot.approvals()).isInstanceOf(CodefTargetOutcome.Usable.class);
		assertThat(snapshot.performances()).isInstanceOf(CodefTargetOutcome.Usable.class);
		assertThat(List.of(snapshot.heldCards(), snapshot.approvals(), snapshot.performances()))
			.extracting(CodefTargetOutcome::checkedAt)
			.containsOnly(CHECKED_AT);
		CodefPerformanceReference reference = ((CodefTargetOutcome.Usable<CodefPerformanceReference>) snapshot
			.performances()).items().get(0);
		assertThat(reference.checkedAt()).isEqualTo(CHECKED_AT);
	}

	@Test
	void oneFailedTargetLeavesTheOthersUnchanged() {
		CodefSyncSnapshot whole = allAnswered();
		CodefSyncSnapshot partial = CodefSyncSnapshot.assemble(KB, CLOCK,
				SyntheticFixture.heldCards("held-cards-kb.json"),
				new CodefCallResult.Failed<>(CodefCallResult.Stage.PRODUCT, CodefCallResult.Reason.TIMEOUT,
						OptionalInt.empty()),
				SyntheticFixture.performances("performances-kb.json"));

		assertThat(partial.approvals()).isEqualTo(new CodefTargetOutcome.Unusable<CodefApprovalRowView>(CHECKED_AT,
				new CodefUnusableReason.CallFailed(CodefCallResult.Stage.PRODUCT, CodefCallResult.Reason.TIMEOUT,
						OptionalInt.empty())));
		assertThat(partial.heldCards()).isEqualTo(whole.heldCards());
		assertThat(partial.performances()).isEqualTo(whole.performances());
	}

	@Test
	void eachTargetFailsIndependently() {
		CodefSyncSnapshot snapshot = CodefSyncSnapshot.assemble(KB, CLOCK,
				new CodefCallResult.Answered<CodefHeldCard>(new CodefProductResponse.Unauthorized<>()),
				SyntheticFixture.approvals("approvals-kb.json"),
				new CodefCallResult.Answered<CodefCardPerformance>(new CodefProductResponse.HttpFailure<>(500)));

		assertThat(snapshot.heldCards()).isEqualTo(new CodefTargetOutcome.Unusable<CodefHeldCardCandidate>(CHECKED_AT,
				new CodefUnusableReason.HttpUnauthorized()));
		assertThat(snapshot.approvals()).isEqualTo(allAnswered().approvals());
		assertThat(snapshot.performances()).isEqualTo(new CodefTargetOutcome.Unusable<CodefPerformanceReference>(
				CHECKED_AT, new CodefUnusableReason.HttpFailed(500)));
	}

	@Test
	void organizationCodeReachesTheApprovalRows() {
		CodefSyncSnapshot snapshot = CodefSyncSnapshot.assemble("0302", CLOCK,
				SyntheticFixture.heldCards("held-cards-kb.json"), SyntheticFixture.approvals("approvals-kb.json"),
				SyntheticFixture.performances("performances-kb.json"));

		List<CodefApprovalRowView> rows = ((CodefTargetOutcome.Usable<CodefApprovalRowView>) snapshot.approvals())
			.items();
		assertThat(rows).extracting(CodefApprovalRowView::form)
			.containsOnly(new CodefCancellationForm.OrganizationNotDocumented());
	}

	@Test
	void textHasNoRawApprovalNumberOrSensitiveValues() {
		String text = allAnswered().toString();

		assertThat(text).doesNotContain("SYN-APR-")
			.doesNotContain("SYN-MASKED-0000")
			.doesNotContain("SYN-USER")
			.doesNotContain("SYN-JTI-0000");
	}

	@Test
	void nullInputsAreRejected() {
		assertThatThrownBy(() -> CodefSyncSnapshot.assemble(null, CLOCK, SyntheticFixture.heldCards("held-cards-kb.json"),
				SyntheticFixture.approvals("approvals-kb.json"), SyntheticFixture.performances("performances-kb.json")))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> CodefSyncSnapshot.assemble(KB, null, SyntheticFixture.heldCards("held-cards-kb.json"),
				SyntheticFixture.approvals("approvals-kb.json"), SyntheticFixture.performances("performances-kb.json")))
			.isInstanceOf(NullPointerException.class);
	}

	@Test
	void noPublicApiLeadsToEngineOrDomainTypes() {
		// D-16: 실적조회 참고값을 StateInput·StateValue 같은 계산 입력으로 바꾸는 경로를 두지 않는다. R8: 도메인 Ledger로도 넘기지 않는다.
		List<String> leaks = new ArrayList<>();
		for (Class<?> type : PUBLIC_TYPES) {
			for (Method method : type.getMethods()) {
				if (!Modifier.isPublic(method.getModifiers()) || method.getDeclaringClass() == Object.class) {
					continue;
				}
				List<Type> signature = new ArrayList<>(List.of(method.getGenericParameterTypes()));
				signature.add(method.getGenericReturnType());
				for (Type part : signature) {
					if (mentionsBenefitPackage(part)) {
						leaks.add(type.getSimpleName() + "." + method.getName());
					}
				}
			}
		}
		assertThat(leaks).isEmpty();
	}

	@Test
	void approvalViewDoesNotExposeTheRawApprovalNumber() {
		assertThat(CodefApprovalRowView.class.getRecordComponents()).extracting(component -> component.getName())
			.doesNotContain("approvalNo", "approvalNumber");
		assertThat(CodefApprovalRowView.class.getRecordComponents())
			.filteredOn(component -> component.getName().startsWith("approval"))
			.extracting(component -> (Object) component.getType())
			.containsExactly(boolean.class);
	}

	private static boolean mentionsBenefitPackage(Type type) {
		if (type instanceof Class<?> raw) {
			Class<?> component = raw.isArray() ? raw.getComponentType() : raw;
			return component.getName().startsWith("com.beone.api.benefit.");
		}
		if (type instanceof ParameterizedType parameterized) {
			if (mentionsBenefitPackage(parameterized.getRawType())) {
				return true;
			}
			for (Type argument : parameterized.getActualTypeArguments()) {
				if (mentionsBenefitPackage(argument)) {
					return true;
				}
			}
		}
		return false;
	}

}
