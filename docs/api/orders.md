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

## GET /api/v1/me/orders — 내 주문 목록 조회

- [결정 38-5] 현재 사용자가 저장한 주문을 최신순으로 나눠 조회한다. 수정·삭제·상태 필터는 없다.
- **계약 초안(#178, 구현 전 관리자 확인 대기).** 확인 전까지 이 경로는 위 프로필 설명대로 405다.

현재 사용자 장부(`user_id`)의 주문을 최신순으로 한 페이지씩 돌려주는 API.

### 대상

- 현재 사용자 장부의 주문만 담는다. `local`·`demo`는 `X-BeONE-Demo-User` 헤더로, `member`는 회원 세션으로 사용자를 정한다.
- 다른 사용자의 주문은 담지 않는다. `member`에서 시연 장부(`MY_CARDS` 등)의 주문은 보이지 않는다. 회원과 시연 사용자는 장부가 다르기 때문이다(결정 37·38-2).
- 모든 상태(`CREATED`·`PAID`·`CANCELLED`)를 담는다. 상태 필터는 두지 않는다(필요하면 후속).

### Request Header

```text
X-BeONE-Demo-User: MY_CARDS
```

### Path / Query Parameter

- `page` · Query · 정수 · 선택 · 0부터 센다. 기본 `0`, 허용 `0`~`1000`
- `size` · Query · 정수 · 선택 · 한 페이지 건수. 기본 `20`, 허용 `1`~`50`

두 값은 십진 숫자만 받는다(`0`, `7`, `50`). 빈 값, 부호·공백·소수점이 붙은 값, 앞자리 0(`07`), 같은 이름을 두 번 보낸 경우, 허용 범위 밖의 값은 400 `INVALID_REQUEST`다. 그 밖의 쿼리 이름은 무시한다.

### 정렬

`createdAt` 내림차순이고, 같은 시각이면 주문 번호(`orderId`의 숫자) 내림차순이다. 같은 데이터에 같은 요청이면 항상 같은 순서·같은 페이지를 돌려준다.

### Request Body

없음.

### Response Body

성공: **200 OK**

```json
{
  "data": {
    "items": [
      {
        "orderId": "ord-5002",
        "amountWon": 4500,
        "orderDate": "2026-11-03",
        "merchantName": null,
        "merchantClass": "CU_STORE",
        "status": "CREATED",
        "createdAt": "2026-11-03T11:02:00+09:00",
        "simulatedPaymentId": null
      },
      {
        "orderId": "ord-5001",
        "amountWon": 10000,
        "orderDate": "2026-11-03",
        "merchantName": "스타벅스 송도점",
        "merchantClass": "STARBUCKS_STORE",
        "status": "PAID",
        "createdAt": "2026-11-03T10:40:00+09:00",
        "simulatedPaymentId": "pay-7001"
      }
    ],
    "page": 0,
    "size": 20,
    "hasNext": false
  }
}
```

#### 응답 필드

- `items[]` · 목록용 요약. 각 필드의 뜻과 형식은 [주문 상세 조회](#get-apiv1meordersorderid--주문-상세-조회)와 같고, 값이 없는 필드도 `null`로 쓴다
  - 담는 필드: `orderId`, `amountWon`, `orderDate`, `merchantName`, `merchantClass`, `status`, `createdAt`, `simulatedPaymentId`
  - 빼는 필드: `orderTime`, `paymentMethod`, `latestRecommendationId`. 상세 화면에서만 필요하고, `latestRecommendationId`는 주문마다 추천을 따로 찾아야 해서 목록에 넣지 않는다. 필요하면 상세 조회를 부른다
- `page` · Number · 요청에 적용한 페이지 번호(생략하면 `0`)
- `size` · Number · 요청에 적용한 페이지 크기(생략하면 `20`). 실제 `items` 건수는 이보다 적을 수 있다
- `hasNext` · Boolean · 다음 페이지에 주문이 더 있으면 `true`
- 전체 건수(`totalCount`)는 주지 않는다. 화면은 `hasNext`로 "더 보기"를 정한다
- 주문이 없거나 마지막 페이지를 지나면 200과 `items: []`, `hasNext: false`다(404 아님)

### 페이지 방식을 고른 이유(page·size)

- 커서 방식은 `createdAt`과 주문 번호를 묶은 불투명 커서의 인코딩·검증·변조 처리가 필요하다. page·size는 정수 두 개 검사로 끝나고 Spring Data `Slice`로 바로 구현된다.
- MVP의 사용자별 주문 수는 작아 오프셋 비용이 문제 되지 않는다.
- 한계: 페이지를 넘기는 사이 새 주문이 생기면 다음 페이지에 앞 페이지의 마지막 주문이 다시 보일 수 있다(최신순이라 새 주문이 앞에 끼므로). 화면은 `orderId`로 중복을 걸러 낸다. 주문은 삭제하지 않으므로 빠지는 주문은 없다.

### member 프로필 규칙

- 검사 순서는 401 `UNAUTHENTICATED` → 400이다. GET이라 CSRF 토큰을 보지 않는다. 세션이 없으면 파라미터가 틀려도 401이다.
- 성공·실패 모든 응답에 `Cache-Control: no-store`가 붙는다([회원 세션 규칙](../api.md#회원-세션member-프로필)).

### 다른 메서드

`PUT`·`PATCH`·`DELETE /api/v1/me/orders`는 계속 405 `METHOD_NOT_ALLOWED`다. 주문은 수정·삭제하지 않는다.

### 실패 응답

- **400 `INVALID_REQUEST`**: 요청값이 올바르지 않은 경우
  - `page`·`size` 형식 오류·범위 밖. `errors`에 `{"field": "page" 또는 "size", "message": "형식이 올바르지 않습니다."}`를 담고 입력 원문은 남기지 않는다

- **401 `UNAUTHENTICATED`**: `member`에서 회원 세션이 없거나 만료된 경우

- **500 `INTERNAL_SERVER_ERROR`**: 서버 오류

### 구현 메모(초안)

- 인덱스: 기존 `idx_orders_user_id`로 사용자 행을 좁힌 뒤 정렬한다. MVP 규모에서는 새 Flyway 인덱스를 추가하지 않는 안을 제안한다.

### 근거

- 결정 38-5
- 결정 기록 D-09(현재 사용자 범위), 결정 37(회원과 업무 장부 연결)

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
