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
- 근거 확인(작성 당시): 계정 API의 경로·요청·응답·암호화 방식은 저장소에 근거 사본이 없었습니다. 아래 보완 절에서 공식 SDK와 대조했습니다. 조회 요청의 `connectedId`·`orderBy`도 샌드박스 확인 절차의 값입니다. 실호출 전 근거를 확인해야 합니다.
- 결정 필요: N9(비공개 검증 환경과 공개 전환 방식), N14(호출 총량·시간 창, LP-2에서 반영).
- 하지 않은 것: CODEF 실호출, 실키·환경 비밀 파일, 서버 접속, prod 등록, 프로세스를 넘는 호출 상한(LP-2), 연결 무효 결과 코드 대응(근거 없음), frontend·Android.
- 참고: 새 환경 변수 이름 `CODEF_PUBLIC_KEY`는 처음에 비밀값이 아니라는 이유로 #192(LP-4)의 prod 차단 목록에 넣지 않았으나, 관리자 지시로 #192 병합 뒤 목록에 추가했습니다(아래 2026-11-25 보완).
- 검증: `./gradlew test webTest --rerun-tasks` 통과(단위 1451 = develop 1424 + 27, 웹 436 = 435 + 1). `./gradlew integrationTest --rerun-tasks` 단독 실행 통과(198 = 196 + 2, peak 3,795MiB, 실행 전 다른 작업선의 Docker 스모크가 내려간 것을 확인). 실패·skip 0.
- 통합 시험 첫 실행에서 클래스가 같이 쓰는 스텁 서버의 요청 기록이 다른 시험으로 새어 1건 실패했습니다. 시험마다 기록을 비우고(`CodefStubServer.clearRequests`), 토큰은 클라이언트 빈이 캐시하므로 토큰 요청은 1건 이하로 단언하도록 고쳤습니다.

## 2026-11-25 · 보완(develop 7f3e49d 동기화, 공개키 차단, 계정 API 근거 대조)

- PR #192가 `develop` `7f3e49d`로 병합돼 `origin/develop`을 merge했습니다(rebase 없음). 충돌은 `PublicProfileGuard`의 `@throws` 설명, `docs/domains/codef.md` 공개(prod) 범위의 두 문장, progress README이며 양쪽 규칙·문장·행을 모두 살렸습니다. 코드 충돌은 없었고 두 규칙(`prod & codef-live`, CODEF 환경 변수 이름)이 함께 남습니다.
- `CODEF_PUBLIC_KEY`를 `PublicProfileGuard.CODEF_SECRET_VARIABLES`(prod 차단 목록)에 넣었습니다. 공개키라 비밀은 아니지만 prod에 CODEF 설정이 없어야 한다는 결정 38-8에 맞춘 관리자 지시입니다. 테스트 1건(매개변수 1개)과 이름 일치 단언을 더했습니다.
- 계정 API 근거 대조
  - 공식 개발자 문서 <https://developer.codef.io/cert/account/cid-overview>는 스크립트로 그려지는 페이지라 읽기 도구로 본문을 얻지 못했습니다(제목만 나옴). 로그인·키 입력은 하지 않았습니다.
  - 대신 공식 SDK <https://github.com/codef-io/easycodef-java>(커밋 `ea96123e3a7f7c703d8392c8bea0d40b97b731e1`, 2024-09-26)의 README, `src/main/java/io/codef/api/EasyCodefConstant.java`, `EasyCodefUtil.java`, `src/test/java/io/codef/api/account/CreateAccountTest.java`, `DeleteAccountTest.java`와 대조했습니다.
  - 일치(코드 변경 없음): 경로 `/v1/account/create`·`/v1/account/delete`, 등록 `accountList` 항목(`countryCode` KR, `businessType` CD, `clientType` P, `loginType` 1, `id`, `password`), 삭제 요청의 최상위 `connectedId`와 로그인 입력 없는 `accountList`, 응답 `data.connectedId`(README 응답 예시에 `successList`·`errorList`와 함께 있음), RSA 암호화(공개키 Base64 → X.509, `Cipher.getInstance("RSA")` = JDK 기본 RSA/ECB/PKCS1Padding, 결과 Base64). SDK는 평문을 기본 문자셋으로 바꾸고(Java 21 기본 UTF-8) 이 구현은 UTF-8을 명시합니다.
  - 다른 점: 없음. 샌드박스·데모·정식 호스트(`sandbox`·`development`·`api.codef.io`)도 전송 계층 허용 목록과 같습니다.
  - **실호출 전 확인 필요**: `loginType` 1이 아이디 로그인이라는 설명, 등록 실패 코드(예: 카드소지확인)가 최상위 `result`인지 `errorList`인지, 요청 본문 URL 인코딩 필요 여부(상품 호출과 같은 미확인 항목), 기관 코드 `0301`에서 같은 형식이 통하는지, 공식 개발자 문서 본문과의 일치.
- 검증: `./gradlew test webTest --rerun-tasks` 통과(단위 1462 = develop 1434 + 28, 웹 436, 실패·skip 0). 통합은 다른 작업선 Docker 스모크와 순서를 받은 뒤 실행합니다.
