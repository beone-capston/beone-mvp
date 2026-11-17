# #129 CODEF 조회·동기화 API (C3)

브랜치 `feat/129-codef-syncs`, 시작 기준 `origin/develop` `6f9a4c9`.

## 2026-11-18 · codef syncs POST, codef 전용 저장, 승인번호 HMAC · PR(`develop` 대상, 병합 전)
- 근거: [`docs/api/codef.md`](../../api/codef.md) POST `/api/v1/me/codef/syncs`, D-09·D-15·D-16·D-17, 결정 20(R1·R2·R3)·22(R7·R8·#93·HMAC 1안)·30, C0 관리자 판단(K1 새 후보 `UNMAPPED`·상품 후보 없음, K2 codef 전용 테이블·원장 불변, K3 하위 패키지, P2 재연결 필요는 가짜만, P8 카드명+발급일자 정확히 하나, P9 기간, P11 동기), **2026-11-18 사용자 직접 결정** P6(HMAC 키 환경 변수 필수, 없으면 승인은 부분 실패·저장 없음, 원문 저장 금지), C3 착수 확인의 관리자 판단 Q1~Q8(결정 28).
- 한 일:
  - `codef.application`: `CodefSyncService`, `CodefSyncGateway`(인터페이스, `FakeCodefGateway`가 함께 구현), `CodefSyncPeriod`·`CodefSyncPeriodException`, `CodefHeldCardMatcher`, `ApprovalFingerprintKeySource`·`EnvironmentApprovalFingerprintKeySource`(환경 변수 `BEONE_CODEF_APPROVAL_HMAC_KEY`, 고정 기본값 없음), `HmacApprovalFingerprinter`, `CodefSyncResult`·`CodefSyncTarget`, 예외 2종.
  - `codef.persistence`: `codef_syncs`·`codef_held_cards`·`codef_approval_rows`·`codef_performance_references` 엔티티·저장소(Flyway `V12__codef_syncs.sql`, 임시 번호. V11은 인증 U2 예정). `CodefConnectionEntity`에 `markSynced`·`markReconnectRequired`.
  - `codef.web`: `CodefSyncController`(local·demo만), 요청·응답, `CODEF_RECONNECT_REQUIRED` 409 추가, 기간 위반 400 처리, `StrictDateDeserializer`(monthlystate 것을 옮겨 옴).
  - `card`(관리자 판단 P8 최소 접근): `UserCardService.lockHeldCardsForCodefSync`·`registerCodefCard`, `UserCardEntity.codef`. 스키마 변경 없음.
  - 가짜 게이트웨이 합성 fixture `backend/src/main/resources/codef/fake/`(실제 카드명·승인번호 형식 복사 없음), 동기화 시나리오 아이디 `fake-sync-reconnect`·`fake-sync-unavailable`·`fake-sync-partial`.
  - 기존 `codef` 해석·전송·sync 클래스, `common/**`, `user/**`, `application*.yml`, `docs/api.md`, `docs/decisions.md`는 바꾸지 않았습니다.
  - 계약: `docs/api/codef.md` syncs 절에서 응답 예시의 새 후보를 `candidateCardProductId: null`·`mappingStatus: "UNMAPPED"`로 정정하고 근거 줄을 더했으며(관리자 판단 K1, 결정 20 R3), 실패 목록에 `PORTFOLIO_READ_ONLY` 409를 docs/api.md 근거로 더했습니다(Q7).
  - 문서: [codef 안내](../../domains/codef.md#codef-조회동기화-api-129)의 조회·동기화 절, [전체 구조](../../architecture.md)의 codef 줄·구현 API·테이블 목록.
- 정한 동작:
  - 새 CODEF 카드는 `UNMAPPED`·상품 없음. 같은 카드 판단은 (카드명, 발급일자) 묶음의 응답 n장·기존 m장으로 하며 m=0이면 새로, n=1·m=1이면 같은 카드, 그 밖은 건너뜀. KB는 발급일자를 주지 않아 사실상 카드명만으로 대조합니다. 카드명 없음·표시 이름 1~30자 밖은 건너뛰고 자르지 않습니다.
  - 승인 행은 받아들인 행만 저장하고 원장은 바꾸지 않습니다. **불완전 행은 저장하지 않으며 재조회 때 다시 판정합니다.** 보유 카드에 붙지 않은 불완전 행도 `userCardId=null`로 응답에 담습니다. 정상→Type1 전액 취소는 기존 행의 종류만 바꾸고 `imported`에 셉니다.
  - HMAC 키가 없으면 승인내역을 조회하지 않고 `partialFailures`(`APPROVALS`, `CODEF_UNAVAILABLE`), `imported`·`skippedDuplicates`는 `null`. 실패한 대상의 건수는 0이 아닌 `null`입니다. 세 대상 모두 실패면 502이고 아무것도 저장하지 않습니다.
  - 실적은 실적현황 하나당 항목 하나, 모르는 값은 `null`, 참고값 이력으로만 저장하고 계산·월간 상태에 쓰지 않습니다.
  - 저장 트랜잭션은 보유 카드 행 → 사용자 행 → 연결 행 순으로 잠그고, 조회 중 해제·교체됐으면 저장하지 않고 409 `CODEF_NOT_CONNECTED`입니다.
- 실코드 대응이 없는 것: `CODEF_RECONNECT_REQUIRED`는 가짜 게이트웨이의 연결 무효로만 나옵니다(P2). 실 CODEF 조회·운영 키 공급·키 회전은 미구현입니다.
- 남은 것: 결정 22의 "기간이 바뀐 재조회에서 짝지은 Type2 취소가 다시 불완전으로 표시되는 문제" 보완은 하지 않았습니다(불완전 행은 저장하지 않음). 승인번호를 승인·취소 행이 공유하는지는 실계정 관측 전 미확인입니다. `GET /api/v1/me`의 `codefConnected`(card 패키지, "1차 항상 false")는 C2 병합으로 실제 연결 상태를 반영할 수 있게 됐지만 이번 PR에서 바꾸지 않았고 후속 후보로 남깁니다. 출처 선택(C1)은 이 PR 병합 뒤입니다.
- 서비스에서 달라진 점: local·demo 서버에서 CODEF 연결 뒤 동기화를 누르면 합성 카드가 매핑 전 보유 카드로 나타나고, 합성 승인 행 판정 결과와 카드사 조회 실적(참고)을 볼 수 있습니다. 실제 카드사 데이터는 조회되지 않고 원장 조회 결과(행)는 바뀌지 않습니다. frontend 변경 없음.
- 검증: `./gradlew test webTest integrationTest build javadoc --rerun-tasks` 결과는 PR 본문에 기록합니다.
- 로컬·테스트 DB만 사용했고 서버 DB 적용·배포는 하지 않았습니다.
- PR #131 CodeRabbit(`64491cf`) 반영: ① V12의 카드명·발급일자·지문 열 정렬 규칙을 `utf8mb4_bin`(PAD SPACE, 후행 공백 무시)에서 `utf8mb4_0900_bin`(NO PAD)으로 바꿔 후행 공백만 다른 카드명을 다른 키로 봅니다. 아직 병합 전 마이그레이션이라 같은 파일을 고쳤고, 통합 시험에 후행 공백 행은 들어가고 완전히 같은 키는 막히는지 더했습니다. ② 도메인 문서·`CodefSyncService` Javadoc의 검사 순서를 실제대로 요청 본문·날짜 형식 400이 가상 포트폴리오 409보다 먼저로 고쳤습니다. ③ `docs/api/codef.md` syncs 설명·처리 흐름과 `docs/domains/card.md` 핵심 용어에 남아 있던 "카드명으로 `CANDIDATE` 후보 제시" 설명을 `UNMAPPED`·후보 없음으로 정정했습니다.
- CodeRabbit(`375af63`) 반영: `docs/domains/card.md` 보유 출처 용어의 `CODEF`(2차) 표시를 CODEF 동기화(#129)가 만드는 현재 출처로 고쳤습니다(문서만).
