# BeONE 기여 가이드

BeONE의 기능 요구사항은 [PRD](docs/PRD.md), 미결 결정은 [결정 기록](docs/decisions.md)을 기준으로 합니다. 코드 작성 규칙은 [코드 컨벤션](docs/conventions.md)을 따릅니다.

## 작업 흐름

1. GitHub 이슈를 만들고 작업 범위와 완료 기준을 적습니다.
2. `develop`에서 작업 브랜치를 만듭니다.
3. 관련 코드와 테스트를 함께 변경합니다.
4. 변경한 프로젝트의 테스트·빌드를 실행합니다.
5. `develop`을 대상으로 PR을 열고 관련 이슈와 검증 결과를 적습니다.
6. PR을 검토한 뒤 병합합니다.

`develop`은 기본 브랜치이자 일상 개발의 통합 브랜치, `main`은 배포·제출 기준 브랜치입니다. `develop`의 변경을 `main`에 반영할 때도 PR을 사용합니다.

## 브랜치 이름

`종류/이슈번호-짧은-설명` 형식을 사용합니다. 설명은 영문 소문자와 하이픈으로 적습니다.

| 종류 | 용도 | 예시 |
| --- | --- | --- |
| `feat` | 기능 추가·개선 | `feat/12-manual-transaction` |
| `fix` | 버그 수정 | `fix/18-db-connection` |
| `refactor` | 동작 변경 없는 구조 개선 | `refactor/21-benefit-calculator` |
| `test` | 독립적인 테스트 작업 | `test/25-card-rules` |
| `docs` | 문서 작업 | `docs/7-api-guide` |
| `chore` | 설정·빌드·CI·의존성 작업 | `chore/9-github-templates` |
| `harness` | AI 개발 도구 지침 변경 | `harness/30-review-rules` |

이슈가 없는 긴급 작업은 먼저 이슈를 만들고 연결합니다.

## 커밋 메시지

다음 형식을 사용합니다.

```text
type(scope): 한글로 변경 내용 요약
```

`scope`는 필요할 때만 사용합니다. 예: `backend`, `frontend`, `android`, `docs`, `infra`.

```text
feat(backend): 수동 거래 입력 API 추가
fix(frontend): 추천 결과의 금액 표시 오류 수정
test(backend): 월 할인 한도 경계 사례 추가
docs: 개발 환경 실행 방법 수정
chore(android): Gradle 데몬 JDK 버전 고정
```

- 타입은 브랜치 종류와 같은 `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `harness`를 사용합니다.
- 제목에는 무엇이 바뀌었는지 적고, 변경 이유나 주의 사항이 필요하면 본문에 적습니다.
- AI 도구의 `Co-Authored-By` 공동 작성 표기는 사용하지 않습니다. 학교나 팀의 AI 활용 공개 규정이 있다면 별도로 따릅니다.

## 이슈와 PR

- `.github/ISSUE_TEMPLATE/`의 해당 양식을 사용합니다.
- 작업 브랜치 PR의 대상(base)은 기본 브랜치인 `develop`입니다. PR을 열 때 대상을 확인합니다. `develop`을 `main`에 반영하는 PR만 `main`을 대상으로 합니다.
- 작업 브랜치 → `develop` PR 본문에는 `Closes #123` 형식으로 이슈를 연결합니다.
- 변경 내용, 검증 결과, 리뷰가 필요한 부분을 적습니다.
- UI를 변경했다면 가능할 때 화면 캡처를 첨부합니다.
- 원칙적으로 다른 팀원 한 명의 리뷰를 받은 뒤 병합합니다.
- 작업 브랜치 → `develop` PR은 squash merge를 사용합니다.
- `develop` → `main` PR은 merge commit을 사용합니다. 두 브랜치의 허용 병합 방식은 각각 GitHub 룰셋으로 제한합니다.

`develop`은 기본 브랜치이므로, 이를 대상으로 한 PR 본문에 `Closes #123`을 적으면 PR 병합 시 해당 이슈가 자동으로 닫힙니다. `#123`만 적으면 이슈 링크만 만들어지고 자동으로 닫히지는 않습니다. `main` 대상 PR에서는 닫기 키워드로 이슈가 자동 종료되지 않습니다.

## PR을 열기 전 확인

- [ ] 이슈의 완료 기준을 확인했다.
- [ ] 변경한 동작에 맞는 테스트를 추가하거나 수정했다. 테스트가 필요 없다면 PR에 이유를 적었다.
- [ ] 변경한 프로젝트의 테스트와 빌드를 실행하고 결과를 PR에 적었다.
- [ ] API, DB 스키마, 실행 방법이 바뀌었다면 관련 문서를 수정했다.
- [ ] 도메인·패키지 구조, 주요 클래스의 책임·사용법이 바뀌었다면 [전체 구조](docs/architecture.md)와 해당 `docs/domains/` 문서를 갱신했다. 갱신이 필요 없다면 PR에 이유를 적었다.
- [ ] `.env`, 비밀키, CODEF 토큰, 실제 개인 거래내역이 포함되지 않았다.

프로젝트별 실행·테스트 명령은 [README](README.md)의 **빌드·테스트** 항목을 기준으로 합니다. 관련 없는 프로젝트의 테스트는 PR에 `해당 없음`으로 표시합니다.

## GitHub 저장소 설정

- 기본 브랜치이자 개발 통합 브랜치는 `develop`이고, `main`은 배포·제출 기준 브랜치입니다.
- `protect-develop` 룰셋은 PR을 필수로 하고 squash merge만 허용합니다.
- `protect-main` 룰셋은 PR을 필수로 하고 merge commit만 허용합니다.
- 두 룰셋 모두 브랜치 삭제와 force push를 차단합니다.
- 이슈 라벨은 `feat`, `fix`, `refactor`, `docs`, `chore`, `harness`, `test`, `android`, `frontend`, `backend`를 사용합니다.

## CodeRabbit 리뷰 확인

1. `develop` 대상 PR을 연 뒤 CodeRabbit의 상태 댓글과 실제 리뷰 댓글을 확인합니다. 자동 리뷰가 시작되었다면 중복으로 수동 명령을 남기지 않습니다.
2. 자동 리뷰가 시작되지 않으면 상태 댓글의 **Trigger review**를 누르거나 PR의 일반 댓글에 `@coderabbitai full review`를 남겨 전체 리뷰를 요청합니다. 요약·상태 댓글만으로 코드 리뷰가 완료됐다고 판단하지 않습니다.
3. 후속 커밋을 올린 뒤 자동 증분 리뷰가 달렸는지 확인합니다. 시작되지 않으면 `@coderabbitai review`로 새 변경분을 요청합니다. 전체 변경을 처음부터 다시 검토해야 할 때만 `@coderabbitai full review`를 사용합니다.
4. CodeRabbit의 의견은 참고 자료입니다. 실제 오류 여부와 수정 사항을 사람이 확인하고, 팀원 리뷰와 관련 테스트를 거친 뒤 병합합니다. CodeRabbit 자동 승인이나 체크를 필수 병합 조건으로 사용하지 않습니다.

리뷰 시작 조건과 명령은 [CodeRabbit 자동 리뷰 설정](https://docs.coderabbit.ai/configuration/auto-review), [리뷰 명령](https://docs.coderabbit.ai/reference/review-commands), [플랜별 조건](https://docs.coderabbit.ai/management/plans)을 참고합니다.
