# Android release 준비: 서명·설치·실기기 확인

결정 38 9항(서명된 release APK와 실 HTTPS 기기 검증까지가 MVP, 스토어 배포 제외)을 위한 절차서입니다. 결정 40은 이 일을 "미리 진행"할 항목으로 정했습니다. 이 문서는 키 생성, 서명 빌드, 설치, 실기기 확인을 **사용자가 승인 뒤 직접 실행**하도록 순서를 정리합니다. 키 생성·서명 빌드·설치·실기기 확인은 아직 하나도 하지 않았습니다.

- 빌드 설정: [`android/app/build.gradle.kts`](../../android/app/build.gradle.kts)
- 서명 환경 변수 설명: [WebView 셸의 release 서명 환경 변수](../android/webview-shell.md#release-서명-환경-변수)(#182)
- 실기기 순서표: [브리지 v1 실기기 확인 순서표](../android/device-checklist.md#브리지-v1-실기기-확인-순서표), [기기 인증과 60초 활성 상태](../android/device-checklist.md#기기-인증과-60초-활성-상태-22)

## 용어

- **release APK:** 배포용 설치 파일. debug와 달리 원격 디버깅(`chrome://inspect`)이 꺼지고, 서버 HTTPS 화면만 엽니다.
- **서명:** APK를 만든 사람을 증명하는 키 서명입니다. 같은 앱을 업데이트하려면 같은 키로 서명해야 하므로, 키를 잃어버리면 기존 설치본 위에 업데이트할 수 없습니다.
- **키 저장소(keystore):** 서명 키와 인증서를 담은 파일(`.jks` 등)입니다. 비밀번호로 보호합니다.

## 1. 현재 설정 확인 결과 (코드 변경 없음)

명세 AR-1이 요구한 설정은 이미 develop에 있습니다(#182 서명 골격, #89 release 주소). 이 PR은 아래를 확인만 하고 빌드 설정을 바꾸지 않았습니다.

| 요구 | 현재 상태 | 근거 |
|---|---|---|
| release `signingConfig`는 환경 변수로만 받음 | `BEONE_RELEASE_STORE_FILE`·`BEONE_RELEASE_STORE_PASSWORD`·`BEONE_RELEASE_KEY_ALIAS`·`BEONE_RELEASE_KEY_PASSWORD` 4개가 모두 있을 때만 `signingConfigs.release`를 만듦. 빈 값은 없는 것으로 봄 | `build.gradle.kts` |
| 키스토어는 저장소 밖 | `BEONE_RELEASE_STORE_FILE`이 저장소 밖 경로를 가리킴. 루트 `.gitignore`의 `# Signing keys`에 `*.jks`·`*.keystore`·`*.p12`·`*.pem`이 이미 있음. 서명 값은 properties 파일을 쓰지 않고 환경 변수로만 받으므로 properties 패턴은 더하지 않음 | `.gitignore` |
| 값이 없을 때 동작 | **기본: 서명 없이 빌드**(`app-release-unsigned.apk`). **배포용: `-Pbeone.release.requireSigning=true`를 붙이면 빠진 변수 이름만 보여 주고 구성 단계에서 실패** | 아래 근거 |
| `testReleaseUnitTest`·`lintRelease`는 키 없이 통과 | 통과(PR 검증 결과) | — |
| release 시작 주소 | `WEB_START_URL = "https://beonepay.site/"`. debug는 같은 `buildConfigField` 방식으로 `http://localhost:5173/`(속성으로 바꿀 수 있음) | `build.gradle.kts`, `ReleaseBridgeConfigTest` |
| 신뢰 origin | `https://beonepay.site` 하나. `www`·`http://`는 차단 | `ReleaseBridgeConfigTest`, `ReleaseNavigationPolicyTest` |
| release에서 평문 HTTP 금지 | main `network_security_config.xml`이 `cleartextTrafficPermitted="false"`이고 시스템 인증서만 신뢰. 평문 허용(`localhost`·`127.0.0.1`·`10.0.2.2`)은 debug 소스셋에만 있음 | `app/src/main/res/xml/`, `app/src/debug/res/xml/` |

**값이 없을 때 "서명 없이 빌드"를 기본으로 둔 근거**(#182의 선택을 유지):

- 키가 없는 리뷰·CI·로컬 환경에서도 `assembleRelease`·`testReleaseUnitTest`·`lintRelease`로 release 설정을 계속 검사할 수 있습니다.
- 서명 없는 APK(`app-release-unsigned.apk`)는 설치되지 않습니다. 실수로 배포될 위험이 없습니다.
- 배포용 빌드는 `-Pbeone.release.requireSigning=true`로 반드시 서명을 요구하므로, 서명이 빠진 채 배포본을 만드는 실수는 실패로 막힙니다.

## 2. 키 생성·보관·주입 (승인 항목, 사용자가 실행)

**N8 결정 대기:** 키 보관 위치·담당, 백업 방식, versionCode 증가 규칙을 정한 뒤에 실행합니다. 정하기 전에는 키를 만들지 않습니다.

1. **키 생성**(사용자 PC, 저장소 밖 폴더). 비밀번호는 명령줄에 쓰지 않고 프롬프트로 입력합니다.

   ```bash
   # 저장소 밖 경로 예: ~/beone-release/
   keytool -genkeypair -v \
     -keystore <저장소 밖 경로>/beone-release.jks \
     -alias <키 별칭> \
     -keyalg RSA -keysize 4096 -validity 10000
   # 이름·조직 등 인증서 정보와 비밀번호는 프롬프트에서 입력
   ```

2. **인증서 지문 사전 기록:** 키를 만든 직후, 서명 빌드 전에 키 저장소에서 별칭 인증서의 SHA-256 지문을 확인합니다. 그 값을 N8에서 정한 위치에 기록합니다. 비밀번호는 프롬프트로 입력하고, 출력 중 지문 줄만 옮깁니다.

   ```bash
   keytool -list -v -keystore <저장소 밖 경로>/beone-release.jks -alias <키 별칭> | grep -E 'SHA256:'
   # 예: SHA256: AB:CD:…(32바이트). 이 값이 이후 모든 서명본의 기준이다
   ```

   지문은 비밀이 아닙니다. 다만 3절에서 서명본을 판정하는 기준이 되므로, APK를 만들기 **전에** 키에서 직접 읽은 값을 기록해야 합니다. 첫 APK에서 읽은 값을 기준으로 삼으면 잘못된 키로 서명한 첫 APK를 걸러 내지 못합니다.
3. **보관:** 키 파일과 비밀번호는 Git·`gradle.properties`·`local.properties`·문서·이슈·PR·채팅·CI 변수에 넣지 않습니다. 저장소 폴더 안에도 두지 않습니다. 백업 위치와 담당은 N8에서 정합니다. 키를 잃어버리면 같은 앱으로 업데이트할 수 없습니다.
4. **주입과 서명 빌드:** 값은 그 셸에서만 환경 변수로 넣습니다.

   ```bash
   cd android
   export BEONE_RELEASE_STORE_FILE=<저장소 밖 경로>/beone-release.jks
   read -rs BEONE_RELEASE_STORE_PASSWORD && export BEONE_RELEASE_STORE_PASSWORD
   export BEONE_RELEASE_KEY_ALIAS=<키 별칭>
   read -rs BEONE_RELEASE_KEY_PASSWORD && export BEONE_RELEASE_KEY_PASSWORD
   ./gradlew assembleRelease -Pbeone.release.requireSigning=true
   # 산출물: app/build/outputs/apk/release/app-release.apk
   ```

   - 끝나면 셸을 닫거나 `unset BEONE_RELEASE_STORE_PASSWORD BEONE_RELEASE_KEY_PASSWORD`로 값을 지웁니다.
   - versionCode·versionName은 지금 `1`·`0.1.0`입니다. 바꾸는 규칙은 N8에서 정하고, 바꾸면 별도 PR로 합니다.

## 3. 서명 확인

Android SDK build-tools의 `apksigner`로 확인합니다. 출력에는 인증서 지문만 나오고 비밀번호는 나오지 않습니다.

```bash
APKSIGNER="$ANDROID_HOME/build-tools/<버전>/apksigner"
"$APKSIGNER" verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

| 확인값 | 기대 |
|---|---|
| `Verifies` | 첫 줄이 `Verifies` |
| 서명 방식 | `Verified using v2 scheme (APK Signature Scheme v2): true`(minSdk 30이라 v2 이상이면 충분) |
| 서명자 인증서 | `Signer #1 certificate SHA-256 digest`가 2절 2단계에서 **키에서 미리 기록한 지문**과 같음(콜론·대소문자 차이만 무시). 첫 APK의 값이 아니라 사전 기록값과 비교함. debug 키(`CN=Android Debug`)가 아님 |
| 패키지 | `aapt2 dump badging <apk>`의 `package: name='com.beone.app'`, versionCode·versionName이 의도한 값 |

인증서 SHA-256 지문은 비밀이 아닙니다. 사전 기록값과 다르면 그 APK는 설치·전달하지 않고 키와 환경 변수를 다시 확인합니다. 이후 서명본도 같은 사전 기록값과 비교합니다.

## 4. 설치 (승인 항목)

- `adb` 사용과 기기 설치는 승인 항목이며 사용자가 실행합니다. 이 PR은 adb·기기를 쓰지 않았습니다.

  ```bash
  adb devices                       # 기기 1대 확인
  adb install app/build/outputs/apk/release/app-release.apk
  ```

- **서명이 다른 앱이 이미 있으면 설치되지 않습니다**(`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). debug 앱(`com.beone.app`, debug 키)이 깔려 있으면 먼저 지워야 합니다. 이때 앱 데이터도 지워집니다. 지우는 것도 사용자가 정합니다.
- 서명 없는 APK(`app-release-unsigned.apk`)는 설치되지 않습니다.

## 5. 실기기 확인 체크리스트 (실 HTTPS)

최종 확인은 실제 Android 기기에서 합니다. 에뮬레이터 결과를 최종 확인으로 보고하지 않습니다. 기록에는 기기 모델·OS 버전·확인 날짜·APK 인증서 지문을 함께 적고, 생체·PIN·개인 정보가 보이는 화면은 첨부하지 않습니다.

**전제:** 항목마다 필요한 서버 상태가 다릅니다.

| 서버 상태 | 확인할 수 있는 항목 |
|---|---|
| 지금(정적 확인 화면만) | R1 |
| 승인된 임시 시험 페이지(T)를 같은 origin `/bridge-test/`에 잠시 올린 상태([브리지 시험 페이지 서버 적용 절차](bridge-test-page.md), 사용자 실행) | R1, R2·R3, R4(T의 `http://` 링크), R5~R9(T에서 `auth.request`·`state.get`으로 확인) |
| 공개 배포 뒤(웹과 같은 origin `/api`, 승인 ⑧) | 전부. R10은 이때만 |

| # | 확인 | 기대 | 연결 항목 |
|---|---|---|---|
| R1 | release 앱 실행 | `https://beonepay.site/`를 열고 "웹 화면 주소가 설정되지 않았습니다" 안내가 없음. PC `chrome://inspect`에 WebView가 보이지 않음 | [BR22](../android/device-checklist.md#release-시작-주소와-단일-origin), W5 release |
| R2 | 서버에서 서빙하는 시험 페이지로 브리지 기본 동작 | debug와 같은 결과 | [BR23](../android/device-checklist.md#release-시작-주소와-단일-origin)(BR1·BR2·BR3·BR17②·BR18④ 반복) |
| R3 | `https://www.beonepay.site/`, `http://beonepay.site/` 링크 | 열리지 않고 차단 안내. 신뢰 origin은 `https://beonepay.site` 하나 | [BR24](../android/device-checklist.md#release-시작-주소와-단일-origin) |
| R4 | 평문 HTTP 주소로 이동(T의 `http://beonepay.site/` 링크 등) | 차단(탐색 정책, release는 `cleartextTrafficPermitted="false"`) | 위 1절, BR24와 같은 링크 |
| R5 | 지문 인증 → 활성 | `BIOMETRIC`으로 활성, 60초부터 줄어듦 | [#22 기기 인증 2번](../android/device-checklist.md#기기-인증과-60초-활성-상태-22) |
| R6 | 60초 대기 | 0초에 비활성, 만료·재인증 안내 | #22 3번 |
| R7 | 지문 불일치 → 잠김 → 같은 창에서 PIN | PIN 성공 시 `DEVICE_CREDENTIAL`로 활성 | #22 7·8번 |
| R8 | 활성 중 재인증 | 60초로 다시 시작(남은 시간이 더해지지 않음) | #22 6번 |
| R9 | 활성 중 홈으로 나갔다 복귀, 다른 앱으로 전환 후 복귀 | 즉시 비활성(브리지 `state`의 `active:false`) | #22 9·10번 |
| R10 | 회원 로그인 → 카드 → 주문 → 모의 결제 승인(실 HTTPS) | 같은 origin 세션 쿠키로 동작, 승인 결과 표시. 실제 결제 없음 | 공개 배포 뒤. 팀원 프론트 화면 기준 |

- #22의 기존 관측은 debug 전용 **B:ONE 인증 점검** 화면 기준입니다. 이 화면은 release에 없습니다. 그래서 R5~R9는 release 서명본에서 웹 화면의 브리지 인증 요청(서버에서 서빙하는 시험 페이지 또는 결제 화면)으로 다시 확인하고, 상태는 브리지 `state` 응답(`active`·`remainingMs`·`reason`)으로 기록합니다. 시험 페이지 서버 배치는 [브리지 시험 페이지 서버 적용 절차](bridge-test-page.md)를 따릅니다(사용자 실행).
- R10은 공개 배포(승인 ⑧)와 팀원 프론트가 준비된 뒤에 합니다.

## 6. 남은 결정과 승인

| 항목 | 상태 | 담당 |
|---|---|---|
| N8: 키 보관 위치·담당·백업, versionCode 규칙 | 결정 대기 | 사용자 |
| 키 생성·서명 빌드 | 승인 대기 | 사용자 실행 |
| APK 전달·adb 설치·기존 debug 앱 삭제 | 승인 대기 | 사용자 실행 |
| 실기기 확인(R1~R10) | 승인 대기. R2~R9는 임시 시험 페이지 배치(승인) 뒤, R10은 공개 배포 뒤 | 사용자 실행 |
| 스토어 배포 | MVP 밖(결정 38-9) | — |
