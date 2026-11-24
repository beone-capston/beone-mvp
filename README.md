# BeONE

BeONE은 사용자가 보유한 카드 중 **월간 총예상 혜택이 가장 큰 카드**를 결제 직전에 자동 선택하는 시뮬레이터입니다. 배포 서비스명은 **B:ONE**입니다.

> 실제 결제는 발생하지 않습니다. 결제는 서비스 내부의 모의 승인으로 대체합니다.

요구사항의 기준 문서는 [`docs/PRD.md`](docs/PRD.md)이고, 미결 결정은 [`docs/decisions.md`](docs/decisions.md)에 기록합니다. 이슈·브랜치·커밋·PR 절차는 [`CONTRIBUTING.md`](CONTRIBUTING.md), 코드·테스트 규칙은 [`docs/conventions.md`](docs/conventions.md)를 따릅니다.

## 저장소 구조

```
android/    Android 앱 (Kotlin, React 화면을 WebView로 표시할 셸)
frontend/   React 웹 (Vite + TypeScript)
backend/    Spring Boot API (Java 21, MySQL)
docs/       PRD, 결정 기록, 코드·테스트 컨벤션
fixtures/   평가·시연용 데이터 (fixtures/private/ 는 git 제외)
deploy/     배포용 Compose와 이미지 빌드·로컬 검증 스크립트
docker-compose.yml   로컬 MySQL
.env.example         환경변수 예시
```

세 애플리케이션은 각각 독립적으로 빌드·테스트합니다. 루트에는 공용 빌드 도구가 없습니다.

백엔드 배포 이미지와 배포 Compose는 [백엔드 배포 기반](docs/deployment/backend-compose.md)에 있습니다. 로컬 개발 실행은 아래 절차를 그대로 따릅니다.

## 요구 환경

아래 버전으로 빌드·테스트를 확인했습니다 (2026-10-03, macOS arm64).

| 도구 | 버전 |
|---|---|
| JDK | 21 |
| Node.js / npm | 24.x / 11.x (`frontend/.nvmrc`) |
| Docker / Compose | 29.x / v5 |
| Android Studio | 2025.3, Android SDK Platform 36 |

| 프로젝트 | 주요 구성 |
|---|---|
| backend | Spring Boot 4.1.1, Gradle 9.7.1 (Groovy DSL), Flyway, MySQL Connector/J, Testcontainers |
| frontend | React 19, Vite 8, TypeScript 6.0, Vitest 5, Testing Library, oxlint |
| android | AGP 9.1.1, Gradle 9.3.1 (Kotlin DSL), compileSdk/targetSdk 36, minSdk 30, AppCompat, androidx.biometric 1.1.0 |
| DB | MySQL 8.4.11 (docker-compose와 Testcontainers가 같은 이미지 사용) |

## 환경변수

```bash
cp .env.example .env
```

- `.env`는 git에서 제외됩니다. 실제 값은 절대 커밋하지 않습니다.
- CODEF 키(`CODEF_*`)는 **서버(backend)에서만** 사용합니다. `android/`와 `frontend/`에 넣지 않습니다. 키가 없어도 서버는 뜨고 CODEF 호출만 실패합니다. 변수 목록은 [CODEF 설정과 샌드박스 확인 절차](docs/development/codef-sandbox-check.md)를 봅니다.
- CODEF 연결·동기화 키 `BEONE_CODEF_CONNECTED_ID_KEY`(connectedId 암호화)·`BEONE_CODEF_APPROVAL_HMAC_KEY`(승인번호 HMAC)는 각각 Base64로 적은 32바이트이고 고정 기본값이 없습니다. 값은 저장소에 두지 않고 서버 프로세스 환경에만 줍니다. 없으면 CODEF 연결은 502, 동기화의 승인내역은 부분 실패입니다([codef 안내](docs/domains/codef.md)). `.env.example`에는 아직 이름이 없습니다.
- `frontend/.env.example`은 프론트엔드 전용 공개 설정입니다. `VITE_` 변수는 번들에 포함되므로 비밀값을 넣지 않습니다.

## 실행

### 1. MySQL

```bash
docker compose up -d --wait
```

호스트 포트 기본값은 **3307**입니다 (`MYSQL_PORT`). 로컬에 설치된 MySQL이 3306을 쓰는 경우와 충돌하지 않게 하기 위해서입니다.

### 2. Backend

```bash
cd backend
set -a; source ../.env; set +a
./gradlew bootRun
curl localhost:8080/actuator/health   # {"status":"UP"}
```

### 3. Frontend

```bash
cd frontend
npm ci
npm run dev
```

### 4. Android

Android Studio에서 `android/`를 열거나, 기기·에뮬레이터를 연결한 뒤 다음을 실행합니다.

```bash
cd android
./gradlew installDebug
```

debug 앱은 개발 서버 `http://localhost:5173/`을 엽니다. 실기기에서는 프런트엔드 개발 서버와 백엔드를 켠 뒤 `adb reverse tcp:5173 tcp:5173`(화면)과 `adb reverse tcp:8080 tcp:8080`(API)을 둘 다 연결합니다. `local`·`demo` 경로는 화면(5173)과 API(8080)를 각각 쓰기 때문입니다(D-23 11항). 개발 서버에는 `/api`를 `http://localhost:8080`으로 넘기는 Vite 프록시가 있어(D-23 11항 2026-11-24 보충, 결정 37 O2-L) 화면이 같은 origin으로 API를 호출합니다. 기존 안내대로 두 포트를 모두 연결해도 동작합니다. 주소는 `./gradlew installDebug -Pbeone.devServerUrl=<주소>`로 바꿀 수 있습니다. debug에서 평문 HTTP는 `localhost`·`127.0.0.1`·`10.0.2.2`에만 허용되므로, 그 밖의 주소는 기기가 신뢰하는 시스템 인증서의 HTTPS여야 합니다. 실기기에서는 LAN 주소 대신 `adb reverse`를 쓸 수 있습니다. release 앱은 `https://beonepay.site/`를 열고, 탐색과 브리지(`window.BeoneBridge`)의 신뢰 origin은 `https://beonepay.site` 하나입니다. release 앱의 실기기 확인은 아직 하지 않았습니다([`docs/android/webview-shell.md`](docs/android/webview-shell.md)).

## 프론트엔드 메인화면

- 내 카드 모드는 등록 카드가 없는 초기 화면이며, 가상 포트폴리오 모드에서는 시연용 카드 4종을 예시로 표시합니다. 모드 선택은 현재 화면에서만 유지됩니다.
- 카드 추가·마이페이지·혜택 메뉴는 준비 중 안내를 제공합니다.
- **모의결제** 탭의 주문 생성(#163)과 빠른결제의 60초 사용 준비·가상 주문서 결제 흐름(#167)이 있습니다. 개발 플래그로 결제·인증을 목(mock)으로 바꾸지 않으면 같은 origin의 주문·추천·모의 승인 API(`/api/v1/me/...`)를 세션 쿠키와 함께 호출하고(쓰기는 CSRF 토큰 포함, `local`·`demo`용 `X-BeONE-Demo-User` 헤더도 함께 보냄)(`frontend/src/orders/orderApi.ts`, `frontend/src/payment/paymentApi.ts`), 혜택 계산은 서버 추천 결과를 표시합니다. 기기 인증은 Android 브리지 `window.BeoneBridge`를 호출합니다(`frontend/src/hooks/useNativeAuth.ts`).
- 업무 API 호출은 `member` 프로필 계약(세션 쿠키·CSRF·Vite 프록시)을 따릅니다(`frontend/src/api/client.ts`). 401을 받으면 로그인 화면으로 돌아갑니다. 내 카드 목록·수동 등록, 이메일 인증 화면은 아직 없습니다(프론트 P0 후속 PR). 시연 헤더를 회원 빌드에서 뺄지는 N18 결정 대기입니다. Android 실기기에서 실제 프런트 화면·브리지·API를 함께 확인하는 통합 확인은 아직 하지 않았습니다.
- 네이비·민트 브랜드 색상과 제공된 로고 이미지(`frontend/public/beone-brand.png`)를 사용하며, 모든 화면에서 최대 480px 너비의 세로 앱 레이아웃과 하단 메뉴를 제공합니다. Android WebView의 안전 영역 여백을 반영합니다.
- 실행은 `frontend/`에서 `npm run dev` 후 터미널에 표시된 주소로 접속합니다.

## 개발용 가상카드 및 모의 승인

백엔드와 Android 없이 세 화면을 확인하려면 기존 개발 서버를 종료한 뒤 `frontend/`에서 실행합니다.

```bash
VITE_DEVICE_AUTH_MODE=mock VITE_PAYMENT_API_MODE=mock npm run dev
```

`http://localhost:5173`에서 개발용 로그인 후 카드 이미지를 탭하면 바로 60초 사용 준비 화면이 열립니다. **모의결제** 탭의 내 카드 목록에 있는 목 주문을 열고 **빠른결제로 이동**을 누르면 해당 주문을 선택할 수 있습니다. **가상 주문서 결제**를 눌러 결과를 확인합니다. 응답 지연은 `VITE_PAYMENT_MOCK_DELAY_MS`로 조정합니다(기본 800ms).

인증과 결제 전송 모드는 각각 `native`·`mock`, `api`·`mock`으로 선택합니다. 개발 플래그가 없거나 배포 빌드일 때는 네이티브 인증과 기존 백엔드 계약을 사용합니다. 서버 승인에는 시연 프로필과 실제로 생성한 주문이 필요하며, 서버의 결제 전 설정을 사용합니다. 구조는 [가상카드 및 모의 승인 안내](docs/domains/frontend-payment.md)를 참고합니다.

## 빌드·테스트

### Backend

| 명령 | 내용 | Docker |
|---|---|---|
| `./gradlew test` | 단위 테스트 (Spring 컨텍스트·DB 없음) | 불필요 |
| `./gradlew webTest` | `@WebSliceTest` 웹 슬라이스 테스트 (MockMvc, DB 없음) | 불필요 |
| `./gradlew integrationTest` | `@IntegrationTest` 통합 테스트 (Testcontainers MySQL) | 필요 |
| `./gradlew codefSandboxCheck` | CODEF 샌드박스 형식 확인. **외부를 호출하고 키가 필요합니다.** `check`·`build`에 들지 않으며 [절차](docs/development/codef-sandbox-check.md)대로 사용자가 직접 실행합니다 | 불필요 |
| `./gradlew build` | 단위 + 웹 + 통합 테스트 + 빌드 | 필요 |

- 통합 테스트는 `com.beone.api.support.IntegrationTest` 어노테이션을 붙입니다. `integration` 태그가 붙어 `test` 태스크에서는 제외됩니다.
- 웹 슬라이스 테스트는 `com.beone.api.support.WebSliceTest` 어노테이션을 붙입니다. `web` 태그가 붙어 `webTest`에서만 실행됩니다. `check`·`build`는 `webTest`와 `integrationTest`를 함께 실행하므로 Docker가 필요합니다.
- API 응답 형식과 오류 처리는 [`docs/api.md`](docs/api.md)를 따릅니다.
- MySQL 버전을 바꿀 때는 `docker-compose.yml`과 `backend/src/test/java/com/beone/api/support/MySqlImage.java`를 함께 수정합니다. 두 값이 다르면 `MySqlVersionAlignmentTest`가 실패합니다.
- DB 스키마는 Flyway 마이그레이션(`backend/src/main/resources/db/migration`)으로만 변경합니다.

### Frontend

```bash
cd frontend
npm ci
npm run lint
npm test
npm run build
```

### Android

```bash
cd android
./gradlew assembleDebug testDebugUnitTest lintDebug
```

release 빌드도 키 없이 확인할 수 있습니다. 서명 환경 변수가 없으면 서명 없는 APK(`app-release-unsigned.apk`)를 만듭니다.

```bash
cd android
./gradlew assembleRelease testReleaseUnitTest lintRelease
```

release 서명 값은 환경 변수 `BEONE_RELEASE_STORE_FILE`·`BEONE_RELEASE_STORE_PASSWORD`·`BEONE_RELEASE_KEY_ALIAS`·`BEONE_RELEASE_KEY_PASSWORD`로만 받습니다(값은 저장소·파일에 적지 않습니다). 배포용 빌드는 `-Pbeone.release.requireSigning=true`를 붙여 값이 빠지면 실패하게 합니다. 키 생성·보관·백업·versionCode 규칙은 N8 결정·승인 대기이며, 실제 서명 빌드는 아직 하지 않았습니다([release 서명 환경 변수](docs/android/webview-shell.md#release-서명-환경-변수)).

debug 빌드에는 실기기 인증 확인용 **B:ONE 인증 점검** 화면이 함께 설치됩니다(release 미포함). 확인 항목은 [`docs/android/device-checklist.md`](docs/android/device-checklist.md)에 있습니다.

계측 테스트(`connectedAndroidTest`)는 아직 작성하지 않았습니다. 추가하면 기기 또는 에뮬레이터를 연결해 실행합니다. 생체인증·기기 PIN·60초 활성화의 최종 확인은 에뮬레이터가 아닌 실제 Android 기기에서 합니다 ([PRD FR-06](docs/PRD.md)).

## 보안 원칙

자세한 내용은 [PRD §7](docs/PRD.md#7-보안과-표시-원칙)을 따릅니다.

- 실물 카드번호, CVV, 카드 비밀번호, 계좌정보를 수집·저장하지 않습니다.
- CODEF 키·토큰은 서버(backend)에서 관리하며 Android 앱·프런트엔드·Git 저장소에 넣지 않습니다.
- CODEF 연결 비밀번호는 CODEF 연결 요청에만 사용하고 DB·로그·브라우저 저장소에 남기지 않습니다.
- 실계정 CODEF 응답 전문은 저장하거나 로그에 남기지 않습니다.
- 실제 개인 거래내역은 Git 저장소·공개 평가 자료·fixture·이슈·PR에 포함하지 않습니다. 서비스에서 실제 조회한 거래와 자체 모의 거래 원장은 구분해 관리합니다.
- `fixtures/private/`은 git에서 제외되지만, 실제 민감정보의 보관 장소로 사용하지 않습니다.
