# #100 업무 API 공통 기반과 시연 사용자 API

브랜치 `feat/100-business-api-foundation`, 시작 기준 `origin/develop` `a19dc25`.

## 2026-11-17 · `user` 패키지·CORS·Flyway V2 · PR(`develop` 대상, 병합 전)
- 근거: 관리 기록 결정 25(업무 API 1차, 공통 기반 1명 먼저), [D-09·D-14](../../decisions.md#d-09d-14-확정-정책), [D-23](../../decisions.md#d-23-확정-정책) 11·12·13항, [업무 API 공통 규약](../../api.md#업무-api-공통-규약), [`docs/api/cards.md`](../../api/cards.md)의 `GET /api/v1/demo-users`.
- 한 일:
  - `backend/src/main/java/com/beone/api/user/`(`web`/`application`/`persistence`): 시연 사용자 엔티티·저장소, `DemoUserService`, 현재 사용자 결정 지점 `CurrentUserResolver`(컨트롤러 인자 `CurrentUser` 주입), 헤더 판정 `DemoUserHeader`, 헤더 오류 처리기 `DemoUserExceptionHandler`, `GET /api/v1/demo-users`.
  - `common.web.LocalDemoCorsConfiguration`: `local`·`demo` 프로필 CORS(D-23 11항 값 그대로).
  - Flyway `V2__demo_users.sql`: `users` 테이블과 시드 2명. 값은 명세 응답 예시의 `demoUser`·`displayName`·`portfolioType`·`readOnly`만 씁니다.
  - 테스트: 단위 `DemoUserHeaderTest`, 웹 `DemoUserApiWebTest`·`DemoUserProfileOffWebTest`, 통합 `DemoUserIntegrationTest`. 기대값은 명세 문서에서 먼저 옮겼습니다.
  - 문서: [user 안내](../../domains/user.md) 신규, [구조 안내](../../architecture.md)의 user 줄·업무 API 패키지 구조.
- 서비스에서 달라진 점: `local`·`demo` 프로필로 서버를 띄우면 `GET /api/v1/demo-users`가 시연 사용자 2명을 돌려주고, 프런트 개발 서버(`http://localhost:5173`)에서 CORS로 부를 수 있습니다. 이후 `/me/...` API는 `CurrentUser` 인자만 받으면 됩니다.
- 관리자 답으로 정한 것(2026-11-17, 계약 변경 없음):
  - 업무 오류 기반: `docs/api.md`의 "공통 `ApiException`을 두지 않는다"를 우선해 `common.web`에 공통 업무 예외를 추가하지 않았습니다. `user` 패키지의 자기 예외와 `@Order` 처리기가 기능별 오류 처리 방식의 첫 구현입니다.
  - `GET /api/v1/me`: `cardCount`가 보유 카드 저장소에 의존하므로 이 PR에서 빼고 보유 카드(A2) 작업으로 옮겼습니다. 0으로 임시 반환하지 않습니다.
  - `local`·`demo` 밖: 시연 전환 부품을 등록하지 않아 404입니다. 기본 프로필(`spring.profiles.default`)은 정하지 않았습니다. 로컬 실행에는 `SPRING_PROFILES_ACTIVE=local`이 필요하며, README 안내는 관리자가 후속으로 판단합니다.
- 작은 구현 결정: 헤더 값은 대소문자·공백을 고치지 않고 정확히 비교하며, 같은 헤더가 여러 개면 400입니다. 헤더 오류의 `errors`는 `[]`입니다(본문 필드 오류가 아님). 열거형 열은 MySQL ENUM이 아닌 VARCHAR로 두었습니다. CORS 거부(403)의 본문은 Spring 기본이며 공통 실패 형식이 아닙니다.
- 검증: `./gradlew test webTest integrationTest build javadoc --rerun-tasks` 결과는 PR 본문에 기록합니다.
- 남은 제한: `/me`와 다른 업무 API, 회원 인증(D-24), 공개 환경 CORS(미정), `users`와 회원의 관계(미결). 서버 DB 적용·배포는 하지 않았습니다.
