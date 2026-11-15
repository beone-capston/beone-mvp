# #35 카드 한 장·주문 한 건의 OrderBenefit 결정기

브랜치 `feat/35-order-benefit-decider`, 시작 기준 `origin/develop` `76198ec`.

## 2026-10-31 · 주문 혜택 결정기 구현 · 완료(커밋 전)
- 한 일: #33 취합기를 내부에서 호출해 서비스별 최종 채택·기여액과 카드의 이번 주문 혜택을 정하는 `OrderBenefitDecider`를 `primitive`에, 결과 타입 `OrderBenefit`(`Unavailable`·`Decided`)·`PerformanceRecheck`·`PendingReason`을 `result`에, 실적 정책 근거 입력 `PolicyTermEvidence`를 `input`에 추가했습니다. 기존 타입·취합기·판정기·fixture·빌드 설정은 바꾸지 않았습니다.
- 서비스에서 달라진 점: 한도 없는 비선택형 서비스만 있는 카드는 주문 한 건의 카드 금액을 확정할 수 있습니다. 근거가 부족하면(확인 필요·지원 범위 밖 서비스, 중복 관계 미확정·중복 허용, 취소 귀속·할인 매출 실적 제외의 영향) 카드 금액은 `확인 필요`이고 구조화된 사유가 남습니다. 실제 카드 4종은 한도·선택형 서비스가 있어 대부분 `확인 필요`이며, 카드 간 비교·추천은 아직 없습니다.
- 지키는 조건: 규칙 사용 불가는 `Unavailable`로 남고 0원이 되지 않습니다. 실적과 무관한 미충족 후보는 `NOT_ADOPTED`·0원, 실적 미충족만 있거나 사유 없는 0원 후보는 실적 정책 영향이 있으면 `rechecks`로 분리하고(원본 0원은 최종 기여액이 아님) 없으면 `NOT_ADOPTED`·0원입니다. 막는 사유가 있으면 양수 후보는 모두 `UNDETERMINED`·기여액 확인 필요이고 `ADOPTED`가 없습니다. 사유가 없으면 최댓값 하나를 채택하고 동률은 서비스 ID `String.compareTo` 오름차순으로 정하며, 카드 금액은 확정 기여액의 합입니다. 결과는 입력 순서와 무관하게 서비스 ID 순입니다. 원본 취합 결과와 `unresolvedConditionalTerms`를 그대로 보존합니다. null, 근거·규칙 카드 불일치와 취합기가 거부하는 입력(상태 카드 불일치·음수 실적 등)은 거부합니다.
- 범위 결정: 정책(U1·U2·U3·U4·U6)과 기술 선택은 2026-10-31 사용자 확정(이슈 #35). 사유 쌍 단위 판정, 실적 정책 사유 대상, 생성자의 사유 종류별 소속·정렬 검증, 확인 필요 문구는 2026-10-31 관리자 승인(구현 전 계약 점검). D-13에 #35 결정을 기록하고 정해진 미결 항목만 정리했습니다.
- 남은 제한: `CardBenefit`·`Recommendation` 연결(후속 필수), 한도·선택형 판정과 한도 차감, 중복 허용 서비스의 합산, 할인 매출 근거 생성과 원장에서 근거를 만드는 연결, `RuleCatalog.select`를 포함한 통합 진입점, 카드 단위 사유 우선순위는 하지 않습니다. 근거의 실적 기간 적합성은 호출자 책임입니다. 테스트 값은 가상 카드·서비스·업종의 합성 값이며 실제 약관 검증이 아닙니다.
- 문서: `docs/domains/benefit.md`(결정기 절, input·result 타입 설명, 범위·흐름 문장), `docs/decisions.md` D-13(#35 결정, 미결 목록 정리), 이 기록과 `README.md`(#33·PR #34 병합 `76198ec` 반영). `docs/architecture.md`는 도메인·패키지 변경이 없어 수정하지 않았습니다.

## 2026-10-31 · 검증과 자체 리뷰 · 완료
- 검증: `./gradlew test --rerun-tasks`, `./gradlew build --rerun-tasks`, `./gradlew javadoc --rerun-tasks`, `git diff --check`, 신규 파일 행 끝 공백 검사, 수정 문서 상대 링크·앵커 검사. 모두 실제 실행·통과(단위 363·웹 25·통합 3개, skip 0. 기준 `76198ec` + 미커밋 변경). 신규 테스트는 단위 97개(결정기 37, 결과·입력 타입 60)이며 합성 값 검증입니다. Javadoc 경고는 변경 전 기준선 277건과 위치·내용이 같고, 신규 main 파일 5개는 doclint 경고 0건입니다.
- 자체 리뷰: 작업자 본인의 읽기 전용 내부 리뷰이며 팀원 승인·외부 리뷰가 아닙니다. 신규 공개 타입의 Javadoc 누락(경고 13건)을 찾아 보완했고, 그 뒤 `test`·`build`·`javadoc`을 다시 실행했습니다.
- 다음: 커밋·push·PR은 사용자 승인 대기.

## 2026-10-31 · 주석 보완·로컬 검증 · 완료(커밋 전)
- 한 일: PR #36 리뷰의 Docstring Coverage 지적에 따라 설명이 없던 private 메서드에 Javadoc을 달았습니다. `OrderBenefitDecider`(`decided`·`unsupportedDetail`·`pendingSummary`·`notAdopted`, `affectingPolicyTerms`·`stackingReasons`의 @param·@return), `OrderBenefit.Decided`의 검증 메서드 9개, `PendingReason.compareServices`가 대상이며 책임·호출 전제·정책(U2·U3·U4·U6)·확인하지 않는 판단을 적었습니다. 로직·시그니처·문자열·테스트는 바꾸지 않았습니다.
- 검증: `.java` 변경 줄이 모두 주석·공백임을 스크립트로 확인했습니다. `./gradlew build --rerun-tasks`·`./gradlew javadoc --rerun-tasks`는 실제 실행·통과(단위 363·웹 25·통합 3개, skip 0)했고, 제한 없는 Javadoc 경고는 기준선 277건과 줄 번호까지 같습니다. 로컬 결과이며 CodeRabbit 수치가 기준을 넘는다는 뜻은 아닙니다.
