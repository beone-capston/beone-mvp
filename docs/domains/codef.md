# codef 도메인 안내

## 목적과 범위

`codef`는 CODEF 카드 API 응답 본문을 BeONE이 쓸 수 있는 비민감 값으로 해석하는 **응답 해석 경계**입니다(#41). CODEF(금융기관 데이터를 대신 조회해 주는 외부 API)의 요청 전송, OAuth 토큰(접근 권한을 토큰으로 위임하는 인증 방식의 발급값), 암호화, 계정 연결(connectedId: CODEF가 연결된 금융기관 계정을 가리키는 식별값)과는 분리되어 있으며, 이 패키지는 응답 문자열을 받아 결과 코드 분류와 `data` 항목만 돌려줍니다. 그 앞단의 전송 단계 해석(#43)은 HTTP 상태 코드와 응답 본문을 받아 상태를 구분하고 본문을 URL 디코딩합니다. 실제 HTTP 호출은 하지 않습니다.

구현된 범위는 승인내역·보유카드·실적조회 응답의 해석, 공통 결과 코드의 분류, 빈 값·금액·코드 값의 보수적 해석입니다. HTTP 호출, 요청 DTO(요청 값을 담아 계층 사이로 옮기는 객체), 토큰 발급·갱신, 추가 인증 2차 요청, DB 저장, 엔드포인트, BeONE 상태값(`StateValue`: 값과 출처·기준 기간을 함께 담는 `benefit` 도메인 타입)으로의 변환은 구현하지 않았습니다. 이 패키지의 테스트는 합성 응답만 쓰며 실제 응답 검증이 아닙니다.

CODEF 결과를 쓸 수 없는 경우(오류 분류, 미분류, 형식 오류, 값 없음)에도 수동 입력 경로는 유지해야 합니다(CLAUDE.md 제품 경계). 이 패키지는 그 판단 재료만 제공하며 수동 입력 API는 없습니다.

## 근거 자료

- 출처: Aside의 브라우저로 공식 공개 문서를 렌더링해 수집한 본문 사본(수집 2026-10-31T12:00:47Z). 구현 작업자가 직접 브라우저로 검증한 것이 아닙니다.
- 대상 문서와 표시 갱신일: 승인내역(`/products/card/common/p/approval`, 2026-10-21), 보유카드(`/products/card/common/p/account`, 2026-10-19), 실적조회(`/products/card/common/p/result-check`, 2026-10-19), 공통 result/data 구조(`/common-guide/rest-api`), 오류코드 검색 결과 "만료"·"통신"·"토큰"(`/common-guide/error-code`).
- 성공 코드 출처: Aside의 브라우저로 공식 오류코드 페이지(https://developer.codef.io/common-guide/error-code)에서 CF-00000을 검색해 확인한 행(관찰 2026-11-01T07:10:00Z). 행 내용은 `CF-00000` 설명 "성공"입니다. 위 사본과 별개의 별첨이며, 사본에 성공 코드가 없던 것은 검색 범위("만료"·"통신"·"토큰")에 따른 수집 누락이었습니다. 공식 문서에 성공 코드가 없었다는 뜻이 아닙니다.
- 사본의 JSON 구조도는 공식 예시 하나에서 값만 뺀 것입니다. 완전한 스키마나 실제 응답 검증이 아닙니다.
- 전송 단계 근거: 공식 SDK `codef-io/easycodef-java-v2`(MIT) 커밋 `721f5661a52da3bcee8f24c86c04557e1e073e9a`의 `ResponseHandler`·`EasyCodefConnector`·`CodefResponseCode`. 공식 SDK 구현에서 확인한 동작이며, 공식 명세 문서가 아닙니다. SDK 동작이 서버 계약 전체를 보장하지는 않습니다(아래 "전송 단계" 절).
- 미확인: HTTP 헤더 중 Content-Type, 시간 초과·재시도 정책, 서버 쪽 인코딩 계약(SDK의 디코딩 동작만 확인), OAuth 발급·갱신의 서버 계약, 기관별 필수조건 시트 본문, 승인 취소·부분취소 상세 탭, 오류 전체 목록(587건 중 검색 결과만 수집)과 상세 설명, 별도 명세 릴리스 버전.

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
└── CodefFields              필드 읽기 공통 규칙(패키지 내부)
```

`benefit` 도메인과 `common.web`에 의존하지 않습니다. JSON은 Spring Boot에 포함된 Jackson(`tools.jackson`)으로 읽습니다.

## 주요 클래스와 처리 흐름

[CodefCardResponseParser](../../backend/src/main/java/com/beone/api/codef/CodefCardResponseParser.java)의 `parseApprovals`·`parseHeldCards`·`parseCardPerformances`는 응답 본문을 받아 [CodefResponse](../../backend/src/main/java/com/beone/api/codef/CodefResponse.java)를 돌려줍니다.

1. 본문이 JSON 객체가 아니면 예외를 던지지 않고 결과는 미분류, `data`는 형식 오류입니다. 파서 예외 문구에 본문 일부가 들어갈 수 있어 전달하지 않습니다.
2. `result.code`·`transactionId`만 보관하고 [CodefResultCategory](../../backend/src/main/java/com/beone/api/codef/CodefResultCategory.java)로 분류합니다. `message`·`extraMessage`는 기관 문구가 그대로 담길 수 있어 보관하지 않습니다.
3. `data`는 [CodefList](../../backend/src/main/java/com/beone/api/codef/CodefList.java)로 읽습니다. 객체 → 1건, 배열 → N건(빈 배열은 0건), 키 누락 → 값 없음(`Missing`), null·원시값·객체가 아닌 배열 원소 → 형식 오류(`Malformed`). 실적조회의 중첩 목록도 같은 규칙이며, 중첩 목록의 빈 문자열은 "미제공시 빈칸"으로 보고 값 없음입니다(최상위 `data`의 빈 문자열은 형식 오류).
4. 결과 코드 분류와 `data` 해석은 독립입니다. 성공 분류여도 `data`의 누락·형식 오류·빈 값·해석 못 한 금액·목록 밖 코드 값은 그대로 남고, 0원·0건·검증 완료로 바뀌지 않습니다. 두 값을 합쳐 "사용 가능"을 판정하는 메서드는 없으며, `data` 항목을 계산에 쓸지는 호출자가 결과 코드와 항목 값을 따로 보고 판단합니다. 예를 들어 추가 인증 응답의 `data`도 형식상 객체 1건으로 읽힙니다.

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

- 확인(SDK 구현): 상품 요청은 POST, `Authorization: Bearer <access token>`, 본문은 UTF-8 JSON 문자열입니다. 토큰은 `https://oauth.codef.io/oauth/token?grant_type=client_credentials&scope=read`에 `Authorization: Basic …`으로 POST하며, 토큰 응답은 URL 디코딩하지 않습니다. 상품 응답은 상태와 관계없이 먼저 디코딩한 뒤 200이면 해석하고, 401은 인증 오류, 그 밖은 서버 오류로 나눕니다. `data`는 객체를 먼저 시도하고 아니면 배열로 읽습니다.
- SDK와 다르게 둔 것: SDK는 401·그 밖의 상태에서 디코딩한 본문을 예외에 싣지만, 여기서는 본문을 읽지도 보관하지도 않습니다. SDK는 `result`가 없으면 파싱 오류로 보지만, 여기서는 기존 파서 규칙(결과 미분류, `data`는 별도 해석)을 따릅니다.
- 미확인: Content-Type(SDK가 `StringEntity(본문, UTF-8)`만 쓰고 명시하지 않음), 연결·응답 시간 초과와 재시도(SDK는 기본 클라이언트 설정만 사용), `CF-12872`의 의미(SDK 상수 이름 `isAddAuthExceedResponse`만 있고 공식 설명 행이 없어 분류하지 않음), 기관별 필수조건, 취소 상세, 오류 전체 목록.
- 만들지 않은 것: HTTP 클라이언트, 토큰 발급·캐시·갱신, 요청 본문·요청 DTO, RSA 암호화, connectedId, 추가 인증 2차 요청, 설정 키·비밀값·DB. 요청 쪽에는 수집 금지 입력(카드번호·비밀번호·인증서)이 섞여 있고, 키 없이는 실제로 검증할 수 없습니다.

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

## 제약과 후속 작업

- 실적 기간·충족 여부·현재이용금액을 BeONE 상태값(`StateValue`, `StateSource.CODEF`)으로 바꾸는 것은 후보 매핑일 뿐 확정하지 않습니다(D-04: [결정 기록](../decisions.md)의 상태 출처 결정). 기관마다 전월·당월 제공 범위가 다르다는 문서 설명이 있습니다(비씨: 시작일자 기준 전월, 신한: 당월, 현대·롯데: 전월·당월).
- 카드명·발급사로 실제 상품(SKU)을 확정하지 않습니다(D-01: [결정 기록](../decisions.md)의 카드 상품 식별 결정, `CANDIDATE` 유지).
- 조회 가능 기간(예: 승인내역 국민·삼성 최근 12개월, 신한·롯데 최근 6개월 등)과 기관별 필수 파라미터는 문서 참고일 뿐 코드화하지 않았습니다.
- 승인 취소·부분취소 상세와 오류 전체 목록을 확인하기 전에는 취소 금액 반영 규칙과 추가 분류를 만들지 않습니다. 성공 분류가 있어도 `data`를 계산에 쓸 수 있는지는 별도 판단입니다.

## 관련 문서

- [전체 구조](../architecture.md)
- [benefit 안내](benefit.md)
- 공식 SDK 저장소: https://github.com/codef-io/easycodef-java-v2 (근거 커밋 `721f566`)
