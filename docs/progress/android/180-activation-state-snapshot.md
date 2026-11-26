# #180 60초 활성 상태 브리지 응답의 단일 시점 스냅샷

- 이슈: [#180](https://github.com/beone-capston/beone-mvp/issues/180)
- 브랜치: `fix/180-activation-state-snapshot` (기준 `origin/develop` `0dab978`)
- 근거: [#171 단계 기록](171-android-release-guards.md)의 '발견한 것'(PR #174 리뷰 포인트), 결정 38 관리자 기술 판단 — 새 정책 선택이 아니라 현행 60초 계약의 일관성을 깨는 기술 오류이므로 재현 → 단일 시점 스냅샷 최소 수정 → 회귀 검증으로 처리
- 성격: 버그 수정. 공개 계약·보안 정책·Gradle 설정은 바꾸지 않았습니다.

## 2026-11-24 · 재현·수정 · draft PR

### 문제

[`ActivationBridgePort.currentState`](../../../android/app/src/main/java/com/beone/app/bridge/ActivationBridgePort.kt)가 활성 상태(`viewModel.state`)와 남은 시간(`viewModel.remainingMillis()`)을 따로 시계를 읽어 구했습니다. 첫 읽기에서 활성이고 두 번째 읽기 전에 만료 시점을 넘으면 브리지 `state` 응답이 `active=true`·`remainingMs=0`이 됩니다. 계약상 활성이면 남은 시간 > 0, 남은 시간 0이면 비활성(`EXPIRED`)이어야 합니다(PRD §4.3, D-21 C3, [브리지 v1](../../android/bridge-contract-candidate.md) B5).

### 재현

[`ActivationBridgePortTest.stateAndRemainingComeFromOneClockReadWhenExpiryPassesBetweenReads`](../../../android/app/src/test/java/com/beone/app/bridge/ActivationBridgePortTest.kt): 인증 성공 뒤 만료 2ms 전에서, 시계를 읽을 때마다 1ms씩 진행하는 단계 시계로 `currentState()`를 부릅니다. 수정 전 코드에서 다음처럼 실패했습니다.

```text
AssertionError: active=true remainingMs=0 expected:<true> but was:<false>
```

### 수정

- [`VirtualCardActivation.snapshot()`](../../../android/app/src/main/java/com/beone/app/auth/VirtualCardActivation.kt): 시계를 한 번 읽은 시점으로 상태와 남은 시간을 함께 돌려줍니다(`ActivationSnapshot`). 기존 `state`·`remainingMillis()`도 이를 거치며 시그니처와 결과는 같습니다.
- [`VirtualCardActivationViewModel.activationSnapshot()`](../../../android/app/src/main/java/com/beone/app/auth/VirtualCardActivationViewModel.kt): 응답 대기 시간 만료를 먼저 반영(`refresh`)한 뒤 스냅샷을 읽습니다.
- `ActivationBridgePort.currentState`: 스냅샷의 상태·남은 시간과 요청 단계로 `BridgeState`를 만듭니다. 읽는 순서(대기 시간 반영 → 활성 상태 → 요청 단계)는 그대로입니다.

### 바꾸지 않은 것

- 공개 메서드 시그니처(`BridgeAuthPort`, `state`, `remainingMillis()`), 브리지 메시지 형식, 60초 값, 만료 판정(`>=`)
- 활성이면서 인증 중인 조합 처리(A1, AG-03 결정 대기)
- 보안 정책, Gradle 설정, debug 인증 점검 화면

### 검증

`android/`에서 실행했습니다. adb·기기·에뮬레이터, `assembleRelease`, 서명은 사용하지 않았습니다.

| 명령 | 결과 |
| --- | --- |
| `./gradlew assembleDebug testDebugUnitTest lintDebug` | 성공. debug 단위 테스트 174 → 175건, 실패 0. lint 오류 0·경고 8 |
| `./gradlew testReleaseUnitTest` | 성공. release 단위 테스트 178 → 179건, 실패 0 |

재현 테스트, 기존 U5 경계 테스트(`VirtualCardActivationTest`), 브리지·활성화 테스트 전체가 통과했습니다. 실기기에서 관측한 것은 아닙니다.

### 문서

Android 설명 문서(`docs/android/`, `docs/domains/`)는 고치지 않았습니다. 외부에서 보이는 동작과 계약은 그대로이고, 문서에 `currentState`의 시계 읽기 방식을 적은 곳이 없습니다.
