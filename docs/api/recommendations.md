# 추천

추천 계산과 결과 조회를 다룹니다(D-12, D-13, D-15, D-18). 계산 상태는 HTTP 오류가 아니라 `data` 안에 담습니다.

공통 규약과 공통 실패 응답은 [`docs/api.md`](../api.md#업무-api-공통-규약)를, 전체 목록은 [README](README.md)를 봅니다. JSON은 합성 예시이며 실제 API 실행 결과나 약관 검증 결과가 아닙니다. 모든 API는 구현 전입니다.

## POST /api/v1/me/orders/{orderId}/recommendations — 추천 계산

- [1차] 주문에 대해 보유 카드별 혜택을 계산하고 추천 카드, 조건부 여부, 비교·제외 카드와 이유를 반환한다(D-15, D-18).

현재 주문과 카드별 월간 상태로 카드마다 현재 주문 혜택과 월간 합계를 계산하고, 동률 규칙(D-12)으로 추천 카드를 고르는 API.

매핑이 `USER_CONFIRMED`이고 판정일에 `VERIFIED` 규칙이 있으며 금액이 확정된 카드끼리만 비교합니다. 일부 카드만 확정되면 확정 카드끼리 비교해 **조건부 추천**(`conditional=true`)을 냅니다. 확정 카드가 0장이면 `NOT_DETERMINED`입니다(D-18 K2). 계산 상태(확인 필요, 미충족 등)는 HTTP 오류가 아니라 `data` 안에 담습니다.

```text
보유 카드 중 매핑 USER_CONFIRMED 카드 선별 (나머지는 excludedCards)
→ 카드별 VERIFIED 규칙 선택(판정일)
→ 서비스별 후보·채택·기여액 결정, D-18 K1 범위의 월 한도는 입력 사용액 기준 남은 한도(체인 내 버킷별 최솟값)로 판정
→ 금액 확정 카드끼리 D-12 순서(월간 합계 → 현재 주문 혜택 → 대표 카드 → 카드 ID)로 선택
→ 확정 안 된 카드는 excludedCards + 이유(D-18 K3 순서)
```

### Request Header

```text
X-BeONE-Demo-User: MY_CARDS
```

### Path / Query Parameter

- `orderId` · Path · String · 필수

### Request Body

없음.

### Response Body

성공: **201 Created**

```json
{
  "data": {
    "recommendationId": "rec-3001",
    "orderId": "ord-5001",
    "calculatedAt": "2026-11-03T10:41:00+09:00",
    "decisionDate": "2026-11-03",
    "selectionMode": "AUTO_RECOMMEND",
    "outcome": "SELECTED",
    "conditional": true,
    "selected": {
      "userCardId": "uc-1001",
      "cardProductId": "09174",
      "basis": {
        "decidedBy": "MONTHLY_TOTAL",
        "arbitraryTieBreak": false,
        "tiedUserCardIds": [],
        "gapToRunnerUpWon": null
      }
    },
    "comparedCards": [
      {
        "userCardId": "uc-1001",
        "cardProductId": "09174",
        "displayName": "청춘대로 톡톡",
        "mapping": {
          "status": "USER_CONFIRMED",
          "note": "사용자 확인 매핑, 공식 SKU·약관 버전 미검증"
        },
        "appliedRule": {
          "ruleVersion": "09174-v1",
          "verificationStatus": "VERIFIED",
          "officialSources": [
            {
              "uri": "https://img2.kbcard.com/obj/card/download/09174__prdctOpmn_20260714.pdf",
              "locator": "p.3 §2"
            }
          ]
        },
        "currentOrderBenefit": {
          "amountWon": 2100,
          "status": "CONFIRMED"
        },
        "monthlyTotalBenefit": {
          "amountWon": 2100,
          "status": "CONFIRMED"
        },
        "services": [
          {
            "serviceId": "SVC-GREAT",
            "applicationStatus": "APPLIED_CAP_REMAINING",
            "candidateWon": 5000,
            "contribution": {
              "amountWon": 2100,
              "status": "CONFIRMED"
            },
            "adoption": "ADOPTED",
            "limits": [
              {
                "limitBucketId": "LB-GREAT",
                "parentLimitBucketId": null,
                "limitWon": 10000,
                "usedBeforeWon": {
                  "value": 7900,
                  "source": "MANUAL"
                },
                "remainingAfterWon": 0
              }
            ],
            "limitReason": null,
            "usageBasis": "USER_ENTERED",
            "mayDifferFromActual": true,
            "usageNote": "입력한 사용액 기준, 미매입 전표에 따라 달라질 수 있음"
          },
          {
            "serviceId": "SVC-ENJOY",
            "applicationStatus": "NOT_APPLICABLE_CATEGORY",
            "candidateWon": 0,
            "contribution": {
              "amountWon": 0,
              "status": "CONFIRMED"
            },
            "adoption": "NOT_ADOPTED",
            "limits": [],
            "limitReason": null,
            "usageBasis": null,
            "mayDifferFromActual": false,
            "usageNote": null
          }
        ],
        "noBenefitReason": null,
        "policyNotes": [],
        "pendingReasons": []
      }
    ],
    "excludedCards": [
      {
        "userCardId": "uc-1003",
        "cardProductId": "01664",
        "reason": "NEEDS_CONFIRMATION",
        "detail": "전월 실적이 입력되지 않아 실적 조건을 판정할 수 없습니다.",
        "candidateCardProductId": null,
        "monthlyTotalBenefit": {
          "amountWon": null,
          "status": "NEEDS_CONFIRMATION"
        }
      },
      {
        "userCardId": "uc-1002",
        "cardProductId": null,
        "reason": "NO_SUPPORTED_RULE",
        "detail": "검증된 규칙 없음(지원 범위 밖)",
        "candidateCardProductId": null,
        "monthlyTotalBenefit": {
          "amountWon": null,
          "status": "NEEDS_CONFIRMATION"
        }
      }
    ],
    "fixedCardComparison": null,
    "inputSnapshot": {
      "order": {
        "amountWon": 10000,
        "orderDate": "2026-11-03",
        "merchantClass": "STARBUCKS_STORE",
        "paymentMethod": "DIRECT"
      },
      "representativeUserCardId": null,
      "monthlyStateSnapshotAt": {
        "uc-1001": "2026-11-03T10:20:00+09:00"
      }
    },
    "notes": [
      "예정 소비는 이번 버전에서 월간 합계에 반영하지 않습니다(D-18 K4).",
      "비교에서 빠진 카드가 더 나을 수 있어 조건부 추천입니다."
    ]
  }
}
```

#### 응답 필드

- `outcome` · String · `SELECTED` / `NOT_DETERMINED`(비교 가능한 카드 0장, D-18 K2)
- `conditional` · Boolean · 비교에서 빠진 카드가 있으면 `true`. "최적"이라고 단정하지 않는다
- `selected.basis.decidedBy` · String · `MONTHLY_TOTAL` / `CURRENT_ORDER_BENEFIT` / `REPRESENTATIVE_CARD` / `CARD_ID_ASC`(코드 `SelectionBasis`). 뒤의 두 단계면 `arbitraryTieBreak=true`(혜택 우열이 아닌 임의 규칙, D-12)
- `selected.basis.gapToRunnerUpWon` · Integer|null · 차선 카드와 차액. 비교 카드가 1장이라 비교할 수 없으면 `null`이다. 이때 카드 간 최적 추천을 주장하지 않는다
- `comparedCards[].mapping.note` · 사용자 확인 매핑이면 "사용자 확인 매핑, 공식 SKU·약관 버전 미검증"(D-15)
- `comparedCards[].appliedRule` · 규칙 버전·검증 상태·공식 근거(D-14 원칙 5)
- `currentOrderBenefit` · `OrderBenefit` 값을 그대로 쓴다(D-18 K3)
- `monthlyTotalBenefit` · 1차에서는 현재 주문 기준이라 `currentOrderBenefit`과 같은 값이다. 예정 소비는 넣지 않는다(D-18 K4). 이번 달 상태는 잔여 한도와 실적 판정에 쓰고 합계에 더하지 않는다
- `services[].applicationStatus` · `APPLIED` / `APPLIED_CAP_REMAINING` / `LIMIT_EXHAUSTED` / `NOT_APPLICABLE_EXCLUDED` / `NOT_APPLICABLE_CATEGORY` / `NOT_APPLICABLE_PERFORMANCE` / `NOT_APPLICABLE_MIN_AMOUNT` / `NOT_APPLICABLE_PAYMENT_METHOD` / `NOT_APPLICABLE_SELECTION`(제안, 코드에 없음) / `RULE_EXPIRED` / `NO_VERIFIED_RULE` / `NEEDS_CONFIRMATION`
- `services[].candidateWon` · Integer|null · 서비스 단독 후보 금액. 기여액과 다를 수 있다. 미확정이면 `null`
- `services[].adoption` · `ADOPTED` / `NOT_ADOPTED` / `UNDETERMINED`(코드 `Adoption`). 중복 합산 예외(D-13 #35 8번)에서는 합산한 서비스가 모두 `ADOPTED`이고 카드 금액은 기여액의 합이다. 각 서비스를 할인 전 금액으로 계산해 서비스별로 절사하고(D-03) 자기 체인의 남은 한도로 줄인다. 합계가 결제 금액을 넘으면 `NEEDS_CONFIRMATION`이다
- `services[].contribution` · 실제 기여액. 미확정이면 `amountWon=null`, `status=NEEDS_CONFIRMATION`
- `services[].limits[]` · Array · 서비스가 속한 한도 버킷과 그 상위 버킷(체인)마다 한 항목. 한도·입력 사용액(출처 포함)·주문 뒤 남은 한도를 담는다. 한도가 없으면 `[]`다. 남은 한도는 체인 내 버킷별 (한도 − 입력 사용액)의 최솟값으로 판정한다(D-18 K1)
  - 정률 혜택: 남은 한도로 줄였으면 `APPLIED_CAP_REMAINING`
  - 정액 혜택: 남은 한도가 0이면 확정 0원(`LIMIT_EXHAUSTED`), 0보다 크고 정액보다 적으면 `NEEDS_CONFIRMATION`
  - 체인 안 버킷 중 하나라도 사용액이 `UNKNOWN`이면 그 서비스는 `NEEDS_CONFIRMATION`이고 `remainingAfterWon=null`이다. 확정 0원은 체인 안 모든 버킷의 사용액이 입력되어 있을 때만이다
  - 사용액이 한도와 같으면 남은 한도 0으로 판정한다
- `services[].limitReason` · String|null · `USAGE_EXCEEDS_LIMIT`(제안). 체인 안 어느 버킷이든 입력 사용액이 한도보다 크면 입력이 규칙과 모순되므로 그 서비스는 `NEEDS_CONFIRMATION`이고 `amountWon=null`, `remainingAfterWon=null`이다. 0원 확정이나 음수 혜택으로 만들지 않는다. 같은 사유를 `pendingReasons[]`에 `NEEDS_CONFIRMATION`으로 담는다
- `services[].usageBasis` · String|null · 제안. 한도가 있으면 `USER_ENTERED`, 없으면 `null`
- `services[].mayDifferFromActual` · Boolean · 제안. 한도가 있는 모든 서비스 결과(0원 결과 포함)에서 `true`다. 실제 카드사 차감과 다를 수 있다는 표시다
- `services[].usageNote` · String|null · 한도가 있으면 "입력한 사용액 기준, 미매입 전표에 따라 달라질 수 있음"을 그대로 표시한다
- 택1 선택형 서비스(D-18 K1 U2 ① 예외): 선택형은 계속 범위 밖이다. 거래 업종이 확정되고 그 거래에 적용되지 않음이 확정되면 카드 금액을 막는 사유에서 뺀다
  - 대상 업종 불일치: `NOT_APPLICABLE_CATEGORY`
  - 다른 서비스를 선택했거나 `serviceSelectionStatus=NONE`: `NOT_APPLICABLE_SELECTION`(제안)
  - 그 밖의 경우(`UNKNOWN`, 업종 미확정, 적용일 없음, 선택 서비스가 그 거래에 적용됨): `NEEDS_CONFIRMATION`
- `excludedCards[].reason` · 카드 사유 우선순위(D-18 K3): `RULE_UNAVAILABLE` > `MAPPING_UNCONFIRMED` > `NEEDS_CONFIRMATION` > `UNSUPPORTED`. 사용자가 4종이 아니라고 지정한 카드는 `NO_SUPPORTED_RULE`, 사용자가 추천에서 뺀 카드는 `USER_EXCLUDED`. 이 값은 K3 순서에 넣지 않고 "사용자 제외"로 따로 표시한다(D-19)
- `excludedCards[].candidateCardProductId` · `MAPPING_UNCONFIRMED`일 때 확인할 수 있는 후보(D-15)
- `comparedCards[].noBenefitReason` · String|null · 금액이 확정 0원인 비교 카드의 사유. `CONFIRMED_NOT_MET`(K3 순서의 마지막)이며, 적용 서비스가 있으면 `null`
- `comparedCards[].policyNotes[]` · 약관 근거 없는 사용자 결정을 적용했을 때의 표시. 예: 음수 전월 실적 판정의 "약관 근거 없음, 사용자 결정"(D-17), 원 미만을 절사한 경우의 "약관 근거 없음, 사용자 결정"(D-03)
- `fixedCardComparison` · Object|null · 대표 카드 고정일 때 월간 최적 카드와의 비교(PRD §4.1). `representativeUserCardId`, `optimalUserCardId`, `differenceWon`. 비교할 수 없으면 `optimalUserCardId`·`differenceWon`이 `null`이다. 자동 추천이면 객체 자체가 `null`
- `inputSnapshot` · 계산에 쓴 주문·대표 카드·월간 상태 스냅샷 시각. 같은 입력·규칙 버전의 재현을 확인하는 기록이다
- `conditional=false`여도 비교 카드가 1장이면 "최적"이라고 표시하지 않는다
- `pendingReasons[]` · 카드 금액을 확정하지 못한 사유(`NEEDS_CONFIRMATION`·`UNSUPPORTED`·`PERFORMANCE_POLICY`·`STACKING_UNRESOLVED`·`STACKING_CONFIRMED`(코드 `PendingReason.Kind`)). `STACKING_CONFIRMED`는 중복 허용이 확정됐지만 합산 예외(D-13 #35 8번) 조건을 만족하지 않는 쌍에만 남는다

비교 가능한 카드가 0장인 응답(2026-11-03 기준 규칙 데이터가 없어 실제로는 모두 이 형태입니다):

```json
{
  "data": {
    "recommendationId": "rec-3003",
    "orderId": "ord-5001",
    "calculatedAt": "2026-11-03T10:41:00+09:00",
    "decisionDate": "2026-11-03",
    "selectionMode": "AUTO_RECOMMEND",
    "outcome": "NOT_DETERMINED",
    "conditional": false,
    "selected": null,
    "comparedCards": [],
    "excludedCards": [
      {
        "userCardId": "uc-1001",
        "cardProductId": "09174",
        "reason": "RULE_UNAVAILABLE",
        "detail": "판정일에 유효한 VERIFIED 규칙이 없습니다.",
        "candidateCardProductId": null,
        "monthlyTotalBenefit": {
          "amountWon": null,
          "status": "NEEDS_CONFIRMATION"
        }
      }
    ],
    "fixedCardComparison": null,
    "inputSnapshot": null,
    "notes": [
      "비교할 수 있는 카드가 없어 추천을 확정할 수 없습니다."
    ]
  }
}
```

### 실패 응답

- **404 `ORDER_NOT_FOUND`**: 없거나 다른 사용자의 주문

- **409 `ORDER_ALREADY_PAID`**: 이미 모의 승인된 주문
  - 결제된 주문으로는 새 추천을 만들지 않는다

- **500 `INTERNAL_SERVER_ERROR`**: 서버 오류

### 명세 단계 미정

- 판정일(`decisionDate`)을 주문일로 정하는 안은 [진행 기록](../progress/backend/README.md) 사용자 확인 7번(미결)입니다. 현재 예시는 주문일입니다.
- 카드 사유 순서는 화면 문구 확인 뒤 다시 볼 수 있습니다(D-18 K3).
- D-07 추천 2초 목표 측정 환경은 미결입니다.

### 근거

- 결정 기록 D-12·D-13·D-15, D-18
- benefit 도메인 안내(OrderBenefit, CardBenefit, Recommendation, SelectionBasis, ApplicationStatus)

## GET /api/v1/me/recommendations/{recommendationId} — 추천 결과·근거 조회

- [1차] 계산했던 추천 결과와 근거(입력 스냅샷 시각, 규칙 버전, 카드별 사유)를 다시 조회한다.

이미 계산한 추천 결과를 다시 조회하는 API. 다시 계산하지 않는다.

### Request Header

```text
X-BeONE-Demo-User: MY_CARDS
```

### Path / Query Parameter

- `recommendationId` · Path · String · 필수

### Request Body

없음.

### Response Body

성공: **200 OK**

```json
{
  "data": {
    "recommendationId": "rec-3001",
    "orderId": "ord-5001",
    "calculatedAt": "2026-11-03T10:41:00+09:00",
    "decisionDate": "2026-11-03",
    "selectionMode": "AUTO_RECOMMEND",
    "outcome": "SELECTED",
    "conditional": true,
    "selected": {
      "userCardId": "uc-1001",
      "cardProductId": "09174",
      "basis": {
        "decidedBy": "MONTHLY_TOTAL",
        "arbitraryTieBreak": false,
        "tiedUserCardIds": [],
        "gapToRunnerUpWon": null
      }
    },
    "comparedCards": [
      {
        "userCardId": "uc-1001",
        "cardProductId": "09174",
        "displayName": "청춘대로 톡톡",
        "mapping": {
          "status": "USER_CONFIRMED",
          "note": "사용자 확인 매핑, 공식 SKU·약관 버전 미검증"
        },
        "appliedRule": {
          "ruleVersion": "09174-v1",
          "verificationStatus": "VERIFIED",
          "officialSources": [
            {
              "uri": "https://img2.kbcard.com/obj/card/download/09174__prdctOpmn_20260714.pdf",
              "locator": "p.3 §2"
            }
          ]
        },
        "currentOrderBenefit": {
          "amountWon": 2100,
          "status": "CONFIRMED"
        },
        "monthlyTotalBenefit": {
          "amountWon": 2100,
          "status": "CONFIRMED"
        },
        "services": [
          {
            "serviceId": "SVC-GREAT",
            "applicationStatus": "APPLIED_CAP_REMAINING",
            "candidateWon": 5000,
            "contribution": {
              "amountWon": 2100,
              "status": "CONFIRMED"
            },
            "adoption": "ADOPTED",
            "limits": [
              {
                "limitBucketId": "LB-GREAT",
                "parentLimitBucketId": null,
                "limitWon": 10000,
                "usedBeforeWon": {
                  "value": 7900,
                  "source": "MANUAL"
                },
                "remainingAfterWon": 0
              }
            ],
            "limitReason": null,
            "usageBasis": "USER_ENTERED",
            "mayDifferFromActual": true,
            "usageNote": "입력한 사용액 기준, 미매입 전표에 따라 달라질 수 있음"
          },
          {
            "serviceId": "SVC-ENJOY",
            "applicationStatus": "NOT_APPLICABLE_CATEGORY",
            "candidateWon": 0,
            "contribution": {
              "amountWon": 0,
              "status": "CONFIRMED"
            },
            "adoption": "NOT_ADOPTED",
            "limits": [],
            "limitReason": null,
            "usageBasis": null,
            "mayDifferFromActual": false,
            "usageNote": null
          }
        ],
        "noBenefitReason": null,
        "policyNotes": [],
        "pendingReasons": []
      }
    ],
    "excludedCards": [
      {
        "userCardId": "uc-1003",
        "cardProductId": "01664",
        "reason": "NEEDS_CONFIRMATION",
        "detail": "전월 실적이 입력되지 않아 실적 조건을 판정할 수 없습니다.",
        "candidateCardProductId": null,
        "monthlyTotalBenefit": {
          "amountWon": null,
          "status": "NEEDS_CONFIRMATION"
        }
      },
      {
        "userCardId": "uc-1002",
        "cardProductId": null,
        "reason": "NO_SUPPORTED_RULE",
        "detail": "검증된 규칙 없음(지원 범위 밖)",
        "candidateCardProductId": null,
        "monthlyTotalBenefit": {
          "amountWon": null,
          "status": "NEEDS_CONFIRMATION"
        }
      }
    ],
    "fixedCardComparison": null,
    "inputSnapshot": {
      "order": {
        "amountWon": 10000,
        "orderDate": "2026-11-03",
        "merchantClass": "STARBUCKS_STORE",
        "paymentMethod": "DIRECT"
      },
      "representativeUserCardId": null,
      "monthlyStateSnapshotAt": {
        "uc-1001": "2026-11-03T10:20:00+09:00"
      }
    },
    "notes": [
      "예정 소비는 이번 버전에서 월간 합계에 반영하지 않습니다(D-18 K4).",
      "비교에서 빠진 카드가 더 나을 수 있어 조건부 추천입니다."
    ],
    "invalidated": false,
    "invalidatedReason": null
  }
}
```

#### 응답 필드

- 형식은 「추천 계산」과 같다
- 이 조회는 과거 입력과 규칙 버전의 재현 기록이다. 승인 직전 검증을 대신하지 않는다
- `invalidated` · Boolean · 매핑 변경·월간 상태 재입력 등으로 이 결과를 승인 근거로 쓸 수 없게 됐는지
- `invalidatedReason` · String|null · `MAPPING_CHANGED` / `MONTHLY_STATE_CHANGED` / `ORDER_PAID`

### 실패 응답

- **404 `RECOMMENDATION_NOT_FOUND`**: 없거나 다른 사용자의 추천

- **500 `INTERNAL_SERVER_ERROR`**: 서버 오류

### 근거

- 결정 기록 D-14·D-15
