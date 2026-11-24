# #160 member 프로필에 주문·예정 소비·추천 등록 (G3b)

브랜치 `feat/160-member-order-recommendation`, 시작 기준 `origin/feat/159-member-monthly-preference` `229952c`(G3a). #158과 G3a가 `develop`에 병합되기 전이라 PR은 열지 않았습니다. 병합 뒤 관리자 지시에 따라 `origin/develop`을 merge하고 `develop` 대상 PR을 엽니다.

## 2026-11-24 · `order`·`recommendation` 컨트롤러 프로필, 테스트·문서

- 근거: 결정 36·37, 결정 32 Q2(추천 제외 변경 → `MAPPING_CHANGED`)·Q3(결제 설정 변경은 추천 무효화 없음), G3 명세 패키지 6절 2번(PR-G3b, endpoint #6~11).
- 한 일
  - `OrderController`·`PlannedSpendController`·`RecommendationController`의 `@Profile`에 `member`를 더했습니다. 서비스·DB·Flyway는 바꾸지 않았습니다.
  - 세션 401·쓰기 CSRF 403·`Cache-Control: no-store`·검사 순서는 #158의 `/api/v1/me/**` 인터셉터·필터가 처리합니다. 경로별 설정은 더하지 않았습니다.
  - 모의 승인·원장·`simulateFailure`·CODEF는 건드리지 않았습니다(G3c, D9). 새 비교 정책도 없습니다(Q4 한계 유지).
- 테스트
  - `MemberApiTestClient`: 정리 함수에 `planned_spends`를 더하고, 시연 헤더를 붙인 쓰기 도우미(`sendWithDemoHeader`)를 더했습니다.
  - `MemberOrderRecommendationIntegrationTest`(8, `member` 프로필): `MemberProfileIntegrationTest`와 같은 프로필·가변 시계 조합이라 새 통합 컨텍스트가 없습니다.
    - 회원 장부 주문 생성·조회, 상세의 `latestRecommendationId`
    - 회원 A·B의 주문·추천 분리. 남의 것·없는 것·형식 오류의 404 본문이 같음(주문 조회, 추천 계산, 추천 조회)
    - 예정 소비 회원별 분리. B의 교체가 A의 행을 지우지 않음
    - 시연 장부 주문·추천 404, 시연 헤더를 붙인 조회·쓰기도 회원 장부로 처리
    - 6개 경로 쿠키 없음·익명 세션 401(401이 403·400보다 먼저)
    - 쓰기 3개(주문 생성·예정 소비 교체·추천 계산) CSRF 없음·틀림 403과 미저장(403이 본문 400·404보다 먼저), 기존 예정 소비 유지
    - 본문 400 미저장
    - 결정 32 Q3(결제 설정 변경 뒤 추천 유효)와 Q2(추천 제외·되돌림·매핑 해제 → `MAPPING_CHANGED`, `invalidatedRecommendationIds`)를 API로 확인. 다른 회원의 추천은 그대로
  - `MemberProfileIntegrationTest`: 미등록 경로 목록에서 주문·예정 소비·추천을 뺐습니다. 모의 승인·원장·CODEF 4개·`/demo-users`는 남겼습니다. `GET /api/v1/me/orders`는 로그인 여부와 관계없이 405 `METHOD_NOT_ALLOWED`·`Allow: POST`·no-store로 옮겼습니다(docs/api.md 공통 실패 응답).
  - `ORDER_ALREADY_PAID`·`ORDER_PAID`는 모의 승인이 필요해 G3c에서 확인합니다.
  - 기대값은 `docs/api/orders.md`·`recommendations.md`·`cards.md`·`docs/api.md`, 결정 32·37에서 가져왔습니다.
  - 첫 실행 실패 2건과 수정(계약 기대값은 바꾸지 않음, 테스트의 잘못된 가정만 고침)
    - 주문 `createdAt`을 가변 시계 값으로 가정했습니다. order 패키지는 자체 시계 `OrderClock`(시스템 시계)을 쓰고, 계약(`orders.md`)은 생성 시각의 형식만 정합니다. 응답 값의 형식(`+09:00`)을 확인하고 상세 조회와 같은지 비교하도록 바꿨습니다. 같은 이유로 예정 소비 날짜를 실행일 기준 이번 달로 만듭니다(계약: "이번 달이어야 한다").
    - 매핑 해제의 `invalidatedRecommendationIds`를 이번 추천 하나로 가정했습니다. 무효화는 계산 때 카드 근거와 현재 상태의 비교라서(도메인 `recommendation.md` 처리 흐름), 추천 제외를 되돌린 순간 첫 추천이 다시 유효해졌고 해제로 함께 무효가 됐습니다. 계약(`cards.md`)은 "이번 변경으로 승인 근거로 쓸 수 없게 된 추천"이므로 이번 추천을 포함하고 다른 회원 추천을 포함하지 않는지로 바꿨습니다.
    - 관찰(정책 아님): 추천 제외를 켰다 끄면 그 사이 무효였던 추천이 다시 유효해집니다. 계약 문서는 무효화가 되돌아갈 수 있는지 말하지 않습니다. 기존 local·demo 동작과 같고 이번 범위에서 바꾸지 않았습니다.
- 검증: `./gradlew test webTest` 통과(단위 1391, 웹 393). `./gradlew integrationTest` 단독 실행: 첫 실행 170/172(위 2건 실패, 컨테이너 메모리 합 최고 3,961MiB), 수정 뒤 172 통과(최고 3,973MiB, 시작 전 배포 컨테이너 4개 약 2,120MiB). 배포 컨테이너 4개는 그대로 유지했습니다.
- 문서: `docs/api.md` 회원 세션 절, `docs/api/README.md`, `docs/api/auth.md`, `orders.md`·`recommendations.md` 프로필 문장, 도메인 `order.md`·`recommendation.md`(+ `auth.md`·`user.md`·`monthlystate.md`의 낡은 범위 문장), `docs/decisions.md` 결정 36·37 구현 범위.
- 남은 것
  - G3c(모의 결제·원장·`simulateFailure`)
  - #158·G3a 병합 뒤 `origin/develop` merge와 PR
