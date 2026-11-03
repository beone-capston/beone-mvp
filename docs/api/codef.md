# CODEF 연결(2차 초안)

**2차 초안입니다.** CODEF 전송 계약 결정 직후 확정합니다(D-14). 구현 기준으로 쓰지 않습니다.

공통 규약과 공통 실패 응답은 [`docs/api.md`](../api.md#업무-api-공통-규약)를, 전체 목록은 [README](README.md)를 봅니다. JSON은 합성 예시이며 실제 API 실행 결과나 약관 검증 결과가 아닙니다. 모든 API는 구현 전입니다.

## GET /api/v1/me/codef/connection — CODEF 연결 상태 조회

- [2차·전송 계약 미결] KB국민카드 CODEF 연결 상태와 마지막 동기화 시각을 조회한다.

내 카드 사용자의 CODEF 연결 여부와 마지막 동기화 결과를 조회하는 API.

> 2차 설계 초안입니다. D-14에 따라 CODEF 전송 계약(헤더·인코딩·OAuth·timeout·재시도) 결정 직후 확정합니다. 공식 오류 목록에서 오류 응답 다수가 과금 대상으로 표시돼 있어 서버는 자동 재시도하지 않습니다.

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
    "organization": "0301",
    "status": "CONNECTED",
    "connectedAt": "2026-11-04T09:00:00+09:00",
    "lastSyncedAt": "2026-11-04T09:01:00+09:00",
    "reconnectRequired": false
  }
}
```

#### 응답 필드

- `status` · `NOT_CONNECTED` / `CONNECTED` / `RECONNECT_REQUIRED`
- `connectedId`·아이디·비밀번호는 응답에 없다

### 실패 응답

- **500 `INTERNAL_SERVER_ERROR`**: 서버 오류

### 근거

- 결정 기록 D-14 원칙 9
- docs/domains/codef.md

## POST /api/v1/me/codef/connection — CODEF 계정 연결

- [2차·전송 계약 미결] 카드사 홈페이지 아이디·비밀번호로 CODEF 계정을 연결한다. 비밀번호는 즉시 암호화해 전송하고 원문은 폐기한다.

KB국민카드 홈페이지 로그인 아이디·비밀번호로 CODEF 계정을 연결(connectedId 등록)하는 API.

```text
POST connection {loginId, password}
→ 서버가 즉시 CODEF 공개키로 RSA 암호화
→ CODEF 계정 등록 요청
→ 결과 코드가 정확히 CF-00000일 때만 연결 성공
→ connectedId는 서버에만 저장, 비밀번호 원문 폐기
```

> 2차 설계 초안입니다. D-14에 따라 CODEF 전송 계약(헤더·인코딩·OAuth·timeout·재시도) 결정 직후 확정합니다. 공식 오류 목록에서 오류 응답 다수가 과금 대상으로 표시돼 있어 서버는 자동 재시도하지 않습니다.

### Request Header

```text
Content-Type: application/json
X-BeONE-Demo-User: MY_CARDS
```

### Request Body

```json
{
  "organization": "0301",
  "loginType": "ID",
  "loginId": "kbuser01",
  "password": "********"
}
```

#### organization

1차·2차 MVP는 KB국민카드만 지원한다.

```text
0301
```

#### loginType

인증서 로그인은 별도 결정 필요(허용하지 않은 상태)다.

```text
ID
```

#### 필수 필드

```text
organization
loginType
loginId
password
```

- `password`는 저장·로그·응답·오류 메시지에 남기지 않는다. 검증 오류 응답의 `errors`에도 값을 넣지 않는다. `loginId`도 응답에 다시 담지 않는다.
- 최초 연결은 201, 재연결 성공은 200(제안).
- 카드번호·카드 비밀번호(앞 2자리 포함)·CVV·유효기간 필드는 없다. KB 카드소지확인은 지원 보류(수동 입력 대체)다.

### Response Body

성공: 최초 연결은 **201 Created**, 재연결은 **200 OK**(제안). 본문 형식은 같다.

```json
{
  "data": {
    "organization": "0301",
    "status": "CONNECTED",
    "connectedAt": "2026-11-04T09:00:00+09:00",
    "lastSyncedAt": null,
    "reconnectRequired": false
  }
}
```

### 실패 응답

- **400 `INVALID_REQUEST`**: 요청값이 올바르지 않은 경우
  - 필수 필드 누락
  - `organization`·`loginType` 허용 밖 값

- **400 `CODEF_LOGIN_FAILED`**: 카드사 로그인 실패
  - CODEF 결과 코드가 로그인 실패 계열

- **409 `CODEF_CARD_VERIFICATION_NOT_SUPPORTED`**: 카드소지확인이 필요한 경우
  - CF-12108 등. 지원 보류 경로이므로 수동 입력으로 안내한다

- **502 `CODEF_UNAVAILABLE`**: CODEF 응답을 해석할 수 없거나 미분류 오류
  - CF-00000이 아닌 미분류 결과 코드, 전송 단계 오류

- **409 `PORTFOLIO_READ_ONLY`**: 가상 포트폴리오 사용자가 요청한 경우

- **500 `INTERNAL_SERVER_ERROR`**: 서버 오류

### 명세 단계 미정

- CODEF 결과 분류(`CodefResultCategory`)와 위 오류 코드의 대응표.
- 재연결 시 기존 connectedId 처리.
- 공개 배포 시 HTTPS 전제(D-09).

### 근거

- 결정 기록 D-14 원칙 9
- docs/domains/codef.md, codef-kb.md

## DELETE /api/v1/me/codef/connection — CODEF 연결 해제

- [2차·전송 계약 미결] CODEF 연결을 해제한다. 이미 가져온 조회 데이터의 보존·삭제 정책은 미결이다.

CODEF 연결을 해제하는 API.

> 2차 설계 초안입니다. D-14에 따라 CODEF 전송 계약(헤더·인코딩·OAuth·timeout·재시도) 결정 직후 확정합니다. 공식 오류 목록에서 오류 응답 다수가 과금 대상으로 표시돼 있어 서버는 자동 재시도하지 않습니다.

### Request Header

```text
X-BeONE-Demo-User: MY_CARDS
```

### Request Body

없음.

### Response Body

성공: **204 No Content**

본문 없음.

### 실패 응답

- **409 `CODEF_NOT_CONNECTED`**: 연결되어 있지 않은 경우

- **500 `INTERNAL_SERVER_ERROR`**: 서버 오류

### 명세 단계 미정

- 조회 데이터(CODEF 원장·보유카드) 보존·삭제 정책.

### 근거

- 결정 기록 D-14

## POST /api/v1/me/codef/syncs — CODEF 조회·동기화

- [2차·전송 계약 미결] 보유카드·승인내역·실적을 조회해 보유카드는 매핑 후보로, 승인내역은 CODEF 원장으로 옮기고 실적은 참고값으로만 저장한다(D-16·D-17).

CODEF에서 KB국민카드 보유카드·승인내역·실적조회를 가져와 BeONE 데이터로 옮기는 API.

```text
보유카드 → HeldCard(CODEF), 카드명으로 매핑 후보(CANDIDATE)만 제시
승인내역 → CODEF 원장. 전액 취소로 확정된 형태만 반영, 부분취소 의심·짝 없음·짝 여럿·판단 불가 행은 넣지 않고 불완전 표시
실적조회 → "카드사 조회 실적(참고)"로만 저장, 전월 실적에 자동 반영하지 않음
재조회 → 중복 판단 키로 같은 행을 두 번 넣지 않음
```

> 2차 설계 초안입니다. D-14에 따라 CODEF 전송 계약(헤더·인코딩·OAuth·timeout·재시도) 결정 직후 확정합니다. 공식 오류 목록에서 오류 응답 다수가 과금 대상으로 표시돼 있어 서버는 자동 재시도하지 않습니다.

### Request Header

```text
Content-Type: application/json
X-BeONE-Demo-User: MY_CARDS
```

### Request Body

```json
{
  "from": "2026-10-01",
  "to": "2026-11-03"
}
```

#### 선택 필드

```text
from
to
```

- 승인내역 조회 기간. 생략하면 이번 달 1일~오늘. 최대 12개월(KB 조회 조건). `from`이 `to`보다 늦으면 400이고, 역순 기간은 CODEF 조회 조건으로 넘기지 않는다.
- 짝이 없는 취소 행을 맞추려고 조회 기간을 임의로 넓히지 않는다.

### Response Body

성공: **200 OK**

```json
{
  "data": {
    "syncId": "sync-1",
    "syncedAt": "2026-11-04T09:01:00+09:00",
    "heldCards": {
      "fetched": 3,
      "newCandidates": [
        {
          "userCardId": "uc-2001",
          "cardName": "청춘대로 톡톡카드",
          "candidateCardProductId": "09174",
          "mappingStatus": "CANDIDATE"
        }
      ]
    },
    "approvals": {
      "imported": 41,
      "skippedDuplicates": 12,
      "incompleteRows": [
        {
          "userCardId": "uc-2001",
          "approvalNoPresent": true,
          "reason": "PARTIAL_CANCELLATION_SUSPECTED"
        }
      ]
    },
    "performances": [
      {
        "userCardId": "uc-2001",
        "label": "카드사 조회 실적(참고)",
        "period": {
          "from": "2026-10-01",
          "to": "2026-10-31"
        },
        "currentUseWon": 518000,
        "fetchedAt": "2026-11-04T09:01:00+09:00",
        "usedInCalculation": false
      }
    ],
    "partialFailures": []
  }
}
```

#### 응답 필드

- `performances[].usedInCalculation` · 항상 `false`. 사용자가 확인해 수동 입력해야 계산에 쓴다(D-16)
- `incompleteRows[]` · 원장에 넣지 않은 행과 이유. 카드번호·승인번호 원문은 담지 않는다
- 부분 동기화 실패와 전체 실패를 구분한다. 일부 항목만 실패하면 200과 `data.partialFailures[]`(항목: `target`=`HELD_CARDS`/`APPROVALS`/`PERFORMANCES`, 공개 `code`·`message`, 실패가 없으면 `[]`)를 담고, 전부 실패하면 502를 준다(제안). timeout을 504로 나눌지는 전송 계약과 함께 정한다
- HTTP 200만으로 CODEF 성공으로 보지 않는다. URL 디코딩 뒤 결과 코드를 확인한다. 전체 카드번호가 오면 모델·저장·로그·브라우저로 넘기기 전에 제거한다. 카드 이용한도를 월 혜택 한도로 쓰지 않는다

### 실패 응답

- **409 `CODEF_NOT_CONNECTED`**: 연결되어 있지 않은 경우

- **409 `CODEF_RECONNECT_REQUIRED`**: 재연결이 필요한 경우

- **502 `CODEF_UNAVAILABLE`**: CODEF 전체 실패

- **400 `INVALID_REQUEST`**: 요청값이 올바르지 않은 경우
  - 날짜 형식 오류, 12개월 초과, `from`이 `to`보다 늦음

- **500 `INTERNAL_SERVER_ERROR`**: 서버 오류

### 명세 단계 미정

- 중복 판단 키(카드, 승인번호, 승인일시, 금액, 행 형태 등)는 이 2차 명세를 확정할 때 정합니다(D-17, 2026-11-03 사용자 지시).
- 승인번호를 승인 행과 취소 행이 공유하는지(실계정 관측 전 확인 필요).

### 근거

- 결정 기록 D-15·D-16·D-17
- docs/domains/codef-kb.md(취소 표현 구분)
