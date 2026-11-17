# auth 도메인 안내

## 목적과 범위

`auth`는 회원 인증([D-24](../decisions.md#d-24-확정-정책))을 맡습니다. 회원 가입·이메일 인증·로그인·세션·비밀번호 재설정 API 9개를 작은 단위로 나눠 구현합니다. API 계약은 [인증 API 명세](../api/auth.md)에 있습니다.

구현된 범위(U1, #124):

- 서버 세션 테이블 `auth_sessions`(Flyway `V9__auth_sessions.sql`, 임시 번호)
- 세션 쿠키 `__Host-BEONE_SESSION`의 발급·읽기
- CSRF 토큰 조회 `GET /api/v1/auth/csrf`. 세션이 없으면 익명 세션을 발급합니다.
- `/api/v1/auth/**` POST 요청의 CSRF 검사와, 모든 인증 응답의 `Cache-Control: no-store`
- CSRF 실패 응답 403 `CSRF_INVALID`

구현하지 않은 범위:

- 가입·인증메일·이메일 인증 완료(U2), 로그인·로그아웃·세션 조회(U3), 비밀번호 재설정(U4)
- 회원 테이블, Argon2id 비밀번호 해시, 가짜 메일 발송기, 메일 요청 제한(U2 이후)
- 회원과 시연 사용자(`users`)·`/me` 업무 자원의 연결. 회원과 두 포트폴리오 모드의 관계가 D-24 미결이라 연결하지 않습니다.
- 공개 프로필 활성화와 공개 CORS. 인증 부품은 `local`·`demo` 프로필에서만 등록됩니다.

## 핵심 용어

- **서버 세션:** 서버 DB(MySQL)에 저장한 로그인 상태입니다. 브라우저는 세션 ID만 쿠키로 들고 있습니다. Redis는 쓰지 않습니다.
- **익명 세션:** 회원 로그인 전에 CSRF 토큰을 받으려고 만드는 세션입니다. 회원 로그인이 아닙니다. 미사용 2시간에 만료하고(2026-11-18 사용자 직접 결정) 회원 동시 세션 5개 계산에 넣지 않습니다.
- **CSRF(교차 사이트 요청 위조):** 다른 사이트가 사용자의 브라우저를 이용해 쿠키가 붙은 요청을 몰래 보내는 공격입니다. 서버는 쿠키와 함께 그 세션의 토큰을 헤더(`X-CSRF-TOKEN`)로 받아, 다른 사이트가 알 수 없는 값이 있는지 확인합니다.
- **`__Host-` 쿠키:** 브라우저가 `Secure`·`Path=/`·`Domain` 없음일 때만 받아 주는 이름 접두어입니다. 하위 도메인이 쿠키를 덮어쓰지 못하게 합니다.

## 패키지 구조

[D-23](../decisions.md#d-23-확정-정책) 13항의 `web`/`application`/`persistence` 구조입니다.

```text
com.beone.api.auth
├── web/
│   ├── AuthCsrfController        GET /api/v1/auth/csrf, local·demo
│   ├── CsrfTokenResponse         응답 data(headerName, token)
│   ├── SessionCookie             쿠키 이름, 읽기, Set-Cookie 문자열
│   ├── AuthRequestInterceptor    /api/v1/auth/** POST CSRF 검사
│   ├── AuthNoStoreFilter         /api/v1/auth/** 모든 응답(404·405 포함)에 no-store
│   ├── AuthWebConfiguration      위 인터셉터·필터 등록, local·demo
│   ├── CsrfInvalidException      CSRF 실패(토큰 값을 담지 않음)
│   ├── AuthErrorCode             인증 오류 코드(U1은 CSRF_INVALID만)
│   └── AuthExceptionHandler      auth.web 컨트롤러의 기능별 처리기(@Order 최우선)
├── application/
│   ├── AuthSessionService        세션 발급·조회·만료 판정, local·demo
│   ├── SessionTokens             세션 ID·CSRF 토큰 생성, SHA-256 해시, 상수 시간 비교
│   ├── AnonymousSessionProperties 익명 세션 수명 설정값(미사용 2시간, 절대 상한 없음)
│   ├── AuthConfiguration         설정값 등록, local·demo
│   └── ActiveSession·IssuedSession 결과 타입(toString에서 원문 숨김)
└── persistence/
    ├── AuthSessionEntity         auth_sessions 한 행
    └── AuthSessionRepository     해시로 세션 찾기(쓰기 잠금)
```

소스: [web](../../backend/src/main/java/com/beone/api/auth/web/), [application](../../backend/src/main/java/com/beone/api/auth/application/), [persistence](../../backend/src/main/java/com/beone/api/auth/persistence/), [마이그레이션](../../backend/src/main/resources/db/migration/V9__auth_sessions.sql).

## 주요 클래스의 역할과 관계

- [`AuthSessionService`](../../backend/src/main/java/com/beone/api/auth/application/AuthSessionService.java)는 쿠키 값으로 세션을 찾고(`use`), 인증 POST에서는 CSRF 토큰까지 맞을 때만 세션을 갱신하며(`useWithCsrf`, 조회·검사·갱신이 한 트랜잭션이라 틀린 토큰은 서버 만료를 늘리지 않음), 익명 세션을 발급합니다(`issueAnonymous`). 세션은 미사용 만료에 끝나고, 절대 만료가 있으면 그 시각에도 끝납니다. 같은 시각이 되면 이미 만료입니다. 익명 세션은 절대 만료가 없습니다(`absolute_expires_at`이 `NULL`, 2026-11-18 사용자 직접 결정). 회원 세션의 30일은 로그인 단위(U3)에서 채웁니다. 유효한 세션을 쓸 때마다 미사용 만료를 늘리되 절대 만료가 있으면 넘기지 않고, 이번 사용 시각부터 새 미사용 만료까지를 쿠키 `Max-Age`로 돌려줍니다. 만료한 행은 찾을 때 지웁니다. 조회에 쓰기 잠금(`PESSIMISTIC_WRITE`)을 걸어, 같은 세션을 쓰는 동시 요청이 옛 만료 값으로 판정해 다른 요청이 방금 연장한 세션을 지우지 않게 합니다.
- [`SessionTokens`](../../backend/src/main/java/com/beone/api/auth/application/SessionTokens.java)는 상태가 없는 부품입니다.
  - 세션 ID: `SecureRandom` 32바이트를 패딩 없는 Base64URL로 쓴 43자입니다. DB에는 SHA-256 해시(16진수 64자)만 저장합니다.
  - CSRF 토큰: 세션 ID에 고정 접두어를 붙여 SHA-256으로 유도한 43자입니다. 서버는 토큰을 따로 저장하지 않고, 요청 쿠키에서 다시 계산해 상수 시간(`MessageDigest.isEqual`)으로 비교합니다. 쿠키가 `HttpOnly`라 다른 사이트의 스크립트는 세션 ID를 읽어 토큰을 만들 수 없습니다. 세션 ID가 바뀌면(로그인·로그아웃, U3) 토큰도 바뀝니다.
- [`AuthRequestInterceptor`](../../backend/src/main/java/com/beone/api/auth/web/AuthRequestInterceptor.java)는 핸들러가 정해진 `/api/v1/auth/**` 요청에서 실행됩니다. 그래서 CSRF 실패(403)가 본문 검증(400)보다 먼저 납니다. 없는 경로(404)·지원하지 않는 메서드(405)는 공통 처리를 따릅니다. `Cache-Control: no-store`는 핸들러 조회 전에 도는 서블릿 필터 [`AuthNoStoreFilter`](../../backend/src/main/java/com/beone/api/auth/web/AuthNoStoreFilter.java)가 붙이므로 404·405에도 들어갑니다. 세션 서비스는 `ObjectProvider`로 요청 때 꺼냅니다. 다른 기능의 웹 슬라이스 테스트에 이 설정이 함께 올라와도 세션 서비스 빈이 필요 없게 하려는 것입니다.
- [`AuthExceptionHandler`](../../backend/src/main/java/com/beone/api/auth/web/AuthExceptionHandler.java)는 `auth.web` 패키지의 컨트롤러에만 적용합니다. 인터셉터가 던진 예외도 그 요청의 핸들러 기준으로 이 처리기가 받습니다.
- 요청 진입점은 [`AuthCsrfController`](../../backend/src/main/java/com/beone/api/auth/web/AuthCsrfController.java), 쿠키 문자열은 [`SessionCookie`](../../backend/src/main/java/com/beone/api/auth/web/SessionCookie.java), 인터셉터·필터 등록은 [`AuthWebConfiguration`](../../backend/src/main/java/com/beone/api/auth/web/AuthWebConfiguration.java), 익명 세션 수명은 [`AnonymousSessionProperties`](../../backend/src/main/java/com/beone/api/auth/application/AnonymousSessionProperties.java), 저장은 [`AuthSessionEntity`](../../backend/src/main/java/com/beone/api/auth/persistence/AuthSessionEntity.java)·[`AuthSessionRepository`](../../backend/src/main/java/com/beone/api/auth/persistence/AuthSessionRepository.java)입니다.
- 인증 부품은 `X-BeONE-Demo-User` 헤더와 `CurrentUserResolver`를 쓰지 않습니다. `user` 패키지와 의존 관계가 없습니다.

## 처리 흐름

```text
GET /api/v1/auth/csrf
→ AuthNoStoreFilter: Cache-Control: no-store
→ AuthCsrfController
   → SessionCookie.read: __Host-BEONE_SESSION 값(같은 이름이 여럿이면 첫 번째)
   → AuthSessionService.use: 형식 검사 → 해시로 조회(쓰기 잠금) → 만료면 행 삭제 → 유효하면 사용 시각 갱신
   → 유효: 200 {headerName, token}
           + Set-Cookie: 같은 ID, Max-Age=<새 미사용 만료까지 초>(서버 만료와 같은 시각)
   → 없음·만료: issueAnonymous → 200 {headerName, token}
                + Set-Cookie: __Host-BEONE_SESSION=<ID>; Path=/; Max-Age=<미사용 수명 초>; Secure; HttpOnly; SameSite=Lax

POST /api/v1/auth/... (U2 이후 API)
→ AuthNoStoreFilter: no-store
→ AuthRequestInterceptor: AuthSessionService.useWithCsrf(쿠키, X-CSRF-TOKEN): 조회(쓰기 잠금) → 만료면 삭제 → 토큰 비교 → 맞을 때만 사용 시각 갱신
   → 실패: CsrfInvalidException → AuthExceptionHandler → 403 CSRF_INVALID
   → 통과: 세션 쿠키를 새 Max-Age로 다시 심음 → 본문 검증과 컨트롤러 처리
```

## 짧은 사용 예

```bash
# 1. 토큰과 익명 세션 쿠키 받기
curl -i http://localhost:8080/api/v1/auth/csrf
# HTTP/1.1 200
# Set-Cookie: __Host-BEONE_SESSION=<세션 ID>; Path=/; Max-Age=7200; Secure; HttpOnly; SameSite=Lax  (기본 idle-timeout PT2H일 때)
# Cache-Control: no-store
# {"data":{"headerName":"X-CSRF-TOKEN","token":"<토큰>"}}

# 2. 이후 인증 POST는 같은 쿠키와 토큰 헤더를 함께 보낸다(U2 이후 API).
curl -X POST http://localhost:8080/api/v1/auth/sign-up \
  -H 'Cookie: __Host-BEONE_SESSION=<세션 ID>' -H 'X-CSRF-TOKEN: <토큰>' \
  -H 'Content-Type: application/json' -d '{"email":"user@example.com","password":"..."}'
```

## 설정

| 키 | 의미 | 기본값 |
| --- | --- | --- |
| `beone.auth.anonymous-session.idle-timeout` | 익명 세션 미사용 만료, 쿠키 `Max-Age` | `PT2H` (2026-11-18 사용자 직접 결정) |

익명 세션에는 발급 기준 절대 상한이 없어 설정 키도 없습니다(2026-11-18 사용자 직접 결정). 미사용 만료는 1초 이상이어야 합니다. 어기면 서버가 시작하지 않습니다. `application*.yml`에는 값을 넣지 않았고, 테스트는 값을 명시해 주입합니다.

## 제약과 현재 구현 범위

- 인증 API는 `local`·`demo` 프로필 전용입니다. 공개 프로필의 활성화는 메일 공급자 결정 뒤에 정합니다.
- `local`·`demo` CORS는 자격 증명을 허용하지 않아, 지금은 `localhost:5173` 화면에서 인증 API로 쿠키를 보낼 수 없습니다. API와 테스트는 동작합니다. 화면 연결 때 CORS를 따로 결정합니다.
- 회원 세션(회원 ID 연결, 7일·30일·동시 5개)은 U3에서 `auth_sessions`에 회원 열을 더해 구현합니다. U1의 세션은 모두 익명 세션입니다.
- 만료 세션은 조회할 때만 지웁니다. 주기적 정리는 없습니다.
- 테스트: `SessionTokensTest`·`AnonymousSessionPropertiesTest`·`AuthSessionServiceTest`·`SessionCookieTest`(단위), `AuthCsrfApiWebTest`·`AuthCsrfProfileOffWebTest`(웹 슬라이스, 테스트 전용 POST 경로로 CSRF 검사), `AuthSessionIntegrationTest`(실제 HTTP·MySQL, V9 적용·해시 저장·만료).
