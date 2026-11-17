# #118 모의 원장 조회 API

브랜치 `feat/118-ledger-transactions-api`, 시작 기준 `origin/develop` `ca60dda`.

## 2026-11-17 · `ledger` 패키지 조회 쪽 · PR(`develop` 대상, 병합 전)
- 근거: 관리 기록 결정 25(업무 API 1차, B4), 결정 28 보충, 결정 29, 관리자 답변(CODEF coverage 처리, 달 조건, 기록 후 진행할 선택 승인), [`docs/api/ledger.md`](../../api/ledger.md), [업무 API 공통 규약](../../api.md#업무-api-공통-규약), D-09·D-16·D-17.
- 한 일:
  - `ledger/web`(신규): `GET /api/v1/me/ledger-transactions?month=YYYY-MM[&userCardId][&source]`, 쿼리 값 검사, 기능별 오류 처리기(400 `INVALID_REQUEST`, 404 `USER_CARD_NOT_FOUND`). `local`·`demo`에서만 등록.
  - `ledger/application`: 공개 조회 부품 `LedgerQueryService`(B2f가 다시 쓸 수 있음)와 결과 `LedgerMonthView`, `LedgerCardNotFoundException`. `LedgerSource`에 `CODEF` 값 추가(조회 필터·완전성 항목에만 쓰고 1차에 저장하는 코드는 없음).
  - `ledger/persistence`: 사용자·거래일 기간 조회 메서드 하나 추가. B3 쓰기 부품(`LedgerWriter`) 동작은 그대로입니다.
  - card는 `UserCardService.list`·`UserCardId`를 읽기만 하고 코드는 바꾸지 않았습니다. Flyway 없음.
  - 테스트: 단위 2개 클래스 추가, 웹 2개 클래스(`controllers` 한정), 통합 `LedgerTransactionIntegrationTest`. 기대값은 계약 예시와 관리자 승인 규칙에서 먼저 옮겼습니다.
  - 문서: [ledger 안내](../../domains/ledger.md)(조회 쪽 구현 반영), [구조 안내](../../architecture.md)의 구현 현황과 `ledger` 줄.
- 서비스에서 달라진 점: 시연 화면에서 카드·월별로 모의 승인·취소 원장 행과 그 달 원장이 완전한지를 볼 수 있습니다. 실제 카드 거래(CODEF)는 아직 수집하지 않아 "완전하지 않음"으로 표시되며 0건을 완전한 0건으로 보여 주지 않습니다.
- 관리자 승인으로 정한 것(계약 문서는 바꾸지 않음, 세부는 [ledger 안내의 조회 규칙](../../domains/ledger.md#조회-규칙)):
  - **1차 CODEF coverage는 `complete=false`, `incompleteReasons=[]`**입니다. 1차는 CODEF 원장을 수집하지 않아 완전하다고 선언할 수 없고(완전성 거짓 선언 금지), 사유 코드는 새로 만들지 않았습니다. "출처마다 한 항목" 계약은 유지합니다. **사유는 2차 CODEF 연결 때 채웁니다.**
  - 달 조건은 거래일(`transactionDate`)이 그 달인 행입니다(완전성 기간과 같은 달력 월).
  - 정렬은 거래일 → 행 ID 오름차순, 완전성은 카드 ID 오름차순·`SIMULATION` → `CODEF`.
  - 대상 카드는 해제하지 않은 보유 카드 전부(`userCardId`를 주면 그 카드만). 형식 오류·해제·다른 사용자 카드는 404 `USER_CARD_NOT_FOUND`, 허용 밖 `source`(빈 값 포함)는 400 필드 오류입니다.
  - `SIMULATION` 완전성은 `complete=true`입니다(모의 승인·취소는 모두 기록).
- 한계(후속 작업 몫): CODEF 행·불완전 사유(2차), 스냅샷 뒤 합산(B2f), 페이지 나눔(계약 명세 단계 미정). 해제한 카드의 원장 행은 조회에 나오지 않습니다.
- 검증: `./gradlew test webTest integrationTest build javadoc --rerun-tasks` 결과는 PR 본문에 기록합니다.
- 로컬·테스트 DB만 사용했고 서버 DB 적용·배포는 하지 않았습니다. frontend 변경 없음.
