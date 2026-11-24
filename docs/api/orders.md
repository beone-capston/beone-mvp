# 주문과 예정 소비

가상 주문과 예정 소비를 다룹니다. 예정 소비는 MVP에서 저장만 합니다(D-18 K4).

공통 규약과 공통 실패 응답은 [`docs/api.md`](../api.md#업무-api-공통-규약)를, 전체 목록은 [README](README.md)를 봅니다. JSON은 합성 예시이며 실제 API 실행 결과나 약관 검증 결과가 아닙니다. 구현 상태는 [README](README.md#업무-api-1차-명세)의 상태 항목을 봅니다.

프로필: 네 API는 `local`·`demo`와 로컬 회원 프로필 `member`에 등록돼 있습니다(G3b #160). 아래 `X-BeONE-Demo-User` 헤더는 `local`·`demo`의 방식입니다. `member`에서는 헤더 대신 회원 세션 쿠키(`__Host-BEONE_SESSION`)로 현재 사용자를 정하고 `POST /api/v1/me/orders`와 `PUT /api/v1/me/planned-spends`에 `X-CSRF-TOKEN`이 필요합니다. 검사 순서는 401 `UNAUTHENTICATED` → 403 `CSRF_INVALID` → 400 → 404이고 모든 응답에 `Cache-Control: no-store`가 붙습니다([회원 세션 규칙](../api.md#회원-세션member-프로필)). 다른 회원의 주문은 없는 주문과 같은 404 `ORDER_NOT_FOUND`이고, 예정 소비는 회원 장부마다 나뉩니다. 주문 목록 API는 없으므로 `GET /api/v1/me/orders`는 405 `METHOD_NOT_ALLOWED`입니다.

## POST /api/v1/me/orders — 주문 생성

- [1차] 결제할 가상 주문을 만든다. 추천·모의 승인은 이 주문 ID로 요청한다.
- 2026-11-24 보충(결정 38-4): 회원 화면은 승인 후 결과만 보여 주는 흐름을 유지한다. 모의 승인 API가 서버에서 추천을 다시 계산하므로 주문 뒤 추천 API를 거치지 않고 바로 승인할 수 있다. 이때 `seenRecommendationId`가 없어 `recalculation.changed=null`이며 결함이 아니다([모의 승인](mock-payments.md)).

결제 전 가상 주문(금액·날짜·가맹점 분류·결제 방식)을 만드는 API.

```text
주문 입력 화면
→ POST /api/v1/me/orders
→ POST /api/v1/me/orders/{orderId}/recommendations
→ POST /api/v1/me/orders/{orderId}/simulated-payments
```

### Request Header

```text
Content-Type: application/json
X-BeONE-Demo-User: MY_CARDS
```

### Request Body

```json
{
  "amountWon": 10000,
  "orderDate": "2026-11-03",
  "orderTime": null,
  "merchantName": "스타벅스 송도점",
  "merchantClass": "STARBUCKS_STORE",
  "paymentMethod": "DIRECT"
}
```

#### paymentMethod

```text
DIRECT
ELIGIBLE_SIMPLE_PAY
AUTOPAY
```

#### merchantClass

사용자나 시나리오가 확인한 가맹점·업종 분류 코드다. 허용 값은 [`rules/merchant-classes.txt`](../../backend/src/main/resources/rules/merchant-classes.txt) 기준이다(D-23). 가맹점 이름으로 추정해 채우지 않는다.

```text
STARBUCKS_STORE
CU_STORE
GS25_STORE
TELECOM_SKT
ORDINARY_DOMESTIC
TAX
…
```

위 값은 평가 자료(`fixtures/evaluation-cases.json`)에 있는 코드입니다.

#### 필수 필드

```text
amountWon
orderDate
merchantClass
paymentMethod
```

- `amountWon`은 1 이상 정수(원). 소수·문자열 금액은 400이다.
- `orderDate`는 YYYY-MM-DD(Asia/Seoul).

#### 선택 필드

```text
merchantName
orderTime
```

- `merchantName`은 화면 표시용이며 최대 50자. 계산에 쓰지 않는다.
- `orderTime`은 `HH:mm:ss`(Asia/Seoul)다. 보내지 않으면 `null`이고 00:00을 만들어 넣지 않는다.

### Response Body

성공: **201 Created**

```json
{
  "data": {
    "orderId": "ord-5001",
    "amountWon": 10000,
    "orderDate": "2026-11-03",
    "orderTime": null,
    "merchantName": "스타벅스 송도점",
    "merchantClass": "STARBUCKS_STORE",
    "paymentMethod": "DIRECT",
    "status": "CREATED",
    "createdAt": "2026-11-03T10:40:00+09:00"
  }
}
```

#### 응답 필드

- `status` · String · `CREATED` / `PAID`(모의 승인됨) / `CANCELLED`(전액 취소됨)
- 주문은 수정하지 않는다. 입력을 바꾸려면 새 주문을 만든다(이전 추천을 새 주문의 승인 근거로 쓰지 않기 위함)

### 실패 응답

- **400 `INVALID_REQUEST`**: 요청값이 올바르지 않은 경우
  - 필수 필드 누락
  - `amountWon` 0 이하
  - 날짜 형식 오류
  - 허용 밖 `paymentMethod`

- **500 `INTERNAL_SERVER_ERROR`**: 서버 오류

### 근거

- 결정 기록 D-14, D-23
- `merchantClass` 허용 값은 [`rules/merchant-classes.txt`](../../backend/src/main/resources/rules/merchant-classes.txt) 기준이다(D-23). 입력 검증은 업무 API가 한다
- benefit 도메인 안내(Order, MerchantClass, PaymentMethod)

## GET /api/v1/me/orders/{orderId} — 주문 상세 조회

- [1차] 주문 입력과 결제 상태, 연결된 모의 결제를 조회한다.

주문 하나의 입력값과 결제 상태를 조회하는 API.

### Request Header

```text
X-BeONE-Demo-User: MY_CARDS
```

### Path / Query Parameter

- `orderId` · Path · String · 필수

### Request Body

없음.

### Response Body

성공: **200 OK**

```json
{
  "data": {
    "orderId": "ord-5001",
    "amountWon": 10000,
    "orderDate": "2026-11-03",
    "orderTime": null,
    "merchantName": "스타벅스 송도점",
    "merchantClass": "STARBUCKS_STORE",
    "paymentMethod": "DIRECT",
    "status": "PAID",
    "createdAt": "2026-11-03T10:40:00+09:00",
    "simulatedPaymentId": "pay-7001",
    "latestRecommendationId": "rec-3001"
  }
}
```

#### 응답 필드

- `simulatedPaymentId` · String|null · 모의 승인됐으면 결제 ID
- `latestRecommendationId` · String|null · 이 주문으로 마지막에 계산한 추천

### 실패 응답

- **404 `ORDER_NOT_FOUND`**: 없거나 다른 사용자의 주문

- **500 `INTERNAL_SERVER_ERROR`**: 서버 오류

### 근거

- 결정 기록 D-14

## GET /api/v1/me/planned-spends — 예정 소비 조회

- [1차] 예정 소비 목록을 조회한다. 1차에서는 월간 합계 계산에 쓰지 않는다(D-18 K4).

이번 달 예정 소비 목록을 조회하는 API.

> D-18 K4(MVP 범위 축소, 이유: 일정): 1차는 예정 소비를 입력·저장만 하고 월간 합계는 현재 주문 기준으로 계산합니다. PRD FR-05 범위를 줄인 결정입니다.

### Request Header

```text
X-BeONE-Demo-User: MY_CARDS
```

### Request Body

없음.

### Response Body

성공: **200 OK**

```json
{
  "data": {
    "items": [
      {
        "plannedSpendId": "ps-1",
        "plannedDate": "2026-11-20",
        "amountWon": 30000,
        "merchantClass": "STARBUCKS_STORE",
        "paymentMethod": "DIRECT",
        "merchantName": "스타벅스",
        "plannedTime": null
      }
    ],
    "usedInRecommendation": false
  }
}
```

#### 응답 필드

- `usedInRecommendation` · Boolean · 1차에서는 항상 `false`(D-18 K4)

### 실패 응답

- **500 `INTERNAL_SERVER_ERROR`**: 서버 오류

### 근거

- D-18 K4
- PRD FR-05

## PUT /api/v1/me/planned-spends — 예정 소비 일괄 설정

- [1차] 예정 소비를 최대 5건까지 통째로 교체한다. 1차에서는 저장만 한다(D-18 K4).

예정 소비 목록을 한 번에 교체해 저장하는 API.

> K4에 따라 1차에서는 추천 계산에 반영하지 않습니다. 화면에 "이번 버전은 예정 소비를 추천에 반영하지 않습니다"를 표시합니다.

### Request Header

```text
Content-Type: application/json
X-BeONE-Demo-User: MY_CARDS
```

### Request Body

```json
{
  "items": [
    {
      "plannedDate": "2026-11-20",
      "amountWon": 30000,
      "merchantClass": "STARBUCKS_STORE",
      "paymentMethod": "DIRECT",
      "merchantName": "스타벅스"
    }
  ]
}
```

#### 필수 필드

```text
items
```

- `items`는 0~5건. 빈 배열이면 모두 지운다.
- 각 항목의 `plannedDate`, `amountWon`(1 이상), `merchantClass`, `paymentMethod`가 필수다.
- `plannedDate`는 이번 달이어야 한다. 조회 범위와 같으며, 벗어나면 400 `INVALID_REQUEST`다(D-23).

#### 선택 필드

```text
items[].merchantName
items[].plannedTime
```

- `merchantName`은 최대 50자. `plannedTime`은 `HH:mm:ss`이고 없으면 응답에 `null`로 담는다. 시각을 만들어 넣지 않는다.

### Response Body

성공: **200 OK**

```json
{
  "data": {
    "items": [
      {
        "plannedSpendId": "ps-1",
        "plannedDate": "2026-11-20",
        "amountWon": 30000,
        "merchantClass": "STARBUCKS_STORE",
        "paymentMethod": "DIRECT",
        "merchantName": "스타벅스",
        "plannedTime": null
      }
    ],
    "usedInRecommendation": false
  }
}
```

### 실패 응답

- **400 `INVALID_REQUEST`**: 요청값이 올바르지 않은 경우
  - 5건 초과
  - 필수 필드 누락·금액 0 이하·날짜 형식 오류

- **500 `INTERNAL_SERVER_ERROR`**: 서버 오류

### 근거

- D-18 K4
- benefit 도메인 안내(PlannedSpendings 최대 5건)
