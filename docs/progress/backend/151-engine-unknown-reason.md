# #151 엔진 입구 사유 `POST_SNAPSHOT_ACTIVITY_UNRESOLVED` (결정 32 Q6)

브랜치 `fix/151-engine-unknown-reason`, 시작 기준 `origin/develop` `a682fce`(#144 병합 뒤, 결정 34).

## 2026-11-24 · 스냅샷 뒤 활동 영향 불확정 사유 분리 · PR(`develop` 대상, 병합 전)

### 문제
- #120의 [`SnapshotUsage`](../../../backend/src/main/java/com/beone/api/recommendation/application/SnapshotUsage.java)는 스냅샷 뒤 승인의 차감액을 모르거나(`null`), 차감액이 0이 아닌 승인을 스냅샷 뒤 취소한 버킷을 엔진 입구에 `StateInput.notEntered()`로 넘겼다. 사용자는 값을 입력했으므로 사유 `NOT_ENTERED`(입력하지 않음)는 사실과 다르다([#120 기록](120-recommendation-usage-links.md)의 "엔진 사유 추가 후보").

### 바꾼 것
- [`StateInput`](../../../backend/src/main/java/com/beone/api/benefit/engine/StateInput.java): `UNKNOWN` 사유 허용 목록에 `POST_SNAPSHOT_ACTIVITY_UNRESOLVED`를 더하고 만들기 메서드 `postSnapshotActivityUnresolved()`를 추가했다. 값이 있는 입력(`MANUAL` 등)의 `usable=false` 사유로는 받지 않는다.
- `SnapshotUsage`: 위 경우 입력한 값(버킷 사용액, 그 밖의 버킷 사용액)을 새 사유로 넘긴다. 스냅샷 값이 원래 미입력(값 `null`)이면 그 값을 그대로 둔다. 진짜 미입력은 `NOT_ENTERED`다.
- 바꾸지 않은 것: 금액·혜택 계산, `UNKNOWN`·`확인 필요` 판정, 한도 복원 규칙(D-17, 여전히 없음), CODEF 반영, `INFERRED`, 출처 충돌(C1), 월간 상태 사유 [`StateReason`](../../../backend/src/main/java/com/beone/api/monthlystate/application/StateReason.java)(월간 상태 API 응답 값이며 엔진 입구 사유와 별개).

### 이름 근거 (결정 28, 내부 이름)
- 엔진 입구 기존 사유는 대문자 스네이크 상태 이름이다(`NOT_ENTERED`, `SOURCE_CONFLICT`, `NEGATIVE_UNCONFIRMED_ATTRIBUTION`, 거부되는 `PERIOD_MISMATCH`).
- `POST_SNAPSHOT`: 원인이 월간 상태 스냅샷(`snapshotAt`) 뒤의 활동이다(D-16, `docs/api/monthly-states.md`의 스냅샷 뒤 승인·취소 설명).
- `ACTIVITY`: 원장 조회 `LedgerQueryService.activityAfter`와 그 결과 `LedgerActivity`(승인·취소 모두)의 이름을 따랐다.
- `UNRESOLVED`: `SnapshotUsage`가 이 버킷을 이미 `unresolved`·`otherUnresolved`로 불렀고, 값을 정하지 못했다는 뜻이다(0이나 미입력이 아님).

### 공개 응답 영향
- 없음. 엔진은 `StateInput.reason`을 입력 검사에만 쓰고 [`EngineInputs`](../../../backend/src/main/java/com/beone/api/benefit/engine/EngineInputs.java)에서 사유 없는 `UNKNOWN` 상태값으로 바꾼다. 추천 응답·저장 문서에 이 사유가 들어가는 경로가 없다. 그래서 `docs/api/*.md`는 고치지 않았고 웹·통합 단언도 추가하지 않았다. 엔진·추천 도메인 문서([benefit](../../domains/benefit.md), [recommendation](../../domains/recommendation.md))만 고쳤다.

### 테스트
- `StateInputTest`: 새 사유는 `UNKNOWN`·값 없음·쓸 수 없음이고, 값이 있는 입력의 사유로는 거부된다. `notEntered()`의 사유는 `NOT_ENTERED`다.
- `SnapshotUsageTest`: 기존 확인 필요 사례 3개의 기대값을 새 사유로 바꿨다(사유 단언만 바뀌고 확인 필요인 점은 같다). 원래 미입력인 값은 같은 활동이 있어도 `NOT_ENTERED`로 남는 사례와, 새 사유의 값이 `UNKNOWN`·`null`·쓸 수 없음인 사례를 더했다.
- `DefaultRecommendationEngineTest`: 같은 버킷을 `notEntered()`와 새 사유로 넣은 두 엔진 결과가 같다(`NEEDS_CONFIRMATION`).
