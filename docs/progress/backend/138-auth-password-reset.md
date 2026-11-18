# #138 회원 인증 U4 비밀번호 재설정 요청·완료 API

브랜치 `feat/138-auth-password-reset`, 시작 기준 `origin/develop` `8a02ea8`(PR #133 병합).

## 2026-11-18 · `auth` 패키지 확장(새 Flyway 없음) · PR(`develop` 대상, 병합 전)
- 근거: [D-24](../../decisions.md#d-24-확정-정책)(재설정 완료 시 그 회원의 전체 세션 종료), 관리 기록 결정 28·29·30. 계약은 [인증 API 명세](../../api/auth.md)(Notion 인증 명세 고정 사본 반영)입니다.
- 사본에 없는 동작 2가지는 구현 전에 질문했고(`msg_671c09f61ffc`), 관리자 판단(결정 28, `msg_d2a3bbe284cc`)으로 정했습니다. 사용자 보고 대상입니다.
  - 미인증 계정의 재설정 요청: 계정이 있으면 인증 여부와 관계없이 메일을 보냅니다. 완료해도 미인증 상태는 그대로입니다.
  - 로그인 실패 잠금: 재설정은 잠금과 실패 기록을 건드리지 않습니다.
- 한 일:
  - `POST /api/v1/auth/password-reset-requests`(같은 202), `POST /api/v1/auth/password-resets`(204). 모두 `local`·`demo` 프로필 전용이고 U1의 CSRF 검사를 받습니다.
  - 재설정 토큰: 43자 Base64URL, 15분·1회용, 재발급 시 이전 재설정 토큰 삭제. `auth_tokens`에 목적 `PASSWORD_RESET`으로 SHA-256 해시만 저장합니다. `purpose`가 문자열 열이라 Flyway 파일은 추가하지 않았습니다(`V14` 없음).
  - 메일 60초 제한은 U2의 `MailRequestThrottle`을 그대로 써 가입·인증메일 재요청과 합산합니다. 계정이 없어도 똑같이 셉니다.
  - 완료: 회원 행을 잠근 한 트랜잭션에서 Argon2id 새 해시로 바꾸고 그 회원의 모든 세션과 재설정 토큰을 지웁니다. 해시 계산은 잠금 전에 해 같은 회원의 로그인 대기를 줄입니다. 이메일 인증 상태·인증 토큰·로그인 잠금은 그대로이고 자동 로그인하지 않습니다.
  - U2의 토큰 발급·커밋 뒤 메일 발송을 `AuthTokenMailer`로 옮겨 가입과 재설정이 함께 씁니다(동작 변경 없음, U2 테스트 그대로 통과).
- 테스트: `PasswordResetApiWebTest`(22), `AuthCsrfProfileOffWebTest` 추가 1, `PasswordResetIntegrationTest`(15, 리뷰 반영 뒤 16). 다른 목적 토큰 상호 거부(U2 이월)와 이전 비밀번호 로그인·재설정 동시 경합(10회)을 포함합니다.
- 남은 것: 메일 링크 형식·실발송(미정), 회원 세션과 `/me` 연결(D-24 미결), 로그인 상태의 비밀번호 변경·탈퇴·이메일 변경(D-24 후속), 만료 토큰·제한 기록 정리 기한(미정).

## 2026-11-18 · CodeRabbit 반영(head `9684b88`)과 최신 `develop` 병합(`d90f3c3`, F1 #137)
- Minor: `docs/architecture.md` auth 항목의 "범위 밖"에 남은 비밀번호 재설정(U4)을 지웠습니다.
- Nitpick: 같은 재설정 토큰을 서로 다른 세션·서로 다른 새 비밀번호로 동시에 보내는 통합 테스트를 더했습니다. 정확히 하나만 204, 나머지는 400 `AUTH_TOKEN_INVALID`이고 저장된 비밀번호는 성공한 요청의 값입니다.
- PR이 충돌 상태라 `origin/develop`을 merge했습니다(rebase 없음). 충돌은 `docs/progress/backend/README.md` 목록뿐이고 양쪽을 살렸습니다. 새 Flyway 파일은 없습니다.
