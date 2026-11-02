# 모의 승인·취소

실제 카드 결제가 아닌 서비스 내부 모의 승인과 취소를 다룹니다(D-14, D-17). 멱등 키 규칙은 [공통 규약](../api.md#업무-api-공통-규약)을 따릅니다.

공통 규약과 공통 실패 응답은 [`docs/api.md`](../api.md#업무-api-공통-규약)를, 전체 목록은 [README](README.md)를 봅니다. JSON은 합성 예시이며 실제 API 실행 결과나 약관 검증 결과가 아닙니다. 모든 API는 구현 전입니다.

## POST /api/v1/me/orders/{orderId}/simulated-payments — 모의 승인 요청

- [1차] 주문을 서버가 다시 계산해 모의 승인하고 원장에 한 번만 기록한다. 사용자가 본 추천과 다르면 차이를 담는다(D-14 ⑥⑦).

주문 ID로 모의 승인을 요청하는 API. 서버가 승인 직전에 추천을 다시 계산하고, 결제 전 설정에 따라 카드를 정해 원장에 승인 행을 남긴다.

```text
POST simulated-payments (Idempotency-Key)
→ 같은 키·같은 본문의 이전 요청이 있으면 그 결과를 그대로 반환
→ 추천 재계산
→ AUTO_RECOMMEND: 재계산의 선택 카드 / FIXED_REPRESENTATIVE: 대표 카드
→ seenRecommendationId 결과와 비교해 differences 작성
→ 원장에 SIMULATION·APPROVAL 행 기록, 주문 PAID
```

> 서버는 Android 기기 인증(생체·PIN, 60초 활성) 결과를 승인 근거로 신뢰하지 않습니다(D-08). 앱은 인증이 끝난 뒤에만 이 API를 부르지만, 서버 권한 판단에는 쓰지 않습니다.

### Request Header

```text
Content-Type: application/json
X-BeONE-Demo-User: MY_CARDS
Idempotency-Key: 6f1c2a9e-3b7d-4c10-9e55-2d4b8a7f0c31
```

- `Idempotency-Key` · 필수 · UUID 형식 권장. 같은 키·같은 본문은 같은 결과를 반환한다. 같은 키·다른 본문은 409다.

### Path / Query Parameter

- `orderId` · Path · String · 필수

### Request Body

```json
{
  "seenRecommendationId": "rec-3001"
}
```

#### 필수 필드

```text
(없음)
```

#### 선택 필드

```text
seenRecommendationId
```

- 사용자가 화면에서 본 추천 ID. 보내면 재계산 결과와 비교해 `recalculation.differences`를 채운다.
- 카드 ID는 요청으로 받지 않는다. 서버가 결제 전 설정을 읽어 카드를 정한다. 실제 카드사 승인·NFC·결제 토큰이 아니다.
- 보내지 않으면 비교 없이 승인하고 `changed=null`로 둔다.

> 조건부 추천이어도 승인할 수 있고, 승인 결과에 조건부였다는 사실을 남깁니다(D-18 K2). 대표 카드 고정 모드에서 혜택이 `확인 필요`인 카드를 승인하고 혜택을 `확인 필요`로 기록하는 동작은 제안이며, 결정 기록에는 아직 없습니다.

### Response Body

성공: **201 Created**

```json
{
  "data": {
    "simulatedPaymentId": "pay-7001",
    "orderId": "ord-5001",
    "status": "APPROVED",
    "approvedAt": "2026-11-03T10:42:00+09:00",
    "amountWon": 10000,
    "selectionMode": "AUTO_RECOMMEND",
    "userCardId": "uc-1001",
    "cardProductId": "09174",
    "conditional": true,
    "currentOrderBenefit": {
      "amountWon": 2100,
      "status": "CONFIRMED"
    },
    "appliedRule": {
      "ruleVersion": "09174-v1",
      "verificationStatus": "VERIFIED",
      "officialSources": [
        {
          "uri": "https://img2.kbcard.com/obj/card/download/09174__prdctOpmn_20260714.pdf",
          "locator": "p.1"
        }
      ]
    },
    "selectionBasis": {
      "decidedBy": "MONTHLY_TOTAL",
      "arbitraryTieBreak": false,
      "tiedUserCardIds": []
    },
    "mappingNote": "사용자 확인 매핑, 공식 SKU·약관 버전 미검증",
    "recalculation": {
      "recommendationId": "rec-3002",
      "seenRecommendationId": "rec-3001",
      "changed": false,
      "differences": []
    },
    "ledgerTransactionId": "lt-9001",
    "cancellableAmountWon": 10000
  }
}
```

#### 응답 필드

- `recalculation.changed` · Boolean|null · 사용자가 본 추천과 선택 카드·혜택이 달라졌는지
- `recalculation.differences[]` · `{"field":"selected.userCardId","before":"uc-1001","after":"uc-1003","reason":"MONTHLY_STATE_CHANGED"}` 형식
- `status` · `APPROVED` / `FAILED`. `FAILED`는 서비스 내부 테스트 조건의 실패이고 카드사 거절이 아니다(PRD §4, FR-07)
- `currentOrderBenefit` · 승인 시점 계산값. 미확정이면 `amountWon=null`. 카드사가 지급한 금액이 아니다
- 승인된 혜택은 다음 계산의 사용액으로 서비스 체인 안 모든 한도 버킷에 반영한다(D-18 K1). 모의 원장 기준이며 실제 카드사 차감 시점이 아니다
- `ledgerTransactionId` · 원장에 남긴 승인 행
- `cancellableAmountWon` · 남은 취소 가능 금액

### 실패 응답

- **400 `INVALID_REQUEST`**: 요청값이 올바르지 않은 경우
  - `Idempotency-Key` 누락

- **404 `ORDER_NOT_FOUND`**: 없거나 다른 사용자의 주문

- **409 `ORDER_ALREADY_PAID`**: 이미 결제된 주문
  - 다른 Idempotency-Key로 같은 주문을 다시 승인함

- **409 `IDEMPOTENCY_KEY_REUSED`**: 같은 Idempotency-Key를 다른 요청에 다시 쓴 경우

- **409 `RECOMMENDATION_NOT_DETERMINED`**: 자동 추천인데 추천을 확정할 수 없는 경우
  - 비교 가능한 카드가 0장(D-18 K2). 매핑 확인·월간 상태 입력 또는 대표 카드 고정이 필요하다

- **409 `REPRESENTATIVE_CARD_NOT_ELIGIBLE`**: 대표 카드 고정인데 대표 카드를 쓸 수 없는 경우
  - 대표 카드가 해제됐거나 매핑이 바뀜

- **500 `INTERNAL_SERVER_ERROR`**: 서버 오류

### 명세 단계 미정

- 모의 승인 실패(`FAILED`)를 일으키는 내부 테스트 조건과 그 응답 코드.
- 대표 카드 고정에서 `확인 필요` 카드의 승인 허용 여부.
- `seenRecommendationId`가 무효화된 추천일 때 승인을 막을지, 차이만 표시할지(현재 제안: 차이만 표시).

### 근거

- 결정 기록 D-08·D-12·D-14(원칙 6·7)·D-16, D-18 K2
- benefit 도메인 안내(LedgerTransaction, LedgerSource)

## GET /api/v1/me/simulated-payments/{simulatedPaymentId} — 모의 결제 상세 조회

- [1차] 모의 결제의 승인·취소 내역과 적용 규칙, 남은 취소 가능 금액을 조회한다.

모의 결제 하나의 승인 결과와 취소 내역을 조회하는 API.

### Request Header

```text
X-BeONE-Demo-User: MY_CARDS
```

### Path / Query Parameter

- `simulatedPaymentId` · Path · String · 필수

### Request Body

없음.

### Response Body

성공: **200 OK**

```json
{
  "data": {
    "simulatedPaymentId": "pay-7001",
    "orderId": "ord-5001",
    "status": "PARTIALLY_CANCELLED",
    "approvedAt": "2026-11-03T10:42:00+09:00",
    "amountWon": 10000,
    "selectionMode": "AUTO_RECOMMEND",
    "userCardId": "uc-1001",
    "cardProductId": "09174",
    "conditional": true,
    "currentOrderBenefit": {
      "amountWon": 2100,
      "status": "CONFIRMED"
    },
    "appliedRule": {
      "ruleVersion": "09174-v1",
      "verificationStatus": "VERIFIED",
      "officialSources": [
        {
          "uri": "https://img2.kbcard.com/obj/card/download/09174__prdctOpmn_20260714.pdf",
          "locator": "p.1"
        }
      ]
    },
    "selectionBasis": {
      "decidedBy": "MONTHLY_TOTAL",
      "arbitraryTieBreak": false,
      "tiedUserCardIds": []
    },
    "mappingNote": "사용자 확인 매핑, 공식 SKU·약관 버전 미검증",
    "recalculation": {
      "recommendationId": "rec-3002",
      "seenRecommendationId": "rec-3001",
      "changed": false,
      "differences": []
    },
    "ledgerTransactionId": "lt-9001",
    "cancellableAmountWon": 6000,
    "cancellations": [
      {
        "cancellationId": "cn-8001",
        "amountWon": 4000,
        "cancelledAt": "2026-11-03T11:00:00+09:00",
        "ledgerTransactionId": "lt-9002"
      }
    ]
  }
}
```

#### 응답 필드

- `status` · String · `APPROVED` / `FAILED` / `PARTIALLY_CANCELLED` / `CANCELLED`

### 실패 응답

- **404 `SIMULATED_PAYMENT_NOT_FOUND`**: 없거나 다른 사용자의 결제

- **500 `INTERNAL_SERVER_ERROR`**: 서버 오류

### 근거

- 결정 기록 D-17

## POST /api/v1/me/simulated-payments/{simulatedPaymentId}/cancellations — 모의 결제 취소

- [1차] 모의 승인을 전액 또는 부분 취소하고 원장에 취소 행을 남긴다. 실적·한도 영향은 확정 규칙이 있는 카드만 반영한다(D-17).

BeONE이 만든 모의 승인을 취소하는 API. 원장에는 항상 기록하고, 실적·한도·혜택 영향은 근거가 확정된 경우만 반영한다.

```text
POST cancellations (Idempotency-Key)
→ 원 승인에 연결된 CANCELLATION 행(취소일, 음수 금액) 기록
→ 원 승인이 최근 스냅샷 뒤의 모의 승인이면 스냅샷 값에서 뺌, 앞이면 영향값 확인 필요
→ 실적 차감: 귀속 규칙(RECEIPT_MONTH 등)이 확정된 카드만
→ 혜택·한도 되돌림: 약관 규칙이 확정된 카드만, 아니면 확인 필요
→ 영향 결과를 confirmed / needsConfirmation 으로 나눠 응답
```

### Request Header

```text
Content-Type: application/json
X-BeONE-Demo-User: MY_CARDS
Idempotency-Key: 6f1c2a9e-3b7d-4c10-9e55-2d4b8a7f0c31
```

- `Idempotency-Key` · 필수. 같은 키·같은 본문은 같은 결과, 같은 키·다른 본문은 409.

### Path / Query Parameter

- `simulatedPaymentId` · Path · String · 필수

### Request Body

```json
{
  "amountWon": 4000
}
```

#### 필수 필드

```text
amountWon
```

- 1 이상 정수. 남은 취소 가능 금액 이하여야 한다. 전액 취소는 남은 금액 전체를 보낸다.
- 여러 번 부분취소할 수 있다. 합계는 원 승인 금액을 넘지 않는다.
- 취소의 취소(재승인)는 받지 않는다.

### Response Body

성공: **201 Created**

```json
{
  "data": {
    "cancellationId": "cn-8001",
    "simulatedPaymentId": "pay-7001",
    "amountWon": 4000,
    "cancelledAt": "2026-11-03T11:00:00+09:00",
    "ledgerTransactionId": "lt-9002",
    "paymentStatus": "PARTIALLY_CANCELLED",
    "cancellableAmountWon": 6000,
    "impact": {
      "confirmed": [
        {
          "target": "PERFORMANCE",
          "userCardId": "uc-1001",
          "month": "2026-11",
          "deltaWon": -4000,
          "basis": "RECEIPT_MONTH"
        }
      ],
      "needsConfirmation": [
        {
          "target": "BENEFIT",
          "userCardId": "uc-1001",
          "serviceId": "SVC-GREAT",
          "reason": "혜택 되돌림 규칙이 약관에서 확정되지 않았습니다."
        },
        {
          "target": "LIMIT_USAGE",
          "userCardId": "uc-1001",
          "limitBucketId": "LB-GREAT",
          "reason": "한도 복원 시점 근거가 확정되지 않았습니다."
        }
      ]
    }
  }
}
```

#### 응답 필드

- 원장 행 기록, 결제 상태 변경, 영향 재계산은 한 트랜잭션으로 처리한다
- `impact.confirmed[]` · 확정 규칙으로 바뀐 값과 근거. `target`은 `PERFORMANCE`·`LIMIT_USAGE`·`BENEFIT`
- `impact.needsConfirmation[]` · `확인 필요`가 된 값과 이유. `target` 값은 위와 같다. 부분취소 영향 계산의 원 미만은 D-03 절사(사용자 결정, 규칙 데이터 PR에서 기록)로 맞춘다. 혜택 되돌림 규칙 자체가 확정되지 않았으면 여기에 담는다
- 취소일이 원 승인과 다른 달이고 귀속 규칙이 없는 카드는 두 달의 영향값을 모두 `needsConfirmation`에 담는다

### 실패 응답

- **400 `INVALID_REQUEST`**: 요청값이 올바르지 않은 경우
  - `Idempotency-Key` 누락
  - `amountWon` 누락·0 이하

- **404 `SIMULATED_PAYMENT_NOT_FOUND`**: 없거나 다른 사용자의 결제

- **409 `CANCELLATION_AMOUNT_EXCEEDED`**: 남은 취소 가능 금액을 넘는 경우

- **409 `PAYMENT_ALREADY_CANCELLED`**: 이미 전액 취소된 결제

- **409 `IDEMPOTENCY_KEY_REUSED`**: 같은 Idempotency-Key를 다른 요청에 다시 쓴 경우

- **500 `INTERNAL_SERVER_ERROR`**: 서버 오류

### 근거

- 결정 기록 D-03·D-16·D-17
- benefit 도메인 안내(Ledger.cancellationEvidence, PolicyTermEvidence)
