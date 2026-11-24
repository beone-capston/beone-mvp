# #153 결정 32 기존 동작 명시(S1A Q1~Q5·Q7~Q9, R9)

브랜치 `docs/153-decision32-contract-notes`, 시작 기준 `origin/develop` `4b18396`.

## 2026-11-24 · 기존 동작 문서 명시 · PR(`develop` 대상, 병합 전)
- 근거: S1A 정책 질문에 대한 사용자 직접 선택(결정 32, 2026-11-18)과 감사 문서 변경안 R9. 코드는 바꾸지 않고, 지금 코드가 하는 동작을 확인한 뒤 적었습니다.
- 한 일:
  - [`docs/api/codef.md`](../../api/codef.md): 연결 상태 조회에서 가상 포트폴리오는 200 `NOT_CONNECTED`, 409는 쓰기 요청에만(Q7, R9). 근거 `CodefConnectionService#get`(쓰기 검사 `requireWritable` 없음), `CodefConnectionApiWebTest#getNotConnectedKeepsNulls`.
  - [`docs/api/recommendations.md`](../../api/recommendations.md): `invalidatedReason` 우선순위, `MAPPING_CHANGED`가 매핑·규칙 상품 변경·카드 해제·추천 제외 변경을 묶음(Q2), 결제 설정 변경은 무효화하지 않고 승인 때 그때 설정으로 재계산(Q3). 근거 `InvalidationReason`, `RecommendationService#invalidation`·`sameComparisonTarget`·`compute`.
  - [`docs/api/mock-payments.md`](../../api/mock-payments.md): 재계산 비교 기준은 `selected`이고 대표 카드 고정 모드에서 대표 카드 혜택만 바뀌면 감지하지 못하는 한계, 3단계 재검토(Q4). 근거 `RecalculationComparison`, `CardChoice#choose`.
  - [`docs/api/ledger.md`](../../api/ledger.md): CODEF coverage는 행 없음·`complete=false`·사유 없음(Q5, R8). 근거 `LedgerQueryService`. [`docs/domains/ledger.md`](../../domains/ledger.md)는 #142에서 이미 고쳐 바꾸지 않았습니다.
  - [`docs/api/monthly-states.md`](../../api/monthly-states.md): source-selections 제한 구현(항상 409·저장 없음) 안내(Q1). 근거 `MonthlyStateService#selectSource`. [`docs/api/README.md`](../../api/README.md)에는 이미 있어 그대로 뒀습니다.
  - [`docs/api/auth.md`](../../api/auth.md) U4 세부 해석, [`docs/api/cards.md`](../../api/cards.md) `codefConnected`: 출처를 사용자 확인(결정 32 Q9·Q8)으로 표기했습니다. U3 세 항목은 원 결정의 구현 설명이라 그대로 뒀습니다.
- 하지 않은 일: 공개 응답 필드 추가, Q6 엔진 사유(#151 소관), 새 정책, Notion 편집. 코드 주석(`LedgerQueryService`의 "2차 CODEF 연결 때 채운다")은 문서만 바꾸는 범위라 손대지 않았습니다.
- 코드·테스트·설정 변경 없음. 테스트는 실행하지 않았습니다(문서만 변경).
