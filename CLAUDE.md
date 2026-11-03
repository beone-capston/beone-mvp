# BeONE 개발 지침

이 파일은 Claude Code가 BeONE 저장소에서 작업할 때 따르는 지침입니다.

## 먼저 확인할 문서

- 기능 범위와 요구사항: `docs/PRD.md`
- 미결 사항과 확정 결정: `docs/decisions.md`
- 실제 실행·테스트 명령: `README.md`
- 이슈·브랜치·커밋·PR 절차: `CONTRIBUTING.md`
- 이름·구현·테스트·보안 규칙: `docs/conventions.md`
- 전체 구조와 도메인 간 관계: `docs/architecture.md`
- 도메인별 패키지·클래스 설명: `docs/domains/`

요구사항이나 문서 간 내용이 충돌하면 임의로 결정하지 말고 충돌 지점을 알려준다. 구현 결과에 맞추려고 PRD나 테스트의 기대값을 조용히 바꾸지 않는다.

## 저장소 구조

- `android/`: Android Kotlin 앱. React 화면을 표시할 WebView 셸
- `frontend/`: Vite, React, TypeScript 웹
- `backend/`: Java 21, Spring Boot, MySQL API
- `docs/`: PRD와 결정 기록
- `fixtures/`: 평가·시연용 비식별 데이터
- `docker-compose.yml`: 로컬 MySQL

세 애플리케이션은 독립적으로 빌드·테스트한다. 루트에 공용 빌드 도구가 있다고 가정하지 않는다.

## 작업 원칙

1. 작업 전 관련 문서와 대상 모듈을 확인하고 범위를 간단히 정리한다.
2. 관련 없는 파일을 변경하거나 기존 작업을 덮어쓰지 않는다.
3. 기능 구현과 함께 필요한 테스트를 작성한다.
4. 변경한 모듈의 실제 검증 명령을 실행하고 결과를 보고한다.
5. 실행하지 못한 테스트는 통과했다고 말하지 않고 이유를 기록한다.
6. 사용자가 요청하지 않은 커밋, push, 브랜치 이력 재작성, 원격 저장소 생성은 하지 않는다.

## 장기 연속 개발 모드

- 사용자가 세션의 작업 지시로 장기 연속 개발 모드의 시작·재개를 명시적으로 요청한 경우에만 적용한다. `docs/development/backend-start.md` 또는 `docs/development/android-start.md`의 시작 프롬프트를 작업 지시로 전달받은 경우가 여기에 해당한다. 이 문서들을 읽거나 검토·수정하라는 요청은 모드 시작이 아니다.
- 이 모드에서는 작업 전에 [장기 연속 개발 운영 규칙](docs/development/autonomous-workflow.md)을 읽고 따른다. 그 문서가 허용하는 커밋, 자기 feature 브랜치의 일반 push, `develop` 대상 PR 생성·갱신은 모드 시작 요청을 작업 원칙 6의 사용자 요청으로 본다. 허용 범위는 해당 작업선의 담당 경로와 운영 규칙 3절이 허용한 공용 문서의 자기 모듈 절로 한정한다.
- 이 모드를 요청하지 않은 일반 작업에는 적용하지 않는다. 일반 작업의 커밋·push·PR은 작업 원칙 6에 따라 사용자가 요청한 경우에만 한다.
- 이 모드에서도 PR을 `develop`·`main`에 병합하는 것은 자동 권한에 포함되지 않는다. `develop`·`main` 직접 수정·push, 강제 push, 이미 만든 커밋의 이력 변경(rebase·reset·amend), 훅 우회도 하지 않는다.
- 충돌 해결을 위해 최신 `origin/develop`을 자기 feature 브랜치에 merge하는 것은 운영 규칙에 따라 허용된다. 이것은 PR 병합이 아니다.

## 도메인·구조 문서 유지

- 도메인이나 패키지를 변경하기 전에 `docs/architecture.md`와 해당 `docs/domains/<domain>.md`를 확인한다.
- 다음 변경은 코드와 같은 PR에서 관련 설명 문서를 함께 갱신한다.
  - 도메인 또는 패키지의 추가·삭제·이동·이름 변경
  - 주요 클래스의 추가·삭제·이름 변경 또는 책임 변경
  - 주요 입력·출력 모델, 공개 사용법, 검증 조건의 변경
  - 도메인 간 의존 관계 또는 데이터·처리 흐름의 변경
- 신규 도메인은 `docs/domains/<domain>.md`를 작성하고 `docs/architecture.md`에 링크와 역할을 추가한다.
- 처음 참여한 팀원이 이해할 수 있는 한국어로 작성하고, 전문 용어는 처음 사용할 때 풀어 설명한다.
- 도메인 문서에는 목적과 범위, 핵심 용어, 패키지 구조, 주요 클래스의 역할과 관계, 데이터·처리 흐름, 짧은 코드 예시, 제약과 현재 구현 범위를 적는다.
- 모든 필드와 메서드를 복제하지 않는다. 핵심 책임과 사용법을 설명하고 실제 소스 파일을 상대 링크로 연결한다.
- 구현된 기능과 계획·미구현 기능을 구분한다. 코드에 없는 계산·연동·검증 기능을 구현된 것처럼 설명하지 않는다. 미확정 조건을 확정값이나 0원으로 표현하지 않는다.
- 문서는 해당 PR의 최종 코드 상태를 기준으로 작성한다. 삭제·이동된 경로, 문서 간 링크, 오래된 예시를 확인한다.
- 내부 구현만 변경되어 설명에 영향이 없다면 불필요하게 문서를 수정하지 않는다. PR 본문에 문서 갱신이 필요하지 않은 이유를 적는다.
- 작업 완료 보고에는 갱신한 문서와 변경 내용을 포함한다.
- 이 규칙은 작업 시 문서 갱신을 요구하는 지침이다. 파일 변경을 감지해 문서를 자동 생성하는 기능이나 CI 강제 검사는 아니다.

## 제품의 경계

- 실제 카드 결제는 구현하지 않는다. 결제는 서비스 내부의 모의 승인이다.
- 카드 혜택 계산과 최종 추천은 결정론적 코드가 담당한다.
- CODEF 연동이 실패해도 수동 입력 경로를 유지한다.
- AI를 사용할 수 없어도 검증된 규칙으로 추천과 모의 승인이 가능해야 한다. AI 실패 시 사용자 업종 확인과 계산 결과 기반 기본 설명을 제공한다.
- 카드 규칙은 확인된 약관과 `docs/decisions.md`의 결정을 근거로 구현한다.
- 카드 4종의 약관을 선정한 뒤 규칙 엔진 구현 전에 독립 정답 사례 20개와 입력 변경 시험 10개의 기대 결과를 고정한다. 구현 결과에 맞춰 기대값을 수정하지 않는다.

## 기술 스택과 설정

- Backend: Java 21, Spring Boot 4.1.1, Gradle Groovy DSL, Flyway, MySQL
- Frontend: React 19, Vite 8, TypeScript, Vitest, Testing Library, oxlint
- Android: Kotlin, Android Gradle Plugin 9.1.1, Gradle Kotlin DSL
- DB: MySQL 8.4.11. 로컬 compose 이미지와 Testcontainers 이미지를 일치시킨다.

백엔드가 Java라는 사실과 Android Gradle 설정이 Kotlin DSL이라는 사실을 혼동하지 않는다. 기존 Gradle 설정 형식을 요청 없이 변경하지 않는다.

## 검증 명령

다음 명령은 각 디렉터리에서 실행한다. 버전이나 스크립트가 바뀌면 `README.md`와 이 문서를 함께 수정한다.

### Backend

```bash
cd backend
./gradlew test
./gradlew webTest
./gradlew integrationTest
./gradlew build
```

- `test`: Docker가 필요 없는 단위 테스트
- `webTest`: Docker·DB가 필요 없는 `@WebSliceTest` MockMvc 웹 슬라이스 테스트. API 응답·예외 처리를 바꾸면 실행한다(`docs/api.md`)
- `integrationTest`: Docker가 필요한 Testcontainers MySQL 통합 테스트
- `build`: 단위·웹·통합 테스트를 포함하므로 Docker 필요
- `codefSandboxCheck`: 외부 CODEF 샌드박스를 호출한다. 위 명령에 포함되지 않으며 사용자가 직접 실행한다(`docs/development/codef-sandbox-check.md`)

로컬 API를 실행할 때는 먼저 프로젝트 루트에서 MySQL을 실행하고, README에 따라 루트 `.env`를 백엔드 프로세스에 전달한다.

```bash
# 프로젝트 루트
docker compose up -d --wait

# backend/
set -a; source ../.env; set +a
./gradlew bootRun
```

실행 상태는 다른 터미널에서 `curl http://localhost:8080/actuator/health`로 확인한다. IntelliJ 실행 구성은 루트 `.env`를 자동으로 읽는다고 가정하지 않는다.

### Frontend

```bash
cd frontend
npm ci
npm run lint
npm test
npm run build
```

개발 서버는 `npm run dev`로 실행하고, 터미널에 표시된 주소에서 확인한다.

### Android

```bash
cd android
./gradlew assembleDebug testDebugUnitTest lintDebug
```

기기·에뮬레이터 동작 확인이 필요한 변경은 Android Studio에서 직접 실행해 확인한다. 생체인증·기기 PIN·60초 활성화의 최종 확인은 실제 Android 기기에서 하며, 에뮬레이터 결과를 최종 확인으로 보고하지 않는다.

## 보안

- `.env` 및 실제 비밀값을 커밋하거나 출력 결과에 노출하지 않는다.
- CODEF 키·토큰은 서버에서 관리한다. 연결 비밀번호는 CODEF 연결 요청에만 사용하고 DB·로그·브라우저 저장소에 남기지 않는다.
- 실물 카드번호, CVV, 카드 비밀번호, 계좌정보를 수집·저장하지 않는다.
- 실계정 CODEF 응답 전문은 저장하거나 로그에 남기지 않는다. 실제 개인 거래내역은 Git 저장소·공개 평가 자료·fixture·이슈·PR에 포함하지 않는다. 실제 조회 거래와 자체 모의 거래 원장은 구분해 관리한다.
- `fixtures/private/`이 Git에서 제외되더라도 실제 민감정보의 보관 장소로 사용하지 않는다.
- 외부 자료를 테스트에 사용할 때는 민감정보를 제거한 뒤 근거와 변환 방식을 기록한다.

## GitHub 작업

GitHub 연결 후 작업 브랜치는 `develop`에서 만들고 PR은 `develop`으로 보낸다. `develop`을 `main`에 반영하는 PR만 예외다. 브랜치 이름, 커밋 메시지, 리뷰·병합 방식은 `CONTRIBUTING.md`를 따른다. GitHub가 아직 연결되지 않은 상태라면 원격 저장소나 `develop`이 이미 존재한다고 가정하지 않는다.
