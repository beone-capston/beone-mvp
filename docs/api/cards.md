# 사용자와 보유 카드

현재 사용자와 보유 카드, 상품 매핑(D-09, D-15)을 다룹니다.

공통 규약과 공통 실패 응답은 [`docs/api.md`](../api.md#업무-api-공통-규약)를, 전체 목록은 [README](README.md)를 봅니다. JSON은 합성 예시이며 실제 API 실행 결과나 약관 검증 결과가 아닙니다. 모든 API는 구현 전입니다.

## GET /api/v1/demo-users — 시연 사용자 목록 조회

- [1차] 시연 화면의 사용자 전환 목록을 조회한다. 로컬·시연 환경에서만 열린다(D-09).

시연 화면에서 "내 카드"와 "가상 포트폴리오" 사용자를 바꿀 수 있도록 고정 시연 사용자 목록을 조회하는 API.

MVP는 로그인이 없고 시드에 넣은 고정 사용자 2명만 있습니다(D-09). 사용자 전환은 이후 모든 요청의 `X-BeONE-Demo-User` 헤더 값으로 합니다. 이 API는 그 값과 화면 표시 이름을 알려 줍니다.

```text
앱/WebView 시작
→ GET /api/v1/demo-users
→ 사용자 선택(기본 선택은 프런트가 MY_CARDS로 둔다)
→ 이후 모든 요청에 X-BeONE-Demo-User 헤더 전송
```

> 이 API와 헤더 전환은 `local`·`demo` 프로필에서만 켭니다. 다른 환경에서는 404를 반환합니다. 세션 로그인을 붙이면 이 API와 헤더를 없애고 `CurrentUserResolver` 구현만 바꿉니다(D-09).

### Request Header

```text
(헤더 없음)
```

### Request Body

없음.

### Response Body

성공: **200 OK**

```json
{
  "data": {
    "users": [
      {
        "demoUser": "MY_CARDS",
        "displayName": "내 카드",
        "portfolioType": "REAL",
        "readOnly": false
      },
      {
        "demoUser": "VIRTUAL_PORTFOLIO",
        "displayName": "가상 포트폴리오",
        "portfolioType": "SYNTHETIC",
        "readOnly": true
      }
    ]
  }
}
```

#### 응답 필드

- `demoUser` · String · `X-BeONE-Demo-User`에 넣을 값
- `portfolioType` · String · `REAL`(실제 보유 카드, 수동·CODEF) / `SYNTHETIC`(시드 데이터)
- `readOnly` · Boolean · 가상 포트폴리오는 시드 데이터라 보유 카드·월간 상태를 수정할 수 없다

### 실패 응답

- **404 `NOT_FOUND`**: 시연 전환이 꺼진 환경(local·demo 외)

- **500 `INTERNAL_SERVER_ERROR`**: 서버 오류

### 명세 단계 미정

- 헤더 이름 `X-BeONE-Demo-User`와 값 이름은 제안입니다. 프런트 연동 전에 확정합니다.

### 근거

- 결정 기록 D-09
- 공통 API 응답·오류 처리(docs/api.md)

## GET /api/v1/me — 현재 사용자 조회

- [1차] 현재 요청의 시연 사용자와 포트폴리오 종류, 보유 카드 수를 조회한다.

헤더로 지정한 현재 사용자가 누구인지와 첫 화면 분기에 필요한 상태를 조회하는 API.

```text
GET /api/v1/me
→ cardCount=0 이고 readOnly=false → 보유 카드 등록 화면
→ cardCount>0 → 주문 입력 화면
```

### Request Header

```text
X-BeONE-Demo-User: MY_CARDS
```

- 현재 사용자는 서버의 `CurrentUserResolver` 한 곳에서 헤더를 읽어 정합니다. 경로·본문에 사용자 ID를 넣지 않습니다(D-09).

### Request Body

없음.

### Response Body

성공: **200 OK**

```json
{
  "data": {
    "demoUser": "MY_CARDS",
    "displayName": "내 카드",
    "portfolioType": "REAL",
    "readOnly": false,
    "cardCount": 2,
    "codefConnected": false
  }
}
```

#### 응답 필드

- `cardCount` · Integer · 등록 해제되지 않은 보유 카드 수
- `codefConnected` · Boolean · 2차 CODEF 연결 여부. 1차에서는 항상 `false`

### 실패 응답

- **500 `INTERNAL_SERVER_ERROR`**: 서버 오류

### 근거

- 결정 기록 D-09

## GET /api/v1/me/cards — 보유 카드 목록 조회

- [1차] 현재 사용자의 보유 카드와 상품 매핑 상태, 추천 제외 여부·이유를 조회한다(D-15).

현재 사용자의 보유 카드 목록과, 각 카드가 추천 비교에 들어갈 수 있는지(매핑 상태)를 조회하는 API.

매핑되지 않은 카드도 목록에는 보입니다. 추천에서 빠지는 이유는 `recommendationEligibility`로 나눠 보여 줍니다(D-15).

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
        "userCardId": "uc-1001",
        "displayName": "청춘대로 톡톡",
        "holdingSource": "MANUAL",
        "mapping": {
          "status": "USER_CONFIRMED",
          "cardProductId": "09174",
          "confirmationMethod": "MANUAL_SELECTION",
          "confirmedAt": "2026-11-03T10:12:00+09:00",
          "candidateCardProductId": null,
          "candidateBasis": null,
          "note": "사용자 확인 매핑, 공식 SKU·약관 버전 미검증"
        },
        "excludedFromRecommendation": false,
        "recommendationEligibility": {
          "eligible": true,
          "reason": null
        },
        "registeredAt": "2026-11-03T10:12:00+09:00"
      },
      {
        "userCardId": "uc-1002",
        "displayName": "회사 법인카드",
        "holdingSource": "MANUAL",
        "mapping": {
          "status": "NOT_SUPPORTED",
          "cardProductId": null,
          "confirmationMethod": "MANUAL_SELECTION",
          "confirmedAt": "2026-11-03T10:13:00+09:00",
          "candidateCardProductId": null,
          "candidateBasis": null,
          "note": null
        },
        "excludedFromRecommendation": false,
        "recommendationEligibility": {
          "eligible": false,
          "reason": "NO_SUPPORTED_RULE"
        },
        "registeredAt": "2026-11-03T10:13:00+09:00"
      }
    ]
  }
}
```

#### 응답 필드

- `holdingSource` · String · `MANUAL`(수동 등록) / `CODEF`(2차 보유카드 조회) / `SYNTHETIC`(가상 포트폴리오)
- `mapping.status` · String · `UNMAPPED` / `CANDIDATE` / `USER_CONFIRMED` / `NOT_SUPPORTED`. 계산에는 `USER_CONFIRMED`만 쓴다. 나중에 `OFFICIALLY_VERIFIED`를 더할 수 있다
- `mapping.confirmationMethod` · String|null · `MANUAL_SELECTION` / `CODEF_CANDIDATE`
- `mapping.candidateBasis` · Object|null · CODEF 후보일 때 후보를 낸 근거(예: `{"cardName":"..."}`). 카드명만으로 확정하지 않는다
- `mapping.note` · String|null · `USER_CONFIRMED`면 "사용자 확인 매핑, 공식 SKU·약관 버전 미검증"
- `recommendationEligibility.reason` · String|null · `MAPPING_UNCONFIRMED`(UNMAPPED·CANDIDATE) / `NO_SUPPORTED_RULE`(NOT_SUPPORTED) / `USER_EXCLUDED`(사용자가 뺀 카드)
- 가상 포트폴리오 사용자의 카드는 규칙 카드 자체라 매핑 대상이 아니다(D-15). `holdingSource=SYNTHETIC`이고 `mapping`은 `null`이며 `cardProductId`를 카드 항목에 직접 담는다
- `mapping.candidateCardProductId` · String|null · `CANDIDATE`일 때 사용자가 확인할 수 있는 후보 상품(D-15). `CANDIDATE`의 `cardProductId`는 `null`이다
- `excludedFromRecommendation` · Boolean · 사용자가 직접 추천에서 뺀 카드(PRD §4.1). 켜면 `recommendationEligibility.reason=USER_EXCLUDED`. 이 값은 D-18 K3 순서에 넣지 않고 "사용자 제외"로 따로 표시한다(D-19)
- 카드번호·끝 4자리·유효기간은 응답에 없다(D-14 ⑨)

### 실패 응답

- **500 `INTERNAL_SERVER_ERROR`**: 서버 오류

### 근거

- 결정 기록 D-15
- D-14 원칙 9

## POST /api/v1/me/cards — 보유 카드 수동 등록

- [1차] 보유 카드를 수동 등록한다. 상품을 고르면 USER_CONFIRMED, 고르지 않으면 UNMAPPED로 저장한다.

사용자가 가진 카드를 수동으로 등록하는 API. 카드번호는 받지 않고 표시 이름과 매핑할 상품만 받는다.

```text
카드 상품 목록 조회
→ 사용자가 4종 중 하나 선택 → cardProductId 전송 → USER_CONFIRMED
→ 4종이 아니라고 선택 → notSupported=true → NOT_SUPPORTED
→ 아직 모름 → 둘 다 생략 → UNMAPPED
```

### Request Header

```text
Content-Type: application/json
X-BeONE-Demo-User: MY_CARDS
```

### Request Body

```json
{
  "displayName": "청춘대로 톡톡",
  "cardProductId": "09174",
  "notSupported": false
}
```

#### 필수 필드

```text
displayName
```

- `displayName`은 1~30자이며 앞뒤 공백을 제거한다. 카드번호를 표시 이름에 넣지 않도록 안내한다.

#### 선택 필드

```text
cardProductId
notSupported
```

- `cardProductId`를 보내면 사용자가 직접 고른 것으로 보고 `USER_CONFIRMED`(`MANUAL_SELECTION`)로 저장한다.
- `notSupported=true`면 `NOT_SUPPORTED`로 저장한다. `cardProductId`와 함께 보낼 수 없다.
- 둘 다 생략하면 `UNMAPPED`다. 추천 비교에서 빠지고 이유는 "매핑 확인 필요"다.
- 카드번호·끝 4자리·CVV·유효기간·카드 비밀번호 필드는 없다. 보내도 저장하지 않고 400으로 거부한다.

### Response Body

성공: **201 Created**

```json
{
  "data": {
    "userCardId": "uc-1001",
    "displayName": "청춘대로 톡톡",
    "holdingSource": "MANUAL",
    "mapping": {
      "status": "USER_CONFIRMED",
      "cardProductId": "09174",
      "confirmationMethod": "MANUAL_SELECTION",
      "confirmedAt": "2026-11-03T10:12:00+09:00",
      "candidateCardProductId": null,
      "candidateBasis": null,
      "note": "사용자 확인 매핑, 공식 SKU·약관 버전 미검증"
    },
    "excludedFromRecommendation": false,
    "recommendationEligibility": {
      "eligible": true,
      "reason": null
    },
    "registeredAt": "2026-11-03T10:12:00+09:00"
  }
}
```

### 실패 응답

- **400 `INVALID_REQUEST`**: 요청값이 올바르지 않은 경우
  - `displayName` 누락·길이 초과
  - `cardProductId`와 `notSupported=true`를 함께 보냄
  - 알 수 없는 필드(카드번호 등) 포함

- **404 `CARD_PRODUCT_NOT_FOUND`**: 없는 `cardProductId`

- **409 `PORTFOLIO_READ_ONLY`**: 가상 포트폴리오 사용자가 요청한 경우
  - 가상 포트폴리오는 시드 데이터라 수정할 수 없다

- **409 `USER_CARD_DUPLICATED`**: 같은 상품을 이미 등록한 경우
  - 같은 사용자에게 같은 `cardProductId`의 `USER_CONFIRMED` 카드가 이미 있음

- **500 `INTERNAL_SERVER_ERROR`**: 서버 오류

### 명세 단계 미정

- 같은 상품의 카드를 두 장 보유하는 경우를 허용할지(현재 제안: 중복 거부). `UNMAPPED`·`NOT_SUPPORTED` 카드는 이 검사를 하지 않는다.

### 근거

- 결정 기록 D-15
- D-14 원칙 9

## PATCH /api/v1/me/cards/{userCardId}/mapping — 보유 카드 매핑 확인·변경

- [1차] 보유 카드의 상품 매핑을 사용자가 확인하거나 바꾼다. 바꾸면 기존 계산 결과는 다시 계산 대상이 된다(D-15).

보유 카드가 규칙 카드 4종 중 무엇인지 사용자가 확인·변경·취소하는 API.

CANDIDATE는 카드명이 정확히 같아도 자동으로 확정하지 않습니다. 사용자가 이 API로 확인해야 USER_CONFIRMED가 됩니다. 매핑을 바꾸거나 취소하면 그 카드의 이전 추천 결과는 다시 계산하거나 `확인 필요`로 되돌립니다.

### Request Header

```text
Content-Type: application/json
X-BeONE-Demo-User: MY_CARDS
```

### Path / Query Parameter

- `userCardId` · Path · String · 필수

### Request Body

```json
{
  "status": "USER_CONFIRMED",
  "cardProductId": "09174",
  "confirmationMethod": "CODEF_CANDIDATE"
}
```

#### status

`CANDIDATE`는 서버만 만든다(2차 CODEF 후보). 요청으로 보낼 수 없다.

```text
USER_CONFIRMED
NOT_SUPPORTED
UNMAPPED
```

#### confirmationMethod

```text
MANUAL_SELECTION
CODEF_CANDIDATE
```

#### 필수 필드

```text
status
```

- `USER_CONFIRMED`면 `cardProductId`와 `confirmationMethod`가 필수다.
- `CODEF_CANDIDATE`는 현재 매핑이 `CANDIDATE`이고 그 후보 상품과 같을 때만 쓸 수 있다.
- `NOT_SUPPORTED`·`UNMAPPED`는 `cardProductId`를 보내지 않는다.

### Response Body

성공: **200 OK**

```json
{
  "data": {
    "userCardId": "uc-2001",
    "displayName": "청춘대로 톡톡",
    "holdingSource": "CODEF",
    "mapping": {
      "status": "USER_CONFIRMED",
      "cardProductId": "09174",
      "confirmationMethod": "CODEF_CANDIDATE",
      "confirmedAt": "2026-11-03T11:00:00+09:00",
      "candidateCardProductId": null,
      "candidateBasis": {
        "cardName": "청춘대로 톡톡카드"
      },
      "note": "사용자 확인 매핑, 공식 SKU·약관 버전 미검증"
    },
    "excludedFromRecommendation": false,
    "recommendationEligibility": {
      "eligible": true,
      "reason": null
    },
    "invalidatedRecommendationIds": [
      "rec-3001"
    ]
  }
}
```

#### 응답 필드

- `invalidatedRecommendationIds` · Array · 매핑 변경으로 더 이상 승인 근거로 쓸 수 없게 된 추천 ID. 없으면 `[]`

### 실패 응답

- **400 `INVALID_REQUEST`**: 요청값이 올바르지 않은 경우
  - `status` 누락·허용 밖 값
  - `USER_CONFIRMED`인데 `cardProductId` 누락
  - `CODEF_CANDIDATE`인데 현재 후보와 다른 상품

- **404 `USER_CARD_NOT_FOUND`**: 없거나 다른 사용자의 카드

- **404 `CARD_PRODUCT_NOT_FOUND`**: 없는 상품 ID

- **409 `PORTFOLIO_READ_ONLY`**: 가상 포트폴리오 사용자가 요청한 경우

- **500 `INTERNAL_SERVER_ERROR`**: 서버 오류

### 근거

- 결정 기록 D-15

## PATCH /api/v1/me/cards/{userCardId} — 보유 카드 표시 이름·추천 제외 변경

- [1차] 표시 이름을 바꾸거나 사용자가 이 카드를 추천에서 빼거나 다시 넣는다. PRD §4.1 "필요한 경우 추천 제외 카드를 설정한다"의 API다.

### Request Header

```text
Content-Type: application/json
X-BeONE-Demo-User: MY_CARDS
```

### Request Body

```json
{
  "displayName": "청춘대로 톡톡",
  "excludedFromRecommendation": true
}
```

- 보낸 필드만 바꾼다. 빈 객체는 400이다.
- 매핑은 이 API로 바꾸지 않는다.

### Response Body

성공: **200 OK**. 응답 형식은 보유 카드 목록의 항목과 같다. 값이 바뀌면 `invalidatedRecommendationIds`를 함께 담는다.

### 실패 응답

- **400 `INVALID_REQUEST`**: 빈 객체, 길이 초과, 알 수 없는 필드
- **404 `USER_CARD_NOT_FOUND`**: 없거나 다른 사용자의 카드
- **409 `REPRESENTATIVE_CARD_IN_USE`**: 대표 카드 고정 모드의 대표 카드를 추천에서 빼는 경우
- **409 `PORTFOLIO_READ_ONLY`**: 가상 포트폴리오 사용자

### 명세 단계 미정

- 사용자가 뺀 카드만 있을 때 조건부 표시: 사용자가 고른 제외이므로 `conditional`을 켜지 않는 안을 제안합니다.

### 근거

- PRD §4.1, D-15

## DELETE /api/v1/me/cards/{userCardId} — 보유 카드 등록 해제

- [1차] 보유 카드 등록을 해제해 이후 추천에서 뺀다. 기존 모의 결제·원장 기록은 보존한다.

보유 카드를 목록과 이후 추천에서 빼는 API. 이미 만든 모의 결제·원장 기록은 지우지 않는다. 실제 카드 해지가 아니고 물리 삭제도 아니다.

### Request Header

```text
X-BeONE-Demo-User: MY_CARDS
```

### Path / Query Parameter

- `userCardId` · Path · String · 필수

### Request Body

없음.

### Response Body

성공: **204 No Content**

본문 없음.

### 실패 응답

- **404 `USER_CARD_NOT_FOUND`**: 없거나 이미 해제했거나 다른 사용자의 카드

- **409 `PORTFOLIO_READ_ONLY`**: 가상 포트폴리오 사용자가 요청한 경우

- **409 `REPRESENTATIVE_CARD_IN_USE`**: 결제 전 설정의 대표 카드인 경우
  - 대표 카드 고정 모드에서 이 카드가 대표 카드다. 먼저 설정을 바꿔야 한다

- **500 `INTERNAL_SERVER_ERROR`**: 서버 오류

### 명세 단계 미정

- 이미 해제한 카드의 재요청: 현재 404. 기존 초안은 204(멱등)를 제안했다.
- 자동 추천 모드에서 동률 기준으로만 쓰는 대표 카드를 해제하면 대표 카드를 비운다(제안).

### 근거

- 결정 기록 D-15
