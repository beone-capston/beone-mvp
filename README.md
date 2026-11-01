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
docker-compose.yml   로컬 MySQL
.env.example         환경변수 예시
```

세 애플리케이션은 각각 독립적으로 빌드·테스트합니다. 루트에는 공용 빌드 도구가 없습니다.

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
| android | AGP 9.1.1, Gradle 9.3.1 (Kotlin DSL), compileSdk/targetSdk 36, minSdk 30, AppCompat |
| DB | MySQL 8.4.11 (docker-compose와 Testcontainers가 같은 이미지 사용) |

## 환경변수

```bash
cp .env.example .env
```

- `.env`는 git에서 제외됩니다. 실제 값은 절대 커밋하지 않습니다.
- CODEF 키(`CODEF_*`)는 **서버(backend)에서만** 사용합니다. `android/`와 `frontend/`에 넣지 않습니다.
- `frontend/.env.example`은 프론트엔드 전용 공개 설정(`VITE_API_BASE_URL`)입니다. `VITE_` 변수는 번들에 포함되므로 비밀값을 넣지 않습니다.

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

debug 앱은 개발 서버 `http://localhost:5173/`을 엽니다. 실기기에서는 프런트엔드 개발 서버를 켠 뒤 `adb reverse tcp:5173 tcp:5173`으로 연결합니다. 주소는 `./gradlew installDebug -Pbeone.devServerUrl=<주소>`로 바꿀 수 있습니다. debug에서 평문 HTTP는 `localhost`·`127.0.0.1`·`10.0.2.2`에만 허용되므로, 그 밖의 주소는 기기가 신뢰하는 시스템 인증서의 HTTPS여야 합니다. 실기기에서는 LAN 주소 대신 `adb reverse`를 쓸 수 있습니다. release 앱의 웹 화면 주소는 아직 정하지 않았습니다([`docs/android/webview-shell.md`](docs/android/webview-shell.md)).

## 빌드·테스트

### Backend

| 명령 | 내용 | Docker |
|---|---|---|
| `./gradlew test` | 단위 테스트 (Spring 컨텍스트·DB 없음) | 불필요 |
| `./gradlew webTest` | `@WebSliceTest` 웹 슬라이스 테스트 (MockMvc, DB 없음) | 불필요 |
| `./gradlew integrationTest` | `@IntegrationTest` 통합 테스트 (Testcontainers MySQL) | 필요 |
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

계측 테스트(`connectedAndroidTest`)는 아직 작성하지 않았습니다. 추가하면 기기 또는 에뮬레이터를 연결해 실행합니다. 생체인증·기기 PIN·60초 활성화의 최종 확인은 에뮬레이터가 아닌 실제 Android 기기에서 합니다 ([PRD FR-06](docs/PRD.md)).

## 보안 원칙

자세한 내용은 [PRD §7](docs/PRD.md#7-보안과-표시-원칙)을 따릅니다.

- 실물 카드번호, CVV, 카드 비밀번호, 계좌정보를 수집·저장하지 않습니다.
- CODEF 키·토큰은 서버(backend)에서 관리하며 Android 앱·프런트엔드·Git 저장소에 넣지 않습니다.
- CODEF 연결 비밀번호는 CODEF 연결 요청에만 사용하고 DB·로그·브라우저 저장소에 남기지 않습니다.
- 실계정 CODEF 응답 전문은 저장하거나 로그에 남기지 않습니다.
- 실제 개인 거래내역은 Git 저장소·공개 평가 자료·fixture·이슈·PR에 포함하지 않습니다. 서비스에서 실제 조회한 거래와 자체 모의 거래 원장은 구분해 관리합니다.
- `fixtures/private/`은 git에서 제외되지만, 실제 민감정보의 보관 장소로 사용하지 않습니다.
