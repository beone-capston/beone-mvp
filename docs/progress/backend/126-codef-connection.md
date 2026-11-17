# #126 CODEF 연결 상태 조회·계정 연결 API (C2)

브랜치 `feat/126-codef-connection`, 시작 기준 `origin/develop` `c3ce45e`.

## 2026-11-18 · codef 연결 GET·POST·DELETE, 게이트웨이 가짜 구현, connectedId 암호화 · PR(`develop` 대상, 병합 전)
- 근거: [`docs/api/codef.md`](../../api/codef.md) GET·POST·DELETE `/api/v1/me/codef/connection`, D-09·D-14 원칙 9, 결정 30(fixture·가짜·로컬 HTTP·테스트 영속까지, 실 CODEF·실키·실계정 금지), C0 1단계 대조에 대한 관리자 판단(K3 하위 패키지 신규 허용, P1·P2 근거 있는 코드만 대응, P3 성공 시 교체·실패 시 유지, P7 가짜 구현은 RSA 없음·비밀번호 보관 안 함), **2026-11-18 사용자 직접 결정** P4(해제는 connectedId 삭제와 추가 수집 중단, 조회 데이터 보존)·P5(connectedId AES-GCM 암호화, 테스트 합성 키, 운영 키는 서버 환경 변수로 실연동 승인 때, 키 없으면 연결 실패·평문 대체 금지).
- 한 일:
  - `codef.application`: `CodefConnectionService`(조회·등록·해제), `ConnectedIdCipher`(AES-256-GCM, 사용자 ID 추가 인증 데이터), `ConnectedIdKeySource`·`EnvironmentConnectedIdKeySource`(환경 변수 `BEONE_CODEF_CONNECTED_ID_KEY`, 고정 기본값 없음), `CodefGateway`(인터페이스), `FakeCodefGateway`(local·demo), `CodefRegistration`·`CodefRegistrations`(결과 분류 → 등록 결과), 상태·뷰·예외.
  - `codef.persistence`: `codef_connections` 엔티티·저장소(Flyway `V10__codef_connections.sql`, 임시 번호. V9는 인증 U1 PR #125).
  - `codef.web`: 컨트롤러(local·demo만), 요청·응답, 오류 코드 5종(`CODEF_NOT_CONNECTED` 409, `CODEF_LOGIN_FAILED` 400, `CODEF_CARD_VERIFICATION_NOT_SUPPORTED` 409, `CODEF_UNAVAILABLE` 502, `PORTFOLIO_READ_ONLY` 409), 기능별 처리기.
  - 기존 `codef` 해석·전송·sync 클래스, `common/**`, `user/**`, `application*.yml`, `docs/api.md`·`docs/api/**`는 바꾸지 않았습니다.
  - 문서: [codef 안내](../../domains/codef.md)의 CODEF 연결 API 절, [전체 구조](../../architecture.md)의 codef 줄.
- 정한 동작: 연결 행이 없으면 `NOT_CONNECTED`. 연결 행이 있으면 재연결(200)이며 connectedId·`connectedAt`을 바꾸고 `lastSyncedAt`은 유지. 이전 connectedId는 커밋 뒤 정리 요청, 저장 실패 시 새 connectedId 정리. 게이트웨이 호출은 DB 트랜잭션 밖이고 저장만 사용자 행 잠금 아래 짧은 트랜잭션.
- 실코드 대응이 없는 것(관리자 판단 P1·P2): `CODEF_LOGIN_FAILED`와 `RECONNECT_REQUIRED`로 가는 실 결과 코드가 없습니다. 결과 분류에 로그인 실패 분류가 없기 때문이며(#47), 가짜 게이트웨이로만 시험합니다.
- 해제(P4): 연결 행만 지우고 커밋 뒤 CODEF 쪽 정리를 요청합니다. 해제는 데이터 완전 삭제가 아닙니다. CODEF 보유 카드·codef 승인 행·확인한 매핑은 보존하고 카드는 보유 카드 해제로 따로 지웁니다. 보존 기간과 탈퇴·완전 삭제 정책은 미결입니다.
- 가상 포트폴리오의 DELETE는 409 `PORTFOLIO_READ_ONLY`입니다. 계약 DELETE 실패 목록에는 없지만 docs/api.md 업무 오류 코드 절이 가상 포트폴리오의 CODEF 쓰기 요청에 이 코드를 쓰도록 정해 그것을 따랐고, 2026-11-18 관리자 판단으로 승인돼 `docs/api/codef.md` DELETE 실패 목록에 한 줄을 더했습니다(관리자가 허용한 계약 정합화).
- 암호화(P5): 키가 없거나 형식이 틀리면 게이트웨이를 부르기 전에 502 `CODEF_UNAVAILABLE`, 저장 없음. 운영 키 공급·설정과 회전은 별도 승인이며 미구현입니다.
- HMAC 실키 공급(P6)은 C3(syncs)에서 다룹니다.
- 서비스에서 달라진 점: local·demo 서버에서 CODEF 연결 상태를 보고 합성 아이디로 연결·재연결을 시연할 수 있습니다. 실제 카드사 계정은 연결되지 않습니다. frontend 변경 없음.
- 검증: `./gradlew test webTest integrationTest build javadoc --rerun-tasks` 결과는 PR 본문에 기록합니다.
- 로컬·테스트 DB만 사용했고 서버 DB 적용·배포는 하지 않았습니다.
- PR #127 CodeRabbit(`07ffbd7`) 반영: ① 키 없음 통합 시험이 실행 셸의 `BEONE_CODEF_CONNECTED_ID_KEY`에 따라 실패할 수 있다는 지적 → 공유 빌드 파일(`build.gradle`)을 고치는 제안 대신 시험이 키 없는 공급을 직접 주입하도록 바꿨습니다(환경과 무관). ② `docs/architecture.md`의 codef 범위 밖 설명을 해석·전송 계층과 연결 계층으로 나누고, 구현 목록에 연결 API·`codef_connections`를 더했습니다. ③ 재연결 시험이 저장값을 복호화해 새로 발급된 connectedId인지, 이전 값이 정리됐는지 직접 확인하도록 보강했습니다(시험을 `codef.application` 패키지로 옮김).
- CodeRabbit(`73ed666`) Outside diff Minor 반영: `docs/api/codef.md` DELETE 설명과 명세 단계 미정에 해제 시 보존(2026-11-18 사용자 결정)을 적고, 보존 기간·탈퇴·완전 삭제만 미정으로 남겼습니다.
