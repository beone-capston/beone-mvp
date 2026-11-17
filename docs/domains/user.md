# user 도메인 안내

## 목적과 범위

`user`는 업무 API가 "지금 요청한 사용자"를 정하는 **현재 사용자 결정 지점**과 시연 사용자 목록을 담당합니다(#100). MVP에는 가입·로그인이 없고, 시드(Flyway 마이그레이션으로 미리 넣은 데이터)로 넣은 고정 시연 사용자 2명(내 카드·가상 포트폴리오)만 있습니다([D-09](../decisions.md#d-09d-14-확정-정책)).

구현된 범위:

- 시연 사용자 테이블 `users`와 시드 2명(`V2__demo_users.sql`)
- `X-BeONE-Demo-User` 헤더로 현재 사용자를 정하는 `CurrentUserResolver`
- 헤더가 없거나 잘못됐을 때 400 `INVALID_REQUEST`를 돌려주는 기능별 예외 처리기
- `GET /api/v1/demo-users` 시연 사용자 목록 API([명세](../api/cards.md#get-apiv1demo-users--시연-사용자-목록-조회))
- `local`·`demo` 프로필의 CORS 허용 목록(`common.web.LocalDemoCorsConfiguration`, D-23 11항)
- `GET /api/v1/me`는 보유 카드 수가 필요해 `card` 패키지(`CurrentUserController`, #108)에 있고, 이 패키지의 `CurrentUser`·`DemoUserService`를 읽기만 합니다([card 안내](card.md#보유-카드)).

구현하지 않은 범위:

- 회원 가입·로그인·세션([D-24](../decisions.md#d-24-확정-정책), 구현 전), `UNAUTHENTICATED`·`FORBIDDEN` 오류 코드(자리만 예약)
- `local`·`demo` 밖의 현재 사용자 결정. 그 환경에서는 아래 부품이 등록되지 않습니다.

## 핵심 용어

- **시연 사용자(`DemoUser`)**: `MY_CARDS`(내 카드)·`VIRTUAL_PORTFOLIO`(가상 포트폴리오). 이름이 곧 헤더 값이자 응답의 `demoUser` 값입니다.
- **현재 사용자(`CurrentUser`)**: 요청 하나의 사용자. 저장소의 `userId`와 `demoUser`를 담습니다. 사용자 데이터는 모두 `user_id`로 나눠 저장합니다(D-09).
- **포트폴리오 종류(`PortfolioType`)**: `REAL`(실제 보유 카드, 수동·CODEF)·`SYNTHETIC`(시드 데이터).
- **읽기 전용(`readOnly`)**: 가상 포트폴리오는 보유 카드·CODEF 쓰기를 할 수 없습니다. 월간 상태·택1 선택은 바꿀 수 있습니다([D-19](../decisions.md#d-19-확정-정책)). 쓰기 거부(`PORTFOLIO_READ_ONLY`)는 각 기능이 구현합니다.
- **프로필**: Spring이 환경별로 설정·부품을 켜고 끄는 이름. 시연 전환은 `local`·`demo`에서만 켭니다.

## 패키지 구조

[D-23](../decisions.md#d-23-확정-정책) 13항의 `web`/`application`/`persistence` 구조입니다.

```text
com.beone.api.user
├── web/
│   ├── CurrentUserResolver            현재 사용자 결정 지점(HandlerMethodArgumentResolver), local·demo
│   ├── UserWebConfiguration           위 해석기를 Spring MVC에 등록, local·demo
│   ├── DemoUserHeader                 헤더 이름과 값 판정(순수 함수)
│   ├── InvalidDemoUserHeaderException 헤더 오류(입력값을 담지 않음)
│   ├── DemoUserExceptionHandler       헤더 오류 → 400 INVALID_REQUEST (@Order 최우선)
│   ├── DemoUserController             GET /api/v1/demo-users, local·demo
│   └── DemoUsersResponse              응답 data
├── application/
│   ├── DemoUserService                시드 조회, DemoUser → CurrentUser
│   ├── CurrentUser·DemoUserProfile    결과 타입
│   └── DemoUser·PortfolioType         열거형
└── persistence/
    ├── UserEntity                     users 테이블 한 행(읽기 전용)
    └── UserRepository                 Spring Data JPA 저장소
```

소스: [web](../../backend/src/main/java/com/beone/api/user/web/), [application](../../backend/src/main/java/com/beone/api/user/application/), [persistence](../../backend/src/main/java/com/beone/api/user/persistence/), [마이그레이션](../../backend/src/main/resources/db/migration/V2__demo_users.sql), [CORS 설정](../../backend/src/main/java/com/beone/api/common/web/LocalDemoCorsConfiguration.java).

## 처리 흐름

```text
요청 (X-BeONE-Demo-User: MY_CARDS)
→ CurrentUserResolver: 컨트롤러 인자에 CurrentUser가 있을 때만 동작
   → DemoUserHeader.parse: 헤더가 정확히 하나이고 값이 MY_CARDS·VIRTUAL_PORTFOLIO와 정확히 같은지 확인
      (대소문자·공백을 고치지 않음. 아니면 InvalidDemoUserHeaderException)
   → DemoUserService.currentUserOf: users 테이블에서 userId 조회 (시드가 없으면 500)
→ 컨트롤러는 CurrentUser만 받아 서비스에 넘김

InvalidDemoUserHeaderException
→ DemoUserExceptionHandler: 400 {"code":"INVALID_REQUEST","message":"요청 값을 확인해 주세요.","errors":[]}
   (헤더 값은 응답·로그에 남기지 않음. 로그는 DEBUG로 코드·상태·예외 클래스 이름만)
```

`/api/v1/demo-users`와 상품 조회는 사용자 자원이 아니므로 헤더를 읽지 않습니다.

## 사용법: 새 `/me/...` 컨트롤러

컨트롤러 메서드에 `CurrentUser` 인자를 두면 됩니다. 헤더나 사용자 ID를 직접 읽지 않습니다.

```java
@RestController
@Profile({ "local", "demo" })
@RequestMapping("/api/v1/me/cards")
class UserCardController {

	@GetMapping
	ApiResponse<UserCardsResponse> list(CurrentUser currentUser) {
		return ApiResponse.of(userCardService.list(currentUser));
	}
}
```

- `CurrentUserResolver`는 `local`·`demo`에서만 등록되므로 `CurrentUser`를 받는 컨트롤러도 같은 프로필로 제한합니다. 그 밖의 환경에서 쓰는 방식은 인증 API(D-24) 범위입니다.
- 기능 오류는 이 패키지의 `DemoUserExceptionHandler`처럼 기능 패키지에 예외와 `@Order`를 붙인 처리기를 둡니다([기능별 오류 처리](../api.md#기능별-오류-처리)). 공통 업무 예외는 두지 않습니다.

## 프로필과 CORS

- `local`·`demo` 프로필에서만 `DemoUserController`, `CurrentUserResolver`, `UserWebConfiguration`, `LocalDemoCorsConfiguration`이 등록됩니다. 그 밖에서는 `/api/v1/demo-users`가 404 `NOT_FOUND`입니다(명세의 "다른 환경에서는 404").
- 기본 프로필은 정하지 않았습니다. 로컬에서 두 기능을 쓰려면 실행할 때 `SPRING_PROFILES_ACTIVE=local`을 줍니다.
- CORS(브라우저가 다른 주소의 API를 부를 때 서버가 허용 목록을 알려 주는 규칙): `/api/**`에 origin `http://localhost:5173`, 메서드 `GET, POST, PUT, PATCH, DELETE, OPTIONS`, 헤더 `Content-Type, X-BeONE-Demo-User, Idempotency-Key`, 자격 증명 없음. 허용 밖 origin·메서드·헤더의 사전 요청은 Spring 기본 처리로 403이며, 이 응답 본문은 공통 실패 형식이 아닙니다(브라우저가 본문을 읽지 않는 경로).

## 테스트

- `test`: [`DemoUserHeaderTest`](../../backend/src/test/java/com/beone/api/user/web/DemoUserHeaderTest.java) 헤더 값 판정
- `webTest`: [`DemoUserApiWebTest`](../../backend/src/test/java/com/beone/api/user/web/DemoUserApiWebTest.java)(local: 목록 응답, 현재 사용자 주입, 헤더 없음·잘못된 값·중복 400, CORS 허용·거부), [`DemoUserProfileOffWebTest`](../../backend/src/test/java/com/beone/api/user/web/DemoUserProfileOffWebTest.java)(프로필 없음: 404, 부품 미등록)
- `integrationTest`: [`DemoUserIntegrationTest`](../../backend/src/test/java/com/beone/api/user/DemoUserIntegrationTest.java)(V2 적용과 `ddl-auto: validate`, 시드 2명, 저장소·서비스 조회, 실제 HTTP 응답)

## 제약

- 시드 값은 [명세](../api/cards.md)의 응답 예시에 있는 값만 씁니다. `user_id`는 자동 증가 값이며 다른 테이블의 시드는 `demo_user`로 찾아 참조합니다.
- `users` 테이블과 회원(D-24)의 관계는 미결입니다. 회원 기능을 붙일 때 이 테이블을 어떻게 이어 쓸지는 그때 정합니다.
