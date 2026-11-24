# auth 도메인 안내

## 목적과 범위

`auth`는 회원 인증([D-24](../decisions.md#d-24-확정-정책))을 맡습니다. 회원 가입·이메일 인증·로그인·세션·비밀번호 재설정 API 9개를 작은 단위로 나눠 구현합니다. API 계약은 [인증 API 명세](../api/auth.md)에 있습니다.

구현된 범위:

- U1(#124)
  - 서버 세션 테이블 `auth_sessions`(Flyway `V9__auth_sessions.sql`, 임시 번호)
  - 세션 쿠키 `__Host-BEONE_SESSION`의 발급·읽기
  - CSRF 토큰 조회 `GET /api/v1/auth/csrf`. 세션이 없으면 익명 세션을 발급합니다.
  - `/api/v1/auth/**` POST 요청의 CSRF 검사와, 모든 인증 응답의 `Cache-Control: no-store`(`member` 프로필에서는 `/api/v1/me`·`/api/v1/me/**` 응답에도)
  - CSRF 실패 응답 403 `CSRF_INVALID`
- U2(#128)
  - 회원 테이블 `members`, 1회용 토큰 `auth_tokens`, 메일 요청 제한 기록 `auth_mail_throttles`(Flyway `V11__auth_members.sql`, 임시 번호)
  - 회원가입 `POST /api/v1/auth/sign-up`, 인증메일 재요청 `POST /api/v1/auth/email-verification-requests`, 이메일 인증 완료 `POST /api/v1/auth/email-verifications`
  - Argon2id 비밀번호 해시, 이메일 인증 토큰(30분·1회용), 같은 주소 60초 메일 요청 제한(429 `RATE_LIMITED`·`Retry-After`), 가짜 메일 발송기
- U3(#132)
  - 회원 로그인 `POST /api/v1/auth/login`, 로그아웃 `POST /api/v1/auth/logout`, 현재 회원 세션 조회 `GET /api/v1/auth/session`
  - 회원 세션: `auth_sessions`에 회원 ID 열 추가, 미사용 7일·로그인 뒤 최대 30일·회원당 동시 5개(6번째는 가장 오래 쓰지 않은 세션 종료). 로그인 때 세션 ID·CSRF 토큰 교체와 이전 세션 폐기(세션 고정 방지)
  - 로그인 실패 제한: 같은 이메일 15분 안 5번 실패 시 15분 잠금(429 `RATE_LIMITED`·`Retry-After`), 실패 기록 `auth_login_failures`·잠금 상태 `auth_login_throttles`(Flyway `V13__auth_member_sessions.sql`, 임시 번호)
- U4(#138)
  - 비밀번호 재설정 요청 `POST /api/v1/auth/password-reset-requests`, 재설정 완료 `POST /api/v1/auth/password-resets`
  - 재설정 토큰(15분·1회용, 재발급 시 이전 토큰 무효)을 `auth_tokens`에 목적 `PASSWORD_RESET`으로 해시만 저장. 새 테이블·Flyway 파일 없음
  - 메일 60초 제한은 가입·인증메일 재요청과 합산, 계정 유무와 관계없이 같은 202. 완료하면 비밀번호를 바꾸고 그 회원의 모든 세션과 재설정 토큰을 무효화(D-24)
- G2 첫 수직 기능(#157, [결정 37](../decisions.md#결정-37-회원과-업무-장부-연결))
  - 로컬 회원 프로필 `member`: 인증 부품을 `local`·`demo`와 함께 이 프로필에도 등록(메일은 가짜 발송기)
  - 이메일 인증 완료 때 같은 트랜잭션에서 `MemberEmailVerifiedEvent`를 발행해 회원의 '내 카드' 장부를 만들게 함(받는 쪽은 [user](user.md))
  - `member`의 업무 API(`/api/v1/me`, `/api/v1/me/**`) 세션 검사: 401 `UNAUTHENTICATED` → 쓰기 CSRF 403 `CSRF_INVALID` 순서, 통과하면 미사용 7일 연장과 쿠키 재발급, 회원 ID를 요청 속성에 담음

구현하지 않은 범위:

- 실메일 발송·외부 공급자·메일 링크 주소 형식(미정)
- `local`·`demo`에서 회원과 `/me` 업무 자원의 연결. 이 두 프로필은 시연 헤더 방식을 유지합니다. 회원 연결은 `member` 프로필의 `/me`·보유 카드·월간 상태·결제 전 설정(G3a #159)·주문·예정 소비·추천(G3b #160)까지입니다. 모의 승인·원장의 `member` 등록은 G3 후속 범위(G3c)이고, 공개 회원의 CODEF 연결 범위(D9)와 회원용 가상 포트폴리오(D1b)는 미결입니다.
- 공개 프로필 활성화와 공개 CORS(O1~O3 미결). 인증 부품은 `local`·`demo`·`member` 프로필에서만 등록됩니다.

## 핵심 용어

- **서버 세션:** 서버 DB(MySQL)에 저장한 로그인 상태입니다. 브라우저는 세션 ID만 쿠키로 들고 있습니다. Redis는 쓰지 않습니다.
- **익명 세션:** 회원 로그인 전에 CSRF 토큰을 받으려고 만드는 세션입니다. 회원 로그인이 아닙니다. 미사용 2시간에 만료하고(2026-11-18 사용자 직접 결정) 회원 동시 세션 5개 계산에 넣지 않습니다.
- **CSRF(교차 사이트 요청 위조):** 다른 사이트가 사용자의 브라우저를 이용해 쿠키가 붙은 요청을 몰래 보내는 공격입니다. 서버는 쿠키와 함께 그 세션의 토큰을 헤더(`X-CSRF-TOKEN`)로 받아, 다른 사이트가 알 수 없는 값이 있는지 확인합니다.
- **`__Host-` 쿠키:** 브라우저가 `Secure`·`Path=/`·`Domain` 없음일 때만 받아 주는 이름 접두어입니다. 하위 도메인이 쿠키를 덮어쓰지 못하게 합니다.
- **Argon2id:** 비밀번호 저장용 해시 함수입니다. 일부러 메모리(19MiB)와 시간을 써서, DB가 유출돼도 비밀번호를 대량으로 맞혀 보기 어렵게 합니다.
- **1회용 토큰:** 메일로 보내는 43자 무작위 문자열입니다. DB에는 SHA-256 해시만 두고, 쓰거나 다시 발급하면 지웁니다.
- **회원 세션:** 로그인에 성공하면 만드는 세션입니다. 미사용 7일과 로그인 뒤 30일 중 먼저 오는 시각에 끝나고, 한 회원은 동시에 5개까지 가집니다(D-24).
- **세션 고정 방지:** 로그인 전에 받은 세션 ID를 로그인 뒤에도 쓰면, 그 ID를 미리 심어 둔 공격자가 로그인된 세션을 가로챌 수 있습니다. 그래서 로그인하면 이전 세션을 지우고 새 세션 ID를 발급합니다.
- **계정 존재 비공개:** 가입·인증메일 재요청·재설정 요청은 계정이 있든 없든, 인증했든 안 했든 같은 202로 답해, 응답으로 어떤 이메일이 가입했는지 알 수 없게 합니다.

## 패키지 구조

[D-23](../decisions.md#d-23-확정-정책) 13항의 `web`/`application`/`persistence` 구조입니다.

```text
com.beone.api.auth
├── web/
│   ├── AuthCsrfController        GET /api/v1/auth/csrf, local·demo·member
│   ├── CsrfTokenResponse         응답 data(headerName, token)
│   ├── SessionCookie             쿠키 이름, 읽기, Set-Cookie 문자열
│   ├── AuthRequestInterceptor    /api/v1/auth/** POST CSRF 검사
│   ├── AuthNoStoreFilter         /api/v1/auth/** 모든 응답(404·405 포함)에 no-store, member에서는 /api/v1/me/**에도
│   ├── AuthWebConfiguration      위 인터셉터·필터 등록, local·demo·member
│   ├── CsrfInvalidException      CSRF 실패(토큰 값을 담지 않음)
│   ├── MemberRegistrationController 가입·인증메일 재요청·이메일 인증 완료, local·demo·member
│   ├── SignUpRequest·EmailVerificationRequestRequest·EmailVerificationRequest 요청 본문(정의하지 않은 필드는 400)
│   ├── MemberEmail·NewPassword   이메일 형식·새 비밀번호 규칙 검사
│   ├── UnknownFieldRejecting     정의하지 않은 필드 거부
│   ├── AcceptedResponse          202 안내 문구
│   ├── MemberSessionController   로그인·로그아웃·세션 조회, local·demo·member
│   ├── LoginRequest·LoginResponse·MemberSessionResponse 로그인 요청·응답, 세션 조회 응답(+09:00 시점)
│   ├── InvalidCredentialsException·EmailNotVerifiedException·UnauthenticatedException·LoginRateLimitedException 401·403·401·429 예외
│   ├── PasswordResetController   재설정 요청·완료, local·demo·member
│   ├── PasswordResetRequestRequest·PasswordResetRequest 요청 본문(정의하지 않은 필드는 400)
│   ├── AuthErrorCode             인증 오류 코드(UNAUTHENTICATED, INVALID_CREDENTIALS, EMAIL_NOT_VERIFIED, CSRF_INVALID, AUTH_TOKEN_INVALID, RATE_LIMITED)
│   ├── AuthExceptionHandler      auth.web 컨트롤러의 기능별 처리기(@Order 최우선)
│   ├── MemberApiInterceptor      member 업무 API 세션·CSRF 검사, 쿠키 재발급(컨트롤러가 있는 경로만)
│   ├── MemberApiWebConfiguration 위 인터셉터를 /api/v1/me, /api/v1/me/**에 등록, member
│   ├── MemberApiExceptionHandler member 업무 API의 401·403을 인증 API와 같은 응답으로(전역, member)
│   └── AuthenticatedMember       검사를 통과한 요청의 회원 ID(요청 속성), user 패키지가 읽음
├── application/
│   ├── AuthSessionService        세션 발급·조회·만료 판정, 회원 세션 발급(동시 5개)·종료, member 업무 요청 검사(checkMemberRequest), local·demo·member
│   ├── MemberRequestCheck        member 업무 요청 검사 결과(Authenticated·Unauthenticated·CsrfInvalid)
│   ├── MemberEmailVerifiedEvent  이메일 인증 완료 이벤트(장부 생성용)
│   ├── SessionTokens             세션 ID·CSRF 토큰 생성, SHA-256 해시, 상수 시간 비교
│   ├── AnonymousSessionProperties 익명 세션 수명 설정값(미사용 2시간, 절대 상한 없음)
│   ├── AuthConfiguration         설정값 등록, local·demo·member
│   ├── ActiveSession·IssuedSession 결과 타입(toString에서 원문 숨김)
│   ├── MemberRegistrationService 가입·재요청·인증 완료, local·demo·member
│   ├── PasswordResetService      재설정 요청·완료(전체 세션 종료), local·demo·member
│   ├── AuthTokenMailer           1회용 토큰 발급(이전 토큰 삭제·해시 저장)과 커밋 뒤 메일, 가입·재설정이 함께 씀
│   ├── MailRequestThrottle       같은 주소 60초 메일 요청 제한
│   ├── PasswordHasher            Argon2id 해시·비교
│   ├── EmailAddresses·MemberIds  이메일 정규화·검사, 회원 ID 발급
│   ├── AuthMailSender·FakeAuthMailSender·AuthMail 메일 발송 포트, 가짜 발송기, 메일 내용
│   ├── MailRateLimitedException·AuthTokenInvalidException 429·400 예외
│   ├── MemberLoginService        로그인 판정·로그아웃·현재 세션, local·demo·member
│   ├── LoginAttemptThrottle      같은 이메일 15분 5회 실패 잠금
│   └── LoginResult·MemberSession·CurrentMemberSession 결과 타입
└── persistence/
    ├── AuthSessionEntity         auth_sessions 한 행(회원 ID가 있으면 회원 세션)
    ├── AuthSessionRepository     해시로 세션 찾기·회원별 세션 찾기(쓰기 잠금)·회원의 세션 모두 삭제
    ├── LoginThrottleEntity·LoginThrottleRepository auth_login_throttles 한 행, 만들거나 배타 잠금
    ├── LoginFailureEntity·LoginFailureRepository auth_login_failures 한 건, 기간 안 실패 수 세기·지우기
    ├── MemberEntity·MemberRepository members 한 행(비밀번호 변경), 이메일로 찾기(쓰기 잠금)
    ├── AuthTokenEntity·AuthTokenRepository·AuthTokenPurpose auth_tokens 한 행, 해시로 찾기(쓰기 잠금)·회원별 삭제, 목적(이메일 인증·비밀번호 재설정)
    └── MailThrottleEntity·MailThrottleRepository auth_mail_throttles 한 행, 만들거나 배타 잠금
```

소스: [web](../../backend/src/main/java/com/beone/api/auth/web/), [application](../../backend/src/main/java/com/beone/api/auth/application/), [persistence](../../backend/src/main/java/com/beone/api/auth/persistence/), 마이그레이션 [V9](../../backend/src/main/resources/db/migration/V9__auth_sessions.sql)·[V11](../../backend/src/main/resources/db/migration/V11__auth_members.sql)·[V13](../../backend/src/main/resources/db/migration/V13__auth_member_sessions.sql).

## 주요 클래스의 역할과 관계

- [`AuthSessionService`](../../backend/src/main/java/com/beone/api/auth/application/AuthSessionService.java)는 쿠키 값으로 세션을 찾고(`use`), 인증 POST에서는 CSRF 토큰까지 맞을 때만 세션을 갱신하며(`useWithCsrf`, 조회·검사·갱신이 한 트랜잭션이라 틀린 토큰은 서버 만료를 늘리지 않음), 익명 세션을 발급합니다(`issueAnonymous`). 세션은 미사용 만료에 끝나고, 절대 만료가 있으면 그 시각에도 끝납니다. 같은 시각이 되면 이미 만료입니다. 익명 세션은 절대 만료가 없습니다(`absolute_expires_at`이 `NULL`, 2026-11-18 사용자 직접 결정). 회원 세션(`member_id`가 있는 행)은 미사용 7일, 절대 만료 30일입니다. 유효한 세션을 쓸 때마다 미사용 만료를 늘리되 절대 만료가 있으면 넘기지 않고, 이번 사용 시각부터 새 미사용 만료까지를 쿠키 `Max-Age`로 돌려줍니다. 만료한 행은 찾을 때 지웁니다. 조회에 쓰기 잠금(`PESSIMISTIC_WRITE`)을 걸어, 같은 세션을 쓰는 동시 요청이 옛 만료 값으로 판정해 다른 요청이 방금 연장한 세션을 지우지 않게 합니다.
- 같은 서비스가 회원 세션도 다룹니다. `issueMember`는 요청의 이전 세션(익명이든 회원이든)을 지우고, 그 회원의 만료 세션을 지우고, 유효한 세션이 이미 5개면 가장 오래 쓰지 않은 것(마지막 사용 시각, 같으면 먼저 발급한 것)부터 지운 뒤 새 세션을 만듭니다. 로그인 응답 쿠키 `Max-Age`는 사본대로 30일(2592000초)이고, 이후 CSRF를 통과한 POST·`GET /api/v1/auth/csrf`가 서버 미사용 만료까지(최대 7일)로 다시 심습니다. `useMember`는 회원 세션만 찾아 사용 시각을 갱신하며, 익명 세션은 갱신하지 않고 빈 값입니다. `end`는 쿠키의 세션을 지웁니다.
- [`MemberLoginService`](../../backend/src/main/java/com/beone/api/auth/application/MemberLoginService.java)는 로그인 판정 순서를 맡습니다: [`LoginAttemptThrottle`](../../backend/src/main/java/com/beone/api/auth/application/LoginAttemptThrottle.java) 잠금(429, 비밀번호를 보지 않음) → 비밀번호 확인(계정 없음도 시작 때 만든 더미 해시와 Argon2id 비교, 불일치와 같은 401, 둘 다 실패로 셈) → 이메일 인증(403, 비밀번호가 맞은 뒤에만, 실패로 세지 않음) → 실패 기록 삭제 → `issueMember`. 실패 기록이 커밋되도록 실패도 예외가 아니라 [`LoginResult`](../../backend/src/main/java/com/beone/api/auth/application/LoginResult.java) 값으로 돌려주고, 컨트롤러가 오류 응답으로 바꿉니다. 잠금 순서는 로그인 잠금 행 → 회원 행 → 세션 행이고, 로그인 트랜잭션은 READ COMMITTED입니다. MySQL 기본 격리(REPEATABLE READ)에서는 회원별 세션 잠금 조회가 인덱스 간격까지 잠가 세션이 없던 서로 다른 회원의 동시 로그인이 교착했기 때문입니다(동시 시험에서 재현 뒤 수정). 같은 회원의 동시 로그인은 회원 행 잠금으로 차례로 처리돼 상한을 넘지 않습니다. 서로 다른 두 계정의 세션으로 상대 계정에 동시에 로그인하면(계정 전환) 이전 세션 행과 대상 회원의 세션 행을 엇갈려 잠가 MySQL이 한쪽을 교착으로 되돌리므로, 진 로그인은 `TransactionTemplate`으로 새 트랜잭션을 열어 처음부터 다시 판정합니다(최대 3번, 실패 기록도 롤백돼 두 번 세지 않음). 현재 시각은 잠금 상태 행을 잡은 뒤에, 실패 시각은 비밀번호 비교 뒤에 읽어 잠금 대기·Argon2id 계산 시간만큼 판정이 늦거나 잠금이 짧아지지 않게 합니다.
- [`LoginAttemptThrottle`](../../backend/src/main/java/com/beone/api/auth/application/LoginAttemptThrottle.java)은 이메일 SHA-256 해시로 `auth_login_throttles` 행을 만들거나 배타 잠금한 뒤(메일 요청 제한과 같은 방식) 잠금 시각을 봅니다. 실패하면 15분보다 오래된 실패 기록을 지우고 이번 실패를 더해, 15분 안 실패가 5번이면 지금부터 15분 동안 잠그고 실패 기록을 비웁니다. 잠긴 동안의 시도는 세지 않고 잠금을 늘리지 않습니다. 성공하면 실패 기록을 지웁니다.
- [`MemberSessionController`](../../backend/src/main/java/com/beone/api/auth/web/MemberSessionController.java)는 로그인 성공·로그아웃 응답에서 인터셉터가 심은 갱신 쿠키를 `HttpServletResponse.setHeader`로 덮어써 `Set-Cookie`를 하나만 보냅니다. 세션 조회는 쿠키를 다시 심지 않습니다(사본의 응답 헤더 예시대로).
- [`SessionTokens`](../../backend/src/main/java/com/beone/api/auth/application/SessionTokens.java)는 상태가 없는 부품입니다.
  - 세션 ID: `SecureRandom` 32바이트를 패딩 없는 Base64URL로 쓴 43자입니다. DB에는 SHA-256 해시(16진수 64자)만 저장합니다.
  - CSRF 토큰: 세션 ID에 고정 접두어를 붙여 SHA-256으로 유도한 43자입니다. 서버는 토큰을 따로 저장하지 않고, 요청 쿠키에서 다시 계산해 상수 시간(`MessageDigest.isEqual`)으로 비교합니다. 쿠키가 `HttpOnly`라 다른 사이트의 스크립트는 세션 ID를 읽어 토큰을 만들 수 없습니다. 세션 ID가 바뀌면(로그인·로그아웃, U3) 토큰도 바뀝니다.
- [`AuthRequestInterceptor`](../../backend/src/main/java/com/beone/api/auth/web/AuthRequestInterceptor.java)는 핸들러가 정해진 `/api/v1/auth/**` 요청에서 실행됩니다. 그래서 CSRF 실패(403)가 본문 검증(400)보다 먼저 납니다. 없는 경로(404)·지원하지 않는 메서드(405)는 공통 처리를 따릅니다. `Cache-Control: no-store`는 핸들러 조회 전에 도는 서블릿 필터 [`AuthNoStoreFilter`](../../backend/src/main/java/com/beone/api/auth/web/AuthNoStoreFilter.java)가 붙이므로 404·405에도 들어갑니다. 세션 서비스는 `ObjectProvider`로 요청 때 꺼냅니다. 다른 기능의 웹 슬라이스 테스트에 이 설정이 함께 올라와도 세션 서비스 빈이 필요 없게 하려는 것입니다.
- [`AuthExceptionHandler`](../../backend/src/main/java/com/beone/api/auth/web/AuthExceptionHandler.java)는 `auth.web` 패키지의 컨트롤러에만 적용합니다. 인터셉터가 던진 예외도 그 요청의 핸들러 기준으로 이 처리기가 받습니다.
- 요청 진입점은 [`AuthCsrfController`](../../backend/src/main/java/com/beone/api/auth/web/AuthCsrfController.java), 쿠키 문자열은 [`SessionCookie`](../../backend/src/main/java/com/beone/api/auth/web/SessionCookie.java), 인터셉터·필터 등록은 [`AuthWebConfiguration`](../../backend/src/main/java/com/beone/api/auth/web/AuthWebConfiguration.java), 익명 세션 수명은 [`AnonymousSessionProperties`](../../backend/src/main/java/com/beone/api/auth/application/AnonymousSessionProperties.java), 저장은 [`AuthSessionEntity`](../../backend/src/main/java/com/beone/api/auth/persistence/AuthSessionEntity.java)·[`AuthSessionRepository`](../../backend/src/main/java/com/beone/api/auth/persistence/AuthSessionRepository.java)입니다.
- [`MemberRegistrationService`](../../backend/src/main/java/com/beone/api/auth/application/MemberRegistrationService.java)는 가입·인증메일 재요청·이메일 인증 완료를 한 트랜잭션씩 처리합니다. 가입과 재요청은 먼저 [`MailRequestThrottle`](../../backend/src/main/java/com/beone/api/auth/application/MailRequestThrottle.java)로 같은 주소 60초 제한을 통과해야 합니다. 가입은 계정 유무와 관계없이 [`PasswordHasher`](../../backend/src/main/java/com/beone/api/auth/application/PasswordHasher.java)로 해시를 계산하고(응답 시간 차이 줄이기), 신규면 미인증 회원을 만들고, 미인증 기존 계정이면 비밀번호를 바꾸지 않고 인증메일만 다시 보내며, 인증된 계정이면 아무것도 보내지 않습니다. 반환값이 없어 컨트롤러는 항상 같은 202로 답합니다. 토큰 발급은 [`AuthTokenMailer`](../../backend/src/main/java/com/beone/api/auth/application/AuthTokenMailer.java)가 맡습니다. 같은 회원의 같은 목적 토큰을 지우고 새 토큰의 해시만 저장하며, 메일은 토큰 저장이 커밋된 뒤에만 [`AuthMailSender`](../../backend/src/main/java/com/beone/api/auth/application/AuthMailSender.java)로 보냅니다.
- 잠금 순서는 회원 행 → 토큰 행입니다. 재발급(회원을 잠근 뒤 이전 토큰 삭제)과 이메일 인증(회원 ID를 잠금 없이 읽고 회원을 잠근 뒤 토큰을 잠가 다시 확인)이 같은 순서라 동시에 실행돼도 교착하지 않습니다. 회원 이메일 열은 이진 비교(`utf8mb4_bin`)라 `jose@`와 `josé@`는 다른 계정입니다.
- [`PasswordResetService`](../../backend/src/main/java/com/beone/api/auth/application/PasswordResetService.java)는 재설정 요청·완료를 한 트랜잭션씩 처리합니다. 요청은 같은 `MailRequestThrottle`을 통과한 뒤(가입·재요청과 합산), 계정이 있으면 인증 여부와 관계없이 `AuthTokenMailer`로 재설정 토큰(15분)을 보내고(관리자 판단, 결정 28) 반환값이 없어 같은 202가 됩니다. 완료는 토큰 해시로 회원 ID를 잠금 없이 읽고, Argon2id 해시를 회원 행 잠금 전에 계산한 뒤, 회원 행을 잠그고 토큰을 다시 확인합니다(없음·회원 다름·목적 다름·만료면 400). 그다음 비밀번호를 바꾸고 그 회원의 모든 세션(`auth_sessions`의 그 회원 행)과 재설정 토큰을 지웁니다. 로그인도 같은 회원 행을 잠그므로, 동시에 이전 비밀번호로 로그인하면 재설정 전에 끝난 세션은 지워지고 뒤에 온 로그인은 401입니다. 이메일 인증 상태·이메일 인증 토큰·로그인 실패 잠금은 바꾸지 않고 세션을 새로 만들지 않습니다.
- [`MailRequestThrottle`](../../backend/src/main/java/com/beone/api/auth/application/MailRequestThrottle.java)은 이메일의 SHA-256 해시로 `auth_mail_throttles` 행을 만들거나 배타 잠금을 건 뒤(`INSERT ... ON DUPLICATE KEY UPDATE`) 잠금 읽기로 마지막 접수 시각을 봅니다. 60초 안이면 남은 초로 `MailRateLimitedException`을 던지고, 아니면 시각을 바꿉니다. 처음부터 배타 잠금을 쓰므로 같은 주소의 동시 요청은 차례로 판정되고 교착이 없습니다(공유 잠금 뒤 배타 잠금으로 올리던 첫 구현은 동시 시험에서 교착 500이 났습니다).
- [`PasswordHasher`](../../backend/src/main/java/com/beone/api/auth/application/PasswordHasher.java)는 Bouncy Castle의 Argon2 구현으로 m=19456KiB·t=2·p=1, 솔트 16바이트, 해시 32바이트의 PHC 문자열을 만듭니다. `matches`는 저장값의 파라미터로 다시 계산해 상수 시간으로 비교하며 로그인 단위(U3)가 씁니다.
- [`FakeAuthMailSender`](../../backend/src/main/java/com/beone/api/auth/application/FakeAuthMailSender.java)는 메일을 보내지 않고 최근 100통을 메모리에만 담습니다. 로그를 남기지 않고, 테스트가 `sentMails()`로 토큰을 꺼냅니다.
- 요청 본문은 [`MemberEmail`](../../backend/src/main/java/com/beone/api/auth/web/MemberEmail.java)(정규화 뒤 형식·254자)과 [`NewPassword`](../../backend/src/main/java/com/beone/api/auth/web/NewPassword.java)(11~32자, 영문·숫자·ASCII 특수기호 각 1개 이상, 그 밖의 문자 불가)로 검사하고, 정의하지 않은 필드는 [`UnknownFieldRejecting`](../../backend/src/main/java/com/beone/api/auth/web/UnknownFieldRejecting.java)이 400으로 만듭니다. 오류 응답과 `toString`에 비밀번호·토큰 값을 담지 않습니다.
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

POST /api/v1/auth/... (인증 POST 공통)
→ AuthNoStoreFilter: no-store
→ AuthRequestInterceptor: AuthSessionService.useWithCsrf(쿠키, X-CSRF-TOKEN): 조회(쓰기 잠금) → 만료면 삭제 → 토큰 비교 → 맞을 때만 사용 시각 갱신
   → 실패: CsrfInvalidException → AuthExceptionHandler → 403 CSRF_INVALID
   → 통과: 세션 쿠키를 새 Max-Age로 다시 심음 → 본문 검증과 컨트롤러 처리

POST /api/v1/auth/sign-up {email, password}
→ 본문 검증(실패 400 INVALID_REQUEST)
→ MemberRegistrationService.signUp(한 트랜잭션)
   → 이메일 정규화 → MailRequestThrottle.acquire(60초 안이면 429 + Retry-After)
   → PasswordHasher.hash(항상 계산)
   → 회원 조회(쓰기 잠금)
      → 없음: members 행(usr-…, 미인증) → 인증 토큰 발급
      → 미인증: 비밀번호 그대로 → 이전 토큰 삭제 → 새 토큰 발급
      → 인증됨: 아무것도 하지 않음
   → 커밋 뒤 가짜 발송기에 메일(받는 사람·목적·원문 토큰)
→ 202 {"data":{"message":"처리 가능한 요청이면 이메일로 안내합니다."}}

POST /api/v1/auth/email-verification-requests {email}
→ 같은 제한 → 미인증 계정이면 새 토큰 발급·메일, 아니면 없음 → 같은 202

POST /api/v1/auth/login {email, password}   (쿠키·CSRF는 로그인 전 익명 세션)
→ CSRF 검사(403) → 본문 검증(400)
→ MemberLoginService.login(한 트랜잭션, READ COMMITTED, 교착으로 지면 새 트랜잭션에서 최대 3번)
   → 이메일 정규화 → 로그인 잠금 행 배타 잠금 → 현재 시각 읽기 → 잠겨 있으면 429 + Retry-After
   → 회원 조회(쓰기 잠금) → Argon2id 비교(계정 없으면 더미 해시)
      → 계정 없음·불일치: 비교 뒤 시각으로 실패 기록(15분 안 5번째면 15분 잠금) → 401 INVALID_CREDENTIALS
      → 미인증: 403 EMAIL_NOT_VERIFIED(실패로 세지 않음)
      → 성공: 실패 기록 삭제 → 이전 세션 삭제 → 그 회원의 만료 세션 삭제·유효 세션이 이미 5개면 가장 오래 쓰지 않은 세션 삭제 → 새 회원 세션(7일·30일, 회원당 최대 5개 유지)
→ 200 {"data":{"userId","email"}} + Set-Cookie 새 세션(Max-Age=2592000). CSRF 토큰도 바뀌므로 GET /api/v1/auth/csrf를 다시 부른다.

POST /api/v1/auth/logout   → CSRF 검사 → 현재 세션 삭제 → 204 + Set-Cookie(Max-Age=0). 다른 기기 세션은 그대로

GET /api/v1/auth/session   → 회원 세션이면 사용 시각 갱신 → 200 {userId, email, authenticatedAt, expiresAt, absoluteExpiresAt}
                           → 세션 없음·만료·로그아웃·익명 세션은 401 UNAUTHENTICATED

POST /api/v1/auth/email-verifications {token}
→ 토큰 해시로 회원 ID만 읽기(잠금 없음) → 회원 잠금 → 토큰 잠금 조회로 다시 확인
   → 없음·회원 다름·목적 다름·만료(30분)면 400 AUTH_TOKEN_INVALID
→ 회원 인증 시각 기록 → 그 회원의 인증 토큰 모두 삭제 → 204(로그인 세션은 만들지 않음)

POST /api/v1/auth/password-reset-requests {email}
→ 가입·재요청과 같은 60초 제한(429 + Retry-After) → 계정이 있으면(인증 여부 무관) 재설정 토큰 발급·커밋 뒤 메일 → 같은 202

POST /api/v1/auth/password-resets {token, newPassword}
→ CSRF 검사(403) → 본문 검증(400, 새 비밀번호 규칙)
→ PasswordResetService.resetPassword(한 트랜잭션)
   → 토큰 해시로 회원 ID만 읽기(잠금 없음, 없으면 400) → 새 비밀번호 Argon2id 해시 계산
   → 회원 잠금 → 토큰 잠금 조회로 다시 확인(회원 다름·목적 다름·만료(15분)면 400 AUTH_TOKEN_INVALID)
   → 비밀번호 해시 변경 → 그 회원의 세션 모두 삭제 → 그 회원의 재설정 토큰 모두 삭제
→ 204(자동 로그인 없음, 이메일 인증 상태·로그인 실패 잠금 그대로)
```

## 짧은 사용 예

```bash
# 1. 토큰과 익명 세션 쿠키 받기
curl -i http://localhost:8080/api/v1/auth/csrf
# HTTP/1.1 200
# Set-Cookie: __Host-BEONE_SESSION=<세션 ID>; Path=/; Max-Age=7200; Secure; HttpOnly; SameSite=Lax  (기본 idle-timeout PT2H일 때)
# Cache-Control: no-store
# {"data":{"headerName":"X-CSRF-TOKEN","token":"<토큰>"}}

# 2. 이후 인증 POST는 같은 쿠키와 토큰 헤더를 함께 보낸다.
curl -i -X POST http://localhost:8080/api/v1/auth/sign-up \
  -H 'Cookie: __Host-BEONE_SESSION=<세션 ID>' -H 'X-CSRF-TOKEN: <토큰>' \
  -H 'Content-Type: application/json' -d '{"email":"user@example.com","password":"<11~32자 비밀번호>"}'
# HTTP/1.1 202
# {"data":{"message":"처리 가능한 요청이면 이메일로 안내합니다."}}
# 메일은 가짜 발송기 메모리에만 있습니다. 로컬에서 토큰을 확인하는 화면·API는 없고, 테스트가 꺼내 씁니다.

# 3. 이메일 인증을 마친 회원의 로그인. 응답의 새 쿠키로 바꾸고 CSRF 토큰을 다시 받는다.
curl -i -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Cookie: __Host-BEONE_SESSION=<익명 세션 ID>' -H 'X-CSRF-TOKEN: <토큰>' \
  -H 'Content-Type: application/json' -d '{"email":"user@example.com","password":"<비밀번호>"}'
# HTTP/1.1 200
# Set-Cookie: __Host-BEONE_SESSION=<새 세션 ID>; Path=/; Max-Age=2592000; Secure; HttpOnly; SameSite=Lax
# {"data":{"userId":"usr-…","email":"user@example.com"}}
curl -i http://localhost:8080/api/v1/auth/session -H 'Cookie: __Host-BEONE_SESSION=<새 세션 ID>'

# 4. 비밀번호 재설정. 토큰은 가짜 발송기 메모리에만 있어 로컬에서는 테스트가 꺼내 쓴다.
curl -i -X POST http://localhost:8080/api/v1/auth/password-resets \
  -H 'Cookie: __Host-BEONE_SESSION=<세션 ID>' -H 'X-CSRF-TOKEN: <토큰>' \
  -H 'Content-Type: application/json' -d '{"token":"<재설정 토큰>","newPassword":"<11~32자 새 비밀번호>"}'
# HTTP/1.1 204  (그 회원의 기존 세션은 모두 끝남)
```

## 설정

| 키 | 의미 | 기본값 |
| --- | --- | --- |
| `beone.auth.anonymous-session.idle-timeout` | 익명 세션 미사용 만료, 쿠키 `Max-Age` | `PT2H` (2026-11-18 사용자 직접 결정) |

익명 세션에는 발급 기준 절대 상한이 없어 설정 키도 없습니다(2026-11-18 사용자 직접 결정). 미사용 만료는 1초 이상이어야 합니다. 어기면 서버가 시작하지 않습니다. `application*.yml`에는 값을 넣지 않았고, 테스트는 값을 명시해 주입합니다.

## 제약과 현재 구현 범위

- 인증 API는 `local`·`demo`·`member` 프로필 전용입니다. `member`는 로컬 개발·테스트용 회원 프로필이며 공개 배포 프로필이 아닙니다. 공개 프로필 구성은 미결입니다(결정 37 O1).
- `local`·`demo` CORS는 자격 증명을 허용하지 않아, 지금은 `localhost:5173` 화면에서 인증 API로 쿠키를 보낼 수 없습니다. API와 테스트는 동작합니다. 화면 연결 때 CORS를 따로 결정합니다.
- `local`·`demo`에서는 회원 세션을 업무 API의 현재 사용자 결정(`CurrentUserResolver`·시연 헤더)에 연결하지 않습니다. `member`에서는 `MemberApiInterceptor`와 `user.web.MemberCurrentUserResolver`가 회원 세션으로 정합니다(결정 37). 로컬 브라우저 연결은 Vite 프록시(같은 origin)로 하기로 했고 프런트 설정은 팀원 조율 대기입니다(N-FE-PROXY).
- 만료 세션은 조회할 때와 그 회원이 다시 로그인할 때만 지웁니다. 주기적 정리는 없습니다.
- 로그인 실패 제한은 이메일 기준이며 IP 기준은 없습니다. 없는 이메일의 잠금 상태 행(이메일 해시)도 남고, 정리 기한은 메일 제한 기록과 함께 미정입니다. 15분보다 오래된 실패 기록은 같은 이메일의 다음 실패 때 지웁니다.
- 세션 조회는 서버 미사용 만료를 늘리지만 쿠키를 다시 심지 않습니다(사본 응답 헤더대로). 쿠키 `Max-Age`는 로그인 30일이나 마지막 POST·CSRF 조회의 값을 따릅니다.
- 미인증 계정·만료 토큰·메일 제한 기록의 정리 기한은 미정이라 지우지 않습니다. 쓴 토큰과 재발급으로 무효가 된 토큰은 바로 지웁니다.
- 가짜 발송기는 서버 메모리에만 메일을 두므로 로컬에서 실제로 인증을 끝까지 해 보려면 테스트를 써야 합니다. 링크 주소 형식은 미정입니다.
- 재설정 요청 쿠키가 그 회원의 세션이면 그 세션도 지워집니다. 응답에는 다른 인증 POST처럼 갱신 쿠키가 붙지만 서버 세션은 없으므로 다음 요청 전에 `GET /api/v1/auth/csrf`로 새 세션을 받아야 합니다.
- `V11`·`V13`은 임시 번호입니다. `develop`에 CODEF C3의 `V12__codef_syncs.sql`(#129)이 먼저 병합돼 지금 순서는 V9 → V10 → V11 → V12 → V13입니다. 최종 번호는 관리자가 병합 직전에 확인합니다.
- 테스트: `SessionTokensTest`·`AnonymousSessionPropertiesTest`·`AuthSessionServiceTest`·`SessionCookieTest`(단위), `AuthCsrfApiWebTest`·`AuthCsrfProfileOffWebTest`(웹 슬라이스, 테스트 전용 POST 경로로 CSRF 검사), `AuthSessionIntegrationTest`(실제 HTTP·MySQL, V9 적용·해시 저장·만료). U2: `PasswordHasherTest`(Argon2 참조 구현 시험 벡터)·`EmailAddressesTest`·`MemberIdsTest`·`MailRequestThrottleTest`·`NewPasswordTest`(단위), `MemberRegistrationApiWebTest`(웹 슬라이스), `MemberRegistrationIntegrationTest`(실제 HTTP·MySQL, V11 적용·해시만 저장·계정 존재 비공개·토큰 30분·1회용·재발급 무효·60초 합산·동시 가입·인증과 재발급 경합·악센트 주소 구분·로그에 원문 없음). U3: `MemberLoginServiceTest`·`LoginAttemptThrottleTest`와 `AuthSessionServiceTest`·`SessionCookieTest` 추가분(단위), `MemberSessionApiWebTest`와 `AuthCsrfProfileOffWebTest` 추가분(웹 슬라이스), `MemberSessionIntegrationTest`(실제 HTTP·MySQL, V13 적용·로그인 때 세션 ID·CSRF 교체와 이전 세션 폐기(병합 조건 ④)·7일·30일·동시 5개·로그아웃은 현재 세션만·계정 존재 비공개·15분 5회 잠금과 경계·동시 실패·서로 다른 회원 동시 로그인·계정 전환 동시 로그인·로그에 원문 없음). U4: `PasswordResetApiWebTest`와 `AuthCsrfProfileOffWebTest` 추가분(웹 슬라이스), `PasswordResetIntegrationTest`(실제 HTTP·MySQL, 해시만 저장·계정 존재 비공개(미인증 계정에도 발송)·완료 시 그 회원의 모든 세션 종료와 다른 회원 세션 유지·미인증 유지·토큰 15분·1회용(같은 토큰 동시 사용 포함)·재발급 무효·다른 목적 토큰 상호 거부(U2 이월)·60초 합산·로그인 잠금 유지·이전 비밀번호 로그인과의 동시 경합·로그에 원문 없음). G2(#157): `AuthSessionServiceTest` 추가분(업무 요청 검사: 7일 연장·30일 상한·CSRF 실패 시 갱신 없음·401이 403보다 먼저), `MemberRegistrationIntegrationTest` 추가분(인증 완료 때 장부 생성), `MemberApiNoStoreWebTest`(`member` 프로필 웹 슬라이스: 업무 경로 401·403·쿠키 재발급 200·미등록 404의 no-store), `MemberProfileIntegrationTest`(`member` 프로필 실제 HTTP·MySQL: 가입→인증→로그인→`/me`, 회원 A·B 카드 격리와 남의 카드 404, 시연 헤더 무시·시연 포트폴리오 접근 불가, 미인증·익명·만료·로그아웃 401, 업무 요청의 7일 연장·30일 상한·쿠키 재발급, CSRF 403·미저장과 401→403→400→404 순서, 동시 6번째 로그인, 미등록 업무 경로 404, 업무 응답 no-store, 남의 카드와 없는 카드의 404 본문 동일).
