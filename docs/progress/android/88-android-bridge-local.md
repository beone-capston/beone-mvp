# #88 Android WebView 브리지 v1 로컬 구현

- 이슈: [#88](https://github.com/beone-capston/beone-mvp/issues/88)
- 브랜치: `feat/88-android-bridge-local` (기준 `origin/develop` `58854544a84b96e8bd309c3718a6bd6a4a8b6e6f`)
- 계약: PR #87(브랜치 `docs/86-bridge-contract-v1`, head `9c10e03`, **미병합**)의 `docs/android/bridge-contract-candidate.md`. 이 브랜치에는 PR #87을 merge하지 않았으므로, 이 브랜치의 같은 파일은 아직 이전(후보) 문구입니다.
- 근거 결정: 2026-11-16 사용자 결정(브리지 v1 채택, release 시작 주소·신뢰 origin, 검사 순서, 크기 상한 4096 UTF-16 코드 단위), [D-21](../../decisions.md#d-21-확정-정책), [D-25](../../decisions.md#d-25-확정-정책)

## 2026-11-16 · 브리지 v1 구현·release origin·기기 인증 연결 · PR 대기

### 한 일

- 새 패키지 [`com.beone.app.bridge`](../../../android/app/src/main/java/com/beone/app/bridge/)
  - `BridgeContract`: 계약 고정값(`v=1`, 크기 상한 4096, 메시지 `type` 6종, `ACTIVE`), 요청 3종 `BridgeRequest`, 오류 코드 3종 `BridgeErrorCode`
  - `BridgeJson`: 최소 JSON 해석기(RFC 8259 문법). Android의 `org.json`은 로컬 단위 테스트에서 동작하지 않고 의존성을 늘리지 않으려고 직접 뒀습니다.
  - `BridgeMessageParser`: 크기 → JSON → `v` → `type` 순서로 판정하고 먼저 걸린 단계의 결과만 돌려줍니다.
  - `BridgeState`·`BridgeOutbound`: `ActivationState`·`AuthRequestPhase`·남은 시간을 `state` 필드로 옮기고(`InactiveReason` → `reason` 명시 매핑, `UNKNOWN` 수단 생략, 대기 단계가 아니면 `authenticating=true`), 네이티브 → 웹 메시지 3종 JSON을 만듭니다. 응답 `v`는 항상 1입니다.
  - `BridgeMessageHandler`: 1단계(신뢰 origin·최상위 프레임)를 통과하지 못하면 아무 응답도 만들지 않고, 통과하면 `error`·`state`·`payment.check.result` 중 하나를 돌려줍니다. 인증 상태 접근은 `BridgeAuthPort` 경계로 분리했습니다.
  - `BridgeOrigins`: 탐색 정책(`WebNavigationPolicy`)의 신뢰 origin 하나를 브리지 신뢰 origin으로 쓰고, `addWebMessageListener` 허용 규칙(`스킴://호스트[:포트]`, 와일드카드 없음)을 만듭니다.
  - `WebMessageBridge`: `WebViewCompat.addWebMessageListener` 연결부. `WEB_MESSAGE_LISTENER`를 지원하지 않으면 붙이지 않습니다. 마지막으로 신뢰 판정을 통과한 최상위 프레임에 상태를 밀어 보냅니다.
  - `ActivationBridgePort`: 브리지 요청을 기존 `VirtualCardActivationViewModel`에 연결합니다. 요청 가능 여부·대기 시간·늦은 결과 처리는 기존 `AuthRequestGate`가 정하며 바꾸지 않았습니다.
- [`MainActivity`](../../../android/app/src/main/java/com/beone/app/MainActivity.kt) 연결
  - 페이지를 불러오기 전에 JS 객체 `BeoneBridge`로 리스너를 붙입니다. 허용 규칙은 신뢰 origin 하나입니다.
  - JS 객체 이름 `BeoneBridge`(웹 호출 `window.BeoneBridge.postMessage(...)`, 대소문자 동일)는 사용자 결정 2026-11-16(decisions/20 D절)이며, PR #87에서 계약 문서에 보충할 예정입니다. 계약 문서는 이 작업에서 고치지 않았습니다.
  - ViewModel을 화면에 연결하고 인증 결과 반영 뒤·앱 이탈(`onStop`, 화면 구성 변경 제외) 뒤 웹에 `state`를 보냅니다. 만료는 밀어 보내지 않습니다(B5).
  - 응답 대기 시간은 기존 `AuthResponseTimeouts` 기본값(인증 120초·정리 10초)을 그대로 씁니다. **근거 없는 제안값이며 실기기 관측 전 미확정입니다.** 값은 바꾸지 않았습니다.
- release 시작 주소를 `https://beonepay.site/`로 바꿨습니다([`build.gradle.kts`](../../../android/app/build.gradle.kts)). 탐색·브리지 신뢰 origin은 이 주소의 origin `https://beonepay.site` 하나입니다. debug는 기존 방식(개발 서버 주소, `-Pbeone.devServerUrl`로 변경 가능) 그대로입니다.
- `androidx.webkit` 1.18.0을 추가했습니다([`libs.versions.toml`](../../../android/gradle/libs.versions.toml)). 다른 의존성·버전은 바꾸지 않았습니다.
- **명세 밖 빌드 설정 1줄**: `gradle.properties`에 `android.onlyEnableUnitTestForTheTestedBuildType=false`를 더했습니다. 이유: AGP 9는 기본으로 debug 단위 테스트만 만들어 `src/testRelease`의 release 설정 테스트를 실행할 태스크가 없습니다. 영향: `testReleaseUnitTest` 태스크가 생기고, 단위 테스트가 release로도 컴파일·실행됩니다. 다른 `gradle.properties` 값은 바꾸지 않았습니다(코디네이터 승인).

### 구현에서 정한 것(계약 범위 안의 작은 결정)

- 최상위 JSON 값이 객체가 아니면(배열·문자열·숫자 등) `INVALID_MESSAGE`입니다.
- `v`는 JSON 숫자 값이 1이면 받습니다. `1`·`1.0`·`1e0`은 같은 숫자로 봅니다. 문자열 `"1"`·`true`·`null`·배열은 "`v`가 있지만 숫자 1이 아님"이라 `UNSUPPORTED_VERSION`입니다.
- `type`이 없거나 문자열이 아니면 필수 필드 누락·형식 오류로 보고 `INVALID_MESSAGE`입니다. 네이티브 → 웹 `type`(`state` 등)을 웹이 보내면 `UNKNOWN_TYPE`입니다.
- 같은 객체 안의 중복 키는 형식 오류(`INVALID_MESSAGE`)입니다. 계약에 없는 다른 필드는 무시합니다.
- 문자열이 아닌 메시지(ArrayBuffer)는 신뢰 판정을 통과한 뒤 `INVALID_MESSAGE`입니다.
- `auth.request`는 요청을 시도한 뒤 그 시점의 `state`를 항상 보냅니다. 요청이 전달돼 상태가 바뀐 경우(인증 중)와, 받을 수 없는 단계·거부·미전달로 상태가 그대로인 경우(C4)를 같은 응답으로 처리합니다.
- origin 비교는 기존 `WebOrigin`(스킴·호스트 소문자, 기본 포트 동일 취급)으로 합니다. 해석할 수 없는 origin(`null` 등)은 신뢰하지 않습니다.

### 계약 밖, 미정

- `WebViewFeature.WEB_MESSAGE_LISTENER`를 지원하지 않는 WebView: 브리지를 붙이지 않고 기존 동작(페이지 표시)을 유지하는 최소 처리만 했습니다. 사용자 안내·차단 같은 정책은 정하지 않았습니다.
- WebView 플랫폼이 메시지를 넘기기 전 자체 크기 제한을 두는지는 확인하지 않았습니다. 상한은 네이티브가 받은 뒤 검사합니다.

### `androidx.webkit` 버전 근거

- 공식 릴리스 노트(<https://developer.android.com/jetpack/androidx/releases/webkit>, 2026-11-16 확인): 최신 안정판 1.18.0(2026-10-21), 이후는 1.19.0-alpha01뿐입니다. 1.18.0 노트에 minSdk·compileSdk·AGP 요구 변경은 없습니다.
- Google Maven의 `webkit-1.18.0.aar` 메타데이터: `minCompileSdk=33`, `minAndroidGradlePluginVersion=7.2.0`, Manifest `minSdkVersion=24`. 이 앱의 compileSdk 36, AGP 9.1.1, minSdk 30과 호환됩니다. POM 의존성은 `androidx.core:core:1.1.0`·`androidx.annotation` 등이며 앱의 `core-ktx` 1.18.0과 충돌하지 않습니다(빌드의 `checkReleaseAarMetadata` 통과).
- `addWebMessageListener`·`WEB_MESSAGE_LISTENER`는 1.18.0 이전부터 있는 API입니다. 실기기 WebView 지원은 PoC(WebView 154)에서만 확인됐고 이번에는 확인하지 않았습니다.

### 검증 기록 (2026-11-16, 로컬)

| 명령 | 결과 |
| --- | --- |
| `./gradlew assembleDebug testDebugUnitTest lintDebug` | 성공. 단위 테스트 164건, 실패 0, 오류 0, 건너뜀 0(결과 XML 합산). 이 작업 전 106건 |
| `./gradlew assembleRelease lintRelease testReleaseUnitTest` | 성공. 서명 없는 release APK 생성. 단위 테스트 164건, 실패 0, 오류 0, 건너뜀 0 |
| lint(debug·release 같음) | 오류 0. 경고: `AndroidGradlePluginVersion` 2, `DataExtractionRules`, `GradleDependency`, `MergeRootFrame`, `MissingApplicationIcon`, `OldTargetApi`, `MissingOnRenderProcessGone` |

- 새 경고는 `MissingOnRenderProcessGone`(기존 `RestrictedWebViewClient`) 하나입니다. `androidx.webkit`이 추가하는 lint 검사로 드러난 것이고, 렌더러 종료 처리 정책이 필요해 이번 범위 밖으로 두었습니다.
- 크기 상한을 4097로 바꾸면 경계 테스트 4건이 실패하는 것을 확인하고 4096으로 되돌렸습니다.
- 연결 경로 테스트(`ActivationBridgePortTest`): 가짜 시계·가짜 authenticator로 인증 시작·성공 60초·만료·중복 요청·120초 시간 초과(`NO_RESPONSE`, 요청 차단)·시간 초과 뒤 늦은 성공 무시·취소·실패·앱 이탈 취소와 늦은 결과 무시·정리 10초 초과·화면 구성 변경·설정 필요·미전달을 확인했습니다. 실기기 인증 창·콜백 순서를 대신하지 않습니다.
- 실행하지 않음: 실기기·에뮬레이터·설치, `connectedAndroidTest`, 실제 프런트와의 연결.

### 남은 제한

- 실기기·설치·실제 프런트·API 연결은 하지 않았습니다(범위 밖, 별도 승인).
- 인증 대기 시간(120초·10초)은 근거 없는 잠정값이며 실기기 관측 전 미확정입니다.
- 계약 문서(PR #87)는 미병합이고 JS 객체 이름 보충도 그쪽에서 할 예정입니다.
- 범위 밖으로 남긴 것: lint `MissingOnRenderProcessGone`, 루트 `README.md`의 "release 앱의 웹 화면 주소는 아직 정하지 않았습니다" 문장(이 변경 뒤 사실과 다름), `docs/progress/android/README.md`·`docs/android/webview-shell.md`·`docs/architecture.md`의 #88 이전 기준 문장(이번에는 #88 줄만 더함).
