# payment 도메인 안내

## 목적과 범위

`payment`는 서비스 안에서만 일어나는 **모의 승인**(실제 카드 결제가 아닌 가짜 승인)과 **모의 취소**를 처리하는 업무 API 패키지입니다(#116). 실제 카드사·PG 호출, 카드번호·승인번호 같은 실물 결제 정보는 없습니다. 계약은 [`docs/api/mock-payments.md`](../api/mock-payments.md)와 [업무 API 공통 규약의 중복 방지](../api.md)입니다.

구현된 범위:

- `POST /api/v1/me/orders/{orderId}/simulated-payments`(201): 승인 직전에 추천을 다시 계산하고, 그 결과로 카드를 정해 승인합니다. 성공하면 자체 모의 원장에 승인 행을 남기고 주문을 `PAID`로 바꿉니다. 본문의 `simulateFailure: true`는 `FAILED` 결제(201)를 만들고, 원장·주문은 바꾸지 않습니다([D-23](../decisions.md#d-23-확정-정책) 4항).
- `GET /api/v1/me/simulated-payments/{simulatedPaymentId}`: 승인 내용, 현재 상태, 남은 취소 가능 금액, 취소 목록.
- `POST /api/v1/me/simulated-payments/{simulatedPaymentId}/cancellations`(201): 부분취소·전액취소. 원장에 음수 취소 행을 남기고, 실적·혜택·한도에 미치는 영향을 `확정`과 `확인 필요`로 나눠 돌려줍니다([D-17](../decisions.md)).
- 두 POST의 `Idempotency-Key` 헤더(필수). 같은 키·같은 요청은 24시간 동안 처음 결과를 그대로 돌려줍니다. 키는 장부(`user_id`)마다 저장합니다. 회원 환경에서는 회원 1명이 '내 카드' 장부 1개를 가지므로 장부 단위가 곧 회원 단위입니다(결정 38-2).
- `local`·`demo`와 로컬 회원 프로필 `member`에서 등록합니다(`member`는 G3c #161). 그 밖에서는 경로가 없어 404입니다. `member`에서는 회원 세션의 '내 카드' 장부가 현재 사용자이고, 세션 401·쓰기 CSRF 403·`Cache-Control: no-store`는 `/api/v1/me/**` 공통 인터셉터·필터가 컨트롤러 전에 처리합니다([docs/api.md 회원 세션](../api.md#회원-세션member-프로필), 결정 37). 소유 확인과 멱등 키가 모두 `user_id` 기준이라 회원 장부에도 그대로 적용되고 서비스 코드는 바꾸지 않았습니다.
- `simulateFailure: true`는 `local`·`demo`에서만 받아 201 `FAILED`로 기록합니다. 그 밖(`member`)에서는 `SimulateFailurePolicy`가 서비스 호출 전에 400 `INVALID_REQUEST`로 거절하므로 주문·결제·원장·멱등 키를 저장하지 않습니다. `false`와 생략은 어느 프로필에서나 정상입니다(결정 38-1).
- 회원 화면은 승인 후 결과만 보여 주는 흐름을 유지합니다(결정 38-4). 승인 API가 추천을 다시 계산해 카드를 정하므로 승인 전 추천 호출은 필요 없습니다. `seenRecommendationId`는 선택이며, 화면이 보내지 않으면 비교 없이 승인하고 `recalculation.changed=null`입니다.
- Flyway `V8__simulated_payments_ledger.sql`(임시 번호, 병합 순서에 따라 바뀔 수 있음, D-23 12항).

실패 응답(계약 목록 그대로):

| 상황 | 상태 | 코드 |
|---|---|---|
| 주문이 없거나 다른 사용자의 주문 | 404 | `ORDER_NOT_FOUND` |
| `seenRecommendationId`의 추천이 없음 | 404 | `RECOMMENDATION_NOT_FOUND` |
| 주문이 이미 승인됨(`CREATED`가 아님) | 409 | `ORDER_ALREADY_PAID` |
| 자동 추천인데 고를 카드가 없음 | 409 | `RECOMMENDATION_NOT_DETERMINED` |
| 대표 카드 고정인데 대표 카드를 쓸 수 없음 | 409 | `REPRESENTATIVE_CARD_NOT_ELIGIBLE` |
| 결제가 없거나 형식이 틀렸거나 다른 사용자의 결제 | 404 | `SIMULATED_PAYMENT_NOT_FOUND` |
| 취소 금액 > 남은 취소 가능 금액 | 409 | `CANCELLATION_AMOUNT_EXCEEDED` |
| 이미 전액 취소됨 | 409 | `PAYMENT_ALREADY_CANCELLED` |
| 같은 키를 다른 요청에 다시 씀 | 409 | `IDEMPOTENCY_KEY_REUSED` |
| 키 없음·빈 값·255자 초과, 시연 사용자 헤더 오류, 본문 형식 오류, `local`·`demo` 밖의 `simulateFailure: true` | 400 | `INVALID_REQUEST` |

## 핵심 용어

- **모의 결제(`SimulatedPayment`)**: 승인 시도 한 건. 상태는 `APPROVED`·`FAILED`·`PARTIALLY_CANCELLED`·`CANCELLED`입니다. ID는 `pay-`+번호입니다.
- **재계산(`recalculation`)**: 승인 직전에 추천을 새로 계산한 결과와, 사용자가 화면에서 본 추천(`seenRecommendationId`)의 비교. 본 추천이 없으면 `changed`는 `null`입니다.
- **취소 가능 금액(`cancellableAmountWon`)**: 승인 금액 − 취소 합계. `FAILED`는 0입니다.
- **취소 영향(`impact`)**: 취소가 카드의 실적(`PERFORMANCE`)·혜택(`BENEFIT`)·한도 사용액(`LIMIT_USAGE`)에 미치는 영향. 약관으로 확정되는 것만 `confirmed`에 넣고, 나머지는 금액 없이 `needsConfirmation`에 이유와 함께 넣습니다. 모르는 값을 0원으로 쓰지 않습니다.
- **멱등 키(`Idempotency-Key`)**: 같은 요청이 두 번 와도 한 번만 처리하게 하는 키. 사용자별로 24시간 보관합니다.
- **지문(fingerprint)**: 사용자 ID·메서드·경로·본문(해석한 값으로 다시 만든 JSON)의 SHA-256. 같은 키로 지문이 다른 요청이 오면 409입니다.

## 패키지 구조

```text
payment
├── web
│   ├── SimulatedPaymentController        # API 3개, Idempotency-Key 검사, CurrentUser 인자만 받음
│   ├── SimulateFailurePolicy             # simulateFailure를 local·demo에서만 받음(그 밖 true는 400)
│   ├── ApprovalRequest / CancellationRequest
│   ├── Strict*Deserializer               # 문자열 숫자·소수·문자열 불리언을 400으로
│   ├── SimulatedPaymentErrorCode
│   └── SimulatedPaymentExceptionHandler  # 기능별 처리기(@Order 최우선)
├── application
│   ├── SimulatedPaymentService           # 승인·조회·취소 흐름
│   ├── CardChoice                        # 재계산 결과 → 승인 카드·혜택·근거
│   ├── RecalculationComparison           # 본 추천 ↔ 재계산 차이
│   ├── CancellationImpacts               # 취소 영향 분류
│   ├── IdempotencyStore / Fingerprint    # 멱등 키 보관·대조
│   ├── ApprovalDocument / PaymentDetail / CancellationDocument  # 응답 본문
│   └── PaymentIds, PaymentStatus, ApprovalCommand, 예외 7개
└── persistence
    ├── SimulatedPaymentEntity / Repository
    ├── PaymentCancellationEntity / Repository
    └── IdempotencyKeyEntity / Repository
```

주요 파일: [SimulatedPaymentService](../../backend/src/main/java/com/beone/api/payment/application/SimulatedPaymentService.java), [CardChoice](../../backend/src/main/java/com/beone/api/payment/application/CardChoice.java), [CancellationImpacts](../../backend/src/main/java/com/beone/api/payment/application/CancellationImpacts.java), [IdempotencyStore](../../backend/src/main/java/com/beone/api/payment/application/IdempotencyStore.java), [V8 마이그레이션](../../backend/src/main/resources/db/migration/V8__simulated_payments_ledger.sql).

## 다른 패키지와의 관계

| 대상 | 공개 부품 | 하는 일 |
|---|---|---|
| 주문 | `order`의 `OrderService.lockForPayment`·`markPaid`·`markCancelled` | 주문 행 잠금, 상태 전이(`CREATED → PAID → CANCELLED`) |
| 추천 | `recommendation`의 `RecommendationService.get`·`calculateForApproval` | 본 추천 조회, 승인 직전 재계산(엔진 차감액 포함) |
| 보유 카드 | `card`의 `UserCardService.list` | `mappingNote` |
| 월간 상태 | `monthlystate`의 `MonthlyStateService.get` | 취소 때 승인 월 스냅샷 시각 확인(읽기만) |
| 규칙 | `benefit.rule`의 `RuleCatalog.select`(읽기만) | 카드의 취소 귀속 규칙(`cancellationAttribution`) |
| 원장 | `ledger`의 `LedgerWriter` | 승인·취소 행 기록, 차감액 저장·조회([ledger 안내](ledger.md)) |

엔진(`benefit/**`)·`common`·`user`·`card`·`monthlystate`·`preference` 코드는 바꾸지 않았습니다. `order`에는 상태 전이 최소 변경만, `recommendation`에는 `calculateForApproval` 하나만 더했습니다.

## 처리 흐름

### 승인(POST)

```text
키 검사(컨트롤러) → 주문 행 잠금 → (주문이 CREATED면) 보유 카드 행 전부 잠금(ID 오름차순, #122) → 지문 계산 → 같은 키·같은 지문이면 저장한 응답 반환
→ 주문이 CREATED가 아니면 409 → (seenRecommendationId가 있으면) 본 추천 조회
→ calculateForApproval(재계산) → CardChoice로 승인 카드 결정 → 재계산 비교
→ 결제 행 저장 → [성공] 원장 승인 행 + 승인 카드의 버킷 차감액 기록, 주문 PAID
→ 응답 본문을 결제 행과 멱등 키에 저장
```

승인 요청 예시(`local`·`demo`의 시연 헤더 방식. `member`에서는 헤더 대신 회원 세션 쿠키와 `X-CSRF-TOKEN`을 보냅니다). 주문·추천 시드는 없으므로 `ord-5001`·`rec-3001`은 계약 예시 값입니다. 먼저 `POST /api/v1/me/orders`로 주문을 만들고 받은 ID로 바꿔 보냅니다. 본문은 생략하거나 `{}`로 보내도 되며, 그러면 본 추천 비교 없이 승인합니다(`changed=null`). 아래 예시처럼 `seenRecommendationId`를 보내는 것은 이미 계산한 추천과 비교하고 싶을 때만입니다.

```bash
curl -X POST "http://localhost:8080/api/v1/me/orders/ord-5001/simulated-payments" \
  -H "X-BeONE-Demo-User: MY_CARDS" \
  -H "Idempotency-Key: 6f1c2a9e-3b7d-4c10-9e55-2d4b8a7f0c31" \
  -H "Content-Type: application/json" \
  -d '{"seenRecommendationId":"rec-3001"}'
```

- 자동 추천: 재계산의 `selected` 카드를 씁니다. 없으면 409 `RECOMMENDATION_NOT_DETERMINED`.
- 대표 카드 고정: 비교 카드에 있으면 그 혜택으로, 제외 이유가 `NEEDS_CONFIRMATION`·`UNSUPPORTED`·`RULE_UNAVAILABLE`이면 혜택 `확인 필요`로 승인합니다(D-19). 매핑 미확인·지원 규칙 없음·추천 제외·대표 카드 없음은 409 `REPRESENTATIVE_CARD_NOT_ELIGIBLE`.
- 같은 주문의 동시 승인은 주문 행 잠금으로 줄을 세웁니다. 뒤 요청은 앞 요청이 바꾼 상태를 읽고 409를 받습니다.

### 동시성과 잠금 순서(#122)

- **문제**: 승인이 주문 행만 잠그면 같은 사용자의 서로 다른 주문은 서로 기다리지 않습니다. 두 승인이 커밋 전의 같은 원장을 읽으면 둘 다 같은 잔여 한도로 차감액을 정할 수 있습니다(PR #121 리뷰 지적).
- **방법**: 승인은 주문이 `CREATED`이면 주문 행을 잠근 직후, 재계산 전에 이 사용자의 해제하지 않은 보유 카드 행 전부를 ID 오름차순으로 쓰기 잠금합니다([`UserCardService.lockHeldCardsForPayment`](../../backend/src/main/java/com/beone/api/card/application/UserCardService.java), 호출자 트랜잭션 필수). 재계산은 이 카드들을 모두 비교하고 각 카드의 스냅샷 뒤 원장 차감액을 읽으므로(#120) 비교 대상 카드 전부를 잠급니다. 잠금은 승인 트랜잭션이 끝날 때 풀립니다.
- **잠금 순서**: 승인은 주문 행 → 보유 카드 행(ID 오름차순), 취소는 결제 행 → 원장 INSERT(외래 키 검사로 카드 행 공유 잠금) → (전액 취소면) 주문 행입니다. 승인이 카드를 잠그는 주문은 `CREATED`뿐이고 취소가 잠그는 주문은 이미 승인된 주문이라 서로 반대 순서로 같은 행을 기다리지 않습니다.
- **이미 승인된 주문은 카드를 잠그지 않습니다(PR #123 리뷰).** 같은 키 재생과 다른 키 409는 재계산하지 않아 카드 잠금이 필요 없습니다. 잠그면 그 주문의 전액 취소(카드 공유 잠금을 쥔 채 주문 행을 기다림)와 재생(주문 행을 쥔 채 카드를 기다림)이 서로 기다립니다. 카드 행은 한 번의 잠금 조회로 사용자 인덱스 순서(ID 오름차순)대로 잠그므로 같은 사용자의 승인끼리도 교착하지 않습니다.
- **읽기 시점**: MySQL 기본 격리 수준(REPEATABLE READ)은 트랜잭션의 첫 일반 읽기에서 읽기 시점을 정합니다. 주문·카드 잠금은 잠금 읽기라 읽기 시점을 정하지 않고, 첫 일반 읽기(멱등 키 조회)보다 앞에 둡니다. 그래서 카드 잠금을 기다린 뒤의 재계산은 앞 승인이 커밋한 원장을 읽습니다.
- **취소는 카드를 잠그지 않습니다.** 취소는 다른 결제의 원장을 읽어 금액을 정하지 않고(원 승인의 차감액과 월간 상태 시각만 읽음), 동시에 진행된 승인과의 결과는 "승인 뒤 취소"를 차례로 처리한 것과 같습니다. 취소 뒤 승인은 #120 규칙으로 그 버킷을 확인 필요로 봅니다.
- 같은 사용자의 승인은 카드가 겹치지 않아도 차례로 처리됩니다. 시연 규모에서는 대기 시간이 짧아 그대로 둡니다.

### 취소(POST)

```text
키 검사 → 결제 행 잠금(없으면 404) → 지문·재생 → CANCELLED면 409 → 금액 > 남은 금액이면 409
→ 원장 취소 행(음수, 원 승인 행 연결) → 취소 행 저장 → 결제 상태 갱신(전액이면 주문 CANCELLED)
→ 취소 영향 계산 → 응답 저장
```

취소 영향 규칙(관리자 답변과 D-16·D-17):

- `PERFORMANCE`: 카드 규칙의 취소 귀속이 `RECEIPT_MONTH`로 확정됐고, 승인 월 월간 상태에 스냅샷이 없거나 승인이 스냅샷보다 뒤일 때만 `confirmed`(취소 월, `deltaWon = -취소 금액`). 귀속 미확정·규칙 없음이면 승인 월(다른 달이면 취소 월도) `확인 필요`입니다. 승인이 스냅샷과 같거나 앞이면(스냅샷에 이미 들어 있는지 모름) 또는 카드를 해제해 월간 상태를 읽지 못하면 취소 월 `확인 필요`입니다.
- `BENEFIT`: 4종 규칙 모두 혜택 되돌림 조항이 없어 항상 `확인 필요`입니다. 승인 혜택이 확정 0원이면 넣지 않습니다.
- `LIMIT_USAGE`: 원장에 저장한 버킷 차감액이 0이 아닌 버킷마다 `확인 필요`입니다(한도 복원 시점 근거 미확정).

## 결정과 근거

결정 28 보충(계약에서 분명한 선택은 기록하고 진행)과 관리자 답변(msg_3010b29ea82d)으로 정했습니다. 계약 문서는 바꾸지 않았습니다.

- ID는 `pay-`·`cn-`·`lt-`+번호(결정 27 규칙). 형식이 틀리면 404.
- 멱등 키는 성공(201) 응답만 저장합니다. 실패 응답은 저장하지 않아 고친 뒤 같은 키로 다시 보낼 수 있습니다. 키 최대 길이는 255자입니다.
- 지문의 본문은 해석한 값으로 다시 만든 JSON입니다. `simulateFailure` 생략은 `false`와 같고, 본문 없음은 `{}`와 같습니다.
- 대표 카드 고정 승인의 `selectionBasis`는 `null`입니다(자동 추천의 선택 근거가 아님).
- `FAILED`의 `approvedAt`은 실패 시각, 혜택은 `{amountWon: null, status: NEEDS_CONFIRMATION}`, `ledgerTransactionId`는 `null`, 취소 가능 금액은 0입니다. `FAILED` 취소는 409 `CANCELLATION_AMOUNT_EXCEEDED`입니다.
- 원장 승인 행의 거래일·시각은 주문일·주문 시각, 취소 행은 취소한 날짜·시각 없음(계약 예시와 같음). 모의 행의 귀속 상태는 `CONFIRMED`입니다.
- 재계산 비교 필드는 `selected.userCardId`와 `selected.currentOrderBenefit.amountWon`, 차이의 `reason`은 본 추천의 무효화 이유(없으면 `null`)입니다. 계약이 `changed`를 "선택 카드·혜택이 달라졌는지"로 정의하므로 대표 카드 고정 모드에서도 `selected` 기준입니다. 그래서 고정 모드에서 대표 카드가 `selected`가 아니면, 대표 카드(승인 카드)의 혜택이 바뀌어도 `selected` 카드와 그 혜택이 그대로인 한 `changed`는 `false`입니다. 대표 카드가 곧 `selected`이면 혜택 변화가 차이로 잡힙니다(계약 문구 정리 후보).
- 주문의 `simulatedPaymentId`는 문자열로 저장해 `order`가 `payment`에 의존하지 않습니다.

## 제약과 한계

- **스냅샷 뒤 합산은 이 패키지 밖에서 합니다(#120).** 이 패키지가 남긴 원장 행·버킷 차감액을 monthlystate가 `afterSnapshot` 요약에, recommendation이 추천 입력의 버킷 사용액에 씁니다. payment 코드는 바뀌지 않았습니다.
- **주문 `latestRecommendationId`·보유 카드 `invalidatedRecommendationIds`는 #120에서 연결했습니다.** 승인 직전 재계산으로 만든 추천도 주문의 마지막 추천이 됩니다([order 안내](order.md)).
- **선택형 서비스 차감액은 엔진 범위 밖이라 `null`로 저장됩니다**(D-18 K1). 그 버킷의 취소 영향은 `확인 필요`입니다.
- 09174는 취소 귀속이 약관에서 확정되지 않아 실적 영향이 항상 `확인 필요`입니다.
- 멱등 키 행은 24시간이 지나면 다음 같은 키 요청 때 지웁니다. 주기적 정리 작업은 없습니다.
- 실제 결제·환불, CODEF 승인내역 연결은 없습니다.
- 회원 가상 장부(결정 37 D1b)는 구현하지 않았습니다. 결정 38-3으로 회원 MVP는 '내 카드'만 제공하고, 회원 가상 장부를 도입하면 멱등 키 저장 단위(결정 38-2)를 다시 검토합니다.
- 승인 전 추천 화면은 만들지 않습니다(결정 38-4). 프런트의 `member` 호출 대응은 팀원 조율 대상입니다.

## 테스트

- 단위(#122): `SimulatedPaymentServiceTest`에 잠금 순서(주문 행 → 보유 카드 행 → 멱등 키 조회 → 재계산), 이미 승인된 주문의 재생·409와 취소가 카드를 잠그지 않음을 더했습니다.
- 단위: `CardChoiceTest`, `RecalculationComparisonTest`, `CancellationImpactsTest`, `PaymentIdsTest`, `FingerprintTest`, `IdempotencyStoreTest`, `SimulatedPaymentServiceTest`(실제 4종 규칙 파일, 저장소 대역). 기대값은 계약 예시(`pay-7001`·`cn-8001`·`lt-9001`·`lt-9002`, 10,000원 중 4,000원 취소 → 6,000원)에서 먼저 옮겼습니다.
- 단위: `SimulateFailurePolicyTest`(`local`·`demo`는 `true` 허용, `member`·프로필 없음은 `true`만 거부, `false`·생략은 모두 허용).
- 웹(`controllers=` 한정): `SimulatedPaymentApiWebTest`(계약 예시 JSON, 실패 응답 전부, 키·헤더·본문 400), `SimulatedPaymentProfileOffWebTest`(프로필 밖 404).
- 통합(#122): `SimulatedPaymentCardLockIntegrationTest`(09174 GREAT, 대표 카드 고정). 차례 승인과 동시 승인 모두 뒤 승인의 재계산이 앞 승인의 원장을 읽어 "GREAT 사용액 모름"이 되고 원장 차감액은 `null` 그대로임을 확인합니다. 동시 승인은 테스트 트랜잭션이 카드 행을 잠근 채 두 승인을 보내고, 두 연결이 카드 잠금 조회에서 기다리는 것을 `information_schema.PROCESSLIST`로 확인한 뒤 풀어 서버에서 겹침을 보장합니다(래치, sleep 없음). 재생 시험은 전액 취소의 앞부분(카드 공유 잠금 → 주문 행 잠금)을 테스트 트랜잭션으로 재현하고, 공유 잠금이 쥐어진 동안 같은 키 재생(201, 같은 본문)과 다른 키 409가 끝나는지 봅니다. 카드 잠금 호출을 빼면 동시 승인 시험이, 재생에도 카드를 잠그면 재생 시험이 실패함을 로컬에서 확인했습니다.
- 통합: `SimulatedPaymentIntegrationTest`(V8 적용, 평가 자료 C01 승인·원장 행·주문 `PAID`, 같은 키 재생·다른 지문 409·다른 키 409, 추천 `ORDER_PAID` 무효화, 부분·전액 취소와 09271 `RECEIPT_MONTH` 확정 영향, 모의 실패 뒤 새 키 승인, 사용자별 분리).
- 통합(`member`, G3c #161): [`MemberPaymentIntegrationTest`](../../backend/src/test/java/com/beone/api/auth/web/MemberPaymentIntegrationTest.java). 회원 장부 승인·원장·재생(201, 같은 본문)·같은 키 다른 본문 409·다른 키 `ORDER_ALREADY_PAID`·추천 `ORDER_PAID`, 회원 A·B 같은 키 독립과 남의 것·없는 것 404 본문 동일, 24시간 안 재생·24시간 뒤 승인 409와 부분 취소 재실행(현재 동작 고정), `simulateFailure: true` 400·주문·결제·원장·키 미저장(400이 404보다 먼저)과 `false`·빈 본문·본문 없음 201(결정 38-1), 무효가 된 추천 승인과 결정 32 Q4 한계(대표 카드만 바꾸면 차이 없음), 추천 → 승인 사이 세션 만료 401·미저장과 재로그인 뒤 처리, 로그아웃·재로그인 뒤 재생, 401·CSRF 403·미저장, 시연 장부 404와 시연 헤더 무시.
