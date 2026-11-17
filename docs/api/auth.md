# 회원 인증 API

회원 가입·이메일 인증·로그인·세션·비밀번호 재설정 API 9개입니다([D-24](../decisions.md#d-24-확정-정책)). 공통 응답·오류 형식은 [`docs/api.md`](../api.md)를 따릅니다.

## 출처와 상태

- **출처:** 저장소 밖 Notion API 명세(인증 9개 페이지)의 고정 사본 `api-spec-snapshot.md`입니다. Notion 마지막 편집은 2026-11-11T09:04Z(9개 모두)이고, 사본 수집 시각은 2026-11-16T06:27:03Z입니다(기준 `develop` `5885454`). 2026-11-16T13:31Z 정합화 작업은 인증 페이지를 바꾸지 않고 보류했습니다. 이 문서는 그 사본의 요청·응답·실패 응답을 옮긴 것입니다. 옮긴 뒤 Notion 원문을 직접 다시 대조하지는 않았습니다.
- **사본에 없는 값:** 아래 「미정」 절에 모았습니다. 구현에서 임의로 확정하지 않습니다.
- **프로필:** 인증 API는 `local`·`demo` 프로필에서만 등록합니다(관리자 판단, 결정 30). 공개 프로필의 활성화는 메일 발송 공급자 결정 뒤로 미룹니다. 그 밖의 환경에서는 경로가 없어 404 `NOT_FOUND`입니다.
- **시연 사용자와의 관계:** 인증 API는 `X-BeONE-Demo-User` 헤더를 읽지 않습니다. 기존 `/api/v1/me`·`/api/v1/me/...`와 현재 사용자 결정 지점(`CurrentUserResolver`)은 바꾸지 않았습니다. 회원과 두 포트폴리오 모드의 관계는 D-24 미결이라, 회원으로 로그인해도 `/me` 업무 자원에는 아직 연결되지 않습니다.

| Method | Endpoint | 설명 | 구현 |
| --- | --- | --- | --- |
| GET | `/api/v1/auth/csrf` | CSRF 토큰 조회 | 구현(#124) |
| POST | `/api/v1/auth/sign-up` | 회원가입 | 구현 전 |
| POST | `/api/v1/auth/email-verification-requests` | 이메일 인증메일 재요청 | 구현 전 |
| POST | `/api/v1/auth/email-verifications` | 이메일 인증 완료 | 구현 전 |
| POST | `/api/v1/auth/login` | 회원 로그인 | 구현 전 |
| POST | `/api/v1/auth/logout` | 회원 로그아웃 | 구현 전 |
| GET | `/api/v1/auth/session` | 현재 회원 세션 조회 | 구현 전 |
| POST | `/api/v1/auth/password-reset-requests` | 비밀번호 재설정 요청 | 구현 전 |
| POST | `/api/v1/auth/password-resets` | 비밀번호 재설정 완료 | 구현 전 |

## 공통 사항

- **세션:** MySQL 서버 세션이며 Redis를 쓰지 않습니다. 회원 세션은 미사용 7일과 로그인 뒤 최대 30일 중 먼저 오는 시각에 만료합니다. 한 회원의 동시 세션은 최대 5개입니다(D-24).
- **세션 쿠키:** `__Host-BEONE_SESSION`입니다. `Secure`·`HttpOnly`·`SameSite=Lax`·`Path=/`이고 `Domain`을 지정하지 않습니다(D-24). 세션 ID는 응답 본문에 넣지 않습니다.
- **CSRF:** 모든 인증 POST 요청은 `X-CSRF-TOKEN` 헤더와 같은 세션의 쿠키를 함께 보냅니다. 토큰은 `GET /api/v1/auth/csrf`로 받고, 로그인·로그아웃 뒤에는 다시 받습니다. 쿠키가 없거나 세션이 만료·무효이거나 토큰이 다르면 본문 검증보다 먼저 403 `CSRF_INVALID`입니다. 토큰과 세션 ID를 로그·URL·localStorage에 저장하지 않습니다.
- **응답 헤더:** 모든 인증 API 응답(실패 포함)에 `Cache-Control: no-store`를 붙입니다.
- **세션 쿠키 갱신(사본에 덧붙임):** CSRF 검사를 통과한 인증 POST 응답에도 같은 세션 쿠키를 새 `Max-Age`(서버 미사용 만료까지)로 다시 심는 `Set-Cookie`를 붙입니다. 사본의 POST 응답 헤더 예시에는 없으며, 서버와 브라우저의 세션 수명을 맞추려고 더한 것입니다(2026-11-18 사용자 직접 결정). 응답이 직접 쿠키를 정하는 로그인·로그아웃은 그 응답의 쿠키를 따릅니다(U3). CSRF 실패(403)에는 붙이지 않습니다.
- **계정 존재 비공개:** 가입·인증메일 재요청·재설정 요청은 계정 유무·인증 상태·실제 발송 여부와 관계없이 같은 202 응답을 줍니다. 202는 요청 접수이며 메일 도착을 보장하지 않습니다.
- **메일 요청 제한:** 같은 주소의 메일 요청은 가입·인증메일 재요청·재설정 요청을 합산하고, 계정이 없어도 똑같이 셉니다. 같은 주소의 재발송 간격은 60초입니다. 초과하면 429 `RATE_LIMITED`와 `Retry-After`(남은 초)입니다.
- **메일 발송:** 이번 범위에서는 테스트 격리용 가짜 발송기만 둡니다. 실발송·외부 공급자·발신 주소는 미결입니다.
- **값 검증**
  - `email`: 이메일 형식, 최대 254자입니다. 앞뒤 공백을 지우고 소문자로 통일합니다. 점과 `+태그`는 유지합니다.
  - 새 비밀번호(`password`·`newPassword`): 11~32자입니다. 영문·숫자·ASCII 특수기호를 각각 1개 이상 넣고 공백·한글은 받지 않습니다. 대·소문자 동시 사용은 강제하지 않습니다. 공백 제거·대소문자 변환을 하지 않습니다.
  - 토큰(`token`): 43자 Base64URL 문자열입니다. 이메일 인증 토큰은 30분, 재설정 토큰은 15분 유효하고, 둘 다 1회만 쓸 수 있습니다. 재발급하면 이전 토큰은 무효입니다. 목적이 다른 토큰은 받지 않습니다.
  - 정의하지 않은 필드(확인용 비밀번호·이름·닉네임 등)가 오면 400 `INVALID_REQUEST`입니다.
- **비밀번호 저장:** Argon2id 해시입니다(D-24). 파라미터는 m=19456KiB, t=2, p=1이며 OWASP Password Storage Cheat Sheet의 최소 권장값입니다(관리자 승인, 결정 30). 가입 단위에서 구현합니다.

### 인증 오류 코드

[`docs/api.md`](../api.md#업무-오류-코드)의 「인증」 묶음입니다. 문구는 사본과 같습니다.

| code | 상태 | message | 쓰는 API |
| --- | --- | --- | --- |
| `UNAUTHENTICATED` | 401 | 로그인이 필요합니다. | 세션 조회 |
| `INVALID_CREDENTIALS` | 401 | 이메일 또는 비밀번호를 확인해 주세요. | 로그인 |
| `EMAIL_NOT_VERIFIED` | 403 | 이메일 인증을 완료해 주세요. | 로그인 |
| `CSRF_INVALID` | 403 | 요청을 확인할 수 없습니다. 화면을 새로고침한 후 다시 시도해 주세요. | 모든 인증 POST |
| `AUTH_TOKEN_INVALID` | 400 | 링크가 유효하지 않거나 만료되었습니다. 다시 요청해 주세요. | 이메일 인증 완료, 비밀번호 재설정 완료 |
| `RATE_LIMITED` | 429 | 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요. | 가입, 인증메일 재요청, 재설정 요청, 로그인 |

필수값·형식·허용 필드 오류는 공통 400 `INVALID_REQUEST`(`요청 값을 확인해 주세요.`)입니다. `FORBIDDEN`은 사본의 인증 API에서 쓰지 않습니다.

## GET /api/v1/auth/csrf — CSRF 토큰 조회

익명·로그인 상태 모두에서 CSRF 토큰을 조회합니다. 회원 로그인이 필요 없습니다.

### Request

- 헤더: 세션 쿠키가 있으면 브라우저가 자동으로 보냅니다. 없으면 서버가 새 익명 세션 쿠키를 발급합니다.
- Path·Query·Body: 없음

### Response

성공: **200 OK**

```json
{
  "data": {
    "headerName": "X-CSRF-TOKEN",
    "token": "<CSRF 토큰>"
  }
}
```

- 유효한 세션 쿠키가 있으면 그 세션의 토큰을 주고, 같은 쿠키를 새 `Max-Age`(이번 사용부터 서버 미사용 만료까지, 익명 세션은 7200)로 다시 심습니다. 쿠키가 없거나 만료·무효이면 CSRF용 익명 세션을 만들고 `Set-Cookie: __Host-BEONE_SESSION=<세션 쿠키>; Path=/; Max-Age=7200; Secure; HttpOnly; SameSite=Lax`로 심습니다. 쿠키 수명 갱신은 사본에 덧붙인 것입니다(2026-11-18 사용자 직접 결정).
- 익명 세션은 회원 로그인이 아닙니다. 미사용 2시간에 만료합니다(2026-11-18 사용자 직접 결정).
- 응답 헤더: `Cache-Control: no-store`

### 실패 응답

기능별 실패는 없습니다. 405·500은 공통 처리를 따릅니다.

## POST /api/v1/auth/sign-up — 회원가입

이메일·비밀번호로 가입을 요청합니다. 신규·중복 모두 같은 202 안내입니다.

### Request

```text
Content-Type: application/json
Cookie: __Host-BEONE_SESSION=<세션 쿠키>
X-CSRF-TOKEN: <CSRF 토큰>
```

```json
{
  "email": "user@example.com",
  "password": "<비밀번호>"
}
```

- 필수: `email`, `password`. 선택 필드 없음
- `email`·`password` 규칙은 「공통 사항」의 값 검증을 따릅니다.

### Response

성공: **202 Accepted**

```json
{
  "data": {
    "message": "처리 가능한 요청이면 이메일로 안내합니다."
  }
}
```

- 신규 계정은 이메일 미인증 상태로 만듭니다. 인증 링크는 30분·1회용이고, 인증 뒤 직접 로그인합니다.
- 기존 계정의 비밀번호·인증 상태는 바꾸지 않습니다. 미인증 계정에는 인증메일을 다시 안내하고, 인증된 계정에는 메일을 보내지 않습니다. 응답은 같습니다.
- 응답 헤더: `Cache-Control: no-store`

### 실패 응답

- **400 `INVALID_REQUEST`**: 필수값·형식·허용 필드 오류
- **403 `CSRF_INVALID`**: CSRF 토큰 누락·불일치
- **429 `RATE_LIMITED`**: 요청 횟수 제한. `Retry-After`

## POST /api/v1/auth/email-verification-requests — 이메일 인증메일 재요청

인증메일 재발송을 요청합니다. 계정 존재와 관계없이 202입니다.

### Request

헤더는 가입과 같습니다.

```json
{
  "email": "user@example.com"
}
```

- 필수: `email`. 선택 필드 없음
- 인증 링크는 30분·1회용이고, 재발급하면 이전 링크는 무효입니다. 같은 주소 재발송 간격은 60초입니다.
- 계정 없음·이미 인증됨을 응답으로 구분하지 않습니다.

### Response

성공: **202 Accepted**. 본문은 가입과 같습니다. 응답 헤더 `Cache-Control: no-store`

### 실패 응답

- **400 `INVALID_REQUEST`**, **403 `CSRF_INVALID`**, **429 `RATE_LIMITED`**(`Retry-After`)

## POST /api/v1/auth/email-verifications — 이메일 인증 완료

메일의 1회용 토큰으로 이메일 소유를 확인합니다. 자동 로그인하지 않습니다.

### Request

헤더는 가입과 같습니다.

```json
{
  "token": "<이메일 인증 토큰>"
}
```

- 필수: `token`(43자 Base64URL, 이메일 인증 목적). 선택 필드 없음
- 인증 토큰은 30분 유효하고 1회 사용 뒤 무효입니다.

### Response

성공: **204 No Content**(본문 없음)

- 이메일 인증 상태만 바꿉니다. 비밀번호를 바꾸지 않고 로그인 세션을 만들지 않습니다.
- 메일 링크를 GET으로 열기만 해서는 인증되지 않습니다. 화면에서 확인한 뒤 이 POST를 호출합니다.
- 응답 헤더: `Cache-Control: no-store`

### 실패 응답

- **400 `INVALID_REQUEST`**: 필수값·형식·허용 필드 오류
- **400 `AUTH_TOKEN_INVALID`**: 만료·사용됨·잘못된 목적의 토큰
- **403 `CSRF_INVALID`**

## POST /api/v1/auth/login — 회원 로그인

이메일 인증을 마친 회원이 로그인하고 MySQL 서버 세션을 만듭니다.

### Request

헤더는 가입과 같습니다.

```json
{
  "email": "user@example.com",
  "password": "<비밀번호>"
}
```

- 필수: `email`, `password`. 선택 필드 없음
- `password`: 문자열, 빈 값 불가, 최대 32자. 원문 그대로 검증하며 공백 제거·대소문자 변환을 하지 않습니다.
- 계정 없음과 비밀번호 오류를 구분하지 않습니다. 이메일 미인증 오류는 비밀번호가 맞은 뒤에만 줍니다.

### Response

성공: **200 OK**

```json
{
  "data": {
    "userId": "usr-1001",
    "email": "user@example.com"
  }
}
```

- `userId`: 서버가 발급하는 불투명 String ID(`usr-` 접두어). `email`: 정규화한 이메일
- 세션 ID를 새로 바꿔 MySQL에 저장합니다. 세션 ID는 본문에 넣지 않습니다.
- 동시 로그인이 5개를 넘으면 가장 오래 쓰지 않은 세션을 종료합니다.
- 로그인에 성공하면 CSRF 토큰도 바뀝니다. 다음 POST 전에 `GET /api/v1/auth/csrf`를 다시 호출합니다.
- 응답 헤더: `Set-Cookie: __Host-BEONE_SESSION=<세션 쿠키>; Path=/; Max-Age=2592000; Secure; HttpOnly; SameSite=Lax`, `Cache-Control: no-store`

### 실패 응답

- **400 `INVALID_REQUEST`**: 필수값·형식·허용 필드 오류
- **401 `INVALID_CREDENTIALS`**: 이메일·비밀번호 불일치
- **403 `EMAIL_NOT_VERIFIED`**: 비밀번호는 맞지만 이메일 미인증
- **403 `CSRF_INVALID`**
- **429 `RATE_LIMITED`**: 같은 이메일로 15분 안에 5번 실패하면 15분 동안 막습니다(`Retry-After`, 2026-11-18 사용자 직접 결정, 아래 「사본에 없어 사용자가 정한 값」).

## POST /api/v1/auth/logout — 회원 로그아웃

현재 세션만 종료하고 세션 쿠키를 지웁니다. 다른 기기의 세션은 유지합니다.

### Request

```text
Cookie: __Host-BEONE_SESSION=<세션 쿠키>
X-CSRF-TOKEN: <CSRF 토큰>
```

Body 없음

### Response

성공: **204 No Content**(본문 없음)

- 현재 서버 세션을 즉시 무효화합니다.
- 유효한 CSRF 토큰이 있으면 이미 로그아웃된 상태여도 204입니다. 무효화된 이전 CSRF 토큰을 다시 쓰면 403입니다.
- 다시 로그인하려면 먼저 `GET /api/v1/auth/csrf`로 새 토큰을 받습니다.
- 응답 헤더: `Set-Cookie: __Host-BEONE_SESSION=; Path=/; Max-Age=0; Secure; HttpOnly; SameSite=Lax`, `Cache-Control: no-store`

### 실패 응답

- **403 `CSRF_INVALID`**

## GET /api/v1/auth/session — 현재 회원 세션 조회

로그인한 회원과 세션 만료 시각을 조회합니다. 기존 시연 `/api/v1/me`는 바꾸지 않습니다.

### Request

```text
Cookie: __Host-BEONE_SESSION=<세션 쿠키>
```

Path·Query·Body 없음

### Response

성공: **200 OK**

```json
{
  "data": {
    "userId": "usr-1001",
    "email": "user@example.com",
    "authenticatedAt": "2026-11-01T10:00:00+09:00",
    "expiresAt": "2026-11-08T10:00:00+09:00",
    "absoluteExpiresAt": "2026-12-01T10:00:00+09:00"
  }
}
```

- `authenticatedAt`: 로그인 시각
- `expiresAt`: 이번 활동 뒤 미사용 7일과 절대 만료 중 빠른 시각
- `absoluteExpiresAt`: 로그인 뒤 30일
- 익명 CSRF 세션은 회원 세션이 아닙니다. 세션 없음·만료·로그아웃·다른 로그인으로 종료된 세션은 모두 401입니다.
- 시연 사용자 헤더를 쓰지 않습니다.
- 응답 헤더: `Cache-Control: no-store`

### 실패 응답

- **401 `UNAUTHENTICATED`**: 세션 없음·만료·무효화

## POST /api/v1/auth/password-reset-requests — 비밀번호 재설정 요청

재설정 메일 발송을 요청합니다. 계정 존재와 관계없이 202입니다.

### Request

헤더는 가입과 같습니다.

```json
{
  "email": "user@example.com"
}
```

- 필수: `email`. 선택 필드 없음
- 재설정 링크는 15분·1회용이고, 재발급하면 이전 링크는 무효입니다. 같은 주소 재발송 간격은 60초입니다.
- 계정 존재를 응답으로 구분하지 않습니다.

### Response

성공: **202 Accepted**. 본문은 가입과 같습니다. 응답 헤더 `Cache-Control: no-store`

### 실패 응답

- **400 `INVALID_REQUEST`**, **403 `CSRF_INVALID`**, **429 `RATE_LIMITED`**(`Retry-After`)

## POST /api/v1/auth/password-resets — 비밀번호 재설정 완료

토큰으로 비밀번호를 바꾸고 그 회원의 모든 기존 세션을 종료합니다.

### Request

헤더는 가입과 같습니다.

```json
{
  "token": "<비밀번호 재설정 토큰>",
  "newPassword": "<새 비밀번호>"
}
```

- 필수: `token`(43자 Base64URL, 비밀번호 재설정 목적), `newPassword`. 선택 필드 없음
- 재설정 토큰은 15분 유효하고 1회 사용 뒤 무효입니다.
- `newPassword` 규칙은 「공통 사항」의 새 비밀번호 규칙을 따릅니다.

### Response

성공: **204 No Content**(본문 없음)

- 비밀번호를 바꾸고(Argon2id) 그 회원의 모든 기존 세션과 재설정 토큰을 원자적으로 무효화합니다. 동시 로그인 경쟁에서도 이전 비밀번호로 만든 세션을 남기지 않습니다.
- 이메일 미인증 상태를 인증 완료로 바꾸지 않습니다. 자동 로그인하지 않습니다.
- 응답 헤더: `Cache-Control: no-store`

### 실패 응답

- **400 `INVALID_REQUEST`**: 필수값·형식·허용 필드 오류
- **400 `AUTH_TOKEN_INVALID`**: 만료·사용됨·잘못된 목적의 토큰
- **403 `CSRF_INVALID`**

## 사본에 없어 사용자가 정한 값

2026-11-18 사용자 직접 결정(Aside 질문)입니다. 로컬·테스트 구현에만 적용합니다.

- **익명 CSRF 세션:** 미사용 2시간에 만료하고, 발급 시점부터의 절대 상한은 두지 않으며, 회원 동시 세션 5개 계산에 넣지 않습니다(모두 사용자 직접 결정. 절대 상한은 미결이었다가 2026-11-18 사용자가 직접 '없음'으로 확정했습니다. 앞서 기록했다가 철회된 '절대 2시간'과는 다른 결정입니다). 유효하게 쓸 때마다(GET csrf, CSRF를 통과한 인증 POST) 서버의 마지막 사용·미사용 만료와 쿠키 `Max-Age=7200`을 함께 갱신합니다. 세션이 끝나면 `GET /api/v1/auth/csrf`로 다시 받습니다. 설정값은 `beone.auth.anonymous-session.idle-timeout`(기본 `PT2H`)입니다. 회원 세션의 미사용 7일·최대 30일·동시 5개는 그대로이고, 로그인에 성공하면 익명 세션을 폐기하고 새 회원 세션을 발급합니다(세션 고정 방지, U3).
- **로그인 실패 제한:** 같은 이메일로 15분 안에 5번 실패하면 15분 동안 429 `RATE_LIMITED`로 막습니다. 기준은 이메일이고 IP 기준은 없습니다. 이메일 존재 여부를 응답으로 구분하지 않습니다. 로그인 단위(U3)에서 구현합니다.

## 미정

사본에 없거나 저장소 결정이 없는 값입니다. 구현에서 임의로 확정하지 않습니다.

- **IP 기준 추가 제한과 프록시(`X-Forwarded-For`) 정책:** 넣지 않았습니다. 공개 배포 전에 따로 점검합니다.
- **메일 발신 주소·공급자·실발송:** 미결입니다. 가짜 발송기만 둡니다.
- **공개 환경 CORS와 화면 연결:** `local`·`demo` CORS([D-23](../decisions.md#d-23-확정-정책) 11항)는 자격 증명을 허용하지 않고 `X-CSRF-TOKEN` 헤더도 허용 목록에 없습니다. 따라서 지금은 `localhost:5173` 화면에서 인증 API로 쿠키를 보낼 수 없습니다. CORS 변경은 화면 연결 때 따로 결정합니다.
- **회원과 포트폴리오 모드의 관계, 공개 회원의 CODEF 연결 범위:** D-24 미결입니다.
- **만료 세션 행 정리:** 만료 세션은 조회할 때만 지웁니다. 주기적 정리는 정하지 않았습니다.
- **후속 범위(D-24):** 탈퇴, 이메일 변경, 로그인 상태의 비밀번호 변경.
