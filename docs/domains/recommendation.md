# recommendation 도메인 안내

## 목적과 범위

`recommendation`은 주문 한 건에 대해 **어떤 보유 카드로 결제할지 추천을 계산해 저장하고, 저장한 결과를 다시 조회**하는 업무 API 패키지입니다(#114). 계약은 [`docs/api/recommendations.md`](../api/recommendations.md)입니다.

혜택 계산·카드 비교·동률 처리는 직접 하지 않습니다. 이 패키지는 다른 패키지의 공개 부품으로 입력을 모아 **엔진 입구**(`benefit.engine`의 [`RecommendationEngine`](../../backend/src/main/java/com/beone/api/benefit/engine/RecommendationEngine.java), [benefit 안내](benefit.md))를 한 번 호출하고, 그 결과를 계약 모양으로 옮겨 저장합니다. 엔진 결과는 다시 계산하거나 고치지 않습니다. 실제 결제는 없습니다.

구현된 범위:

- `POST /api/v1/me/orders/{orderId}/recommendations`: 추천 계산. **201**과 계산 결과를 돌려줍니다. 같은 주문이라도 부를 때마다 새 추천 ID가 생깁니다.
- `GET /api/v1/me/recommendations/{recommendationId}`: 저장한 결과 조회. 다시 계산하지 않고, 계산 응답에 `invalidated`·`invalidatedReason`만 더합니다.
- 실패 응답: 404 `ORDER_NOT_FOUND`(없거나 다른 사용자의 주문), 409 `ORDER_ALREADY_PAID`(모의 승인된 주문), 404 `RECOMMENDATION_NOT_FOUND`(없거나 형식이 틀렸거나 다른 사용자의 추천). 시연 사용자 헤더가 없거나 틀리면 공통 처리기의 400 `INVALID_REQUEST`입니다.
- `local`·`demo`·`member` 프로필에서 등록합니다. `member`(G3b #160)에서는 회원 세션의 '내 카드' 장부가 현재 사용자이고, 세션 401·CSRF 403·`Cache-Control: no-store`는 `/api/v1/me/**` 공통 인터셉터·필터가 처리합니다(결정 37). 계산은 추천 행을 저장하는 POST라서 CSRF 토큰이 필요합니다. 남의 주문·추천과 시연 장부의 주문·추천은 없는 것과 같은 404입니다. 무효화 규칙(추천 제외 변경은 `MAPPING_CHANGED`, 결제 설정 변경은 무효화 없음, 결정 32 Q2·Q3)은 프로필과 관계없이 같습니다. 서비스 코드는 바꾸지 않았습니다.
- Flyway `V7__recommendations.sql`: `recommendations`, `recommendation_card_bases`. 시드는 없습니다. 번호는 다른 작업선과 나눈 **임시 번호**이며, 병합 직전 관리자가 정한 순서에 따라 바뀔 수 있습니다(D-23 12항).

구현하지 않은 범위는 [제약과 한계](#제약과-한계)에 모았습니다.

## 핵심 용어

- **엔진 입구**: `RecommendationEngine.recommend(EngineRequest)`. 판정일, 주문, 결제 선택 방식, 대표 카드, 보유 카드 목록을 받아 `EngineResult`(선택 카드, 비교 카드, 제외 카드, 안내 코드)를 돌려줍니다.
- **비교 카드·제외 카드**: 엔진이 금액을 확정해 비교한 카드(`comparedCards`)와, 매핑 미확인·추천 제외·`확인 필요` 등으로 비교에서 뺀 카드(`excludedCards`)입니다.
- **확인 필요(`NEEDS_CONFIRMATION`)**: 입력이 없거나 규칙이 미확정이라 금액을 정하지 못한 상태입니다. 금액은 `0`이 아니라 `null`로 내보냅니다.
- **무효화**: 계산 뒤 입력이 바뀌어 저장한 추천을 승인 근거로 쓸 수 없게 된 상태입니다. 조회할 때 판정합니다(지연 판정).
- **카드 근거(card basis)**: 계산 때 엔진에 넘긴 보유 카드 한 장의 매핑 상태·규칙 상품 ID·추천 제외 여부·월간 상태 스냅샷 시각입니다. 무효화 판정에 씁니다.

## 패키지 구조

```text
recommendation
├── web
│   ├── RecommendationController        # API 2개, CurrentUser 인자만 받음
│   ├── RecommendationDetailResponse    # 조회 응답(계산 응답 + 무효화 필드)
│   ├── RecommendationErrorCode         # ORDER_NOT_FOUND·ORDER_ALREADY_PAID·RECOMMENDATION_NOT_FOUND
│   └── RecommendationExceptionHandler  # 기능별 처리기(@Order 최우선), order의 OrderNotFoundException도 변환
├── application
│   ├── RecommendationService           # 입력 수집 → 엔진 호출 → 저장, 조회와 무효화 판정
│   ├── EngineInputAssembler            # 업무 조회 결과 → 엔진 입력(모양만 바꿈)
│   ├── SnapshotUsage                   # 스냅샷 뒤 모의 승인 차감액을 버킷 사용액에 반영(#120)
│   ├── RecommendationLinks             # card·order 조회 포트 구현: 마지막 추천, 카드 변경으로 무효가 된 추천(#120)
│   ├── DocumentAssembler               # 엔진 결과 → 계약 응답 본문
│   ├── RecommendationDocument          # 계약 응답 본문(저장도 이 JSON)
│   ├── RecommendationView              # 조회 결과(본문 + 무효화)
│   ├── RecommendationNotes             # noteCode → 안내 문구(한 곳에 모음)
│   ├── InvalidationReason              # ORDER_PAID > MAPPING_CHANGED > MONTHLY_STATE_CHANGED
│   ├── RecommendationId                # rec- + 번호
│   ├── RecommendationCalculation       # 모의 승인용 계산 결과(본문 + 카드별 한도 버킷 차감액, #116)
│   └── OrderAlreadyPaidException, RecommendationNotFoundException
└── persistence
    ├── RecommendationEntity / RecommendationRepository
    └── RecommendationCardBasisEntity / RecommendationCardBasisRepository
```

주요 파일: [RecommendationService](../../backend/src/main/java/com/beone/api/recommendation/application/RecommendationService.java), [EngineInputAssembler](../../backend/src/main/java/com/beone/api/recommendation/application/EngineInputAssembler.java), [DocumentAssembler](../../backend/src/main/java/com/beone/api/recommendation/application/DocumentAssembler.java), [V7 마이그레이션](../../backend/src/main/resources/db/migration/V7__recommendations.sql).

## 다른 패키지와의 관계

모두 **읽기만** 합니다. 반대로 card·order는 자기 패키지의 조회 포트로만 추천을 읽고 구현은 이 패키지의 `RecommendationLinks`에 있습니다(#120).

| 읽는 것 | 공개 부품 | 쓰는 곳 |
|---|---|---|
| 주문 | `order`의 `OrderService.get` | 주문 입력, 판정일, 모의 승인 여부 |
| 보유 카드 | `card`의 `UserCardService.list`(해제 카드 제외) | 엔진에 넘길 카드, 표시 이름·매핑 |
| 월간 상태 | `monthlystate`의 `MonthlyStateService.get`(주문 월) | 전월 실적·버킷 사용액·택1·등록일 |
| 결제 전 설정 | `preference`의 `PaymentPreferenceService.get` | 선택 방식, 대표 카드 |
| 원장(#120) | `ledger`의 `LedgerQueryService.activityAfter` | 스냅샷 뒤 모의 승인 차감액·취소 |
| 엔진 | `benefit.engine`의 `RecommendationEngine` | 계산 전부 |

## 처리 흐름

### 계산(POST)

1. 주문을 읽습니다. 없거나 다른 사용자 것이면 order의 예외가 그대로 올라와 404 `ORDER_NOT_FOUND`가 됩니다.
2. 주문 상태가 `CREATED`가 아니면 409 `ORDER_ALREADY_PAID`입니다. 모의 승인(#116)으로 `PAID`·`CANCELLED`가 된 주문이 여기에 해당합니다.
3. 해제하지 않은 보유 카드 전부와 각 카드의 **주문 월** 월간 상태를 읽어 `HeldCardInput`으로 바꿉니다. 추천에서 뺀 카드와 매핑 미확인 카드도 넘기며, 비교에서 빼는 판단은 엔진이 합니다.
4. 결제 전 설정을 읽습니다. 설정이 없으면 `AUTO_RECOMMEND`입니다.
5. 엔진을 한 번 호출합니다. 판정일은 주문일입니다([D-23](../decisions.md#d-23-확정-정책)).
6. 추천 행을 넣어 번호를 받고, 응답 본문 JSON과 카드 근거를 같은 트랜잭션에서 저장합니다.

```java
EngineResult result = engine.recommend(new EngineRequest(order.orderDate(),
        EngineInputAssembler.order(order), selectionMode, representative, inputs));
```

### 조회(GET)

1. `rec-`+번호 형식이 아니거나, 이 사용자의 추천이 아니면 404 `RECOMMENDATION_NOT_FOUND`입니다. 조회는 항상 사용자 ID와 함께 합니다([D-09](../decisions.md)).
2. 저장한 본문 JSON을 그대로 돌려줍니다. 다시 계산하지 않습니다.
3. 무효화를 아래 순서로 판정해 첫 번째 사유만 넣습니다.
   1. `ORDER_PAID`: 주문 상태가 `CREATED`가 아님
   2. `MAPPING_CHANGED`: 계산 때 넘긴 카드 중 하나라도 해제됐거나, 매핑 상태·규칙 상품 ID·**추천 제외 여부**가 바뀜
   3. `MONTHLY_STATE_CHANGED`: 계산 때 넘긴 카드 중 하나라도 주문 월의 월간 상태 스냅샷 시각이 바뀜(새로 입력, 다시 입력, 비우기 포함)

결제 전 설정(선택 방식·대표 카드) 변경은 무효화 사유가 아닙니다. 계약의 `invalidatedReason` 값에 설정 변경이 없기 때문입니다. 저장한 추천은 **계산 당시의 결제 전 설정에 대해서만 유효**하며, `SELECTED`면 그때 넘긴 대표 카드가 `inputSnapshot.representativeUserCardId`에 남고, `NOT_DETERMINED`면 `inputSnapshot` 자체가 `null`이라 남지 않습니다. 설정을 바꿨다면 POST로 다시 계산합니다.

## 엔진 입력으로 옮기는 규칙

[`EngineInputAssembler`](../../backend/src/main/java/com/beone/api/recommendation/application/EngineInputAssembler.java)는 값을 판단하지 않고 모양만 바꿉니다.

- `policyEvidence`는 항상 `UNDETERMINED`입니다. 계약의 "정해지기 전에는 `UNDETERMINED`"를 따릅니다(결정 27 B2-2). 취소 귀속·할인 매출 실적 제외가 미확정인 카드(01664·01914·09174)는 실적에 기대는 서비스가 `확인 필요`가 될 수 있습니다.
- 가상 포트폴리오 카드(`SYNTHETIC`)는 매핑 상태 없이 카드 상품 ID를 넘깁니다. 그 밖의 카드는 `USER_CONFIRMED`일 때만 매핑 상품 ID를 넘깁니다. 후보 상품 ID는 1차에 없으므로 넘기지 않습니다.
- 월간 상태의 `UNKNOWN`은 `notEntered()`, `MANUAL`은 값·기준 기간·서울 시각(+09:00)을 그대로 넘깁니다. 기준 기간이 맞는지는 엔진이 판정하므로 `PERIOD_MISMATCH`를 만들지 않습니다.
- 카드 사용 등록일은 그 달 월간 상태의 `registeredOn`입니다(계약 "코드 `HeldCard.registeredAt`").
- **스냅샷 뒤 모의 승인 반영(#120, [`SnapshotUsage`](../../backend/src/main/java/com/beone/api/recommendation/application/SnapshotUsage.java))**: 월간 상태에 `snapshotAt`이 있으면 `ledger`의 `LedgerQueryService.activityAfter`로 같은 카드·기록 시각이 스냅샷보다 뒤인 원장 행(승인은 거래일이 주문 월, 취소는 원 승인의 거래일이 주문 월)을 읽어 더합니다([D-16](../decisions.md#d-15d-16d-17-확정-정책)). 승인 차감액은 `limitBucketUsages`에 입력한 같은 버킷에, 입력하지 않은 버킷의 차감액은 `otherBucketsUsedWon`에 더합니다. 더할 값이 미입력이면 미입력 그대로이고, `snapshotAt`이 없으면 원장을 읽지 않습니다. 출처는 `MANUAL`, 기준 기간·시각은 스냅샷 값 그대로입니다. 차감액을 모르는 승인이 있거나, 차감액이 0이 아닌 승인(스냅샷 전 승인 포함)을 스냅샷 뒤 취소했으면 그 버킷은 `notEntered()`로 넘겨 `확인 필요`가 됩니다. 4종 모두 한도 복원 확정 규칙이 없기 때문이며([D-17](../decisions.md#d-15d-16d-17-확정-정책)) 0으로 대신하지 않습니다(관리자 승인 규칙).
- 적용일 없는 택1 선택은 엔진 계약대로 `usable=true`·`effectiveFrom=null`로 넘기고, 엔진이 `확인 필요`로 처리합니다.

## 응답으로 옮기는 규칙

[`DocumentAssembler`](../../backend/src/main/java/com/beone/api/recommendation/application/DocumentAssembler.java)는 엔진 값을 그대로 씁니다.

- 금액은 `{amountWon, status}`이고, `확인 필요`는 `amountWon: null`입니다. `0`으로 바꾸지 않습니다.
- `services[]`에는 계약 예시에 없는 `fractionalWonTruncated`·`fractionalWonBasis`·`unevaluatedExclusions`도 항상 내보냅니다. `limits[].usedBeforeWon`은 `{value, source}`입니다.
- `excludedCards[]`는 `applicationStatus`·`appliedRule`·`pendingReasons` 세 필드를 항상 내보냅니다(값이 없으면 `null`·빈 목록).
- 비교 카드의 `displayName`은 보유 카드의 표시 이름, `mapping`은 보유 카드의 매핑 상태와 안내 문구입니다. 가상 포트폴리오 카드는 `mapping: null`입니다.
- `outcome`이 `NOT_DETERMINED`면 `inputSnapshot`은 `null`입니다(계약 예시). `SELECTED`면 주문 요약, 엔진에 넘긴 대표 카드, 카드별 월간 상태 스냅샷 시각을 담습니다. 스냅샷 시각은 입력이 있는 카드만 키로 넣습니다.
- 안내 문구는 [`RecommendationNotes`](../../backend/src/main/java/com/beone/api/recommendation/application/RecommendationNotes.java) 한 곳에서 noteCode를 바꿉니다. 모르는 코드가 오면 문구를 지어내지 않고 실패합니다. `SINGLE_COMPARED_CARD`는 "비교할 수 있는 카드가 1장이라 다른 카드와 비교한 결과가 아닙니다."입니다(결정 27 B2-1). '최적'이라는 표현은 쓰지 않습니다.

## 저장 구조

- `recommendations`: 추천 ID, 사용자, 주문(외래 키), 주문 월(`YYYY-MM`), 계산 시각, 응답 본문 JSON(`MEDIUMTEXT`). 본문에 추천 ID가 들어가므로 행을 넣어 번호를 받은 뒤 같은 트랜잭션에서 본문을 채웁니다.
- `recommendation_card_bases`: 추천마다 엔진에 넘긴 카드 한 장씩. 매핑 상태(가상 포트폴리오는 `NULL`), 규칙 상품 ID, 추천 제외 여부, 월간 상태 스냅샷 시각(입력이 없으면 `NULL`). 추천 행을 지우면 함께 지워집니다.

## 결정과 근거

관리 기록 결정 27 B2절과 결정 28(관리자 판단)로 정했습니다. 공유 계약 문서는 바꾸지 않았습니다.

- 추천 ID는 `rec-`+자동 증가 번호입니다. 형식이 틀린 값도 404로 다룹니다(B2-2).
- 계산 응답 JSON과 무효화 판정용 카드 근거를 저장하고, 조회는 다시 계산하지 않습니다(B2-3).
- 무효화는 조회할 때 저장한 근거와 지금 상태를 비교해 판정합니다(B2-4, 지연 판정). 카드나 월간 상태를 바꾸는 쪽(`card`·`monthlystate`)에서 추천 행을 갱신하지 않아도 되므로 다른 패키지를 고치지 않습니다.
- **추천 제외 변경을 `MAPPING_CHANGED`로 묶은 이유**: 보유 카드 계약([`cards.md`](../api/cards.md)의 추천 제외 설명)은 `excludedFromRecommendation`이 실제로 바뀌면 그 카드가 들어간 추천을 무효화하라고 요구합니다. 그런데 추천 계약의 `invalidatedReason`은 `MAPPING_CHANGED`·`MONTHLY_STATE_CHANGED`·`ORDER_PAID` 셋뿐입니다. 새 값을 더하면 계약 변경이므로, 추천 제외 변경을 "그 카드가 비교 대상인지가 바뀜"으로 보고 매핑 상태·규칙 상품 변경, 해제와 같은 `MAPPING_CHANGED`로 묶었습니다.
- 계약 문구 정리 후보(결제 전 설정): 설정 변경을 무효화 사유로 둘지, "계산 당시 설정에만 유효"를 계약에 적을지. 지금은 계약대로 무효화하지 않습니다.
- 계약 문구 정리 후보: `invalidatedReason` 설명에 "`MAPPING_CHANGED`는 매핑 상태·규칙 상품 변경, 카드 해제, 추천 제외 변경을 포함한다"를 적거나, 별도 값을 둘지 정하는 것. 관리자가 계약 정리 때 판단합니다.

## 제약과 한계

- **카드·주문 쪽 추천 연결(#120).** card·order가 recommendation을 직접 읽으면 순환하므로, 받는 쪽에 둔 조회 포트(`card`의 `RecommendationInvalidationLookup`, `order`의 `LatestRecommendationLookup`)를 [`RecommendationLinks`](../../backend/src/main/java/com/beone/api/recommendation/application/RecommendationLinks.java)가 구현합니다. 무효화 판정은 조회와 같은 지연 판정(`RecommendationService.invalidationOf`)을 씁니다. 보유 카드 쪽은 포트를 부를 때 찾으므로 빈 생성 순환도 없습니다.
- **엔진 사유 추가 후보.** 스냅샷 뒤 취소·모르는 차감액 때문에 확인 필요가 된 버킷은 엔진 입구에 맞는 모름 사유가 없어 `NOT_ENTERED`로 넘깁니다. 결과는 `확인 필요`로 맞지만 사유가 정확하지 않습니다. 엔진(`benefit`)에 사유를 더하는 일은 별도 작업입니다.
- **#120의 사용액 반영은 자체 모의 원장만 봅니다.** CODEF 실제 조회 거래는 2차 범위입니다. 1차 4종 규칙 중 BASE 같은 한도 없는 서비스는 버킷 차감액이 없어 반영할 것이 없습니다.
- **모의 승인(#116) 뒤 동작.** 주문 상태에 `PAID`·`CANCELLED`가 생겨 승인된 주문의 계산은 409 `ORDER_ALREADY_PAID`, 그 주문의 이전 추천 조회는 `ORDER_PAID` 무효화가 실제로 생깁니다(통합 테스트로 확인).
- **#116에서 더한 공개 메서드는 `calculateForApproval` 하나뿐입니다.** 모의 승인([payment 안내](payment.md))이 승인 직전 재계산에 씁니다. 내부 계산은 `calculate`와 같고(같은 저장·같은 본문), 엔진 결과의 `usageEffects`(카드별 한도 버킷 차감액, 모르면 `null`)를 `RecommendationCalculation`에 함께 돌려줍니다. 원장이 승인 카드의 차감액을 저장할 때 씁니다. `calculate`의 동작·응답은 바꾸지 않았고 이 밖의 recommendation 변경은 없습니다.
- **새 카드 등록은 무효화하지 않습니다.** 무효화는 계산 때 넘긴 카드만 비교합니다. 계산 뒤 새로 등록한 카드는 그 추천에 없었으므로 저장한 결과를 바꾸지 않습니다. 다시 계산하려면 POST를 다시 부릅니다.
- **월간 상태 변경은 스냅샷 시각으로만 판정합니다.** 같은 초 안에 다시 입력하면 시각이 같아 변경으로 보지 않습니다. 월간 상태 시각이 초 단위이기 때문입니다.
- **기준일 안내 noteCode 없음.** "기준일이 승인일이 아닌 카드" 안내([D-23](../decisions.md#d-23-확정-정책))는 엔진에 코드가 없고 계약도 정해지지 않아 만들지 않았습니다(결정 27 B2-2).
- **가상 포트폴리오는 대부분 `확인 필요`입니다.** 월간 시드가 없어(결정 27 A3) 월간 상태를 입력하기 전까지 실적에 기대는 서비스를 판정하지 못합니다.
- **D-07 응답 시간 측정은 하지 않았습니다.** 이번 범위에서 성능 측정은 생략했습니다.
- 예정 소비는 월간 합계에 반영하지 않습니다(D-18 K4, 엔진 안내 `PLANNED_SPENDING_EXCLUDED`).

## 테스트

- 단위(#120 추가): `SnapshotUsageTest`(같은 버킷 가산, 미입력 버킷은 그 밖의 사용액, 모르는 차감액·취소는 확인 필요, 0 대체 없음), `RecommendationLinksTest`(최대 ID, 변경 전 유효 후보, `MAPPING_CHANGED`만 오름차순), `RecommendationServiceTest`의 스냅샷 뒤 차감액 엔진 입력 반영.
- 단위: `RecommendationIdTest`(형식), `RecommendationNotesTest`(문구·모르는 코드 실패), `EngineInputAssemblerTest`(입력 변환, 0 대체 없음), `RecommendationServiceTest`(실제 규칙 파일로 평가 자료 C01 계산, `NOT_DETERMINED` 스냅샷 `null`, 무효화 우선순위, 다른 사용자·형식 오류 404, 모의 승인 판정, `calculateForApproval`이 같은 본문과 엔진 차감액을 `null` 그대로 돌려줌).
- 웹(`controllers=` 한정): `RecommendationApiWebTest`(계약 예시 JSON, 404·409, 헤더 400), `RecommendationProfileOffWebTest`(프로필 밖 미등록).
- 통합: `RecommendationIntegrationTest`(V7 적용, 계산·저장·조회, 사용자별 분리, 월간 상태 재입력·추천 제외·해제 무효화, 가상 포트폴리오의 `확인 필요`). 추천 행이 주문·보유 카드를 참조하므로 테스트 앞뒤로 지웁니다. `RecommendationLinksIntegrationTest`(#120: 주문 `latestRecommendationId`, 표시 이름·추천 제외·매핑 변경의 `invalidatedRecommendationIds`, 승인·취소 뒤 `afterSnapshot`, 실제 빈 연결). [`MemberOrderRecommendationIntegrationTest`](../../backend/src/test/java/com/beone/api/auth/web/MemberOrderRecommendationIntegrationTest.java)(`member`: 회원 A·B 분리, 남의 추천·없는 추천 404 본문 동일, 시연 장부 주문·추천 404, 401, 계산 CSRF 403·미저장, 추천 제외 변경·되돌림·매핑 해제의 `MAPPING_CHANGED`와 결제 설정 변경 뒤 유효(결정 32 Q2·Q3)를 API로 확인, 다른 회원 추천 불변). `ORDER_ALREADY_PAID`·`ORDER_PAID`는 G3c(#161)의 [`MemberPaymentLedgerIntegrationTest`](../../backend/src/test/java/com/beone/api/auth/web/MemberPaymentLedgerIntegrationTest.java)가 `member` API로 확인합니다.
