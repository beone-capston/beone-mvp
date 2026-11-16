# #104 주문 생성·조회와 예정 소비 API

브랜치 `feat/104-orders-planned-spends-api`, 시작 기준 `origin/develop` `6b344cd`(PR #103 병합).

## 2026-11-17 · `order` 패키지·Flyway V3(임시 번호) · PR(`develop` 대상, 병합 전)
- 근거: 관리 기록 결정 25(업무 API 1차, 작업선 B의 B1)·결정 27(B1 미결 답), [D-09·D-14](../../decisions.md#d-09d-14-확정-정책), [D-18 K4](../../decisions.md#d-18-확정-정책), [D-23](../../decisions.md#d-23-확정-정책) 1·12·13·14항, [업무 API 공통 규약](../../api.md#업무-api-공통-규약), [`docs/api/orders.md`](../../api/orders.md).
- 한 일:
  - `backend/src/main/java/com/beone/api/order/`(`web`/`application`/`persistence`): `POST /api/v1/me/orders`(201), `GET /api/v1/me/orders/{orderId}`, `GET`·`PUT /api/v1/me/planned-spends`. 컨트롤러는 `CurrentUser` 인자만 받고 `local`·`demo` 프로필에서만 등록합니다. `ORDER_NOT_FOUND`는 기능별 예외와 `@Order` 처리기로 냅니다(`common.web` 변경 없음).
  - Flyway `V3__orders_planned_spends.sql`: `orders`·`planned_spends`, `user_id` 외래 키, 금액 `CHECK >= 1`. 시드 없음. **임시 번호**이며 병합 직전 관리자가 정한 순서에 따라 바뀔 수 있습니다.
  - 테스트: 단위 `OrderIdTest`·`OrderClockTest`·`MerchantClassCatalogTest`·`StrictDeserializerTest`·`OrderValueFormatsTest`, 웹 `OrderApiWebTest`, 통합 `OrderIntegrationTest`. 기대값은 계약 문서에서 먼저 옮겼습니다.
  - 문서: [order 안내](../../domains/order.md) 신규, [구조 안내](../../architecture.md)의 업무 API 패키지 구조 중 `order` 줄.
- 서비스에서 달라진 점: `local`·`demo`에서 두 시연 사용자 모두 가상 주문을 만들고 다시 조회할 수 있고, 이번 달 예정 소비 0~5건을 저장·조회할 수 있습니다. 추천·모의 승인은 아직 이 주문을 쓰지 않습니다.
- 관리자·사용자 답으로 정한 것(결정 27, 2026-11-17):
  - 주문 ID `ord-` + 자동 증가 번호(선행 0 없음). 형식이 틀린 경로 값도 404 `ORDER_NOT_FOUND`.
  - 예정 소비 ID `ps-` + 자동 증가 번호, 응답은 PUT `items` 순서, PUT마다 새 ID.
  - 이번 달 = Asia/Seoul 연-월. order 전용 시계 `OrderClock`(공유 `Clock` 빈 금지). GET은 이번 달만, PUT은 사용자 기존 항목 전체 교체.
  - 허용 밖 `merchantClass`는 400 `INVALID_REQUEST`. 계약 문구("허용 값은 merchant-classes.txt 기준", "입력 검증은 업무 API가 한다")가 근거입니다. 목록은 공개 파서 `MerchantClassAllowlist.parse`를 읽기 전용으로 재사용합니다(`benefit` 수정 없음).
  - 엄격 역직렬화는 order DTO 필드 단위로만 둡니다(공유 Jackson 설정 변경 없음).
- 공유 파일 1줄 수정(관리자 승인): `backend/src/test/java/com/beone/api/user/web/DemoUserApiWebTest.java`의 `@WebSliceTest`에 `controllers = DemoUserController.class`를 넣었습니다. 이 테스트는 `local` 프로필의 모든 컨트롤러를 슬라이스에 올리는데, 다른 작업선 컨트롤러(이 PR의 `OrderController`·`PlannedSpendController`)가 섞이면 그 서비스 빈이 없어 17건이 모두 실패했습니다. 대상 컨트롤러만 올리도록 한정했고 검증 내용은 바뀌지 않습니다. `OrderApiWebTest`도 처음부터 자기 컨트롤러로 한정했습니다.
- 작은 구현 결정: 응답 시각은 Jackson ISO 출력이 초 0을 생략하므로 `HH:mm:ss`·`+09:00` 문자열로 직접 만듭니다. 생성 시각은 서울 기준 초 단위로 저장합니다. 역직렬화 실패(소수·문자열 금액, 날짜·시각 형식, 허용 밖 결제 방식)의 400은 `errors: []`이고, Bean Validation 실패는 필드 오류를 담습니다. `PaymentMethod`는 엔진 타입을 쓰지 않고 업무 입력 모델로 따로 둡니다.
- 검증: `./gradlew test webTest integrationTest build javadoc --rerun-tasks` 결과는 PR 본문에 기록합니다.
- 남은 제한: `PAID`·`CANCELLED` 전이와 `simulatedPaymentId`·`latestRecommendationId` 채우기(모의 결제·추천 작업), 같은 사용자의 동시 PUT 직렬화 없음, 빈 `merchantName` 처리(계약에 규칙 없음), Flyway 번호 확정. 서버 DB 적용·배포, frontend 변경은 하지 않았습니다.

## 2026-11-17 · CodeRabbit 리뷰 반영(head `147e9e8` 리뷰)
- 반영: 예정 소비 교체에서 웹 검증 뒤 달이 바뀌면 500이던 경로를 `PlannedDateOutOfMonthException` → `PlannedSpendExceptionHandler` 400 `INVALID_REQUEST`(errors `items`)로 바꿨습니다. 기존 값은 지우지 않습니다(`PlannedSpendServiceTest`, `OrderApiWebTest`).
- 반영: `paymentMethod`를 `StrictPaymentMethodDeserializer`로 이름 문자열만 받게 했습니다. 숫자(열거 순번)는 400입니다.
- 보류(관리자 순서 지시): `docs/architecture.md` 54·56·58행 구현 현황 문장은 A1(PR #106)이 먼저 고치므로, #106 병합 뒤 최신 `develop`을 merge하고 order 사실을 덧붙입니다.

## 2026-11-17 · 최신 `develop` merge(#105 → PR #106 병합 `cda31ca`)
- `origin/develop`을 merge했습니다(rebase 아님). 충돌은 `docs/progress/backend/README.md`의 진행 줄 하나였고 #105 줄과 이 작업 줄을 모두 남겼습니다. `DemoUserApiWebTest`는 #106과 같은 문장이라 충돌이 없었습니다.
- `docs/architecture.md`의 업무 API 구현 현황 문장과 패키지 구조 설명 문장에 #105 내용을 보존하면서 주문·예정 소비 API 4개와 `orders`·`planned_spends` 테이블을 덧붙였습니다(CodeRabbit Outside-diff 지적 반영). 패키지 구조 절 제목은 D-23 앵커 때문에 그대로 둡니다.
- Flyway: `develop`에 `V3`가 없어 `V3__orders_planned_spends.sql`을 유지합니다.
