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
- 2026-11-18 사용자 직접 결정(Aside 질문, 관리자 전달 `msg_17a03b6593e4`·`msg_ae33bca0cfaa`):
  - 익명 CSRF 세션은 미사용 2시간에 만료하고 회원 동시 세션 5개 계산에서 뺍니다. 설정 기본값 `idle-timeout`을 `PT2H`로 바꿨습니다.
  - 로그인 실패 제한: 같은 이메일로 15분 안에 5번 실패하면 15분 동안 429 `RATE_LIMITED`입니다(이메일 기준, IP 기준 없음, 이메일 존재 여부를 구분하지 않음). U3에서 구현하며 U1 코드에는 영향이 없습니다.
  - 회원 세션의 미사용 7일·최대 30일·동시 5개는 그대로입니다. IP 추가 제한과 프록시(`X-Forwarded-For`) 정책은 넣지 않고 공개 배포 전 별도 점검으로 남깁니다.
- 정정(관리자 `msg_b64f23b28e75`가 `msg_16e9f77e7fc6`의 판단을 철회): 익명 세션의 절대 상한은 **미결**입니다. 사용자 승인값은 미사용 2시간과 동시 5개 계산 제외뿐입니다. 현재 `absolute-timeout` 기본 `PT2H`·쿠키 `Max-Age=7200`은 확정 전 임시값이고, 병합 전에 정합니다. 아래 문장은 철회 전 기록입니다: 익명 세션의 절대 상한도 2시간이었습니다. 사용자 선택지의 '짧게, 오래된 익명 세션이 쌓이지 않게' 의도에 맞춘 것입니다. 로그인에 성공하면 익명 세션을 폐기하고 새 회원 세션을 발급합니다(세션 고정 방지, U3). 사본의 "세션 ID 갱신"과 같은 뜻이라 충돌은 없습니다.
- 작은 구현 결정:
  - CSRF 토큰은 저장하지 않고 세션 ID에서 SHA-256으로 유도합니다(고정 접두어로 세션 해시와 구분). 세션마다 하나라 여러 탭에서 같은 토큰을 쓸 수 있고, 세션 ID가 바뀌면 토큰도 바뀝니다. 비밀 키가 필요 없습니다. 쿠키가 `HttpOnly`라 다른 사이트가 세션 ID를 읽어 토큰을 만들 수 없습니다.
  - CSRF 검사는 서블릿 필터가 아니라 MVC 인터셉터로 합니다. 기능별 처리기로 공통 응답 형식을 그대로 쓰고, 본문 검증(400)보다 먼저 403이 나게 하려는 것입니다. 없는 경로·405는 공통 처리를 따릅니다.
  - 인터셉터는 세션 서비스를 `ObjectProvider`로 요청 때 꺼냅니다. 다른 기능의 `local` 웹 슬라이스 테스트에 이 설정이 함께 올라와도 기존 테스트를 고칠 필요가 없게 하려는 것입니다.
  - `Set-Cookie`는 사본 예시와 같은 속성·순서로 직접 씁니다(`Expires` 없음). MockMvc 응답 객체는 쿠키를 다시 써서 `Expires`를 덧붙이므로, 헤더 원문은 단위 테스트와 통합 테스트(실제 HTTP)로 확인합니다.
  - 같은 이름의 세션 쿠키가 여럿이면 첫 번째만 씁니다. 만료한 세션 행은 조회할 때 지우고, 주기적 정리는 두지 않았습니다.
  - 시각은 `Clock` 빈이 하나 있으면 그것을, 없으면 시스템 시계를 쓰고 Asia/Seoul 초 단위로 저장합니다(다른 기능과 같은 방식).
- 검증: `./gradlew test webTest integrationTest build javadoc --rerun-tasks` 결과는 PR 본문에 기록합니다.
- 남은 제한: 가입·인증메일·이메일 인증(U2), 로그인·로그아웃·세션 조회(U3, 회원 세션 열 추가), 비밀번호 재설정(U4), 익명 세션 수명과 로그인 429 기준의 사용자 결정, 공개 프로필·CORS, Flyway 번호 확정. 서버 DB 적용·배포, frontend 변경은 하지 않았습니다.

## 2026-11-18 · CodeRabbit 리뷰 반영(head `17b8d2c` 리뷰)
- 리뷰 상태: 제출 리뷰 1건(head `17b8d2c`, 인라인 2건, 모두 Minor). 사람 승인 없음. 그 사이 head `bc60960`(익명 세션 수명 결정 반영)을 올렸고, 두 지적은 `bc60960`에도 그대로 해당해 함께 고쳤습니다.
- 반영 1(동시 요청의 세션 삭제): `use`가 잠금 없이 읽은 옛 만료 값으로, 다른 요청이 방금 연장한 세션을 지울 수 있었습니다. `AuthSessionRepository.findBySessionHash`에 `PESSIMISTIC_WRITE` 잠금을 걸었습니다. 이 메서드는 트랜잭션인 `use`에서만 부릅니다. `AuthSessionIntegrationTest#concurrentUseWaitsForExtensionAndDoesNotDeleteExtendedSession`은 테스트 트랜잭션이 행을 잠그고 연장하는 동안 CSRF 조회가 잠금을 기다리는지 `performance_schema.data_lock_waits`로 확인한 뒤 커밋합니다. 잠금을 빼고 돌리면 연장된 세션을 지우고 새 쿠키를 심어 실패하고, 잠금이 있으면 통과합니다(되돌린 코드는 커밋하지 않음).
- 반영 2(404·405의 `no-store`): 인터셉터는 핸들러가 정해진 요청에서만 돌아, `POST /api/v1/auth/csrf`(405)와 없는 인증 경로(404)에 `Cache-Control: no-store`가 빠졌습니다. 헤더는 서블릿 필터 `AuthNoStoreFilter`(`/api/v1/auth/*`)로 옮기고, CSRF 검사는 인터셉터에 남겼습니다. 웹 테스트와 통합 테스트에 405·404의 헤더 검사를 더했습니다.
- Nitpick 반영(같은 리뷰): `docs/domains/auth.md`의 주요 클래스 설명에 소스 파일 상대 링크를 달았습니다(CLAUDE.md 도메인 문서 규칙).

## 2026-11-18 · 최신 `develop` merge(#122 → PR #123 병합 `c3ce45e`)
- `origin/develop`을 merge했습니다(rebase 아님). 충돌은 `docs/progress/backend/README.md` 진행 목록뿐이었고, develop의 #120·#122 줄을 유지한 채 #124 줄을 그 뒤에 두었습니다.
- Flyway: `develop`이 `V8`까지라 `V9__auth_sessions.sql`을 그대로 둡니다. 다른 작업선과의 최종 순서는 병합 직전 관리자가 정합니다.

## 2026-11-18 · 익명 세션 절대 상한 없음 확정(사용자 직접 결정)
- 경위: 절대 상한은 미결(`msg_b64f23b28e75`) → 판단 보고(`msg_3b34e38d2b1e`, 추천 A) → 2026-11-18 사용자 직접 확정(Aside 질문, 관리자 전달 `msg_50deb5fad9d1`·`msg_26e32287fcf8`). 철회된 '절대 2시간(관리자 판단)'과는 다른 결정입니다.
- 최종 정책: 미사용 2시간, 사용할 때 쿠키 수명과 서버 마지막 사용 갱신, 발급 기준 절대 상한 없음, 동시 5개 계산 제외, 로그인 때 익명 세션 폐기와 새 회원 세션 발급(U3), 회원 세션 7일·30일·5개는 그대로.
- 코드:
  - `AnonymousSessionProperties`에서 `absolute-timeout`을 없앴습니다.
  - V9 `absolute_expires_at`을 `NULL` 허용으로 바꾸고, `CHECK`를 "절대 만료가 있으면 미사용 만료 ≤ 절대 만료"로 고쳤습니다. 익명 세션은 `NULL`이고, 회원 세션의 30일은 U3에서 채웁니다.
  - `AuthSessionService.use`가 새 쿠키 수명(이번 사용부터 새 미사용 만료까지)을 `ActiveSession`에 담아 돌려줍니다.
  - `GET /api/v1/auth/csrf`(유효 세션)와 CSRF를 통과한 인증 POST가 같은 쿠키를 새 `Max-Age`로 다시 심습니다. POST 응답의 `Set-Cookie`는 사본에 덧붙인 것으로 `docs/api/auth.md`에 적었습니다.
- 테스트: 통합 테스트는 수명 설정을 덮어쓰지 않고 기본값(2시간)을 씁니다. 가변 시계를 주입하고 sleep은 쓰지 않습니다. 필수 시험별 이름:
  - ① `AuthSessionIntegrationTest#sustainedUseKeepsAnonymousSessionBeyondTwoHoursAfterCreation`: 110분 간격으로 6번 써서 발급 뒤 11시간까지, 이어서 발급 시각을 40일 전으로 옮긴 뒤에도 유지됩니다(절대 상한 없음).
  - ② `AuthSessionIntegrationTest#anonymousSessionExpiresAfterTwoHoursWithoutUse`: 마지막 사용 뒤 1시간 59분 59초에는 유효하고, 정확히 2시간이면 만료합니다. 옛 행을 지우고 새 세션을 발급하며, 옛 쿠키로 보낸 POST는 403입니다.
  - ③ `AuthSessionIntegrationTest#cookieMaxAgeIsRefreshedOnEachValidUseAndMatchesServerIdleExpiry`: 발급, GET csrf, CSRF를 통과한 POST마다 `Max-Age=7200` 헤더 원문과 서버 `idle_expires_at - last_used_at = 7200초`가 맞습니다. CSRF 실패에는 쿠키가 없습니다.
  - 단위·웹 테스트: `AuthSessionServiceTest`(절대 만료 `NULL` 저장, 40일 전 발급이어도 유효, 절대 만료가 있는 세션의 상한과 쿠키 수명), `AuthCsrfApiWebTest`(GET csrf와 POST의 쿠키 갱신, 403에는 쿠키 없음), `AnonymousSessionPropertiesTest`.
  - ④ 로그인 교체 검증은 U3 병합 조건으로 이월(로그인 API 미존재). 로그인 때 세션 ID·CSRF 교체와 익명 세션 폐기는 U3(login) PR에서 시험합니다. U1에는 테스트만을 위한 교체 메서드를 넣지 않습니다(관리자 판단 결정 28, 질문 `msg_445468a45949` → 답 `msg_05e6695fdc35`).
