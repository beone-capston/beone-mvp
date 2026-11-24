# codef 도메인 안내

## 목적과 범위

`codef`는 CODEF 카드 API 응답 본문을 BeONE이 쓸 수 있는 비민감 값으로 해석하는 **응답 해석 경계**입니다(#41). CODEF(금융기관 데이터를 대신 조회해 주는 외부 API)의 요청 전송, OAuth 토큰(접근 권한을 토큰으로 위임하는 인증 방식의 발급값), 암호화, 계정 연결(connectedId: CODEF가 연결된 금융기관 계정을 가리키는 식별값)과는 분리되어 있으며, 이 패키지는 응답 문자열을 받아 결과 코드 분류와 `data` 항목만 돌려줍니다. 그 앞단의 전송 단계 해석(#43)은 HTTP 상태 코드와 응답 본문을 받아 상태를 구분하고 본문을 URL 디코딩합니다. 실제 HTTP 호출은 하지 않습니다.

[CODEF 연결 API](#codef-연결-api-126)(#126)는 연결 상태 조회·계정 연결·연결 해제를, [CODEF 조회·동기화 API](#codef-조회동기화-api-129)(#129)는 보유카드·승인내역·실적 조회와 codef 전용 저장을 local·demo에서 가짜 게이트웨이로만 처리합니다. 아래 "구현하지 않았습니다"는 해석·전송 계층 기준이며, 실 계정 연결·실 조회·RSA 암호화는 여전히 없습니다.

구현된 범위는 승인내역·보유카드·실적조회 응답의 해석, 공통 결과 코드의 분류, 빈 값·금액·코드 값의 보수적 해석, 그리고 그 앞단의 [전송 계층](#전송-계층)(토큰 발급·캐시, 상품 요청, 시간 초과, 호출 상한)입니다. 계정 연결, RSA 암호화, 추가 인증 2차 요청, DB 저장, 엔드포인트, BeONE 상태값(`StateValue`: 값과 출처·기준 기간을 함께 담는 `benefit` 도메인 타입)으로의 변환은 구현하지 않았습니다. 이 패키지의 테스트는 합성 응답만 쓰며 실제 응답 검증이 아닙니다.

CODEF 결과를 쓸 수 없는 경우(오류 분류, 미분류, 형식 오류, 값 없음)에도 수동 입력 경로는 유지해야 합니다(CLAUDE.md 제품 경계). 이 패키지는 그 판단 재료만 제공하며 수동 입력 API는 없습니다.

## 근거 자료

- 출처: Aside의 브라우저로 공식 공개 문서를 렌더링해 수집한 본문 사본(수집 2026-10-31T12:00:47Z). 구현 작업자가 직접 브라우저로 검증한 것이 아닙니다.
- 대상 문서와 표시 갱신일: 승인내역(`/products/card/common/p/approval`, 2026-10-21), 보유카드(`/products/card/common/p/account`, 2026-10-19), 실적조회(`/products/card/common/p/result-check`, 2026-10-19), 공통 result/data 구조(`/common-guide/rest-api`), 오류코드 검색 결과 "만료"·"통신"·"토큰"(`/common-guide/error-code`).
- 성공 코드 출처: Aside의 브라우저로 공식 오류코드 페이지(https://developer.codef.io/common-guide/error-code)에서 CF-00000을 검색해 확인한 행(관찰 2026-11-01T07:10:00Z). 행 내용은 `CF-00000` 설명 "성공"입니다. 위 사본과 별개의 별첨이며, 사본에 성공 코드가 없던 것은 검색 범위("만료"·"통신"·"토큰")에 따른 수집 누락이었습니다. 공식 문서에 성공 코드가 없었다는 뜻이 아닙니다.
- 사본의 JSON 구조도는 공식 예시 하나에서 값만 뺀 것입니다. 완전한 스키마나 실제 응답 검증이 아닙니다.
- 전송 단계 근거: 공식 SDK `codef-io/easycodef-java-v2`(MIT) 커밋 `721f5661a52da3bcee8f24c86c04557e1e073e9a`의 `ResponseHandler`·`EasyCodefConnector`·`CodefResponseCode`. 공식 SDK 구현에서 확인한 동작이며, 공식 명세 문서가 아닙니다. SDK 동작이 서버 계약 전체를 보장하지는 않습니다(아래 "전송 단계" 절).
- KB 조건·취소 표현·오류 목록 대조 근거(#45): Aside가 공식 공개 페이지와 공식 연결 시트에서 수집한 자료(관찰 2026-11-01T08:16:27Z). 카드 개인 API 파라미터 상세 시트와 공식 오류코드 페이지 전체 다운로드(587행)이며, 실제 응답 관측은 없습니다. KB 상세는 [KB국민카드 조회 조건 근거](codef-kb.md)에 있습니다.
- 미확인(샌드박스 확인 전): 상품 요청 Content-Type을 서버가 받는지, 토큰 응답의 실제 인코딩과 만료 필드, 서버 쪽 인코딩 계약(SDK의 디코딩 동작만 확인), 실제 지연, 기관별 필수조건 시트 본문, 승인 취소·부분취소 상세 탭, 오류 각 코드의 상세 조치(전체 목록 587행은 #45에서 확보해 분류 코드만 대조), 별도 명세 릴리스 버전.

## 핵심 용어

- 공통 봉투: 모든 응답의 `result`(code·message·extraMessage·transactionId)와 `data`.
- 단건·다건: 문서는 "단건의 경우 객체형, 다건의 경우 리스트형"이라고 합니다. 객체는 1건, 배열은 N건, 빈 배열은 0건입니다.
- 값 없음(미확인): 키 누락·빈 문자열. 문서의 "옵션 및 미제공시 빈칸", "필수여도 상세내역에 따라 필수가 아닐 수도 있음"에 따라 0원·당월·기본값으로 바꾸지 않습니다.
- 미분류: 이번 분류 표에 없는 결과 코드. 의미를 추정하지 않고 확인 필요로 둡니다.

## 패키지 구조

```text
codef/
├── CodefProductResponseReader HTTP 상태·본문 → CodefProductResponse (전송 단계, #43)
├── CodefProductResponse     전송 결과: 본문 해석·401 인증 오류·그 밖의 HTTP 실패
├── CodefCardResponseParser  응답 본문 → CodefResponse (승인내역·보유카드·실적조회)
├── CodefResponse·CodefResult 공통 결과와 data 해석 결과
├── CodefResultCategory      결과 코드 분류 표
├── CodefList·CodefAmount    단건·다건·누락·형식 오류, 금액 해석
├── CodefApproval            승인내역 항목
├── CodefHeldCard            보유카드 항목
├── CodefCardPerformance     실적조회 항목(혜택·상세·실적현황 중첩)
├── CodefCancellationFormDescriber 해석된 승인내역 한 건 → 취소 표현 형태 기술(KB만, #45)
├── CodefCancellationForm    type1 취소·type2 취소 행·정상 행·판단 불가·근거 미정리
└── CodefFields              필드 읽기 공통 규칙(패키지 내부)
```

`benefit` 도메인과 `common.web`에 의존하지 않습니다. JSON은 Spring Boot에 포함된 Jackson(`tools.jackson`)으로 읽습니다.

## 주요 클래스와 처리 흐름

[CodefCardResponseParser](../../backend/src/main/java/com/beone/api/codef/CodefCardResponseParser.java)의 `parseApprovals`·`parseHeldCards`·`parseCardPerformances`는 응답 본문을 받아 [CodefResponse](../../backend/src/main/java/com/beone/api/codef/CodefResponse.java)를 돌려줍니다.

1. 본문이 JSON 객체가 아니면 예외를 던지지 않고 결과는 미분류, `data`는 형식 오류입니다. 파서 예외 문구에 본문 일부가 들어갈 수 있어 전달하지 않습니다.
2. `result.code`·`transactionId`만 보관하고 [CodefResultCategory](../../backend/src/main/java/com/beone/api/codef/CodefResultCategory.java)로 분류합니다. `message`·`extraMessage`는 기관 문구가 그대로 담길 수 있어 보관하지 않습니다.
3. `data`는 [CodefList](../../backend/src/main/java/com/beone/api/codef/CodefList.java)로 읽습니다. 객체 → 1건, 배열 → N건(빈 배열은 0건), 키 누락 → 값 없음(`Missing`), null·원시값·객체가 아닌 배열 원소 → 형식 오류(`Malformed`). 실적조회의 중첩 목록도 같은 규칙이며, 중첩 목록의 빈 문자열은 "미제공시 빈칸"으로 보고 값 없음입니다(최상위 `data`의 빈 문자열은 형식 오류).
4. 결과 코드 분류와 `data` 해석은 독립입니다. 성공 분류여도 `data`의 누락·형식 오류·빈 값·해석 못 한 금액·목록 밖 코드 값은 그대로 남고, 0원·0건·검증 완료로 바뀌지 않습니다. 해석기에는 두 값을 합쳐 "사용 가능"을 판정하는 메서드가 없으며, 그 판정은 하위 패키지 [`codef.sync`](#오프라인-동기화-변환-82)가 조회 대상별로 합니다. 판정을 통과해도 항목 값의 빈 값·미해석은 그대로입니다. 예를 들어 추가 인증 응답의 `data`도 형식상 객체 1건으로 읽힙니다.

필드 값 규칙([CodefFields](../../backend/src/main/java/com/beone/api/codef/CodefFields.java)):

- 문자열: 누락·null·공백뿐인 문자열·문자열이 아닌 값은 빈 값입니다.
- 금액([CodefAmount](../../backend/src/main/java/com/beone/api/codef/CodefAmount.java)): 값 없음은 `Absent`, 부호 있는 정수만 `Parsed`, 쉼표·소수점·통화 기호·범위 초과는 `Unparsed`(원문 미보관). 음수는 문서상 KB 취소전표매입에서 나올 수 있어 허용하지만, 부호로 취소 여부를 해석하지 않습니다.
- 코드 값: 문서에 값 목록이 있는 필드만 열거형으로 바꾸고, 목록 밖 값·빈 값은 `UNKNOWN`입니다. 대소문자·공백을 고치지 않습니다.
- 날짜·시각: 승인내역·보유카드의 날짜 형식은 문서에 없어 문자열 그대로 둡니다. 실적조회의 반영기간은 문서상 yyyyMMdd이지만 기간 의미를 확정하지 않으므로 문자열로 둡니다.

## 전송 단계

[CodefProductResponseReader](../../backend/src/main/java/com/beone/api/codef/CodefProductResponseReader.java)의 `readApprovals`·`readHeldCards`·`readCardPerformances`는 HTTP 상태 코드와 응답 본문 문자열을 받아 [CodefProductResponse](../../backend/src/main/java/com/beone/api/codef/CodefProductResponse.java)를 돌려줍니다(#43). 실제 HTTP 클라이언트가 받은 값을 넘겨받는 자리이며, 이 단계는 호출하지 않습니다.

| HTTP 상태 | 결과 | 본문 |
| --- | --- | --- |
| 200 | `Interpreted`: UTF-8 URL 디코딩 뒤 `CodefCardResponseParser` 결과 | 해석함 |
| 401 | `Unauthorized`: 인증 오류 | 해석·보관하지 않음 |
| 그 밖(100~599) | `HttpFailure(status)`: 서버·전송 오류 | 해석·보관하지 않음 |

- HTTP 상태 구분은 결과 코드 분류(`CodefResultCategory`)와 섞지 않습니다. 200일 때만 본문의 결과 코드를 분류하며, 성공 코드여도 `data` 형식 오류는 그대로입니다.
- 디코딩은 `URLDecoder.decode(본문, UTF-8)`이며 SDK와 같습니다. 따라서 `+`는 공백, `%2B`는 `+`, `%EC%B9%B4…` 같은 UTF-8 바이트열은 한글이 되고, UTF-8로 읽을 수 없는 바이트(`%FF`)는 대체 문자(U+FFFD)가 됩니다. 디코딩이 먼저라 본문의 `"+100"`은 `" 100"`이 되고, 금액 규칙(앞뒤 공백 제거 뒤 정수)으로 100이 됩니다.
- 잘못된 퍼센트 인코딩(`%`, `%7`, `%G1` 등)은 결과 미분류, `data` 형식 오류(`response body is not valid URL encoding`)입니다. SDK에서는 이 경우 처리되지 않은 예외가 나며, 여기서는 디코더 예외 문구에 본문 일부가 들어갈 수 있어 전달하지 않습니다.
- 100~599 밖의 상태 코드와 null 본문은 호출 오류로 거부합니다. 상태가 200이 아니면 본문은 읽지 않습니다.

SDK에서 확인한 것과 하지 않은 것:

- 확인(SDK 구현): 상품 요청은 POST, `Authorization: Bearer <access token>`, 본문은 UTF-8 JSON 문자열입니다. SDK는 토큰을 쿼리 문자열로 요청하고 토큰 응답을 디코딩하지 않습니다. 전송 계층은 공식 REST API 예제의 form 본문을 쓰고, 토큰 응답은 [전송 계층](#전송-계층)의 규칙으로 읽습니다. 상품 응답은 상태와 관계없이 먼저 디코딩한 뒤 200이면 해석하고, 401은 인증 오류, 그 밖은 서버 오류로 나눕니다. `data`는 객체를 먼저 시도하고 아니면 배열로 읽습니다.
- SDK와 다르게 둔 것: SDK는 401·그 밖의 상태에서 디코딩한 본문을 예외에 싣지만, 여기서는 본문을 읽지도 보관하지도 않습니다. SDK는 `result`가 없으면 파싱 오류로 보지만, 여기서는 기존 파서 규칙(결과 미분류, `data`는 별도 해석)을 따릅니다.
- 미확인: Content-Type(SDK가 `StringEntity(본문, UTF-8)`만 쓰고 명시하지 않음. 전송 계층의 `application/json`은 제안), `CF-12872`의 구체적 의미(공식 오류 목록 340행 설명은 "인증 요청이 정상적으로 완료되지 않았습니다. 확인 후 거래하시기 바랍니다."뿐이고 추가 설명이 없으며, SDK 상수 이름 `isAddAuthExceedResponse`만으로 의미를 정하지 않아 미분류), 기관별 필수조건, 부분취소 상세, 오류 각 코드의 상세 조치.
- 이 단계에서 만들지 않은 것: 요청 DTO, RSA 암호화, connectedId, 추가 인증 2차 요청, DB. 요청 쪽에는 수집하지 않는 입력(카드번호·실물 카드 비밀번호), 별도 결정이 필요한 입력(인증서), 연결 요청에서만 받을 입력(카드사 홈페이지 아이디·비밀번호, [D-14 원칙 9](../decisions.md#d-09d-14-확정-정책))이 섞여 있고, 키 없이는 실제로 검증할 수 없습니다.

## 전송 계층

`codef.transport` 패키지입니다(#62). 호출 결과를 위 전송 단계의 `CodefProductResponseReader`에 넘깁니다. **합성 응답과 로컬 스텁 서버로만 시험했습니다. 실제 CODEF 호출 결과가 아닙니다.** 샌드박스 확인 전에는 아래 "제안" 값과 토큰 응답 형식이 맞는지 모릅니다.

- [CodefClient](../../backend/src/main/java/com/beone/api/codef/transport/CodefClient.java): 진입점. 토큰 캐시를 공유하고 관측을 엽니다.
- [CodefObservation](../../backend/src/main/java/com/beone/api/codef/transport/CodefObservation.java): 관측 1회. 객체 하나가 호출 상한의 집계 범위이고, 토큰과 상품 API마다 3회까지 보냅니다. 어떤 조회를 관측 1회로 볼지는 호출자가 정하며, 프로세스를 넘어선 누적은 세지 않습니다. 로그인 입력(`id`, `password`, `cardNo`, `cardPassword`)은 거부합니다.
- [CodefCallResult](../../backend/src/main/java/com/beone/api/codef/transport/CodefCallResult.java): `Answered`(HTTP 응답을 받음. 401·서버 오류 포함) 또는 `Failed`(단계, 이유, `httpStatus`). `httpStatus`(`OptionalInt`)는 토큰 단계에서 응답을 받은 실패(`TOKEN_REJECTED`, `TOKEN_UNREADABLE`)에만 채워집니다.
- [CodefTokenCache](../../backend/src/main/java/com/beone/api/codef/transport/CodefTokenCache.java): 토큰을 서버 메모리에만 둡니다. 발급 시각 + 7일이 지나면 재발급하고, 동시 요청은 한 번만 발급합니다.
- [CodefHttp](../../backend/src/main/java/com/beone/api/codef/transport/CodefHttp.java): 요청 한 번을 보냅니다. JDK HTTP 클라이언트를 쓰고 TLS 1.2 이상만 허용합니다.
- [CodefApiKeys](../../backend/src/main/java/com/beone/api/codef/transport/CodefApiKeys.java): 키를 프로세스 환경 변수에서만 읽습니다. 없으면 "설정 안 됨"이고 서버는 뜹니다.
- [CodefTransportSettings](../../backend/src/main/java/com/beone/api/codef/transport/CodefTransportSettings.java), [CodefTransportConfiguration](../../backend/src/main/java/com/beone/api/codef/transport/CodefTransportConfiguration.java): 호스트·시간 초과 설정과 조립.

```java
CodefObservation observation = codefClient.startObservation();
CodefCallResult<CodefHeldCard> result = observation.heldCards(Map.of("organization", "0301"));
```

| 항목 | 동작 | 근거 |
| --- | --- | --- |
| 토큰 요청 | `POST https://oauth.codef.io/oauth/token`, form 본문 `grant_type=client_credentials&scope=read`, Basic 인증 | 공식 REST API 예제 |
| 토큰 응답 | `{`로 시작하면 그대로, 아니면 URL 디코딩 뒤 JSON으로 읽음 | 사용자 결정. 샌드박스 확인 뒤 한쪽으로 고정 |
| 상품 요청 | POST, Bearer, UTF-8 JSON. `Content-Type: application/json` | 본문은 SDK, Content-Type은 제안. 샌드박스에서 재확인 |
| 시간 초과 | 상품 310초, 토큰 110초, 연결 5초. 본문 수신까지 포함 | 300초는 공식. 여유 10초, 110초, 5초는 제안 |
| 재시도 | 없음. 리다이렉트도 따라가지 않음 | 사용자 결정 |
| 401, `CF-00401` | 토큰을 버리고 결과를 돌려줌. 다시 보내지 않음 | 제안 |
| 호출 상한 | 관측 1회에 API별 3회. 넘으면 보내기 전에 거부 | 사용자 결정 |
| 로그 | API 이름, HTTP 상태, `result.code`(`CF-` 5자리 형식만, 그 밖은 `UNRECOGNIZED`) | CLAUDE.md |

시간 초과나 연결 끊김은 상대가 처리했는지 알 수 없다는 뜻입니다. 사용자가 다시 실행합니다. JDK 클라이언트는 연결 수립 실패 때 내부에서 연결을 다시 시도할 수 있습니다. 이 단계는 요청이 서버에 닿기 전이고, 연결된 뒤 끊긴 요청을 다시 보내지 않는 것은 테스트로 확인했습니다.

환경 변수와 샌드박스 확인 절차는 [CODEF 설정과 샌드박스 확인 절차](../development/codef-sandbox-check.md)를 봅니다. 310초 대기를 동기로 둘지 비동기로 둘지는 2차 API 명세에서 정합니다. 이 패키지를 호출하는 엔드포인트는 아직 없습니다.

## 결과 코드 분류

| 분류 | 코드 | 근거(사본) |
| --- | --- | --- |
| 성공 | `CF-00000`과 정확히 일치 | 성공 코드 별첨 "CF-00000 성공" 행 |
| 추가 인증 필요 | `CF-03002`이면서 `data.continue2Way`가 불리언 `true` | 카드 API 3개 문서 "추가인증 출력부" |
| OAuth 토큰 오류 | `CF-09990`~`CF-09994`, `CF-00026` | 오류코드 검색 "토큰" |
| 인증서 만료·폐기 | `CF-12806`, `CF-12808` | 오류코드 검색 "만료" |
| 통신 실패·시간 초과·차단 | `CF-09980`, `CF-09981`, `CF-12200`, `CF-12202`, `CF-12203` | 오류코드 검색 "통신" |
| 요청 형식 오류 | `CF-00405`, `CF-11301`~`CF-11306` | 오류코드 검색 "통신" |
| 도메인·IP·연결 계정 | `CF-00004`, `CF-00005`, `CF-00013`, `CF-00017`, `CF-04015`, `CF-04038` | 오류코드 검색 "토큰" |
| 카드소지확인 지원 보류 | `CF-12108` | 공식 오류 목록 249행 "조회에 실패했습니다. 대상기관 사이트에서 카드소지확인이 필요합니다." |
| 미분류(확인 필요) | 그 밖의 모든 코드(공식 목록에 있지만 분류하지 않은 코드 포함), 코드 누락 | — |

- 성공은 `result.code`가 문자열 `CF-00000`과 정확히 같을 때만입니다. 앞뒤 공백, 소문자, 자릿수 차이, 메시지 문구("성공"), HTTP 상태로는 성공을 추정하지 않으며 미분류입니다. 성공 분류는 공급자 결과 코드의 분류일 뿐이며 `data`를 쓸 수 있다는 뜻, 실제 응답 검증, 상태값 매핑(D-04), 상품 확인(D-01)을 뜻하지 않습니다.
- `CF-03002`인데 `continue2Way`가 없거나 `false`이거나 불리언이 아니면(예: 문자열 `"true"`) 미분류입니다. 문서는 "추가 인증 여부에 관한 판단은 continue2Way 값이 있는지, 'true' 값인지로 판단"하라고 하고 타입을 Boolean으로 적었으며, 그 밖의 `CF-03002` 의미는 사본에 없습니다. 연결 실패로 숨기지 않습니다.
- 카드소지확인은 카드번호·카드 비밀번호 입력이 필요해 BeONE의 수집 금지 원칙(CLAUDE.md 보안)에 따라 지원 보류입니다.
- 공식 오류 목록 대조(#45): 대조 당시(#47 정정 전) 분류 표의 코드 30개는 모두 공식 전체 목록(587행)에 있습니다(코드·원본 행 번호·공식 설명 대조).
  - **불일치 2건:** `CF-12401`(원본 270행)과 `CF-12834`(315행)는 공식 설명이 카드 확인 경로에 한정되지 않아 당시 분류와 맞지 않았습니다. #47(PR #48)에서 정정되어 `develop`에 반영됐고, 현재 두 코드는 `UNCLASSIFIED`입니다(아래 "정정(#47)").
  - `CF-03002`는 공식 목록에 두 행입니다. 47행은 비고가 없고, 48행 비고는 "추가 정보 입력을 위한 대기 중 타임아웃 발생"이며 조치사항은 "CF-03002 반환 이후 추가인증 Timeout 종료 시 CF-12001로 변경"입니다. 분류 규칙(`continue2Way` 불리언 `true`)은 그대로입니다.
  - `CF-12872`(340행)의 공식 설명은 "인증 요청이 정상적으로 완료되지 않았습니다. 확인 후 거래하시기 바랍니다."뿐이고 추가 설명이 없습니다. 공식 SDK 상수 이름(`isAddAuthExceedResponse`)만으로 횟수 초과·시간 초과·재시도로 정하지 않으며, 미분류를 유지합니다.
- 정정(#47): `CF-12401`·`CF-12834`는 #42에서 카드 API 문서의 현대카드 카드 비밀번호 사례(미입력 시 `CF-12401`, 3회 오류 시 `CF-12834`)를 근거로 지원 보류로 분류했습니다. 그러나 공식 오류 목록 전체 다운로드 원본(Aside 수집, 관찰 2026-11-01T08:16:27Z)의 설명은 카드 확인 경로에 한정되지 않습니다.
  - 270행 `CF-12401`: "로그인 파라미터가 누락되었습니다."
  - 315행 `CF-12834`: "계정 잠김 해제가 필요합니다. 확인 후 거래하시기 바랍니다."
  - 사용자 결정(2026-11-01)에 따라 미분류로 정정했고, 이 응답은 이제 "지원 보류" 대신 "확인 필요"로 나옵니다. 현대카드 사례는 기관별 발생 사례일 뿐 공통 분류 근거로 쓰지 않습니다.
  - 로그인 누락·계정 잠김 분류, 자동 재시도, 계정 잠금 해제, 추가 정보 수집은 만들지 않았습니다.
- `CF-13316`(만료된 발급번호), `CF-12040`(우리카드 카드목록 없음)은 사본에 있지만 이번 분류 표 밖이라 미분류입니다.

## 옮기는 필드와 기준

공식 명세의 모든 필드를 복제하지 않습니다. BeONE 계산(거래 분류, 실적·혜택 판단)과 보유 카드 후보 표시에 필요한 비민감 필드만 옮깁니다.

- [CodefApproval](../../backend/src/main/java/com/beone/api/codef/CodefApproval.java)(승인내역): 사용일자·사용일시·카드명·가맹점명·가맹점 업종·이용금액·원화금액·통화코드·결제방법(`"1"`~`"3"`)·국내/외(`"1"`·`"2"`)·취소여부(`"0"`~`"3"`)·취소금액·승인번호(취소 대응용).
- [CodefHeldCard](../../backend/src/main/java/com/beone/api/codef/CodefHeldCard.java)(보유카드): 카드명·카드구분·휴면 여부(`"Y"`·`"N"`)·발급일자(신규 유예 확인 후보)·카드상태.
- [CodefCardPerformance](../../backend/src/main/java/com/beone/api/codef/CodefCardPerformance.java)(실적조회): 카드명·소지자구분·발급사, 혜택(구분·혜택명·업종), 혜택 상세(혜택명·실적조건·업종), 실적현황(현재이용금액·필요실적금액·기준이용금액·반영기간·제공기준·충족여부 `"0"`·`"1"`).

옮기지 않는 필드:

- 민감: 카드번호(`resCardNo`·`resCardNo1`), 가맹점 사업자번호·전화번호·주소, 세션(`commSession`), 추가 인증 식별값(`jti`·`jobIndex`·`threadIndex`·`twoWayTimestamp`·`extraInfo`), 카드 유효기간(`resValidPeriod`), 결과 문구(`message`·`extraMessage`).
- 현재 계산에 쓰지 않음: 가맹점번호·부가세·봉사료·할부개월·결제예정일·승인내역 조회 기간, 카드 이미지·혜택 바로가기 링크, 기관마다 표기가 다른 교통카드 여부.

결과 타입은 옮긴 값만 가지므로 `toString`·예외 문구에 원문이나 민감값이 나타나지 않습니다. 합성 민감값으로 이를 확인하는 테스트가 있습니다.

```java
// 합성 예시: 전송 계층이 받은 본문을 해석만 한다
CodefResponse<CodefApproval> response = CodefCardResponseParser.parseApprovals(body);
if (response.result().category() == CodefResultCategory.SUCCESS) {
	// 결과 코드만 성공. data가 형식 오류·값 없음이면 그대로 남는다
}
if (response.result().category() == CodefResultCategory.ADDITIONAL_AUTHENTICATION_REQUIRED) {
	// 추가 인증 필요. 연결 실패로 숨기지 않는다
}
if (response.data() instanceof CodefList.Items<CodefApproval> items) {
	items.items(); // 0건 이상. 결과 코드와 별개로 해석된 값
}
```

## 오프라인 동기화 변환 (#82)

`codef.sync`는 전송 계층이 돌려준 세 조회 대상(보유카드·승인내역·실적조회)의 호출 결과를 받아, 대상마다 "쓸 수 있는지"를 판정하고 2차 동기화(D-16)에 필요한 중간 값으로 옮기는 **오프라인 변환**입니다. CODEF를 호출하지 않고, 저장하지 않으며, 엔진 입력이나 도메인 원장을 만들지 않습니다. 테스트는 공식 시트 셀을 근거로 만든 합성 응답만 쓰며 실계정 관측이 아닙니다.

```text
codef/sync/
├── CodefSyncSnapshot        세 대상 결과 묶음. 기관 코드·조회 시각(주입받은 Clock에서 한 번 읽음)
├── CodefTargetOutcome       대상 하나의 판정: Usable(결과·형태·항목) / Unusable(이유)
├── CodefUnusableReason      쓸 수 없는 이유(중립값): 호출 실패·HTTP 401·그 밖의 HTTP 상태·본문은 받았지만 쓸 수 없음
├── CodefHeldCardCandidate   보유카드 → 매핑 전 후보(보유 출처 CODEF, 매핑 상태 UNMAPPED)
├── CodefApprovalRowView     승인 행 → 취소 표현 형태·원화 금액 가능 여부·승인번호 유무
└── CodefPerformanceReference 실적조회 → 카드사 조회 실적(참고), 계산 미사용 고정
```

처리 흐름:

1. 판정([CodefTargetOutcome](../../backend/src/main/java/com/beone/api/codef/sync/CodefTargetOutcome.java)): 쓸 수 있는 경우는 응답을 받았고(`Answered`) HTTP 200 본문을 해석했으며(`Interpreted`) 결과가 성공(`CF-00000`)이고 `data`가 항목 목록(`Items`, 0건 포함)일 때뿐입니다. 그 밖에는 [CodefUnusableReason](../../backend/src/main/java/com/beone/api/codef/sync/CodefUnusableReason.java)에 전송 실패 단계·이유, HTTP 상태, 결과 코드·트랜잭션 아이디·분류, `data` 상태(누락·형식 오류와 고정 사유)를 보존합니다. 추가 인증 필요(`CF-03002` + `continue2Way` 불리언 `true`)는 2차 요청이 범위 밖이라 쓸 수 없음으로 두되 `additionalAuthenticationRequired()`로 드러냅니다. 이 이유를 공개 오류 코드로 바꾸는 일은 HTTP 계층(팀원 담당)에 남깁니다.
2. 부분 실패: 세 대상은 따로 판정합니다. 하나가 실패해도 나머지 결과는 바뀌지 않습니다.
3. 보유카드([CodefHeldCardCandidate](../../backend/src/main/java/com/beone/api/codef/sync/CodefHeldCardCandidate.java)): 카드명(나누지 않음)·휴면 여부(휴면도 목록에 남김)·카드구분·발급일자·상태를 그대로 옮깁니다. 보유 출처 `CODEF`, 매핑 상태 `UNMAPPED`는 엔진 입력 `HeldCardInput`의 같은 이름 토큰과 같지만, 이 타입은 `benefit`에 의존하지 않습니다. 등록일은 발급일로 추정하지 않아 항상 미확인입니다.
4. 승인내역([CodefApprovalRowView](../../backend/src/main/java/com/beone/api/codef/sync/CodefApprovalRowView.java)): 기존 [취소 표현 기술](codef-kb.md#취소-표현-구분) 결과, 원화 금액을 만들 수 있는지(통화가 정확히 `KRW`, 국내, 이용금액 해석 모두 맞을 때만. KB 원화금액은 미제공이라 환산하지 않음), 승인번호가 있는지만 담습니다. 원문 승인번호와 금액은 담지 않고, 사용일자·사용일시는 원문 문자열 그대로입니다.
5. 실적조회([CodefPerformanceReference](../../backend/src/main/java/com/beone/api/codef/sync/CodefPerformanceReference.java)): 혜택·실적현황을 모두 담고 하나를 고르지 않습니다. 금액·날짜는 값 또는 미확인 이유(`ABSENT`·`UNPARSED`) 중 정확히 하나이며, 미확인을 0원으로 바꾸지 않습니다. 반영기간은 정확히 8자리 yyyyMMdd이고 실제 있는 날짜일 때만 읽고, 당월 종료일(조회일 전날)을 말일로 바꾸지 않습니다. `usedInCalculation()`은 항상 거짓이며 `StateInput`·`StateValue`로 바꾸는 메서드가 없습니다(공개 API 검사 테스트).

```java
// 합성 예시: 전송 계층의 세 호출 결과를 판정·변환만 한다
CodefSyncSnapshot snapshot = CodefSyncSnapshot.assemble("0301", clock, heldCards, approvals, performances);
if (snapshot.approvals() instanceof CodefTargetOutcome.Unusable<CodefApprovalRowView> unusable) {
	unusable.reason(); // 중립 이유. 보유카드·실적 결과는 그대로이고 수동 입력 경로는 유지한다
}
```

1단계에서 구현하지 않은 것: 카드 연결(R1), Type2 짝짓기와 재조회 중복 판단 키(R2), 승인 일자·시각 해석(R4), CODEF 원장 행·불완전 사유는 아래 [2단계](#원장-후보-행짝짓기재조회-대조-92)에서 구현했습니다. 카드명 → 후보 상품(`candidateCardProductId`) 산출(R3, 보류), 공개 오류 코드 대응(R5, 팀원), 완전 선언·도메인 `Ledger`/`LedgerTransaction` 연결(R7·R8), 엔진 입력 생성, 저장, 엔드포인트는 여전히 없습니다. 실제 KB 응답이 시트의 `O`·`△` 필드를 주는지는 실계정 관측 전까지 미확인입니다.

## 원장 후보 행·짝짓기·재조회 대조 (#92)

2026-11-16 사용자 결정(R1·R2·R4를 "관측 전 후보 처리" 제한 아래 추천안대로 채택, R3 보류)과 D-17을 따라, 승인내역 응답 하나를 **CODEF 원장 후보 행**으로 판정하는 순수 함수입니다. 같은 입력이면 같은 결과를 돌려주고 저장·로그·네트워크가 없습니다. 테스트는 합성 응답과 테스트 코드 안의 합성 키만 쓰며 실계정·실키 관측이 아닙니다. 이 판정은 도메인 원장(`LedgerTransaction`)이 아니고 HTTP API 완성도 아닙니다.

```text
codef/sync/
├── CodefCardLinker / CodefCardLink      R1 카드 연결: 같은 동기화 보유카드와 카드명 정확 일치 1장일 때만 Linked
├── CodefPerformanceLink                 실적 참고값 + 카드 연결 결과(미연결이면 카드 미연결)
├── CodefUsedDateTime                    R4 사용일자·사용일시 해석(제안 형식, 실패는 추정 없이 UNPARSED·ABSENT)
├── CodefApprovalFingerprinter           승인번호 → 지문(HMAC) 함수 인터페이스. 주입받기만 함
├── CodefApprovalFingerprint             지문 값
├── CodefApprovalKey                     R2 중복 판단 키(카드명, 지문, 사용일자, 사용일시, 부호 포함 금액)
├── CodefLedgerCandidateBuilder          판정 순수 함수(행 판정 → 같은 키 → 재조회 대조 → Type2 짝짓기)
├── CodefLedgerCandidates                결과: 행 목록, 불완전 표시 범위, 받아들인 키·종류
├── CodefLedgerCandidateRow              행 하나: 위치, 카드 연결, 날짜·시각, 취소 표현 형태, Accepted / Incomplete
├── CodefLedgerRowKind                   APPROVAL / APPROVAL_FULLY_CANCELED(Type1) / CANCELLATION(Type2)
├── CodefRecheck                         NEW / ALREADY_RECORDED / CANCELED_SINCE_RECORDED
├── CodefIncompleteReason                불완전 사유 5종(원장 조회 명세 사본과 같음)
├── CodefIncompleteMark                  연결된 카드·그 행 날짜에만 붙는 불완전 표시
└── CodefAttributionMonth                취소 귀속월 NEEDS_CONFIRMATION
```

용어:

- **지문(HMAC)**: 비밀키로 만든 되돌릴 수 없는 값입니다. 원문 승인번호를 남기지 않고 같은 번호인지만 비교합니다. 승인번호는 자릿수가 짧아 평문 해시(SHA-256 등)는 전수 대입으로 원문을 되찾을 수 있으므로 키가 있는 HMAC을 씁니다. 키가 바뀌면 지문도 바뀌어 이전 기록과 비교가 끊깁니다.
- **원장 후보 행**: 원장에 넣어도 되는지 판정만 한 행입니다. 저장은 호출자(업무 서비스, 팀원 담당)가 합니다.

처리 흐름([CodefLedgerCandidateBuilder](../../backend/src/main/java/com/beone/api/codef/sync/CodefLedgerCandidateBuilder.java)). 행마다 처음 걸린 사유 하나만 남깁니다.

1. 카드 연결(R1, [CodefCardLinker](../../backend/src/main/java/com/beone/api/codef/sync/CodefCardLinker.java)): 행 카드명과 문자열로 정확히 같은 보유카드가 정확히 한 장일 때만 연결합니다. 공백·대소문자·`카드명|발급사` 분리 같은 정규화는 하지 않습니다. 0장·2장 이상·카드명 없음·보유카드 조회 실패면 연결하지 않고 그 행은 `UNDETERMINED_ROW`, 실적은 카드 미연결입니다. 연결은 같은 실물 카드나 공식 상품의 입증이 아닙니다.
2. 형태: 기존 [취소 표현 기술](codef-kb.md#취소-표현-구분)을 씁니다. 부분취소 코드(`"2"`)와 Type1 취소금액 불일치는 `PARTIAL_CANCELLATION_SUSPECTED`, 그 밖의 판단 불가 형태(거절 `"3"`, 음수 금액의 정상 코드 등)와 KB 밖 기관은 `UNDETERMINED_ROW`입니다. 원화 금액을 만들 수 없는 행(통화가 `KRW`가 아니거나 비었음, 해외)도 `UNDETERMINED_ROW`입니다.
3. 키 재료(R2·R4): 승인번호 또는 사용일자가 없으면 `DUPLICATE_KEY_AMBIGUOUS`입니다. 사용일자가 정확히 8자리 yyyyMMdd의 실제 날짜가 아니거나, 사용일시가 없거나 정확히 6자리 HHmmss의 유효 시각(00:00:00~23:59:59)이 아니면 `UNDETERMINED_ROW`입니다([CodefUsedDateTime](../../backend/src/main/java/com/beone/api/codef/sync/CodefUsedDateTime.java), `ResolverStyle.STRICT`). 형식은 제안이며 관측 전입니다.
4. 한 응답 안 같은 키: 키(카드명, 지문, 사용일자, 사용일시, 부호 포함 금액)가 같은 행이 둘 이상이면 모두 `DUPLICATE_KEY_AMBIGUOUS`입니다. 취소여부는 키에 넣지 않습니다.
5. 재조회 대조: 이전 기록(키 → 종류 맵)을 입력으로 받습니다. 없던 키는 `NEW`, 같은 종류면 `ALREADY_RECORDED`(다시 넣지 않음), 정상으로 기록된 키가 Type1 전액 취소로 바뀌면 `CANCELED_SINCE_RECORDED`(같은 승인에 연결된 취소)입니다. 그 밖의 종류 변화(예: Type1 → 정상)는 모순으로 보고 `UNDETERMINED_ROW`입니다.
6. Type2 짝짓기(D-17): 같은 카드·같은 지문의 정상 행이 **이 응답 안에** 정확히 하나이고 금액 절댓값이 같을 때만 받아들입니다. 없으면 `NO_MATCHING_APPROVAL`(조회 기간을 넓히지 않음, 승인번호를 공유하지 않는 기관도 여기로 떨어짐), 여럿이면 `MULTIPLE_MATCHING_APPROVALS`, 취소가 작으면 `PARTIAL_CANCELLATION_SUSPECTED`, 취소가 크거나 짝 정상 행이 받아들여지지 않았거나 정상 행 하나를 취소 행 여럿이 가리키면 `UNDETERMINED_ROW`입니다. 이전 기록으로 짝을 대신하지 않습니다.
7. 결과([CodefLedgerCandidates](../../backend/src/main/java/com/beone/api/codef/sync/CodefLedgerCandidates.java)): Type1·Type2 행의 취소 귀속월은 항상 `NEEDS_CONFIRMATION`입니다. `incompleteMarks()`는 R1로 연결된 카드의 그 행 사용일자에만 불완전을 표시하고(날짜를 해석하지 못했으면 기간 미확인), 카드 미연결 행은 어느 카드에도 표시하지 않습니다. `acceptedKinds()`는 다음 재조회의 이전 기록으로 쓸 수 있는 키·종류입니다. 원장이 "완전"하다고 선언하지 않습니다(R7).

```java
// 합성 예시: 같은 동기화의 보유카드로 연결기를 만들고, 승인내역을 판정만 한다
CodefCardLinker linker = CodefCardLinker.of(snapshot.heldCards());
CodefLedgerCandidates candidates = CodefLedgerCandidateBuilder.build("0301", linker, approvals,
		fingerprinter /* 주입. 운영 구현은 미결 */, previousKinds /* 처음이면 Map.of() */);
candidates.incompleteMarks(); // 연결된 카드·그 날짜에만 붙는 불완전 표시
candidates.acceptedKinds();   // 저장은 호출자가 한다
```

보안: 원문 승인번호는 지문 함수에 넘기기만 하고 결과 필드·`toString`·예외 문구·로그에 남기지 않습니다. 지문 함수가 원문을 그대로 돌려주면 `IllegalStateException`(문구에 원문 없음)으로 거부합니다. 결과 타입은 `CodefApproval`을 담지 않습니다(테스트로 확인).

구현하지 않은 것:

- HMAC 운영 구현체, 키 이름·보관 위치·공급 방식(환경 변수·사용자 입력 등)·회전: 미결입니다. 설정 키·환경 변수·Spring Bean도 만들지 않았습니다. 테스트는 테스트 코드 안 합성 키만 씁니다.
- 카드명 → 후보 상품 산출(R3, 보류): 새 후보는 `UNMAPPED`이고, `USER_CONFIRMED`를 만들거나 바꾸지 않습니다.
- 원장 완전 선언(R7), 도메인 `Ledger`/`LedgerTransaction` 연결(R8), 저장·영속·HTTP·DB.
- 실계정 미확인: 승인번호를 승인 행과 취소 행이 공유하는지, Type1 행이 재조회 때 같은 행 그대로 정상에서 취소로 바뀌는지, 사용일자·사용일시 실제 형식.
- 조회 기간이 달라진 재조회에서 이미 짝지어 기록한 Type2 취소의 원 승인이 기간 밖으로 빠지면, 그 취소 행은 다시 `NO_MATCHING_APPROVAL`로 표시됩니다(이전 기록으로 짝을 대신하지 않는 보수적 처리).

## CODEF 연결 API (#126)

`/api/v1/me/codef/connection`의 GET(연결 상태)·POST(계정 연결)·DELETE(연결 해제)입니다([계약](../api/codef.md)). 결정 30에 따라 **fixture·가짜 구현·로컬 HTTP·테스트 영속까지**이며 실 CODEF·실키·실계정은 쓰지 않습니다. 해제 범위(P4)와 connectedId 암호화(P5)는 2026-11-18 사용자 직접 결정입니다.

```text
codef/
├── application/
│   ├── CodefConnectionService   연결 조회·등록·해제. 게이트웨이 호출은 트랜잭션 밖, 저장만 짧은 트랜잭션
│   ├── ConnectedIdCipher        connectedId AES-256-GCM 암호화(패키지 내부)
│   ├── ConnectedIdKeySource     암호화 키 공급. 운영 구현 EnvironmentConnectedIdKeySource(환경 변수)
│   ├── CodefGateway             CODEF 계정 등록·정리 경계(인터페이스)
│   ├── FakeCodefGateway         local·demo용 가짜. 합성 아이디로 실패 재현, 그 밖은 합성 connectedId
│   ├── CodefRegistration        등록 결과: Registered·LoginRejected·CardVerificationRequired·Unavailable
│   ├── CodefRegistrations       결과 코드 분류 → 등록 결과 대응표
│   ├── CodefConnectionStatusLookup card의 CodefConnectionLookup 구현. 연결 행이 있으면 GET /api/v1/me codefConnected=true(#136)
│   └── CodefConnectionView·CodefConnectionStatus·CodefConnectResult, 예외 5종
├── persistence/                 codef_connections 엔티티·저장소(Flyway V10, 임시 번호)
└── web/                         컨트롤러, 요청·응답, 오류 코드, 기능별 처리기
```

- **연결 상태**: 행이 없으면 `NOT_CONNECTED`(`connectedAt`·`lastSyncedAt` `null`). 행은 `CONNECTED` 또는 `RECONNECT_REQUIRED`이고 `reconnectRequired`는 후자일 때만 참입니다. 가상 포트폴리오 사용자는 연결할 수 없어 항상 `NOT_CONNECTED`입니다.
- **최초 연결 201, 재연결 200**: 연결 행이 있으면(상태와 무관) 재연결입니다. 재연결 성공이면 connectedId를 새 값으로 바꾸고 `connectedAt`을 재연결 시각으로 바꾸며 `lastSyncedAt`은 그대로 둡니다. 이전 connectedId는 커밋 뒤 게이트웨이에 정리를 요청합니다. 재연결이 실패하면 기존 연결을 그대로 둡니다(관리자 판단 P3). 저장이 실패하면 방금 받은 connectedId를 정리합니다. 정리 실패는 BeONE 상태를 바꾸지 않고 예외 클래스 이름만 로그에 남깁니다.
- **검사 순서**: 요청 형식 400(필수 4필드, `organization`은 `0301`, `loginType`은 `ID`만, 문자열이 아닌 값은 `errors: []`) → 가상 포트폴리오 409 `PORTFOLIO_READ_ONLY` → 암호화 키·게이트웨이가 없으면 게이트웨이를 부르지 않고 502 `CODEF_UNAVAILABLE` → 게이트웨이 결과. local·demo 밖에서는 컨트롤러가 등록되지 않아 404입니다.
- **연결 해제(DELETE, 2026-11-18 사용자 직접 결정 P4)**: 204, 본문 없음. 연결 행(암호화한 connectedId)을 지우고 추가 수집을 멈춥니다. 커밋 뒤 CODEF 쪽 connectedId 정리를 요청합니다(키가 없거나 복호화에 실패하면 정리만 건너뜀). **해제는 데이터 완전 삭제가 아닙니다.** CODEF로 들어온 보유 카드·codef 승인 행·사용자가 확인한 매핑은 보존하며 카드는 보유 카드 해제로 따로 지웁니다. 보존 기간, 탈퇴·완전 삭제 정책은 미결입니다. 연결이 없으면 409 `CODEF_NOT_CONNECTED`. 가상 포트폴리오는 409 `PORTFOLIO_READ_ONLY`입니다. [docs/api.md](../api.md#업무-오류-코드)의 "가상 포트폴리오의 CODEF 쓰기 요청" 규칙에 따른 것이며 계약 DELETE 실패 목록에도 반영했습니다.
- **결과 → 오류 코드**(관리자 판단 P1·P2, [`CodefRegistrations`](../../backend/src/main/java/com/beone/api/codef/application/CodefRegistrations.java)):

| 결과 분류 | 등록 결과 | 응답 |
| --- | --- | --- |
| `SUCCESS`이고 connectedId가 있음 | 등록 | 201·200 |
| `SUCCESS`인데 connectedId가 비어 있음 | 사용 불가 | 502 `CODEF_UNAVAILABLE` |
| `CARD_VERIFICATION_NOT_SUPPORTED`(`CF-12108`) | 카드소지확인 필요 | 409 `CODEF_CARD_VERIFICATION_NOT_SUPPORTED` |
| 추가 인증 필요(2-way 범위 밖), 토큰·인증서·통신·요청 형식·도메인·IP·연결 계정 오류, 미분류 | 사용 불가 | 502 `CODEF_UNAVAILABLE` |

  **실코드 대응이 없는 것**: 결과 분류에 로그인 실패 분류가 없으므로(#47에서 `CF-12401`·`CF-12834`를 미분류로 정정) 400 `CODEF_LOGIN_FAILED`로 가는 실 결과 코드는 없습니다. 이 응답은 가짜 게이트웨이의 로그인 거절로만 나옵니다. `RECONNECT_REQUIRED`로 바꾸는 실 결과 코드도 없습니다. 근거 있는 코드가 확인되면 별도 결정으로 더합니다. 시간 초과를 504로 나누지 않습니다.
- **민감 정보**: 비밀번호는 `char[]`로 받아 게이트웨이에 넘긴 뒤 지우고 저장하지 않습니다. 아이디도 저장하지 않습니다. 요청 객체·등록 결과·엔티티의 문자열 표현에 아이디·비밀번호·connectedId를 넣지 않습니다. 가짜 게이트웨이는 RSA 암호화를 하지 않습니다(관리자 판단 P7). 실 구현은 받은 즉시 CODEF 공개키로 암호화해 보내야 합니다(D-14 원칙 9).
- **가짜 게이트웨이 합성 아이디**: `fake-login-rejected`(로그인 거절), `fake-card-verification`(카드소지확인), `fake-unavailable`(사용 불가). 그 밖의 아이디는 성공이며 `fake-cid-` 접두어의 무작위 connectedId를 줍니다. 실계정 아이디를 넣어도 CODEF로 보내지 않습니다.
- **현재 사용자 응답(#136)**: `GET /api/v1/me`의 `codefConnected`는 연결 행이 있으면 `true`입니다(`RECONNECT_REQUIRED` 포함, 재연결 필요 여부는 연결 상태 GET으로 봄). card가 codef를 참조하지 않도록 card의 조회 포트 `CodefConnectionLookup`을 codef의 `CodefConnectionStatusLookup`이 구현합니다.
- **저장**: `codef_connections`(사용자당 한 행, 아이디·비밀번호 컬럼 없음). 동시 연결·해제는 사용자 행을 먼저 잠가 차례로 처리합니다.
- **connectedId 암호화(2026-11-18 사용자 직접 결정 P5)**: AES-256-GCM, 저장 형식 `v1:` + Base64(무작위 12바이트 IV ‖ 암호문 ‖ 16바이트 태그), 사용자 ID를 추가 인증 데이터로 묶어 다른 사용자 행으로 옮긴 값은 복호화되지 않습니다. 키는 프로세스 환경 변수 `BEONE_CODEF_CONNECTED_ID_KEY`(Base64로 적은 32바이트)에서 읽고 `application*.yml`에는 두지 않습니다. local·demo에도 고정 기본값이 없으며, 키가 없거나 형식이 틀리면 연결 POST가 502 `CODEF_UNAVAILABLE`로 실패하고 평문으로 대신 저장하지 않습니다. 테스트는 합성 키를 주입합니다. 운영 키 공급·설정과 키 회전은 별도 승인이며 구현하지 않았습니다. 키 값은 저장소·로그에 남기지 않습니다.

```bash
# local 프로필 서버에서. 아이디·비밀번호는 합성 값이며 CODEF로 보내지 않는다
curl -X POST "http://localhost:8080/api/v1/me/codef/connection" \
  -H "X-BeONE-Demo-User: MY_CARDS" -H "Content-Type: application/json" \
  -d '{"organization":"0301","loginType":"ID","loginId":"demo-user","password":"demo-pass"}'
```

테스트: 단위 `CodefRegistrationsTest`(분류 전부), `FakeCodefGatewayTest`, `ConnectedIdCipherTest`(왕복·IV·다른 사용자·변조·다른 키), `EnvironmentConnectedIdKeySourceTest`, `CodefConnectionServiceTest`(최초·재연결 교체·실패 시 유지·저장 실패 정리·비밀번호 지움·키 없음·해제), 웹 `CodefConnectionApiWebTest`(계약 예시 JSON, 실패 응답 전부, 검증 400, 헤더), `CodefConnectionProfileOffWebTest`(프로필 밖 404), 통합 `CodefConnectionIntegrationTest`(V10, 최초 201·재연결 200과 connectedId 교체·정리, 암호문 저장, 해제 204와 보유 카드 보존, 실패 시 유지, 포트폴리오 409, 사용자 분리, 자격 증명 컬럼 없음), `CodefConnectionWithoutKeyIntegrationTest`(키 없음 502, 저장 없음).

## CODEF 조회·동기화 API (#129)

`POST /api/v1/me/codef/syncs`입니다([계약](../api/codef.md#post-apiv1mecodefsyncs--codef-조회동기화)). 연결 API와 같이 **fixture·가짜 구현·로컬 HTTP·테스트 영속까지**이며 실 CODEF·실키·실계정은 쓰지 않습니다. 보유카드·승인내역·실적을 조회해 각각 매핑 전 보유 카드, codef 전용 승인 행, 카드사 조회 실적(참고)으로 옮깁니다. 조회는 요청 안에서 동기로 끝납니다(관리자 판단 P11). 이 절의 관리자 판단 K1·K2·P2·P6·P8·P9·Q1~Q8은 2026-11-18 C0·C3 확인에서 정한 것이고, P6은 사용자 직접 결정입니다.

```text
codef/
├── application/
│   ├── CodefSyncService             검사 → 조회(트랜잭션 밖) → 판정 → 짧은 저장 트랜잭션
│   ├── CodefSyncGateway             보유카드·승인내역·실적 조회 경계(인터페이스). FakeCodefGateway가 함께 구현
│   ├── CodefSyncPeriod              조회 기간 규칙(P9). 위반은 CodefSyncPeriodException(400)
│   ├── CodefHeldCardMatcher         CODEF 보유카드 ↔ 기존 CODEF 보유 카드 대조(P8·Q1·Q2, 순수 함수)
│   ├── ApprovalFingerprintKeySource 승인번호 HMAC 키 공급. 운영 구현 EnvironmentApprovalFingerprintKeySource(환경 변수)
│   ├── HmacApprovalFingerprinter    승인번호 → HMAC-SHA256 소문자 16진수 64자(패키지 내부)
│   ├── CodefSyncResult·CodefSyncTarget 결과와 부분 실패 대상
│   └── CodefConnectionInvalidException·CodefReconnectRequiredException
├── persistence/                     codef_syncs·codef_held_cards·codef_approval_rows·codef_performance_references(Flyway V12, 임시 번호)
└── web/                             CodefSyncController, 요청·응답, 기존 오류 코드·처리기 확장
```

처리 흐름과 검사 순서:

```text
요청 본문·날짜 형식 400 → 가상 포트폴리오 409 PORTFOLIO_READ_ONLY → 기간 규칙 400 → 연결 없음 409 CODEF_NOT_CONNECTED
→ RECONNECT_REQUIRED 상태 409 CODEF_RECONNECT_REQUIRED → 게이트웨이·connectedId 키 없음 502 CODEF_UNAVAILABLE
→ 보유카드·승인내역(HMAC 키가 있을 때만)·실적 조회 → codef.sync로 대상별 판정
→ 세 대상 모두 실패면 502(아무것도 저장 안 함), 일부 실패면 200 + partialFailures
→ 저장 트랜잭션: 보유 카드 행(ID 오름차순) → 사용자 행 → 연결 행 잠금, 연결이 그사이 해제·교체됐으면 409 CODEF_NOT_CONNECTED
→ codef_syncs 1행 → 보유 카드 → 승인 행 → 실적 참고값 → last_synced_at 갱신
```

- **보유카드(관리자 판단 K1·P8·Q1~Q3)**: 새 카드는 `user_cards`에 `holdingSource=CODEF`, `UNMAPPED`, 상품 ID 없음으로 만듭니다. 카드명으로 상품 후보를 정하지 않습니다(결정 20 R3). 같은 카드 판단 정보(카드명 원문, 발급일자 원문)는 `codef_held_cards`에 둡니다. 같은 (카드명, 발급일자) 묶음이 응답에 n장, 해제하지 않은 기존 CODEF 카드에 m장일 때 m=0이면 n장 모두 새로 만들고, n=1·m=1이면 그 기존 카드이며, 그 밖은 같은 카드를 정할 수 없어 건너뜁니다(중복 누적 방지). 발급일자는 둘 다 없음도 같은 값입니다. KB는 보유카드 발급일자를 주지 않으므로(보유카드!H14) KB에서는 사실상 카드명만으로 대조합니다. 카드명이 없거나 앞뒤 공백을 뗀 표시 이름이 1~30자가 아니면 보유 카드로 만들지 않고 이름을 자르거나 지어내지 않습니다(Q2). 해제한 카드는 대조 대상이 아니라서 다시 동기화하면 새 카드가 생깁니다. 응답 `newCandidates`에는 이번에 새로 만든 카드만 담습니다.
- **승인내역(관리자 판단 K2·Q4, 결정 20 R1·R2, 결정 22 R7·R8·#93)**: [`CodefLedgerCandidateBuilder`](../../backend/src/main/java/com/beone/api/codef/sync/CodefLedgerCandidateBuilder.java)로 판정해 받아들인 행(정상, Type1 전액 취소, 짝지은 Type2 취소)만 `codef_approval_rows`에 넣습니다. `ledger_transactions`와 원장 조회는 바꾸지 않습니다. 원장 조회 coverage에는 새 카드가 나오지만 CODEF는 지금처럼 `complete=false`입니다. 이전 기록은 이 사용자의 저장된 승인 행이며, 같은 키·같은 종류는 `skippedDuplicates`, 정상으로 기록된 키가 Type1 전액 취소로 바뀌면 그 행의 종류만 바꾸고 `imported`에 셉니다.
- **불완전 행은 저장하지 않습니다.** 응답 `incompleteRows`에만 담고, 다음 동기화에서 그때의 응답으로 다시 판정합니다(결정 22의 "기간이 바뀐 재조회의 Type2 재표시" 보완은 하지 않았습니다). 보유 카드에 붙지 않은 행(카드 미연결, 건너뛴 카드의 행)도 숨기지 않고 `userCardId=null`로 담습니다. 건너뛴 카드에 붙은 행은 판정이 받아들여도 붙일 보유 카드가 없어 `UNDETERMINED_ROW`로 둡니다.
- **승인번호 HMAC 키(2026-11-18 사용자 직접 결정 P6, 결정 22 1안)**: 프로세스 환경 변수 `BEONE_CODEF_APPROVAL_HMAC_KEY`(Base64로 적은 32바이트)에서 읽고 `application*.yml`에는 두지 않습니다. local·demo에도 고정 기본값이 없습니다. 키가 없으면 승인내역을 조회하지 않고(과금 회피) 저장하지 않으며 `partialFailures`에 `APPROVALS`·`CODEF_UNAVAILABLE`로 알리고 `imported`·`skippedDuplicates`는 `null`입니다. 성공이나 완전 수집으로 보이게 하지 않습니다. 승인번호 원문은 저장·로그·응답에 남기지 않고 지문만 저장하며, DB CHECK가 64자 소문자 16진수만 받습니다. 키가 바뀌면 이전 지문과 비교가 끊깁니다. 키 회전(이전 키 병행 비교)과 운영 키 공급은 별도 승인이며 구현하지 않았습니다. 테스트는 합성 키를 주입합니다.
- **실적(D-16, 관리자 판단 Q5)**: 실적현황 하나당 `performances[]` 항목 하나이고 `label`은 "카드사 조회 실적(참고)", `usedInCalculation`은 항상 `false`입니다. 확인하지 못한 금액·날짜는 `null`이며 혜택·실적현황 목록이 누락·형식 오류면 값을 모르는 항목 하나를 남겨 0건과 구분합니다. `codef_performance_references`에 동기화마다 이력으로 저장하고 `monthly_states`에는 넣지 않습니다.
- **부분 실패**: 실패한 대상은 `partialFailures[]`(`target`, `code`=`CODEF_UNAVAILABLE`, 공개 문구)에 담고, 그 대상의 건수는 `null`로 두어 0건과 구분합니다. 추가 인증 필요 등 연결 API에서 502로 묶은 분류도 같은 코드입니다(관리자 판단 P1).
- **기간(관리자 판단 P9)**: 생략하면 서울 기준 이번 달 1일~오늘, `to`만 없으면 오늘, `from`만 없으면 `to`가 속한 달 1일입니다. `to`가 오늘보다 늦으면 400(`field`=`to`), `from`이 `to`보다 늦으면 400, `from < to.minusMonths(12).plusDays(1)`이면 400(관계 오류는 `field`=`null`)입니다. 짝을 맞추려고 기간을 넓히지 않습니다.
- **재연결 필요(관리자 판단 P2)**: 게이트웨이가 연결 무효를 알리면 연결 상태를 `RECONNECT_REQUIRED`로 바꾸고 409입니다. 지금은 가짜 게이트웨이만 이 결과를 내며 실 CODEF 결과 코드와의 대응은 없습니다. 다음 연결 성공이 `CONNECTED`로 돌립니다.
- **가상 포트폴리오(관리자 판단 Q7)**: 409 `PORTFOLIO_READ_ONLY`입니다. [docs/api.md](../api.md#업무-오류-코드)의 "가상 포트폴리오의 CODEF 쓰기 요청" 규칙에 따른 것이며 계약 syncs 실패 목록에도 반영했습니다.
- **동시성(관리자 판단 Q8)**: CODEF 조회는 트랜잭션 밖입니다. 저장은 보유 카드 행 → 사용자 행 → 연결 행 순으로 잠급니다. 모의 승인(주문 → 보유 카드 → 원장 삽입의 사용자 행 공유 잠금)과 보유 카드 잠금 순서를 맞춰 교착을 피하고, 같은 사용자의 동시 동기화·카드 해제는 차례로 처리합니다. 중복 판단 키의 유니크 제약(`uk_codef_approval_rows_key`)이 마지막 방어선입니다. 카드명 칼럼은 응답 문자열을 그대로 비교하려고 후행 공백까지 구분하는 `utf8mb4_0900_bin`(NO PAD)입니다. `utf8mb4_bin`은 후행 공백을 무시해 쓰지 않았습니다.
- **card 패키지 접근(관리자 판단 P8의 최소 접근)**: [`UserCardService`](../../backend/src/main/java/com/beone/api/card/application/UserCardService.java)의 `lockHeldCardsForCodefSync`(해제하지 않은 보유 카드 잠금, CODEF 카드 ID 반환)와 `registerCodefCard`(CODEF·UNMAPPED 카드 생성), [`UserCardEntity.codef`](../../backend/src/main/java/com/beone/api/card/persistence/UserCardEntity.java)만 더했습니다. `user_cards` 스키마는 바꾸지 않았습니다.
- **가짜 게이트웨이 조회**: `classpath:codef/fake/`의 합성 fixture(실제 카드명·승인번호 형식을 복사하지 않음)를 전송 계층과 같은 해석기로 읽습니다. 기본 시나리오는 카드 3장(그중 1장은 30자를 넘어 건너뜀), 승인 5행(정상, Type2 짝, 부분취소 코드, 건너뛴 카드의 행), 카드 A의 전월 실적 1건입니다. 승인 사용일자는 요청 시작일, 실적 기간은 서울 기준 전월로 채웁니다. 연결 때 쓴 합성 아이디로 동기화 시나리오를 고릅니다: `fake-sync-reconnect`(연결 무효), `fake-sync-unavailable`(세 조회 모두 실패), `fake-sync-partial`(실적만 실패). 서버를 다시 띄워 모르는 connectedId는 기본 시나리오입니다.

```bash
# local 프로필 서버에서 연결한 뒤. 두 키 모두 합성 값을 직접 넣어 실행한다(저장소·로그에 실키를 남기지 않는다)
curl -X POST "http://localhost:8080/api/v1/me/codef/syncs" \
  -H "X-BeONE-Demo-User: MY_CARDS" -H "Content-Type: application/json" \
  -d '{"from":"2026-11-01","to":"2026-11-04"}'
```

테스트: 단위 `CodefSyncPeriodTest`(생략·한쪽 생략·오늘 이후·역순·12개월 경계·월말), `HmacApprovalFingerprinterTest`(Python으로 따로 계산한 값, 원문 미포함, 키 길이), `EnvironmentApprovalFingerprintKeySourceTest`, `CodefHeldCardMatcherTest`(새로·같은 카드·발급일자 구분·정확 비교·n·m 조합·이름 규칙·코드 포인트), `CodefSyncServiceHelpersTest`(실적 펼치기, 부분 실패 대상), `FakeCodefGatewayTest`(fixture 조회·시나리오), 웹 `CodefSyncApiWebTest`(계약 예시 JSON, 부분 실패·null 건수, 미연결 행 `userCardId=null`, 본문 생략, 날짜 형식 400, 기간 400, 실패 응답 전부, 헤더), `CodefSyncProfileOffWebTest`(프로필 밖 404), 통합 `CodefSyncIntegrationTest`(V12, 첫 동기화 저장 내용과 응답, 원장 불변, 승인번호 원문 없음, 재동기화 중복 없음, 해제 카드 재생성, 부분 실패, 전체 실패 502, 재연결 필요, 포트폴리오·미연결·기간 400, 요청 기간 전달, 사용자 분리), `CodefSyncRecheckIntegrationTest`(정상→Type1 갱신·이미 기록·Type1→정상 모순·취소금액 불일치·새 행, 같은 이름 2장 대조), `CodefSyncWithoutHmacKeyIntegrationTest`(HMAC 키 없음).

## 제약과 후속 작업

- 실적 기간·충족 여부·현재이용금액을 BeONE 상태값(`StateValue`, `StateSource.CODEF`)으로 바꾸는 것은 후보 매핑일 뿐 확정하지 않습니다(D-04: [결정 기록](../decisions.md)의 상태 출처 결정). 기관마다 전월·당월 제공 범위가 다르다는 문서 설명이 있습니다(비씨: 시작일자 기준 전월, 신한: 당월, 현대·롯데: 전월·당월).
- 카드명·발급사로 실제 상품(SKU)을 확정하지 않습니다(D-01: [결정 기록](../decisions.md)의 카드 상품 식별 결정, `CANDIDATE` 유지).
- 조회 가능 기간(예: 승인내역 국민·삼성 최근 12개월, 신한·롯데 최근 6개월 등)과 기관별 필수 파라미터는 문서 참고일 뿐 코드화하지 않았습니다.
- 공개 프로필 `prod`에 CODEF 키·토큰 환경 변수(`CODEF_CLIENT_ID`, `CODEF_CLIENT_SECRET`, `BEONE_CODEF_APPROVAL_HMAC_KEY`, `BEONE_CODEF_CONNECTED_ID_KEY`)가 있으면 `PublicProfileGuard`가 기동을 멈춥니다(결정 38-8, #191). 값은 읽지 않고 메시지에 이름만 넣으며, 빈 값으로 선언만 해도 멈춥니다. 키는 비공개 검증 서버에만 둡니다. **공개 전환이 승인되면 이 규칙을 걷어내야 합니다.**
- 취소 표현은 KB의 형태 기술만 있습니다([KB 문서](codef-kb.md#취소-표현-구분)). 실승인금액·승인과 취소의 매칭·원장·실적·한도 반영 규칙과 추가 분류는 만들지 않습니다. 성공 분류가 있어도 `data`를 계산에 쓸 수 있는지는 별도 판단입니다.

## 관련 문서

- [전체 구조](../architecture.md)
- [benefit 안내](benefit.md)
- [KB국민카드 조회 조건 근거](codef-kb.md)
- 공식 SDK 저장소: https://github.com/codef-io/easycodef-java-v2 (근거 커밋 `721f566`)
