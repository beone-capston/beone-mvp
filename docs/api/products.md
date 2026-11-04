# 카드 상품

규칙 카드 4종과 규칙 요약을 다룹니다. 규칙 데이터는 저장소의 JSON 리소스를 읽어 `RuleCatalog`에 넘길 계획입니다. JSON을 읽는 운영 코드는 아직 없습니다(DB 시드는 나중에 정합니다).

공통 규약과 공통 실패 응답은 [`docs/api.md`](../api.md#업무-api-공통-규약)를, 전체 목록은 [README](README.md)를 봅니다. JSON은 합성 예시이며 실제 API 실행 결과나 약관 검증 결과가 아닙니다. 모든 API는 구현 전입니다.

## GET /api/v1/card-products — 카드 상품 목록 조회

- [1차] 규칙 카드 4종과 규칙 검증 상태·공식 출처를 조회한다. 보유 카드 매핑 선택지로도 쓴다.

BeONE이 계산을 지원하는 카드 상품(규칙 카드 4종)과 각 상품의 규칙 검증 상태를 조회하는 API.

보유 카드를 수동 등록하거나 매핑을 확인할 때 이 목록에서 상품을 고릅니다(D-15). 판정일에 유효한 `VERIFIED` 규칙이 없는 상품도 목록에는 나오고 `ruleStatus`로 구분합니다.

### Request Header

```text
(헤더 없음)
```

- 상품은 사용자 자원이 아니므로 `X-BeONE-Demo-User`가 필요 없습니다.

### Path / Query Parameter

- `asOf` · Query · Date(YYYY-MM-DD) · 선택 · 규칙 유효성 판단 날짜. 생략하면 서버의 오늘(Asia/Seoul)

### Request Body

없음.

### Response Body

성공: **200 OK**

```json
{
  "data": {
    "asOf": "2026-11-03",
    "items": [
      {
        "cardProductId": "09174",
        "cardName": "청춘대로 톡톡카드",
        "issuer": "KB국민카드",
        "cardType": "CREDIT",
        "ruleStatus": "VERIFIED",
        "ruleVersion": "09174-v1",
        "officialSources": [
          {
            "uri": "https://img2.kbcard.com/obj/card/download/09174__prdctOpmn_20260714.pdf",
            "locator": "p.3 §2"
          }
        ]
      },
      {
        "cardProductId": "01664",
        "cardName": "노리 체크카드",
        "issuer": "KB국민카드",
        "cardType": "CHECK",
        "ruleStatus": "NO_VERIFIED_RULE",
        "ruleVersion": null,
        "officialSources": []
      }
    ]
  }
}
```

#### 응답 필드

- `cardProductId` · String · 선행 0을 보존하는 공식 상품 식별자. 숫자로 바꾸지 않는다
- `ruleStatus` · String · `VERIFIED` / `RULE_EXPIRED` / `NO_VERIFIED_RULE`. 계산에는 `VERIFIED`만 쓴다
- `officialSources` · Array · 규칙의 공식 근거(`uri`·`locator`, 코드 `OfficialSource`). 규칙이 없으면 `[]`
- `VERIFIED` 예시는 형식 예시입니다. 2026-11-03 기준 저장소에 `VERIFIED` 규칙 데이터가 없으므로 실제 응답은 모두 `NO_VERIFIED_RULE`입니다. 공식 SKU·약관 버전 일치는 D-01 미결입니다

### 실패 응답

- **400 `INVALID_REQUEST`**: 요청값이 올바르지 않은 경우
  - `asOf` 형식 오류

- **500 `INTERNAL_SERVER_ERROR`**: 서버 오류

### 근거

- 결정 기록 D-01·D-14·D-15
- benefit 도메인 안내(RuleCatalog, VerificationStatus)

## GET /api/v1/card-products/{cardProductId} — 카드 상품 상세·규칙 요약 조회

- [1차] 상품의 판정일 기준 규칙 버전, 서비스별 조건·한도와 계산 지원 범위(D-18 K1)를 조회한다.

카드 상품 하나의 규칙 버전과 서비스별 조건·한도, 그리고 BeONE이 그 서비스를 계산할 수 있는지를 조회하는 API.

K1(2026-11-03 R2 변경)에 따라 한도 서비스는 비선택형이고, 속한 한도 버킷과 그 상위 버킷(체인)의 기간이 모두 월 1일~말일로 확정된 경우 입력 사용액 기준으로 계산합니다. 통합 한도와 매입 순서를 명시한 카드를 포함합니다. 그 밖의 서비스는 `supportStatus`로 범위 밖임을 보여 줍니다.

### Request Header

```text
(헤더 없음)
```

- 상품은 사용자 자원이 아니므로 `X-BeONE-Demo-User`가 필요 없습니다.

### Path / Query Parameter

- `cardProductId` · Path · String · 필수 · 예: `09174`
- `asOf` · Query · Date · 선택 · 생략하면 오늘

### Request Body

없음.

### Response Body

성공: **200 OK**

```json
{
  "data": {
    "cardProductId": "09174",
    "cardName": "청춘대로 톡톡카드",
    "asOf": "2026-11-03",
    "ruleStatus": "VERIFIED",
    "rule": {
      "ruleVersion": "09174-v1",
      "verificationStatus": "VERIFIED",
      "validFrom": "2026-01-01",
      "validTo": null,
      "performanceWindow": "PREVIOUS_CALENDAR_MONTH",
      "cancellationAttribution": null,
      "limitDeductionOrder": "APPROVAL_ORDER"
    },
    "services": [
      {
        "serviceId": "SVC-ENJOY",
        "name": "Enjoy 청구할인",
        "benefitKind": "BILLING_DISCOUNT",
        "supportStatus": "SUPPORTED",
        "limitBucketIds": [],
        "requiresSelection": false
      },
      {
        "serviceId": "SVC-GREAT",
        "name": "Great 청구할인",
        "benefitKind": "BILLING_DISCOUNT",
        "supportStatus": "SUPPORTED_SINGLE_MONTHLY_LIMIT",
        "limitBucketIds": [
          "LB-GREAT"
        ],
        "requiresSelection": false
      },
      {
        "serviceId": "SVC-SHARED",
        "name": "통합 한도 서비스",
        "benefitKind": "BILLING_DISCOUNT",
        "supportStatus": "SUPPORTED_SINGLE_MONTHLY_LIMIT",
        "limitBucketIds": [
          "LB-TOTAL"
        ],
        "requiresSelection": false
      }
    ],
    "limitBuckets": [
      {
        "limitBucketId": "LB-GREAT",
        "tiers": [
          {
            "minimumPerformanceWon": 300000,
            "monthlyCapWon": 10000
          }
        ],
        "parentLimitBucketId": null,
        "period": "CALENDAR_MONTH",
        "sharedByServiceIds": [
          "SVC-GREAT"
        ]
      },
      {
        "limitBucketId": "LB-TOTAL",
        "tiers": [
          {
            "minimumPerformanceWon": 300000,
            "monthlyCapWon": 20000
          }
        ],
        "parentLimitBucketId": null,
        "period": "CALENDAR_MONTH",
        "sharedByServiceIds": [
          "SVC-SHARED"
        ]
      }
    ]
  }
}
```

#### 응답 필드

- `ruleStatus` · String · `VERIFIED` / `RULE_EXPIRED` / `NO_VERIFIED_RULE`
- `rule` · Object|null · 판정일에 유효한 `VERIFIED` 규칙. 없으면 `null`이고 `ruleStatus`로 이유를 준다
- `supportStatus` · String · `SUPPORTED`(한도 없는 비선택형) / `SUPPORTED_SINGLE_MONTHLY_LIMIT`(D-18 K1 지원 한도. 이름과 달리 통합 한도와 매입 순서 명시 카드를 포함한다) / `UNSUPPORTED_LIMIT_PERIOD`(기간 미확정) / `UNSUPPORTED_SELECTION_RULE`(선택형). 범위 밖 서비스는 계산하지 않고 `확인 필요`로 둔다
- `limitDeductionOrder` · String|null · 규칙에 기록한 차감 순서. 단일 주문 계산에는 쓰지 않는다. 순서 조항이 없거나 뜻이 모호하면 `null`이고 확인 필요다(D-18)
- `cancellationAttribution` · String|null · 확정된 취소 귀속 모델(`RECEIPT_MONTH`). 미확정이면 `null`(D-17). 예시의 톡톡은 귀속월 미확정이다
- `limitBuckets[].tiers[]` · 전월 실적 구간별 월 한도(코드 `LimitTier`). `parentLimitBucketId`는 통합 한도의 상위 묶음
- 서비스·한도 ID와 금액은 예시입니다

### 실패 응답

- **404 `CARD_PRODUCT_NOT_FOUND`**: 없는 상품 ID

- **400 `INVALID_REQUEST`**: 요청값이 올바르지 않은 경우
  - `asOf` 형식 오류

- **500 `INTERNAL_SERVER_ERROR`**: 서버 오류

### 근거

- 결정 기록 D-03·D-17, D-18 K1
- benefit 도메인 안내(CardRuleVersion, BenefitService)
