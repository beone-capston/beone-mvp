# 원장

모의 승인·취소 원장과 기간별 완전성을 다룹니다. 원장에서 월간 상태를 자동 산출하지 않습니다(D-16).

공통 규약과 공통 실패 응답은 [`docs/api.md`](../api.md#업무-api-공통-규약)를, 전체 목록은 [README](README.md)를 봅니다. JSON은 합성 예시이며 실제 API 실행 결과나 약관 검증 결과가 아닙니다. 구현 상태는 [README](README.md#업무-api-1차-명세)의 상태 항목을 봅니다.

## GET /api/v1/me/ledger-transactions — 원장 조회

- [1차] 모의 승인·취소 원장과 기간별 완전성(불완전 사유)을 조회한다. 2차에서는 CODEF 원장 행도 출처를 구분해 함께 보여 준다.

카드·월별 원장 거래와, 그 기간 원장이 완전한지를 조회하는 API.

프로필: 이 API는 `local`·`demo`와 로컬 회원 프로필 `member`에 등록돼 있습니다(#165). 아래 `X-BeONE-Demo-User` 헤더는 `local`·`demo`의 방식입니다. `member`에서는 헤더 대신 회원 세션 쿠키(`__Host-BEONE_SESSION`)로 현재 사용자를 정하고, 검사 순서는 401 `UNAUTHENTICATED` → 400 → 404이며 모든 응답에 `Cache-Control: no-store`가 붙습니다([회원 세션 규칙](../api.md#회원-세션member-프로필)). 원장은 회원 장부마다 나뉘고, 다른 회원·시연 장부의 카드로 거르면 없는 카드와 같은 404 `USER_CARD_NOT_FOUND`입니다. `member`에는 CODEF 경로가 없고 CODEF 원장도 연결하지 않았습니다(R8). 모의 결제 API는 N-G3-2·D8 결정 대기로 `member`에 등록하지 않았으므로, 지금 `member` 원장에는 API로 행이 생기지 않습니다.

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
- 현재 구현: `SIMULATION`은 `complete=true`다. CODEF 동기화는 도메인 원장에 행을 넣지 않으므로(결정 R8 미연결 유지) `CODEF`는 행이 없고 `complete=false`, `incompleteReasons=[]`다. 위 2차 사유 값은 아직 채우지 않는다(사용자 확인, 결정 32 Q5, 2026-11-18). 자세한 내용은 [ledger 도메인 문서](../domains/ledger.md#조회-규칙)를 본다
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
