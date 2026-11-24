# #193 CODEF 실 게이트웨이 codef-live 프로필 (LP-1, 스텁 HTTP 시험만)

브랜치 `feat/193-codef-live-gateway`, 시작 기준 `origin/develop` `ef01309`(#190 병합).

## 2026-11-25 · 실 게이트웨이와 codef-live 프로필

- 근거: 결정 38-8(CODEF는 비공개 실연동 검증 뒤에만 회원과 연결, 공개 `prod` 미등록), R8(CODEF 저장과 앱 원장 분리), 결정 10(API별 3회 상한), ROADMAP-next 6-1절(실 게이트웨이 없음, 컨트롤러 local·demo 전용, 재시도 없음).
- 한 일
  - `LiveCodefGateway`(`CodefGateway`·`CodefSyncGateway` 구현)를 `@Profile("member & codef-live & !prod")`로 추가했습니다. 기존 전송 계층으로 계정 등록·삭제와 상품 3종을 부르고, 호출마다 관측을 하나 엽니다.
  - 전송 계층에 계정 API(`CodefAccountApi`, `CodefObservation.createAccount`·`deleteAccount`, `CodefHttp.postAccount`)와 응답 해석(`CodefAccount`, `CodefProductResponseReader.readAccount`)을 더했습니다. 상품 호출의 로그인 입력 금지 규칙은 그대로입니다.
  - 비밀번호는 `CodefPasswordEncryptor`가 공개키(`CODEF_PUBLIC_KEY`)로 암호화한 값만 보냅니다. 공개키가 없으면 CODEF를 부르지 않습니다.
  - `CodefConnectionController`·`CodefSyncController`를 local·demo와 `member & codef-live & !prod`에 등록했습니다. 회원 세션·CSRF·사용자별 연결은 기존 규칙을 그대로 씁니다.
  - `PublicProfileGuard`에 `prod`와 `codef-live` 조합이면 기동 실패하는 규칙을 더했습니다(별도 커밋, #192와 같은 파일).
- **근거 확인 필요**: 계정 API의 경로·요청 형식(`accountList`, `countryCode`·`businessType`·`clientType`·`loginType`)·응답(`data.connectedId`)·암호화 방식(RSA/ECB/PKCS1Padding, Base64)은 저장소에 공식 문서 사본이 없습니다. 조회 요청의 `connectedId`·`orderBy`도 샌드박스 확인 절차의 값입니다. 실호출 전 근거를 확인해야 합니다.
- 결정 필요: N9(비공개 검증 환경과 공개 전환 방식), N14(호출 총량·시간 창, LP-2에서 반영).
- 하지 않은 것: CODEF 실호출, 실키·환경 비밀 파일, 서버 접속, prod 등록, 프로세스를 넘는 호출 상한(LP-2), 연결 무효 결과 코드 대응(근거 없음), frontend·Android.
- 참고: 새 환경 변수 이름 `CODEF_PUBLIC_KEY`는 비밀값이 아니어서 #192(LP-4)의 prod 차단 목록에 넣지 않았습니다. 넣을지는 #192 병합 뒤 판단할 일입니다.
