# #114 추천 계산·조회 API

브랜치 `feat/114-recommendations-api`, 시작 기준 `origin/develop` `4e53d6f`.

## 2026-11-17 · `recommendation` 패키지 · PR(`develop` 대상, 병합 전)
- 근거: 관리 기록 결정 25(업무 API 1차, B2), 결정 27 B2절, 결정 28(관리자 판단), [`docs/api/recommendations.md`](../../api/recommendations.md), [업무 API 공통 규약](../../api.md#업무-api-공통-규약), [D-09](../../decisions.md)·[D-12](../../decisions.md#d-10d-12-확정-정책)·[D-18](../../decisions.md#d-18-확정-정책)·[D-22](../../decisions.md#d-22-확정-정책)·[D-23](../../decisions.md#d-23-확정-정책).
- 한 일:
  - `backend/src/main/java/com/beone/api/recommendation/`(`web`/`application`/`persistence`): `POST /api/v1/me/orders/{orderId}/recommendations`(201)과 `GET /api/v1/me/recommendations/{recommendationId}`. 기능별 오류 처리기가 404 `ORDER_NOT_FOUND`, 409 `ORDER_ALREADY_PAID`, 404 `RECOMMENDATION_NOT_FOUND`를 만듭니다. `local`·`demo` 프로필에서만 등록합니다.
  - 계산은 엔진 입구 `RecommendationEngine.recommend` 한 번만 호출합니다. 주문·보유 카드·주문 월의 월간 상태·결제 전 설정은 각 패키지의 공개 부품으로 읽기만 하며, `order`·`card`·`monthlystate`·`preference`·`benefit`·`common`·`user` 코드는 바꾸지 않았습니다.
  - Flyway `V7__recommendations.sql`(임시 번호). 테이블은 `recommendations`(응답 본문 JSON), `recommendation_card_bases`(무효화 판정용 카드 근거)입니다. 시드는 없습니다.
  - 테스트:
    - 단위 4개 클래스. `RecommendationServiceTest`는 실제 규칙 파일과 실제 엔진을 씁니다.
    - 웹 2개 클래스(`controllers` 한정)
    - 통합 `RecommendationIntegrationTest`
    - 기대값은 계약 예시, 평가 자료 C01, 결정 27 B2에서 먼저 옮겼습니다.
  - 문서: [recommendation 안내](../../domains/recommendation.md)(신규), [구조 안내](../../architecture.md)의 구현 현황과 `recommendation` 줄.
- 서비스에서 달라진 점: 시연 화면에서 주문을 만든 뒤 추천을 계산해 고른 카드·비교 카드·제외 사유를 보고, 나중에 같은 결과를 다시 볼 수 있습니다. 계산 뒤 매핑·추천 제외·해제·월간 상태가 바뀌면 조회 결과에 무효 표시와 사유가 붙습니다. 입력하지 않은 값은 0이 아니라 `확인 필요`로 보입니다.
- 결정 27 B2로 정한 것(계약 문서는 바꾸지 않음). 세부 근거는 [recommendation 안내](../../domains/recommendation.md#결정과-근거)에 있습니다.
  - 주문 상태가 `CREATED`가 아니면 409입니다. 추천 ID는 `rec-`+번호이고, 형식이 틀린 값은 404입니다.
  - 응답 JSON과 카드 근거를 저장하고, 조회는 다시 계산하지 않습니다. 무효화는 조회 때 지연 판정합니다.
  - 우선순위는 `ORDER_PAID` > `MAPPING_CHANGED` > `MONTHLY_STATE_CHANGED`입니다.
  - **추천 제외 변경은 `MAPPING_CHANGED`로 묶었습니다.** 보유 카드 계약은 추천 제외가 실제로 바뀌면 무효화하라고 요구하는데, 추천 계약의 사유 값은 셋뿐입니다. 값을 더하면 계약 변경이라 "비교 대상 여부 변경"으로 보고 매핑 변경과 묶었습니다.
  - 계약 문구 정리 후보: `invalidatedReason`의 `MAPPING_CHANGED` 설명에 해제·추천 제외 변경 포함을 적을지, 별도 값을 둘지.
  - `inputSnapshot`은 `NOT_DETERMINED`일 때 `null`입니다. 스냅샷 시각은 입력이 있는 카드만 키로 넣습니다.
  - 가상 포트폴리오 카드는 `mapping: null`이고, `displayName`은 보유 카드 표시 이름입니다.
  - `policyEvidence`는 항상 `UNDETERMINED`입니다. `SINGLE_COMPARED_CARD`는 중립 문구입니다.
  - 그 밖의 확정 사항: 판정일은 주문일, 월간 상태는 주문 월, 해제하지 않은 보유 카드를 전부 넘김, 등록일은 월간 상태의 `registeredOn`, 적용일 없는 선택은 `usable=true`·`effectiveFrom=null`, 설정이 없으면 `AUTO_RECOMMEND`, 제외 카드는 세 필드를 항상 내보냄, `services[]`의 소수점 처리·미평가 제외 필드 포함, `usedBeforeWon`은 `{value, source}`, POST마다 새 추천.
- 한계(후속 작업 몫):
  - card의 `invalidatedRecommendationIds`는 `[]`, order의 `latestRecommendationId`는 `null`로 둡니다(B2-5). 서로 의존하게 되는 것을 피하려고 관리자가 별도 후속 노드로 잡습니다.
  - `ORDER_ALREADY_PAID`·`ORDER_PAID`는 주문 상태가 `CREATED`뿐이라 모의 승인(B3) 전까지 실제로 생기지 않습니다.
  - 계산 뒤 새로 등록한 카드는 무효화하지 않습니다. 월간 상태를 같은 초 안에 다시 입력하면 변경으로 보지 않습니다.
  - "기준일이 승인일이 아닌 카드" 안내 noteCode는 만들지 않았습니다(엔진·계약에 없음).
  - 가상 포트폴리오는 월간 시드가 없어 대부분 `확인 필요`입니다(결정 27 A3).
  - D-07 응답 시간 측정은 생략했습니다.
- 검증: `./gradlew test webTest integrationTest build javadoc --rerun-tasks` 결과는 PR 본문에 기록합니다.
- 서버 DB 적용·배포는 하지 않았습니다. frontend 변경 없음.
