# #111 결제 전 설정 조회·변경 API와 대표 카드 보호

브랜치 `feat/111-payment-preference-api`, 시작 기준 `origin/develop` `9eb8596`(PR #109 병합).

## 2026-11-17 · `preference` 패키지·Flyway V6(임시 번호) · PR(`develop` 대상, 병합 전)
- 근거: 관리 기록 결정 25(업무 API 1차 A4, 작업선 B 대기 중 수행)·결정 27 A2·A3절·결정 28(관리자 판단), [D-12](../../decisions.md#d-10d-12-확정-정책), [D-15](../../decisions.md#d-15d-16d-17-확정-정책), [D-19](../../decisions.md#d-19-확정-정책), [D-23](../../decisions.md#d-23-확정-정책), [`docs/api/preferences.md`](../../api/preferences.md), [`docs/api/cards.md`](../../api/cards.md)의 `REPRESENTATIVE_CARD_IN_USE`·대표 카드 해제 문장.
- 한 일:
  - `backend/src/main/java/com/beone/api/preference/`(`web`/`application`/`persistence`): `GET`·`PUT /api/v1/me/payment-preference`. 컨트롤러는 `CurrentUser` 인자만 받고 `local`·`demo` 프로필에서만 등록합니다. `USER_CARD_NOT_FOUND`·`REPRESENTATIVE_CARD_NOT_ELIGIBLE`은 기능별 처리기로 냅니다(`common.web` 변경 없음).
  - 대표 카드 보호: `PaymentPreferenceCardGuard`가 card의 `RepresentativeCardGuard`를 구현합니다. 고정 모드 대표 카드의 추천 제외·해제는 409 `REPRESENTATIVE_CARD_IN_USE`, 자동 추천 모드 대표 카드 해제는 대표 카드를 비웁니다.
  - card 최소 연결(관리자 승인 4곳): `RepresentativeCardInUseException` 추가, `UserCardErrorCode` 1줄, `UserCardExceptionHandler` 처리 메서드 1개, `UserCardApiWebTest` 409 사례 2개(기존 단언 불변). `UserCardService`는 이미 구현 빈을 `ObjectProvider`로 찾으므로 바꾸지 않았습니다.
  - Flyway `V6__payment_preferences.sql`: 사용자당 한 행(기본 키 `user_id`), 대표 카드 외래 키, 모드 값·고정 모드 대표 카드 필수 `CHECK`. 시드 없음. **임시 번호**이며 다른 작업선의 `V5`(A3)와 함께 병합 직전 관리자가 순서를 정합니다.
  - 테스트: 단위 `PaymentPreferenceServiceTest`·`PaymentPreferenceCardGuardTest`, 웹 `PaymentPreferenceApiWebTest`·`PaymentPreferenceProfileOffWebTest`·`UserCardApiWebTest` 2건, 통합 `PaymentPreferenceIntegrationTest`. 기대값은 계약 문서에서 먼저 옮겼습니다.
  - 문서: [preference 안내](../../domains/preference.md) 신규, [card 안내](../../domains/card.md)의 대표 카드 확장 지점 문장을 구현됨으로 정정, [구조 안내](../../architecture.md)의 구현 현황 문장과 `preference` 줄.
- 서비스에서 달라진 점: `local`·`demo`에서 두 시연 사용자 모두 자동 추천 / 대표 카드 고정과 대표 카드를 저장·조회할 수 있습니다. 내 카드 사용자는 고정 대표 카드를 추천에서 빼거나 해제할 수 없습니다. 추천·모의 승인은 아직 이 설정을 쓰지 않습니다.
- 관리자 판단으로 정한 것(결정 28, 2026-11-17, 질문 `msg_d45d5c133ce9` → 답 `msg_18963a110c59`):
  - card 연결은 위 4곳만 바꿉니다. preference가 `UserCardController`를 지목하는 처리기를 두는 대안은 F0 방식과 어긋나 쓰지 않습니다.
  - 자동 추천 모드의 대표 카드를 추천에서 빼면 막지도 비우지도 않고 저장을 유지합니다. 추천(B2)은 추천 비교 대상이 아닌 대표 카드를 동률 3단계에서 무시해야 합니다(D-15).
  - 미설정 조회 = `AUTO_RECOMMEND`·`null`·`null`(계약 응답 필드 문장), 적격 = 보유 카드 `recommendationEligibility.eligible`(`USER_CONFIRMED` 또는 `SYNTHETIC`, 추천 제외 아님), 형식 틀린 `uc-` 값 404, 검사 순서 400 → 404 → 409, 잠금으로 직렬화.
- 작은 구현 결정:
  - 대표 카드 필드 누락과 `null`을 구분하려고 요청 DTO를 레코드 대신 setter로 받아 보냈는지를 기록합니다. 문자열 필드에 숫자·불리언을 보내면 400 `errors: []`입니다.
  - 잠금은 설정 행이 아니라 항상 있는 사용자 행(`users`)에 겁니다. 없는 설정 행을 잠그면 MySQL 간격 잠금끼리 처음 저장하는 요청이 교착될 수 있기 때문입니다. 저장은 `INSERT ... ON DUPLICATE KEY UPDATE`입니다.
  - 자동 추천 모드 대표 카드를 해제로 비우면 `updatedAt`도 해제 시각으로 바꿉니다.
  - 시각은 `Clock` 빈이 하나 있으면 그것을, 없으면 시스템 시계를 쓰고 Asia/Seoul 초 단위로 저장합니다(card와 같은 방식).
- 검증: `./gradlew test webTest integrationTest build javadoc --rerun-tasks` 결과는 PR 본문에 기록합니다.
- 남은 제한: 추천·모의 승인의 설정 사용(B2·B3), 추천 비교 대상이 아닌 대표 카드 무시(B2), 가상 포트폴리오 추천은 월간 상태 입력 전까지 대부분 `확인 필요`(결정 27 A3), Flyway 번호 확정. 서버 DB 적용·배포, frontend 변경은 하지 않았습니다.

## 2026-11-17 · CodeRabbit 리뷰 반영(head `b816327` 리뷰)
- 리뷰 상태: 요약 코멘트 coverage `b816327`, "No actionable comments", check 통과. 인라인·제출 리뷰 없음. 사람 승인 없음.
- 반영: 요약의 보안 구조 검토(Medium·reliability)가 지적한 잠금 순서 순환을 고쳤습니다. 설정 변경이 사용자 행을 먼저 잡고 저장의 외래 키 검사로 대표 카드 행 공유 잠금을 기다리는 동안, 같은 카드를 해제하는 요청은 카드 행을 잡고 사용자 행을 기다려 교착이 됐습니다. 설정 변경도 대표 카드 행을 먼저 잠그도록(카드 행 → 사용자 행) 바꿔 보유 카드 변경과 순서를 맞췄습니다(`PaymentPreferenceRepository.lockUserCard`).
- 확인: `PaymentPreferenceIntegrationTest#firstSaveWaitsForCardMutationLockOrderWithoutDeadlock`이 카드 변경과 같은 순서로 잠그는 트랜잭션과 설정 저장을 겹칩니다. 옛 순서로 되돌려 돌리면 `Deadlock found when trying to get lock`으로 실패하고, 고친 순서에서는 200으로 끝나는 것을 확인했습니다(되돌린 코드는 커밋하지 않음).
- 비반영: 교착 재시도 정책은 두지 않았습니다. 잠금 순서를 하나로 맞춰 교착 원인을 없앴기 때문입니다.

## 2026-11-17 · CodeRabbit 리뷰 반영(head `1d2e570` 리뷰)
- 리뷰 상태: 요약 coverage `1d2e570`, check 통과, 제출 리뷰 1건(인라인 1건, Minor). 사람 승인 없음.
- 반영: 잠금 순서 통합 테스트가 `Thread.sleep(1000)`으로만 순서를 맞춰, 요청이 늦게 시작되면 잘못된 잠금 순서도 통과할 수 있다는 지적. 설정 저장 요청이 `user_cards` 행 잠금을 실제로 기다리는지 `performance_schema.data_lock_waits`로 최대 10초 확인한 뒤 사용자 행을 잠그도록 바꿨고, 확인하지 못하면 실패합니다. 잠금 대기 표는 전역 권한이 필요해 테스트 컨테이너의 root 계정으로 읽습니다.
- 확인: 옛 잠금 순서로 되돌려 돌리면 이 테스트가 `Deadlock found`로 실패하고, 고친 순서에서는 통과합니다(되돌린 코드는 커밋하지 않음).
- 2026-11-17 14:55경 관리 측 노트북 잠자기·네트워크 끊김으로 작업이 멈췄다가 같은 worktree에서 재개했습니다. 끊김 전 검증 결과는 쓰지 않고 전체 명령을 다시 실행했습니다.

## 2026-11-17 · CodeRabbit 리뷰 반영(head `0045609` 리뷰)
- 리뷰 상태: 요약 coverage `0045609`, "No actionable comments", check 통과, 비차단 의견. 사람 승인 없음.
- 반영(같은 지적 2회째): 스레드 답글이 잠금 대기 확인을 테이블 이름만이 아니라 테스트 세션·카드 행으로 좁히라고 했습니다. `data_lock_waits`의 차단 스레드가 테스트 트랜잭션 연결(`CONNECTION_ID()`)이고 대기 잠금이 그 카드 번호(`LOCK_DATA`)일 때만 진행하도록 바꿨습니다. 옛 잠금 순서에서는 여전히 `Deadlock found`로 실패합니다(되돌린 코드는 커밋하지 않음).

## 2026-11-17 · 최신 `develop` merge(A3 #110 → PR #113 병합 `6446bc7`)
- `origin/develop`을 merge했습니다(rebase 아님). 충돌은 `docs/architecture.md`의 구현 현황 문장·패키지 구조 문장·패키지 트리 두 줄이었고, `monthlystate`(#110)와 `preference`(#111) 내용을 모두 남겼습니다. `docs/progress/backend/README.md`는 자동 병합으로 두 줄이 모두 남았습니다.
- Flyway: `develop`에 `V5__monthly_states.sql`이 들어와 `V6__payment_preferences.sql`을 그대로 둡니다(관리자 확인).
- 통합 테스트 정리 순서: `monthly_states`가 `user_cards`를 참조하게 됐지만, `MonthlyStateIntegrationTest`는 테스트 앞뒤로 `monthly_states`를 먼저 지우고 이 PR의 테스트는 월간 상태를 만들지 않으며 앞뒤로 `payment_preferences`를 먼저 지웁니다. 서로의 `user_cards` 정리를 막는 행이 남지 않아 바꾸지 않았습니다.
