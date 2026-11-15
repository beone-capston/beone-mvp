# BeONE 코드·테스트 컨벤션

이 문서는 Java 백엔드, React/TypeScript 프런트엔드, Android/Kotlin의 공통 구현 기준입니다. GitHub 작업 절차는 [CONTRIBUTING.md](../CONTRIBUTING.md)를 따릅니다.

## 공통 원칙

- 이름은 역할과 의미가 드러나는 영어로 작성합니다. 주석과 문서는 필요한 경우 한글로 작성할 수 있습니다.
- 기존 모듈의 구조와 스타일을 먼저 확인하고, 변경과 관계없는 코드를 한꺼번에 고치지 않습니다.
- 포맷·정적 검사가 가능한 규칙은 도구에 맡깁니다. 문서의 규칙과 도구의 설정이 다르면 함께 수정합니다.
- 하나의 메서드나 컴포넌트가 입력 처리, 외부 연동, 계산, 표시를 모두 맡지 않도록 책임을 나눕니다.
- 외부 API 응답을 그대로 핵심 계산 로직에 전달하지 않고, 필요한 필드로 변환한 뒤 사용합니다.

## 이름 규칙

| 대상 | 규칙 | 예시 |
| --- | --- | --- |
| Java·Kotlin 클래스, 인터페이스 | `PascalCase` | `BenefitCalculator` |
| Java·Kotlin 메서드, 함수, 변수 | `camelCase` | `calculateBenefit` |
| TypeScript 함수, 변수 | `camelCase` | `calculateBenefit` |
| React 컴포넌트 | `PascalCase` | `RecommendationCard` |
| 상수 | `UPPER_SNAKE_CASE` | `MAX_RETRY_COUNT` |
| Java 패키지 | 영문 소문자 | `com.beone.api.benefit` |

이름에 구현 수단보다 도메인 역할을 우선합니다. 의미가 불분명한 `Manager`, `Helper`, `Util` 같은 이름은 가능한 한 구체적인 책임으로 바꿉니다.

### 파일 이름

- Java·Kotlin 파일은 주된 클래스 이름과 맞춥니다. 예: `BenefitCalculator.java`, `MainActivity.kt`
- React 컴포넌트 파일은 `PascalCase.tsx`를 사용합니다. 예: `RecommendationCard.tsx`
- 일반 TypeScript 모듈과 훅은 `camelCase.ts` 또는 `useName.ts`를 사용합니다. 예: `formatAmount.ts`, `useRecommendation.ts`
- 프런트엔드 테스트는 대상 파일 옆에 `*.test.ts` 또는 `*.test.tsx` 형식으로 둡니다.

## 백엔드: Java 21·Spring Boot

- 백엔드 소스와 테스트는 Java로 작성합니다. 백엔드 Gradle 설정은 Groovy DSL(`build.gradle`, `settings.gradle`)을 사용합니다.
- HTTP 요청·응답 처리, 혜택 계산, DB 접근, CODEF 연동의 책임을 구분합니다.
- API 성공·실패 응답 형식, 오류 코드, 예외 처리·로깅 기준은 [API 공통 응답·오류 처리](api.md)를 따릅니다. 기능별 오류는 `ApiErrorCode` 구현 enum과 공통 처리기보다 높은 우선순위의 처리기로 추가하고, `ApiErrorCode`를 계산·도메인 코드에서 사용하지 않습니다.
- 요청 DTO의 검증 메시지는 한글로 명시하고 `${validatedValue}`나 입력 원문을 넣지 않습니다.
- 혜택 계산과 최종 카드 추천은 입력이 같으면 결과가 같은 결정론적 코드로 작성합니다. AI 출력을 계산의 정답이나 최종 결정으로 사용하지 않습니다.
- 금액 계산에 `float`나 `double`을 사용하지 않습니다. 원화 금액은 정수로 다루고, 비율·반올림 규칙은 명시하여 테스트합니다.
- 할인 제외, 전월 실적, 월 한도, 동률 처리 등 결과에 영향을 주는 규칙은 카드 약관 및 결정 기록에 근거를 남깁니다.
- DB 스키마 변경은 Flyway 마이그레이션으로 관리합니다. 이미 공유된 마이그레이션 파일을 임의로 수정하지 않고 새 파일을 추가합니다.
- CODEF 연동 실패 시에도 수동 입력 경로가 유지되어야 합니다.
- AI를 사용할 수 없어도 검증된 규칙으로 추천과 모의 승인이 가능해야 합니다. AI 실패 시 사용자 업종 확인과 계산 결과 기반 기본 설명을 제공합니다.

## 프런트엔드: React·TypeScript

- 사용자에게 보여줄 데이터와 API 요청·응답 형식을 구분합니다.
- 금액과 혜택 결과의 표시 형식은 계산 로직과 분리합니다.
- 로딩·오류·데이터 없음 상태를 필요한 화면에서 구분해 표시합니다.
- `VITE_` 환경변수는 브라우저 번들에 포함되므로 비밀값을 넣지 않습니다.
- 변경 후 프로젝트의 lint, 테스트, 빌드를 실행합니다.

## Android: Kotlin·WebView 셸

- Android 애플리케이션 코드는 Kotlin으로 작성합니다. Android Gradle 설정의 Kotlin DSL(`*.gradle.kts`)은 백엔드 언어 선택과 별개입니다.
- Android 고유 기능과 React WebView 화면 사이의 책임을 분명히 합니다.
- WebView와 네이티브 코드 사이에 전달하는 값은 필요한 범위로 제한하고, 비밀값이나 실제 금융 데이터를 불필요하게 노출하지 않습니다.
- 인증 및 플랫폼 동작을 변경하면 에뮬레이터 또는 기기에서 동작을 확인합니다. 생체인증·기기 PIN·60초 활성화·만료·재인증의 최종 확인은 실제 Android 기기에서 합니다.

## 테스트

### 공통

- 버그 수정에는 가능하면 해당 버그를 재현하는 테스트를 추가합니다.
- 경계값, 제외 조건, 실패 경로를 정상 사례와 함께 확인합니다.
- 테스트는 실행 순서나 이전 테스트의 데이터에 의존하지 않도록 작성합니다.
- 실제 계정, 실제 개인 거래내역, CODEF 비밀값으로 테스트하지 않습니다.

### 백엔드

- 단위 테스트는 Spring 컨텍스트와 DB 없이 실행되도록 유지합니다. 실행 명령은 `./gradlew test`입니다.
- 컨트롤러·요청 검증·응답 직렬화·예외 처리는 프로젝트의 `@WebSliceTest`(`@WebMvcTest` + `web` 태그)와 MockMvc로 확인합니다. 실행 명령은 `./gradlew webTest`이며 DB·Docker 없이 실행되어야 합니다. 처리기 메서드를 직접 호출하는 테스트로 HTTP 동작 검증을 대신하지 않습니다.
- `@WebSliceTest`와 `@IntegrationTest`를 한 클래스에 함께 쓰지 않습니다. 테스트 전용 컨트롤러는 테스트 클래스 안의 정적 클래스로 두고 `@Import`로 등록해 다른 테스트 컨텍스트에 섞이지 않게 합니다. `TestTagSelectionTest`가 태그 중복과 태그 없는 Spring 컨텍스트 테스트를 검사합니다.
- DB 통합 테스트에는 프로젝트의 `@IntegrationTest`를 사용하고 Testcontainers MySQL로 실행합니다. 실행 명령은 `./gradlew integrationTest`입니다.
- 첫 카드 4종의 약관을 선정한 뒤 규칙 엔진 구현 전에 독립 정답 사례 20개와 입력 변경 시험 10개의 기대 결과를 고정합니다. 두 개발자가 작성·교차 검토하며, 엔진 결과를 복사해 기대값으로 삼지 않습니다.
- MySQL 이미지를 바꾸면 `docker-compose.yml`과 Testcontainers 이미지 버전을 함께 확인합니다.

### 프런트엔드·Android

- 프런트엔드는 Vitest·Testing Library로 사용자에게 보이는 동작을 확인합니다.
- Android는 단위 테스트와 빌드를 확인합니다. 계측 테스트는 추가된 기능에 필요할 때 별도로 도입합니다.
- 테스트 실행 명령은 [README](../README.md)의 **빌드·테스트** 항목을 기준으로 합니다.

## 보안과 테스트 데이터

- 실물 카드번호, CVV, 카드 비밀번호, 계좌정보를 수집하거나 저장하지 않습니다.
- CODEF 키·토큰은 서버에서 관리하고 Android 앱·프런트엔드·Git 저장소에 넣지 않습니다. CODEF 연결 비밀번호는 연결 요청에만 사용하며 DB·로그·브라우저 저장소에 남기지 않습니다.
- 실계정 CODEF 응답 전문은 저장하거나 로그에 남기지 않습니다. 실제 개인 거래내역은 Git 저장소·공개 평가 자료·테스트 fixture에 넣지 않습니다. 서비스의 실제 조회 거래와 자체 모의 거래 원장은 구분해 관리하며, 평가 fixture에는 민감정보를 제거한 합성·비식별 데이터만 사용합니다.
- `fixtures/private/`이 Git에서 제외된다는 사실만으로 민감정보 저장이 허용되는 것은 아닙니다.
- 이슈·PR에 붙이는 로그와 스크린샷에서도 비밀값과 개인 데이터를 제거합니다.

## 규칙 변경

규칙의 원본은 이 문서입니다. 변경이 필요하면 팀에서 이유와 예외를 논의하고 문서를 수정합니다. `CLAUDE.md`나 Codex 지침을 사용하는 경우, 해당 지침도 이 문서와 모순되지 않게 갱신합니다.
