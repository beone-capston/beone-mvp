# user 도메인 안내

## 목적과 범위

`user`는 업무 API가 "지금 요청한 사용자"를 정하는 **현재 사용자 결정 지점**과 시연 사용자 목록, 회원의 업무 장부를 담당합니다(#100, #157). `users` 테이블 한 행이 업무 데이터의 주인(**장부**)입니다. 행은 두 종류입니다.

- 시드(Flyway 마이그레이션으로 미리 넣은 데이터)로 넣은 고정 시연 사용자 2명(내 카드·가상 포트폴리오). `local`·`demo` 프로필에서 `X-BeONE-Demo-User` 헤더로 고릅니다([D-09](../decisions.md#d-09d-14-확정-정책)).
- 회원 1명당 '내 카드' 장부 1개. 로컬 회원 프로필 `member`에서 회원 세션으로 고릅니다([결정 37](../decisions.md#결정-37-회원과-업무-장부-연결)).

구현된 범위:

- 시연 사용자 테이블 `users`와 시드 2명(`V2__demo_users.sql`)
- `X-BeONE-Demo-User` 헤더로 현재 사용자를 정하는 `CurrentUserResolver`
- 헤더가 없거나 잘못됐을 때 400 `INVALID_REQUEST`를 돌려주는 기능별 예외 처리기
- `GET /api/v1/demo-users` 시연 사용자 목록 API([명세](../api/cards.md#get-apiv1demo-users--시연-사용자-목록-조회))
- `local`·`demo` 프로필의 CORS 허용 목록(`common.web.LocalDemoCorsConfiguration`, D-23 11항)
- `GET /api/v1/me`는 보유 카드 수가 필요해 `card` 패키지(`CurrentUserController`, #108)에 있고, 이 패키지의 `CurrentUser`·`UserProfileService`를 읽기만 합니다([card 안내](card.md#보유-카드)).
- 회원 장부(#157): `users.member_id`(Flyway `V14`), 이메일 인증 완료 이벤트로 장부를 만드는 `MemberUserService`, `member` 프로필의 현재 사용자 결정 지점 `MemberCurrentUserResolver`, 프로필 동시 사용을 막는 `CurrentUserModeGuard`

구현하지 않은 범위:

- 회원 가입·로그인·세션 자체는 [auth](auth.md) 패키지가 맡습니다. `FORBIDDEN` 오류 코드는 자리만 예약했습니다.
- 회원용 가상 포트폴리오(결정 37 D1b, 결정 38-3으로 후순위), 회원 탈퇴 때 장부 처리(D10), 공개 프로필 구성(O1). `local`·`demo`·`member` 밖에서는 현재 사용자 결정 부품이 등록되지 않습니다.

## 핵심 용어

- **시연 사용자(`DemoUser`)**: `MY_CARDS`(내 카드)·`VIRTUAL_PORTFOLIO`(가상 포트폴리오). 이름이 곧 헤더 값이자 응답의 `demoUser` 값입니다.
- **현재 사용자(`CurrentUser`)**: 요청 하나의 사용자(장부). 저장소의 `userId`, `demoUser`(회원 장부면 `null`), `portfolioType`, `readOnly`를 담습니다. 사용자 데이터는 모두 `user_id`로 나눠 저장합니다(D-09). 쓰기 가능 여부는 `readOnly`로 판단하고 `demoUser`로 판단하지 않습니다.
- **회원 장부**: 회원 1명의 '내 카드'(REAL, 쓰기 가능) `users` 행. `member_id`만 있고 `demo_user`는 `NULL`입니다. 이메일 인증을 마칠 때 같은 트랜잭션에서 만듭니다(결정 37 D1a).
- **포트폴리오 종류(`PortfolioType`)**: `REAL`(실제 보유 카드, 수동·CODEF)·`SYNTHETIC`(시드 데이터).
- **읽기 전용(`readOnly`)**: 가상 포트폴리오는 보유 카드·CODEF 쓰기를 할 수 없습니다. 월간 상태·택1 선택은 바꿀 수 있습니다([D-19](../decisions.md#d-19-확정-정책)). 쓰기 거부(`PORTFOLIO_READ_ONLY`)는 각 기능이 구현합니다.
- **프로필**: Spring이 환경별로 설정·부품을 켜고 끄는 이름. 시연 전환은 `local`·`demo`에서, 회원 세션 방식은 로컬 개발·테스트용 `member`에서 켭니다. 둘을 함께 켜면 기동에 실패합니다.

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
│   ├── DemoUsersResponse              응답 data
│   ├── MemberCurrentUserResolver      member의 현재 사용자 결정 지점(회원 ID → 회원 장부)
│   ├── MemberUserWebConfiguration     위 해석기를 Spring MVC에 등록, member
│   └── CurrentUserModeGuard           member와 local·demo 동시 사용 시 기동 실패(모든 프로필)
├── application/
│   ├── DemoUserService                시드 조회, DemoUser → CurrentUser
│   ├── MemberUserService              회원 장부 조회, 이메일 인증 이벤트로 장부 생성
│   ├── UserProfileService             CurrentUser → 표시 정보(UserProfile), /me용
│   ├── CurrentUser·DemoUserProfile·UserProfile  결과 타입
│   └── DemoUser·PortfolioType         열거형
└── persistence/
    ├── UserEntity                     users 테이블 한 행(시연 행은 읽기 전용, 회원 장부는 memberCards로 생성)
    └── UserRepository                 Spring Data JPA 저장소
```

소스: [web](../../backend/src/main/java/com/beone/api/user/web/), [application](../../backend/src/main/java/com/beone/api/user/application/), [persistence](../../backend/src/main/java/com/beone/api/user/persistence/), [마이그레이션](../../backend/src/main/resources/db/migration/V2__demo_users.sql), [회원 장부 마이그레이션](../../backend/src/main/resources/db/migration/V14__member_users.sql), [CORS 설정](../../backend/src/main/java/com/beone/api/common/web/LocalDemoCorsConfiguration.java).

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

### member 프로필 (회원 세션)

```text
요청 (Cookie: __Host-BEONE_SESSION=..., 쓰기면 X-CSRF-TOKEN)
→ auth.web.MemberApiInterceptor (/api/v1/me, /api/v1/me/**, 컨트롤러가 있는 경로만)
   → AuthSessionService.checkMemberRequest: 회원 세션 아니면 401, 쓰기에 CSRF 틀리면 403, 통과하면 미사용 만료 7일 연장
   → 쿠키 재발급, 회원 ID를 요청 속성(AuthenticatedMember)에 담음
→ MemberCurrentUserResolver: 회원 ID → MemberUserService.currentUserOf → CurrentUser(demoUser=null, REAL, readOnly=false)
   (X-BeONE-Demo-User는 읽지 않음. 회원 ID나 장부가 없으면 구성 오류로 500)
→ 컨트롤러는 local·demo와 똑같이 CurrentUser만 받음
```

회원 장부 생성: `MemberRegistrationService.verifyEmail`이 같은 트랜잭션에서 `MemberEmailVerifiedEvent`를 발행하고, `MemberUserService.onEmailVerified`가 장부가 없을 때만 만듭니다. 장부를 만들지 못하면 인증도 함께 취소됩니다. `auth`는 `user`를 알지 않고 이벤트만 발행합니다(`user` → `auth` 한 방향 의존). 이미 인증된 회원은 `V14`가 백필했습니다. `MemberUserService`는 프로필 지정이 없고 이벤트를 발행하는 `MemberRegistrationService`는 `local`·`demo`·`member`에 있으므로, `local`·`demo`에서도 이메일 인증을 마치면 회원 장부가 생깁니다. 시연 2행과는 별개 행이고 `/api/v1/demo-users`(`findByDemoUserIsNotNull`)와 시연 헤더 해석(`findByDemoUser`)에는 닿지 않습니다. 같은 로컬 DB를 `member` 프로필로 다시 띄우면 그 회원의 장부로 쓰입니다.

## 사용법: 새 `/me/...` 컨트롤러

컨트롤러 메서드에 `CurrentUser` 인자를 두면 됩니다. 헤더나 사용자 ID를 직접 읽지 않습니다.

```java
@RestController
@Profile({ "local", "demo" })
@RequestMapping("/api/v1/me/cards")
class UserCardController {

	@GetMapping
	ApiResponse<UserCardResponses.ItemList> list(CurrentUser currentUser) {
		return ApiResponse.of(UserCardResponses.ItemList.from(userCardService.list(currentUser)));
	}
}
```

- `CurrentUserResolver`는 `local`·`demo`, `MemberCurrentUserResolver`는 `member`에서만 등록됩니다. `CurrentUser`를 받는 컨트롤러는 이 중 열 프로필로 제한합니다. `member`에 여는 업무 API는 지금 `/api/v1/me`, 보유 카드(#157), 월간 상태·결제 전 설정(G3a #159), 주문·예정 소비·추천(G3b #160), 원장 조회(#165), 모의 결제(G3c #161, 결정 38)입니다(결정 37).
- 기능 오류는 이 패키지의 `DemoUserExceptionHandler`처럼 기능 패키지에 예외와 `@Order`를 붙인 처리기를 둡니다([기능별 오류 처리](../api.md#기능별-오류-처리)). 공통 업무 예외는 두지 않습니다.

## 프로필과 CORS

- `local`·`demo` 프로필에서만 `DemoUserController`, `CurrentUserResolver`, `UserWebConfiguration`, `LocalDemoCorsConfiguration`이 등록됩니다. 그 밖에서는 `/api/v1/demo-users`가 404 `NOT_FOUND`입니다(명세의 "다른 환경에서는 404").
- `member` 프로필에서는 `MemberCurrentUserResolver`, `MemberUserWebConfiguration`만 등록되고 시연 부품과 CORS 설정은 없습니다. 로컬 브라우저는 Vite 프록시로 같은 origin을 쓰기로 했고(결정 37 O2-L) 프런트 설정은 조율 대기입니다.
- 기본 프로필은 정하지 않았습니다. 로컬에서 시연 기능을 쓰려면 `SPRING_PROFILES_ACTIVE=local`, 회원 흐름을 쓰려면 `SPRING_PROFILES_ACTIVE=member`를 줍니다. 둘을 함께 주면 기동하지 않습니다.
- CORS(브라우저가 다른 주소의 API를 부를 때 서버가 허용 목록을 알려 주는 규칙): `/api/**`에 origin `http://localhost:5173`, 메서드 `GET, POST, PUT, PATCH, DELETE, OPTIONS`, 헤더 `Content-Type, X-BeONE-Demo-User, Idempotency-Key`, 자격 증명 없음. 허용 밖 origin·메서드·헤더의 사전 요청은 Spring 기본 처리로 403이며, 이 응답 본문은 공통 실패 형식이 아닙니다(브라우저가 본문을 읽지 않는 경로).

## 테스트

- `test`: [`DemoUserHeaderTest`](../../backend/src/test/java/com/beone/api/user/web/DemoUserHeaderTest.java) 헤더 값 판정, [`CurrentUserTest`](../../backend/src/test/java/com/beone/api/user/application/CurrentUserTest.java), [`MemberUserServiceTest`](../../backend/src/test/java/com/beone/api/user/application/MemberUserServiceTest.java)(장부 생성·조회), [`CurrentUserModeGuardTest`](../../backend/src/test/java/com/beone/api/user/web/CurrentUserModeGuardTest.java)(프로필 동시 사용 시 기동 실패)
- `webTest`: [`DemoUserApiWebTest`](../../backend/src/test/java/com/beone/api/user/web/DemoUserApiWebTest.java)(local: 목록 응답, 현재 사용자 주입, 헤더 없음·잘못된 값·중복 400, CORS 허용·거부), [`DemoUserProfileOffWebTest`](../../backend/src/test/java/com/beone/api/user/web/DemoUserProfileOffWebTest.java)(프로필 없음: 404, 부품 미등록)
- `integrationTest`: [`DemoUserIntegrationTest`](../../backend/src/test/java/com/beone/api/user/DemoUserIntegrationTest.java)(V2 적용과 `ddl-auto: validate`, 시드 2명, 저장소·서비스 조회, 실제 HTTP 응답), [`MemberUsersMigrationIntegrationTest`](../../backend/src/test/java/com/beone/api/user/MemberUsersMigrationIntegrationTest.java)(V14 백필·제약), [`MemberProfileIntegrationTest`](../../backend/src/test/java/com/beone/api/auth/web/MemberProfileIntegrationTest.java)(member 프로필 수직 기능)

## 제약

- 시드 값은 [명세](../api/cards.md)의 응답 예시에 있는 값만 씁니다. `user_id`는 자동 증가 값이며 다른 테이블의 시드는 `demo_user`로 찾아 참조합니다.
- `users` 행은 `demo_user`와 `member_id` 중 정확히 하나만 가집니다(`ck_users_owner`). `member_id`는 UNIQUE이고 `members`를 RESTRICT로 참조하므로 장부가 있는 회원 행은 지울 수 없습니다. 탈퇴·보존 정책(D10)은 미결입니다.
- 업무 테이블 11개의 `users(user_id)` FK는 바꾸지 않았습니다. 회원 장부도 같은 `user_id`로 업무 데이터를 나눕니다.
