# #82 CODEF 2차 오프라인 변환 1단계(대상별 판정·보유카드 후보·실적 참고·승인 행 분류)

브랜치 `feat/82-codef-offline-adapter`, 시작 기준 `origin/develop` `5885454`(PR #81 병합 뒤).

## 2026-11-16 · `codef.sync` 1단계 · 완료(PR 전)
- 근거: [D-04·D-15·D-16·D-17](../../decisions.md), [KB 조회 조건](../../domains/codef-kb.md)의 시트 셀, 기존 `codef` 해석기·`codef.transport` 결과 타입. 관리 측 계약 감사(2026-11-16, 후보 1)의 범위를 따랐습니다. 실계정·샌드박스 호출과 실제 응답 관측은 없습니다.
- 한 일:
  - `backend/src/main/java/com/beone/api/codef/sync/`: 조회 대상별 판정(`CodefTargetOutcome`·`CodefUnusableReason`), 보유카드 후보(`CodefHeldCardCandidate`), 승인 행 형태 분류(`CodefApprovalRowView`), 실적 참고값(`CodefPerformanceReference`), 세 대상 묶음(`CodefSyncSnapshot`).
  - 합성 fixture 12개(`backend/src/test/resources/codef/sync/*.json`): 최상위 `_fixture`에 `synthetic=true`·`observed=false`·시트 셀 근거를 두고 값은 `SYN` 접두를 씁니다. 테스트가 이 표시를 확인합니다.
  - 단위 테스트 47개(`backend/src/test/java/com/beone/api/codef/sync/`). 기대값은 구현 전에 D-15~D-17과 시트 셀로 적었고 구현 뒤 바꾸지 않았습니다. 네트워크·스텁 서버·Spring 컨텍스트를 쓰지 않습니다.
- 서비스에서 달라진 점: 전송 계층이 돌려준 세 호출 결과를 대상별로 "쓸 수 있음/쓸 수 없음 + 중립 이유"로 나누고, 하나가 실패해도 나머지는 그대로 씁니다. 보유카드는 매핑 전 후보(`CODEF`·`UNMAPPED`, 등록일 미확인), 승인 행은 취소 표현 형태·원화 금액 가능 여부·승인번호 유무, 실적조회는 계산에 쓰지 않는 참고값(`usedInCalculation=false`)으로 옮깁니다. 이 결과를 쓰는 서비스·엔드포인트·저장은 아직 없습니다.
- 지키는 조건: 미확인을 0원·0건·당월로 바꾸지 않습니다. 원문 승인번호·카드번호·추가 인증 식별값은 결과와 `toString`에 나오지 않습니다(테스트). 실적 참고값을 `StateInput`·`StateValue`로 바꾸는 공개 메서드가 없고, 공개 API가 `benefit` 패키지 타입을 쓰지 않습니다(테스트). 기존 parser·transport 동작은 바꾸지 않았습니다.
- 결정 기록(새 정책 아님, 이미 확정된 원칙의 적용):
  - 원화 금액은 통화가 정확히 `KRW`, 국내/외가 국내, 이용금액이 정수로 해석될 때만 만들 수 있다고 봅니다(KB 원화금액 미제공, D-04 제공된 값만 정규화).
  - 실적 반영기간은 문서 형식 yyyyMMdd이고 실제 있는 날짜일 때만 읽습니다. 승인 일자·시각은 형식이 문서에 없어 원문 그대로 둡니다.
- 검증: `./gradlew test webTest integrationTest build javadoc --rerun-tasks` 결과는 PR 본문에 기록합니다.
- 남은 제한(사용자 결정 대기): 카드 연결(R1), 짝짓기·중복 키(R2, HMAC 키 공급 방식 포함), 카드명 → 후보 상품(R3), 승인 날짜 해석(R4). 공개 오류 코드 대응(R5)은 팀원 담당이고, 도메인 원장 연결(R8)은 하지 않습니다. 실제 KB 응답과 시트의 일치는 미확인입니다.
