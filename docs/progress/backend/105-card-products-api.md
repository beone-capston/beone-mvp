# #105 카드 상품 목록·상세 API

브랜치 `feat/105-card-products-api`, 시작 기준 `origin/develop` `6b344cd`.

## 2026-11-17 · `card` 패키지 상품 조회 · PR(`develop` 대상, 병합 전)
- 근거: 관리 기록 결정 25(업무 API 1차, 작업선 A의 A1), 결정 27 A1(계약 미결 7건, 사용자 선택), [`docs/api/products.md`](../../api/products.md), [업무 API 공통 규약](../../api.md#업무-api-공통-규약), [D-23](../../decisions.md#d-23-확정-정책).
- 한 일:
  - `backend/src/main/java/com/beone/api/card/`(`web`/`application`): `GET /api/v1/card-products`, `GET /api/v1/card-products/{cardProductId}`, 기능별 오류 코드 `CARD_PRODUCT_NOT_FOUND`와 처리기, `asOf` 판정. 카드 상품 테이블·Flyway 없음.
  - `benefit/rule/RuleCatalog`에 읽기 전용 `cardIds()` 한 개와 단위 테스트만 추가했습니다(결정 27 A1-1). 판정 로직과 다른 benefit 코드는 바꾸지 않았습니다.
  - 테스트: 단위 `CardProductServiceTest`·`SupportStatusTest`·`RuleCatalogTest`(추가 1건), 웹 `CardProductApiWebTest`·`CardProductLocalProfileWebTest`, 통합 `CardProductIntegrationTest`. 기대 JSON의 필드 구성은 명세, 값은 규칙 JSON·`card-benefit-research.md`·결정 27에서 먼저 옮겼습니다.
  - 문서: [card 안내](../../domains/card.md) 신규, [구조 안내](../../architecture.md)의 업무 API 패키지 구조 `card` 줄, [benefit 안내](../../domains/benefit.md)의 `RuleCatalog` 줄(메서드 설명 한 줄).
- 서비스에서 달라진 점: 모든 프로필에서 시연 사용자 헤더 없이 규칙 카드 4종 목록과 상품별 규칙 요약(규칙 버전, 서비스별 지원 상태, 한도 묶음)을 조회할 수 있습니다. 보유 카드 등록 화면의 상품 선택지로 쓸 수 있습니다.
- 결정 27 A1로 정한 것(계약 문서는 바꾸지 않음): 목록 원천은 `cardIds()`와 4종 표의 일치 확인, 이름·카드사·종류는 `card-benefit-research.md` 1절 근거의 표, 정렬은 상품 ID 문자열 오름차순, 서비스 이름은 `displayName` 또는 `serviceId`, `supportStatus` 판정 순서, `sharedByServiceIds`는 직접 연결 서비스만, 모든 프로필 등록. 세부 근거는 [card 안내](../../domains/card.md#응답-값을-정한-근거)에 있습니다.
- 작은 구현 결정: 규칙이 없는 상세의 `services`·`limitBuckets`는 `[]`(목록 `officialSources`의 "규칙이 없으면 `[]`"와 같은 방식), `asOf`는 `YYYY-MM-DD`만 받고 형식 오류의 `errors`는 `asOf` 필드 하나, 형식 오류가 상품 없음보다 먼저, 오늘 날짜는 `Clock` 빈(없으면 시스템 시계)을 Asia/Seoul로 바꿔 구함.
- 공유 테스트 수정(관리자 판단 결정 28, 테스트 파일만·단언 불변): `@WebSliceTest`는 `controllers`를 지정하지 않으면 모든 컨트롤러를 올리는데, 모든 프로필에 등록되는 `CardProductController`의 의존성(`CardProductService`·`RuleCatalog`)은 슬라이스에 없어 기존 웹 테스트 4개 클래스의 컨텍스트가 뜨지 않았습니다. 그래서 다음처럼 고쳤습니다.
  - `DemoUserApiWebTest`: `@WebSliceTest(controllers = DemoUserController.class, properties = "logging.level.com.beone.api.user.web=DEBUG")`(B1 #104와 같은 줄)
  - `DemoUserProfileOffWebTest`: `@WebSliceTest(controllers = DemoUserController.class)`. 프로필 조건은 그대로라 기본 프로필에서 등록되지 않으며, 404·미등록 검증이 그대로 통과하는 것을 실행으로 확인했습니다.
  - `GlobalExceptionHandlerWebTest`: `controllers`에 테스트 내부 `ContractTestController`만 지정(기존 `properties` 유지)
  - `WebSliceIsolationWebTest`: 전체 스캔을 유지해야 "다른 테스트 컨트롤러가 섞이지 않음"의 의미가 남으므로 `controllers`를 지정하지 않고 `@MockitoBean CardProductService`만 추가. 모든 프로필에 등록되는 업무 컨트롤러는 현재 상품 API뿐이라 유지 부담이 작습니다.
  - card 웹 테스트 2개는 처음부터 `controllers = CardProductController.class`로 한정했습니다.
- CodeRabbit(head `57f4b90`) diff 밖 지적 1건 반영(관리자 판단 결정 28): `docs/architecture.md` 54·58행의 구현 현황에 카드 상품 조회를 넣고, `docs/domains/benefit.md` `RuleCatalogConfiguration` 줄을 "카드 상품 조회가 주입받는다"로 고쳤습니다. 56행 제목 "(D-23, 미구현)"은 `docs/decisions.md` D-23 13항의 링크 앵커(`#업무-api-패키지-구조-d-23-미구현`)가 걸려 있고 decisions.md는 수정 금지라 그대로 두었습니다.
- 검증: `./gradlew test webTest integrationTest build javadoc --rerun-tasks` 결과는 PR 본문에 기록합니다.
- 남은 제한: 보유 카드 API 5개와 `GET /api/v1/me`(A2), 계산식·실적 기간 미확정 서비스를 `supportStatus`로 표시하지 못하는 한계(현재 4종에는 해당 없음), D-01 SKU 일치 미결. 서버 DB 적용·배포는 하지 않았습니다.
