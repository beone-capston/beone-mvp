# recommendation 도메인 안내

## 목적과 범위

`recommendation`은 주문 한 건에 대해 **어떤 보유 카드로 결제할지 추천을 계산해 저장하고, 저장한 결과를 다시 조회**하는 업무 API 패키지입니다(#114). 계약은 [`docs/api/recommendations.md`](../api/recommendations.md)입니다.

혜택 계산·카드 비교·동률 처리는 직접 하지 않습니다. 이 패키지는 다른 패키지의 공개 부품으로 입력을 모아 **엔진 입구**(`benefit.engine`의 [`RecommendationEngine`](../../backend/src/main/java/com/beone/api/benefit/engine/RecommendationEngine.java), [benefit 안내](benefit.md))를 한 번 호출하고, 그 결과를 계약 모양으로 옮겨 저장합니다. 엔진 결과는 다시 계산하거나 고치지 않습니다. 실제 결제는 없습니다.

구현된 범위:

- `POST /api/v1/me/orders/{orderId}/recommendations`: 추천 계산. **201**과 계산 결과를 돌려줍니다. 같은 주문이라도 부를 때마다 새 추천 ID가 생깁니다.
- `GET /api/v1/me/recommendations/{recommendationId}`: 저장한 결과 조회. 다시 계산하지 않고, 계산 응답에 `invalidated`·`invalidatedReason`만 더합니다.
- 실패 응답: 404 `ORDER_NOT_FOUND`(없거나 다른 사용자의 주문), 409 `ORDER_ALREADY_PAID`(모의 승인된 주문), 404 `RECOMMENDATION_NOT_FOUND`(없거나 형식이 틀렸거나 다른 사용자의 추천). 시연 사용자 헤더가 없거나 틀리면 공통 처리기의 400 `INVALID_REQUEST`입니다.
- `local`·`demo` 프로필에서만 등록합니다(다른 사용자 자원 API와 같음).
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
│   ├── DocumentAssembler               # 엔진 결과 → 계약 응답 본문
│   ├── RecommendationDocument          # 계약 응답 본문(저장도 이 JSON)
│   ├── RecommendationView              # 조회 결과(본문 + 무효화)
│   ├── RecommendationNotes             # noteCode → 안내 문구(한 곳에 모음)
│   ├── InvalidationReason              # ORDER_PAID > MAPPING_CHANGED > MONTHLY_STATE_CHANGED
│   ├── RecommendationId                # rec- + 번호
│   └── OrderAlreadyPaidException, RecommendationNotFoundException
└── persistence
    ├── RecommendationEntity / RecommendationRepository
    └── RecommendationCardBasisEntity / RecommendationCardBasisRepository
```

주요 파일: [RecommendationService](../../backend/src/main/java/com/beone/api/recommendation/application/RecommendationService.java), [EngineInputAssembler](../../backend/src/main/java/com/beone/api/recommendation/application/EngineInputAssembler.java), [DocumentAssembler](../../backend/src/main/java/com/beone/api/recommendation/application/DocumentAssembler.java), [V7 마이그레이션](../../backend/src/main/resources/db/migration/V7__recommendations.sql).

## 다른 패키지와의 관계

모두 **읽기만** 합니다. 다른 패키지의 코드는 바꾸지 않았습니다.

| 읽는 것 | 공개 부품 | 쓰는 곳 |
|---|---|---|
| 주문 | `order`의 `OrderService.get` | 주문 입력, 판정일, 모의 승인 여부 |
| 보유 카드 | `card`의 `UserCardService.list`(해제 카드 제외) | 엔진에 넘길 카드, 표시 이름·매핑 |
| 월간 상태 | `monthlystate`의 `MonthlyStateService.get`(주문 월) | 전월 실적·버킷 사용액·택1·등록일 |
| 결제 전 설정 | `preference`의 `PaymentPreferenceService.get` | 선택 방식, 대표 카드 |
| 엔진 | `benefit.engine`의 `RecommendationEngine` | 계산 전부 |

## 처리 흐름

### 계산(POST)

1. 주문을 읽습니다. 없거나 다른 사용자 것이면 order의 예외가 그대로 올라와 404 `ORDER_NOT_FOUND`가 됩니다.
2. 주문 상태가 `CREATED`가 아니면 409 `ORDER_ALREADY_PAID`입니다. 지금 주문 상태는 `CREATED`뿐이라 실제로는 생기지 않고, 모의 승인(B3)이 상태를 더하면 동작합니다.
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

결제 전 설정(선택 방식·대표 카드) 변경은 무효화 사유가 아닙니다. 계약의 `invalidatedReason` 값에 설정 변경이 없기 때문입니다. 저장한 추천은 **계산 당시의 결제 전 설정에 대해서만 유효**하며, 그때 넘긴 대표 카드는 `inputSnapshot.representativeUserCardId`에 남습니다. 설정을 바꿨다면 POST로 다시 계산합니다.

## 엔진 입력으로 옮기는 규칙

[`EngineInputAssembler`](../../backend/src/main/java/com/beone/api/recommendation/application/EngineInputAssembler.java)는 값을 판단하지 않고 모양만 바꿉니다.

- `policyEvidence`는 항상 `UNDETERMINED`입니다. 계약의 "정해지기 전에는 `UNDETERMINED`"를 따릅니다(결정 27 B2-2). 취소 귀속·할인 매출 실적 제외가 미확정인 카드(01664·01914·09174)는 실적에 기대는 서비스가 `확인 필요`가 될 수 있습니다.
- 가상 포트폴리오 카드(`SYNTHETIC`)는 매핑 상태 없이 카드 상품 ID를 넘깁니다. 그 밖의 카드는 `USER_CONFIRMED`일 때만 매핑 상품 ID를 넘깁니다. 후보 상품 ID는 1차에 없으므로 넘기지 않습니다.
- 월간 상태의 `UNKNOWN`은 `notEntered()`, `MANUAL`은 값·기준 기간·서울 시각(+09:00)을 그대로 넘깁니다. 기준 기간이 맞는지는 엔진이 판정하므로 `PERIOD_MISMATCH`를 만들지 않습니다.
- 카드 사용 등록일은 그 달 월간 상태의 `registeredOn`입니다(계약 "코드 `HeldCard.registeredAt`").
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

- **카드·주문 쪽 추천 연결 없음(B2-5).** 보유 카드 응답의 `invalidatedRecommendationIds`는 계속 `[]`, 주문의 `latestRecommendationId`는 계속 `null`입니다. card·order가 recommendation을 읽으면 서로 의존하게 되므로 별도 후속 작업에서 처리합니다.
- **모의 승인된 주문 없음.** `ORDER_ALREADY_PAID`와 `ORDER_PAID` 무효화는 주문 상태가 `CREATED`뿐이라 아직 실제로 생기지 않습니다. 판정 함수는 단위 테스트로 확인했고, B3가 상태를 더하면 동작합니다.
- **새 카드 등록은 무효화하지 않습니다.** 무효화는 계산 때 넘긴 카드만 비교합니다. 계산 뒤 새로 등록한 카드는 그 추천에 없었으므로 저장한 결과를 바꾸지 않습니다. 다시 계산하려면 POST를 다시 부릅니다.
- **월간 상태 변경은 스냅샷 시각으로만 판정합니다.** 같은 초 안에 다시 입력하면 시각이 같아 변경으로 보지 않습니다. 월간 상태 시각이 초 단위이기 때문입니다.
- **기준일 안내 noteCode 없음.** "기준일이 승인일이 아닌 카드" 안내([D-23](../decisions.md#d-23-확정-정책))는 엔진에 코드가 없고 계약도 정해지지 않아 만들지 않았습니다(결정 27 B2-2).
- **가상 포트폴리오는 대부분 `확인 필요`입니다.** 월간 시드가 없어(결정 27 A3) 월간 상태를 입력하기 전까지 실적에 기대는 서비스를 판정하지 못합니다.
- **D-07 응답 시간 측정은 하지 않았습니다.** 이번 범위에서 성능 측정은 생략했습니다.
- 예정 소비는 월간 합계에 반영하지 않습니다(D-18 K4, 엔진 안내 `PLANNED_SPENDING_EXCLUDED`).

## 테스트

- 단위: `RecommendationIdTest`(형식), `RecommendationNotesTest`(문구·모르는 코드 실패), `EngineInputAssemblerTest`(입력 변환, 0 대체 없음), `RecommendationServiceTest`(실제 규칙 파일로 평가 자료 C01 계산, `NOT_DETERMINED` 스냅샷 `null`, 무효화 우선순위, 다른 사용자·형식 오류 404, 모의 승인 판정).
- 웹(`controllers=` 한정): `RecommendationApiWebTest`(계약 예시 JSON, 404·409, 헤더 400), `RecommendationProfileOffWebTest`(프로필 밖 미등록).
- 통합: `RecommendationIntegrationTest`(V7 적용, 계산·저장·조회, 사용자별 분리, 월간 상태 재입력·추천 제외·해제 무효화, 가상 포트폴리오의 `확인 필요`). 추천 행이 주문·보유 카드를 참조하므로 테스트 앞뒤로 지웁니다.
