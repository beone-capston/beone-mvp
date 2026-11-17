# card 도메인 안내

## 목적과 범위

`card`는 사용자가 다루는 카드를 담당하는 업무 API 패키지입니다([D-23](../decisions.md#d-23-확정-정책) 13항). 두 부분으로 나뉩니다.

- **카드 상품 조회**: BeONE이 계산을 지원하는 규칙 카드 4종과 상품별 규칙 요약을 보여 줍니다. 보유 카드를 등록하거나 매핑할 때 고르는 선택지이기도 합니다([D-15](../decisions.md)).
- **보유 카드**: 사용자별 보유 카드 목록·수동 등록·상품 매핑·표시 이름·해제.

구현된 범위:

- (#105) `GET /api/v1/card-products` 카드 상품 목록([명세](../api/products.md#get-apiv1card-products--카드-상품-목록-조회))
- (#105) `GET /api/v1/card-products/{cardProductId}` 카드 상품 상세·규칙 요약([명세](../api/products.md#get-apiv1card-productscardproductid--카드-상품-상세규칙-요약-조회))
- (#105) 기능별 오류 코드 `CARD_PRODUCT_NOT_FOUND`(404)와 처리기
- (#108) `GET /api/v1/me` 현재 사용자([명세](../api/cards.md#get-apiv1me--현재-사용자-조회))
- (#108) 보유 카드 API 5개 `GET`·`POST /api/v1/me/cards`, `PATCH /api/v1/me/cards/{userCardId}/mapping`, `PATCH`·`DELETE /api/v1/me/cards/{userCardId}`([명세](../api/cards.md#get-apiv1mecards--보유-카드-목록-조회)), 보유 카드 테이블 `user_cards`와 가상 포트폴리오 시드(Flyway `V4__user_cards.sql`)

- (#111) 대표 카드 보호: 대표 카드 고정 모드의 대표 카드를 추천에서 빼거나 해제하면 409 `REPRESENTATIVE_CARD_IN_USE`, 자동 추천 모드 대표 카드를 해제하면 대표 카드를 비웁니다. 구현은 결제 전 설정([preference 안내](preference.md))이 맡습니다.

계획·미구현(자세한 내용은 [보유 카드](#보유-카드)의 "아직 하지 않는 것"):

- 매핑·추천 제외 변경에 따른 이전 추천 무효화: 추천(B2)에서 연결합니다.
- CODEF 후보 매핑(`CANDIDATE`): 카드명으로 상품 후보를 정하지 않기로 해(결정 20 R3) 만들지 않습니다. CODEF 보유카드(`holdingSource=CODEF`, `UNMAPPED`)는 CODEF 동기화(#129)가 만듭니다.

## 핵심 용어

- **카드 상품 ID(`cardProductId`)**: KB국민카드의 공식 상품 식별자(예: `09174`). 앞자리 0을 지키는 문자열이며 숫자로 바꾸지 않습니다. `9174`는 다른 값이라 404입니다.
- **규칙 카탈로그(`RuleCatalog`)**: 서버 시작 때 규칙 JSON(`backend/src/main/resources/rules/`)을 읽어 만든 규칙 모음. 카드 상품 테이블은 없고 이것이 원본입니다([benefit 안내](benefit.md)).
- **판정일(`asOf`)**: 어느 날짜에 유효한 규칙을 보여 줄지 정하는 날짜. 생략하면 서버의 오늘(Asia/Seoul)입니다.
- **규칙 상태(`ruleStatus`)**: `VERIFIED`(판정일에 유효한 검증 규칙 있음), `RULE_EXPIRED`(규칙은 있지만 판정일을 포함하는 버전 없음), `NO_VERIFIED_RULE`(검증 규칙 없음). 판정 순서는 `RuleCatalog.select`를 그대로 따릅니다.
- **지원 상태(`supportStatus`)**: 서비스 하나를 BeONE이 계산할 수 있는 범위인지([D-18](../decisions.md) K1). 범위 밖은 계산하지 않고 `확인 필요`로 둡니다.
- **보유 카드(`userCardId`)**: 사용자가 가진 카드 한 장. ID는 `uc-` 뒤에 저장소 번호를 붙인 문자열입니다(예: `uc-1001`). 카드번호·끝 4자리·CVV·유효기간은 받지도 저장하지도 않습니다(D-14 ⑨).
- **보유 출처(`holdingSource`)**: `MANUAL`(수동 등록), `CODEF`(CODEF 동기화 #129가 만듦, local·demo 가짜 게이트웨이만), `SYNTHETIC`(가상 포트폴리오 시드).
- **매핑(`mapping`)**: 보유 카드가 규칙 카드 4종 중 무엇인지 연결한 기록([D-15](../decisions.md#d-15d-16d-17-확정-정책)). 상태는 `UNMAPPED`(모름)·`CANDIDATE`(CODEF 후보)·`USER_CONFIRMED`(사용자 확인)·`NOT_SUPPORTED`(4종 아님)이고 계산에는 `USER_CONFIRMED`만 씁니다. `CANDIDATE`는 상태 값으로만 있고, 지금은 CODEF 카드도 `UNMAPPED`로 만들며 후보 상품·후보 매핑을 만들지 않습니다(결정 20 R3). 가상 포트폴리오 카드는 규칙 카드 자체라 매핑이 없습니다(`mapping: null`).
- **추천 비교 대상(`recommendationEligibility`)**: 이 카드가 추천 비교에 들어갈 수 있는지와 빠지는 이유(`USER_EXCLUDED`·`MAPPING_UNCONFIRMED`·`NO_SUPPORTED_RULE`).

## 패키지 구조

```text
com.beone.api.card
├── web/
│   ├── CardProductController         GET /api/v1/card-products, /{cardProductId} (모든 프로필)
│   ├── CardProductListResponse       목록 응답 data
│   ├── AsOfParameter                 asOf 문자열 판정(YYYY-MM-DD만)
│   ├── InvalidAsOfException          asOf 형식 오류(입력값을 담지 않음)
│   ├── CardProductErrorCode          CARD_PRODUCT_NOT_FOUND
│   ├── CardProductExceptionHandler   형식 오류 → 400, 없는 상품 → 404 (@Order 최우선)
│   ├── CurrentUserController         GET /api/v1/me (local·demo)
│   ├── UserCardController            /api/v1/me/cards 5개 (local·demo)
│   ├── RegisterUserCardRequest·ChangeMappingRequest·UpdateUserCardRequest  요청 본문과 필드 사이 규칙
│   ├── UnknownFieldRejecting         알 수 없는 필드(카드번호 등) → 400
│   ├── CardDisplayName·CrossFieldChecked·StrictBooleanDeserializer  표시 이름·필드 사이·참거짓 검증
│   ├── UserCardResponses·CurrentUserResponse·UserCardValueFormats  응답 모양
│   ├── UserCardErrorCode             USER_CARD_NOT_FOUND·USER_CARD_DUPLICATED·PORTFOLIO_READ_ONLY
│   └── UserCardExceptionHandler      보유 카드 예외 → 400·404·409 (@Order 최우선)
├── application/
│   ├── CardProductService            RuleCatalog를 읽어 목록·상세를 만듦, 시작 시 상품 표 대조, 상품 존재 확인
│   ├── SupportedCardProducts         4종 표시 정보 표(이름·카드사·종류)
│   ├── CardProductProfile·CardType   표시 정보 타입
│   ├── CardProductSummary·CardProductDetail·OfficialSourceView  응답 모양의 결과 타입
│   ├── RuleStatus·SupportStatus      상태 열거형(SupportStatus에 판정 규칙)
│   ├── CardProductNotFoundException  없는 상품
│   ├── UserCardService               보유 카드 조회·등록·매핑·변경·해제, /me 요약
│   ├── UserCardId                    uc-+번호 ↔ 저장소 번호
│   ├── HoldingSource·MappingStatus·ConfirmationMethod·EligibilityReason  보유 카드 열거형
│   ├── RecommendationEligibility     추천 비교 대상 판정
│   ├── UserCardView·UserCardMapping·UserCardChangeResult·CurrentUserSummary  결과 타입
│   ├── NewUserCard·MappingChange·UserCardChange  검증을 마친 입력
│   ├── RepresentativeCardGuard       대표 카드 확장 지점(구현: preference.PaymentPreferenceCardGuard, 빈이 없으면 막지 않음)
│   └── UserCardNotFound·UserCardDuplicated·PortfolioReadOnly·CandidateMismatch 예외
└── persistence/
    ├── UserCardEntity                user_cards 한 행(해제해도 지우지 않음)
    └── UserCardRepository            사용자 ID와 해제 여부로만 조회
```

소스: [web](../../backend/src/main/java/com/beone/api/card/web/), [application](../../backend/src/main/java/com/beone/api/card/application/), [persistence](../../backend/src/main/java/com/beone/api/card/persistence/), [V4__user_cards.sql](../../backend/src/main/resources/db/migration/V4__user_cards.sql).

## 처리 흐름

```text
GET /api/v1/card-products/09174?asOf=2026-11-03
→ CardProductController
   → asOf가 없으면 CardProductService.today() (시계를 Asia/Seoul로 바꿔 날짜를 구함)
     있으면 AsOfParameter.parse (형식 오류 → InvalidAsOfException → 400)
→ CardProductService.getProduct("09174", 2026-11-03)
   → 상품 표에서 ID를 문자열 그대로 찾음 (없으면 CardProductNotFoundException → 404)
   → RuleCatalog.select(CardId, asOf)
      Selected    → ruleStatus VERIFIED, 규칙·서비스·한도 묶음을 응답 모양으로 옮김
      Unavailable → ruleStatus RULE_EXPIRED/NO_VERIFIED_RULE, rule null, services·limitBuckets []
→ ApiResponse.of(...)
```

목록은 상품 표의 4종마다 같은 선택을 하고 `cardProductId` 문자열 오름차순(`01664`, `01914`, `09174`, `09271`)으로 돌려줍니다.

## 응답 값을 정한 근거

계약([products.md](../api/products.md))에 없던 값은 관리 기록 결정 27 A1(2026-11-17, 사용자 선택)으로 정했습니다. 공유 계약 문서는 바꾸지 않았습니다.

| 값 | 정한 방식 | 근거 |
| --- | --- | --- |
| 상품 목록의 원천 | `RuleCatalog.cardIds()`(읽기 전용, #105에서 추가)와 상품 표가 정확히 같아야 서버가 뜸 | 결정 27 A1-1·2 |
| `cardName`·`issuer`·`cardType` | `SupportedCardProducts` 표. 이름은 [card-benefit-research.md](../card-benefit-research.md) 1절 표에서 "KB국민" 접두어를 뗀 것, 카드사는 명세 예시 표기 `KB국민카드` | 결정 27 A1-2, D-02 |
| 목록 정렬 | `cardProductId` 문자열 오름차순 | 결정 27 A1-3, D-12 |
| `services[].name` | 규칙의 `displayName`이 있으면 그것, 없으면 `serviceId` 그대로. 한글 이름을 지어내지 않음 | 결정 27 A1-4 |
| `supportStatus` | 선택형 → `UNSUPPORTED_SELECTION_RULE`, 한도 없음 → `SUPPORTED`, 한도 있음+한도 기간 확정 → `SUPPORTED_SINGLE_MONTHLY_LIMIT`, 한도 기간 미확정 → `UNSUPPORTED_LIMIT_PERIOD` | 결정 27 A1-5, D-18 K1 |
| `sharedByServiceIds` | 그 한도 묶음을 `limitBucketIds`에 직접 적은 서비스만. 자식 묶음을 거쳐 차감하는 서비스는 넣지 않음 | 결정 27 A1-6 |
| `limitBuckets[].period` | 규칙 정책의 한도 기간(코드상 버킷별 값이 아님). 미확정이면 `null` | 결정 27 A1-6 |
| 등록 프로필 | 모든 프로필. 사용자 자원이 아니므로 시연 사용자 헤더를 읽지 않음 | 결정 27 A1-7, [시연 사용자 규약](../api.md#시연-사용자) |

그 밖의 작은 구현 결정:

- 미확정 규칙 조건(`cancellationAttribution`·`limitDeductionOrder`·`performanceWindow`)과 종료일 없는 `validTo`, 위치 없는 출처의 `locator`는 `null`로 둡니다. 0이나 기본값으로 바꾸지 않습니다.
- 규칙이 없는 상세의 `services`·`limitBuckets`는 `[]`입니다. 목록의 `officialSources`가 "규칙이 없으면 `[]`"인 것과 같은 방식이며, 이유는 `ruleStatus`와 `rule: null`로 줍니다.
- `asOf`는 네 자리 연도·두 자리 월·일만 받습니다. `2026-11-3`, 시간대가 붙은 값, 앞뒤 공백, 빈 값(`asOf=`), 없는 날짜(`2026-02-30`)는 400 `INVALID_REQUEST`이고 `errors`는 `[{"field":"asOf","message":"형식이 올바르지 않습니다."}]`입니다(공통 처리기의 타입 오류 문구와 같음). 형식 오류가 상품 없음보다 먼저입니다.
- 오늘 날짜는 `Clock` 빈이 하나 있으면 그것을, 없으면 시스템 시계를 쓰고 항상 Asia/Seoul로 바꿔 구합니다. 테스트는 `Clock` 빈으로 고정합니다.

## 보유 카드

현재 사용자(`X-BeONE-Demo-User` 헤더로 정함, [user 안내](user.md))의 보유 카드를 다룹니다. 컨트롤러는 `CurrentUser` 인자만 받고 헤더를 직접 읽지 않으며, `local`·`demo` 프로필에서만 등록됩니다(그 밖은 404). `GET /api/v1/me`는 보유 카드 수가 필요해 이 패키지에 두었고 `user` 패키지의 `DemoUserService.listDemoUsers()`로 표시 정보를 읽습니다(`user` 코드는 바꾸지 않음).

### 처리 흐름

```text
POST /api/v1/me/cards {"displayName":"청춘대로 톡톡","cardProductId":"09174"}
→ CurrentUserResolver가 헤더로 CurrentUser를 정함 (없거나 틀리면 400)
→ 요청 본문 검증 (알 수 없는 필드·표시 이름 길이·cardProductId+notSupported 동시 → 400)
→ UserCardService.register
   → 가상 포트폴리오(readOnly)면 PortfolioReadOnlyException → 409
   → cardProductId가 있으면 CardProductService.requireSupported (없으면 404 CARD_PRODUCT_NOT_FOUND)
   → 같은 상품의 해제 안 된 USER_CONFIRMED 카드가 있으면 409 USER_CARD_DUPLICATED
   → user_cards에 저장 (USER_CONFIRMED·MANUAL_SELECTION, 확인·등록 시각 = 서울 현재 시각)
→ 201 + 목록 항목과 같은 모양
```

검사 순서는 모든 쓰기 API에서 요청 형식 400 → 가상 포트폴리오 409 → 카드 없음 404 → 상품 없음 404 → 중복 409입니다(결정 27 A2-9). 다른 사용자의 카드, 이미 해제한 카드, 형식이 틀린 ID(`1001`, `uc-01001`)는 모두 "없는 카드" 404입니다.

### 저장과 중복 금지

- 테이블 `user_cards`는 `user_id`(FK `users`)로 사용자를 나눕니다([D-09](../decisions.md#d-09d-14-확정-정책)). 카드 상품 테이블은 없고 `card_product_id`는 규칙 JSON의 상품 ID 문자열입니다(D-23 12항).
- 해제(`DELETE`)는 행을 지우지 않고 `released_at`을 채웁니다. 목록·`cardCount`·조회는 해제한 카드를 보지 않습니다. 이미 만든 모의 결제·원장 기록이 이 행을 가리킬 수 있기 때문입니다.
- 같은 사용자·같은 상품의 `USER_CONFIRMED` 중복 금지(D-23 2항)는 두 곳에서 지킵니다. 서비스가 저장 전에 검사해 409로 답하고, DB는 생성 열 `active_product_id`(해제 안 된 `USER_CONFIRMED`·`SYNTHETIC` 카드만 상품 ID를 가짐)와 유니크 인덱스 `uk_user_cards_active_product`로 동시 요청까지 막습니다. 이 유니크 제약 위반만 409로 바꾸고 다른 제약 위반은 그대로 둡니다(500). `UNMAPPED`·`NOT_SUPPORTED`는 검사하지 않습니다.
- 엔티티는 바뀐 열만 갱신합니다(`@DynamicUpdate`). 이름 변경과 해제가 동시에 일어나도 이름 변경이 해제 시각을 지워 카드가 되살아나지 않습니다.
- 쓰기 API(매핑·이름/제외·해제)는 카드 행에 쓰기 잠금(`PESSIMISTIC_WRITE`)을 걸고 읽습니다. 같은 카드를 바꾸는 요청은 차례로 처리되고, 뒤 요청은 앞 요청이 커밋한 상태를 읽고 검사합니다.
- 모의 승인용 잠금(#122): `UserCardService.lockHeldCardsForPayment`는 이 사용자의 해제하지 않은 카드 행 전부를 ID 오름차순으로 쓰기 잠금합니다(`UserCardRepository.lockActiveByUserIdOrderByUserCardId`, 호출자 트랜잭션 필수 `MANDATORY`). payment 승인이 재계산 전에 부르며, 순서는 [payment 안내의 동시성 절](payment.md#동시성과-잠금-순서122)에 있습니다.
- 체크 제약: 가상 포트폴리오 카드는 매핑 없이 상품 ID를 갖고, 그 밖의 카드는 매핑 상태가 있으며, 상품 ID는 `USER_CONFIRMED`·`SYNTHETIC`만 갖습니다.
- 가상 포트폴리오 시드는 규칙 카드 4종 전부입니다(결정 27 A2-1, 사용자 결정). 표시 이름은 상품 조회의 `cardName`과 같고, 등록 시각은 마이그레이션을 적용한 서울 시각입니다. 시드 내용과 `SupportedCardProducts`의 일치는 통합 테스트가 확인합니다.

### 응답 값을 정한 근거

계약([cards.md](../api/cards.md))에 없던 값은 관리 기록 결정 27 A2절(2026-11-17)로 정했습니다. 공유 계약 문서는 바꾸지 않았습니다.

| 값 | 정한 방식 | 근거 |
| --- | --- | --- |
| `userCardId` | `uc-` + 저장소 번호(선행 0 없음). 형식이 틀리면 404 | A2-1 |
| 목록 정렬·범위 | 등록 순(ID 오름차순), 해제한 카드 제외 | A2-2 |
| `recommendationEligibility` | 사용자 제외 → `USER_EXCLUDED`, 그다음 `UNMAPPED`·`CANDIDATE` → `MAPPING_UNCONFIRMED`, `NOT_SUPPORTED` → `NO_SUPPORTED_RULE`, `USER_CONFIRMED`·`SYNTHETIC` → 대상. 규칙 만료·월간 상태·설정은 보지 않음 | A2-3 |
| 최상위 `cardProductId` | `SYNTHETIC` 항목에만 있음. `MANUAL` 항목은 매핑 안에만 있음 | A2-4, D-15 |
| 매핑 변경 응답 | 계약 예시 모양 그대로(`registeredAt` 없음, `invalidatedRecommendationIds` 있음) | A2-5 |
| `invalidatedRecommendationIds` | 변경 직전 지연 판정으로 유효했고 이 카드 변경으로 `MAPPING_CHANGED`가 된 이 사용자의 추천, ID 오름차순(#120). 매핑 변경은 늘 담고(없으면 `[]`), 표시 이름·제외 변경은 제외 값이 실제로 바뀔 때만 담음 | A2-6, 계약, 결정 28 보충 |
| `confirmationMethod` | `USER_CONFIRMED`는 필수, `NOT_SUPPORTED`는 생략 또는 `MANUAL_SELECTION`(저장은 `MANUAL_SELECTION`), `UNMAPPED`는 보내면 400(저장 시 확인 방법·시각 `null`). `CODEF_CANDIDATE`는 후보가 없어 항상 400 | A2-7 |
| 같은 카드 재확인 | 자기 자신은 중복이 아님. 200이고 확인 시각만 바뀜 | A2-8 |
| 표시 이름 | 앞뒤 공백을 뗀 뒤 1~30자(유니코드 문자 기준). 공백만이면 400 | A2-9, 계약 |
| 알 수 없는 필드 | 400 `INVALID_REQUEST`, `errors: []`(역직렬화 실패와 같은 모양). 공유 Jackson 설정을 바꾸지 않고 요청 DTO에만 적용 | A2-9, D-14 ⑨ |
| CODEF 후보 열 | 만들지 않음. `candidateCardProductId`·`candidateBasis`는 항상 `null` | A2-10 |
| `codefConnected` | 1차는 항상 `false` | 계약 |

그 밖의 작은 구현 결정:

- `PATCH /me/cards/{id}`에서 값이 `null`인 필드는 보내지 않은 것으로 봅니다. 둘 다 없으면 `errors: [{"field": null, "message": "변경할 값이 없습니다."}]`입니다.
- 참거짓 필드(`notSupported`·`excludedFromRecommendation`)는 JSON `true`·`false`만 받습니다. `"true"`·`1`은 400입니다. 열거 값(`status`·`confirmationMethod`)은 문자열로 받아 허용 목록과 비교하므로 숫자 순번이나 소문자는 400입니다.
- 시각은 `Clock` 빈이 하나 있으면 그것을, 없으면 시스템 시계를 쓰고 Asia/Seoul로 바꿔 초 단위로 저장합니다. 응답은 `+09:00`이 붙은 ISO-8601입니다.

### 대표 카드 보호(#111 구현)

대표 카드 고정 모드의 대표 카드를 추천에서 빼거나 해제할 때의 409 `REPRESENTATIVE_CARD_IN_USE`와, 자동 추천 모드에서 동률 기준 대표 카드를 해제할 때 비우기(D-23)는 결제 전 설정의 [`PaymentPreferenceCardGuard`](../../backend/src/main/java/com/beone/api/preference/application/PaymentPreferenceCardGuard.java)가 [`RepresentativeCardGuard`](../../backend/src/main/java/com/beone/api/card/application/RepresentativeCardGuard.java)를 구현해 맡습니다. 서비스는 추천 제외로 바꾸기 전에 `beforeExclusion`, 해제 전에 `beforeRelease`를 같은 트랜잭션에서 부릅니다. 구현 빈이 없는 슬라이스 테스트 등에서는 기본 구현 `NONE`이 아무것도 막지 않습니다. 자동 추천 모드 대표 카드를 추천에서 빼는 것은 막지도 비우지도 않습니다(결정 28 관리자 판단). 자세한 규칙은 [preference 안내](preference.md#대표-카드-보호보유-카드-patchdelete)에 있습니다.

409 응답은 보유 카드 API에서 나가므로 card 패키지에 [`RepresentativeCardInUseException`](../../backend/src/main/java/com/beone/api/card/application/RepresentativeCardInUseException.java), `UserCardErrorCode.REPRESENTATIVE_CARD_IN_USE`, `UserCardExceptionHandler` 처리 메서드를 더했습니다(관리자 승인 최소 변경). card는 preference를 모르고 확장 지점으로만 만납니다.

### 아직 하지 않는 것(한계)

- **추천 무효화 목록(#120)**: 무효화 자체는 추천 조회 때의 지연 판정이 합니다. 보유 카드 응답의 목록은 recommendation이 card를 읽으므로 순환을 피하려고 조회 포트 [`RecommendationInvalidationLookup`](../../backend/src/main/java/com/beone/api/card/application/RecommendationInvalidationLookup.java)을 card에 두고 구현은 recommendation의 `RecommendationLinks`가 합니다. `UserCardService`는 변경 저장 전에 `validUsing`(이 카드를 근거로 쓰고 지금 유효한 추천)을, 저장 뒤 `invalidatedByMapping`(그중 이제 `MAPPING_CHANGED`인 추천)을 같은 트랜잭션에서 부릅니다. 이미 무효였던 추천(승인된 주문, 다른 카드·월간 상태 변경)은 담지 않으므로, 제외했다가 되돌리면 되돌린 응답은 `[]`입니다. 포트는 부를 때 찾고 구현 빈이 없으면 `[]`입니다. 카드 해제(`DELETE`)는 응답 본문이 없어 목록을 돌려주지 않습니다.
- **CODEF(2차)**: `CODEF` 출처 카드는 [CODEF 동기화](codef.md#codef-조회동기화-api-129)(#129)가 `UserCardService.registerCodefCard`(`UserCardEntity.codef`, `UNMAPPED`·상품 없음, 호출자 트랜잭션 필수)로만 만듭니다. 사용자는 수동 카드와 같은 매핑 변경으로 상품을 고릅니다. 동기화 저장 전 잠금은 `lockHeldCardsForCodefSync`(모의 승인과 같은 ID 오름차순 잠금, CODEF 카드 ID 반환)입니다. `CANDIDATE` 매핑과 `CODEF_CANDIDATE` 확인은 후보 상품을 정하지 않으므로(결정 20 R3) 만들지 않았고 후보 열도 없습니다.
- `USER_CARD_MAPPING_REQUIRED`(409)는 이 작업의 API가 쓰지 않습니다.
- `V4`는 임시 번호입니다. 병합 직전 관리자가 다른 작업선과 순서를 정하면 번호를 올릴 수 있습니다(D-23 12항).

## 사용법

다른 업무 패키지가 상품 정보를 쓸 때는 `CardProductService`를, 현재 사용자의 보유 카드가 필요하면 `UserCardService.list(currentUser)`를 주입받아 씁니다(추천 비교에는 `recommendationEligibility().eligible()`이 `true`인 카드만). 규칙 판정이 필요하면 이 서비스를 거치지 말고 [엔진 입구](benefit.md#engine-업무-api의-입구)를 부릅니다.

```java
CardProductDetail detail = cardProductService.getProduct("09174", cardProductService.today());
if (detail.ruleStatus() != RuleStatus.VERIFIED) {
	// 계산하지 않고 확인 필요로 둔다
}
```

## 테스트

- `test`: [`CardProductServiceTest`](../../backend/src/test/java/com/beone/api/card/application/CardProductServiceTest.java)(정렬·선행 0·규칙 상태·미확정 null·직접 공유·시작 시 표 대조·서울 날짜, 저장소 실제 규칙과 표 일치, 상품 존재 확인), [`SupportStatusTest`](../../backend/src/test/java/com/beone/api/card/application/SupportStatusTest.java)(판정 순서), `RuleCatalogTest#cardIdsListsEveryCardWithAnyVersionInStringOrder`, [`UserCardServiceTest`](../../backend/src/test/java/com/beone/api/card/application/UserCardServiceTest.java)(등록 상태별 저장 값·검사 순서·중복과 DB 유니크 위반·재확인·제외와 대표 카드 확장 지점 호출·해제), [`RecommendationEligibilityTest`](../../backend/src/test/java/com/beone/api/card/application/RecommendationEligibilityTest.java), [`UserCardIdTest`](../../backend/src/test/java/com/beone/api/card/application/UserCardIdTest.java)
- `webTest`: [`CardProductApiWebTest`](../../backend/src/test/java/com/beone/api/card/web/CardProductApiWebTest.java)(프로필 없음, 실제 규칙 JSON: 목록·상세 응답 전체, 적용 전 날짜, 선택형·통합 한도, 없는 상품 404, `asOf` 형식 오류 400), [`CardProductLocalProfileWebTest`](../../backend/src/test/java/com/beone/api/card/web/CardProductLocalProfileWebTest.java)(local에서 헤더 없이·잘못된 헤더로도 200), [`UserCardApiWebTest`](../../backend/src/test/java/com/beone/api/card/web/UserCardApiWebTest.java)(6개 API의 계약 예시 응답, 문서의 실패 응답 전부, 헤더 없음·잘못된 값, 알 수 없는 필드·카드번호 거부), [`UserCardProfileOffWebTest`](../../backend/src/test/java/com/beone/api/card/web/UserCardProfileOffWebTest.java)(프로필 밖 404)
- 웹 슬라이스 주의: 상품 컨트롤러는 모든 프로필에 등록되므로 `controllers`를 지정하지 않은 `@WebSliceTest`에도 들어옵니다. 새 웹 테스트는 `controllers`로 자기 컨트롤러만 올리고, 전체 스캔이 필요한 테스트는 `@MockitoBean CardProductService`를 둡니다(#105에서 기존 웹 테스트 4개를 이렇게 고침).
- `integrationTest`: [`CardProductIntegrationTest`](../../backend/src/test/java/com/beone/api/card/CardProductIntegrationTest.java)(전체 애플리케이션에서 실제 HTTP 목록·404), [`UserCardIntegrationTest`](../../backend/src/test/java/com/beone/api/card/UserCardIntegrationTest.java)(V4 적용, 시드 4장과 상품 표 일치, 가상 포트폴리오 쓰기 409, 등록·변경·해제 저장, 같은 상품 중복과 DB 유니크·체크 제약, 다른 사용자 카드 404)

## 제약

- `supportStatus`는 지원 범위만 나타냅니다. 선택형이 아니고 한도 기간도 확정인데 계산식이나 전월 실적 기간이 미확정이라 계산이 막히는 서비스는 계약 값에 맞는 것이 없어 표시하지 못합니다(새 값을 만들지 않음, 결정 27 A1-5). 현재 저장소의 4종 규칙에는 이런 서비스가 없습니다. 09271 `FUEL`은 계산식이 미확정이지만 선택형이라 `UNSUPPORTED_SELECTION_RULE`입니다.
- 4종 규칙은 `VERIFIED`이지만 실제 보유 카드와 공식 SKU·약관 버전의 일치는 D-01 미결입니다.
- 상품 표시 정보는 코드의 표에 있으므로 규칙 카드를 추가하면 표도 함께 고쳐야 합니다. 둘이 다르면 서버가 뜨지 않습니다. 가상 포트폴리오 시드(`V4`)의 카드 구성도 함께 봐야 합니다.
- 보유 카드의 한계(추천 무효화·CODEF)는 [보유 카드](#보유-카드)의 "아직 하지 않는 것"에 있습니다.
