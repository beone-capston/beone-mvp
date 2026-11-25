# #134 월간 상태 출처 충돌 계산 기준 선택 API (C1)

브랜치 `feat/134-source-selections`, 시작 기준 `origin/develop` `876b515`.

## 2026-11-18 · source-selections PUT, 충돌 없음 409 · PR(`develop` 대상, 병합 전)
- 근거: [`docs/api/monthly-states.md`](../../api/monthly-states.md) PUT source-selections, [`docs/api.md`](../../api.md) 업무 오류 코드 표(월간 상태 2차 행, 가상 포트폴리오 규칙), D-04·D-16·D-19, C0 관리자 판단 P10(요청 검증 400, 카드 404, 충돌이 없으면 항상 409 `SOURCE_CONFLICT_NOT_FOUND`).
- 한 일:
  - `monthlystate.web`: `MonthlyStateController`에 `PUT /source-selections`, 요청 `SourceSelectionRequest`(네 필드 필수, `item`·`selectedSource` 계약 값만, 기간은 기존 `PeriodRequest`), 오류 코드 `SOURCE_CONFLICT_NOT_FOUND` 409, 처리기 한 개.
  - `monthlystate.application`: `MonthlyStateService.selectSource`(기존 카드 확인 재사용 → `SourceConflictNotFoundException`), 입력 모델 `SourceSelection`.
  - Flyway 없음, 테이블 없음. `card`·`codef`·`common/**`·`user/**`·`application*.yml`·`docs/api.md`·`docs/api/**`·`docs/decisions.md`는 바꾸지 않았습니다.
  - 문서: [monthlystate 안내](../../domains/monthlystate.md#출처-충돌-선택-134)의 출처 충돌 선택 절과 구현 범위·구조·테스트, [전체 구조](../../architecture.md)의 구현 API 목록·패키지 트리.
- 정한 동작: 충돌 경로가 없는 이유는 월간 상태가 `MANUAL`만 저장하고, CODEF 실적은 참고값이라 월간 상태에 들어오지 않으며(D-16, #129 `usedInCalculation=false`), `INFERRED`는 보류이고, 참고해 입력한 `MANUAL` 값과 그 CODEF 값은 충돌이 아니기 때문입니다. 계약의 "해당 항목에 그 출처 값이 없음" 400과 한도 묶음 ID 확인은 충돌 후보가 있어야 판단할 수 있어 지금은 충돌 없음 409가 먼저입니다. 가상 포트폴리오는 docs/api.md에 따라 `PORTFOLIO_READ_ONLY`가 아니라 같은 판정입니다. 서비스가 예외 없이 돌아오면 500으로 끝내 200을 지어내지 않습니다.
- 확장 지점: 두 번째 출처가 결정으로 열리면 `selectSource`에서 출처별 후보 수집·충돌 판정, `conflictVersion` 대조와 `SOURCE_CONFLICT_CHANGED`, 출처 값 없음 400, 선택 저장과 200 응답, 조회 `conflicts[]`를 함께 만듭니다.
- 서비스에서 달라진 점: 화면이 출처 충돌 선택을 부르면 계약의 409 응답을 받습니다. 충돌이 화면에 나타나는 일은 아직 없습니다. frontend 변경 없음.
- 검증: `./gradlew test webTest integrationTest build javadoc --rerun-tasks` 결과는 PR 본문에 기록합니다.
- 로컬·테스트 DB만 사용했고 서버 DB 적용·배포는 하지 않았습니다.
