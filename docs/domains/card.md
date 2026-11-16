# card 도메인 안내

## 목적과 범위

`card`는 사용자가 다루는 카드를 담당하는 업무 API 패키지입니다([D-23](../decisions.md#d-23-확정-정책) 13항). 두 부분으로 나뉩니다.

- **카드 상품 조회**: BeONE이 계산을 지원하는 규칙 카드 4종과 상품별 규칙 요약을 보여 줍니다. 보유 카드를 등록하거나 매핑할 때 고르는 선택지이기도 합니다([D-15](../decisions.md)).
- **보유 카드**: 사용자별 보유 카드 목록·수동 등록·상품 매핑·표시 이름·해제.

구현된 범위(#105):

- `GET /api/v1/card-products` 카드 상품 목록([명세](../api/products.md#get-apiv1card-products--카드-상품-목록-조회))
- `GET /api/v1/card-products/{cardProductId}` 카드 상품 상세·규칙 요약([명세](../api/products.md#get-apiv1card-productscardproductid--카드-상품-상세규칙-요약-조회))
- 기능별 오류 코드 `CARD_PRODUCT_NOT_FOUND`(404)와 처리기

계획·미구현:

- 보유 카드 API 5개(`/api/v1/me/cards...`)와 `GET /api/v1/me`, 보유 카드 테이블(`persistence/`)과 Flyway 마이그레이션. 다음 작업(A2)에서 구현합니다. 지금은 이 경로가 없습니다(404).

## 핵심 용어

- **카드 상품 ID(`cardProductId`)**: KB국민카드의 공식 상품 식별자(예: `09174`). 앞자리 0을 지키는 문자열이며 숫자로 바꾸지 않습니다. `9174`는 다른 값이라 404입니다.
- **규칙 카탈로그(`RuleCatalog`)**: 서버 시작 때 규칙 JSON(`backend/src/main/resources/rules/`)을 읽어 만든 규칙 모음. 카드 상품 테이블은 없고 이것이 원본입니다([benefit 안내](benefit.md)).
- **판정일(`asOf`)**: 어느 날짜에 유효한 규칙을 보여 줄지 정하는 날짜. 생략하면 서버의 오늘(Asia/Seoul)입니다.
- **규칙 상태(`ruleStatus`)**: `VERIFIED`(판정일에 유효한 검증 규칙 있음), `RULE_EXPIRED`(규칙은 있지만 판정일을 포함하는 버전 없음), `NO_VERIFIED_RULE`(검증 규칙 없음). 판정 순서는 `RuleCatalog.select`를 그대로 따릅니다.
- **지원 상태(`supportStatus`)**: 서비스 하나를 BeONE이 계산할 수 있는 범위인지([D-18](../decisions.md) K1). 범위 밖은 계산하지 않고 `확인 필요`로 둡니다.

## 패키지 구조

```text
com.beone.api.card
├── web/
│   ├── CardProductController         GET /api/v1/card-products, /{cardProductId} (모든 프로필)
│   ├── CardProductListResponse       목록 응답 data
│   ├── AsOfParameter                 asOf 문자열 판정(YYYY-MM-DD만)
│   ├── InvalidAsOfException          asOf 형식 오류(입력값을 담지 않음)
│   ├── CardProductErrorCode          CARD_PRODUCT_NOT_FOUND
│   └── CardProductExceptionHandler   형식 오류 → 400, 없는 상품 → 404 (@Order 최우선)
└── application/
    ├── CardProductService            RuleCatalog를 읽어 목록·상세를 만듦, 시작 시 상품 표 대조
    ├── SupportedCardProducts         4종 표시 정보 표(이름·카드사·종류)
    ├── CardProductProfile·CardType   표시 정보 타입
    ├── CardProductSummary·CardProductDetail·OfficialSourceView  응답 모양의 결과 타입
    ├── RuleStatus·SupportStatus      상태 열거형(SupportStatus에 판정 규칙)
    └── CardProductNotFoundException  없는 상품
```

`persistence/`는 보유 카드 작업에서 추가합니다. 소스: [web](../../backend/src/main/java/com/beone/api/card/web/), [application](../../backend/src/main/java/com/beone/api/card/application/).

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

## 사용법

다른 업무 패키지가 상품 정보를 쓸 때는 `CardProductService`를 주입받습니다. 규칙 판정이 필요하면 이 서비스를 거치지 말고 [엔진 입구](benefit.md#engine-업무-api의-입구)를 부릅니다.

```java
CardProductDetail detail = cardProductService.getProduct("09174", cardProductService.today());
if (detail.ruleStatus() != RuleStatus.VERIFIED) {
	// 계산하지 않고 확인 필요로 둔다
}
```

## 테스트

- `test`: [`CardProductServiceTest`](../../backend/src/test/java/com/beone/api/card/application/CardProductServiceTest.java)(정렬·선행 0·규칙 상태·미확정 null·직접 공유·시작 시 표 대조·서울 날짜, 저장소 실제 규칙과 표 일치), [`SupportStatusTest`](../../backend/src/test/java/com/beone/api/card/application/SupportStatusTest.java)(판정 순서), `RuleCatalogTest#cardIdsListsEveryCardWithAnyVersionInStringOrder`
- `webTest`: [`CardProductApiWebTest`](../../backend/src/test/java/com/beone/api/card/web/CardProductApiWebTest.java)(프로필 없음, 실제 규칙 JSON: 목록·상세 응답 전체, 적용 전 날짜, 선택형·통합 한도, 없는 상품 404, `asOf` 형식 오류 400), [`CardProductLocalProfileWebTest`](../../backend/src/test/java/com/beone/api/card/web/CardProductLocalProfileWebTest.java)(local에서 헤더 없이·잘못된 헤더로도 200)
- 웹 슬라이스 주의: 상품 컨트롤러는 모든 프로필에 등록되므로 `controllers`를 지정하지 않은 `@WebSliceTest`에도 들어옵니다. 새 웹 테스트는 `controllers`로 자기 컨트롤러만 올리고, 전체 스캔이 필요한 테스트는 `@MockitoBean CardProductService`를 둡니다(#105에서 기존 웹 테스트 4개를 이렇게 고침).
- `integrationTest`: [`CardProductIntegrationTest`](../../backend/src/test/java/com/beone/api/card/CardProductIntegrationTest.java)(전체 애플리케이션에서 실제 HTTP 목록·404). 저장·사용자별 분리 대상이 없어 그 시험은 없습니다.

## 제약

- `supportStatus`는 지원 범위만 나타냅니다. 선택형이 아니고 한도 기간도 확정인데 계산식이나 전월 실적 기간이 미확정이라 계산이 막히는 서비스는 계약 값에 맞는 것이 없어 표시하지 못합니다(새 값을 만들지 않음, 결정 27 A1-5). 현재 저장소의 4종 규칙에는 이런 서비스가 없습니다. 09271 `FUEL`은 계산식이 미확정이지만 선택형이라 `UNSUPPORTED_SELECTION_RULE`입니다.
- 4종 규칙은 `VERIFIED`이지만 실제 보유 카드와 공식 SKU·약관 버전의 일치는 D-01 미결입니다.
- 상품 표시 정보는 코드의 표에 있으므로 규칙 카드를 추가하면 표도 함께 고쳐야 합니다. 둘이 다르면 서버가 뜨지 않습니다.
