# #132 회원 인증 U3 로그인·로그아웃·현재 회원 세션 조회 API

브랜치 `feat/132-auth-login`, 시작 기준 `origin/develop` `098cd74`(PR #130 병합).

## 2026-11-18 · `auth` 패키지 확장·Flyway V13(임시 번호) · PR(`develop` 대상, 병합 전)
- 근거: [D-24](../../decisions.md#d-24-확정-정책)(미사용 7일·최대 30일·동시 5개), 2026-11-18 사용자 직접 결정(같은 이메일 15분 안 5번 실패 시 15분 429, IP 기준 없음, 익명 세션 미사용 2시간·절대 상한 없음), 관리 기록 결정 28·29·30. 계약은 [인증 API 명세](../../api/auth.md)(Notion 인증 명세 고정 사본 반영)입니다.
- 6번째 로그인 처리는 사본에 "가장 오래 쓰지 않은 세션을 종료"로 있어 질문 없이 그대로 구현했습니다.
- 한 일:
  - `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`, `GET /api/v1/auth/session`. 모두 `local`·`demo` 프로필 전용이고, 로그인·로그아웃은 U1의 CSRF 검사를 받습니다.
  - Flyway `V13__auth_member_sessions.sql`: `auth_sessions`에 `member_id`(회원 FK, 삭제 시 함께 삭제, 회원 세션은 절대 만료 필수 CHECK, 회원·마지막 사용 인덱스), `auth_login_throttles`(이메일 해시 키, 잠금 시각), `auth_login_failures`(이메일 해시, 실패 시각). **임시 번호**이며 `V12`는 CODEF C3 몫으로 비워 둡니다.
  - 로그인은 성공 때 요청의 이전 세션(익명이든 회원이든)을 지우고 새 세션 ID를 발급합니다(세션 고정 방지). CSRF 토큰은 세션 ID에서 유도되므로 함께 바뀝니다. 응답 쿠키는 `Max-Age=2592000`(사본) 하나입니다.
  - 동시 5개: 새 세션을 만들기 전에 그 회원의 만료 세션을 지우고, 유효한 세션이 5개면 가장 오래 쓰지 않은 세션부터 지웁니다. 회원 행 잠금으로 같은 회원의 동시 로그인을 차례로 처리합니다.
  - 로그인 실패 제한, 계정 없음도 더미 해시와 Argon2id 비교(응답 시간 차이 줄이기), `INVALID_CREDENTIALS`·`EMAIL_NOT_VERIFIED`·`UNAUTHENTICATED`(문구 사본).
  - `/me`·`CurrentUserResolver`·시연 헤더·`user/**`·`common/**`·`application*.yml`·CORS·frontend·android, 익명 세션 규칙은 바꾸지 않았습니다. 새 의존성 없음.
  - 문서: [인증 API 명세](../../api/auth.md)(구현 표, CSRF 현재 동작, 쿠키 갱신, 로그인 실패 제한, 「구현에서 정한 세부 해석(U3)」, 미정), [auth 안내](../../domains/auth.md), [구조 안내](../../architecture.md)의 auth 항목·트리 줄·구현 현황 문장의 인증 부분.
- 병합 조건 ④: `MemberSessionIntegrationTest#loginReplacesSessionIdAndCsrfTokenAndDiscardsAnonymousSession`이 실제 HTTP·MySQL에서 로그인 뒤 새 세션 ID ≠ 익명 세션 ID, 새 CSRF 토큰 ≠ 이전 토큰, 익명 세션 행 삭제, 이전 쿠키·이전 토큰으로 POST하면 403, 회원 세션에서 다시 로그인해도 교체됨을 확인합니다. 시계는 주입한 가변 시계이고 sleep은 없습니다.
- 구현에서 정한 세부 해석(PR 리뷰 포인트): 실패 세기 경계(정확히 15분 전 실패 제외), 비밀번호가 맞은 미인증은 실패로 세지 않음, 잠긴 동안의 시도는 세지 않음, 성공·잠금 때 실패 기록 삭제, 판정 순서 403 → 400 → 429 → 401 → 403, 세션 조회는 서버 미사용 만료만 늘리고 쿠키를 다시 심지 않음.
- 동시성: 처음 구현에서 서로 다른 회원 8명이 동시에 로그인하는 통합 시험이 MySQL 교착(500)으로 실패했습니다. 기본 격리 REPEATABLE READ에서 회원별 세션 잠금 조회(`FOR UPDATE`)가 인덱스 간격까지 잠가, 세션이 없던 회원들의 새 세션 삽입끼리 서로 기다렸기 때문입니다. 로그인 트랜잭션을 READ COMMITTED로 바꿔 간격 잠금을 없앴고(같은 회원은 회원 행 잠금으로 계속 차례 처리), 같은 시험을 3번 더 돌려 교착 없이 통과했습니다. 같은 이메일 동시 실패 7건은 정확히 401 5건·429 2건입니다.
- 검증: `./gradlew test webTest integrationTest build javadoc --rerun-tasks` 결과는 PR 본문에 기록합니다.
- 남은 제한: 비밀번호 재설정(U4, 다른 목적 토큰 시험 포함), 회원 세션과 `/me` 업무 자원의 연결(D-24 미결), IP 기준 제한, 로그인 잠금 상태 행·만료 세션 정리 기한, 공개 프로필·CORS, Flyway 번호 확정. 서버 DB 적용·배포, frontend 변경은 하지 않았습니다.

## 2026-11-18 · CodeRabbit 리뷰 반영(head `03ac995` 리뷰)
- 리뷰 상태: 제출 리뷰 1건(head `03ac995`, 인라인 4건, 모두 Minor, 리뷰 본문의 범위 밖 지적 없음). 네 지적 모두 현재 코드·문서에 해당해 반영했습니다.
- 반영 1(계정 전환 교착): A의 세션으로 B에, B의 세션으로 A에 동시에 로그인하면 이전 세션 행과 대상 회원 세션 행을 엇갈려 잠가 교착 500이 났습니다. `MemberSessionIntegrationTest#crossAccountSwitchesNeverFailWithServerError`(15회 동시 전환)가 고치기 전 첫 회에 500으로 실패했습니다. 로그인 전체를 `TransactionTemplate`(READ COMMITTED) 한 번으로 실행하고, `PessimisticLockingFailureException`(교착·잠금 대기 실패)로 지면 새 트랜잭션에서 처음부터 다시 판정합니다(최대 3번). 진 시도는 실패 기록까지 롤백되므로 두 번 세지 않습니다. 고친 뒤 4번 돌려 모두 통과했고, MySQL은 전환마다 한쪽을 교착으로 되돌리지만(로그 15건) 재시도가 흡수합니다.
- 반영 2(잠금 판정 시각): 잠금 상태 행을 기다리기 전에 읽은 시각으로 판정·기록해, 대기 뒤 잠금이 이미 끝났는데 429를 주거나 잠금 기간이 Argon2id 계산 시간만큼 짧아질 수 있었습니다. `LoginAttemptThrottle.lock`으로 행을 먼저 잡은 뒤 시각을 읽어 `lockedFor`에 넘기고, 실패 시각은 비밀번호 비교 뒤에 읽습니다. `MemberLoginServiceTest#timesAreReadAfterTheThrottleLockAndAfterPasswordComparison`(읽을 때마다 1초 가는 시계)로 확인합니다.
- 반영 3·4(문서): `docs/architecture.md` 사용자 식별 문장의 "로그인은 없고"를 업무 API가 회원 로그인에 연결되지 않았다는 뜻으로, `docs/domains/auth.md` 로그인 흐름의 "5개 넘으면"을 "유효 세션이 이미 5개면"으로 고쳤습니다.

## 2026-11-18 · 최신 `develop` 병합(`876b515`, CODEF C3 #131)
- PR이 충돌 상태라 `origin/develop`을 merge했습니다(rebase 없음). 충돌은 `docs/architecture.md` 구현 현황 문장과 `docs/progress/backend/README.md` 목록으로, 양쪽 내용을 모두 살렸습니다.
- `V12__codef_syncs.sql`이 먼저 들어와 Flyway 순서는 V11 → V12 → V13입니다. 병합 뒤 전체 검증을 다시 했습니다(결과는 PR 본문).

## 2026-11-18 · CodeRabbit 사소한 지적 반영(head `c6d254b` 리뷰 본문의 Nitpick 2건)
- `MemberSessionIntegrationTest#crossAccountSwitchesNeverFailWithServerError`: 상태 코드만 보던 것을, 각 전환 로그인 응답의 `userId`와 새 쿠키로 조회한 세션의 `userId`가 전환 대상 회원인지까지 확인하도록 강화했습니다.
- `MemberLoginServiceTest#timesAreReadAfterTheThrottleLockAndAfterPasswordComparison`: 읽기 횟수로 시간을 구분하던 시계를 가변 시계로 바꾸고, 잠금(`lock`)과 비밀번호 비교(`matches`) 대역이 각각 시간을 흘려보내게 해 시각을 그 뒤에 읽는지 검증합니다.
- 코드 변경 없음(테스트만).

## 2026-11-18 · 최신 `develop` 병합(`94f655a`, 월간 상태 출처 선택 C1 #135)
- PR이 다시 충돌 상태라 `origin/develop`을 merge했습니다(rebase 없음). 충돌은 `docs/architecture.md` 구현 현황 문장과 `docs/progress/backend/README.md` 목록으로, 양쪽 내용을 모두 살렸습니다. 새 Flyway 파일은 없어 순서는 V11 → V12 → V13 그대로입니다.
