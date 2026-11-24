# 결제 전 설정

자동 추천과 대표 카드 고정을 다룹니다(D-12).

공통 규약과 공통 실패 응답은 [`docs/api.md`](../api.md#업무-api-공통-규약)를, 전체 목록은 [README](README.md)를 봅니다. JSON은 합성 예시이며 실제 API 실행 결과나 약관 검증 결과가 아닙니다. 구현 상태는 [README](README.md#업무-api-1차-명세)의 상태 항목을 봅니다.

프로필: 두 API는 `local`·`demo`와 로컬 회원 프로필 `member`에 등록돼 있습니다(G3a #159). 아래 `X-BeONE-Demo-User` 헤더는 `local`·`demo`의 방식입니다. `member`에서는 헤더 대신 회원 세션 쿠키(`__Host-BEONE_SESSION`)로 현재 사용자를 정하고 PUT에 `X-CSRF-TOKEN`이 필요합니다. 검사 순서는 401 `UNAUTHENTICATED` → 403 `CSRF_INVALID` → 400 → 404 → 409이고 모든 응답에 `Cache-Control: no-store`가 붙습니다([회원 세션 규칙](../api.md#회원-세션member-프로필)). 설정 변경은 기존 추천을 무효화하지 않습니다. 모의 승인 때 그 시점의 설정으로 다시 계산합니다(결정 32 Q3).

## GET /api/v1/me/payment-preference — 결제 전 설정 조회

- [1차] 자동 추천 / 대표 카드 고정 설정과 대표 카드를 조회한다.

결제할 때 카드를 자동 추천으로 고를지, 대표 카드로 고정할지를 조회하는 API.

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
    "selectionMode": "AUTO_RECOMMEND",
    "representativeUserCardId": "uc-1001",
    "updatedAt": "2026-11-03T10:30:00+09:00"
  }
}
```

#### 응답 필드

- `selectionMode` · String · `AUTO_RECOMMEND` / `FIXED_REPRESENTATIVE`
- `representativeUserCardId` · String|null · 대표 카드. 자동 추천 모드에서도 동률 처리 3단계(D-12)에 쓴다
- `updatedAt` · DateTime|null · 마지막 설정 시각(ISO-8601). 설정한 적이 없으면 `null`
- 설정한 적이 없으면 `AUTO_RECOMMEND`, 대표 카드 `null`을 반환한다(저장된 기본값이 아니라 미설정 표시)

### 실패 응답

- **500 `INTERNAL_SERVER_ERROR`**: 서버 오류

### 근거

- 결정 기록 D-12
- benefit 도메인 안내(SelectionPreference, SelectionMode)

## PUT /api/v1/me/payment-preference — 결제 전 설정 변경

- [1차] 자동 추천 / 대표 카드 고정과 대표 카드를 설정한다. 대표 카드는 추천 비교 가능한 카드만 고를 수 있다.

결제 전에 카드 선택 방식과 대표 카드를 설정하는 API.

### Request Header

```text
Content-Type: application/json
X-BeONE-Demo-User: MY_CARDS
```

### Request Body

```json
{
  "selectionMode": "FIXED_REPRESENTATIVE",
  "representativeUserCardId": "uc-1001"
}
```

#### selectionMode

```text
AUTO_RECOMMEND
FIXED_REPRESENTATIVE
```

#### 필수 필드

```text
selectionMode
representativeUserCardId
```

- `representativeUserCardId`는 항상 보낸다. 값은 `null`일 수 있고 필드 누락은 400이다(D-23). 모드만 바꾸다 대표 카드를 모르게 지우지 않기 위해서다.

- 대표 카드는 매핑이 `USER_CONFIRMED`인 카드만 고를 수 있다. 추천 비교에서 빠진 카드는 동률 처리의 대표 카드 후보도 될 수 없다(D-15). 주문마다 달라지는 혜택 `확인 필요`는 이 제한이 아니다(D-19).
- `selectionMode`와 대표 카드의 조합
  - `AUTO_RECOMMEND` + 대표 카드: 저장하고 동률 처리 3단계에만 쓴다(D-12)
  - `AUTO_RECOMMEND` + `null`: 대표 카드를 지운다
  - `FIXED_REPRESENTATIVE` + 대표 카드: 그 카드로 고정한다
  - `FIXED_REPRESENTATIVE` + `null`: 400 `INVALID_REQUEST`
  - 모드와 무관하게 필드 누락: 400 `INVALID_REQUEST`
- 결제 중 카드를 다시 고르는 단계는 없다(PRD §4). 사용자 통제는 이 설정으로만 한다.

### Response Body

성공: **200 OK**

```json
{
  "data": {
    "selectionMode": "FIXED_REPRESENTATIVE",
    "representativeUserCardId": "uc-1001",
    "updatedAt": "2026-11-03T10:30:00+09:00"
  }
}
```

#### 응답 필드

- 응답 형식은 「결제 전 설정 조회」와 같다. `updatedAt`은 이번에 저장한 시각이다

### 실패 응답

- **400 `INVALID_REQUEST`**: 요청값이 올바르지 않은 경우
  - `selectionMode` 누락·허용 밖 값
  - `representativeUserCardId` 필드 누락
  - `FIXED_REPRESENTATIVE`인데 대표 카드가 `null`

- **404 `USER_CARD_NOT_FOUND`**: 없거나 다른 사용자의 카드

- **409 `REPRESENTATIVE_CARD_NOT_ELIGIBLE`**: 추천 비교에서 빠진 카드를 대표 카드로 고른 경우
  - 매핑이 `USER_CONFIRMED`가 아님

- **500 `INTERNAL_SERVER_ERROR`**: 서버 오류

### 근거

- 결정 기록 D-12·D-15·D-23
