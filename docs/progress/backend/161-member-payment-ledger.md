# #161 member 프로필에 모의 결제·원장 등록 (G3c)

브랜치 `feat/161-member-payment-ledger`, 시작 기준 `origin/feat/160-member-order-recommendation` `a2a326a`(G3b). #158·G3a·G3b가 `develop`에 병합되기 전이라 PR은 열지 않았습니다. 병합 뒤 관리자 지시에 따라 `origin/develop`을 merge하고 `develop` 대상 PR을 엽니다.

## 2026-11-24 · `payment`·`ledger` 컨트롤러 프로필, `simulateFailure` 400, 테스트·문서

- 근거: 결정 36·37, 결정 32 Q4, G3 명세 패키지 1-3절(`simulateFailure`), 3절(멱등성), 4-2절(세션 만료 다단계), 5절, 6절 3번(PR-G3c, endpoint #12~15).
- 한 일
  - `SimulatedPaymentController`·`LedgerTransactionController`의 `@Profile`에 `member`를 더했습니다. 서비스·DB·Flyway는 바꾸지 않았습니다.
  - `simulateFailure`: `SimulateFailurePolicy`(새 클래스)가 `local`·`demo` 밖에서 `true`를 받으면 서비스 호출 전에 400 `INVALID_REQUEST`로 거절합니다. 결제·멱등 키를 저장하지 않고, `false`·생략은 정상 처리합니다. `local`·`demo`의 `FAILED`(201)는 그대로입니다.
    - **N-G3-2 사용자 확인 대기 중인 문언 기준 구현**입니다. 근거는 `docs/api/mock-payments.md` 요청 본문의 "`local`·`demo` 프로필에서만 받고 그 밖에는 400 `INVALID_REQUEST`"와 실패 목록, D-23 4항입니다. 사용자가 `member`도 허용하기로 정하면 정책 객체의 프로필 조건과 테스트 기대값만 바뀝니다.
    - `true`만 거부하고 `false`는 받습니다. 계약은 필드 존재와 `true` 중 무엇을 거부 기준으로 할지 적지 않았고, 지문 정규화가 생략과 `false`를 같게 보므로 패키지 1-3절 권장대로 `true`만 거부했습니다.
  - 세션 401·쓰기 CSRF 403·`Cache-Control: no-store`·검사 순서는 #158의 `/api/v1/me/**` 인터셉터·필터가 처리합니다. 경로별 설정은 더하지 않았습니다.
  - 결정 32 Q4 '대표 카드 변경 미감지' 한계와 CODEF 원장 미연결(R8)은 바꾸지 않았습니다. 새 비교 정책은 없습니다. CODEF·`/demo-users`는 `member`에 등록하지 않았습니다(D9). C1은 건드리지 않았습니다.
- 테스트
  - `SimulateFailurePolicyTest`(단위 4): `local`·`demo` 허용, `member`·프로필 없음은 `true`만 거부.
  - `MemberApiTestClient`: 정리 대상에 `idempotency_keys`를 더하고(결제·취소·원장은 주문·카드 삭제의 외래 키 연쇄 삭제), 추가 헤더(`Idempotency-Key`·시연 헤더)를 붙이는 `sendWithHeaders`를 더했습니다.
  - `MemberPaymentLedgerIntegrationTest`(9, `member` 프로필): `MemberProfileIntegrationTest`와 같은 프로필·가변 시계 조합이라 새 통합 컨텍스트가 없습니다.
    - 승인·원장·상세, 같은 키·같은 본문 재생(201, 같은 본문, 결제·원장·키 1건), 같은 키·다른 본문 409 `IDEMPOTENCY_KEY_REUSED`, 다른 키 409 `ORDER_ALREADY_PAID`, 승인 뒤 추천 계산 409 `ORDER_ALREADY_PAID`, 본 추천 `ORDER_PAID`
    - 회원 A·B가 같은 키로 각자 승인(독립), 원장 분리, 남의 것·없는 것·형식 오류의 404 본문 동일(결제 상세·취소, 승인 주문, `seenRecommendationId`, 원장 카드 필터)
    - 24시간 안 같은 키 재생, 24시간 뒤 승인은 `ORDER_ALREADY_PAID`(이중 결제 없음)이고 부분 취소는 다시 실행, 전액 취소 뒤 주문 `CANCELLED`·`PAYMENT_ALREADY_CANCELLED`
    - `simulateFailure: true` 400·미저장(없는 주문에도 400, 400이 404보다 먼저), 같은 키 `false`는 201
    - 무효가 된 추천(`MONTHLY_STATE_CHANGED`)으로 승인 201, 재계산 ID가 본 추천과 다름. Q4: 대표 카드 고정에서 대표 카드만 바꾸면 본 추천은 유효, 승인 카드는 새 대표 카드, `changed=false`·`differences=[]`
    - 추천 → 승인 사이 미사용 7일 만료: 승인·취소 401, 결제·원장·키 0건, 추천은 남음. 재로그인 뒤 이전 CSRF 토큰 403·미저장, 새 토큰으로 같은 키 처리. 승인 뒤 로그아웃 → 재로그인 → 같은 키 재생
    - 4개 경로 쿠키 없음·익명 세션 401(401이 403·400보다 먼저)
    - 승인·취소 CSRF 없음·틀림 403과 미저장(403이 키 400·본문 400·404보다 먼저), 거절된 키 재사용 가능
    - 시연 장부 주문·결제·카드 404, 회원 원장에 시연 원장 미노출, 시연 헤더를 붙인 승인도 회원 장부에 기록, CODEF 출처 행 없음
  - `MemberProfileIntegrationTest`: 미등록 경로 목록에서 모의 승인·원장을 뺐습니다. CODEF 4개·`/demo-users`는 남겼습니다.
  - 현재 동작 고정(계약 문언이 아닌 코드 조합에서 나온 기대값, 패키지 5-3절): 같은 키 재생의 응답 상태 201, 24시간 뒤 같은 키 부분 취소의 재실행.
  - 기대값은 `docs/api/mock-payments.md`·`ledger.md`·`recommendations.md`·`docs/api.md` 중복 방지, D-23, 결정 32·37에서 가져왔습니다.
  - 첫 실행에서 모두 통과해 고친 기대값은 없습니다.
- 검증: `./gradlew test webTest` 통과(단위 1395, 웹 393). 첫 시도는 `MigrationNamingTest` 1건이 실패했는데, 작업 도구가 마이그레이션 폴더에 만든 무시 대상 상태 폴더(`.omc`) 때문이었고 코드와 무관합니다. 폴더를 지운 뒤 통과했습니다. `./gradlew integrationTest` 단독 실행: 181 통과(실패 0), 컨테이너 메모리 합 최고 3,960MiB(한도 4,760MiB, 시작 전 배포 컨테이너 4개 약 2,120MiB). 배포 컨테이너 4개는 그대로 유지했습니다.
- 문서: `docs/api.md` 회원 세션 절, `docs/api/README.md`, `docs/api/auth.md`, `mock-payments.md`·`ledger.md` 프로필 문장, `recommendations.md`의 G3c 문장, 도메인 `payment.md`·`ledger.md`(+ `auth.md`·`user.md`·`recommendation.md`의 낡은 범위 문장), `docs/architecture.md`의 `member` 범위 문장(#157 이후 낡은 "보유 카드 API만"), `docs/decisions.md` 결정 37 구현 범위. D8 미결 표기는 바꾸지 않았습니다(N-G3-3 대기).
- 남은 것
  - N-G3-2(`member`의 `simulateFailure` 허용 여부) 사용자 확인. 결과가 다르면 정책 객체와 테스트 기대값을 고칩니다.
  - N-G3-3(D8 표기), N-G3-4(Q4 재검토 시점)
  - #158·G3a·G3b 병합 뒤 `origin/develop` merge와 PR
