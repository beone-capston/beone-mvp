# 원장

모의 승인·취소 원장과 기간별 완전성을 다룹니다. 원장에서 월간 상태를 자동 산출하지 않습니다(D-16).

공통 규약과 공통 실패 응답은 [`docs/api.md`](../api.md#업무-api-공통-규약)를, 전체 목록은 [README](README.md)를 봅니다. JSON은 합성 예시이며 실제 API 실행 결과나 약관 검증 결과가 아닙니다. 모든 API는 구현 전입니다.

## GET /api/v1/me/ledger-transactions — 원장 조회

- [1차] 모의 승인·취소 원장과 기간별 완전성(불완전 사유)을 조회한다. 2차에서는 CODEF 원장 행도 출처를 구분해 함께 보여 준다.

카드·월별 원장 거래와, 그 기간 원장이 완전한지를 조회하는 API.

### Request Header

```text
X-BeONE-Demo-User: MY_CARDS
```

### Path / Query Parameter

- `month` · Query · String(YYYY-MM) · 필수
- `userCardId` · Query · String · 선택 · 생략하면 전체 카드
- `source` · Query · String · 선택 · `SIMULATION` / `CODEF`

### Request Body

없음.

### Response Body

성공: **200 OK**

```json
{
  "data": {
    "month": "2026-11",
    "items": [
      {
        "ledgerTransactionId": "lt-9001",
        "userCardId": "uc-1001",
        "source": "SIMULATION",
        "entryType": "APPROVAL",
        "amountWon": 10000,
        "transactionDate": "2026-11-03",
        "transactionTime": null,
        "merchantClass": "STARBUCKS_STORE",
        "paymentMethod": "DIRECT",
        "transactionNature": null,
        "merchantName": "스타벅스 송도점",
        "originalTransactionId": null,
        "simulatedPaymentId": "pay-7001",
        "attributionMonth": {
          "value": "2026-11",
          "status": "CONFIRMED"
        }
      },
      {
        "ledgerTransactionId": "lt-9002",
        "userCardId": "uc-1001",
        "source": "SIMULATION",
        "entryType": "CANCELLATION",
        "amountWon": -4000,
        "transactionDate": "2026-11-03",
        "transactionTime": null,
        "merchantClass": "STARBUCKS_STORE",
        "paymentMethod": "DIRECT",
        "transactionNature": null,
        "merchantName": "스타벅스 송도점",
        "originalTransactionId": "lt-9001",
        "simulatedPaymentId": "pay-7001",
        "attributionMonth": {
          "value": "2026-11",
          "status": "CONFIRMED"
        }
      }
    ],
    "coverage": [
      {
        "userCardId": "uc-1001",
        "source": "SIMULATION",
        "period": {
          "from": "2026-11-01",
          "to": "2026-11-30"
        },
        "complete": true,
        "incompleteReasons": []
      }
    ]
  }
}
```

#### 응답 필드

- `transactionTime` · 시각이 없으면 `null`이다. 00:00을 만들어 넣지 않는다
- `transactionNature` · `ORDINARY` / `TAX` / `null`. 코드 `LedgerTransaction`의 선택 값이다. 1차 모의 거래는 받지 않아 `null`이다
- `amountWon` · 승인은 양수, 취소는 음수. 음수라는 사실만으로 취소로 판단하지 않고 `entryType`을 본다
- `attributionMonth.status` · `CONFIRMED` / `NEEDS_CONFIRMATION`. 2차 CODEF 취소 행은 접수일이 없어 `NEEDS_CONFIRMATION`(D-17)
- `coverage[]` · 카드·출처별 완전성(제안). `source`를 생략하면 출처마다 한 항목씩 담고, 출처를 합쳐 계산하지 않는다. `source`를 지정하면 그 출처만 담는다
- `coverage[].incompleteReasons[]` · 2차 CODEF: `PARTIAL_CANCELLATION_SUSPECTED` / `NO_MATCHING_APPROVAL` / `MULTIPLE_MATCHING_APPROVALS` / `UNDETERMINED_ROW` / `DUPLICATE_KEY_AMBIGUOUS`. 해당 카드·해당 기간에만 표시한다
- 1차에서 원장 행으로 전월 실적·버킷 사용액을 자동 산출하지 않는다(D-16)

### 실패 응답

- **400 `INVALID_REQUEST`**: 요청값이 올바르지 않은 경우
  - `month` 누락·형식 오류

- **404 `USER_CARD_NOT_FOUND`**: 없거나 다른 사용자의 카드

- **500 `INTERNAL_SERVER_ERROR`**: 서버 오류

### 명세 단계 미정

- 페이지 나눔(기존 초안의 `cursor`·`limit`). 1차는 달 단위 조회로 건수가 작아 넣지 않았다. 필요하면 응답 형식을 바꾸지 않고 추가한다.

### 근거

- 결정 기록 D-16·D-17
- benefit 도메인 안내(Ledger, LedgerCoverage, LedgerEntryType)
