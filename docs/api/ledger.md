# 원장

모의 승인·취소 원장과 기간별 완전성을 다룹니다. 원장에서 월간 상태를 자동 산출하지 않습니다(D-16).

공통 규약과 공통 실패 응답은 [`docs/api.md`](../api.md#업무-api-공통-규약)를, 전체 목록은 [README](README.md)를 봅니다. JSON은 합성 예시이며 실제 API 실행 결과나 약관 검증 결과가 아닙니다. 구현 상태는 [README](README.md#업무-api-1차-명세)의 상태 항목을 봅니다.

## GET /api/v1/me/ledger-transactions — 원장 조회

- [1차] 모의 승인·취소 원장과 기간별 완전성(불완전 사유)을 조회한다. 2차에서는 CODEF 원장 행도 출처를 구분해 함께 보여 준다.

카드·월별 원장 거래와, 그 기간 원장이 완전한지를 조회하는 API.

프로필: 이 API는 `local`·`demo`와 로컬 회원 프로필 `member`에 등록돼 있습니다(#165). 아래 `X-BeONE-Demo-User` 헤더는 `local`·`demo`의 방식입니다. `member`에서는 헤더 대신 회원 세션 쿠키(`__Host-BEONE_SESSION`)로 현재 사용자를 정하고, 검사 순서는 401 `UNAUTHENTICATED` → 400 → 404이며 모든 응답에 `Cache-Control: no-store`가 붙습니다([회원 세션 규칙](../api.md#회원-세션member-프로필)). 원장은 회원 장부마다 나뉘고, 다른 회원·시연 장부의 카드로 거르면 없는 카드와 같은 404 `USER_CARD_NOT_FOUND`입니다. `member`에는 CODEF 경로가 없어 CODEF 행이 생기지 않습니다(저장된 CODEF 승인 행은 출처 `CODEF`로 함께 보여 줍니다, #289). 모의 결제 API는 N-G3-2·D8 결정 대기로 `member`에 등록하지 않았으므로, 지금 `member` 원장에는 API로 행이 생기지 않습니다.

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
- `ledgerTransactionId` · 모의 행 `lt-{번호}`, CODEF 행 `car-{번호}`(#289)
- `syncId` · String|null · CODEF 행을 처음 저장한 동기화(`sync-` 형식, CODEF 동기화 응답의 `syncId`). 모의 행은 `null`(#289)
- `fullyCanceled` · Boolean|null · CODEF 승인 행이 전액 취소(Type1, 승인과 취소가 한 행)인지. 행을 지어내지 않고 한 항목 `entryType=APPROVAL`로 둔다. 모의 행은 `null`(#289)
- CODEF 행(#289, D-16 변경 이력 2026-11-27 T4): 저장된 CODEF 승인 행을 출처 `CODEF`로 함께 담는다. 사용일자가 그 달인 행이고, 같은 날 안에서는 모의 행 뒤에 사용일시 순이다. 카드번호·승인번호는 저장하지 않아 담지 않고, `merchantClass`·`paymentMethod`·`transactionNature`·`merchantName`·`originalTransactionId`·`simulatedPaymentId`는 `null`이다. `transactionTime`은 사용일시다. 승인의 `attributionMonth`는 사용월 `CONFIRMED`, 짝지은 취소(Type2, `entryType=CANCELLATION`, 음수)는 `value=null`·`NEEDS_CONFIRMATION`이다(D-17)
- `coverage[]` · 카드·출처별 완전성(제안). `source`를 생략하면 출처마다 한 항목씩 담고, 출처를 합쳐 계산하지 않는다. `source`를 지정하면 그 출처만 담는다. 카드를 거르지 않았고 CODEF를 포함하며 보유 카드에 붙지 않은 CODEF 불완전 행이 있으면 끝에 `userCardId=null`·`source=CODEF`·`complete=false` 항목 하나를 더한다(특정 카드를 불완전으로 만들지 않음, #289)
- `coverage[].incompleteReasons[]` · CODEF: `PARTIAL_CANCELLATION_SUSPECTED` / `NO_MATCHING_APPROVAL` / `MULTIPLE_MATCHING_APPROVALS` / `UNDETERMINED_ROW` / `DUPLICATE_KEY_AMBIGUOUS`(동기화 판정 사유, 해외·원화 아님·판단 불가 행 포함) / `PERIOD_NOT_COVERED`(#289, 승인 판정을 마친 동기화가 그 달 전체를 덮지 않음). 해당 카드·해당 기간에만 표시한다
- CODEF 완전성(#289): CODEF로 만든 보유 카드는 승인 판정을 마친 동기화가 그 달 전체를 조회일 전날까지 덮고(이번 달은 다음 달 동기화 전까지 항상 불완전) 그 카드·그 달(또는 사용월 미상) 불완전 행이 없을 때만 `complete=true`다. 그 달 전체를 덮는 가장 최근 동기화가 있으면 그것과 그 뒤 동기화만 본다. V18 전 동기화와 승인 조회에 실패한 동기화는 쓰지 않는다. 수동 카드는 CODEF 행이 생기지 않아 판단하지 않고 `complete=false`, `incompleteReasons=[]`다. `SIMULATION`은 `complete=true`다. 자세한 내용은 [ledger 도메인 문서](../domains/ledger.md#조회-규칙)를 본다
- 원장 조회는 원장 행으로 전월 실적·버킷 사용액을 산출하지 않는다. 전월 승인 합계 추정값은 [월간 상태](monthly-states.md)의 `inferredReference`가 보여 주며 계산에 쓰지 않는다(D-16)

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
