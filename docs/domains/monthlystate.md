# monthlystate 도메인 안내

## 목적과 범위

`monthlystate`는 보유 카드 한 장의 **월간 상태**를 조회하고 사용자가 직접 입력한 값으로 저장하는 업무 API 패키지입니다(#110). 월간 상태란 추천 계산에 필요한 그 달의 카드 상태값입니다. 전월 실적, 한도 묶음(여러 혜택이 함께 쓰는 할인 한도 단위)별 사용액, 나머지 한도 묶음 사용액, 택1 서비스 선택, 카드 사용 등록일이 여기에 들어갑니다. 계약은 [`docs/api/monthly-states.md`](../api/monthly-states.md)입니다.

구현된 범위:

- `GET /api/v1/me/cards/{userCardId}/monthly-states/{month}`: 이 카드·이 달의 상태. 입력한 적이 없는 달도 404가 아니라 모든 값이 `UNKNOWN`인 200 응답입니다.
- `PUT /api/v1/me/cards/{userCardId}/monthly-states/{month}`: 이 카드·이 달의 수동 입력(`MANUAL`)을 통째로 교체합니다. 저장 시각이 `snapshotAt`이자 모든 값의 `updatedAt`입니다.
- Flyway `V5__monthly_states.sql`: `monthly_states`(카드·월마다 한 행)와 자식 테이블 `monthly_state_bucket_usages`·`monthly_state_service_selections`. 시드는 없습니다. 번호는 병합 직전 다른 작업선과의 순서에 따라 바뀔 수 있습니다([D-23](../decisions.md#d-23-확정-정책) 12항).

구현하지 않은 범위:

- 스냅샷 뒤 모의 승인 합산. 계약은 `snapshotAt` 뒤의 모의 승인을 체인 버킷 사용액에 더하라고 합니다([D-16](../decisions.md#d-15d-16d-17-확정-정책)). 모의 결제(B3)가 아직 없어 이 합산이 없습니다. 조회 응답의 `afterSnapshot`은 승인이 실제로 0건이므로 `{0, 0, 0}`입니다. **B3에서 실제 합산을 연결해야 합니다.** 그 전까지 이 값은 "모의 결제가 없다"는 사실만 나타냅니다.
- 2차 출처 충돌 선택 `source-selections`와 CODEF 값. `conflicts`는 항상 `[]`, `referencedCodef`는 항상 `null`, `source`는 `MANUAL`·`UNKNOWN`만 나옵니다.
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

- [`MonthlyStateController`](../../backend/src/main/java/com/beone/api/monthlystate/web/MonthlyStateController.java): 두 API. 현재 사용자는 `CurrentUser` 인자로만 받고 `local`·`demo` 프로필에서만 등록됩니다. 경로 `month`는 [`MonthParameter`](../../backend/src/main/java/com/beone/api/monthlystate/web/MonthParameter.java)가 `YYYY-MM`으로만 받습니다.
- [`MonthlyStateRequest`](../../backend/src/main/java/com/beone/api/monthlystate/web/MonthlyStateRequest.java): PUT 본문. 금액은 JSON 정수만([`StrictWonDeserializer`](../../backend/src/main/java/com/beone/api/monthlystate/web/StrictWonDeserializer.java)), 날짜는 `YYYY-MM-DD`만([`StrictDateDeserializer`](../../backend/src/main/java/com/beone/api/monthlystate/web/StrictDateDeserializer.java)), 알 수 없는 필드는 거부합니다. 필드 사이 규칙은 [`CrossFieldChecked`](../../backend/src/main/java/com/beone/api/monthlystate/web/CrossFieldChecked.java)로 필드 경로와 함께 알립니다.
- [`MonthlyStateResponse`](../../backend/src/main/java/com/beone/api/monthlystate/web/MonthlyStateResponse.java): 조회 응답. 입력 응답 `MonthlyStateResponse.Saved`는 `afterSnapshot`만 없습니다(계약 예시).
- [`MonthlyStateExceptionHandler`](../../backend/src/main/java/com/beone/api/monthlystate/web/MonthlyStateExceptionHandler.java): 이 컨트롤러에만 적용되는 오류 처리기. `common.web`은 바꾸지 않았습니다.
- [`MonthlyStateService`](../../backend/src/main/java/com/beone/api/monthlystate/application/MonthlyStateService.java): 카드 확인 → 매핑 확인 → 규칙 ID 검사 → 교체 저장 → 조회 모델 변환을 맡습니다.
- [`MonthlyStateInput`](../../backend/src/main/java/com/beone/api/monthlystate/application/MonthlyStateInput.java) / [`MonthlyStateView`](../../backend/src/main/java/com/beone/api/monthlystate/application/MonthlyStateView.java): 웹 계층과 서비스 사이의 입력·조회 모델입니다.
- [`MonthlyStateEntity`](../../backend/src/main/java/com/beone/api/monthlystate/persistence/MonthlyStateEntity.java), `BucketUsageEntity`, `ServiceSelectionEntity`: 테이블 3개. 사용자·보유 카드는 ID 값으로만 참조하고 `card` 엔티티에 연관을 두지 않습니다.

다른 패키지와의 관계: 보유 카드 존재·소유·매핑은 `card`의 공개 부품 `UserCardService.list`로, 버킷·서비스 ID는 `CardProductService.getProduct`로 확인합니다. 둘 다 읽기만 하고 `card`·`benefit` 코드는 바꾸지 않았습니다.

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
- **`afterSnapshot`(A3-1)**: 조회 응답에만 있습니다. 입력이 있으면 `{0, 0, 0}`, 입력이 없으면 `null`입니다. 위의 "구현하지 않은 범위"를 보세요.
- **형식과 검사 순서(A3-9·A3-10)**: `month`는 `YYYY-MM`만 받고(`2026-1`, `2026-13`은 400) 범위 제한은 없습니다. 검사 순서는 400(형식) → 404 → 409 → 400(규칙 ID)입니다. 알 수 없는 필드는 400(`errors: []`)입니다. 해제한 카드는 404입니다.

## 테스트

- 단위 [`MonthlyStateServiceTest`](../../backend/src/test/java/com/beone/api/monthlystate/application/MonthlyStateServiceTest.java): 실제 규칙 파일(카드 4종)로 ID 검사, 매핑 409, 교체·생략, 매핑 변경 뒤 숨김을 확인합니다.
- 웹 [`MonthlyStateApiWebTest`](../../backend/src/test/java/com/beone/api/monthlystate/web/MonthlyStateApiWebTest.java): `@WebSliceTest(controllers = MonthlyStateController.class)`로 계약 JSON, 형식 400, 오류 코드를 확인합니다.
- 통합 [`MonthlyStateIntegrationTest`](../../backend/src/test/java/com/beone/api/monthlystate/MonthlyStateIntegrationTest.java): Testcontainers MySQL에서 V5 적용, 저장·조회, 같은 달 첫 저장 동시 요청, 달별·사용자별 분리, 해제 카드 404를 확인합니다. 보유 카드 테스트가 수동 카드 행을 지우므로 이 테스트는 자기가 만든 월간 상태 행을 앞뒤로 지웁니다.

## 제약과 남은 위험

- 같은 카드·같은 달의 동시 저장은 행 확보 뒤 쓰기 잠금으로 차례로 처리합니다. 마지막에 커밋한 요청의 값이 남습니다. 요청이 아주 많이 몰리면 MySQL이 교착을 감지해 한 요청을 실패시킬 수 있고, 재시도는 하지 않습니다.
- 버킷 사용액이 한도를 넘어도 저장합니다(계약). 넘는지는 엔진이 판단합니다.
- 다른 테스트나 작업이 `user_cards` 행을 지우려면 먼저 그 카드의 `monthly_states` 행을 지워야 합니다(외래 키).
