# #116 모의 승인·조회·취소 API

브랜치 `feat/116-simulated-payments-api`, 시작 기준 `origin/develop` `40761ee`.

## 2026-11-17 · `payment`·`ledger`(쓰기) 패키지 · PR(`develop` 대상, 병합 전)
- 근거: 관리 기록 결정 25(업무 API 1차, B3), 결정 28 보충, 결정 29, 관리자 답변(분할 승인, 취소 영향 기준, recommendation 공개 메서드 1개 허용), [`docs/api/mock-payments.md`](../../api/mock-payments.md), [`docs/api/ledger.md`](../../api/ledger.md), [업무 API 공통 규약](../../api.md#업무-api-공통-규약)의 중복 방지, D-09·D-16·D-17·D-18·D-19·D-23.
- 한 일:
  - `payment`(`web`/`application`/`persistence`): 승인 `POST /api/v1/me/orders/{orderId}/simulated-payments`(201), 조회 `GET /api/v1/me/simulated-payments/{simulatedPaymentId}`, 취소 `POST /api/v1/me/simulated-payments/{simulatedPaymentId}/cancellations`(201). 두 POST에 `Idempotency-Key`(사용자별 24시간). `local`·`demo`에서만 등록.
  - `ledger`: 쓰기 쪽만(`LedgerWriter`). 자체 모의 원장 승인·취소 행과 승인 카드의 한도 버킷 차감액 저장. 조회 API 없음.
  - `order` 최소 변경: `OrderStatus`에 `PAID`·`CANCELLED`, 주문 `simulatedPaymentId` 저장·응답, `OrderService.lockForPayment`·`markPaid`·`markCancelled`(`MANDATORY`, 행 잠금).
  - `recommendation` 변경은 공개 메서드 `calculateForApproval` 하나(`RecommendationCalculation` 반환)뿐입니다. `calculate` 동작은 그대로이며 단위 테스트로 확인했습니다.
  - Flyway `V8__simulated_payments_ledger.sql`(임시 번호): `orders.simulated_payment_id` 열, `simulated_payments`, `payment_cancellations`, `ledger_transactions`, `ledger_usage_effects`, `idempotency_keys`. 시드 없음.
  - 테스트: 단위(payment 7개 클래스, ledger 1개, order 1개, recommendation 2건 추가), 웹 2개 클래스(`controllers` 한정)와 주문 상세 1건, 통합 `SimulatedPaymentIntegrationTest`. 기대값은 계약 예시·평가 자료 C01·결정에서 먼저 옮겼습니다.
  - 문서: [payment 안내](../../domains/payment.md)·[ledger 안내](../../domains/ledger.md)(신규), [order 안내](../../domains/order.md) 상태 전이, [recommendation 안내](../../domains/recommendation.md) 추가 메서드, [구조 안내](../../architecture.md)의 구현 현황과 `payment`·`ledger` 줄.
- 서비스에서 달라진 점: 시연 화면에서 추천을 본 뒤 모의 승인을 누르면 승인 직전 재계산 결과로 카드가 정해지고, 본 추천과 달라졌는지가 함께 보입니다. 같은 버튼을 두 번 눌러도 한 번만 승인됩니다. 부분·전액 취소를 할 수 있고, 취소가 실적·혜택·한도에 미치는 영향은 약관으로 확정되는 것만 금액으로, 나머지는 `확인 필요`로 보입니다.
- 기록하고 진행한 선택(관리자 승인, 계약 문서는 바꾸지 않음): [payment 안내의 결정과 근거](../../domains/payment.md#결정과-근거). 요지는 ID 형식(`pay-`·`cn-`·`lt-`), 성공 응답만 멱등 저장·키 255자, 승인은 주문 행·취소는 결제 행 잠금, 대표 카드 고정의 `selectionBasis = null`, `FAILED` 응답 모양, 원장 행 날짜 기준, 취소 영향은 `RECEIPT_MONTH` 확정 카드의 실적만 `confirmed`.
- 한계(후속 작업 몫):
  - 스냅샷 뒤 승인·취소 합산과 월간 상태 `afterSnapshot` 실제 산출(B2f, monthlystate 담당). [monthlystate 안내](../../domains/monthlystate.md)는 이 작업에서 고치지 않았습니다.
  - 주문 `latestRecommendationId`, 보유 카드 `invalidatedRecommendationIds`(B2f).
  - 원장 조회 API, CODEF 실제 거래 행.
  - 선택형 서비스 차감액은 `null`(엔진 범위 밖, D-18 K1). 혜택 되돌림·한도 복원은 약관 근거가 없어 항상 `확인 필요`.
- 검증: `./gradlew test webTest integrationTest build javadoc --rerun-tasks` 결과는 PR 본문에 기록합니다.
- 로컬·테스트 DB만 사용했고 서버 DB 적용·배포는 하지 않았습니다. frontend 변경 없음.
