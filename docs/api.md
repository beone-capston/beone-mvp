# BeONE API 공통 응답·오류 처리

이 문서는 백엔드 API의 공통 성공·실패 응답 계약과 예외 처리 기준입니다. 구현은 `backend/src/main/java/com/beone/api/common/web/`에 있습니다. 개별 API의 `data` 구조와 필드별 nullable 정책은 각 API를 설계할 때 정하며, 이 문서에서 확정하지 않습니다.

## 응답 계약

### 성공

```json
{
  "data": {
    "id": "example-id"
  }
}
```

- 결과는 `data`에만 담습니다. 공통 성공 코드·메시지, `traceId`, `requestId`는 두지 않습니다.
- 컨트롤러가 `ApiResponse.of(...)`를 직접 반환합니다. 성공 응답을 자동으로 감싸지 않습니다.
- `204 No Content` 응답에는 본문이 없습니다.
- 파일 다운로드·스트리밍·Actuator 응답은 이 형식으로 감싸지 않습니다.

### 실패

```json
{
  "code": "INVALID_REQUEST",
  "message": "요청 값을 확인해 주세요.",
  "errors": [
    { "field": "amountWon", "message": "1 이상이어야 합니다." },
    { "field": null, "message": "시작일은 종료일보다 늦을 수 없습니다." }
  ]
}
```

예상하지 못한 오류:

```json
{
  "code": "INTERNAL_SERVER_ERROR",
  "message": "요청 처리 중 오류가 발생했습니다.",
  "errors": []
}
```

- 실패 본문은 `code`, `message`, `errors` 세 필드만 사용합니다. `details` 등 다른 필드를 추가하지 않습니다.
- `errors`는 항상 배열입니다. 상세가 없으면 `[]`입니다.
- 요청 전체에 대한 오류(객체 수준 검증, 교차 파라미터 검증)는 `"field": null`로 표현하며, 전역 Jackson 설정과 관계없이 `field` 속성을 생략하지 않습니다.
- 여러 검증 오류를 함께 반환합니다. `errors`의 순서는 계약이 아니므로 클라이언트와 테스트는 순서에 의존하지 않습니다.
- 실패 응답의 `Content-Type`은 항상 `application/json`입니다. 클라이언트의 `Accept`가 JSON을 포함하지 않아도 오류 본문은 JSON으로 씁니다.

## 오류 코드와 HTTP 상태

| code | 기본 상태 | message |
| --- | --- | --- |
| `INVALID_REQUEST` | 400 | 요청 값을 확인해 주세요. |
| `NOT_FOUND` | 404 | 요청한 리소스를 찾을 수 없습니다. |
| `METHOD_NOT_ALLOWED` | 405 | 지원하지 않는 요청 방식입니다. |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | 지원하지 않는 콘텐츠 형식입니다. |
| `INTERNAL_SERVER_ERROR` | 500 | 요청 처리 중 오류가 발생했습니다. |

공개 `code` 값은 enum 상수명과 별도로 관리합니다(`CommonApiErrorCode`). 상수 이름을 바꿔도 공개 값은 바뀌지 않아야 합니다.

### 코드와 상태는 일대일이 아니다

따로 열거하지 않은 표준 프레임워크 오류는 원래 HTTP 상태와 헤더를 그대로 유지하고, 본문의 `code`만 다음처럼 포괄 매핑합니다.

- 404·405·415를 제외한 4xx → `INVALID_REQUEST` (예: 406, 409, 413)
- 5xx → `INTERNAL_SERVER_ERROR` (예: 503)

따라서 `INVALID_REQUEST`가 400이 아닌 상태로, `INTERNAL_SERVER_ERROR`가 500이 아닌 상태로 내려올 수 있습니다. 클라이언트는 HTTP 상태와 `code`를 함께 확인합니다.

### 예외별 처리

| 상황 | 대표 예외 | 상태 | code | errors |
| --- | --- | --- | --- | --- |
| 요청 객체의 필드·객체 검증 실패 | `MethodArgumentNotValidException` | 400 | `INVALID_REQUEST` | 필드 오류는 필드 이름, 객체 오류는 `null` |
| 파라미터 검증 실패 | `HandlerMethodValidationException`(입력) | 400 | `INVALID_REQUEST` | 파라미터 이름, 교차 파라미터 오류는 `null` |
| 반환값 검증 실패 | `HandlerMethodValidationException`(반환값) | 500 | `INTERNAL_SERVER_ERROR` | `[]` (내부 상세 비공개) |
| 잘못된 JSON·역직렬화 실패 | `HttpMessageNotReadableException` | 400 | `INVALID_REQUEST` | `[]` |
| 경로·쿼리 값의 타입 오류 | `MethodArgumentTypeMismatchException` | 400 | `INVALID_REQUEST` | `{"field": 이름, "message": "형식이 올바르지 않습니다."}` |
| 필수 쿼리·경로·파트 누락 | `MissingServletRequestParameterException` 등 | 400 | `INVALID_REQUEST` | `{"field": 이름, "message": "필수 값입니다."}` |
| 없는 경로 | `NoResourceFoundException` | 404 | `NOT_FOUND` | `[]` |
| 지원하지 않는 메서드 | `HttpRequestMethodNotSupportedException` | 405 | `METHOD_NOT_ALLOWED` | `[]`, `Allow` 헤더 유지 |
| 지원하지 않는 콘텐츠 형식 | `HttpMediaTypeNotSupportedException` | 415 | `UNSUPPORTED_MEDIA_TYPE` | `[]`, `Accept` 헤더 유지 |
| 그 밖의 표준 프레임워크 오류 | `ResponseStatusException`, `MissingRequestHeaderException` 등 | 원래 상태 | 포괄 매핑 | `[]` |
| 예상하지 못한 예외 | 그 밖의 모든 `Exception` | 500 | `INTERNAL_SERVER_ERROR` | `[]` |

- `IllegalArgumentException`, `ConstraintViolationException`(서비스 계층 메서드 검증·JPA 검증)은 요청 오류로 단정할 수 없으므로 일괄 400으로 바꾸지 않고 500으로 처리합니다. 요청 오류로 보아야 하는 경우는 기능별 처리기에서 명시적으로 다룹니다.
- 응답이 이미 전송(commit)된 뒤 발생한 예외는 응답을 다시 쓰지 않고 안전한 로그만 남깁니다.
- 커스텀 예외 클래스의 `@ResponseStatus`는 공통 처리기가 적용하지 않습니다(500으로 처리). 상태를 지정하려면 아래의 기능별 처리 방식을 사용합니다.

## 기능별 오류 처리

공통 처리기(`GlobalExceptionHandler`)는 `ResponseEntityExceptionHandler`를 확장한 최하위 우선순위(`Ordered.LOWEST_PRECEDENCE`) advice입니다. 기능별 오류는 다음 방식으로 공통 처리기보다 먼저 처리합니다.

1. 기능 패키지에 `ApiErrorCode`를 구현하는 오류 코드 enum을 둡니다. 공개 `code`, 기본 상태, 고정 메시지를 지정합니다.
2. 컨트롤러 안의 `@ExceptionHandler` 또는 `@Order`로 더 높은 우선순위를 지정한 `@RestControllerAdvice`에서 `ApiErrorResponse`를 반환합니다. 우선순위를 지정하지 않은 advice는 공통 처리기와 순서가 정해지지 않으므로 반드시 `@Order`를 붙입니다.

```java
enum CardErrorCode implements ApiErrorCode {

	CARD_NOT_REGISTERED("CARD_NOT_REGISTERED", HttpStatus.NOT_FOUND, "등록되지 않은 카드입니다.");

	// code·status·message 필드와 접근자는 CommonApiErrorCode와 같은 형태로 둔다.
}

@RestControllerAdvice(assignableTypes = CardController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class CardExceptionHandler {

	@ExceptionHandler(CardNotRegisteredException.class)
	ResponseEntity<ApiErrorResponse> handleCardNotRegistered() {
		CardErrorCode errorCode = CardErrorCode.CARD_NOT_REGISTERED;
		return ResponseEntity.status(errorCode.status()).body(ApiErrorResponse.of(errorCode));
	}
}
```

- `ApiErrorCode`는 HTTP 상태를 포함하므로 컨트롤러·예외 처리기 같은 웹 경계에서만 사용합니다. 계산·도메인 코드는 이 타입에 의존하지 않습니다.
- 공통 `ApiException`, 범용 예외 번역기, 오류 코드 레지스트리는 두지 않습니다.

## 업무 API 공통 원칙

업무 API의 범위와 공통 원칙은 [D-09·D-14](decisions.md#d-09d-14-확정-정책)로 확정했습니다(2026-11-01). 개별 API 명세는 아직 없으며, 아래 원칙을 모든 업무 API 명세에 적용합니다.

- 경로는 `/api/v1` 아래에 두고, 사용자 기준 자원은 `/api/v1/me/...`로 씁니다. 사용자 ID를 경로·본문에 넣지 않고 현재 사용자는 서버 한 곳에서 정합니다(D-09).
- 성공·실패 형식은 이 문서의 응답 계약을 따릅니다. 계산 상태는 `data` 안에 담습니다.
- 금액은 원 단위 정수이고 필드 이름은 `...Won`으로 끝냅니다. 모르는 값은 `null`과 상태값을 함께 주고 0으로 대신하지 않습니다.
- 출처·기준 기간·갱신 시각은 데이터 묶음 단위로 붙입니다.
- 추천·승인 응답에는 규칙 버전, 근거 출처, 선택 이유, 동률 기준을 담습니다.
- 모의 승인·취소는 `Idempotency-Key` 헤더를 받습니다. 승인은 주문 ID로 받아 서버가 다시 계산하고, 사용자가 본 추천과 다르면 차이와 이유를 담습니다.
- 서버는 Android 기기 인증 결과를 신뢰하지 않습니다(D-08).
- 카드번호·실물 카드 비밀번호·CVV·유효기간 필드를 두지 않습니다. CODEF 연결용 카드사 홈페이지 로그인 아이디·비밀번호만 요청 본문으로 받고, 저장·로그·응답·오류 메시지에 남기지 않습니다.

### 예약한 오류 코드 자리

인증 필요(401)와 권한 없음(403) 자리를 예약합니다(D-09). MVP는 로그인이 없어 이 응답을 보내지 않으며, 공개 `code` 값은 업무 API 명세에서 정합니다. 아직 `CommonApiErrorCode`에 추가하지 않았습니다.

## 계산 결과 상태와 API 오류의 구분

한도 소진, 전월 실적 미충족, 규칙 미확인, 추천 확정 불가는 요청이 정상적으로 처리된 결과의 상태입니다. 이 상태를 일괄적으로 예외나 HTTP 오류로 바꾸지 않고, 계산 결과의 `data` 안에서 구분해 표현합니다. 계산 모델은 이 상태를 `ApplicationStatus`(예: `LIMIT_EXHAUSTED`, `NOT_APPLICABLE_PERFORMANCE`)와 `RecommendationOutcome.NotDetermined`로 표현합니다([benefit 안내](domains/benefit.md)). 이를 API `data`로 옮기는 구조는 각 API 설계에서 정하며, 확인되지 않은 상태를 0원으로 바꾸지 않습니다. HTTP 오류는 요청 자체를 처리할 수 없는 경우(검증 실패, 없는 리소스, 서버 오류 등)에만 사용합니다.

## 검증 메시지

- DTO의 Bean Validation 메시지는 `message` 속성에 한글로 명시합니다. 예: `@Min(value = 1, message = "1 이상이어야 합니다.")`
- 메시지에 `${validatedValue}`나 입력 원문을 넣지 않습니다. 응답 `errors`에 입력값이 그대로 노출됩니다.
- 서버 로케일 전체를 고정하지 않습니다. 메시지를 명시하지 않은 제약은 서버 기본 로케일의 기본 메시지가 나갈 수 있으므로 메시지를 생략하지 않습니다.
- 타입 오류와 필수값 누락은 공통 처리기가 고정 문구(`형식이 올바르지 않습니다.`, `필수 값입니다.`)를 사용합니다. 요청 객체 바인딩의 타입 오류도 입력 원문이 담긴 기본 메시지 대신 같은 고정 문구로 바꿉니다.

## 로깅과 보안

응답과 로그에 다음을 그대로 남기지 않습니다.

- 검증 실패 값(`rejectedValue`), 예외 메시지, 원인 예외 메시지, 전체 스택
- 요청 본문·헤더·파라미터 값
- 비밀번호·토큰·실계정 CODEF 응답 전문

공통 처리기의 로그 형식은 다음과 같습니다.

- 4xx: `DEBUG` 수준으로 오류 코드, HTTP 상태, 예외 클래스 이름
- 5xx: `ERROR` 수준으로 위 정보에 더해 원인 예외 클래스 이름 목록(메시지 제외)과 첫 `com.beone` 발생 위치 한 줄(`Class#method:line`)
- 발생 위치는 원인 체인의 가장 안쪽 예외부터 스택에서 처음 만나는 `com.beone` 프레임입니다. 해당 프레임이 없으면 `origin=none`으로 남기고 위치를 만들어 내지 않습니다. 프레임워크가 직접 던진 예외는 호출한 `com.beone` 코드가 표시되거나 `none`일 수 있습니다.
- 원인 체인은 순환을 끊고 최대 깊이를 제한해 로그가 무한히 반복되지 않습니다.
- 이 진단 정보는 서버 로그 전용이며 API 응답에는 넣지 않습니다.

상위 클래스의 기본 로그 중 예외 원문이 남는 두 경로를 재정의했습니다.

- 405 처리: `PageNotFound` 로거의 `WARN`(요청 메서드가 담긴 예외 메시지)을 남기지 않습니다.
- 이미 전송된 응답: `Response already committed. Ignoring: <예외>` `WARN`을 남기지 않습니다.

### 프레임워크 로그 수준 주의

공통 처리기는 직접 작성한 로그만 통제합니다. 라이브러리 로그까지 모두 안전하다고 보장하지 않으므로 공유 환경(개발 서버, 시연 환경, 이슈·PR에 붙이는 로그)에서는 다음을 지킵니다.

- `org.springframework.web`, `org.springframework.web.servlet` 로거를 `DEBUG` 이하로 낮추지 않습니다. 예외 처리 결과를 남기는 `DEBUG` 로그(`Resolved [예외]`)에 예외 메시지가 포함됩니다.
- `spring.mvc.log-resolved-exception`을 켜지 않습니다. 처리된 예외의 메시지를 `WARN`으로 남깁니다.
- `DispatcherServlet` 바깥(서블릿 필터, 내장 서버)에서 발생한 예외는 이 처리기가 다루지 않습니다. 필터를 추가할 때는 해당 필터의 예외·로그 처리를 따로 확인합니다.
- Hibernate·JDBC·HTTP 클라이언트의 SQL·요청 로그는 값이 포함될 수 있으므로 공유 환경에서 켜지 않습니다.

## 테스트

- 응답 레코드 직렬화, 오류 코드, 진단 정보 생성은 단위 테스트(`./gradlew test`)로 확인합니다.
- 실제 디스패치·직렬화·예외 처리 경계는 `@WebSliceTest`(MockMvc 웹 슬라이스)로 확인합니다. 실행 명령은 `./gradlew webTest`이며 DB·Docker가 필요 없습니다.
- 웹 테스트 작성 규칙은 [코드·테스트 컨벤션](conventions.md)의 **테스트 > 백엔드** 항목을 따릅니다.

## 구현 범위

구현됨:

- `ApiResponse<T>`, `ApiErrorResponse`, `FieldViolation`, `ApiErrorCode`, `CommonApiErrorCode`, `GlobalExceptionHandler`
- 위 표의 표준 MVC 예외 처리, 포괄 상태 매핑, 안전한 500 처리와 로그
- `@WebSliceTest`와 `webTest` Gradle 태스크

미구현·범위 밖:

- 실제 업무 API와 DTO, 개별 API의 `data` 구조와 nullable 정책(범위·공통 원칙만 D-14로 확정)
- 기능별 오류 코드 enum과 기능별 처리기(각 기능 구현 시 추가)
- 인증·인가 오류(401·403)의 공개 `code`와 Spring Security 연동(자리만 예약, D-09)
- DB·CODEF·AI 등 외부 연동 예외의 세부 분류(현재는 예상하지 못한 예외로 500 처리)
- 서블릿 필터 단계 예외의 공통 응답
