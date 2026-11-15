# CODEF 설정과 샌드박스 확인 절차

CODEF 전송 계층의 환경 변수와, 샌드박스 호스트에서 형식을 확인하는 절차입니다. 샌드박스(테스트 데이터만 돌려주는 CODEF 환경) 확인은 **사용자가 직접 실행**합니다. 키를 다루는 쪽을 사용자로 한정하기 위해서입니다. 에이전트는 `.env`를 읽거나 출력하지 않고 이 작업을 실행하지 않습니다.

## 환경 변수

| 이름 | 용도 | 필수 여부 |
| --- | --- | --- |
| `CODEF_CLIENT_ID` | 토큰 발급의 클라이언트 ID | CODEF 호출에 필수. 없어도 서버는 뜹니다 |
| `CODEF_CLIENT_SECRET` | 토큰 발급의 클라이언트 시크릿 | CODEF 호출에 필수. 없어도 서버는 뜹니다 |
| `CODEF_PUBLIC_KEY` | 계정 연결 요청의 RSA 암호화 공개키 | 전송 계층은 쓰지 않습니다. 계정 연결 단계에서 필요합니다 |

- 값은 사용자가 루트 `.env`에 직접 넣고, Git·로그·보고에 남기지 않습니다.
- 키가 하나라도 없거나 비어 있으면 "설정 안 됨"입니다. 서버는 정상으로 뜨고 수동 입력 경로는 영향을 받지 않습니다. CODEF 호출은 토큰 발급도 시도하지 않고 `CREDENTIALS_MISSING`으로 실패합니다.
- 기동 로그에는 `CODEF 키 설정 안 됨`과 비어 있는 변수 이름만 남습니다. 값과 길이는 남기지 않습니다.
- `.env.example`은 이번 변경에서 고치지 않았습니다. 그 파일의 CODEF 주석이 이 표와 다르면 이 표를 따릅니다. 파일을 고치거나 새로 만들어야 하면 사용자가 직접 합니다.

선택 설정(Spring 설정값):

| 키 | 기본값 | 근거 |
| --- | --- | --- |
| `beone.codef.product-host` | `https://sandbox.codef.io` | 데모·정식 호스트는 별도 승인 뒤 켭니다. CODEF 공식 호스트의 HTTPS만 허용합니다 |
| `beone.codef.product-response-timeout` | `310s` | 공식 카드 상품 Timeout 300초. 여유 10초는 제안, 샌드박스에서 재확인 |
| `beone.codef.token-response-timeout` | `110s` | 제안, 샌드박스에서 재확인 |
| `beone.codef.connect-timeout` | `5s` | 근거 없는 제안, 샌드박스에서 재확인 |

## 샌드박스 확인 실행

**실행 전 조건**

- 사용자가 CODEF 키를 확인하고 루트 `.env`에 넣었습니다.
- `./gradlew test`가 통과했습니다. 이 작업은 `codef-sandbox` 태그를 빼므로 외부를 호출하지 않습니다.
- 지금까지 호출 수가 API별 3회 미만입니다. 프로세스를 넘어선 누적은 코드가 세지 않으므로 실행한 사람이 기록합니다.

**실행:** [README](../../README.md)의 로컬 실행 방식으로 루트 `.env`를 셸에 불러온 뒤 `backend/`에서 아래를 실행합니다. `--no-daemon`은 비밀값이 든 환경 변수를 Gradle 데몬에 남기지 않기 위해서입니다.

```bash
./gradlew codefSandboxCheck --console=plain --no-daemon
```

출력의 `run=1`은 고정 표시입니다. 실행 횟수는 따로 적습니다.

**하는 일**

1. 토큰 1회를 요청합니다. 실패하면 상품을 호출하지 않고 끝납니다.
2. 상품 3종을 각 1회 호출합니다. 다시 보내지 않습니다.
3. 허용한 항목만 출력합니다. 호스트가 `sandbox.codef.io`가 아니면 호출 전에 실패합니다.

| 호출 | 경로 | 본문 |
| --- | --- | --- |
| 토큰 | `https://oauth.codef.io/oauth/token` | `grant_type=client_credentials&scope=read` |
| 보유카드 | `/v1/kr/card/p/account/card-list` | `{"organization":"0301"}` |
| 승인내역 | `/v1/kr/card/p/account/approval-list` | `{"organization":"0301","startDate":"20260901","endDate":"20260930","orderBy":"0"}` |
| 실적조회 | `/v1/kr/card/p/account/result-check-list` | `{"organization":"0301"}` |

샌드박스에서 같은 경로인지, 기관코드 `0301`이 유효한지, `connectedId`가 필요한지는 문서에 없어 이 절차의 확인 항목입니다. 카드사 아이디·비밀번호와 카드번호는 넣지 않습니다.

**출력 형식** (아래는 형식 예시이며 실행 결과가 아닙니다)

```text
CODEF_SANDBOX host=sandbox.codef.io run=1
TOKEN    success=true http=200 encoding=RAW_JSON fields=access_token,expires_in,token_type otherFields=0
CARD     sent=1 http=200 encoding=PERCENT resultCode=CF-00000 hasResult=true data=ARRAY
APPROVAL sent=1 http=200 encoding=PERCENT resultCode=CF-00000 hasResult=true data=ARRAY
RESULT   sent=1 http=200 encoding=PERCENT resultCode=CF-00000 hasResult=true data=OBJECT
OUTPUT_SELF_CHECK=PASS
```

호출이 예외로 끝나면 `failure=<이유>`만 씁니다. 출력에 키나 토큰이 섞이면 결과 줄을 모두 버리고 `OUTPUT_SELF_CHECK=FAIL`만 씁니다.

**바로 멈추고 보고하는 경우**

- `connectedId`가 필요하다고 나옵니다. 샌드박스 계정 등록은 이 절차의 범위가 아닙니다.
- 키 관련 오류나 `CF-00004`·`CF-00005`(호스트와 토큰 종류 불일치)가 나옵니다. 호스트를 바꾸지 않습니다.
- 샌드박스인데 실데이터로 보이는 응답이 나옵니다.
- 상품 요청의 Content-Type이 거부됩니다. 다른 값을 시험하기 전에 보고합니다.
- `OUTPUT_SELF_CHECK=FAIL`이 나옵니다. 결과 파일을 열거나 공유하지 않습니다.

**보고에 쓰지 않는 것:** 키, 토큰, `connectedId`, 응답 본문 값, `result.message`, 카드명·금액·이름 같은 응답 값, 스크린샷.

## 끝난 뒤

토큰 응답 처리를 한쪽으로 고정하고, "제안" 값을 확인됨 또는 제안 유지로 정리합니다. 샌드박스는 실제 지연을 보여 주지 않으므로 시간 값은 제안으로 남을 수 있습니다. 데모·정식 호스트와 실계정은 따로 승인받습니다.
