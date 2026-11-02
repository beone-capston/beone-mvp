# 월간 상태

카드별 월간 상태의 조회·수동 입력과, 2차의 출처 충돌 선택을 다룹니다(D-04, D-16, D-17).

공통 규약과 공통 실패 응답은 [`docs/api.md`](../api.md#업무-api-공통-규약)를, 전체 목록은 [README](README.md)를 봅니다. JSON은 합성 예시이며 실제 API 실행 결과나 약관 검증 결과가 아닙니다. 모든 API는 구현 전입니다.

## GET /api/v1/me/cards/{userCardId}/monthly-states/{month} — 월간 상태 조회

- [1차] 카드 한 장의 계산 월 상태값을 출처·기준 기간·입력 시각과 함께 조회한다. 계산에 못 쓰는 값은 이유를 준다.

카드 한 장의 월간 상태(전월 실적, 한도 버킷 사용액, 선택 서비스, 등록일)를 값마다 출처·기준 기간과 함께 조회하는 API.

값마다 `source`·`period`·`updatedAt`을 붙입니다(D-04, D-14 원칙 4). 입력하지 않은 값은 `UNKNOWN`이고 `value`는 `null`입니다. 기준 기간이 카드의 실적·한도 기간과 맞지 않거나 출처 충돌이 있으면 `usable=false`와 이유를 줍니다.

### Request Header

```text
X-BeONE-Demo-User: MY_CARDS
```

### Path / Query Parameter

- `userCardId` · Path · String · 필수
- `month` · Path · String(YYYY-MM) · 필수 · 계산 대상 월(주문이 속한 달)

### Request Body

없음.

### Response Body

성공: **200 OK**

```json
{
  "data": {
    "userCardId": "uc-1001",
    "month": "2026-11",
    "snapshotAt": "2026-11-03T10:20:00+09:00",
    "previousMonthPerformanceWon": {
      "value": 523000,
      "source": "MANUAL",
      "period": {
        "from": "2026-10-01",
        "to": "2026-10-31"
      },
      "updatedAt": "2026-11-03T10:20:00+09:00",
      "usable": true,
      "reason": null,
      "referencedCodef": null
    },
    "limitBucketUsages": [
      {
        "limitBucketId": "LB-GREAT",
        "value": 4000,
        "source": "MANUAL",
        "period": {
          "from": "2026-11-01",
          "to": "2026-11-30"
        },
        "updatedAt": "2026-11-03T10:20:00+09:00",
        "usable": true,
        "reason": null
      }
    ],
    "otherBucketsUsedWon": {
      "value": null,
      "source": "UNKNOWN",
      "period": null,
      "updatedAt": null,
      "usable": false,
      "reason": "NOT_ENTERED"
    },
    "selectedServices": [],
    "registeredOn": {
      "value": null,
      "source": "UNKNOWN",
      "period": null,
      "updatedAt": null,
      "usable": false,
      "reason": "NOT_ENTERED"
    },
    "conflicts": [],
    "afterSnapshot": {
      "approvalCount": 1,
      "approvedAmountWon": 12000,
      "cancellationCount": 0
    }
  }
}
```

#### 응답 필드

- `snapshotAt` · DateTime|null · 수동 입력 시각. 이 시각 뒤의 모의 승인만 사용액에 더한다(D-16). 입력이 없으면 `null`
- `source` · String · `MANUAL` / `CODEF` / `INFERRED` / `SYNTHETIC` / `UNKNOWN`
- `value` · `null`은 모름이고 `0`은 사용자가 0을 직접 입력한 값이다. 둘을 같게 다루지 않는다
- `previousMonthPerformanceWon.value` · Integer · 음수가 올 수 있다(D-17). 0으로 바꾸지 않는다
- 해당 달의 상태를 입력한 적이 없어도 404가 아니라 모든 값이 `UNKNOWN`인 응답을 준다. 404는 카드가 없거나 다른 사용자의 카드일 때만이다
- `otherBucketsUsedWon` · `limitBucketUsages`에 없는 한도 묶음의 사용액(코드 `otherBucketsUsed`)
- `registeredOn` · 카드 사용 등록일(코드 `HeldCard.registeredAt`). 카드 단위 값이라 달이 달라도 같은 값이다
- `usable` / `reason` · 계산에 쓸 수 있는지와 이유: `NOT_ENTERED` / `PERIOD_MISMATCH` / `SOURCE_CONFLICT` / `NEGATIVE_UNCONFIRMED_ATTRIBUTION`
- `referencedCodef` · Object|null · 2차에서 사용자가 CODEF 참고값을 보고 입력했을 때 참고한 조회월·조회 시각
- `conflicts` · Array · 같은 항목·같은 기준 기간에 출처가 다른 값이 서로 다를 때만 담는다(D-16). 1차 수동 입력만 있으면 항상 `[]`. 항목 형식: `item`, `period`, `conflictVersion`, `candidates[]`(`source`, `value`, `period`, `updatedAt`), `selection`(`selectedSource`, `selectedAt`, 없으면 `null`)
- `afterSnapshot` · Object · 스냅샷 뒤 모의 승인·취소 요약. 화면 표시용이며 상태값을 덮어쓰지 않는다

### 실패 응답

- **400 `INVALID_REQUEST`**: 요청값이 올바르지 않은 경우
  - `month` 형식 오류

- **404 `USER_CARD_NOT_FOUND`**: 없거나 다른 사용자의 카드

- **500 `INTERNAL_SERVER_ERROR`**: 서버 오류

### 근거

- 결정 기록 D-04·D-16·D-17
- benefit 도메인 안내(StateValue, CardMonthlyState)

## PUT /api/v1/me/cards/{userCardId}/monthly-states/{month} — 월간 상태 수동 입력

- [1차] 카드 한 장의 월간 상태를 MANUAL로 저장한다. 일부만 입력할 수 있고, 입력하지 않은 값은 UNKNOWN이다(D-16).

카드 한 장의 월간 상태를 사용자가 직접 입력하는 API. 저장 시각이 이번 달 사용액의 스냅샷 기준 시각이 된다.

```text
PUT monthly-states/2026-11
→ 값마다 MANUAL·기준 기간·입력 시각 저장
→ snapshotAt = 저장 시각
→ 이후 모의 승인은 사용액에 더함
→ 이후 모의 취소는 D-17 조건에 맞을 때만 뺌
```

### Request Header

```text
Content-Type: application/json
X-BeONE-Demo-User: MY_CARDS
```

### Path / Query Parameter

- `userCardId` · Path · String · 필수
- `month` · Path · String(YYYY-MM) · 필수

### Request Body

```json
{
  "previousMonthPerformanceWon": {
    "value": 523000,
    "period": {
      "from": "2026-10-01",
      "to": "2026-10-31"
    }
  },
  "limitBucketUsages": [
    {
      "limitBucketId": "LB-GREAT",
      "value": 4000,
      "period": {
        "from": "2026-11-01",
        "to": "2026-11-30"
      }
    }
  ],
  "otherBucketsUsedWon": null,
  "selectedServices": [
    {
      "serviceId": "SVC-STORE",
      "effectiveFrom": "2026-10-15"
    }
  ],
  "registeredOn": null
}
```

#### 필수 필드

```text
(없음) 모든 항목은 선택
```

- 본문 전체가 이 카드·이 달의 수동 입력을 **교체**한다. 생략한 항목이나 `null`은 `UNKNOWN`으로 저장한다.
- 값을 보낸 항목은 `period`(`from`·`to`, YYYY-MM-DD)가 필수다.

#### 선택 필드

```text
previousMonthPerformanceWon
limitBucketUsages
otherBucketsUsedWon
selectedServices
registeredOn
```

- 금액 항목의 `value`는 정수이며 소수·문자열은 400이다. `previousMonthPerformanceWon.value`는 **음수를 받는다**(D-17). 0으로 바꾸지 않는다.
- `source`·`updatedAt`은 서버가 정한다. 요청으로 받지 않는다. 이 입력은 CODEF 조회값을 덮어쓰지 않는다.
- `limitBucketUsages[].value`는 0 이상 정수다. `0`은 "사용한 혜택 없음"을 직접 입력한 값이다.
- 카드 규칙에 없는 버킷 ID는 400이다. 버킷을 나눠 입력할 수 없는 나머지 사용액은 `otherBucketsUsedWon`에 넣는다.
- `selectedServices[].effectiveFrom`(적용일)이 없으면 그 서비스는 `확인 필요`다.
- `registeredOn`은 `{"value":"2026-09-01","period":{"from":"2026-09-01","to":"2026-09-01"}}` 형식이다. 기준 기간은 등록일 하루로 둔다(제안).
- `period`가 카드의 실적·한도 기간(D-03)과 다르면 저장은 하되 `usable=false`, `reason=PERIOD_MISMATCH`로 둔다. 오류가 아니다.

> 저장 시각(`snapshotAt`)보다 앞선 모의 승인을 다시 더하지 않습니다. 그 승인을 나중에 취소하면 스냅샷에 들어 있었는지 알 수 없으므로 영향받는 값은 `확인 필요`가 됩니다(D-17).

### Response Body

성공: **200 OK**

```json
{
  "data": {
    "userCardId": "uc-1001",
    "month": "2026-11",
    "snapshotAt": "2026-11-03T10:20:00+09:00",
    "previousMonthPerformanceWon": {
      "value": 523000,
      "source": "MANUAL",
      "period": {
        "from": "2026-10-01",
        "to": "2026-10-31"
      },
      "updatedAt": "2026-11-03T10:20:00+09:00",
      "usable": true,
      "reason": null,
      "referencedCodef": null
    },
    "limitBucketUsages": [
      {
        "limitBucketId": "LB-GREAT",
        "value": 4000,
        "source": "MANUAL",
        "period": {
          "from": "2026-11-01",
          "to": "2026-11-30"
        },
        "updatedAt": "2026-11-03T10:20:00+09:00",
        "usable": true,
        "reason": null
      }
    ],
    "otherBucketsUsedWon": {
      "value": null,
      "source": "UNKNOWN",
      "period": null,
      "updatedAt": null,
      "usable": false,
      "reason": "NOT_ENTERED"
    },
    "selectedServices": [
      {
        "serviceId": "SVC-STORE",
        "effectiveFrom": "2026-10-15",
        "source": "MANUAL",
        "updatedAt": "2026-11-03T10:20:00+09:00",
        "usable": true,
        "reason": null
      }
    ],
    "registeredOn": {
      "value": null,
      "source": "UNKNOWN",
      "period": null,
      "updatedAt": null,
      "usable": false,
      "reason": "NOT_ENTERED"
    },
    "conflicts": []
  }
}
```

#### 응답 필드

- 응답 형식은 「월간 상태 조회」와 같다

### 실패 응답

- **400 `INVALID_REQUEST`**: 요청값이 올바르지 않은 경우
  - `month`·`period` 날짜 형식 오류, `from`이 `to`보다 늦음
  - 카드 규칙에 없는 `limitBucketId`·`serviceId`
  - `usedBenefitWon` 음수

- **404 `USER_CARD_NOT_FOUND`**: 없거나 다른 사용자의 카드

- **409 `USER_CARD_MAPPING_REQUIRED`**: 매핑이 확인되지 않은 카드
  - `limitBucketUsages`·`selectedServices`를 보냈는데 `USER_CONFIRMED`가 아닌 경우. 어떤 규칙의 버킷·서비스인지 알 수 없다. 전월 실적만 보내면 매핑 없이 저장한다(D-16)

- **409 `PORTFOLIO_READ_ONLY`**: 가상 포트폴리오 사용자가 요청한 경우

- **500 `INTERNAL_SERVER_ERROR`**: 서버 오류

### 명세 단계 미정

- 가상 포트폴리오의 수동 상태값 변경: 이 명세는 읽기 전용으로 제안했지만 PRD §3.2는 "가상 거래 또는 명시적인 수동 상태값을 바꾸면 다시 계산"을 요구합니다. 사용자 확인이 필요합니다.
- 화면에서 묻는 순서와 필수·선택 구분(D-16).

### 구현 시 주의

- D-17의 음수 전월 실적 판정은 구현 전이며, 현재 판정기는 음수를 거부합니다. 판정기 변경이 이 API 구현의 선행 작업입니다.

### 근거

- 결정 기록 D-16·D-17
- benefit 도메인 안내(CardMonthlyState, ServiceSelection)

## PUT /api/v1/me/cards/{userCardId}/monthly-states/{month}/source-selections — 출처 충돌 계산 기준 선택

- [2차] 같은 항목·같은 기준 기간에 출처가 다른 값이 충돌할 때 사용자가 계산 기준을 고른다. 자동 우선순위는 없다(D-16).

월간 상태 값의 출처 충돌을 사용자가 해소하는 API. 선택은 그 값 하나에만 적용한다.

충돌은 같은 카드·같은 항목·같은 기준 기간에 출처가 다른 값이 서로 다를 때만 생깁니다. 값이 같거나, 기준 기간이 다르거나, `UNKNOWN`이거나, 사용자가 참고해 입력한 MANUAL 값과 그 CODEF 값이면 충돌이 아닙니다. 1차(수동 입력만)에서는 충돌이 생기지 않습니다.

### Request Header

```text
Content-Type: application/json
X-BeONE-Demo-User: MY_CARDS
```

### Path / Query Parameter

- `userCardId` · Path · String · 필수
- `month` · Path · String(YYYY-MM) · 필수

### Request Body

```json
{
  "item": "previousMonthPerformanceWon",
  "period": {
    "from": "2026-10-01",
    "to": "2026-10-31"
  },
  "selectedSource": "MANUAL",
  "conflictVersion": "cf-v3"
}
```

#### item

```text
previousMonthPerformanceWon
otherBucketsUsedWon
limitBucketUsage:{limitBucketId}
registeredOn
```

> `INFERRED` 후보는 원장 기반 산출이 별도 결정으로 열린 뒤에만 생깁니다(D-16 보류). 예시는 형식 예시입니다.

#### selectedSource

```text
MANUAL
CODEF
INFERRED
```

#### 필수 필드

```text
item
period
selectedSource
conflictVersion
```

- `conflictVersion`은 「월간 상태 조회」의 `conflicts[]`에 있는 값이다. 그 사이 새 값이 들어와 충돌 내용이 바뀌었으면 409로 다시 확인받는다.
- 선택은 이 카드·이 항목·이 기준 기간에만 적용하고 다른 달·항목으로 넓히지 않는다.

### Response Body

성공: **200 OK**

```json
{
  "data": {
    "item": "previousMonthPerformanceWon",
    "period": {
      "from": "2026-10-01",
      "to": "2026-10-31"
    },
    "selectedSource": "MANUAL",
    "selectedValue": 523000,
    "candidates": [
      {
        "source": "MANUAL",
        "value": 523000,
        "updatedAt": "2026-11-03T10:20:00+09:00"
      },
      {
        "source": "INFERRED",
        "value": 498000,
        "updatedAt": "2026-11-04T09:02:00+09:00",
        "ruleVersion": "09174-v1"
      }
    ],
    "selectedAt": "2026-11-04T09:05:00+09:00"
  }
}
```

### 실패 응답

- **400 `INVALID_REQUEST`**: 요청값이 올바르지 않은 경우
  - 허용 밖 `item`·`selectedSource`
  - 해당 항목에 그 출처 값이 없음

- **404 `USER_CARD_NOT_FOUND`**: 없거나 다른 사용자의 카드

- **409 `SOURCE_CONFLICT_CHANGED`**: 충돌 내용이 바뀐 경우
  - CODEF 재조회 등으로 후보 값이 달라짐

- **409 `SOURCE_CONFLICT_NOT_FOUND`**: 충돌이 없는 항목

- **500 `INTERNAL_SERVER_ERROR`**: 서버 오류

### 명세 단계 미정

- 화면에서 충돌을 한곳에 모아 보여 주는 형식.

### 근거

- 결정 기록 D-04·D-16
