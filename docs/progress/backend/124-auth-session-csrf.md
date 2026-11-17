# #124 회원 인증 U1 세션·CSRF 기반과 CSRF 토큰 조회 API

브랜치 `feat/124-auth-session-csrf`, 시작 기준 `origin/develop` `c02409c`(PR #121 병합).

## 2026-11-18 · `auth` 패키지·Flyway V9(임시 번호) · PR(`develop` 대상, 병합 전)
- 근거: 관리 기록 결정 30(남은 API 14개, 인증 9개 관리측 담당 해석)·결정 28(관리자 판단), [D-24](../../decisions.md#d-24-확정-정책), [D-09](../../decisions.md#d-09d-14-확정-정책), [D-23](../../decisions.md#d-23-확정-정책).
- 계약 출처: 저장소 밖 Notion 인증 명세 9개 페이지의 고정 사본 `sources/api-loop-20261116/api-spec-snapshot.md`(관리 폴더)입니다. Notion 마지막 편집은 2026-11-11T09:04Z, 수집은 2026-11-16T06:27:03Z(기준 `develop` `5885454`)입니다. 지정된 정합화 사본 `notion-spec-resync-applied.json`(2026-11-16T13:31:04Z)에는 인증 9개의 카탈로그 행만 있고 본문은 보류(`held: 9authpages`)되어, 관리자가 위 사본을 출처로 인정했습니다(질문 `msg_d1333c769ead` → 답 `msg_29177a2bdb21`). Notion 원문을 다시 대조하지는 않았습니다.
- 한 일:
  - `backend/src/main/java/com/beone/api/auth/`(`web`/`application`/`persistence`): `GET /api/v1/auth/csrf`, `/api/v1/auth/**` 인터셉터(모든 응답 `Cache-Control: no-store`, POST의 CSRF 검사), 403 `CSRF_INVALID` 기능별 처리기. 모두 `local`·`demo` 프로필 전용입니다. `common/**`·`user/**`·`application*.yml`·CORS는 바꾸지 않았습니다.
  - Flyway `V9__auth_sessions.sql`: 세션 ID SHA-256 해시(고유), 생성·마지막 사용·미사용 만료·절대 만료 시각, 미사용 만료 ≤ 절대 만료 `CHECK`. **임시 번호**이며 병합 직전 관리자가 순서를 정합니다.
  - 테스트: 단위 `SessionTokensTest`·`AnonymousSessionPropertiesTest`·`AuthSessionServiceTest`·`SessionCookieTest`, 웹 `AuthCsrfApiWebTest`·`AuthCsrfProfileOffWebTest`, 통합 `AuthSessionIntegrationTest`. 기대값은 사본(`docs/api/auth.md`)과 D-24에서 먼저 옮겼습니다. U1에는 인증 POST API가 없어 테스트 클래스 안의 전용 POST 경로로 CSRF 검사를 확인합니다.
  - 문서: [인증 API 명세](../../api/auth.md) 신규(사본 9개 반영, 사본에 없는 값은 「미정」), [API 목록](../../api/README.md) 한 줄, [공통 문서](../../api.md) 업무 오류 코드 「인증」 묶음과 예약 코드 문장, [auth 안내](../../domains/auth.md) 신규, [구조 안내](../../architecture.md)의 auth 항목·패키지 트리 줄·구현 현황 문장의 인증 부분.
- 서비스에서 달라진 점: `local`·`demo`에서 `GET /api/v1/auth/csrf`로 CSRF 토큰과 익명 세션 쿠키를 받을 수 있습니다. 인증 POST API(가입·로그인 등)는 아직 없습니다. 기존 업무 API와 시연 사용자 헤더 동작은 그대로입니다.
- 관리자 판단으로 정한 것(결정 28·30, 답 `msg_29177a2bdb21`):
  - `docs/api.md` 오류 표에 「인증」 묶음 추가, `docs/api/auth.md` 신규와 README 한 줄 허용.
  - 회원은 별도 `members` 테이블(ID `usr-` 문자열, U2)로 두고 `users`·`/me`·`CurrentUserResolver`·시연 헤더는 바꾸지 않습니다. 회원과 포트폴리오의 관계는 D-24 미결로 둡니다.
  - Spring Security 없이 auth 패키지에서 직접 구현합니다. 의존성은 U2에서 `bcprov-jdk18on` 1개만 추가하고, Argon2id는 m=19456KiB·t=2·p=1(OWASP 최소 권장)입니다. U1은 의존성을 추가하지 않습니다.
  - 인증 API 전체를 `local`·`demo` 전용으로 등록하고, 메일은 가짜 발송기만 둡니다(U2).
  - CORS는 바꾸지 않고, 화면 연결이 불가하다는 점을 미결로 기록합니다.
- 사용자 결정 대기(관리자가 사용자에게 질문, 답이 올 때까지 병합하지 않음):
  - 익명 CSRF 세션 수명: 설정값 `beone.auth.anonymous-session.idle-timeout`·`absolute-timeout`으로 주입합니다. 기본값 `P7D`·`P30D`는 **사용자 결정 대기**이며, 테스트는 미사용 1시간·최대 3시간을 명시합니다.
  - 로그인 실패 제한(429) 기준: U3 범위라 U1 코드에는 영향이 없습니다.
- 작은 구현 결정:
  - CSRF 토큰은 저장하지 않고 세션 ID에서 SHA-256으로 유도합니다(고정 접두어로 세션 해시와 구분). 세션마다 하나라 여러 탭에서 같은 토큰을 쓸 수 있고, 세션 ID가 바뀌면 토큰도 바뀝니다. 비밀 키가 필요 없습니다. 쿠키가 `HttpOnly`라 다른 사이트가 세션 ID를 읽어 토큰을 만들 수 없습니다.
  - CSRF 검사는 서블릿 필터가 아니라 MVC 인터셉터로 합니다. 기능별 처리기로 공통 응답 형식을 그대로 쓰고, 본문 검증(400)보다 먼저 403이 나게 하려는 것입니다. 없는 경로·405는 공통 처리를 따릅니다.
  - 인터셉터는 세션 서비스를 `ObjectProvider`로 요청 때 꺼냅니다. 다른 기능의 `local` 웹 슬라이스 테스트에 이 설정이 함께 올라와도 기존 테스트를 고칠 필요가 없게 하려는 것입니다.
  - `Set-Cookie`는 사본 예시와 같은 속성·순서로 직접 씁니다(`Expires` 없음). MockMvc 응답 객체는 쿠키를 다시 써서 `Expires`를 덧붙이므로, 헤더 원문은 단위 테스트와 통합 테스트(실제 HTTP)로 확인합니다.
  - 같은 이름의 세션 쿠키가 여럿이면 첫 번째만 씁니다. 만료한 세션 행은 조회할 때 지우고, 주기적 정리는 두지 않았습니다.
  - 시각은 `Clock` 빈이 하나 있으면 그것을, 없으면 시스템 시계를 쓰고 Asia/Seoul 초 단위로 저장합니다(다른 기능과 같은 방식).
- 검증: `./gradlew test webTest integrationTest build javadoc --rerun-tasks` 결과는 PR 본문에 기록합니다.
- 남은 제한: 가입·인증메일·이메일 인증(U2), 로그인·로그아웃·세션 조회(U3, 회원 세션 열 추가), 비밀번호 재설정(U4), 익명 세션 수명과 로그인 429 기준의 사용자 결정, 공개 프로필·CORS, Flyway 번호 확정. 서버 DB 적용·배포, frontend 변경은 하지 않았습니다.
