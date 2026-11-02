# 업무 API 1차 명세

BeONE 업무 API의 구현 기준 명세입니다. 공통 규약은 [`docs/api.md`](../api.md#업무-api-공통-규약)에 있고, 이 폴더에는 기능 묶음별 개별 API를 적습니다.

- **상태:** 설계 확정 전 명세이며 **모두 구현 전**입니다. 동작하는 업무 API는 아직 없습니다. OpenAPI 문서는 구현할 때 코드에서 만듭니다.
- **기준:** [결정 기록](../decisions.md)의 D-09·D-12~D-18과 `backend`의 `benefit` 도메인 모델입니다. 문서와 코드가 다르면 이 폴더를 임의로 맞추지 말고 충돌을 알립니다.
- **예시 JSON:** 합성 값입니다. 실제 API 실행, 약관 검증, CODEF 응답 관측의 결과가 아닙니다.
- **규칙 데이터:** 2026-11-03 기준 `VERIFIED` 규칙 데이터가 저장소에 없습니다. 이 상태에서 추천은 모든 카드가 `RULE_UNAVAILABLE`인 `NOT_DETERMINED`입니다.

## API 목록 (28개: 1차 23개, 2차 초안 5개)

순서는 D-14의 1차 명세 순서를 따릅니다. 일정이 밀리면 뒤쪽 조회부터 줄이고 설정 → 주문 → 추천 → 모의 승인·취소 흐름은 지킵니다.

| 구분 | Method | Endpoint | 설명 | 문서 |
| --- | --- | --- | --- | --- |
| 1차 | GET | `/api/v1/card-products` | 카드 상품 목록 | [products](products.md) |
| 1차 | GET | `/api/v1/card-products/{cardProductId}` | 상품 상세·규칙 요약 | [products](products.md) |
| 1차 | GET | `/api/v1/demo-users` | 시연 사용자 목록 | [cards](cards.md) |
| 1차 | GET | `/api/v1/me` | 현재 사용자 | [cards](cards.md) |
| 1차 | GET | `/api/v1/me/cards` | 보유 카드 목록 | [cards](cards.md) |
| 1차 | POST | `/api/v1/me/cards` | 보유 카드 수동 등록 | [cards](cards.md) |
| 1차 | PATCH | `/api/v1/me/cards/{userCardId}/mapping` | 상품 매핑 확인·변경 | [cards](cards.md) |
| 1차 | PATCH | `/api/v1/me/cards/{userCardId}` | 표시 이름·추천 제외 변경 | [cards](cards.md) |
| 1차 | DELETE | `/api/v1/me/cards/{userCardId}` | 보유 카드 등록 해제 | [cards](cards.md) |
| 1차 | GET | `/api/v1/me/cards/{userCardId}/monthly-states/{month}` | 월간 상태 조회 | [monthly-states](monthly-states.md) |
| 1차 | PUT | `/api/v1/me/cards/{userCardId}/monthly-states/{month}` | 월간 상태 수동 입력 | [monthly-states](monthly-states.md) |
| 1차 | GET | `/api/v1/me/payment-preference` | 결제 전 설정 조회 | [preferences](preferences.md) |
| 1차 | PUT | `/api/v1/me/payment-preference` | 결제 전 설정 변경 | [preferences](preferences.md) |
| 1차 | POST | `/api/v1/me/orders` | 주문 생성 | [orders](orders.md) |
| 1차 | GET | `/api/v1/me/orders/{orderId}` | 주문 상세 | [orders](orders.md) |
| 1차 | GET | `/api/v1/me/planned-spends` | 예정 소비 조회 | [orders](orders.md) |
| 1차 | PUT | `/api/v1/me/planned-spends` | 예정 소비 일괄 설정(저장만) | [orders](orders.md) |
| 1차 | POST | `/api/v1/me/orders/{orderId}/recommendations` | 추천 계산 | [recommendations](recommendations.md) |
| 1차 | GET | `/api/v1/me/recommendations/{recommendationId}` | 추천 결과·근거 조회 | [recommendations](recommendations.md) |
| 1차 | POST | `/api/v1/me/orders/{orderId}/simulated-payments` | 모의 승인 | [mock-payments](mock-payments.md) |
| 1차 | GET | `/api/v1/me/simulated-payments/{simulatedPaymentId}` | 모의 결제 상세 | [mock-payments](mock-payments.md) |
| 1차 | POST | `/api/v1/me/simulated-payments/{simulatedPaymentId}/cancellations` | 모의 취소(전액·부분) | [mock-payments](mock-payments.md) |
| 1차 | GET | `/api/v1/me/ledger-transactions` | 원장 조회 | [ledger](ledger.md) |
| 2차 | GET | `/api/v1/me/codef/connection` | CODEF 연결 상태 | [codef](codef.md) |
| 2차 | POST | `/api/v1/me/codef/connection` | CODEF 계정 연결 | [codef](codef.md) |
| 2차 | DELETE | `/api/v1/me/codef/connection` | CODEF 연결 해제 | [codef](codef.md) |
| 2차 | POST | `/api/v1/me/codef/syncs` | CODEF 조회·동기화 | [codef](codef.md) |
| 2차 | PUT | `/api/v1/me/cards/{userCardId}/monthly-states/{month}/source-selections` | 출처 충돌 계산 기준 선택 | [monthly-states](monthly-states.md) |

## 확인 상태 값

D-14가 명세 단계로 넘긴 값 목록입니다. 2026-11-03 사용자 지시로 정했습니다. "제안"은 아직 확인받지 않은 값입니다.

| 대상 | 값 | 근거 |
| --- | --- | --- |
| 매핑 상태 | `UNMAPPED`·`CANDIDATE`·`USER_CONFIRMED`·`NOT_SUPPORTED`. 가상 포트폴리오 카드는 매핑 대상이 아니므로 `mapping=null` | D-15 |
| 상태값 사유 | `NOT_ENTERED`·`PERIOD_MISMATCH`·`SOURCE_CONFLICT`·`NEGATIVE_UNCONFIRMED_ATTRIBUTION` | D-16·D-17 |
| 추천 제외 사유 | `RULE_UNAVAILABLE` > `MAPPING_UNCONFIRMED` > `NEEDS_CONFIRMATION` > `UNSUPPORTED`, `NO_SUPPORTED_RULE`, `USER_EXCLUDED`(제안) | D-15, D-18 K3 |
| 비교 카드 0원 사유 | `CONFIRMED_NOT_MET`(K3 순서의 마지막) | D-18 K3 |
| 서비스 지원 범위 | `SUPPORTED`·`SUPPORTED_SINGLE_MONTHLY_LIMIT`(통합 한도와 매입 순서 명시 카드 포함)·`UNSUPPORTED_LIMIT_PERIOD`·`UNSUPPORTED_SELECTION_RULE` | D-18 K1 |
| 한도 표시 | `usageBasis`: `USER_ENTERED`, `mayDifferFromActual`: `true`(제안) | D-18 K1 |
| 한도 판정 사유 | `USAGE_EXCEEDS_LIMIT`(제안) | D-18 K1 |
| 택1 선택 상태 | `UNKNOWN`·`SELECTED`·`NONE` | D-18 K1 |
| 선택으로 미적용 | `NOT_APPLICABLE_SELECTION`(제안, 코드 `ApplicationStatus`에 없어 구현 때 추가) | D-18 K1 |
| 취소 영향 대상 | `PERFORMANCE`·`LIMIT_USAGE`·`BENEFIT` | D-17 |

코드 enum을 그대로 쓰는 값: `StateSource`, `ApplicationStatus`, `Adoption`, `SelectionBasis`, `SelectionMode`, `PaymentMethod`, `LedgerEntryType`, `LedgerSource`, `VerificationStatus`, `PendingReason.Kind`.

## 명세 단계 미정

- **가상 포트폴리오 쓰기:** 이 명세는 보유 카드·월간 상태를 읽기 전용으로 제안했지만 PRD §3.2는 가상 거래·수동 상태값을 바꾸면 다시 계산하도록 요구합니다. 사용자 확인이 필요합니다.
- **`NONE` 입력 경로:** 가상 포트폴리오 쓰기가 미정이라, 그 카드의 `NONE`을 어떻게 넣을지도 미정입니다. 쓰기를 막으면 시드 값으로만 들어갑니다.
- **대표 카드 고정의 승인:** 혜택이 `확인 필요`인 대표 카드를 승인할지(제안: 승인하고 `확인 필요`로 기록). 결정 기록에 없습니다.
- **추천 제외:** `USER_EXCLUDED`와 추천 제외 API는 제안이며 결정 기록에 없습니다.
- **모의 승인 실패:** PRD FR-07의 내부 테스트 실패(`FAILED`)를 일으키는 조건.
- **판정일:** `decisionDate`를 주문일로 쓸지([진행 기록](../progress/backend/README.md) 사용자 확인 7번).
- **같은 상품 두 장 보유:** 현재 제안은 중복 거부입니다.
- **`merchantClass` 전체 목록:** 규칙 데이터와 함께 정합니다.
- **무효화된 추천으로 승인:** 현재 제안은 차이만 표시입니다.
- **D-07:** 추천 2초 목표의 측정 환경.
- **2차 CODEF:** 전송 계약, 결과 코드와 오류 코드 대응, 중복 판단 키, 승인번호 공유 여부, 연결 해제 시 조회 데이터 보존·삭제.

## 기존 초안 대조 (D-14 후속)

2026-10-11에 작성한 팀 Notion 초안(설계안 39개와 형식 예시 1개)과 대조한 결과입니다. 그 초안은 D-09 결정 전 작성본이라 보존만 하고, 구현 기준은 이 폴더입니다.

| 처리 | 기존 초안 번호·내용 | 이유 |
| --- | --- | --- |
| 이어 씀(경로 정리) | 1·2 상품, 6·7·9 보유 카드, 10·11 월간 상태, 12 원장 조회, 17·18 주문, 20·21 예정 소비, 22·23 설정, 25·28 조회, 30~33 CODEF | `/portfolios/{mode}`, `portfolioId`를 `/me`로 바꿈(D-09) |
| 이어 씀(수정) | 8 보유 카드 수정: 표시 이름·추천 제외만 남김 | 추천 제외는 PRD §4.1 요구. 자동납부·선택 서비스·등록일은 주문과 월간 상태로 옮김 |
| 바꿈 | 24 추천, 26 모의 승인: 주문 하위 경로로 이동 | D-14 원칙 7 |
| 바꿈 | 29 모의 취소: `/cancel` → `/cancellations`(금액 지정) | D-17 부분취소 |
| 바꿈 | 34·35 CODEF 후보: 보유 카드 `mapping`과 매핑 API로 합침 | D-15 |
| 뺌 | 3·4 규칙 조회 | 규칙은 저장소 JSON으로 관리하고 상품 상세에 요약 |
| 뺌 | 5 포트폴리오 목록 | 시연 사용자 목록·현재 사용자로 대체(D-09) |
| 뺌 | 13·14·15 합성·수동 원장 등록·수정·취소, 16 원장 기반 산출 | D-16 원장 기반 산출 보류. PRD §3.2와의 관계는 위 미정 항목 참고 |
| 뺌 | 19 주문 수정 | 입력을 바꾸면 새 주문을 만듦(이전 추천을 승인 근거로 쓰지 않기 위함) |
| 뺌 | 27 모의 결제 목록 | 원장 조회로 대체(원장 행에 `simulatedPaymentId`가 있음) |
| 뺌 | 36~39 규칙 초안·AI 추출·사람 검토 | AI는 업무 API 밖(D-14) |
| 뺌 | 0 형식 예시 | D-09로 불필요 |
| 새로 만듦 | 시연 사용자 목록, 현재 사용자, 출처 충돌 선택 | D-09, D-16 |

기존 초안의 다음 규칙은 이 명세에 이어 적었습니다: 금액 소수·문자열 거부, 선택 시각에 00:00을 만들지 않음, 월간 상태가 없으면 404 대신 `UNKNOWN`, `source`·`updatedAt`은 서버가 정함, 승인 요청에 카드 ID를 받지 않음, 취소는 한 트랜잭션, 추천 입력 스냅샷, 비교 불가 차액은 `null`, 원장 행의 `merchantClass`·`paymentMethod`, CODEF 응답 처리 주의.
