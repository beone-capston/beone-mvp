# #178 본인 주문 목록 조회 GET /api/v1/me/orders (결정 38-5)

브랜치 `feat/178-member-order-list`, 시작 기준 `origin/feat/161-member-payment-ledger` `74e9e5c`(PR #177, 병합 전). #177과 `docs/decisions.md`·`docs/api.md`·`docs/api/orders.md`를 함께 고치므로 그 위에 쌓았습니다. #177 병합 뒤 `develop` 동기화가 필요합니다.

## 2026-11-24 · 계약 초안 → 관리자 확인 → 구현

- 근거: 결정 38-5(N-G4-ORDERLIST, 2026-11-24 사용자 직접 답). 본인이 저장한 주문의 목록 조회만 추가하고 수정·삭제·광범위 CRUD는 넣지 않습니다.
- 계약: `docs/api/orders.md`에 초안을 먼저 쓰고(`025fbef`) 관리자 확인을 받았습니다. 확인 조건은 세 가지였습니다. (a) `orderDate`(사용자 입력 주문 날짜)와 `createdAt`(저장 시각, 정렬 기준)의 차이를 문서에 밝힐 것, (b) `Slice`의 size+1 조회로 `hasNext`를 정하고 같은 `createdAt` 동률의 `orderId DESC` 정렬을 통합 테스트로 고정할 것, (c) 인덱스를 추가하지 않고 쿼리가 `user_id` 조건을 포함하는지 테스트로 확인할 것.
  - 대상: 현재 사용자 장부(`user_id`)의 모든 상태 주문. 상태 필터 없음.
  - 정렬: `createdAt` 내림차순, 같으면 주문 번호 내림차순.
  - 범위: `page`(기본 0, 0~1000)·`size`(기본 20, 1~50). 십진 숫자만 받고 빈 값·부호·공백·앞자리 0·중복 파라미터·범위 밖은 400 `INVALID_REQUEST`(필드 이름만). 저장소에 기존 페이지 관례가 없어 커서보다 단순한 page·size를 골랐습니다.
  - 응답: `items`(상세 응답에서 고른 `orderId`·`amountWon`·`orderDate`·`merchantName`·`merchantClass`·`status`·`createdAt`·`simulatedPaymentId`)·`page`·`size`·`hasNext`. 전체 건수 없음. 빈 목록은 200과 `items: []`.
  - `member`: 401 → 400(GET이라 CSRF 없음), 모든 응답 `Cache-Control: no-store`, 시연 장부 주문 미노출. `PUT`·`PATCH`·`DELETE /api/v1/me/orders`는 405 유지.
- 한 일
  - `OrderController`에 `GET` 목록을 더했습니다. 쿼리 검사는 `OrderListParameters`, 오류는 `InvalidOrderQueryException` → `OrderExceptionHandler` 400입니다(원장 조회의 쿼리 검사 방식과 같음).
  - `OrderService.list`와 `OrderPage`(범위 상수), `OrderRepository.findByUserId(Long, Pageable)`(`Slice`). 응답 `OrderListResponse`.
  - Flyway는 추가하지 않았습니다(기존 `idx_orders_user_id` 사용, 관리자 확인 (c)).
- 테스트
  - `OrderServiceListTest`(단위 4): 저장소를 현재 사용자 ID와 `PageRequest(page, size, createdAt DESC, orderId DESC)`로 부름, `Slice`의 `hasNext` 전달, 범위 밖은 조회 없이 거부, 범위 상수.
  - `OrderApiWebTest`(웹 +30): 계약 예시 응답(요약 필드만), 빈 목록, 경계 값 전달, `page`·`size` 형식·범위 오류, 중복 파라미터, 모르는 쿼리 무시, 모음 경로 `PUT`·`PATCH`·`DELETE` 405, 시연 헤더 없음·잘못된 값에 목록 포함.
  - `MemberOrderRecommendationIntegrationTest`(+3, 기존 401 경로 목록에 목록 조회 추가): A·B 격리, 시연 장부 미노출과 시연 헤더 무시, 빈 목록, 요약 필드가 상세와 같은 값, `created_at`을 직접 맞춘 10:05 동률 3건의 번호 내림차순, 페이지 경계(크기 딱 맞음·한 건 초과·마지막 페이지 뒤 빈 배열·`page=1000&size=50`), 400 필드 이름과 no-store, 세션 없음·익명 세션은 쿼리가 틀려도 401.
  - `OrderIntegrationTest`(local +2): 시연 사용자별 분리·최신순 전체 JSON, 저장소 `findByUserId`가 다른 사용자 행이 더 많아도 이 사용자의 행만 담고 size+1로 `hasNext`를 정함(관리자 확인 (c)).
  - `MemberProfileIntegrationTest.onlyAllowedRoutesAreRegistered`: `GET /me/orders` 405 단언을 로그인 200·미로그인 401로 바꾸고, `PUT`·`PATCH`·`DELETE`는 로그인 여부와 관계없이 405(`Allow`에 GET·POST)로 고정했습니다.
  - 기대값은 위 계약 문서와 관리자 확인 조건에서 가져왔습니다. 첫 실행에서 모두 통과해 고친 기대값은 없습니다.
- 검증: `./gradlew test webTest` 통과(단위 1403, 웹 423). `./gradlew integrationTest` 단독 실행: 192 통과(실패 0), 컨테이너 메모리 합 최고 3,984MiB(한도 4,760MiB, 시작 전 배포 컨테이너 4개 2,140MiB, 3초 간격 측정). 배포 컨테이너 4개는 그대로 유지했습니다.
- 문서: `docs/api/orders.md`(목록 절, 프로필 문장), `docs/api.md`(회원 세션 등록 경로, 405 문장, #170 웹 클라이언트 규칙의 405 예시를 `DELETE /api/v1/me/orders`로 변경), `docs/api/README.md`(상태·목록 29개), `docs/decisions.md`(결정 38-5 보충, 결정 37 G3b 405 문장에 날짜 보충), `docs/domains/order.md`, `docs/architecture.md`, 이 기록.
- 하지 않은 것: 주문 수정·삭제·상태 필터·전체 건수, frontend·Android 변경(팀원 소유, 별도 조율), 새 Flyway 인덱스.
