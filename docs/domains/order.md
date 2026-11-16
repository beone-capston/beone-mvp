# order 도메인 안내

## 목적과 범위

`order`는 결제할 **가상 주문**과 이번 달 **예정 소비**를 저장·조회하는 업무 API 패키지입니다(#104). 주문은 이후 추천·모의 승인이 참조하는 입력이고, 예정 소비는 1차에서 저장만 하며 추천 계산에 쓰지 않습니다([D-18 K4](../decisions.md#d-18-확정-정책)). 계약은 [`docs/api/orders.md`](../api/orders.md)입니다.

구현된 범위:

- `POST /api/v1/me/orders`(201): 주문 생성. 상태는 `CREATED`
- `GET /api/v1/me/orders/{orderId}`: 주문 상세. 없거나 다른 사용자의 주문은 404 `ORDER_NOT_FOUND`
- `GET /api/v1/me/planned-spends`: 이번 달 예정 소비 목록
- `PUT /api/v1/me/planned-spends`: 예정 소비 0~5건을 통째로 교체
- Flyway `V3__orders_planned_spends.sql`: `orders`·`planned_spends` 테이블. 시드는 없습니다. 번호는 병합 직전 다른 작업선과의 순서에 따라 바뀔 수 있습니다([D-23](../decisions.md#d-23-확정-정책) 12항).

구현하지 않은 범위:

- 주문 상태 `PAID`·`CANCELLED`로의 전이와 `ORDER_ALREADY_PAID`: 모의 결제(`payment`) 작업 몫입니다. 지금 `OrderStatus`에는 `CREATED`만 있습니다.
- 주문 상세의 `simulatedPaymentId`·`latestRecommendationId`: 계약상 "모의 승인 전"·"계산한 추천 없음"이면 `null`인 필드입니다. 모의 결제·추천이 아직 없으므로 항상 `null`이며, 그 기능을 만드는 작업이 채웁니다.
- 예정 소비의 추천 반영(D-18 K4). 응답의 `usedInRecommendation`은 항상 `false`입니다.
- 주문 수정. 입력을 바꾸려면 새 주문을 만듭니다(계약).

## 핵심 용어

- **주문(`OrderDetail`)**: 금액·주문일·선택 시각·가맹점 이름·가맹점 분류·결제 방식과 상태, 생성 시각. 만든 뒤 입력값을 고치지 않습니다.
- **예정 소비(`PlannedSpendDetail`)**: 이번 달 안에 할 예정인 소비 한 건. 사용자당 최대 5건이며 PUT으로만 바꿉니다.
- **가맹점 분류(`merchantClass`)**: 사용자나 시나리오가 확인한 업종 코드(예: `STARBUCKS_STORE`). 허용 값은 [`rules/merchant-classes.txt`](../../backend/src/main/resources/rules/merchant-classes.txt)입니다(D-23). 가맹점 이름으로 추정해 채우지 않습니다.
- **결제 방식(`PaymentMethod`)**: `DIRECT`·`ELIGIBLE_SIMPLE_PAY`·`AUTOPAY`. 엔진의 `benefit.card.PaymentMethod`와 값은 같지만 업무 입력 모델로 따로 둡니다. 엔진으로 옮기는 일은 추천 작업이 합니다.
- **이번 달**: Asia/Seoul 기준 현재 연-월. 예정 소비의 허용 범위이자 조회 범위입니다.

## 패키지 구조

[D-23](../decisions.md#d-23-확정-정책) 13항의 `web`/`application`/`persistence` 구조입니다.

```text
com.beone.api.order
├── web/
│   ├── OrderController · PlannedSpendController   API 4개, local·demo 프로필
│   ├── CreateOrderRequest · PlannedSpendsRequest  요청 본문과 Bean Validation
│   ├── CreatedOrderResponse · OrderDetailResponse · PlannedSpendsResponse  응답 data
│   ├── StrictWonDeserializer · StrictDateDeserializer · StrictTimeDeserializer · StrictPaymentMethodDeserializer  필드 단위 엄격 역직렬화
│   ├── AllowedMerchantClass(+Validator) · InCurrentMonth(+Validator)  허용 목록·이번 달 제약
│   ├── OrderValueFormats                          응답 날짜·시각 문자열
│   ├── OrderErrorCode · OrderExceptionHandler      ORDER_NOT_FOUND 404 (@Order 최우선)
│   └── PlannedSpendExceptionHandler               검증 뒤 달이 바뀐 예정 소비 → 400 INVALID_REQUEST
├── application/
│   ├── OrderService · PlannedSpendService         생성·조회·교체(사용자 ID 기준)
│   ├── OrderClock                                 order 전용 시계(Asia/Seoul)
│   ├── MerchantClassCatalog                       merchant-classes.txt 허용 목록
│   ├── OrderId · PlannedSpendId                   외부 ID 표현
│   ├── NewOrder · NewPlannedSpend · OrderDetail · PlannedSpendDetail  입력·결과 타입
│   ├── OrderStatus · PaymentMethod                열거형
│   ├── OrderNotFoundException                     없거나 다른 사용자의 주문
│   └── PlannedDateOutOfMonthException             교체 직전 재확인에서 이번 달 밖
└── persistence/
    ├── OrderEntity · OrderRepository              orders 테이블
    └── PlannedSpendEntity · PlannedSpendRepository planned_spends 테이블
```

소스: [web](../../backend/src/main/java/com/beone/api/order/web/), [application](../../backend/src/main/java/com/beone/api/order/application/), [persistence](../../backend/src/main/java/com/beone/api/order/persistence/), [마이그레이션](../../backend/src/main/resources/db/migration/V3__orders_planned_spends.sql).

## 처리 흐름

```text
POST /api/v1/me/orders (X-BeONE-Demo-User 헤더)
→ CurrentUserResolver(user 패키지)가 CurrentUser 주입. 헤더 오류는 400 INVALID_REQUEST
→ JSON 역직렬화: 금액은 JSON 정수만, 날짜 YYYY-MM-DD·시각 HH:mm:ss 문자열만 (아니면 400, errors [])
→ Bean Validation: 필수 필드, 금액 1 이상, merchantName 50자, merchantClass 허용 목록 (아니면 400, 필드 오류)
→ OrderService.create: user_id와 함께 저장, status=CREATED, createdAt=OrderClock.now()
→ 201 {"data": {... "orderId": "ord-<번호>"}}

GET /api/v1/me/orders/{orderId}
→ OrderId.parse: "ord-" + 선행 0 없는 번호가 아니면 바로 "없는 주문"
→ OrderRepository.findByOrderIdAndUserId: 다른 사용자의 주문은 찾지 않음
→ 없으면 OrderNotFoundException → 404 ORDER_NOT_FOUND (경로 값은 응답·로그에 남기지 않음)

PUT /api/v1/me/planned-spends
→ 검증: items 필수·0~5건, 각 항목 필수 필드·금액·형식·허용 목록, plannedDate가 이번 달
→ PlannedSpendService.replace: 이번 달을 다시 확인(그 사이 달이 바뀌면 지우지 않고 400 INVALID_REQUEST, errors items)
   → 사용자의 기존 예정 소비 전체(지난달 것 포함) 삭제 → 요청 순서대로 저장
→ 이번 달 목록 반환, usedInRecommendation=false
```

## 사용법

다른 업무 패키지(예: 추천)는 주문을 `OrderService.get(currentUser, orderId)`로 읽습니다. 형식 오류·없음·다른 사용자 주문은 모두 `OrderNotFoundException`입니다.

```java
OrderDetail order = orderService.get(currentUser, "ord-5001");
LocalDate judgmentDate = order.orderDate(); // D-23 1항: 판정일은 주문일
```

`orderTime`·`merchantName`은 값이 없으면 `null`입니다. 시각을 00:00으로 채우지 않습니다.

## 결정과 근거

계약에 없던 값은 구현 전에 관리자에게 묻고 사용자 결정(관리 기록 결정 27, 2026-11-17)을 받았습니다.

- **주문 ID**: `ord-` + 자동 증가 번호(선행 0 없음, 예 `ord-1`). 형식이 틀린 경로 값(`5001`, `ord-007`, `ord-abc`)도 존재를 드러내지 않도록 404 `ORDER_NOT_FOUND`입니다. 계약 실패 목록에 400이 없습니다.
- **예정 소비 ID와 정렬**: `ps-` + 자동 증가 번호. 응답은 PUT 요청 `items` 순서(저장 번호 오름차순)이고, PUT마다 새 ID가 붙습니다.
- **이번 달 판정 시계**: Asia/Seoul 연-월([값 형식](../api.md#값-형식)). 공유 `Clock` 빈을 만들지 않고 `OrderClock`만 주입해 다른 기능 패키지와 빈이 겹치지 않게 했습니다. 테스트는 고정 시계로 바꿉니다.
- **조회·교체 범위**: GET은 계약 문구 "조회 범위와 같으며"에 따라 이번 달 `plannedDate`만 돌려주고, PUT은 사용자의 기존 예정 소비 전체를 지운 뒤 교체합니다.
- **허용 밖 `merchantClass`**: 계약의 "허용 값은 merchant-classes.txt 기준", "입력 검증은 업무 API가 한다"에 따라 400 `INVALID_REQUEST`입니다(필드 오류 `허용된 가맹점 분류가 아닙니다.`). 목록은 새 파서를 만들지 않고 규칙 로더와 같은 공개 파서 `benefit.rule.loading.MerchantClassAllowlist.parse`를 읽기 전용으로 써서 해석 규칙이 갈라지지 않게 했습니다. `benefit` 코드는 바꾸지 않았습니다.
- **엄격 역직렬화**: Jackson 기본 설정은 `"10000"`·`10000.5`를 숫자로 바꿔 받고, 날짜에 시각이 붙어도 받습니다. 공유 Jackson 설정을 바꾸지 않고 order DTO 필드에만 역직렬화기를 붙여 계약의 "소수·문자열 금액은 400"과 값 형식을 지킵니다. `paymentMethod`도 이름 문자열만 받습니다(Jackson 기본은 숫자를 열거 순번으로 받을 수 있음, PR #107 리뷰). 이 경로의 400은 `errors: []`입니다(공통 처리기의 역직렬화 실패 처리).
- **달 경계**: 웹 검증과 서비스가 "이번 달"을 각각 읽으므로 서울 기준 말일 자정 직전 요청은 검증 통과 뒤 달이 바뀔 수 있습니다. 서비스가 교체 직전에 다시 확인해 기존 값을 지우지 않고 400 `INVALID_REQUEST`(`{"field":"items","message":"이번 달 날짜여야 합니다."}`)로 끝냅니다(PR #107 리뷰).
- **응답 문자열 형식**: Jackson의 ISO 출력은 초가 0이면 초를 생략하므로(`10:40`), 시각·생성 시각은 `HH:mm:ss`·`+09:00` 형식 문자열로 직접 만듭니다.

## 테스트

- `test`: [`OrderIdTest`](../../backend/src/test/java/com/beone/api/order/application/OrderIdTest.java)(ID 형식), [`OrderClockTest`](../../backend/src/test/java/com/beone/api/order/application/OrderClockTest.java)(서울 기준 달 경계), [`PlannedSpendServiceTest`](../../backend/src/test/java/com/beone/api/order/application/PlannedSpendServiceTest.java)(교체 직전 달 재확인), [`MerchantClassCatalogTest`](../../backend/src/test/java/com/beone/api/order/application/MerchantClassCatalogTest.java)(계약 예시 코드 허용, 대소문자·공백 거부), [`StrictDeserializerTest`](../../backend/src/test/java/com/beone/api/order/web/StrictDeserializerTest.java), [`OrderValueFormatsTest`](../../backend/src/test/java/com/beone/api/order/web/OrderValueFormatsTest.java)
- `webTest`: [`OrderApiWebTest`](../../backend/src/test/java/com/beone/api/order/web/OrderApiWebTest.java)(계약 예시 응답, 계약 실패 목록 전부, 허용 밖 분류, 50자, 이번 달 경계, 시연 사용자 헤더 없음·잘못된 값)
- `integrationTest`: [`OrderIntegrationTest`](../../backend/src/test/java/com/beone/api/order/OrderIntegrationTest.java)(V3 적용과 `ddl-auto: validate`, 실제 HTTP 저장·조회, 다른 시연 사용자 주문 404, 예정 소비 사용자별 분리·요청 순서·교체·지난달 행 처리, 거부된 PUT은 기존 값 유지)

## 제약과 남은 위험

- 같은 사용자의 PUT 두 건이 동시에 들어오면 삭제·저장이 섞여 5건을 넘을 수 있습니다. 시연 사용자 2명의 로컬 환경이라 잠금을 두지 않았습니다.
- `merchantName`의 빈 문자열(`""`)은 계약에 규칙이 없어 그대로 받습니다. 엔진 입력으로 옮길 때 처리 방식은 추천 작업에서 확인합니다.
- `merchantName` 50자는 Java 문자열 길이(UTF-16) 기준입니다. 한글은 글자당 1이고, 이모지처럼 보조 문자는 2로 셉니다.
- 주문과 예정 소비는 `user_id` 값으로만 사용자를 참조하고(외래 키는 DB에 있음) `user` 패키지 엔티티에 연관을 두지 않습니다.
