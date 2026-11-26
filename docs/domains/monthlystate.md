# monthlystate 도메인 안내

## 목적과 범위

`monthlystate`는 보유 카드 한 장의 **월간 상태**를 조회하고 사용자가 직접 입력한 값으로 저장하는 업무 API 패키지입니다(#110). 월간 상태란 추천 계산에 필요한 그 달의 카드 상태값입니다. 전월 실적, 한도 묶음(여러 혜택이 함께 쓰는 할인 한도 단위)별 사용액, 나머지 한도 묶음 사용액, 택1 서비스 선택, 카드 사용 등록일이 여기에 들어갑니다. 계약은 [`docs/api/monthly-states.md`](../api/monthly-states.md)입니다.

구현된 범위:

- `GET /api/v1/me/cards/{userCardId}/monthly-states/{month}`: 이 카드·이 달의 상태. 입력한 적이 없는 달도 404가 아니라 모든 값이 `UNKNOWN`인 200 응답입니다.
- `PUT /api/v1/me/cards/{userCardId}/monthly-states/{month}`: 이 카드·이 달의 수동 입력(`MANUAL`)을 통째로 교체합니다. 저장 시각이 `snapshotAt`이자 모든 값의 `updatedAt`입니다.
- `PUT /api/v1/me/cards/{userCardId}/monthly-states/{month}/source-selections`(#134): 출처 충돌 계산 기준 선택. 요청 검증(400)과 카드 확인(404) 뒤 **충돌이 없으면 항상 409 `SOURCE_CONFLICT_NOT_FOUND`**이며 지금은 충돌이 생기는 경로가 없어 이 응답으로만 끝나고 아무것도 저장하지 않습니다([출처 충돌 선택](#출처-충돌-선택-134)).
- 로컬 회원 프로필 `member` 등록(G3a #159): 세 API를 `member`에도 등록했습니다. 현재 사용자는 회원 세션의 '내 카드' 장부이고, 세션 401·쓰기 CSRF 403·`Cache-Control: no-store`는 `/api/v1/me/**` 공통 인터셉터·필터가 컨트롤러 전에 처리합니다([docs/api.md 회원 세션](../api.md#회원-세션member-프로필), 결정 37). 그래서 검사 순서는 401 → 403 → 400 → 404 → 409입니다. 카드 소유 확인이 `user_id` 기준이므로 남의 카드·시연 장부의 카드는 없는 카드와 같은 404입니다. 출처 충돌 선택은 `member`에서도 항상 409 `SOURCE_CONFLICT_NOT_FOUND`이고 저장하지 않습니다(결정 32 Q1, 결정 36). 서비스 코드는 바꾸지 않았습니다.
- CODEF 실적 참고값 미리 채우기·확인 저장·충돌 표시(#281, [D-16 변경 이력 2026-11-27](../decisions.md#d-15d-16d-17-확정-정책)): 조회·입력 응답의 전월 실적에 카드사 조회 실적(참고) `codefReference`를 붙이고(계산 미사용), PUT에서 `referencedCodefId`를 받으면 참고한 값을 `referencedCodef`로 남깁니다. 같은 기준 기간의 최신 참고값과 값이 다르면 `conflicts[]`와 `usable=false`(`SOURCE_CONFLICT`)입니다([아래 절](#codef-실적-참고값-281)).
- Flyway `V5__monthly_states.sql`: `monthly_states`(카드·월마다 한 행)와 자식 테이블 `monthly_state_bucket_usages`·`monthly_state_service_selections`. 시드는 없습니다. 번호는 병합 직전 다른 작업선과의 순서에 따라 바뀔 수 있습니다([D-23](../decisions.md#d-23-확정-정책) 12항).

구현하지 않은 범위:

- 상태값에 스냅샷 뒤 승인을 더하지 않습니다. 계약대로 `afterSnapshot`은 화면 표시용 요약이고 상태값을 덮어쓰지 않습니다. 버킷 사용액에 스냅샷 뒤 승인 차감액을 더하는 일은 추천 입력을 만들 때 recommendation이 합니다([D-16](../decisions.md#d-15d-16d-17-확정-정책), [recommendation 안내](recommendation.md)).
- 출처 충돌의 계산 기준 선택(200 응답, 선택 저장, `SOURCE_CONFLICT_CHANGED`)과 CODEF 값의 자동 반영. 충돌은 PUT 재저장으로 해소하고(사용자 결정 2026-11-27 Q3), `source-selections`는 계속 409입니다. 월간 상태 값의 `source`는 `MANUAL`·`UNKNOWN`만 나옵니다. CODEF 참고값은 `codefReference`·`referencedCodef`·충돌 후보로만 보입니다.
- 한도 묶음 사용액·나머지 사용액의 CODEF 기반 추정(`INFERRED`). 후속 작업(T4)입니다.
- 기준 기간 판단. 받은 `period`가 카드의 실적·한도 기간과 맞는지는 저장할 때 보지 않습니다. 엔진이 판단합니다([D-22](../decisions.md#d-22-확정-정책)).
- 추천 엔진 입력(`MonthlyStateInput` 등 `benefit` 쪽 타입)으로 바꾸는 변환. 추천 작업(B2)이 이 서비스 결과를 읽어 바꿉니다.

## 핵심 용어

- **상태값(`StateValue`)**: 값·출처(`source`)·기준 기간(`period`)·입력 시각(`updatedAt`)을 묶은 것입니다([D-04](../decisions.md#d-03d-04-확정-정책)). 미입력은 `value=null`, `source=UNKNOWN`, `usable=false`, `reason=NOT_ENTERED`입니다. **`null`(모름)과 사용자가 입력한 0은 다릅니다.** 미입력을 0으로 바꾸지 않습니다.
- **스냅샷(`snapshotAt`)**: 그 달의 수동 입력을 마지막으로 저장한 시각입니다. 입력이 없으면 `null`입니다.
- **택1 입력 상태(`ServiceSelectionStatus`)**: `UNKNOWN`(미입력, 확인 필요), `SELECTED`(서비스를 고름), `NONE`(선택 없음을 사용자가 명시)입니다. 서버가 저장된 값으로 정하며 요청으로 받지 않습니다([D-18](../decisions.md#d-18-확정-정책) K1).
- **규칙 상품**: 버킷·서비스 ID를 해석할 카드 규칙의 상품입니다. 가상 포트폴리오 카드는 자기 카드 상품이고, 그 밖의 카드는 매핑이 `USER_CONFIRMED`일 때의 매핑 상품입니다. 없으면 매핑 확인이 필요한 카드입니다.

## 패키지 구조

```text
monthlystate
├── web          # 컨트롤러, 요청 검증, 응답 모양, 기능별 오류 처리
├── application  # 서비스, 입력·조회 모델, 기능 예외
└── persistence  # JPA 엔티티 3개와 저장소
```

- [`MonthlyStateController`](../../backend/src/main/java/com/beone/api/monthlystate/web/MonthlyStateController.java): 세 API(조회, 수동 입력, 출처 충돌 선택). 현재 사용자는 `CurrentUser` 인자로만 받고 `local`·`demo`·`member` 프로필에서 등록됩니다. 경로 `month`는 [`MonthParameter`](../../backend/src/main/java/com/beone/api/monthlystate/web/MonthParameter.java)가 `YYYY-MM`으로만 받습니다.
- [`MonthlyStateRequest`](../../backend/src/main/java/com/beone/api/monthlystate/web/MonthlyStateRequest.java): PUT 본문. 금액은 JSON 정수만([`StrictWonDeserializer`](../../backend/src/main/java/com/beone/api/monthlystate/web/StrictWonDeserializer.java)), 날짜는 `YYYY-MM-DD`만([`StrictDateDeserializer`](../../backend/src/main/java/com/beone/api/monthlystate/web/StrictDateDeserializer.java)), 알 수 없는 필드는 거부합니다. 필드 사이 규칙은 [`CrossFieldChecked`](../../backend/src/main/java/com/beone/api/monthlystate/web/CrossFieldChecked.java)로 필드 경로와 함께 알립니다.
- [`SourceSelectionRequest`](../../backend/src/main/java/com/beone/api/monthlystate/web/SourceSelectionRequest.java): 출처 충돌 선택 본문. 네 필드 필수, `item`·`selectedSource`는 계약 값만, 기간은 PUT과 같은 `PeriodRequest`, 알 수 없는 필드 거부.
- [`MonthlyStateResponse`](../../backend/src/main/java/com/beone/api/monthlystate/web/MonthlyStateResponse.java): 조회 응답. 입력 응답 `MonthlyStateResponse.Saved`는 `afterSnapshot`만 없습니다(계약 예시).
- [`MonthlyStateExceptionHandler`](../../backend/src/main/java/com/beone/api/monthlystate/web/MonthlyStateExceptionHandler.java): 이 컨트롤러에만 적용되는 오류 처리기. `common.web`은 바꾸지 않았습니다.
- [`MonthlyStateService`](../../backend/src/main/java/com/beone/api/monthlystate/application/MonthlyStateService.java): 카드 확인 → 매핑 확인 → 규칙 ID 검사 → 교체 저장 → 조회 모델 변환을 맡습니다. `selectSource`는 카드 확인 뒤 [`SourceConflictNotFoundException`](../../backend/src/main/java/com/beone/api/monthlystate/application/SourceConflictNotFoundException.java)으로 끝납니다(입력 모델 [`SourceSelection`](../../backend/src/main/java/com/beone/api/monthlystate/application/SourceSelection.java)).
- [`MonthlyStateInput`](../../backend/src/main/java/com/beone/api/monthlystate/application/MonthlyStateInput.java) / [`MonthlyStateView`](../../backend/src/main/java/com/beone/api/monthlystate/application/MonthlyStateView.java): 웹 계층과 서비스 사이의 입력·조회 모델입니다.
- [`MonthlyStateEntity`](../../backend/src/main/java/com/beone/api/monthlystate/persistence/MonthlyStateEntity.java), `BucketUsageEntity`, `ServiceSelectionEntity`: 테이블 3개. 사용자·보유 카드는 ID 값으로만 참조하고 `card` 엔티티에 연관을 두지 않습니다.

다른 패키지와의 관계: 보유 카드 존재·소유·매핑은 `card`의 공개 부품 `UserCardService.list`로, 버킷·서비스 ID는 `CardProductService.getProduct`로 확인합니다. 둘 다 읽기만 하고 `card`·`benefit` 코드는 바꾸지 않았습니다. CODEF 실적 참고값은 `codef`의 읽기 전용 창구 [`CodefPerformanceReferenceQuery`](../../backend/src/main/java/com/beone/api/codef/application/CodefPerformanceReferenceQuery.java)로만 읽습니다(monthlystate → codef 한 방향, codef는 monthlystate를 읽지 않음, #281).

## 처리 흐름

PUT 한 번의 흐름입니다. 각 단계에서 실패하면 그 자리에서 멈추고 아무것도 저장하지 않습니다.

1. 경로 `month` 형식과 본문 형식·필드 규칙을 검사합니다. 실패하면 400 `INVALID_REQUEST`입니다.
2. 현재 사용자의 해제하지 않은 보유 카드 중에서 `userCardId`를 찾습니다. 없거나 다른 사용자의 카드이면 404 `USER_CARD_NOT_FOUND`입니다.
3. 본문에 버킷 사용액·`selectedServices`·`noServiceSelection`이 있는데 규칙 상품이 없으면 409 `USER_CARD_MAPPING_REQUIRED`입니다. 전월 실적·나머지 버킷·등록일만 보낸 경우는 매핑 없이도 저장합니다.
4. 버킷·서비스 ID를 그 달 규칙과 대조합니다. 맞지 않으면 400이고 `errors`에 `limitBucketUsages[1].limitBucketId`처럼 위치를 적습니다.
5. 그 카드·그 달의 행이 없으면 먼저 빈 행을 넣고(`INSERT … ON DUPLICATE KEY UPDATE`), 그 행을 쓰기 잠금으로 읽어 값 전체와 규칙 상품 ID, 저장 시각을 덮어씁니다. 같은 달의 첫 저장 요청이 동시에 와도 유니크 제약 위반 없이 차례로 처리됩니다. 자식 행은 지운 뒤 요청 순서대로 다시 넣습니다.

```java
// 서비스 사용 예: 전월 실적만 입력하면 나머지 항목은 모두 미입력(UNKNOWN)이 된다
MonthlyStateView view = monthlyStateService.replace(currentUser, "uc-1001", YearMonth.of(2026, 11),
        new MonthlyStateInput(new MonthlyStateInput.Amount(-12000, octoberPeriod), null, null, null, null, null));
view.previousMonthPerformanceWon().value();   // -12000 (음수 그대로)
view.otherBucketsUsedWon().source();          // UNKNOWN
```

## 결정과 근거

계약에 없던 값은 관리자 판단(관리 기록 결정 27 A3절, 2026-11-17)으로 정했습니다. 계약 문서는 바꾸지 않았습니다.

- **가상 포트폴리오에는 월간 시드가 없습니다(결정 27 A3).** 시드를 만들지 않았기 때문에 가상 포트폴리오 4장의 상태는 사용자가 입력하기 전까지 모두 `UNKNOWN`입니다. **그래서 가상 포트폴리오의 추천은 상태를 입력하기 전까지 대부분 `확인 필요`가 됩니다.** 가상 포트폴리오도 PUT으로 쓸 수 있고 매핑 409가 나지 않습니다([D-19](../decisions.md#d-19-확정-정책)). 계약의 "생략하면 시드 값"은 지금 `UNKNOWN`과 같습니다.
- **음수 전월 실적은 그대로 저장하고 `usable=true`입니다(A3-8).** 사용자가 직접 입력한 값은 [D-17](../decisions.md#d-15d-16d-17-확정-정책)의 근거 확정 값입니다. 구간 판정은 엔진이 합니다. `NEGATIVE_UNCONFIRMED_ATTRIBUTION`은 취소 귀속이 확인되지 않은 음수용이라 1차 수동 입력에서는 나오지 않습니다.
- **ID 검사 기준(A3-2·A3-3)**: 그 달 1일과 말일에 적용되는 규칙 두 개의 ID 합집합과 대조합니다. 그 달에 쓸 규칙이 하나도 없으면 버킷·서비스는 모두 400입니다. `selectedServices[].serviceId`는 선택형(`requiresSelection`) 서비스여야 합니다. 계약의 "(제안)" 두 가지도 채택했습니다. 선택형 서비스가 없는 카드에 `noServiceSelection`을 보내거나, `selectedServices` 항목과 `noServiceSelection`을 함께 보내면 400입니다. `selectedServices: []`는 미입력과 같습니다.
- **적용일이 없는 선택(A3-4)**: 저장하되 그 항목은 `usable=false`, `reason=NOT_ENTERED`입니다. `noServiceSelection`도 같습니다.
- **등록일은 그 달 입력의 일부입니다(A3-5, 권장안 변경).** 계약 응답 설명은 등록일을 "카드 단위 값이라 달이 달라도 같은 값"이라고 적고 있습니다. 하지만 카드 단위로 한 값을 두면 어떤 달의 PUT에서 등록일을 생략할 때 다른 달의 값까지 지워지는 부작용이 생깁니다. 관리자 판단으로 등록일을 다른 항목처럼 (카드, 월) 상태 본문에 저장하고, "본문 교체·생략은 UNKNOWN" 규칙을 그 달 안에서만 적용합니다. 따라서 등록일을 입력하지 않은 달은 `UNKNOWN`입니다. `registeredOn.period`는 `from`·`to`가 모두 `value`와 같아야 합니다(아니면 400).
- **순서와 중복(A3-6)**: 버킷 사용액·선택은 요청 순서대로 저장하고 돌려줍니다. 같은 `limitBucketId`·`serviceId`를 두 번 보내면 400입니다. 입력하지 않은 버킷은 목록에 나오지 않습니다.
- **매핑이 바뀐 뒤(A3-7)**: 저장할 때 규칙 상품 ID를 함께 저장합니다. 조회할 때 지금의 규칙 상품과 다르면, 이전 규칙의 버킷 사용액과 택1 값은 지금 규칙의 ID가 아니므로 보여 주지 않습니다(빈 목록, `UNKNOWN`). 매핑을 해제한 경우도 같습니다. 전월 실적·나머지 버킷·등록일은 그대로 보여 줍니다.
- **`afterSnapshot`(A3-1, #120)**: 조회 응답에만 있고, 입력이 없으면(`snapshotAt`이 `null`) `null`입니다. 입력이 있으면 자체 모의 원장(`ledger`의 `LedgerQueryService.activityAfter`)에서 같은 카드·기록 시각이 `snapshotAt`보다 뒤인 행을 셉니다. 승인은 거래일이 그 달인 행, 취소는 원 승인의 거래일이 그 달인 행입니다. 취소는 원 승인 행의 거래일 월에 귀속합니다. 11월 승인을 12월에 취소하면 11월 활동이고(그 달 버킷은 확인 필요), 10월 승인을 11월에 취소한 행은 11월 활동이 아닙니다([monthly-states.md](../api/monthly-states.md) 218행, [D-17](../decisions.md#d-15d-16d-17-확정-정책), 관리자 승인 규칙). `approvalCount`는 승인 행 수, `approvedAmountWon`은 승인 금액 합(취소를 빼지 않음), `cancellationCount`는 취소 행 수입니다(관리자 승인 규칙). 같은 초에 기록된 행은 스냅샷에 들어 있을 수 있으므로 세지 않습니다. 다시 입력하면 새 `snapshotAt` 뒤의 행만 셉니다. monthlystate는 이를 위해 `ledger`를 읽습니다(ledger는 card만 읽어 순환이 없습니다).
- **형식과 검사 순서(A3-9·A3-10)**: `month`는 `YYYY-MM`만 받고(`2026-1`, `2026-13`은 400) 범위 제한은 없습니다. 검사 순서는 400(형식) → 404 → 409 → 400(규칙 ID)입니다. 알 수 없는 필드는 400(`errors: []`)입니다. 해제한 카드는 404입니다.

## CODEF 실적 참고값 (#281)

[D-16](../decisions.md#d-15d-16d-17-확정-정책)은 실적조회 금액을 "카드사 조회 실적(참고)"으로 보여 주되 전월 실적에 자동으로 넣지 않고, 사용자가 확인해 입력한 값을 `MANUAL`로 저장하면서 참고한 CODEF 값을 남기게 합니다. 2026-11-27 사용자 결정(Q1~Q3)으로 다음처럼 구현했습니다.

- **미리 채우기(`codefReference`)**: 조회 대상 월 `M`이면, 그 카드에 연결된 참고값(`codef_performance_references.user_card_id`가 이 카드) 중 반영기간이 `M-1`의 1일~말일과 정확히 같은 것의 최신 1건(조회 시각, 같으면 ID가 큰 것)입니다. 상태를 입력한 적 없는 달에도 나옵니다. `usedInCalculation=false`이고 엔진 입력으로 바꾸지 않습니다. 금액을 해석하지 못한 참고값은 `currentUseWon=null`이며 0으로 바꾸지 않습니다. 당월 조회분(종료일이 조회일 전날)과 카드 미연결 참고값은 나오지 않습니다. 화면이 이 값을 입력칸에 넣어 보여 줄 수 있지만, 저장하기 전에는 계산에 쓰이지 않습니다.
- **확인 저장(`referencedCodefId` → `referencedCodef`)**: PUT 전월 실적에 참고값 ID(`cpr-12` 형식)를 함께 보내면, 서비스가 본인의 참고값이고 이 카드에 연결됐고 반영기간이 입력한 `period`와 같은지 확인합니다(아니면 400, 필드 `previousMonthPerformanceWon.referencedCodefId`). 값은 사용자가 보낸 그대로 `MANUAL`이고, 참고값의 ID·동기화 ID·반영기간·금액·조회 시각을 `monthly_states`의 V17 열에 스냅샷으로 둡니다. 참고 없이 다시 저장하면 스냅샷은 지워집니다. 참고값 행이 지워져도 스냅샷은 남고 ID만 `NULL`이 됩니다(`ON DELETE SET NULL`).
- **충돌(Q1·Q2)**: 저장된 전월 실적과 같은 기준 기간의 최신 연결 참고값을 비교합니다. 참고값 금액이 있고 값이 다르며, 저장된 값이 바로 그 참고값을 참고한 것이 아니면 충돌입니다. 참고 없이 입력한 값도 마찬가지입니다. 값이 같거나 참고값 금액이 미확인이거나 기준 기간이 다르면 충돌이 아닙니다. 충돌 동안 전월 실적은 값을 그대로 보여 주되 `usable=false`, `reason=SOURCE_CONFLICT`이고, recommendation의 [`EngineInputAssembler`](../../backend/src/main/java/com/beone/api/recommendation/application/EngineInputAssembler.java)가 엔진에 `usable=false`로 넘겨 미입력과 같게 `확인 필요`가 됩니다. `conflicts[]`에는 `MANUAL`·`CODEF` 후보와 각각의 기준 기간·시각, `conflictVersion`(`cf-{참고값 번호}-{저장 시각}`), `selection: null`이 담깁니다.
- **해소(Q3)**: 최신 참고값의 ID로 다시 PUT합니다(값은 사용자가 정함). `source-selections`는 계속 409이고 CODEF 값을 계산 기준으로 고르지 않습니다.

```java
// 참고값 cpr-5(518,000원)를 보고 510,000원으로 확인해 저장한다
MonthlyStateView view = monthlyStateService.replace(currentUser, "uc-1001", YearMonth.of(2026, 11),
        new MonthlyStateInput(new MonthlyStateInput.Amount(510_000, octoberPeriod, 5L), null, null, null, null, null));
view.previousMonthPerformanceWon().source();   // MANUAL
view.referencedCodef().currentUseWon();        // 518000 (참고한 값, 계산 미사용)
view.conflicts();                              // [] (바로 그 참고값을 참고했으므로)
```

## 출처 충돌 선택 (#134)

계약([`source-selections`](../api/monthly-states.md#put-apiv1mecardsusercardidmonthly-statesmonthsource-selections--출처-충돌-계산-기준-선택))은 같은 카드·항목·기준 기간에 출처가 다른 값이 서로 다를 때 사용자가 계산 기준을 고르는 API입니다. 자동 우선순위는 없습니다([D-16](../decisions.md#d-15d-16d-17-확정-정책)). C0 관리자 판단 P10에 따라 다음처럼 구현했습니다.

- **충돌은 있지만 선택은 저장하지 않는 이유**: #281부터 전월 실적과 CODEF 참고값이 다르면 조회 `conflicts[]`에 충돌이 나옵니다. 그래도 이 API는 2026-11-27 사용자 결정 Q3에 따라 계속 409 `SOURCE_CONFLICT_NOT_FOUND`이고 저장하지 않습니다. CODEF 값을 계산 기준으로 고르면 D-16의 "자동 반영 안 함"과 맞지 않기 때문입니다. 해소는 최신 참고값을 보고 월간 상태를 다시 저장하는 것입니다. `INFERRED`(원장 기반 산출)는 아직 만들지 않습니다.
- **응답**: 검사 순서는 경로 `month`·본문 형식과 필드 규칙 400 → 카드 404(없거나 해제했거나 다른 사용자의 카드) → 409 `SOURCE_CONFLICT_NOT_FOUND`입니다. 수동 값이 있든 없든, 매핑 전 카드든 CODEF 카드든 같습니다. 계약의 "해당 항목에 그 출처 값이 없음" 400은 충돌 후보가 있어야 판단할 수 있어 지금은 충돌 없음 409가 먼저입니다. 한도 묶음 ID가 카드 규칙에 있는지도 보지 않습니다. 매핑 확인 409(`USER_CARD_MAPPING_REQUIRED`)는 계약 실패 목록에 없어 쓰지 않습니다.
- **가상 포트폴리오**: [docs/api.md](../api.md#업무-오류-코드)가 월간 상태는 가상 포트폴리오에서도 동작한다고 정하므로 `PORTFOLIO_READ_ONLY`가 아니라 다른 사용자와 같은 판정(자기 시드 카드 409, 남의 카드 404)입니다.
- **200을 지어내지 않음**: 서비스가 예외 없이 돌아오면 컨트롤러가 `IllegalStateException`(500)으로 끝내 성공처럼 보이지 않게 합니다.
- **확장 지점**: 월간 상태에 두 번째 출처(CODEF 값 반영이나 `INFERRED` 산출)가 결정으로 열리면 `MonthlyStateService.selectSource`에서 이 카드·항목·기준 기간의 출처별 후보를 모아 값이 다를 때만 충돌로 보고, `conflictVersion` 대조(바뀌면 409 `SOURCE_CONFLICT_CHANGED`), 고른 출처 값 없음 400, 선택 저장(새 테이블이 필요하면 새 Flyway)과 200 응답, 조회 `conflicts[]`를 함께 만듭니다. `SOURCE_CONFLICT_CHANGED`는 그때 오류 코드에 더합니다.

## 테스트

- 단위 [`MonthlyStateServiceTest`](../../backend/src/test/java/com/beone/api/monthlystate/application/MonthlyStateServiceTest.java): 실제 규칙 파일(카드 4종)로 ID 검사, 매핑 409, 교체·생략, 매핑 변경 뒤 숨김을 확인합니다. 출처 충돌 선택은 확인·매핑 전·CODEF·시드 카드 모두 충돌 없음이고 아무것도 저장하지 않는지, 없는 카드는 404인지 봅니다. #281: 참고값 미리 채우기(입력 전에도, 계산 미사용), 미확인 금액 `null`·충돌 아님, 참고 없는 값의 충돌과 `SOURCE_CONFLICT`, 같은 값·다른 기간은 충돌 아님, 참고한 바로 그 값은 충돌 아님·재조회로 달라지면 다시 충돌·새 값이 같으면 해소, 참고 저장 스냅샷, 참고 없는 재저장의 스냅샷 삭제, 다른 카드·기간·사용자 참고값 400과 미저장.
- 단위 [`CodefPerformanceReferenceQueryTest`](../../backend/src/test/java/com/beone/api/codef/application/CodefPerformanceReferenceQueryTest.java)·`EngineInputAssemblerTest`(#281): 카드 미연결·기간 없는 참고값 제외, 미확인 금액 유지, `cpr-` 형식, 충돌 값을 엔진에 `usable=false`(`SOURCE_CONFLICT`)로 넘김.
- 웹 [`MonthlyStateApiWebTest`](../../backend/src/test/java/com/beone/api/monthlystate/web/MonthlyStateApiWebTest.java): `@WebSliceTest(controllers = MonthlyStateController.class)`로 계약 JSON, 형식 400, 오류 코드를 확인합니다. #281: `codefReference`·`referencedCodef`·`conflicts[]` JSON, 미확인 금액·지워진 참고값 `null`, `referencedCodefId` 전달·형식 400, 다른 금액 항목의 `referencedCodefId`는 알 수 없는 필드 400.
- 웹 [`SourceSelectionApiWebTest`](../../backend/src/test/java/com/beone/api/monthlystate/web/SourceSelectionApiWebTest.java): 출처 충돌 선택의 계약 요청 409, 가상 포트폴리오 409, 카드 404, 서비스가 예외 없이 돌아오면 500, 항목·출처 값 전부, 필드 400, 본문 형식 400, `month` 400.
- 통합 [`MonthlyStateIntegrationTest`](../../backend/src/test/java/com/beone/api/monthlystate/MonthlyStateIntegrationTest.java): Testcontainers MySQL에서 V5 적용, 저장·조회, 같은 달 첫 저장 동시 요청, 달별·사용자별 분리, 해제 카드 404, 출처 충돌 선택(수동 값이 있어도 409·저장 변화 없음, 가상 포트폴리오 409, 남의 카드·없는 카드 404)을 확인합니다. 보유 카드 테스트가 수동 카드 행을 지우므로 이 테스트는 자기가 만든 월간 상태 행을 앞뒤로 지웁니다.
- 통합 [`MonthlyStateCodefReferenceIntegrationTest`](../../backend/src/test/java/com/beone/api/monthlystate/MonthlyStateCodefReferenceIntegrationTest.java)(#281): V17 적용 MySQL에서 참고값 미리 채우기(당월 조회분·카드 미연결 제외) → 참고 확인 저장과 DB 스냅샷 → 재조회로 다른 값이면 충돌·`source-selections` 409 → 최신 참고값으로 재저장해 해소 → 참고값 행 삭제 뒤 스냅샷 유지, 참고 없는 값의 충돌, 다른 카드·기간·없는 참고값 400.
- 통합 [`MemberMonthlyStatePreferenceIntegrationTest`](../../backend/src/test/java/com/beone/api/auth/web/MemberMonthlyStatePreferenceIntegrationTest.java)(`member`): 회원 장부 저장·조회, 회원 A·B 분리, 시연 카드 404와 시연 헤더 무시, 401, CSRF 403·미저장(403이 출처 충돌 409보다 먼저), 400이 404보다 먼저, 남의 카드와 없는 카드의 404 본문 동일, 출처 충돌 선택 409·미저장, 월간 상태 재입력이 그 회원의 추천만 `MONTHLY_STATE_CHANGED`로 무효화, no-store. 이 테스트는 G3a 당시 추천 API가 `member`에 없어 추천을 서비스로 직접 만듭니다. 결제 설정 변경 뒤 추천 유효(결정 32 Q3)는 G3b(#160)의 [`MemberOrderRecommendationIntegrationTest`](../../backend/src/test/java/com/beone/api/auth/web/MemberOrderRecommendationIntegrationTest.java)가 API로 다시 확인합니다.

## 제약과 남은 위험

- 같은 카드·같은 달의 동시 저장은 행 확보 뒤 쓰기 잠금으로 차례로 처리합니다. 마지막에 커밋한 요청의 값이 남습니다. 요청이 아주 많이 몰리면 MySQL이 교착을 감지해 한 요청을 실패시킬 수 있고, 재시도는 하지 않습니다.
- 버킷 사용액이 한도를 넘어도 저장합니다(계약). 넘는지는 엔진이 판단합니다.
- 다른 테스트나 작업이 `user_cards` 행을 지우려면 먼저 그 카드의 `monthly_states` 행을 지워야 합니다(외래 키).
- 충돌은 조회할 때마다 최신 참고값으로 다시 계산합니다. 저장된 충돌 기록은 없습니다. 참고값의 `current_use_won`이 카드사 인정 실적과 같은지는 확인되지 않았습니다(D-16 재결정 조건). 그래서 값이 달라도 사용자가 확인하기 전에는 어느 쪽도 자동으로 쓰지 않습니다.
- 참고값과 값이 다른 사용자는 다시 저장하기 전까지 그 달 전월 실적이 `확인 필요`입니다(Q1·Q2의 의도된 결과).
