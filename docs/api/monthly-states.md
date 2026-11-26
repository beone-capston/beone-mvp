# 월간 상태

카드별 월간 상태의 조회·수동 입력과, 2차의 출처 충돌 선택을 다룹니다(D-04, D-16, D-17).

공통 규약과 공통 실패 응답은 [`docs/api.md`](../api.md#업무-api-공통-규약)를, 전체 목록은 [README](README.md)를 봅니다. JSON은 합성 예시이며 실제 API 실행 결과나 약관 검증 결과가 아닙니다. 구현 상태는 [README](README.md#업무-api-1차-명세)의 상태 항목을 봅니다.

프로필: 세 API는 `local`·`demo`와 로컬 회원 프로필 `member`에 등록돼 있습니다(G3a #159). 아래 `X-BeONE-Demo-User` 헤더는 `local`·`demo`의 방식입니다. `member`에서는 헤더 대신 회원 세션 쿠키(`__Host-BEONE_SESSION`)로 현재 사용자를 정하고 PUT 두 개에 `X-CSRF-TOKEN`이 필요합니다. 검사 순서는 401 `UNAUTHENTICATED` → 403 `CSRF_INVALID` → 400 → 404 → 409이고 모든 응답에 `Cache-Control: no-store`가 붙습니다([회원 세션 규칙](../api.md#회원-세션member-프로필)). 출처 충돌 계산 기준 선택은 `member`에서도 항상 409 `SOURCE_CONFLICT_NOT_FOUND`이고 저장하지 않습니다(결정 32 Q1).

## GET /api/v1/me/cards/{userCardId}/monthly-states/{month} — 월간 상태 조회

- [1차] 카드 한 장의 계산 월 상태값을 출처·기준 기간·입력 시각과 함께 조회한다. 계산에 못 쓰는 값은 이유를 준다.

카드 한 장의 월간 상태(전월 실적, 한도 버킷 사용액, 선택 서비스, 등록일)를 값마다 출처·기준 기간과 함께 조회하는 API.

값마다 `source`·`period`·`updatedAt`을 붙입니다(D-04, D-14 원칙 4). 입력하지 않은 값은 `UNKNOWN`이고 `value`는 `null`입니다. 출처 충돌이 있으면 `usable=false`와 이유를 줍니다. 기준 기간이 맞는지는 이 API가 판단하지 않고 엔진이 계산 때 판단합니다(D-22).

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
      "referencedCodef": null,
      "codefReference": null
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
    "serviceSelectionStatus": "UNKNOWN",
    "noServiceSelection": null,
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
- `otherBucketsUsedWon` · `limitBucketUsages`에 없는 한도 묶음의 사용액(코드 `otherBucketsUsed`). 체인 버킷의 사용액을 대신하지 않는다
- `serviceSelectionStatus` · String · 택1 선택 입력 상태: `UNKNOWN`(미입력, `확인 필요`) / `SELECTED`(서비스 선택) / `NONE`(선택 없음, 사용자 명시 입력). 서버가 정하며 요청으로 받지 않는다
- `noServiceSelection` · Object|null · `NONE`일 때 `effectiveFrom`, `source`, `updatedAt`, `usable`, `reason`
- `registeredOn` · 카드 사용 등록일(코드 `HeldCard.registeredAt`). 카드 단위 값이라 달이 달라도 같은 값이다
- `usable` / `reason` · 계산에 쓸 수 있는지와 이유: `NOT_ENTERED` / `SOURCE_CONFLICT` / `NEGATIVE_UNCONFIRMED_ATTRIBUTION`
- `referencedCodef` · Object|null · 사용자가 CODEF 참고값을 보고 전월 실적을 입력했을 때(`PUT`의 `referencedCodefId`) 참고한 값의 입력 당시 스냅샷. 참고 없이 입력했으면 `null`(#281)
  - `referenceId` · String|null · 참고값 ID(`cpr-` 형식). 참고값 행이 지워졌으면 `null`이고 나머지는 그대로다
  - `syncId` · String · 그 참고값을 저장한 동기화(`sync-` 형식, CODEF 동기화 응답의 `syncId`와 같음)
  - `inquiryMonth` · String(YYYY-MM) · 조회월(반영기간 시작일의 달)
  - `period` · Object · 반영기간
  - `currentUseWon` · Integer|null · 그때의 카드사 조회 금액. 해석하지 못했으면 `null`(0이 아님)
  - `fetchedAt` · DateTime · 그 참고값의 조회 시각
- `codefReference` · Object|null · 전월 실적 칸에 미리 채워 보여 줄 "카드사 조회 실적(참고)"(D-16, #281). 이 카드에 연결된 CODEF 실적 참고값 중 반영기간이 대상 월의 전월 1일~말일과 정확히 같은 최신 1건이다. 없으면 `null`. **계산에 쓰지 않는다.** 상태를 입력한 적 없는 달에도 나온다. 당월 조회분(종료일이 조회일 전날)과 카드에 연결되지 않은 참고값은 나오지 않는다
  - `referenceId` · String · 참고값 ID(`cpr-` 형식). `PUT`의 `referencedCodefId`로 보낸다
  - `label` · String · 항상 `카드사 조회 실적(참고)`
  - `inquiryMonth` · String(YYYY-MM) · 조회월
  - `period` · Object · 반영기간
  - `currentUseWon` · Integer|null · 카드사 조회 금액(현재이용금액). 해석하지 못했으면 `null`(0이 아님)
  - `fetchedAt` · DateTime · 조회 시각
  - `usedInCalculation` · Boolean · 항상 `false`
- `conflicts` · Array · 같은 항목·같은 기준 기간에 출처가 다른 값이 서로 다를 때만 담는다(D-16). 항목 형식: `item`, `period`, `conflictVersion`, `candidates[]`(`source`, `value`, `period`, `updatedAt`), `selection`(`selectedSource`, `selectedAt`, 없으면 `null`)
  - 지금 충돌이 생기는 항목은 `previousMonthPerformanceWon`뿐이다(#281, 사용자 결정 2026-11-27 Q1). 저장된 전월 실적과 같은 기준 기간의 최신 연결 CODEF 참고값 금액이 있고(미확인은 충돌 아님) 값이 다르며, 그 값이 바로 그 참고값을 참고해 저장된 것이 아니면 충돌이다. 참고 없이 입력한 값도 포함한다. 재조회로 새 참고값이 다른 금액이면 다시 충돌이다
  - 충돌 동안 그 값은 `usable=false`, `reason=SOURCE_CONFLICT`이고 계산에 쓰지 않는다(Q2). `candidates`는 `MANUAL`(저장 값·입력 시각)과 `CODEF`(참고값 금액·조회 시각) 두 개다. `selection`은 항상 `null`이다(계산 기준 선택을 저장하지 않음)
  - 해소는 최신 참고값의 `referenceId`를 `referencedCodefId`로 보내 `PUT`으로 다시 저장하는 것이다(Q3). 값은 사용자가 정한다
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
  "noServiceSelection": null,
  "registeredOn": null
}
```

#### 필수 필드

```text
(없음) 모든 항목은 선택
```

- 본문 전체가 이 카드·이 달의 수동 입력을 **교체**한다. 생략한 항목이나 `null`은 `UNKNOWN`으로 저장한다.
- 가상 포트폴리오도 이 API로 월간 상태와 택1 선택(`NONE` 포함)을 바꿀 수 있고 그 값으로 다시 계산한다(D-19, PRD §3.2). 입력값의 출처는 `MANUAL`이고 시드 값 `SYNTHETIC`과 구분한다. 생략한 항목은 `UNKNOWN`이 아니라 시드 값으로 돌아간다(D-23).
- 값을 보낸 금액 항목과 `registeredOn`은 `period`(`from`·`to`, YYYY-MM-DD)가 필수다. `selectedServices`·`noServiceSelection`은 `period` 대신 `effectiveFrom`을 쓴다.

#### 선택 필드

```text
previousMonthPerformanceWon
limitBucketUsages
otherBucketsUsedWon
selectedServices
noServiceSelection
registeredOn
```

- 금액 항목의 `value`는 정수이며 소수·문자열은 400이다. `previousMonthPerformanceWon.value`는 **음수를 받는다**(D-17). 0으로 바꾸지 않는다.
- `source`·`updatedAt`은 서버가 정한다. 요청으로 받지 않는다. 이 입력은 CODEF 조회값을 덮어쓰지 않는다.
- `previousMonthPerformanceWon.referencedCodefId` · String · 선택 · 사용자가 보고 확인한 카드사 조회 실적(참고)의 `referenceId`(조회 응답 `codefReference.referenceId`, `cpr-` 형식, #281). 보내면 값은 그대로 `MANUAL`로 저장하고 참고한 CODEF 값을 응답 `referencedCodef`로 남긴다. 참고값과 다른 값을 보내도 된다(사용자가 정한 값). 본인의 참고값이고 이 카드에 연결됐고 반영기간이 `period`와 같아야 한다. 생략하면 참고 기록 없이 저장하고 이전 참고 기록은 지운다. 다른 금액 항목에는 이 필드가 없다(알 수 없는 필드 400).
- `limitBucketUsages[].value`는 0 이상 정수다. `0`은 "사용한 혜택 없음"을 직접 입력한 값이다.
- 한도가 체인으로 묶인 서비스는 체인 안 버킷마다 사용액을 입력한다. 하나라도 `UNKNOWN`이면 그 서비스는 `확인 필요`다(D-16, D-18 K1).
- 입력 사용액이 한도보다 커도 저장은 한다. 한도는 실적 구간에 따라 달라 추천 때 판정한다. 제외 업종(`NOT_APPLICABLE_EXCLUDED`)·대상 업종·결제 방식·최소 금액·실적 미충족이 확인되거나, 택1 선택형 서비스에서 다른 서비스를 골랐거나 선택 없음이면(`NOT_APPLICABLE_SELECTION`) 사용액과 무관하게 확정 0원이다. 그렇지 않으면 추천에서 그 서비스는 `확인 필요`(사유 `USAGE_EXCEEDS_LIMIT`)이고, 사용액이 한도와 같으면 남은 한도 0이다(D-18 K1).
- 카드 규칙에 없는 버킷 ID는 400이다. 버킷을 나눠 입력할 수 없는 나머지 사용액은 `otherBucketsUsedWon`에 넣는다. 이 값으로 체인 버킷의 사용액을 대신하지 않는다.
- `selectedServices[].effectiveFrom`(적용일)이 없으면 그 서비스는 `확인 필요`다.
- 택1 선택은 세 상태다(D-18 K1). 둘 다 비우면 미입력(`UNKNOWN`), `selectedServices`에 항목을 보내면 서비스 선택(`SELECTED`), `noServiceSelection`에 `{"effectiveFrom":"2026-11-01"}`을 보내면 선택 없음(`NONE`)이다.
- `NONE`이면 택1 서비스는 그 거래에 적용되지 않음으로 보고 기본 서비스를 판정한다. `effectiveFrom`이 없거나 거래일보다 늦으면 `확인 필요`다.
- 실물 카드에 선택 없음 상태가 있는지는 상품설명서로 확인되지 않는다. `NONE`은 사용자 입력값이며 가상 포트폴리오·평가 사례용이다. 내 카드 모드 화면에서는 실제 선택을 입력받도록 안내한다.
- `registeredOn`은 `{"value":"2026-09-01","period":{"from":"2026-09-01","to":"2026-09-01"}}` 형식이다. 기준 기간은 등록일 하루로 둔다(D-23).
- `period`가 카드의 실적·한도 기간(D-03)과 다른지는 저장·조회 때 판단하지 않는다. 받은 값을 그대로 저장한다. 엔진이 계산 때 판단해 맞지 않으면 그 값을 쓰지 않고 추천에 `확인 필요`로 나타낸다(D-16, D-22).

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
      "referencedCodef": null,
      "codefReference": null
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
    "serviceSelectionStatus": "SELECTED",
    "noServiceSelection": null,
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
  - `limitBucketUsages[].value` 음수(`previousMonthPerformanceWon.value`는 음수를 받는다)
  - `selectedServices` 항목과 `noServiceSelection`을 함께 보냄(제안)
  - 선택형 서비스가 없는 카드에 `noServiceSelection`을 보냄(제안)
  - `referencedCodefId` 형식 오류(`형식이 올바르지 않습니다.`), 또는 본인 참고값이 아니거나 이 카드에 연결되지 않았거나 반영기간이 `period`와 다름(`참고할 수 없는 카드사 조회 실적입니다.`). 필드는 `previousMonthPerformanceWon.referencedCodefId`

- **404 `USER_CARD_NOT_FOUND`**: 없거나 다른 사용자의 카드

- **409 `USER_CARD_MAPPING_REQUIRED`**: 매핑이 확인되지 않은 카드
  - `limitBucketUsages`·`selectedServices`·`noServiceSelection`을 보냈는데 `USER_CONFIRMED`가 아닌 경우. 어떤 규칙의 버킷·서비스인지 알 수 없다. 전월 실적만 보내면 매핑 없이 저장한다(D-16). 가상 포트폴리오 카드는 규칙 카드 자체라 이 오류가 없다(D-15)

- **500 `INTERNAL_SERVER_ERROR`**: 서버 오류

### 명세 단계 미정

- 화면에서 묻는 순서와 필수·선택 구분(D-16).

### 구현 시 주의

- 엔진 입구는 음수 전월 실적을 받아 `확인 필요`로 처리합니다(D-22). D-17의 음수 구간 판정 규칙은 구현 전이며 판정기(`PerformanceTiers`)는 음수를 계속 거부하지만, 엔진이 그 전에 `UNKNOWN`으로 바꿉니다. 이 API는 음수를 그대로 저장하고 엔진에 넘깁니다.
- 엔진 입구는 `serviceSelectionStatus=NONE`과 서비스가 없는 선택 항목 하나로 선택 없음을 받습니다(`MonthlyStateInput`). 미입력은 `UNKNOWN`과 빈 목록입니다.
- `otherBucketsUsedWon`은 미입력이어도 엔진 입력에서 생략할 수 없습니다. 미입력이면 `StateInput.notEntered()`로 넘깁니다.
- `period`가 맞지 않다고 `usable=false`·`reason=PERIOD_MISMATCH`로 저장하거나 엔진에 넘기지 않습니다. 기간 판단은 엔진이 하며, 엔진은 `PERIOD_MISMATCH`를 받으면 계약 위반으로 예외를 던집니다(D-22).
- 모의 승인은 스냅샷 뒤 사용액으로 체인 안 모든 버킷에 더합니다. 모의 원장 기준이며 실제 카드사 차감 시점이 아닙니다.

### 근거

- 결정 기록 D-16·D-17, D-18 K1, D-19
- benefit 도메인 안내(CardMonthlyState, ServiceSelection)

## PUT /api/v1/me/cards/{userCardId}/monthly-states/{month}/source-selections — 출처 충돌 계산 기준 선택

- [2차] 같은 항목·같은 기준 기간에 출처가 다른 값이 충돌할 때 사용자가 계산 기준을 고른다. 자동 우선순위는 없다(D-16).

월간 상태 값의 출처 충돌을 사용자가 해소하는 API. 선택은 그 값 하나에만 적용한다.

충돌은 같은 카드·같은 항목·같은 기준 기간에 출처가 다른 값이 서로 다를 때만 생깁니다. 값이 같거나, 기준 기간이 다르거나, `UNKNOWN`이거나, 사용자가 참고해 입력한 MANUAL 값과 그 CODEF 값이면 충돌이 아닙니다. #281부터 전월 실적과 CODEF 실적 참고값의 충돌이 「월간 상태 조회」 `conflicts[]`에 나옵니다.

> 현재 구현은 제한 구현입니다. 요청 검증 400·카드 404 뒤 항상 409 `SOURCE_CONFLICT_NOT_FOUND`로 끝나고, 선택을 저장하지 않습니다. 아래 200 응답은 그 뒤에 구현할 형식입니다(#135, 사용자 확인 결정 32 Q1, 2026-11-18). 전월 실적과 CODEF 참고값의 충돌(#281)도 이 API로 고르지 않고, 최신 참고값을 보고 월간 상태를 `PUT`으로 다시 저장해 해소합니다(사용자 결정 2026-11-27 Q3). CODEF 값을 계산 기준으로 고르면 D-16의 "자동 반영 안 함"과 맞지 않기 때문입니다.

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
        "period": {
          "from": "2026-10-01",
          "to": "2026-10-31"
        },
        "updatedAt": "2026-11-03T10:20:00+09:00"
      },
      {
        "source": "INFERRED",
        "value": 498000,
        "period": {
          "from": "2026-10-01",
          "to": "2026-10-31"
        },
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
