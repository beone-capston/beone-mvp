# #80 혜택 계산 엔진 결과 연결 (D-22)

브랜치 `feat/80-engine-result-link`, 시작 기준 `origin/develop` `702c329`.

## 2026-11-16 · 엔진 구현체와 입구 평가 · 완료(PR 전)
- 한 일: `RecommendationEngine`의 구현체 `DefaultRecommendationEngine`(Spring 빈)을 추가했습니다. 입력 변환·기간 판단(`EngineInputs`), 카드 평가(`CardEvaluator` → `CardEvaluation`), 한도 구간 실적(`primitive.LimitTierPerformance`)을 새로 두고, 기존 `RuleCatalog`·`OrderBenefitDecider`를 그대로 씁니다.
- 서비스에서 달라진 점: 업무 API가 `EngineRequest`로 보유 카드 전체의 금액·카드 상태·서비스 결과·주문 뒤 남은 한도·승인 때 버킷 차감액(`usageEffects`)·비교/제외 카드·D-12 선택·대표 카드 비교를 받을 수 있습니다. HTTP·DB·화면에는 아직 연결되지 않았습니다(업무 API 담당).
- 사용자 결정(2026-11-16)
  1. 실적 정책 근거 입력: `HeldCardInput`에 `PolicyEvidenceInput(cancellation, benefitedSales)`를 필수로 추가했습니다. 값은 `PRESENT`·`CONFIRMED_ABSENT`·`UNDETERMINED`이며, 업무 API가 확인하지 못했으면 `UNDETERMINED`(= 실적 의존 서비스 `확인 필요`)입니다. 근거: 입력에 근거가 없으면 엔진이 하나를 골라야 하는데, 기존 평가 실행기를 `UNDETERMINED`로 임시 실행하니 대상 27건 중 17건이 기대값과 달랐습니다(실행 뒤 파일 원복). D-22의 "음수 실적의 정책 근거는 입력에 없다, 근거 없음으로 `확인 필요`" 문장은 음수 실적에 관한 것이고, 음수 전월 실적은 이번에도 `확인 필요`로 둡니다.
  2. 새 상태값 `NO_CONTRIBUTION`: 미충족 사유 없이 기여액이 확정 0원인 서비스(원 미만 절사로 0원, 다른 서비스에 밀려 미채택)의 상태입니다. 후보의 미충족 조건으로는 쓰지 않습니다. 카드 상태 4의 순서에서는 각 단계의 마지막입니다.
- 지키는 조건: 기준 기간이 정확히 맞지 않거나 `usable=false`이거나 값이 없으면 `UNKNOWN`이며 0원으로 바꾸지 않습니다. 월간 합계 = 현재 주문 혜택(K4), 사용자 제외만 있으면 `conditional=false`, 비교 카드 0장이면 `NOT_DETERMINED`, 1장이면 `decidedBy`·차액 `null`, `selected`는 모드와 무관하게 D-12 최적 카드입니다. 기여액이 `확인 필요`인 체인의 남은 한도·차감액은 `null`입니다. 규칙·평가를 `VERIFIED`로 올리지 않았고 fixture·기대값은 바꾸지 않았습니다.
- 검증(기준 `702c329` + 미커밋 변경, 이 기록 파일만 검증 뒤 작성): `./gradlew test`·`webTest`·`integrationTest`·`build`·`javadoc`(각 `--rerun-tasks`) 실제 실행·통과. 단위 839·웹 25·통합 6개, 실패 0, skip 0. 통합 테스트는 Testcontainers 임시 컨테이너만 썼고 기존 컨테이너·볼륨 목록은 실행 전후 같습니다.
  - 입구 평가 `EngineEvaluationRunnerTest`: 대상 27건 모두 카드 금액·카드 상태·서비스 기여액/상태·선택 카드·동률 근거·월간 합계가 fixture와 일치합니다. 기존 `EvaluationCaseRunnerTest`도 그대로 통과합니다.
  - `DefaultRecommendationEngineTest` 20개: 손계산 남은 한도(C10 GREAT 주문 뒤 0원, M04 CHECK 5,000 → 3,000)·차감액, 사용액 미입력 시 체인 `null`, 기간 불일치·음수 실적·`usable=false`·근거 `UNDETERMINED`, 사용자 제외만/다른 제외, 0장·1장, 규칙 없음, D-12 동률(카드 ID·대표 카드)·월간 합계 차이, 대표 카드 고정(차이·미확정·규칙 없음), `NO_CONTRIBUTION`.
  - 통합: 서버 시작 때 `RecommendationEngine` 빈이 주입되는지 확인합니다.
- 범위 밖 사례의 실제 출력(기대값과 비교하지 않음)
  - C09: `09174` `NOT_APPLICABLE_PERFORMANCE` 0원, 선택 `09174`.
  - C14: `01664` `APPLIED` 2,500원, 선택 `01664`(fixture 기대 0원·실적 미충족과 다름. 원장의 취소 귀속을 계산하지 않기 때문).
  - C20: `09174` `APPLIED` 5,000원, `09271` `APPLIED` 100원, 선택 `09174`(fixture 기대 `09271`과 다름. 예정 소비를 넣지 않기 때문, K4).
- 공용 문서 후속(담당 범위 밖이라 이 PR에서 고치지 않음)
  - `docs/api/` 명세에 `policyEvidence` 입력과 `services[].applicationStatus`의 `NO_CONTRIBUTION`이 없습니다. `noteCodes` 4종의 문구 대응도 없습니다.
  - `docs/decisions.md` D-22 행의 "입구 타입만 구현했고 계산 연결은 후속 PR"과 2026-11-16 사용자 결정 2건의 기록이 필요합니다.
- 남은 제한: 원 미만 절사로 0원인 전 가맹점 서비스만 있는 주문은 D-22 카드 상태 4-②가 먼저라 카드 상태가 다른 특정 업종 서비스의 `NOT_APPLICABLE_CATEGORY`가 될 수 있습니다(테스트로 고정). 신규 유예 정의가 미확정인 규칙은 남은 한도를 `null`로 둡니다(현재 규칙 4종에는 없음). 사용자 제외·매핑 미확정 카드는 계산하지 않아 차감액을 내지 않습니다. 선택형 서비스 금액, 예정 소비, 원장, 모의 취소 되돌림은 하지 않습니다.
