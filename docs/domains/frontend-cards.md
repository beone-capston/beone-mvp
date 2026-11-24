# 프런트엔드 내 카드

'내 카드' 모드에서 서버에 등록한 보유 카드를 보여 주고, 카드를 수동으로 등록하는 화면입니다(프론트 P0 FE-5). 혜택 계산과 추천은 서버가 하고, 이 화면은 목록과 등록만 맡습니다.

## 용어

- **보유 카드(user card):** 사용자가 가진 카드 한 장입니다. 카드번호는 받지 않고 표시 이름과 매핑 상품만 둡니다([카드 API](../api/cards.md)).
- **매핑:** 보유 카드가 규칙이 있는 카드 상품 4종 중 무엇인지 사용자가 고른 결과입니다. 고르면 `USER_CONFIRMED`, 목록에 없다고 하면 `NOT_SUPPORTED`, 모르면 `UNMAPPED`입니다. 추천 비교에는 `USER_CONFIRMED`만 들어갑니다.
- **카드 소스(`CardSource`):** 화면이 카드를 얻는 경계입니다([portfolio.ts](../../frontend/src/data/portfolio.ts)). 동기 `getCards`만 있으면 디자인 샘플, `load`·`register`가 있으면 서버 카드입니다.

## 구조

| 파일 | 역할 |
|---|---|
| [`cards/cardApi.ts`](../../frontend/src/cards/cardApi.ts) | `GET /api/v1/me/cards`, `GET /api/v1/card-products`, `POST /api/v1/me/cards`(CSRF). 오류를 `CardRequestError.kind`로 나눔 |
| [`cards/memberCardSource.ts`](../../frontend/src/cards/memberCardSource.ts) | 서버 카드 소스. '내 카드'는 서버 목록, 가상 포트폴리오는 기존 시연용 카드. 상품명이 기존 디자인과 같으면 그 디자인을 씀 |
| [`cards/CardRegisterSheet.tsx`](../../frontend/src/cards/CardRegisterSheet.tsx) | 기존 시트로 여는 수동 등록 폼(카드 이름, 카드 상품) |
| [`main.tsx`](../../frontend/src/main.tsx) | 배포 빌드는 서버 카드 소스, 개발용 목 결제 모드(`VITE_PAYMENT_API_MODE=mock`)는 디자인 샘플 |

## 흐름

```text
'내 카드' 진입 → load('MY_CARDS') → 목록(0장이면 빈 상태 화면)
카드 추가 → 시트 → 상품 목록 조회 → 이름·상품 선택 → POST(CSRF) → 목록 다시 조회
```

```ts
// 테스트나 다른 화면에서 API를 바꿔 끼울 수 있다
const source = createMemberCardSource(cardApi)
await source.load?.('MY_CARDS')       // WalletCard[]
await source.register?.register({ displayName: '내 톡톡', cardProductId: '09174' })
```

## 오류 처리

- 409 `USER_CARD_DUPLICATED`, 404 `CARD_PRODUCT_NOT_FOUND`, 400 `INVALID_REQUEST`는 시트 안에서 안내하고 시트를 닫지 않습니다.
- 401은 공통 클라이언트가 로그인 화면 전환을 알립니다. 403 `CSRF_INVALID`이나 토큰 획득 실패는 "보내지 않았으니 다시 시도"로 안내합니다.
- 목록 조회 실패는 화면 아래 안내로 알립니다.

## 현재 범위와 제약

- 매핑 변경(`PATCH …/mapping`), 카드 삭제·추천 제외 설정은 없습니다.
- 대표 카드 선택은 추천 가능한(`eligible`) 카드만 고를 수 있습니다. 설정은 여전히 화면 안의 목 설정입니다.
- 가상 포트폴리오 탭을 회원 화면에서 숨길지(H-3)는 시연 헤더 분기(N18)와 함께 결정 대기입니다.
- 실제 `member` 백엔드와 브라우저로 연결한 확인은 아직 하지 않았습니다.
