# #108 보유 카드 API와 현재 사용자 API

브랜치 `feat/108-user-cards-api`, 시작 기준 `origin/develop` `ebdc882`.

## 2026-11-17 · `card` 패키지 보유 카드·`GET /me` · PR(`develop` 대상, 병합 전)
- 근거: 관리 기록 결정 25(업무 API 1차, 작업선 A의 A2), 결정 27 A2절(가상 포트폴리오 시드는 사용자 결정, 계약 공백 10건은 관리자 판단), [`docs/api/cards.md`](../../api/cards.md), [업무 API 공통 규약](../../api.md#업무-api-공통-규약), [D-09](../../decisions.md#d-09d-14-확정-정책)·[D-15](../../decisions.md#d-15d-16d-17-확정-정책)·[D-19](../../decisions.md#d-19-확정-정책)·[D-23](../../decisions.md#d-23-확정-정책).
- 한 일:
  - `backend/src/main/java/com/beone/api/card/`(`web`/`application`/`persistence`): `GET /api/v1/me`, `GET`·`POST /api/v1/me/cards`, `PATCH /api/v1/me/cards/{userCardId}/mapping`, `PATCH`·`DELETE /api/v1/me/cards/{userCardId}`. 기능별 오류 코드 `USER_CARD_NOT_FOUND`·`USER_CARD_DUPLICATED`·`PORTFOLIO_READ_ONLY`와 처리기. `local`·`demo` 프로필에서만 등록.
  - 상품 존재 확인은 카드 상품 조회의 `CardProductService`에 `requireSupported`를 더해 재사용했습니다(404 `CARD_PRODUCT_NOT_FOUND`).
  - `GET /me`는 `user` 코드를 바꾸지 않고 `DemoUserService.listDemoUsers()`의 표시 정보와 보유 카드 수를 합칩니다. `codefConnected`는 항상 `false`(계약).
  - Flyway `V4__user_cards.sql`(임시 번호): `user_cards` 테이블(`user_id` FK, 해제는 `released_at`), 같은 상품 중복을 막는 생성 열·유니크 인덱스, 출처·매핑 체크 제약, 가상 포트폴리오 시드 4장(규칙 카드 4종 전부, 사용자 결정).
  - 대표 카드 확장 지점 `RepresentativeCardGuard`(기본 구현은 막지 않음). 대표 카드 테이블·설정은 만들지 않았습니다.
  - 테스트: 단위 `UserCardServiceTest`·`RecommendationEligibilityTest`·`UserCardIdTest`·`CardProductServiceTest`(추가 1건), 웹 `UserCardApiWebTest`·`UserCardProfileOffWebTest`(처음부터 `controllers` 한정, 공유 슬라이스 테스트 수정 없음), 통합 `UserCardIntegrationTest`. 기대 JSON은 계약 예시와 결정 27에서 먼저 옮겼습니다.
  - 문서: [card 안내](../../domains/card.md)에 보유 카드 절, [구조 안내](../../architecture.md) 54·58행 구현 현황과 `card` 줄(56행 제목은 D-23 앵커라 유지), [user 안내](../../domains/user.md)의 `/me` 위치 한 줄.
- 서비스에서 달라진 점: 시연 화면이 현재 사용자와 보유 카드 수로 첫 화면을 나누고, 내 카드 사용자는 카드번호 없이 보유 카드를 수동 등록·매핑·이름 변경·추천 제외·해제할 수 있습니다. 가상 포트폴리오 사용자는 규칙 카드 4장을 보고, 보유 카드 쓰기는 409로 막힙니다.
- 결정 27 A2로 정한 것(계약 문서는 바꾸지 않음): `uc-`+번호, 등록 순·해제 제외, 적격 우선순위, `SYNTHETIC`만 최상위 `cardProductId`, 매핑 변경 응답은 계약 예시 모양(`registeredAt` 없음), 확인 방법 규칙, 자기 재확인 200, 검사 순서, 알 수 없는 필드 400(`errors: []`). 세부 근거는 [card 안내](../../domains/card.md#응답-값을-정한-근거-1)에 있습니다.
- 한계(후속 작업 몫):
  - `invalidatedRecommendationIds`는 추천(B2)이 없어 항상 `[]`입니다. B2가 추천을 저장하면 `UserCardService.changeMapping`·`update`에서 무효화 대상을 채워야 합니다(결정 27 A2-6).
  - CODEF 후보 열을 만들지 않아 `candidateCardProductId`·`candidateBasis`는 항상 `null`이고 `CODEF_CANDIDATE` 확인은 400입니다. 2차 CODEF가 새 마이그레이션으로 더합니다(결정 27 A2-10).
  - `REPRESENTATIVE_CARD_IN_USE`와 자동 추천 모드 대표 카드 비우기는 결제 전 설정(A4)이 `RepresentativeCardGuard` 빈을 등록해야 동작합니다.
  - `recommendationEligibility`는 매핑·사용자 제외만 봅니다. 규칙 만료·월간 상태·설정에 따른 제외는 추천 계산에서 다룹니다.
- 검증: `./gradlew test webTest integrationTest build javadoc --rerun-tasks` 결과는 PR 본문에 기록합니다.
- 서버 DB 적용·배포는 하지 않았습니다. frontend 변경 없음.
