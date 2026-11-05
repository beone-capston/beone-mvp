# Android WebView 셸

React 화면을 Android 앱 안에서 보여줄 WebView의 기본 구조입니다. 근거는 [PRD §2.3·FR-06](../PRD.md), [Android 시작 프롬프트 작업 대기열 3번](../development/android-start.md), [실기기 PoC 구현 참고](../d08-biometrics-poc.md#5-구현-참고-결정-아님)입니다.

## 현재 구현 범위

| 구분 | 상태 |
| --- | --- |
| 시작 주소·신뢰 origin을 빌드 설정값(`BuildConfig.WEB_START_URL`)으로 분리 | 구현 |
| 신뢰 origin 밖 탐색 차단, 웹 이외 스킴 차단 | 구현, 단위 테스트 |
| SSL 오류 시 연결 취소 | 구현 |
| debug만 원격 디버깅·로컬 개발 서버 평문 HTTP 허용 | 구현. 소스 설정(네트워크 보안 설정·Manifest)은 단위 테스트, release 값은 빌드 산출물로 확인 |
| 시스템 바·디스플레이 컷아웃 inset 적용 | 구현 |
| release 로딩과 정확한 origin | **미구현** — release는 주소를 설정하지 않아 아무 페이지도 불러오지 않음. 별도 정책 PR에서 개정([아래](#release-로딩-이력과-현재-방향)) |
| 웹 ↔ 네이티브 브리지 | **미구현** — 공개 계약은 [D-21](../decisions.md#d-21-확정-정책)의 [후보](bridge-contract-candidate.md)이며 확정이 아님. 어떤 origin에도 노출하지 않음 |
| 기기 인증(#22)과의 연결 | 미구현. debug 앱에 인증 점검 화면과 WebView 셸이 함께 있지만 서로 연결되어 있지 않음 |
| 실기기·계측 테스트 | 미실행 |

## 빌드별 설정

| 항목 | debug | release |
| --- | --- | --- |
| `WEB_START_URL` | `http://localhost:5173/` (Gradle 속성 `beone.devServerUrl`로 변경) | `""` (설정 없음 → "웹 화면 주소가 설정되지 않았습니다") |
| 평문 HTTP | `localhost`, `127.0.0.1`, `10.0.2.2`만 허용 | 허용하지 않음 |
| 원격 디버깅(`chrome://inspect`) | 켬 | 끔 |

- 네트워크 보안 설정: [main](../../android/app/src/main/res/xml/network_security_config.xml)(release 기본값)과 [debug](../../android/app/src/debug/res/xml/network_security_config.xml)(같은 이름의 리소스로 덮어씀)
- 실기기에서 개발 서버를 볼 때: PC에서 `npm run dev`를 실행한 뒤 `adb reverse tcp:5173 tcp:5173`으로 화면을, 업무 API를 쓰는 화면이면 `adb reverse tcp:8080 tcp:8080`으로 API를 연결합니다. 화면과 API는 origin이 달라 API 쪽 CORS 허용 목록이 필요합니다(업무 API 작업에서 추가). 이 저장소의 Android 작업선은 프런트엔드를 실행·빌드하지 않습니다.
- LAN IP 주소(예: `http://192.168.0.10:5173`)는 debug 평문 허용 목록에 없어 연결되지 않습니다. 필요하면 debug 네트워크 보안 설정에 추가해야 합니다.

## 패키지 구조

| 파일 | 역할 |
| --- | --- |
| [WebOrigin.kt](../../android/app/src/main/java/com/beone/app/web/WebOrigin.kt) | 주소에서 스킴·호스트·포트를 뽑아 비교. http·https만 받고 기본 포트(443·80)는 같게 봅니다 |
| [WebNavigationPolicy.kt](../../android/app/src/main/java/com/beone/app/web/WebNavigationPolicy.kt) | 시작 주소의 origin과 같은 주소만 허용. 설정이 없거나 잘못되면 아무것도 허용하지 않습니다 |
| [RestrictedWebViewClient.kt](../../android/app/src/main/java/com/beone/app/web/RestrictedWebViewClient.kt) | 신뢰 origin 밖 탐색 차단(하위 프레임 포함, 링크·리디렉션·POST), SSL 오류 취소, 뒤로 가기 가능 여부 전달 |
| [MainActivity.kt](../../android/app/src/main/java/com/beone/app/MainActivity.kt) | WebView 생성·설정, 시스템 바·키보드 inset, 웹 기록이 있을 때만 웹 뒤로 가기, 상태 저장 |

WebView 설정: JavaScript·DOM storage 켬(React 실행에 필요), 파일·콘텐츠 접근 끔, 새 창 열기 끔, 위치 정보 끔, HTTPS 페이지 안의 HTTP 리소스(mixed content) 차단.

## 처리 흐름

```text
MainActivity.onCreate
  → WebNavigationPolicy(BuildConfig.WEB_START_URL)
      ├─ 설정 없음/잘못됨 → WebView에 아무것도 불러오지 않고 안내 문구
      └─ 시작 주소 불러오기(화면 재생성 시 저장된 상태 복원)
페이지 안 링크·리디렉션 → shouldOverrideUrlLoading
  ├─ 신뢰 origin과 같음 → WebView에서 계속
  └─ 다름 → 열지 않음(메인 프레임이면 안내 문구). 외부 브라우저로 넘기지도 않음
메인 프레임 페이지 시작 → onPageStarted (POST 폼 제출처럼 위 단계를 거치지 않는 탐색 대비)
  ├─ 신뢰 origin과 같음 → 안내 문구 숨김
  └─ 다름 → 불러오기 중지 + 안내 문구
SSL 오류 → 연결 취소
```

## release 로딩 이력과 현재 방향

현재 release 구현은 주소 미설정입니다. 아무 페이지도 불러오지 않습니다.

- **현재 방향:** 이후 사용자 결정으로 공개 HTTPS 화면을 여는 쪽으로 바뀌었습니다. release 로딩 구현과 정확한 origin은 정해지지 않았고 별도 정책 PR에서 개정합니다.
- **이력(현재 방침 아님):** 2026-11-04 D-21 당시에는 앱 에셋(`WebViewAssetLoader`, `https://appassets.androidplatform.net`)을 release 설계로 정했고, 2026-11-16 시연은 debug 빌드, 개발 서버, `adb reverse`(5173, 8080)로 하기로 했습니다.

## 실기기에서 확인할 항목

단위 테스트와 빌드 산출물 확인으로 대신하지 않습니다. 기기 연결과 설치는 사용자가 직접 합니다.

| # | 확인 항목 | 기대 결과 | 상태 |
| --- | --- | --- | --- |
| W1 | debug 앱 + `adb reverse`로 개발 서버 접속 | React 개발 화면이 보이고 상태 바·내비게이션 바에 가려지지 않음 | 미확인(사용자 관측 대기) |
| W2 | 화면 회전 | 화면이 다시 처음부터 불러와지지 않고 이어짐 | 미확인(사용자 관측 대기). "이어짐"은 주소와 웹 기록 유지로 판정하고 JS 상태는 기록만 합니다(D-21) |
| W3 | 다른 origin으로 가는 링크 | 열리지 않고 안내 문구 | 미실행(지금 React 화면에 링크 없음) |
| W3a | 입력란을 눌러 키보드 표시 | 입력란이 키보드에 가려지지 않음 | 미실행(지금 React 화면에 입력란 없음) |
| W3b | 웹 안에서 페이지 이동 후 뒤로 가기, 첫 화면에서 뒤로 가기 후 앱 복귀, 다시 이동·뒤로 가기 | 웹 기록이 있으면 웹 이전 페이지, 없으면 앱이 백그라운드로. 복귀 후에도 웹 뒤로 가기가 동작 | 미실행(지금 React 화면에 페이지 이동 없음) |
| W4 | release 앱 실행 | "웹 화면 주소가 설정되지 않았습니다" | 미확인. 로컬 debug 키 서명본 허용(커밋·배포 안 함, D-21). 11/14 전에 해도 됩니다 |
| W5 | debug 앱에서 `chrome://inspect` | WebView가 보임. release 앱은 보이지 않음 | debug 부분 미확인(사용자 관측 대기), release 부분 미확인(W4와 같음) |

### debug 실기기 관측 절차 (2026-11-01 준비, 사용자 관측 대기)

이번에는 지금 debug APK로 볼 수 있는 W1·W2·W5(debug)만 관측합니다. 설치·관측은 사용자가 직접 하며, 이 문서 작성 시점까지 기기 조작·설치는 하지 않았습니다. 준비가 끝났다고 해서 시험을 통과한 것은 아닙니다.

**시험 APK**

- 빌드 변형: debug(기본 개발 서버 주소 `http://localhost:5173/`)
- sha256: 미정. `develop` 통합 커밋으로 소스 기준을 고정한 뒤, 그 소스에 대응하는 APK를 확인해 적습니다. 이 값을 확인하기 전에는 관측하지 않습니다.
- 이전 후보: `5ff441df…`(통합 전, 2026-11-01), `0af9497b…`(그 이전). 통합 전 APK이며 관측에 쓰지 않았고, 이 절차의 기준값이 아닙니다.
- 빌드 위치는 `android/app/build/outputs/apk/debug/app-debug.apk`입니다(기본 주소로 `./gradlew assembleDebug`).

**1. 설치 전 확인 (사용자 실행, 기기 상태는 바꾸지 않음)**

```bash
adb shell pm list packages com.beone.app
adb shell dumpsys package com.beone.app | grep -E 'versionName|versionCode|lastUpdateTime'
```

- 회신할 때 기기 일련번호·IP·페어링 정보는 적지 않습니다.
- #24 debug APK 관측은 2026-11-04에 끝났습니다. 설치 전 `lastUpdateTime`이 그 설치 시각과 다르면 그 사이 다른 설치가 있었던 것이므로 기록해 주세요.
- **B:ONE 인증 점검** 아이콘은 #24 단독 debug APK와 통합 debug APK에 모두 있을 수 있으므로, 아이콘만으로 설치본을 구분하지 않습니다. 설치본 APK의 SHA-256으로 확인하고 `lastUpdateTime`은 보조 기록으로 남깁니다.

**2. 교체 영향 (설치 전에 확인)**

- 이 APK는 #22 debug와 패키지 이름(`com.beone.app`)·debug 서명 키·versionCode(1)가 같습니다. `adb install -r`로 설치하면 #22 debug 앱을 **덮어씁니다**.
- 이 APK에는 #22가 통합되어 **B:ONE 인증 점검** 화면이 함께 들어 있습니다. 덮어쓰면 #22 앱의 실행 상태만 사라집니다. #22의 인증·60초 상태는 메모리에만 있고 백업도 끄므로(`allowBackup=false`), 되살릴 앱 데이터는 없습니다.
- 지금까지의 #22 실기기 관측 결과(D-08·R 항목)는 문서에 그대로 남고, 이번 설치로 무효가 되지 않습니다.
- 지금까지의 #22 관측은 `42b1dcc` APK 기준이며, 이 APK로 승계하지 않습니다.
- 위 내용을 확인한 뒤에만 설치합니다.
- `adb uninstall com.beone.app`은 꼭 필요하지 않습니다. 기존 앱을 먼저 지우고 싶을 때만 사용자 판단으로 선택합니다.

**3. 설치와 설치본 확인 (사용자 실행)**

1. `adb install -r android/app/build/outputs/apk/debug/app-debug.apk`(또는 같은 sha256의 사본)
2. `adb shell pm path com.beone.app`이 알려준 `base.apk` 경로를 저장소 밖 폴더로 `adb pull` → `shasum -a 256`이 위 sha256과 같은지 봅니다.
3. 다르면 그 설치로는 관측하지 않습니다. 보조로 `dumpsys package com.beone.app`의 `lastUpdateTime`이 설치 시각으로 바뀌었는지와 런처 아이콘을 적고 알려 주세요.
4. 런처에 **B:ONE**과 **B:ONE 인증 점검**이 모두 있는지 기록합니다. 두 화면이 함께 있어도 브리지 연결은 아닙니다.

**이전 #22 debug APK**

- 통합 APK에도 인증 점검 화면이 포함되지만, #24 단독 APK와 같은 실기기 동작이나 복구를 보장하지 않습니다. 이전 기준으로 되돌려 확인해야 한다면 별도 설치 승인 후 보관한 #24 APK를 사용하고 설치본 SHA-256을 대조합니다.
- 2026-11-04 #24 관측 기준은 소스 `42b1dcc`, APK SHA-256 `f59b96a52921b2e174a0011a27df293ee39a502f791a4e5f962e38031d168737`입니다. 해당 관측 결과를 통합 APK 검증으로 승계하지 않습니다.

**4. 개발 서버 준비 (사용자 실행, 이 작업선은 실행하지 않음)**

1. `frontend`에서 `npm ci` → `npm run dev`. 터미널에 표시된 포트를 확인합니다.
2. 이 APK는 기기 쪽 주소가 `localhost:5173`으로 고정돼 있습니다.
   - PC 포트가 5173이면 `adb reverse tcp:5173 tcp:5173`을 실행합니다. 업무 API를 쓰는 화면이면 `adb reverse tcp:8080 tcp:8080`도 실행합니다.
   - 다른 포트면 `adb reverse tcp:5173 tcp:<PC 포트>`처럼 기기 쪽은 5173으로 둡니다.
   - 쓴 명령을 기록합니다.
3. 개발 서버가 꺼져 있거나 연결되지 않으면 W1은 "관측 불충분"으로 적고 원인(연결 실패 등)을 함께 적습니다.

**5. 관측표**

| # | 조작 | 관측값(사용자 기록) | 판정 |
| --- | --- | --- | --- |
| W1 | 앱 실행, 세로 화면 | React 화면 표시 여부: / `B:ONE` 제목이 상태 바·컷아웃·내비게이션 바에 가리는지: / 안내 문구 표시 여부: | 관측 결과로 판정(관측상 통과·실패·관측 불충분) |
| W2-주소 | 회전 전후 `chrome://inspect` 콘솔에서 `location.href` | 회전 전: / 회전 후: | 관측 결과로 판정 |
| W2-웹 기록 | 회전 전후 `history.length`와 앱 뒤로 가기 동작(웹 이전 페이지로 가는지, 앱이 백그라운드로 가는지) | 회전 전: / 회전 후: | 관측 결과로 판정 |
| W2-JS 상태 | 회전 전 콘솔에서 사용자가 직접 임시 값(예: `window.__w2 = Date.now()`)을 넣고, 회전 후 같은 값이 남는지 확인 | 넣은 값: / 회전 후 값: | 기록만(판정에 쓰지 않음) |
| W5(debug) | 앱을 띄운 채 PC Chrome `chrome://inspect/#devices` | debug WebView 항목이 보이는지: | 관측 결과로 판정 |
| W3·W3a·W3b | — | — | 미실행(지금 React 화면에 링크·입력란·페이지 이동이 없음) |
| W4 | — | — | 이번 관측 범위 밖(11/14 전까지) |
| W5(release) | — | — | 이번 관측 범위 밖(11/14 전까지) |

- 코드 기준으로는 회전하면 Activity와 WebView가 새로 만들어지고 `restoreState`로 웹 기록을 되살립니다. JS 상태가 남는지는 코드만으로 확정하지 않고 관측값으로 기록합니다.
- 지금 웹 기록이 첫 화면뿐이면 `history.length`는 1일 수 있습니다. 그 값 그대로 적습니다. 웹 안 페이지 이동을 새로 만들지 않습니다.
- 콘솔의 임시 값은 W2 관측용이며 React 화면을 바꾸는 것이 아닙니다. 그 외 콘솔 조작(다른 주소로 이동, 입력란 추가)은 하지 않습니다.
- 판정 용어: 관측상 통과(관측 결과가 기대 결과와 일치), 실패, 재현 안 됨, 관측 불충분, 미실행, 관측 못 함
- 계측 테스트용 HTML·에뮬레이터 결과는 이 관측으로 인정하지 않습니다.

**6. 결과 회신 양식**

```text
설치본 sha256: (pull한 base.apk 기준)
APK 소스 기준 SHA: (APK를 만든 소스와 android/ 동일성을 확인한 커밋)
문서·PR HEAD: (APK 소스 기준과 다르면 별도 기록)
빌드 변형: debug
기기: 모델 / Android 버전 (일련번호·IP 제외)
설치 전 확인: pm list 결과 유무 / lastUpdateTime(설치 전·후)
개발 서버: PC 포트 / 사용한 adb reverse 명령
W1:
W2-주소:
W2-웹 기록:
W2-JS 상태:
W5(debug):
특이사항(crash·ANR·안내 문구 등):
```

## 아직 하지 않은 것

- release 로딩 구현과 정확한 origin 결정(별도 정책 PR)
- 브리지 구현과 기기 인증 상태 전달(공개 계약은 D-21의 후보)
- 계측 테스트(`androidTest`의 테스트용 HTML). 승인된 기기·에뮬레이터가 없어 이번에는 작성·실행하지 않았습니다.
- 파일 업로드·다운로드·권한 요청 처리. 현재 화면 요구사항에 없어 기본 동작(처리 안 함)으로 둡니다.
- iframe 로드 자체와 이미지·스크립트·fetch 같은 하위 리소스의 origin 제한. 하위 프레임의 이동은 메인 프레임과 같이 `shouldOverrideUrlLoading`에서 신뢰 origin 밖이면 막고, 안내는 메인 프레임일 때만 띄웁니다(D-21, 현행 차단 유지). 이 콜백이 모든 하위 프레임 이동에 불리는지는 공식 문서로 확인하지 못했으므로 브리지의 최상위 프레임 필터(`isMainFrame`)를 실제 방어선으로 봅니다. `shouldInterceptRequest`와 CSP는 브리지를 구현할 때 정합니다.
- `about:blank`·`blob:` 주소의 메인 프레임 탐색은 신뢰 origin이 아니므로 막습니다. React 화면이 이 방식을 쓰게 되면 다시 판단합니다.
