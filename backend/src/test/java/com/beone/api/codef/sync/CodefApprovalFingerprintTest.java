package com.beone.api.codef.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.beone.api.codef.CodefApproval;
import com.beone.api.codef.sync.CodefLedgerCandidateRow.Decision;

/**
 * 승인번호 지문(결정 R2). 지문 함수는 주입받고, 원문 승인번호는 결과·{@code toString}·예외 문구에 남지 않는다. 키는 테스트 안의 합성 값이며 운영 키가 아니다.
 */
class CodefApprovalFingerprintTest {

	private static final List<Class<?>> NEW_PUBLIC_TYPES = List.of(CodefApprovalFingerprint.class,
			CodefApprovalFingerprinter.class, CodefApprovalKey.class, CodefCardLink.class, CodefCardLink.Linked.class,
			CodefCardLink.NotLinked.class, CodefCardLinker.class, CodefPerformanceLink.class, CodefUsedDateTime.class,
			CodefLedgerCandidateRow.class, Decision.class, Decision.Accepted.class, Decision.Incomplete.class,
			CodefLedgerCandidates.class, CodefLedgerCandidateBuilder.class, CodefIncompleteMark.class,
			CodefIncompleteReason.class, CodefLedgerRowKind.class, CodefRecheck.class, CodefAttributionMonth.class);

	@Test
	void differentSyntheticKeysGiveDifferentFingerprints() {
		CodefApprovalFingerprint one = SyntheticHmacFingerprinter.KEY_ONE.fingerprint("SYN-APR-P01");

		assertThat(SyntheticHmacFingerprinter.KEY_ONE.fingerprint("SYN-APR-P01")).isEqualTo(one);
		assertThat(SyntheticHmacFingerprinter.KEY_TWO.fingerprint("SYN-APR-P01")).isNotEqualTo(one);
		assertThat(SyntheticHmacFingerprinter.KEY_ONE.fingerprint("SYN-APR-P02")).isNotEqualTo(one);
		assertThat(one.value()).doesNotContain("SYN-APR-P01");
	}

	@Test
	void keysFromDifferentSyntheticKeysDoNotMatchOnRecheck() {
		// 키가 바뀌면 이전 기록과 비교가 끊긴다(회전 미결). 같은 응답도 모두 신규로 보인다.
		CodefLedgerCandidates first = LedgerFixture.build("ledger-recheck-first.json");
		CodefLedgerCandidates rotated = CodefLedgerCandidateBuilder.build(SyntheticFixture.KB, LedgerFixture.linker(),
				LedgerFixture.approvals("ledger-recheck-first.json"), SyntheticHmacFingerprinter.KEY_TWO,
				first.acceptedKinds());

		assertThat(rotated.rows()).allSatisfy(row -> assertThat(((Decision.Accepted) row.decision()).recheck())
			.isEqualTo(CodefRecheck.NEW));
	}

	@Test
	void rawApprovalNumbersAreNotKept() {
		for (String fixture : List.of("ledger-approvals-pairing.json", "ledger-approvals-duplicate.json",
				"ledger-approvals-link.json", "ledger-approvals-dates.json", "ledger-recheck-second.json")) {
			List<CodefApproval> approvals = LedgerFixture.approvals(fixture);
			CodefLedgerCandidates candidates = LedgerFixture.build(fixture);
			String text = candidates + " " + candidates.incompleteMarks() + " " + candidates.acceptedKinds();

			assertThat(text).as(fixture).doesNotContain("SYN-APR").doesNotContain("SYN-MASKED");
			for (CodefApproval approval : approvals) {
				approval.approvalNo().ifPresent(number -> assertThat(text).doesNotContain(number));
			}
		}
	}

	@Test
	void receivesTheRawNumberUnchanged() {
		List<String> received = new ArrayList<>();
		CodefApprovalFingerprinter recording = number -> {
			received.add(number);
			return SyntheticHmacFingerprinter.KEY_ONE.fingerprint(number);
		};

		CodefLedgerCandidateBuilder.build(SyntheticFixture.KB, LedgerFixture.linker(),
				LedgerFixture.approvals("ledger-recheck-first.json"), recording, Map.of());

		assertThat(received).containsExactly("SYN-APR-R31", "SYN-APR-R32", "SYN-APR-R33", "SYN-APR-R34");
	}

	@Test
	void fingerprinterReturningTheRawNumberIsRejectedWithoutEchoingIt() {
		CodefApprovalFingerprinter identity = CodefApprovalFingerprint::new;

		assertThatThrownBy(() -> CodefLedgerCandidateBuilder.build(SyntheticFixture.KB, LedgerFixture.linker(),
				LedgerFixture.approvals("ledger-recheck-first.json"), identity, Map.of()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageNotContaining("SYN-APR");
	}

	@Test
	void fingerprinterReturningNullIsRejected() {
		assertThatThrownBy(() -> CodefLedgerCandidateBuilder.build(SyntheticFixture.KB, LedgerFixture.linker(),
				LedgerFixture.approvals("ledger-recheck-first.json"), number -> null, Map.of()))
			.isInstanceOf(NullPointerException.class)
			.hasMessageNotContaining("SYN-APR");
	}

	@Test
	void newTypesHaveNoApprovalNumberFieldAndNoBenefitDependency() {
		for (Class<?> type : NEW_PUBLIC_TYPES) {
			if (type.isRecord()) {
				for (RecordComponent component : type.getRecordComponents()) {
					assertThat(component.getName().toLowerCase()).as("%s.%s", type.getSimpleName(), component.getName())
						.doesNotContain("approvalno")
						.doesNotContain("approvalnumber");
					assertThat(component.getType()).isNotEqualTo(CodefApproval.class);
				}
			}
			for (Field field : type.getDeclaredFields()) {
				assertThat(field.getType()).as("%s.%s", type.getSimpleName(), field.getName())
					.isNotEqualTo(CodefApproval.class);
			}
			for (Method method : type.getMethods()) {
				if (Modifier.isPublic(method.getModifiers())) {
					List<Type> signature = new ArrayList<>(List.of(method.getGenericParameterTypes()));
					signature.add(method.getGenericReturnType());
					for (Type used : signature) {
						assertThat(typeNames(used)).as("%s.%s", type.getSimpleName(), method.getName())
							.noneMatch(name -> name.startsWith("com.beone.api.benefit"));
					}
				}
			}
		}
	}

	@Test
	void noPersistenceOrStoredConfigurationSurface() {
		// 운영 구현체·Spring Bean·설정 키를 만들지 않는다. 새 타입에 Spring 애너테이션이 없어야 한다.
		for (Class<?> type : NEW_PUBLIC_TYPES) {
			assertThat(type.getAnnotations()).as(type.getSimpleName())
				.noneMatch(annotation -> annotation.annotationType().getName().startsWith("org.springframework"));
		}
		assertThat(CodefApprovalFingerprinter.class.isInterface()).isTrue();
	}

	private static List<String> typeNames(Type type) {
		List<String> names = new ArrayList<>();
		if (type instanceof ParameterizedType parameterized) {
			names.add(parameterized.getRawType().getTypeName());
			for (Type argument : parameterized.getActualTypeArguments()) {
				names.addAll(typeNames(argument));
			}
		}
		else {
			names.add(type.getTypeName());
		}
		return names;
	}

}
