# #165 member 프로필에 원장 조회 등록 (G3c 분리)

브랜치 `feat/165-member-ledger`, 시작 기준 `origin/feat/160-member-order-recommendation` `722dd08`(G3b). G3a PR #162와 G3b가 `develop`에 병합되기 전이라 PR은 열지 않았습니다. 병합 뒤 관리자 지시에 따라 PR을 엽니다.

## 2026-11-24 · `ledger` 조회 컨트롤러 프로필, 테스트·문서

- 배경: 사용자 지시(2026-11-24)로 G3c를 나눴습니다. 모의 결제 승인·조회·취소는 `SimulatedPaymentController`를 `member`에 등록하는 순간 `simulateFailure` 처리(N-G3-2)를 골라야 하고, 멱등 키 저장 단위 표기(D8)도 이 묶음에 있어 보류합니다. 그 작업은 `feat/161-member-payment-ledger`(`a0bd32e`)에 수정 없이 보존합니다. 원장 조회(GET만)는 이 미결과 무관해 따로 진행합니다.
- 한 일
  - `LedgerTransactionController`의 `@Profile`에만 `member`를 더했습니다. 서비스·DB·Flyway는 바꾸지 않았습니다.
  - 세션 401·`Cache-Control: no-store`·검사 순서는 #158의 `/api/v1/me/**` 인터셉터·필터가 처리합니다. 조회라 CSRF 검사 대상이 아닙니다.
  - `SimulatedPaymentController`는 등록하지 않았습니다. `simulateFailure` 정책과 D8 표기는 건드리지 않았습니다.
- 테스트
  - `MemberLedgerIntegrationTest`(5, `member` 프로필): `MemberProfileIntegrationTest`와 같은 프로필·가변 시계 조합이라 새 통합 컨텍스트가 없습니다. 결제 API가 `member`에 없으므로 원장 행은 `LedgerTransactionRepository`에 직접 넣는 테스트 픽스처로 만듭니다(출처 `SIMULATION`, 모의 결제 번호 없음).
    - 회원 A·B 원장 분리(행·완전성), 다른 달 행 제외, `source=CODEF`는 행 없음·`complete=false`(R8 미연결 유지)
    - B가 A의 카드·없는 카드·형식 오류 카드로 거르면 404 `USER_CARD_NOT_FOUND` 본문 동일, A의 원장 불변
    - 시연 장부 카드·원장 미노출, 시연 헤더를 붙여도 회원 장부로 조회, 시연 원장 행 수 불변
    - 쿠키 없음·익명 세션(토큰 유무) 401·no-store, 401이 `month` 누락 400보다 먼저, 시연 헤더만으로는 401
    - 인증 뒤 `month` 누락·형식 오류 400·no-store, CSRF 토큰 없이 조회 200
  - `MemberProfileIntegrationTest`: 미등록 경로 목록에서 원장을 뺐습니다. 모의 결제 경로는 승인에 더해 조회(`GET /simulated-payments/pay-1`)·취소(`POST …/cancellations`)도 넣어 결제 묶음 3개가 모두 404인지 확인합니다. CODEF 4개·`/demo-users`는 그대로입니다.
  - `LedgerTransactionProfileOffWebTest`: 설명 문장만 고쳤습니다(동작 동일).
  - 기대값은 `docs/api/ledger.md`·`cards.md`·`docs/api.md`, 결정 22 R8·결정 32 Q5·결정 37에서 가져왔습니다. `feat/161`의 원장 시험 항목을 참고했고 결제·멱등·`simulateFailure` 부분은 가져오지 않았습니다.
- 검증: `./gradlew test webTest` 통과(단위 1395, 웹 393). `./gradlew integrationTest` 단독 실행 177 통과(`MemberLedgerIntegrationTest` 5 포함), 컨테이너 메모리 합 최고 3,974MiB(시작 전 배포 컨테이너 4개 약 2,134MiB, 상한 4,760MiB 미도달). 배포 컨테이너 4개는 그대로 유지했습니다.
- 문서: `docs/api/ledger.md` 프로필 문단, 도메인 `ledger.md`, `docs/api.md` 회원 세션 절, `docs/api/README.md`, `docs/decisions.md` 결정 37 구현 범위, `docs/api/auth.md`·도메인 `auth.md`·`user.md`의 낡은 범위 문장. 결제 묶음은 N-G3-2·D8 결정 대기로 미등록이라고 적었습니다.
- 남은 것
  - 모의 결제 묶음(N-G3-2·D8 결정 뒤 `feat/161` 재개)
  - #162·G3b 병합 뒤 관리자 지시로 PR
