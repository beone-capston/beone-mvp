# benefit 도메인 안내

## 목적과 범위

`benefit`은 카드 혜택 계산과 추천에 필요한 식별자, 금액, 상태, 입력, 약관 규칙, 결과를 정의합니다. 값만 담는 것이 아니라 잘못된 조합을 생성 시점에 차단하고, 모르는 정보는 확인 필요 상태로 유지합니다.

현재는 도메인 모델과 규칙 선택·보조 검증 로직, 서비스 한 건의 산식·조건을 따로따로 판정하는 기본 함수(`primitive`), 그리고 이 함수들로 한도 없는 비선택형 서비스 하나의 후보(`ServiceCandidate`)를 만드는 판정기(`ServiceCandidateEvaluator`, #31), 규칙 선택 결과의 서비스들을 후보·확인 필요·지원 범위 밖으로 나눠 모으는 취합기(`CardServiceCandidateCollector`, #33), 취합 결과로 카드 한 장·주문 한 건의 서비스별 채택·기여액과 카드 금액을 정하는 결정기(`OrderBenefitDecider`, #35), 호출자가 정한 판정일로 규칙 선택부터 결정까지 잇는 진입점(`OrderBenefitResolver`, #38)이 구현되어 있습니다. 결정기는 한도 없는 비선택형 서비스만 판정하며, 한도·선택형 서비스가 있으면 카드 금액은 `확인 필요`입니다. 카드 간 비교(`CardBenefit`·`Recommendation` 연결), 한도 차감, 중복 허용 서비스의 합산, 월간 최적화, 추천 API, CODEF 호출은 아직 구현되지 않았습니다.

## 핵심 용어

- 서비스: 카드 하나가 제공하는 혜택 항목. 기본 적립·주유 할인 등이 각각 서비스입니다. Spring의 서비스 계층을 뜻하지 않습니다.
- 한도 버킷: 혜택이 소비하는 한도 묶음. 서비스별 한도와 여러 서비스를 묶는 통합 한도를 표현합니다.
- 실적 구간(tier): 전월 실적에 따라 계산 방식이나 월 한도가 달라지는 구간.
- 상태: 사용자의 전월 실적·사용 혜택 등 값과 그 출처·기준 기간.
- 규칙 버전: 특정 카드의 특정 적용 기간에 사용하는 약관 규칙 묶음.
- 확인 필요: 값이나 조건을 확정할 수 없는 상태. 확정된 0원과 다릅니다.

## 패키지 구조

```text
benefit/
├── card/    식별자와 거래 분류
├── money/   원화와 혜택 금액
├── state/   값의 출처·기준 기간·갱신 시각
├── input/   계산에 필요한 주문·카드·거래·선택 설정
├── rule/      약관 규칙과 검증 규칙 선택
├── primitive/ 서비스 한 건의 산식·개별 조건 판정, 단일 서비스 후보 판정, 카드 한 장의 서비스 후보 취합·주문 혜택 결정과 규칙 선택 연결
└── result/    서비스 후보·취합 결과, 단일 주문 혜택 결정 결과, 카드별 혜택과 최종 추천 결과
```

## 주요 클래스

### card: 식별자와 거래 분류

- [CardId](../../backend/src/main/java/com/beone/api/benefit/card/CardId.java): 문자열 카드 상품 ID. 앞자리 0을 보존하며 문자열 순서로 비교합니다.
- [ServiceId](../../backend/src/main/java/com/beone/api/benefit/card/ServiceId.java): 카드 안의 혜택 서비스 ID.
- [LimitBucketId](../../backend/src/main/java/com/beone/api/benefit/card/LimitBucketId.java): 서비스가 사용하는 한도 ID.
- [MerchantClass](../../backend/src/main/java/com/beone/api/benefit/card/MerchantClass.java): 사용자나 시나리오가 확인한 가맹점·업종 분류. 비슷한 이름만으로 추정하지 않습니다.
- [PaymentMethod](../../backend/src/main/java/com/beone/api/benefit/card/PaymentMethod.java): 직접 결제·대상 간편결제·자동이체 구분.
- [TransactionNature](../../backend/src/main/java/com/beone/api/benefit/card/TransactionNature.java): 일반 거래·세금 등 거래 성격. 승인/취소 구분과 독립적입니다.
- [Identifiers](../../backend/src/main/java/com/beone/api/benefit/card/Identifiers.java): 빈 값과 공백을 차단하는 패키지 내부 ID 검증 유틸리티.

### money: 금액

- [Won](../../backend/src/main/java/com/beone/api/benefit/money/Won.java): long 정수 원화. 취소 표현을 위해 음수도 허용하고 개별 모델이 허용 부호를 검사합니다.
- [BenefitAmount](../../backend/src/main/java/com/beone/api/benefit/money/BenefitAmount.java): 확정 금액 Confirmed와 사유를 가진 NeedsConfirmation으로 구분합니다. 확정 혜택은 음수일 수 없습니다.

### state: 값의 근거

- [StateValue](../../backend/src/main/java/com/beone/api/benefit/state/StateValue.java): 값과 출처·기준 기간·갱신 시각·추론 규칙 버전을 묶습니다. UNKNOWN은 값을 가질 수 없습니다.
- [StateSource](../../backend/src/main/java/com/beone/api/benefit/state/StateSource.java): CODEF·MANUAL·INFERRED·SYNTHETIC·UNKNOWN을 구분합니다. 출처 표시는 연동 구현 자체가 아닙니다.
- [BasisPeriod](../../backend/src/main/java/com/beone/api/benefit/state/BasisPeriod.java): 값의 기준 날짜 범위. 시작일과 종료일을 모두 포함합니다.

CODEF·수동 입력·추론 값에는 갱신 시각이 필요합니다. 추론 값은 사용한 규칙 버전도 필요합니다. 합성 데이터에는 가상의 갱신 시각을 강제로 만들지 않습니다.

### input: 계산 입력

- [RecommendationInput](../../backend/src/main/java/com/beone/api/benefit/input/RecommendationInput.java): 기준 날짜, 주문, 보유 카드, 원장, 월별 상태, 예정 지출, 선택 설정을 묶는 최상위 입력. 보유 카드 중복, 동일 카드의 월간 상태 중복, 미보유 카드 참조 등을 검사합니다.
- [Order](../../backend/src/main/java/com/beone/api/benefit/input/Order.java): 현재 결제할 주문의 금액·날짜·가맹점 분류·결제 방식.
- [PlannedSpending](../../backend/src/main/java/com/beone/api/benefit/input/PlannedSpending.java): 사용자가 확인한 예정 지출 한 건.
- [PlannedSpendings](../../backend/src/main/java/com/beone/api/benefit/input/PlannedSpendings.java): 예정 지출 목록. 최대 5건.
- [HeldCard](../../backend/src/main/java/com/beone/api/benefit/input/HeldCard.java): 보유 카드와 보유 정보 출처, 등록일 상태.
- [CardMonthlyState](../../backend/src/main/java/com/beone/api/benefit/input/CardMonthlyState.java): 전월 실적, 버킷별 사용 혜택, 기타 버킷 사용량, 선택형 서비스 상태. 전월 실적 Optional.empty는 원장에서 산출해야 함을 뜻합니다.
- [ServiceSelection](../../backend/src/main/java/com/beone/api/benefit/input/ServiceSelection.java): 선택형 서비스와 적용 시작일.
- [LedgerTransaction](../../backend/src/main/java/com/beone/api/benefit/input/LedgerTransaction.java): 거래 한 건. 승인은 양수, 취소는 음수이며 취소 원거래 날짜 등을 검증합니다.
- [Ledger](../../backend/src/main/java/com/beone/api/benefit/input/Ledger.java): 거래 목록과 완전하게 확보된 기간. isComplete()로 기간 전체 확보 여부, cancellationEvidence()로 취소 관련 증거를 판정합니다.
- [LedgerCoverage](../../backend/src/main/java/com/beone/api/benefit/input/LedgerCoverage.java): 특정 카드·기간에 모든 거래가 확보되었다는 선언. 선언하지 않은 기간은 완전하다고 간주하지 않습니다.
- [LedgerEntryType](../../backend/src/main/java/com/beone/api/benefit/input/LedgerEntryType.java): 승인/취소 구분.
- [LedgerSource](../../backend/src/main/java/com/beone/api/benefit/input/LedgerSource.java): 시뮬레이션/CODEF 원장 구분.
- [SelectionPreference](../../backend/src/main/java/com/beone/api/benefit/input/SelectionPreference.java): 자동 추천·대표 카드 고정 설정. 고정 모드에는 대표 카드가 필요합니다.
- [SelectionMode](../../backend/src/main/java/com/beone/api/benefit/input/SelectionMode.java): AUTO_RECOMMEND와 FIXED_REPRESENTATIVE를 정의합니다.
- [PolicyTermEvidence](../../backend/src/main/java/com/beone/api/benefit/input/PolicyTermEvidence.java): 카드 한 장의 취소 귀속(`CANCELLATION_AFFECTING_PERFORMANCE_WINDOW`)·할인 매출 실적 제외(`BENEFITED_SALE_IN_PERFORMANCE_WINDOW`) 발생 근거(#35). 기본값 없는 필수 입력이며 null을 거부합니다. 근거가 이 카드의 실적 기간에 대한 것인지는 호출자 책임이고, 근거가 없으면 `UNDETERMINED`를 넣습니다(`CONFIRMED_ABSENT`를 추정하지 않음). 취소 근거는 `Ledger.cancellationEvidence()`로 만들 수 있지만, 할인 매출 근거를 만드는 코드는 아직 없습니다.
- [InputChecks](../../backend/src/main/java/com/beone/api/benefit/input/InputChecks.java): 양수 금액과 선택적 문자열 검증을 공유하는 내부 유틸리티.

### rule: 약관 규칙

- [CardRuleVersion](../../backend/src/main/java/com/beone/api/benefit/rule/CardRuleVersion.java): 카드별 규칙 버전의 최상위 모델. 출처·정책·서비스·한도를 묶고 중복 ID, 없는 참조, 한도 부모 순환 등을 검사합니다. blockingReasons()는 계산을 막는 규칙 사유를, limitChain()은 상위 통합 한도까지 반환합니다.
- [RuleCatalog](../../backend/src/main/java/com/beone/api/benefit/rule/RuleCatalog.java): 카드와 판단 날짜에 맞는 VERIFIED 규칙만 선택합니다. 검증 규칙의 기간 중첩을 차단합니다.
- [RuleSelection](../../backend/src/main/java/com/beone/api/benefit/rule/RuleSelection.java): 규칙 선택 성공 Selected와 사용 불가 Unavailable. 성공 시에도 규칙상 계산 가능한 서비스와 확인 필요 서비스를 구분합니다.
- [RuleVersionRef](../../backend/src/main/java/com/beone/api/benefit/rule/RuleVersionRef.java): 카드 ID와 규칙 버전 참조.
- [ValidityPeriod](../../backend/src/main/java/com/beone/api/benefit/rule/ValidityPeriod.java): 규칙 유효 기간과 포함·중첩 검사. 종료일 미지정은 알려진 종료일이 없음을 뜻합니다.
- [VerificationStatus](../../backend/src/main/java/com/beone/api/benefit/rule/VerificationStatus.java): VERIFIED·AI_DRAFT·CANDIDATE·SYNTHETIC_TEST_SNAPSHOT 구분.
- [OfficialSource](../../backend/src/main/java/com/beone/api/benefit/rule/OfficialSource.java): 공식 자료 주소와 페이지·절 위치.
- [BenefitService](../../backend/src/main/java/com/beone/api/benefit/rule/BenefitService.java): 대상·제외 업종, 결제 방식, 금액 조건, 실적 구간, 한도, 선택 여부, 중복 혜택과 원 미만 처리 조건.
- [BenefitKind](../../backend/src/main/java/com/beone/api/benefit/rule/BenefitKind.java): 포인트 적립/청구 할인 구분.
- [BenefitCalculation](../../backend/src/main/java/com/beone/api/benefit/rule/BenefitCalculation.java): Rate 비율형과 FixedAmount 정액형 계산 방식의 표현. 금액을 직접 계산하는 엔진은 아닙니다.
- [ServiceTier](../../backend/src/main/java/com/beone/api/benefit/rule/ServiceTier.java): 최소 실적과 해당 구간의 계산 방식.
- [MerchantTarget](../../backend/src/main/java/com/beone/api/benefit/rule/MerchantTarget.java): 전체 가맹점 AllMerchants 또는 특정 분류 SpecificClasses.
- [LimitBucket](../../backend/src/main/java/com/beone/api/benefit/rule/LimitBucket.java): 실적 구간별 한도와 부모 통합 한도 참조.
- [LimitTier](../../backend/src/main/java/com/beone/api/benefit/rule/LimitTier.java): 최소 실적과 월 한도.
- [Tiers](../../backend/src/main/java/com/beone/api/benefit/rule/Tiers.java): 실적 구간이 비어 있지 않고 엄격한 오름차순인지 검사하는 내부 유틸리티.
- [RulePolicy](../../backend/src/main/java/com/beone/api/benefit/rule/RulePolicy.java): 카드 전체 실적·취소·제외·한도·신규 카드 유예 정책.
- [NewCardGrace](../../backend/src/main/java/com/beone/api/benefit/rule/NewCardGrace.java): 신규 카드 유예 기간, 간주 실적, 적용 서비스.
- [GraceSpan](../../backend/src/main/java/com/beone/api/benefit/rule/GraceSpan.java): 다음 달 말까지 또는 등록일부터 일정 일수라는 유예 기간 방식.
- [PerformanceWindow](../../backend/src/main/java/com/beone/api/benefit/rule/PerformanceWindow.java): 전월 실적 산정 기간.
- [TransactionDateBasis](../../backend/src/main/java/com/beone/api/benefit/rule/TransactionDateBasis.java): 승인일·이용일·명세서 기재일·매입일 구분.
- [CancellationAttribution](../../backend/src/main/java/com/beone/api/benefit/rule/CancellationAttribution.java): 취소의 실적 차감 귀속 방식.
- [LimitPeriod](../../backend/src/main/java/com/beone/api/benefit/rule/LimitPeriod.java): 한도 적용 기간.
- [LimitDeductionOrder](../../backend/src/main/java/com/beone/api/benefit/rule/LimitDeductionOrder.java): 승인 순서/매입 순서의 차감 방식.
- [FractionalWonPolicy](../../backend/src/main/java/com/beone/api/benefit/rule/FractionalWonPolicy.java): 원 미만 절사/반올림 방식. 타입이 존재해도 개별 카드 약관이 확정되었다는 뜻은 아닙니다.
- [RuleTerm](../../backend/src/main/java/com/beone/api/benefit/rule/RuleTerm.java): Resolved 확정 조건과 Unresolved 미확정 조건. 미확정 값 접근은 예외를 발생시킵니다.
- [ConditionalTerm](../../backend/src/main/java/com/beone/api/benefit/rule/ConditionalTerm.java): 특정 상황에서만 영향을 주는 미확정 조건. mayBeDisregarded()는 관련 상황이 없다고 확인된 경우에만 true입니다.
- [TermTrigger](../../backend/src/main/java/com/beone/api/benefit/rule/TermTrigger.java): 취소, 혜택받은 거래, 공유 한도 소비, 신규 유예, 중복 서비스, 원 미만 결과 등 조건 발생 상황.
- [TriggerEvidence](../../backend/src/main/java/com/beone/api/benefit/rule/TriggerEvidence.java): PRESENT 발생, CONFIRMED_ABSENT 부재 확인, UNDETERMINED 판단 불가.

RuleCatalog.select()는 규칙 없음 → NO_VERIFIED_RULE, 날짜 불일치 → RULE_EXPIRED, 유효하지만 미검증 → NO_VERIFIED_RULE, 유효한 VERIFIED 존재 → Selected 순서로 판단합니다. 계산 가능 서비스라는 표현은 규칙 자체의 차단 조건이 없다는 의미이며 현재 주문의 실적·업종·한도 조건 충족까지 보장하지 않습니다.

### primitive: 산식·개별 조건 판정과 단일 서비스 후보 판정

규칙 모델(`rule`)에 적힌 값으로 서비스 한 건을 계산·판정하는 순수 함수입니다. Spring·DB·`common.web`에 의존하지 않습니다. 결과 타입(`result`)과는 `ServiceCandidateEvaluator`가 `ServiceCandidate`를, `CardServiceCandidateCollector`가 `CardServiceCandidates`를, `OrderBenefitDecider`가 `OrderBenefit`을 만드는 것으로 연결됩니다. `CardBenefit`·`Recommendation`과는 아직 연결하지 않습니다. 같은 입력이면 항상 같은 값을 돌려주고 입력을 바꾸지 않습니다.

- [BenefitArithmetic](../../backend/src/main/java/com/beone/api/benefit/primitive/BenefitArithmetic.java): 건당 대상 금액 상한 적용(상한 초과분은 대상이 아님. 상한은 0원보다 커야 함), 베이시스 포인트 정률(`금액 × bp ÷ 10,000`), 정액(대상 금액과 관계없이 정해진 금액, D-10). 정액이 결제 금액보다 커도 줄이지 않습니다(줄일지 미정). 음수(취소) 금액은 처리 규칙이 없어 거부합니다.
- [UnroundedWon](../../backend/src/main/java/com/beone/api/benefit/primitive/UnroundedWon.java): 원 미만을 버리지 않은 정확한 금액(정수 원 + 1/10,000원 단위 나머지). hasFractionalWon()으로 원 미만 발생 여부를 알 수 있습니다. 원 미만 처리 방식은 카드별로 확인되지 않았으므로(D-03) 확정된 `FractionalWonPolicy`를 받을 때만 round()로 원 단위 금액을 만듭니다.
- [ServiceConditions](../../backend/src/main/java/com/beone/api/benefit/primitive/ServiceConditions.java): 최소 금액(같으면 충족, D-10·사례집 C13), 결제 방식, 대상 업종, 제외 업종을 각각 따로 판정합니다. 여러 조건이 함께 미충족일 때 어느 사유를 우선할지는 미결(D-13)이라 정하지 않습니다.
- [PerformanceTiers](../../backend/src/main/java/com/beone/api/benefit/primitive/PerformanceTiers.java): 확인된 전월 실적으로 서비스 계산 구간과 한도 구간을 고릅니다. 최소 실적 "이상"이면 그 구간입니다. 가장 낮은 구간에도 못 미치면 빈 값이며, 신규 카드 유예 적용 여부는 호출하는 쪽이 판단합니다. 실적 값의 기간이 맞는지는 판정하지 않으며, 음수 실적은 처리 근거가 없어 거부합니다.

```java
// 합성 예시: 상한 2만원, 35%, 원 미만 처리 방식은 확인된 경우에만 적용
Won target = BenefitArithmetic.capTargetAmount(Won.of(30_000), Optional.of(Won.of(20_000))); // 20,000
UnroundedWon raw = BenefitArithmetic.apply(BenefitCalculation.rate(3_500), target);           // 7,000.0000
if (raw.hasFractionalWon()) {
	// 서비스의 fractionalWon이 Resolved일 때만 raw.round(policy). 아니면 확인 필요로 남긴다.
}
```

한도 차감·잔여 한도, 중복 적용 금액 계산, 기간 판정, 월 1회 제한, 신규 유예의 간주 실적 적용은 이 패키지에 없습니다.

#### ServiceCandidateEvaluator: 단일 서비스 후보 판정(#31)

[ServiceCandidateEvaluator](../../backend/src/main/java/com/beone/api/benefit/primitive/ServiceCandidateEvaluator.java)의 `evaluate(service, order, previousMonthPerformance, newCardGrace)`는 위 함수들을 묶어 "주문 한 건에서 이 서비스 하나만 고를 때의 후보"(`ServiceCandidate`)를 만듭니다. 서비스 간 채택·기여액(`Adoption`·`OrderServiceBenefit`)은 정하지 않습니다.

- 지원 범위: 한도 버킷(`limitBuckets`)이 비어 있는, 즉 약관상 한도가 없는 비선택형 서비스. 한도 버킷이 하나라도 있거나 선택형(`requiresSelection`)이면 범위 밖이므로 `IllegalArgumentException`으로 거부합니다. 범위 안에서 근거가 부족하면 거부하지 않고 확인 필요 금액을 돌려줍니다.
- 호출자 전제 조건: 규칙 버전 선택은 끝난 상태입니다(예: `RuleCatalog`가 고른 계산 가능 서비스). 빈 `limitBuckets`는 약관상 한도 없음이 확인된 서비스에만 씁니다. 모델은 한도 정보 누락과 한도 없음을 구분하지 않으므로, 한도 정보가 확인되지 않은 서비스를 넘기지 않는 것은 호출자 책임입니다. 전월 실적은 호출자가 이 카드의 실적 기간에 맞는다고 보증한 값이며, 판정기는 원장에서 산출하거나 기간을 확인하지 않습니다. 신규 유예 근거(`TriggerEvidence`)는 호출자가 이 서비스에 대해 정합니다. 카드가 유예 기간이 아니거나 유예가 이 서비스에 적용되지 않음을 확인했을 때만 `CONFIRMED_ABSENT`입니다.
- 입력 계약: null 입력과 음수 전월 실적은 거부합니다. 음수 실적은 다른 조건이 미충족이어도 거부합니다. 주문 금액이 0원 이하인 주문은 `Order`가 생성 시 거부합니다.

판정 순서는 다음과 같습니다.

1. 제외 업종·대상 업종·결제 방식·최소 금액·실적 구간을 각각 판정해 확인된 미충족 사유를 순서 없는 집합으로 모읍니다. 사유가 하나라도 있으면 다른 근거가 미확정이어도 후보 금액은 확정 0원입니다.
2. 실적이 가장 낮은 구간에도 못 미칠 때 실적 미충족(`NOT_APPLICABLE_PERFORMANCE`)은 신규 유예 근거가 `CONFIRMED_ABSENT`일 때만 확정합니다. 유예는 실적을 간주 실적 이상으로 올려 보므로, 근거가 `PRESENT`·`UNDETERMINED`이면 실적 부족은 확인 필요이고, 최상위가 아닌 구간에 도달한 경우도 더 높은 구간이 될 수 있어 확인 필요입니다. 이는 `ConditionalTerm`·`NewCardGrace`의 기존 계약을 적용한 것이며 새 금융 정책이 아닙니다. 최상위 구간에 도달했거나 실적 조건이 없는 서비스는 유예 근거와 관계없이 계산합니다.
3. 미충족이 없으면 확인 필요 사유를 봅니다. 확인 필요 사유는 `unmetConditions`에 넣지 않고 `BenefitAmount.NeedsConfirmation`의 사유 문자열에 모읍니다.
   - 계산식 미확정(`BenefitService.unresolvedCalculationReasons()`가 비어 있지 않음): 다른 식으로 대체하지 않습니다.
   - 정액이 주문 결제 금액보다 큼: 결제 금액으로 줄이거나 0원·초과 금액으로 확정하지 않습니다. 같으면 확정입니다.
   - 원 미만: 계산 결과에 실제로 원 미만이 생겼을 때만 `fractionalWon`을 봅니다. 확정이면 그 방식으로 처리하고, 미확정이면 확인 필요입니다. 원 미만이 없으면 처리 방식 미확정만으로 막지 않습니다(`ConditionalTerm.mayBeDisregarded`).
4. 위에 걸리지 않으면 (상한을 적용한 대상 금액으로) 계산한 금액을 확정합니다.

중복 적용 조건(`stackableWith`)은 서비스 하나만 고를 때의 후보에는 영향을 주지 않으므로 판정하지 않습니다. 중복·채택은 후속 단계의 책임입니다.

```java
// 합성 예시: 한도 없는 비선택형 서비스, 0원 구간 1%, 원 미만 처리 미확정
ServiceCandidate candidate = ServiceCandidateEvaluator.evaluate(service, order, Won.of(300_000),
		TriggerEvidence.CONFIRMED_ABSENT);
// 10,000원 주문 → 확정 100원. 10,001원 주문(100.01원)이면 원 미만 처리 미확정으로 확인 필요
```

#### CardServiceCandidateCollector: 카드 한 장의 서비스 후보 취합(#33)

[CardServiceCandidateCollector](../../backend/src/main/java/com/beone/api/benefit/primitive/CardServiceCandidateCollector.java)의 `collect(selection, order, state)`는 이미 끝난 규칙 선택 결과(`RuleSelection`)와 주문 한 건, 같은 카드의 월간 상태(`CardMonthlyState`)를 받아 규칙의 서비스마다 후보를 모읍니다. 결과는 [CardServiceCandidates](../../backend/src/main/java/com/beone/api/benefit/result/CardServiceCandidates.java)입니다. 어떤 서비스를 채택할지, 카드 혜택 합계, 한도 차감은 정하지 않으며 `RuleCatalog.select`를 호출하지 않습니다.

- 입력 검증: null은 거부합니다. 선택 결과의 카드 ID(`selection.cardId()`)와 월간 상태의 카드 ID가 다르면 거부합니다(`Order`에는 카드 ID가 없습니다). 전월 실적이 확인된 값이고 음수이면, 판정기를 거치지 않는 분류(지원 범위 밖·규칙 사용 불가 등)에서도 숨겨지지 않도록 먼저 거부합니다. `Selected`의 계산 가능 서비스와 확인 필요 서비스가 규칙의 서비스를 중복·누락 없이 나누지 않으면 거부합니다. 예외를 잡아 다른 분류로 바꾸지 않습니다.
- 규칙 사용 불가(`RuleSelection.Unavailable`): `CardServiceCandidates.Unavailable(cardId, status, reason)`으로 그대로 옮깁니다. 규칙이 없으면 서비스 목록 자체를 모르므로 0원이나 빈 취합 결과로 만들지 않습니다.
- 선택된 규칙(`RuleSelection.Selected`): `CardServiceCandidates.Collected`에 규칙 참조와 세 분류를 담습니다. 규칙의 서비스는 각각 정확히 한 분류에 들어가며, 목록 순서는 규칙의 서비스 순서일 뿐 채택 우선순위가 아닙니다. 합계는 만들지 않습니다.

서비스마다 아래 순서에서 처음 해당하는 분류에 담습니다(2026-10-30 관리자 승인).

1. 지원 범위 밖(`unsupported`, [UnsupportedService](../../backend/src/main/java/com/beone/api/benefit/result/UnsupportedService.java)): 한도 버킷이 있거나 선택형인 서비스. 판정기를 호출하지 않습니다. 금액이나 확정·미충족 의미가 없는 "이번 기능이 판정하지 않음" 분류이며 0원 후보가 아닙니다. 규칙 선택 단계에서 이미 확인 필요로 분리된 서비스라면 그 사유 원문을 `selectionReasons`에 보존합니다.
2. 규칙 선택 단계 확인 필요(`ConfirmationStage.RULE_SELECTION`): `servicesNeedingConfirmation`의 사유를 그대로 담고 확정하지 않습니다.
3. 전월 실적 미입력(`Optional.empty`)·`UNKNOWN`(`ConfirmationStage.PRIOR_PERFORMANCE`): 실적 값을 만들어 넣지 않으므로 실적 조건이 없는 서비스도 확인 필요입니다. 지원 범위 밖 서비스와 규칙 선택 단계 확인 필요 서비스는 이 사유로 덮어쓰지 않습니다.
4. 판정기 결과: 확정 금액(확정 0원과 미충족 사유 포함)은 후보(`candidates`)에 그대로 둡니다. 확인 필요 금액은 후보 목록에 두지 않고 확인 필요(`ConfirmationStage.CANDIDATE_EVALUATION`)로 옮기며, 사유 문자열을 바꾸지 않습니다.

확인 필요 항목은 [ServiceNeedingConfirmation](../../backend/src/main/java/com/beone/api/benefit/result/ServiceNeedingConfirmation.java)(서비스·단계·사유)입니다. `Collected` 생성자는 후보 목록에 확인 필요 금액이 들어오거나 같은 서비스가 두 분류 이상에 들어오는 것을 거부합니다.

판정기에 넘기는 근거는 다음과 같습니다.

- 전월 실적: 확인된 값(`knownValue()`)을 넘깁니다. 출처·기준 기간이 이 카드의 실적 기간에 맞는지는 #31과 같이 호출자가 보증합니다.
- 신규 유예(서비스별): 규칙의 유예 정의(`RulePolicy.newCardGrace`)가 확정이고 비어 있거나(유예 없음 확인), 확정 정의의 대상 서비스에 이 서비스가 없을 때만 `CONFIRMED_ABSENT`입니다. 정의가 미확정이거나 이 서비스가 대상이면 유예 기간을 판정하지 않고 `UNDETERMINED`로 넘깁니다. `PRESENT`는 만들지 않습니다.

`Selected.unresolvedConditionalTerms`는 결과의 `unresolvedConditionalTerms`에 원본 그대로 전달할 뿐 무시하거나 해소하지 않습니다(`ConditionalTerm.mayBeDisregarded`를 호출하지 않습니다). 조건별로 이번 기능이 분류를 바꾸지 않는 근거는 기존 결정입니다.

- 취소 귀속·할인 매출 실적 제외: 전월 실적 산출 쪽 조건이며, 실적 값의 적합성은 호출자가 보증합니다(D-13 #31 범위 결정 2).
- 한도 차감 순서: 한도 있는 서비스에만 해당하며 이번 범위 밖(후속 단계)입니다.
- 신규 유예: 위 `UNDETERMINED` 전달로 판정기가 처리합니다.
- 중복 적용(`MULTIPLE_SERVICES_QUALIFY`): 서비스 하나만 고를 때의 후보에는 영향이 없고 채택 단계의 책임입니다(위 판정기 설명).
- 원 미만 처리: 판정기가 서비스 규칙에서 직접 처리합니다.

따라서 후보의 확정 금액은 "그 서비스 하나만 고를 때의 단독 후보 금액"이며, 위 조건이 해소됐다거나 채택됐다는 뜻이 아닙니다.

```java
// 합성 예시: 규칙 선택은 이미 끝났고, 월간 상태는 같은 카드의 것
CardServiceCandidates result = CardServiceCandidateCollector.collect(selection, order, state);
if (result instanceof CardServiceCandidates.Collected collected) {
	collected.candidates();          // 확정 금액(0원 포함) 후보. 채택 여부는 아직 없음
	collected.needingConfirmation(); // 단계와 사유가 있는 확인 필요
	collected.unsupported();         // 한도·선택형: 이번 기능이 판정하지 않음
}
```

#### OrderBenefitDecider: 카드 한 장·주문 한 건의 혜택 결정(#35)

[OrderBenefitDecider](../../backend/src/main/java/com/beone/api/benefit/primitive/OrderBenefitDecider.java)의 `decide(selection, order, state, evidence)`는 취합기를 내부에서 호출한 뒤, 서비스별 최종 채택(`Adoption`)·기여액과 카드의 이번 주문 혜택을 [OrderBenefit](../../backend/src/main/java/com/beone/api/benefit/result/OrderBenefit.java)으로 돌려줍니다. 근거가 부족하면 금액을 확정하지 않고 `확인 필요`와 구조화된 사유([PendingReason](../../backend/src/main/java/com/beone/api/benefit/result/PendingReason.java))를 남깁니다. 정책은 2026-10-31 사용자 확정(D-13 #35 결정)을 따릅니다. `RuleCatalog.select`는 호출하지 않습니다.

- 입력 검증: null을 거부하고, 근거(`PolicyTermEvidence`)의 카드가 규칙 선택 결과의 카드와 다르면 `IllegalArgumentException`으로 거부합니다. 월간 상태 카드 불일치·음수 실적 등 취합기의 검증은 그대로 적용됩니다.
- 규칙 사용 불가: 취합 결과가 `Unavailable`이면 `OrderBenefit.Unavailable(cardId, status, reason)`을 돌려줍니다. 카드 금액은 `확인 필요`이며 0원이나 빈 결정 결과가 아닙니다.

처리 순서는 다음과 같습니다(후보는 서비스 ID 순으로 처리).

1. 실적과 무관한 미충족(제외 업종·업종·결제 방식·최소 금액)이 하나라도 있는 후보는 실적 미충족이 함께 있어도 `NOT_ADOPTED`·확정 0원입니다.
2. 나머지 후보는 실적 정책(취소 귀속·할인 매출 실적 제외)의 영향을 봅니다. 서비스에 실적 조건이 있고(`BenefitService.hasPerformanceRequirement()`), 원본 `unresolvedConditionalTerms`에 두 조건 중 하나가 있으며, 그 근거가 `CONFIRMED_ABSENT`가 아니면(`ConditionalTerm.mayBeDisregarded`) 영향이 있다고 봅니다. 실적 조건이 없는 서비스는 실적 값이 바뀌어도 금액이 같으므로 영향이 없고, 규칙에서 조건이 확정이면 근거가 필요 없습니다.
3. 실적 미충족만 있는 후보와 미충족 사유 없는 확정 0원 후보(예: 원 미만 절사로 0원): 영향이 있으면 재확인 대상(`rechecks`, [PerformanceRecheck](../../backend/src/main/java/com/beone/api/benefit/result/PerformanceRecheck.java))으로 분리하고, 없으면 `NOT_ADOPTED`·확정 0원입니다. 재확인 후보의 원본 0원은 최종 기여액이 아니므로 `services`에 넣지 않습니다.
4. 양수 후보: 영향이 있으면 실적 정책 사유를 남깁니다.
5. 막는 사유를 멈추지 않고 모두 모읍니다. 확인 필요 서비스(`NEEDS_CONFIRMATION`)·지원 범위 밖 서비스(`UNSUPPORTED`)마다 하나, 실적 정책 영향 후보(`PERFORMANCE_POLICY`)마다 하나, 양수 후보 쌍마다 중복 관계를 봐서 미확정·한쪽에만 적힘(`STACKING_UNRESOLVED`)이나 서로 중복 허용 확정(`STACKING_CONFIRMED`, 중복 계산은 이번 범위 밖)이면 하나입니다. 양쪽 `stackableWith`가 확정이고 서로를 적지 않았을 때만 중복 불가로 확정합니다. 사유는 종류 선언 순서 → 서비스 ID 순으로 정렬합니다.
6. 사유가 없으면 양수 후보 중 후보 금액이 가장 큰 하나를 후보 금액 그대로 `ADOPTED`로 두고 나머지는 `NOT_ADOPTED`·0원입니다. 같은 금액이면 서비스 ID의 `String.compareTo` 오름차순(대소문자 구분, UTF-16 코드 단위, 숫자·로캘 정렬 없음) 첫 서비스를 채택합니다. 카드 금액은 확정 기여액의 합입니다. 사유가 있으면 양수 후보는 모두 `UNDETERMINED`·기여액 `확인 필요`이고 카드 금액도 사유 요약이 담긴 `확인 필요`입니다.

`services`·`rechecks`는 서비스 ID 순이므로 규칙의 서비스 순서가 달라도 같은 결과입니다. 원본 취합 결과(`source`)와 그 `unresolvedConditionalTerms`는 바꾸지 않고 그대로 담습니다. 지원 범위 서비스에서 나올 수 없는 `LIMIT_EXHAUSTED` 후보를 만나면 0원으로 처리하지 않고 `IllegalStateException`으로 거부합니다.

`OrderBenefit.Decided` 생성자는 결과 조합만 확인합니다. 원본 후보와 `services`·`rechecks` 후보의 정확한 일대일 대응, 사유 연결(재확인·확인 필요·범위 밖 서비스마다 해당 종류 사유, 사유의 서비스가 종류에 맞는 분류에 있음), 카드 금액 확정 ⇔ 사유 없음, 확정 0원 후보는 `NOT_ADOPTED`, 확정이면 최댓값·ID 최소 하나만 후보 금액으로 채택·합계 일치, 미확정이면 `ADOPTED` 없음·양수 후보 모두 `UNDETERMINED`, 목록 정렬·불변 복사입니다. 실적 조건 여부, 중복 관계, 근거의 영향, 사유 없는 0원이 실적 정책 검토를 통과했는지처럼 규칙과 근거가 있어야 알 수 있는 판단은 생성자가 확인하지 않으며 결정기와 그 테스트가 맡습니다.

```java
// 합성 예시: 규칙 선택은 이미 끝났고, 근거는 같은 카드의 실적 기간에 대한 것(호출자 책임)
PolicyTermEvidence evidence = new PolicyTermEvidence(cardId, TriggerEvidence.UNDETERMINED,
		TriggerEvidence.UNDETERMINED); // 근거가 없으면 UNDETERMINED
OrderBenefit result = OrderBenefitDecider.decide(selection, order, state, evidence);
if (result instanceof OrderBenefit.Decided decided) {
	decided.services();     // 채택·기여액을 정한 후보(미정 표시 포함)
	decided.rechecks();     // 실적 정책 때문에 재확인할 후보. 원본 0원은 최종 기여액이 아님
	decided.reasons();      // 비어 있지 않으면 카드 금액은 확인 필요
	decided.orderBenefit(); // 확정이면 확정 기여액의 합
}
```

현재 범위의 제약: 실제 카드 4종은 모두 한도 또는 선택형 서비스가 있어 이 결정기에서는 대부분 `확인 필요`가 됩니다. 테스트는 합성 규칙만 사용하며 실제 카드 검증이나 추천 완성을 뜻하지 않습니다. 사유가 있을 때 카드 단위 사유 우선순위, 중복 허용 서비스의 합산, 한도 차감, 카드 간 비교는 정하지 않았습니다.

### result: 결과와 설명 근거

- [ServiceBenefit](../../backend/src/main/java/com/beone/api/benefit/result/ServiceBenefit.java): 서비스별 상태와 현재 주문 혜택.
- [CardBenefit](../../backend/src/main/java/com/beone/api/benefit/result/CardBenefit.java): 카드별 적용 규칙, 현재 주문 혜택, 예상 월 총혜택, 서비스 내역, 주문 이후 잔여 한도.
- [ApplicationStatus](../../backend/src/main/java/com/beone/api/benefit/result/ApplicationStatus.java): 적용·한도 소진·업종/실적/금액/결제 방식 미충족·규칙 부재·확인 필요 등 결과 사유. 금액 형태와 상태의 일관성을 검사합니다.
- [ServiceCandidate](../../backend/src/main/java/com/beone/api/benefit/result/ServiceCandidate.java): 서비스 하나만 고른다고 할 때의 후보 판정(D-13). 후보 금액 `standaloneAmount`와 확인된 미충족 사유 집합을 담습니다. 미충족 사유는 `NOT_APPLICABLE_*` 5종과 `LIMIT_EXHAUSTED`만 명시적으로 허용하고, 사유가 하나라도 있으면 후보 금액은 확정 0원이어야 합니다. 사유 순서는 계약이 아닙니다.
- [Adoption](../../backend/src/main/java/com/beone/api/benefit/result/Adoption.java): 후보를 카드의 최종 금액에 넣었는지. `ADOPTED`(채택)·`NOT_ADOPTED`(채택하지 않음)·`UNDETERMINED`(정할 수 없음).
- [OrderServiceBenefit](../../backend/src/main/java/com/beone/api/benefit/result/OrderServiceBenefit.java): 단일 주문에서 서비스 하나의 후보·채택·실제 기여액 `contribution`을 나눠 담습니다. 미충족 후보는 `NOT_ADOPTED`만, `NOT_ADOPTED`와 확정 0원 후보의 기여액은 확정 0원만, 후보와 기여액이 모두 확정이면 기여액 ≤ 후보 금액만 허용합니다. `UNDETERMINED`는 미채택(0원)일 수도 있으므로 기여액은 확정 0원이나 확인 필요만 허용합니다. 이 조건들은 결과 표현의 논리적 일관성이며, 실제 채택·기여액을 정하는 근거는 후속 계산기의 책임입니다.
- [Recommendation](../../backend/src/main/java/com/beone/api/benefit/result/Recommendation.java): 카드별 결과와 최종 선택 결과. 중복 카드와 결과 없는 선택 등을 차단하고 선택 카드의 월 총혜택이 확정되었는지 검사합니다.
- [RecommendationOutcome](../../backend/src/main/java/com/beone/api/benefit/result/RecommendationOutcome.java): Selected 선택 결과 또는 NotDetermined 추천 불가 사유. Selected는 선택 근거, 동률 카드, 차순위와의 차이, 조건부 여부를 담습니다.
- [SelectionBasis](../../backend/src/main/java/com/beone/api/benefit/result/SelectionBasis.java): 월 총혜택 → 현재 주문 혜택 → 대표 카드 → 카드 ID 순서의 선택 근거를 표현합니다. 대표 카드·ID 선택은 혜택 우월성을 뜻하지 않는 임의 동률 규칙입니다.

- [CardServiceCandidates](../../backend/src/main/java/com/beone/api/benefit/result/CardServiceCandidates.java): 카드 한 장의 서비스 후보 취합 결과(#33). 규칙 사용 불가 `Unavailable(cardId, status, reason)`(`RULE_EXPIRED`·`NO_VERIFIED_RULE`만)과 취합 결과 `Collected(rule, candidates, needingConfirmation, unsupported, unresolvedConditionalTerms)`로 나뉩니다. 후보 목록은 확정 금액만 허용하고, 같은 서비스가 두 분류 이상에 들어가는 것을 거부하며, 목록은 바꿀 수 없는 복사본입니다. 합계·채택은 담지 않습니다.
- [ServiceNeedingConfirmation](../../backend/src/main/java/com/beone/api/benefit/result/ServiceNeedingConfirmation.java)·[ConfirmationStage](../../backend/src/main/java/com/beone/api/benefit/result/ConfirmationStage.java): 확인 필요 서비스와 그 단계(`RULE_SELECTION`·`PRIOR_PERFORMANCE`·`CANDIDATE_EVALUATION`). 사유는 하나 이상이어야 합니다.
- [UnsupportedService](../../backend/src/main/java/com/beone/api/benefit/result/UnsupportedService.java)·[UnsupportedReason](../../backend/src/main/java/com/beone/api/benefit/result/UnsupportedReason.java): 취합기가 판정하지 않는 서비스와 이유(`HAS_LIMIT_BUCKETS`·`REQUIRES_SELECTION`), 규칙 선택 단계 사유 원문. 금액이 없으며 0원 후보가 아닙니다.
- [OrderBenefit](../../backend/src/main/java/com/beone/api/benefit/result/OrderBenefit.java): 카드 한 장·주문 한 건의 혜택 결정 결과(#35). 규칙 사용 불가 `Unavailable(cardId, status, reason)`(카드 금액 `확인 필요`)과 결정 결과 `Decided(source, services, rechecks, reasons, orderBenefit)`로 나뉩니다. 생성자가 확인하는 조합은 위 결정기 절에 적었습니다.
- [PerformanceRecheck](../../backend/src/main/java/com/beone/api/benefit/result/PerformanceRecheck.java): 실적 정책 조건 때문에 최종 채택·기여액을 정하지 못한 원본 후보와 사유. 후보는 실적 미충족만 있는 확정 0원이거나 미충족 사유 없는 확정 0원이어야 하며, 그 0원은 최종 기여액이 아닙니다.
- [PendingReason](../../backend/src/main/java/com/beone/api/benefit/result/PendingReason.java): 카드 금액을 확정하지 못한 사유 `(kind, services, detail)`. 종류는 `NEEDS_CONFIRMATION`·`UNSUPPORTED`·`PERFORMANCE_POLICY`(서비스 1개)와 `STACKING_UNRESOLVED`·`STACKING_CONFIRMED`(서비스 쌍)이며, 서비스 ID 오름차순·공백 없는 설명을 생성 시 확인합니다.

`ServiceCandidate`·`OrderServiceBenefit`은 기존 `ServiceBenefit`과 별개입니다. `ServiceCandidate`는 `ServiceCandidateEvaluator`가 만들고 `CardServiceCandidateCollector`가 모을 수 있지만, 채택 여부와 기여액은 `OrderBenefitDecider`가 정해 `OrderBenefit`에 담습니다. 이 타입들 자체는 채택·기여액을 계산·추론하지 않고, 아직 `CardBenefit`에 연결되지 않았습니다. 미확정 금액을 0원으로 바꾸는 경로도 없습니다.

```java
ServiceCandidate candidate = new ServiceCandidate(ServiceId.of("SYN-SVC"), BenefitAmount.confirmed(1_000), Set.of());
// 다른 서비스가 채택돼 이 후보는 넣지 않음: 기여액은 확정 0원
new OrderServiceBenefit(candidate, Adoption.NOT_ADOPTED, BenefitAmount.confirmed(0));
```

#### OrderBenefitResolver: 규칙 선택부터 주문 혜택 결정까지(#38)

[OrderBenefitResolver](../../backend/src/main/java/com/beone/api/benefit/primitive/OrderBenefitResolver.java)의 `resolve(catalog, cardId, decisionDate, order, state, evidence)`는 `RuleCatalog.select(cardId, decisionDate)`의 결과를 그대로 `OrderBenefitDecider.decide`에 넘깁니다. 결과는 같은 입력으로 두 단계를 직접 호출한 것과 같으며, 규칙 선택 순서와 결정 정책에 판단을 더하지 않습니다.

- 판정일(`decisionDate`)은 규칙 버전을 고르는 날짜이며 **호출자가 정합니다.** 주문의 날짜(`Order.date`)로 대신하지 않습니다. 카드마다 거래 기준일(승인·이용·이용내역서 기재·매입, D-03)이 다르고 개별 거래의 적용 규칙 버전은 확정되지 않았으므로, 주문일·결제일에서 판정일을 정하는 규칙은 아직 없습니다.
- 규칙 없음·판정일 포함 버전 없음·`VERIFIED` 없음은 `OrderBenefit.Unavailable`(`NO_VERIFIED_RULE`·`RULE_EXPIRED`)이며 카드 금액은 `확인 필요`입니다.
- 입력 검증: null을 거부합니다. 월간 상태·근거의 카드가 판정할 카드와 다르면 규칙 사용 가능 여부와 관계없이 취합기·결정기의 기존 검증이 `IllegalArgumentException`으로 거부합니다.

```java
// 합성 예시: 판정일은 호출자가 정한다
OrderBenefit result = OrderBenefitResolver.resolve(catalog, cardId, decisionDate, order, state, evidence);
```

여러 카드를 비교하거나 `CardBenefit`·`Recommendation`을 만들지 않습니다.

결과 모델은 실제로 카드를 비교·선택하는 알고리즘이 아닙니다. 조건부 추천 여부를 판단하는 것도 후속 계산 로직의 책임입니다.

## 데이터·처리 흐름

1. Order, HeldCard, Ledger, CardMonthlyState 등을 RecommendationInput으로 묶습니다.
2. RuleCatalog가 판단 날짜에 적용 가능한 검증 규칙을 선택합니다. 카드 한 장·주문 한 건은 `OrderBenefitResolver`가 이 선택과 아래 결정을 잇습니다(판정일은 호출자 입력).
3. 미확정 약관과 입력 상태를 고려해 혜택을 계산하고 월간 후보를 비교합니다. 서비스 한 건의 산식·개별 조건 판정과 한도 없는 비선택형 서비스의 단일 후보 판정, 규칙 선택 결과의 서비스 후보 취합(`CardServiceCandidateCollector`), 한도 없는 비선택형 서비스에 대한 카드 한 장·주문 한 건의 채택·카드 금액 결정(`OrderBenefitDecider`)은 `primitive`에 있지만, 카드 간 비교·월간 최적화로 추천을 내는 **엔진은 후속 구현입니다.**
4. 계산 로직이 ServiceBenefit(또는 후보·채택·기여액을 나눈 ServiceCandidate·OrderServiceBenefit·OrderBenefit), CardBenefit, Recommendation을 구성합니다. 현재 `OrderBenefit`까지는 결정기가 만들고, `CardBenefit`·`Recommendation`은 결과를 담는 모델과 일관성 검사만 있습니다.

## 짧은 코드 예시

아래는 모델 사용 예시이며 계산 결과나 카드 약관 확정 근거가 아닙니다. 클래스는 해당 패키지에서 import합니다.

```java
CardId card = CardId.of("09271"); // 앞자리 0 유지
Won payment = Won.of(10_000);
BenefitCalculation rate = BenefitCalculation.rate(100); // 1%를 표현
BenefitCalculation fixed = BenefitCalculation.fixed(2_500);

BenefitAmount zero = BenefitAmount.confirmed(0); // 확정된 0원
BenefitAmount unknown = BenefitAmount.needsConfirmation("잔여 한도 확인 필요");
// unknown.confirmedWon()은 예외: 모르는 금액을 0원으로 대체하지 않음

RuleTerm<FractionalWonPolicy> rounding = RuleTerm.unresolved("공식 근거 확인 필요");
```

## 제약과 주의 사항

- record 생성자에서 잘못된 값을 조기에 차단합니다. List.copyOf(), Map.copyOf(), Set.copyOf()로 컬렉션 자체의 외부 변경을 막습니다.
- 금액은 정수 원화, 비율은 basis point 단위 정수로 표현합니다. 100 basis points = 1%, 10,000 = 100%입니다.
- UNKNOWN 상태, Unresolved 약관, NeedsConfirmation 혜택은 서로 다른 종류의 불확실성입니다. 모두 기본값이나 0원으로 숨기지 않습니다.
- 미확정 조건은 관련 상황의 부재가 확인된 경우에만 무시합니다. 증거 없음은 부재가 아니라 UNDETERMINED입니다.
- 날짜는 Asia/Seoul의 달력 날짜라는 입력 계약입니다. LocalDate 자체가 시간대를 변환하는 것은 아닙니다.
- 모델 검증과 규칙 선택 성공은 실제 공식 약관의 정확성 또는 추천 엔진 정확성을 자동으로 입증하지 않습니다.

## 현재 구현 범위와 관련 문서

구현된 범위는 위 모델, 생성 시 일관성 검사, 규칙 선택, 일부 기간·취소 증거 보조 로직, 서비스 한 건의 산식·개별 조건 판정, 한도 없는 비선택형 서비스의 단일 후보 판정, 카드 한 장의 서비스 후보 취합, 한도 없는 비선택형 서비스에 대한 카드 한 장·주문 한 건의 혜택 결정(#35)과 규칙 선택부터 결정까지의 연결(#38, 판정일은 호출자 입력), 서비스 후보·취합·채택·기여액·주문 혜택 결과 타입입니다. 카드 간 비교·추천, 한도·선택형 판정, 중복 합산, 최적화 엔진과 외부 연동은 별도 구현이 필요합니다. `primitive` 테스트는 가상 업종·서비스의 합성 값이며 실제 카드 약관 검증이나 평가 통과를 뜻하지 않습니다.

테스트는 [benefit 테스트 디렉터리](../../backend/src/test/java/com/beone/api/benefit/)에서 확인합니다. fixture 매핑 테스트를 계산 결과 검증으로 혼동하지 않습니다. 이 문서 작성 과정에서는 테스트를 실행하지 않았습니다.

- [전체 구조](../architecture.md)
- [PRD](../PRD.md)
- [결정 기록](../decisions.md)
- [카드 혜택 조사](../card-benefit-research.md)
- [평가 사례집](../evaluation-casebook.md)

패키지·클래스 책임·입출력·검증 조건 변경 시 코드와 같은 PR에서 이 문서를 갱신합니다. 갱신 규칙은 [CLAUDE.md](../../CLAUDE.md), PR 확인 항목은 [CONTRIBUTING.md](../../CONTRIBUTING.md)를 따릅니다.
