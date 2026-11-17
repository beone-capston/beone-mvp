# #128 회원 인증 U2 회원가입·인증메일 재요청·이메일 인증 완료 API

브랜치 `feat/128-auth-sign-up`, 시작 기준 `origin/develop` `648fa1a`(PR #125 병합).

## 2026-11-18 · `auth` 패키지 확장·Flyway V11(임시 번호) · PR(`develop` 대상, 병합 전)
- 근거: 관리 기록 결정 28·29·30, [D-24](../../decisions.md#d-24-확정-정책), [D-23](../../decisions.md#d-23-확정-정책). 계약은 [인증 API 명세](../../api/auth.md)(Notion 인증 명세 고정 사본 `api-spec-snapshot.md`에서 U1이 옮김)입니다.
- 한 일:
  - `POST /api/v1/auth/sign-up`, `POST /api/v1/auth/email-verification-requests`, `POST /api/v1/auth/email-verifications`. 모두 `local`·`demo` 프로필 전용이고 U1의 CSRF 검사를 그대로 받습니다.
  - Flyway `V11__auth_members.sql`: `members`(회원 ID `usr-`+32자, 정규화 이메일 고유, Argon2id 해시, 인증 시각), `auth_tokens`(토큰 SHA-256 해시 고유, 목적, 30분 만료, 회원 삭제 시 함께 삭제), `auth_mail_throttles`(이메일 SHA-256 해시 키, 마지막 접수 시각). **임시 번호**이며 `V10`(CODEF #127 대기)과의 순서는 병합 직전 관리자가 정합니다.
  - 의존성 `org.bouncycastle:bcprov-jdk18on:1.80` 1개 추가(관리자 승인). Argon2id m=19456KiB·t=2·p=1, 솔트 16바이트, 해시 32바이트, PHC 문자열.
  - 가짜 메일 발송기(`FakeAuthMailSender`): 받는 사람·목적·원문 토큰을 메모리에만 최근 100통 담고 로그 없음. 메일은 토큰 저장 커밋 뒤에만 보냅니다.
  - `user/**`·`/me`·시연 헤더·`common/**`·`application*.yml`·CORS·frontend·android는 바꾸지 않았습니다.
  - 문서: [인증 API 명세](../../api/auth.md)(구현 표, CSRF 현재 동작, 메일 발송, 비밀번호 저장, 세 API의 구현 설명, 관리자 결정 절, 미정), [auth 안내](../../domains/auth.md), [구조 안내](../../architecture.md)의 auth 항목·트리 줄·구현 현황 문장의 인증 부분.
- 관리자 판단(결정 28, 질문 `msg_9f26e82600a1` → 답 `msg_b94e29f1634f`):
  - 메일 링크 주소 형식은 미정. 새 설정값 없이 가짜 발송기 메모리만 씁니다.
  - 회원 ID는 `usr-` + UUID v4 하이픈 뺀 32자(`VARCHAR(36)`).
  - 판정 순서(403 → 400 → 429), 60초 기준(마지막 접수 요청, 거절은 기준을 늦추지 않음, `Retry-After` 올림), 이메일 해시로 제한 기록, 기존 계정 가입도 해시 계산, 재발급·인증 시 이전 토큰 무효를 승인. 정리 기한은 미정 유지.
- 작은 구현 결정:
  - 정의하지 않은 필드는 `card`·`monthlystate`와 같은 방식(`@JsonAnySetter`로 거부)으로 400입니다. 공유 Jackson 설정은 바꾸지 않았습니다.
  - 토큰은 사용·재발급 때 행을 지워 무효로 합니다(만료 토큰은 정리 기한 미정이라 남김). 다른 목적 토큰은 목적 열로 거절하지만, 지금 목적은 이메일 인증 하나뿐이라 다른 목적 토큰 시험은 재설정(U4)에서 합니다.
  - 질문 때는 "동시 가입은 이메일 고유 제약으로 막고 충돌해도 같은 202"라고 적었지만, 실제로는 같은 주소의 메일 요청 제한 행 잠금이 먼저 동시 요청을 차례로 판정해 하나만 202이고 나머지는 429입니다. 고유 제약은 최종 방어로 남습니다. 429는 계정 유무와 관계없이 나므로 계정 존재를 드러내지 않습니다.
  - 메일 요청 제한은 처음에 `INSERT IGNORE` 뒤 조건부 `UPDATE`로 만들었는데, 동시 가입 통합 시험에서 MySQL 교착(공유 잠금 뒤 배타 잠금으로 올리기)으로 500이 났습니다. `INSERT ... ON DUPLICATE KEY UPDATE`로 처음부터 배타 잠금을 잡고 잠금 읽기 뒤 Java에서 판정하도록 바꿨고, 같은 시험을 5번 더 돌려 교착 없이 통과했습니다. 이 때문에 `auth_mail_throttles.last_requested_at`은 같은 트랜잭션이 방금 만든 행에 한해 `NULL`을 허용합니다.
- 검증: `./gradlew test webTest integrationTest build javadoc --rerun-tasks` 결과는 PR 본문에 기록합니다.
- 남은 제한: 로그인·로그아웃·세션 조회(U3, 로그인 교체 시험 ④가 병합 조건), 비밀번호 재설정(U4), 실발송·공급자·링크 형식, 미인증 계정·만료 토큰·제한 기록 정리 기한, 공개 프로필·CORS, Flyway 번호 확정. 서버 DB 적용·배포, frontend 변경은 하지 않았습니다.

## 2026-11-18 · CodeRabbit 리뷰 반영(head `13a8dd0` 리뷰)
- 리뷰 상태: 제출 리뷰 1건(head `13a8dd0`, 인라인 3건, 모두 Minor). 세 지적 모두 현재 코드에 해당해 반영했습니다.
- 반영 1(인증과 재발급의 잠금 순서): 이메일 인증은 토큰 → 회원, 재발급은 회원 → 토큰 순으로 잠가 동시에 실행되면 교착할 수 있었습니다. 인증이 토큰의 회원 ID를 잠금 없이 읽고 회원을 먼저 잠근 뒤 토큰을 잠가 다시 확인하도록 바꿨습니다(`MemberRepository.findLockedById`, `AuthTokenRepository.findMemberIdByTokenHash`). `MemberRegistrationIntegrationTest#verificationRacingReissueNeverFailsWithServerError`(인증과 재발급을 15회 동시 실행)는 고치기 전 교착 500으로 실패했고, 고친 뒤 4번 돌려 모두 통과했습니다.
- 반영 2(이메일 비교 규칙): `members.email`이 기본 `utf8mb4_0900_ai_ci`(악센트 무시)를 받아, `jose@example.com` 가입 뒤 `josé@example.com` 가입이 첫 계정에 인증메일을 다시 보냈습니다. V11에서 `email` 열을 `utf8mb4_bin`으로 지정했습니다(V11은 미병합 자기 파일). 소문자 정규화는 그대로입니다. `#accentedAddressIsADifferentAccount`는 고치기 전 실패, 고친 뒤 통과입니다.
- 반영 3(구조 문서): `docs/architecture.md` auth 항목의 범위 밖 목록에서 가입·인증메일을 빼고 U3·U4만 남겼습니다.
