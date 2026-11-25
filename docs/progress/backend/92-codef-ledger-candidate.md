# #92 CODEF 2차 오프라인 변환 2단계(원장 후보 행·짝짓기·재조회 대조)

브랜치 `feat/92-codef-ledger-candidate`, 시작 기준 `origin/develop` `73d7f04`(PR #83 1단계 병합 `dc107da` 포함).

## 2026-11-16 · `codef.sync` 2단계 · PR #93(`develop` 대상, 병합 전)
- 근거: 2026-11-16 사용자 결정(관리 기록 결정 20 C절: R1·R2·R4를 "관측 전 후보 처리" 제한 아래 추천안 채택, R3 보류), [D-15·D-16·D-17](../../decisions.md), 관리 측 계약 감사(2026-11-16) ④ R1·R2·R4·R7·R8과 ⑤ 후보 2, [KB 조회 조건](../../domains/codef-kb.md)의 시트 셀. 실계정·샌드박스 호출, 실키 사용, 실제 응답 관측은 없습니다.
- 한 일:
  - `backend/src/main/java/com/beone/api/codef/sync/`: 카드 연결(`CodefCardLinker`·`CodefCardLink`·`CodefPerformanceLink`), 날짜·시각 해석(`CodefUsedDateTime`), 지문 주입(`CodefApprovalFingerprinter`·`CodefApprovalFingerprint`), 중복 키(`CodefApprovalKey`), 판정 순수 함수(`CodefLedgerCandidateBuilder`)와 결과 타입(`CodefLedgerCandidates`·`CodefLedgerCandidateRow`·`CodefIncompleteMark`·`CodefLedgerRowKind`·`CodefRecheck`·`CodefIncompleteReason`·`CodefAttributionMonth`).
  - 합성 fixture 8개(`backend/src/test/resources/codef/sync/ledger-*.json`, `_fixture` 표시·`SYN` 값).
  - 단위 테스트 57개(새 테스트 클래스 4개와 테스트 전용 HMAC 지문 함수). 테스트 키는 테스트 코드 안의 `SYN-TEST-HMAC-KEY-…-NOT-A-SECRET` 합성 값입니다. 기대값은 결정 C·D-17 문장에서 구현 전에 정했고, 구현 뒤 바꾸지 않았습니다.
- 서비스에서 달라진 점: 같은 동기화의 보유카드·승인내역을 받아 행마다 "원장 후보로 받아들임(종류·키·재조회 결과·Type2 짝 위치)" 또는 "불완전(사유 5종)"을 판정합니다. 불완전은 연결된 카드의 그 행 날짜에만 표시하고, 다음 재조회에 쓸 키·종류를 돌려줍니다. 저장·HTTP·DB 연결은 없습니다.
- 결정 기록(구현 판단, 새 정책 아님. 사유 5종 안에서의 대응):
  - 한 행에 여러 문제가 있으면 카드 미연결 → 형태 → 원화 금액 → 키 재료(승인번호·사용일자 없음) → 날짜·시각 해석 → 같은 키 → 재조회 모순 → Type2 짝짓기 순으로 처음 걸린 사유 하나만 남깁니다.
  - 사용일자 없음은 결정 R2 문장대로 `DUPLICATE_KEY_AMBIGUOUS`, 사용일자 해석 실패와 사용일시 없음·해석 실패는 결정 R4 문장대로 `UNDETERMINED_ROW`입니다.
  - 부분취소 코드(`"2"`)와 Type1 취소금액 불일치는 D-17 "부분취소일 수 있으므로"에 따라 `PARTIAL_CANCELLATION_SUSPECTED`입니다.
  - Type2 취소 절댓값이 짝 승인보다 크면, 짝 정상 행이 받아들여지지 않았으면, 정상 행 하나를 취소 행 여럿이 가리키면(이중 차감 방지) `UNDETERMINED_ROW`입니다. 재조회 모순(정상 → Type1 밖의 변화)은 감사 R2 "그 밖의 변화는 불완전"에 따라 `UNDETERMINED_ROW`입니다.
  - 원화 금액을 만들 수 없는 행(통화·국내외)은 1단계 감사 ③ "판단 불가로 둔다"를 적용해 `UNDETERMINED_ROW`입니다.
  - Type2 짝은 이번 응답 안에서만 찾습니다. 이전 기록으로 짝을 대신하지 않습니다.
  - 불완전 표시 기간은 그 행의 사용일자(일 단위)로 두고, 카드 실적 기간으로 묶는 일은 호출자에게 남깁니다. CODEF 패키지는 카드 규칙의 실적 기간을 모르기 때문입니다.
- 검증: `./gradlew test webTest integrationTest build javadoc --rerun-tasks` 결과는 PR 본문에 기록합니다.
- 남은 제한: HMAC 운영 구현체와 키 이름·공급·회전(미결), R3 후보 상품 산출(보류), 완전 선언(R7), 도메인 원장 연결(R8), 저장·HTTP. 승인번호 공유 여부, Type1 행의 상태 변화, 날짜·시각 실제 형식은 실계정 관측 전 미확인입니다. 조회 기간이 달라진 재조회에서 이미 기록한 Type2 취소의 원 승인이 기간 밖으로 빠지면 그 취소 행은 다시 불완전으로 표시됩니다.
