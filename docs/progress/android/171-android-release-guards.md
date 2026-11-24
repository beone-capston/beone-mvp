# #171 release 보호 조건과 60초 만료 경계 단위 테스트 보강

- 이슈: [#171](https://github.com/beone-capston/beone-mvp/issues/171)
- 브랜치: `test/171-android-release-guards` (기준 `origin/develop` `d3ab9f5ccad58b7a4c084357583234248e39fddf`)
- 근거: MVP Android 갭 점검 7-2절의 단위 테스트 후보 U1·U2·U3·U5·U6(갭 AG-01·09·10·12·13), 결정 36
- 성격: 테스트 코드만 추가했습니다. 제품 코드(`android/app/src/main`·`src/debug`·`src/release`의 비테스트 파일)와 Gradle 설정, 공개 계약은 바꾸지 않았습니다.

## 2026-11-24 · 테스트 추가 · PR 대기

### 한 일

| ID | 위치 | 고정하는 내용 |
| --- | --- | --- |
| U1 | [`testRelease/.../ReleaseBuildConfigTest.kt`](../../../android/app/src/testRelease/java/com/beone/app/ReleaseBuildConfigTest.kt) | release `BuildConfig.DEBUG == false`. `MainActivity`의 WebView 원격 디버깅은 이 값이 true일 때만 켜집니다. 기존 `ReleaseBridgeConfigTest`에도 같은 단언이 있으며, 이 테스트는 원격 디버깅 근거를 따로 이름 붙여 둡니다 |
| U2 | [`test/.../MainManifestTest.kt`](../../../android/app/src/test/java/com/beone/app/MainManifestTest.kt) | `main/AndroidManifest.xml` 소스를 파싱해 activity가 `.MainActivity` 하나(activity-alias 없음), `VIEW` action·`BROWSABLE` category·`data` 요소 없음, `DeviceAuthCheckActivity` 언급 없음 |
| U3 | [`test/.../MainSourceSetGuardTest.kt`](../../../android/app/src/test/java/com/beone/app/MainSourceSetGuardTest.kt) | `DebugAuthTrace`·`DeviceAuthCheckActivity` 소스가 `src/debug`에만 있고 `src/main`에 없음, `src/main` 소스에 `import android.util.Log` 없음. 경로 해석이 틀려 빈 목록으로 통과하지 않도록 debug 쪽 파일 존재를 먼저 확인합니다 |
| U5 | [`test/.../VirtualCardActivationTest.kt`](../../../android/app/src/test/java/com/beone/app/auth/VirtualCardActivationTest.kt) | 만료 경계에서 남은 1ms면 `active=true`·`remainingMs=1`, 남은 0ms면 `active=false`·`remainingMs=0`·`EXPIRED`(브리지 `BridgeState.of`까지 거친 값). 경과 59,990~60,010ms 구간에서 "활성 ⇔ 남은 시간 > 0" |
| U6 | [`testRelease/.../ReleaseNavigationPolicyTest.kt`](../../../android/app/src/testRelease/java/com/beone/app/web/ReleaseNavigationPolicyTest.kt) | release 시작 주소로 만든 탐색 정책이 `about:blank`·`blob:`·`intent:`·`market:` 주소를 허용하지 않고 메인 프레임 요청을 막음. 시작 주소 자체는 허용됨을 먼저 확인합니다 |

U5 기대값은 구현이 아니라 계약에서 정했습니다. 활성 시간은 인증 성공 시점부터 60초이고(PRD §4.3-3·4, D-21 C3), 웹은 `remainingMs`가 끝나면 `state.get`으로 만료(`EXPIRED`)를 받습니다([브리지 v1](../../android/bridge-contract-candidate.md) B5). 따라서 남은 시간 0은 비활성이어야 하고, 활성이면 남은 시간은 0보다 커야 합니다. 요청 단계는 `Idle`만 썼고, 활성이면서 인증 중인 조합의 기대값은 넣지 않았습니다(AG-03 결정 대기).

### 검증

`android/`에서 실행했습니다. adb·기기·에뮬레이터, `assembleRelease`, 서명은 사용하지 않았습니다.

| 명령 | 결과 | 시간 |
| --- | --- | --- |
| `./gradlew assembleDebug testDebugUnitTest lintDebug` | 성공. debug 단위 테스트 166 → 174건, 실패 0. lint 오류 0·경고 8 | 약 12초 |
| `./gradlew testReleaseUnitTest` | 성공. release 단위 테스트 166 → 178건, 실패 0 | 약 4초 |

### 발견한 것(고치지 않음)

- U5의 고정 시계 테스트는 통과합니다. 다만 [`ActivationBridgePort.currentState`](../../../android/app/src/main/java/com/beone/app/bridge/ActivationBridgePort.kt)는 활성 상태와 남은 시간을 시계를 두 번 읽어 구합니다. 첫 읽기에서 활성(남은 1ms)이고 두 번째 읽기 전에 1ms가 지나면 `active=true`·`remainingMs=0`이 만들어질 수 있음을 커밋하지 않은 임시 테스트로 확인했습니다. 실기기에서 관측한 것은 아니며, 제품 코드 변경이 필요하므로 이 작업에서 다루지 않았습니다.
  - [#180](https://github.com/beone-capston/beone-mvp/issues/180)에서 수정([단계 기록](180-activation-state-snapshot.md)).

### 범위 밖으로 남긴 것

- U4(AG-03 계약 해석 대기), U7(`configureWebView` 리팩터 필요, 별도 승인 대상)
- 실기기·설치·release APK 확인
- [Android 진행 상황](README.md)의 '아직 안 되는 것' 문장(다른 작업선에서 정합 처리)
