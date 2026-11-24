# #186 prod 웹 슬라이스 CODEF 미등록·전달 헤더 고정 (G5 L4·L5)

브랜치 `test/186-prod-codef-forwarded-guards`, 시작 기준 `origin/feat/183-prod-profile-skeleton` `cac7226`(PR #185). `prod` 프로필에 의존하므로 #185 위에 쌓았고, #185 병합 뒤 `develop`과 동기화합니다. 제품 코드는 바꾸지 않았습니다.

## 2026-11-24 · CODEF 공개 미등록과 전달 헤더 미해석 고정

- 배경: 결정 38-8(D9)은 CODEF를 비공개 실연동 검증 전까지 공개에 등록하지 않고 공개 전환은 별도 승인으로 정했습니다. 결정 38-7(O2)은 웹과 `/api`를 같은 HTTPS 주소로 두며, G5 공개 구성 설계(관리 폴더) 2-3절은 앱이 `X-Forwarded-*`를 해석하지 않도록 권장했습니다(#183이 `server.forward-headers-strategy: none` 반영). 설계 5절 L4·L5 범위만 테스트로 고정했습니다.
- 한 일
  - L4 `codef/web/CodefPublicProfileWebTest`(웹 4): `@WebSliceTest` + `@ActiveProfiles("prod")`. CODEF 4경로(`GET`·`POST`·`DELETE /api/v1/me/codef/connection`, `POST /api/v1/me/codef/syncs`)가 세션 없이도 404 `NOT_FOUND`(본문 STRICT 비교, member no-store 유지), CODEF 컨트롤러 2개·`FakeCodefGateway`·`CodefGateway`·`CodefSyncGateway` Bean 없음, 슬라이스 환경이 `prod & member`이고 `local | demo`가 아님. `FakeCodefGateway`는 슬라이스 스캔 대상이 아니라서 `@Import`로 넣어 `@Profile` 조건으로 빠지는지 봅니다.
  - L5 `auth/web/ForwardedHeadersPublicWebTest`(웹 4): 같은 요청을 전달 헤더 없이·위조 `X-Forwarded-For`·`-Proto`·`-Host`·`-Port`와 함께 보내 상태·본문·`Set-Cookie`(속성 전체)·`Location`이 같음을 비교. 로그인·로그아웃 쿠키(`__Host-BEONE_SESSION`, `Secure; HttpOnly; SameSite=Lax`, `Domain` 없음)와 테스트 전용 에코 컨트롤러(scheme·host·port·원격 주소·secure·요청 URL, `ServletUriComponentsBuilder`로 만든 201 `Location`). `ForwardedHeaderFilter` Bean 없음과 `server.forward-headers-strategy=none` 확인. `MemberSessionController`가 패키지 내부 클래스라 설계 초안의 `common/web` 대신 `auth/web`에 뒀습니다.
  - `docs/domains/codef.md`에 "공개(prod) 범위" 절을 더했습니다.
- `codefConnected=false` 확인: 이 필드는 `/api/v1/me/cards`가 아니라 `GET /api/v1/me` 응답입니다. `UserCardApiWebTest`(local, 서비스 대역)와 `MemberProfileIntegrationTest`(member, 실제 DB)가 이미 `false`를 확인하므로 새로 더하지 않았습니다. 웹 슬라이스에서는 값이 서비스 대역에서 오므로 prod 고정 의미가 없고, prod 통합 확인(설계 C3)은 L3 범위입니다.
- 회귀 검출 확인(임시 변경 후 원복, 커밋 안 함)
  - L5 슬라이스에 `ForwardedHeaderFilter`를 넣으면 에코 비교와 Bean 검사가 실패합니다.
  - `server.forward-headers-strategy=framework`로 바꾸면 설정값 검사만 실패합니다. 웹 슬라이스에는 웹 서버 자동 구성이 없어 필터가 자동 등록되지 않기 때문입니다. `native`(Tomcat)는 MockMvc가 보지 않습니다. 실제 HTTP 확인은 prod 통합 테스트 범위입니다.
  - L4를 `local`로 바꾸면 CODEF 컨트롤러가 등록되어 컨텍스트가 의존성 부족으로 실패합니다(프로필 조건이 실제로 작동함).
- 검증: `./gradlew test webTest` 통과(단위 1411, 웹 401 = #185의 393 + 이번 8, 실패·skip 0). 통합 테스트·Docker·서버·실CODEF는 쓰지 않았습니다.
- 하지 않은 것: 제품 코드 변경, CODEF 공개 전환(별도 승인), prod 통합 테스트(L3), Fake 메일 차단(L2), 서버·nginx 반영
- 남은 것: #185 병합 뒤 `develop` 동기화, L2·L3
