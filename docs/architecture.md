# BeONE 구조 안내

## 이 문서의 목적

처음 참여한 팀원이 저장소의 역할 분담과 코드를 읽는 순서를 이해하기 위한 안내입니다. 요구사항은 [PRD](PRD.md), 확정 결정과 미결 사항은 [결정 기록](decisions.md), 실행·검증 명령은 [README](../README.md)를 기준으로 합니다. 이 문서는 요구사항을 새로 결정하지 않습니다.

## 모듈과 역할

- [android/](../android/): Kotlin Android 앱. 제품에서는 React 화면을 표시할 WebView 셸과 기기 인증을 담당합니다. 기기 인증과 60초 활성 상태의 네이티브 기반은 [Android 기기 인증 안내](android/auth-session.md)에 있습니다. 인증 요청을 동시에 하나로 제한하고 앱 이탈로 취소한 요청의 최종 결과를 기다리는 요청 단계 관리(`AuthRequestGate`)는 검증용 구현입니다. 이 구조를 넣기 전 코드로 실기기 1~15번을 통과했고, 지금 코드는 같은 기기 한 대에서 주요 항목을 관측상 통과했습니다. 다만 `정리 중` 경로는 관측하지 못했고 16·17번은 미실행입니다([실기기 확인 목록](android/device-checklist.md)). 웹 ↔ 네이티브 브리지와 기기 인증 상태 전달은 #88에서 Android 부품으로 로컬 구현했습니다. WebView 셸(`MainActivity`)이 브리지를 통해 기기 인증 상태(`VirtualCardActivationViewModel`)와 연결되며, debug 앱의 인증 점검 화면(`DeviceAuthCheckActivity`)은 브리지와 연결되지 않은 별도 화면입니다. 실제 프런트엔드·서버 API 통합과 실기기 확인은 아직 없습니다.
  - WebView 셸의 기본 구조(신뢰 origin(스킴·호스트·포트의 묶음) 설정 분리, 탐색 제한, debug 개발 서버 로딩)는 [Android WebView 셸 안내](android/webview-shell.md)에 있으며, 실기기 관측은 끝나지 않았습니다. 배포 앱이 서버 HTTPS 화면을 불러오는 방침은 [D-25](decisions.md#d-25-확정-정책)에 있습니다. 2026-11-16 사용자 결정으로 release 시작 주소 `https://beonepay.site/`와 신뢰 origin `https://beonepay.site`를 정했고, [D-21](decisions.md#d-21-확정-정책) 보충으로 [v1 계약](android/bridge-contract-candidate.md)을 확정했습니다. #88은 브리지 v1(`com.beone.app.bridge`, JS 객체 `BeoneBridge`)을 신뢰 origin에 노출하고 최상위 프레임의 메시지만 처리해 기기 인증 상태와 연결하는 Android 로컬 구현입니다. 같은 origin의 iframe(페이지 안에 끼워 넣은 하위 프레임)에도 객체는 노출되지만 하위 프레임 메시지에는 응답하지 않습니다. HTTP API·frontend 연결 완성이나 release 실기기 확인을 뜻하지 않으며, 로컬 단위 테스트만 실행했습니다([진행 기록](progress/android/88-android-bridge-local.md)).
- [frontend/](../frontend/): Vite·React·TypeScript 웹. [앱 시작과 회원 인증 화면](domains/frontend-startup.md), 지갑 메인과 [가상 주문](domains/frontend-orders.md) 화면과 [가상카드·모의 승인](domains/frontend-payment.md) 흐름을 제공합니다.
  - [혜택 조회](domains/frontend-benefits.md)는 보유 카드별 월간 상태와 모의 원장·결제 상세를 표시합니다.
- [backend/](../backend/): Java 21·Spring Boot API. 카드 규칙과 계산 입출력 모델, 공통 API 응답·예외 처리를 보유합니다. DB 마이그레이션은 Flyway로 관리합니다.
- [docs/](./): 요구사항, 결정, 조사·평가 자료와 개발 안내.
- [fixtures/](../fixtures/): 비식별 평가·시연 데이터. 테스트용 합성 값은 실제 관측값과 구분합니다.
- [deploy/](../deploy/): 배포용 Compose(백엔드·MySQL, 호스트 포트 비공개)와 이미지 빌드·로컬 검증 스크립트. 공개 서버 적용과 외부 API 공개는 아직 하지 않았습니다([백엔드 배포 기반](deployment/backend-compose.md)). 배포 워크플로(`.github/workflows/deploy.yml`)와 웹 이미지 정의(`deploy/web/Dockerfile`)는 [배포 자동화](deployment/cd.md)에 설명하며, 저장소 변수가 켜지기 전에는 동작하지 않습니다. 켜는 순서는 [배포 자동화 활성화 런북](deployment/cd-activation.md)에 있습니다.

세 애플리케이션은 독립적으로 빌드·테스트합니다. 실행 방법을 이 문서에 중복해서 관리하지 않습니다.

## 도메인이란?

도메인은 제품에서 다루는 문제 영역입니다. 패키지는 관련 코드를 묶는 단위입니다. 하나의 도메인 안에 식별자, 입력, 규칙, 결과 등 여러 책임의 패키지가 들어갈 수 있습니다.

현재 설명 문서가 있는 도메인:

- [benefit: 카드 혜택 규칙과 계산 입출력](domains/benefit.md)
  - 위치: `backend/src/main/java/com/beone/api/benefit/`
  - 하위 패키지: `engine`, `card`, `money`, `state`, `input`, `rule`, `primitive`, `result`
  - 책임: 안전한 데이터 표현, 입력·결과 일관성 검사, 규칙 JSON 로드(`rule.loading`, 잘못되면 서버 시작 실패), 날짜에 맞는 검증 규칙 선택, 서비스 한 건의 산식·개별 조건 판정(`primitive`), 서비스 후보·채택·기여액 결과 표현(`result`), 보유 카드를 계산해 D-12 순서로 고르는 엔진 구현체(`engine.DefaultRecommendationEngine`, #80: 카드 상태·남은 한도·차감액·선별·조건부·대표 카드 비교).
  - 범위 밖: 월간 최적화·예정 소비 반영, 승인 뒤 사용액 저장·합산(업무 API), HTTP 추천 API, CODEF 통신 구현.
- [codef: CODEF 카드 응답 해석 경계](domains/codef.md)
  - 위치: `backend/src/main/java/com/beone/api/codef/`
  - 책임: 승인내역·보유카드·실적조회 응답 본문에서 결과 코드 분류와 비민감 필드만 해석. 빈 값·형식 오류를 0원이나 0건으로 바꾸지 않고, 민감 필드를 옮기지 않습니다. 해석된 KB 승인내역 한 건의 취소 표현 형태를 기술합니다(#45, 금액 계산·매칭 없음). 그 앞단 전송 단계(#43)는 입력받은 HTTP 상태를 200·401·그 밖으로 나누고, 200 본문만 URL 디코딩해 해석합니다. `codef.transport`(#62)는 토큰 발급·캐시, 상품 요청, 시간 초과, 호출 상한을 맡습니다. 키가 없어도 서버는 뜨고 CODEF 호출만 실패합니다.
  - 하위 패키지 `codef.application`·`codef.persistence`·`codef.web`(#126): CODEF 연결 상태 조회·계정 연결·해제 API(`/api/v1/me/codef/connection` GET·POST·DELETE, local·demo만). CODEF 접근은 `CodefGateway` 인터페이스와 local·demo용 가짜 구현만 있고 실 HTTP 구현은 없습니다. connectedId는 `codef_connections`에 AES-GCM 암호문으로만 두고(키는 환경 변수, 없으면 연결 실패) 응답·로그에 내보내지 않습니다. 해제는 연결 행만 지웁니다. 기존 해석·전송·sync 클래스는 바꾸지 않았습니다. `GET /api/v1/me`의 `codefConnected`는 card의 조회 포트 `CodefConnectionLookup`을 codef가 구현해 연결 행 존재로 채웁니다(#136, card → codef 의존 없음).
  - 같은 하위 패키지의 조회·동기화(#129): `POST /api/v1/me/codef/syncs`(local·demo만). `CodefSyncGateway`(가짜 구현만, 합성 fixture)로 조회하고 `codef.sync`의 판정 함수를 호출만 해 새 CODEF 카드를 `user_cards`(카드명이 이름 사전과 앞뒤 공백을 뗀 정확 일치이면 `CANDIDATE`·후보 상품, 아니면 `UNMAPPED`. 카드명이 `카드명|발급사` 형식이면 `|` 앞부분으로 정확 일치를 1회 더 본다. 같은 사용자에게 같은 상품의 활성 후보(`CANDIDATE`)나 확정(`USER_CONFIRMED`) 카드가 있으면 `UNMAPPED`. 서버가 확정하지 않는다. #268)에, 받아들인 승인 행을 `codef_approval_rows`(승인번호는 HMAC 지문만, 키는 환경 변수, 없으면 승인은 부분 실패)에, 실적 참고값을 `codef_performance_references`에 저장합니다. 승인·실적 행은 카드명 정확 일치, 없으면 이름 사전으로 같은 상품이 되는 보유카드 1장에 붙이고(#270), 저장 뒤 진단 집계를 INFO 로그 한 줄로 남깁니다(카드번호·승인번호·금액·일시·connectedId 없음). `ledger_transactions`와 원장 조회는 바꾸지 않습니다. 보유 카드 생성·잠금·후보 두기는 `card`의 `UserCardService` 메서드 3개로만 합니다(codef → card 의존).
  - 하위 패키지 `codef.sync`(#82·#92): 전송 계층의 세 조회 대상 호출 결과를 대상별로 판정(부분 실패)하고 보유카드 후보(`UNMAPPED`)·승인 행 형태 분류·실적 참고값(계산 미사용)으로 옮깁니다. #92에서 카드명 정확 일치 카드 연결(#270부터 정확 일치가 없으면 주입받은 이름 사전으로 같은 상품 보유카드 1장에 연결), 승인 날짜·시각 해석, 승인번호 지문(주입) 기반 중복 키·재조회 대조, D-17 Type2 짝짓기로 원장 후보 행과 불완전 사유를 판정하는 순수 함수를 더했습니다. 후보 상품 산출·완전 선언·도메인 원장 연결·저장은 하지 않습니다.
  - 해석·전송·sync 계층의 범위 밖: 자동 재시도, 실 계정 연결 요청·RSA 암호화·추가 인증 요청, DB 저장, BeONE 상태값으로의 변환. 이 계층은 `benefit`·`common.web`에 의존하지 않습니다.
  - 실 게이트웨이(#193, LP-1): `LiveCodefGateway`가 전송 계층(`CodefClient`)으로 계정 등록·삭제와 상품 3종을 부릅니다. 프로필 `member & codef-live & !prod`에서만 실 게이트웨이와 CODEF 경로 4개를 회원 세션 기준으로 등록하고(local·demo는 가짜 그대로), `prod`와 `codef-live`를 함께 켜면 `PublicProfileGuard`가 기동을 멈춥니다. 시험은 루프백 스텁 HTTP로만 했고 **실호출은 별도 승인 전 하지 않습니다**. 같은 조건에서 `JdbcCodefCallBudget`(#196, LP-2)이 검증 기간 창의 API별 누적 호출 수를 `codef_call_budgets`에 세며, 설정이 없으면 호출 0입니다. 자세한 내용은 [codef 문서](domains/codef.md#비공개-실연동-프로필-codef-live-lp-1-193).
  - 연결·동기화 계층(`codef.application`·`codef.persistence`·`codef.web`)의 범위 밖: 운영 키 공급·회전, 출처 선택 API, 도메인 원장 연결(결정 22 R8), 원장 완전 선언(R7). local·demo의 동기화는 가짜 게이트웨이를 쓰고, 전송 계층(`codef.transport`)은 `codef-live`의 실 게이트웨이만 씁니다.

- [user: 현재 사용자 결정 지점과 시연 사용자](domains/user.md)
  - 위치: `backend/src/main/java/com/beone/api/user/` (`web`, `application`, `persistence`)
  - 책임: 현재 사용자 결정 지점(D-09, 프로필마다 한 곳). `local`·`demo`는 `X-BeONE-Demo-User` 헤더로 정하는 `CurrentUserResolver`, 로컬 회원 프로필 `member`는 회원 세션으로 정하는 `MemberCurrentUserResolver`(#157, 결정 37)이고, 함께 켜면 `CurrentUserModeGuard`가 기동을 멈춥니다. 장부 테이블 `users`(시연 시드 2명 Flyway `V2`, 회원 장부 `member_id` Flyway `V14`), 이메일 인증 이벤트로 회원 장부를 만드는 `MemberUserService`, `GET /api/v1/demo-users`(`local`·`demo`만).
  - 범위 밖: `GET /api/v1/me`(보유 카드 작업과 함께 `card`에 구현), 회원 인증 자체(`auth`), 회원용 가상 포트폴리오(결정 37 D1b, 결정 38-3으로 회원 MVP에서 제공하지 않는 후순위).
- [auth: 회원 인증 세션·CSRF](domains/auth.md)
  - 위치: `backend/src/main/java/com/beone/api/auth/` (`web`, `application`, `persistence`)
  - 책임: MySQL 서버 세션 `auth_sessions`(Flyway `V9`, 세션 ID는 SHA-256 해시만 저장), 세션 쿠키 `__Host-BEONE_SESSION`, `GET /api/v1/auth/csrf`와 `/api/v1/auth/**` POST의 CSRF 검사(#124, U1), 회원 `members`·1회용 토큰 `auth_tokens`·메일 요청 제한 `auth_mail_throttles`(Flyway `V11`)와 회원가입·인증메일 재요청·이메일 인증 완료(#128, U2, Argon2id, 가짜 메일 발송기), 로그인·로그아웃·현재 회원 세션 조회(#132, U3, 회원 세션 7일·30일·동시 5개, 로그인 실패 제한 `auth_login_throttles`·`auth_login_failures`, Flyway `V13`), 비밀번호 재설정 요청·완료(#138, U4, 재설정 토큰은 `auth_tokens`에 목적 `PASSWORD_RESET`으로 저장, 완료 시 그 회원의 모든 세션 종료, 새 테이블 없음). `local`·`demo`·`member` 프로필에서만 등록됩니다. `member` 프로필에서는 `/api/v1/me`·`/api/v1/me/**`의 세션·CSRF 검사(`MemberApiInterceptor`, 401 → 403, 미사용 7일 연장)를 하고 회원 ID를 요청 속성으로 넘기며, 이메일 인증 완료 때 `MemberEmailVerifiedEvent`를 발행합니다(#157). 시연 사용자 헤더와 `user` 패키지에 의존하지 않습니다(`user` → `auth` 한 방향).
  - 범위 밖: `local`·`demo`에서 회원과 `/me` 업무 자원의 연결, `member`에 여는 나머지 업무 API(G3 범위), 공개 회원의 CODEF 연결 범위(D9), 공개 프로필·CORS(O1~O3).

도메인이 아닌 공통 웹 기반 코드:

- `common.web`: 공통 API 응답 형식과 예외 처리
  - 위치: `backend/src/main/java/com/beone/api/common/web/`
  - 책임: 성공·실패 응답 형식, 공통 오류 코드, 요청 오류·예상하지 못한 오류의 HTTP 응답 변환과 안전한 로그, `local`·`demo` 프로필의 CORS 허용 목록(`LocalDemoCorsConfiguration`, D-23 11항).
  - 의존 방향: 컨트롤러·예외 처리기 같은 웹 경계에서만 사용합니다. 도메인 코드는 이 패키지에 의존하지 않습니다.
  - 설명 문서: [API 공통 응답·오류 처리](api.md)
- `common.config`: 공개 프로필 `prod` 골격과 조합 검사(#183, 결정 38-6·7, G5 L1)
  - 위치: `backend/src/main/java/com/beone/api/common/config/`, 설정 `backend/src/main/resources/application.yml`·`application-prod.yml`
  - 책임: `prod`는 공개 환경용 프로필입니다. `application.yml`의 프로필 그룹(`spring.profiles.group.prod: member`, 한 프로필을 켜면 지정한 프로필도 함께 켜지는 Spring 설정)으로 회원 세션·소유권·CSRF·no-store 부품(`member`)을 그대로 재사용합니다. `application-prod.yml`은 전달 헤더(`X-Forwarded-*`)를 해석하지 않도록 `server.forward-headers-strategy: none`, 운영 DB 보호를 위해 `spring.flyway.clean-disabled: true`를 둡니다. actuator는 기존 `health`·상세 없음을 그대로 쓰고, 비밀값은 두지 않습니다(DB 접속 정보는 기존 환경 변수). [`PublicProfileGuard`](../backend/src/main/java/com/beone/api/common/config/PublicProfileGuard.java)는 `prod`가 `local`·`demo`와 함께 켜지거나 그룹이 풀려 `member` 없이 켜지면 빈을 만들기 전에 기동을 멈춥니다(`CurrentUserModeGuard`와 같은 방식, 먼저 실행). `prod`에 CODEF 키·토큰 환경 변수 이름이 있어도 멈춥니다(값은 읽지 않음, 결정 38-8, #191, 공개 전환 승인 시 제거).
  - 현재 범위: 골격만 있습니다. 가짜 메일 발송기 `FakeAuthMailSender`는 `prod`에서 등록하지 않고(#189, G5 L2), `prod`의 메일은 SMTP 발송기 `SmtpAuthMailSender`가 보냅니다(N1, 결정 39-6, #207, [auth](domains/auth.md)). **`prod`는 메일 설정(`spring.mail.host`·`port`·`username`·`password`, `beone.mail.from`, `beone.mail.link-base-url`)이 하나라도 없으면 기동하지 않습니다**(메시지에 키 이름만, `SmtpMailConfigurationTest`·`AuthMailSenderProfileTest`로 고정). 실제 계정으로의 발송은 확인하지 않았습니다(앱 비밀번호 사용자 제공 대기). `prod` 웹·통합 테스트는 실제 SMTP 발송기 구성에 루프백 스텁 SMTP를 받는 쪽으로 둡니다(`PublicProfileWebTest`, `PublicProfileIntegrationTest`, G5 L2·L3). compose의 프로필 지정, 서버·nginx 반영, DB 주소·공개 origin 검사는 하지 않았습니다.

## 사용자 식별과 업무 API

- 사용자 식별은 [D-09](decisions.md#d-09d-14-확정-정책)에 따라 MVP 한정으로 고정 시연 사용자 2명(내 카드·가상 포트폴리오)을 씁니다. 현재 사용자는 서버의 한 곳에서만 정하고, 사용자 데이터는 `user_id`로 나눠 저장합니다. 이는 `local`·`demo` 환경의 계약입니다(현재 사용자 결정 지점은 #100에서 [user](domains/user.md)에 구현). 로컬 회원 프로필 `member`에서는 회원 세션으로 회원의 '내 카드' 장부를 현재 사용자로 정하며, 이 프로필에는 `/api/v1/me`·보유 카드(#157), 월간 상태·결제 전 설정(G3a #159), 주문·예정 소비·추천(G3b #160), 원장 조회(#165), 모의 결제(G3c #161) API만 엽니다([결정 37](decisions.md#결정-37-회원과-업무-장부-연결), [결정 38](decisions.md#결정-38-g3-회원-모의-결제와-승인-흐름)). 공개 회원 환경은 결정 38 6항(O1)에 따라 `prod` 프로필(골격, `member` 재사용)로 구성하며, 공개 서버 배포는 아직 하지 않았습니다(아래 `common.config`).
- 업무 API는 [D-14](decisions.md#d-09d-14-확정-정책)에 따라 `/api/v1/me/...` 형태로 둡니다. 1차는 수동 입력·가상 포트폴리오, 2차는 CODEF 연결·조회입니다.
- 개별 API 명세는 [`docs/api/`](api/README.md)에 있습니다(2026-11-03 작성). 구현 여부와 프로필별 등록 범위는 각 명세와 도메인 안내를 기준으로 합니다.
- 업무 API는 `GET /api/v1/demo-users`(#100), 카드 상품 조회 `GET /api/v1/card-products`·`/{cardProductId}`(#105, 테이블 없음), 주문·예정 소비 `POST /api/v1/me/orders`·`GET /api/v1/me/orders/{orderId}`·`GET`·`PUT /api/v1/me/planned-spends`(#104)와 내 주문 목록 `GET /api/v1/me/orders`(#178, 결정 38-5, 새 테이블·인덱스 없음), 현재 사용자 `GET /api/v1/me`와 보유 카드 `GET`·`POST /api/v1/me/cards`·`PATCH /api/v1/me/cards/{userCardId}/mapping`·`PATCH`·`DELETE /api/v1/me/cards/{userCardId}`(#108, `card` 패키지), 카드 월간 상태 `GET`·`PUT /api/v1/me/cards/{userCardId}/monthly-states/{month}`(#110, `monthlystate` 패키지), 출처 충돌 계산 기준 선택 `PUT /api/v1/me/cards/{userCardId}/monthly-states/{month}/source-selections`(#134, 충돌 경로가 없어 항상 409 `SOURCE_CONFLICT_NOT_FOUND`, 테이블 없음), 결제 전 설정 `GET`·`PUT /api/v1/me/payment-preference`와 보유 카드 대표 카드 보호(#111, `preference` 패키지), 추천 계산·조회 `POST /api/v1/me/orders/{orderId}/recommendations`·`GET /api/v1/me/recommendations/{recommendationId}`(#114, `recommendation` 패키지), 모의 승인·조회·취소 `POST /api/v1/me/orders/{orderId}/simulated-payments`·`GET /api/v1/me/simulated-payments/{simulatedPaymentId}`·`POST /api/v1/me/simulated-payments/{simulatedPaymentId}/cancellations`(#116, `payment` 패키지), 원장 조회 `GET /api/v1/me/ledger-transactions`(#118, `ledger` 패키지, 모의 원장 행만, 새 테이블 없음), CODEF 연결 `GET`·`POST`·`DELETE /api/v1/me/codef/connection`(#126, `codef` 연결 계층, 가짜 게이트웨이만), CODEF 조회·동기화 `POST /api/v1/me/codef/syncs`(#129, 가짜 게이트웨이만, 원장 불변)이 구현됐고, DB 업무 테이블은 시연 사용자 `users`, 주문 `orders`·예정 소비 `planned_spends`(#104), 보유 카드 `user_cards`(#108, 가상 포트폴리오 시드 4장), 월간 상태 `monthly_states`와 자식 테이블 2개(#110, 시드 없음), 결제 전 설정 `payment_preferences`(#111), 추천 `recommendations`·`recommendation_card_bases`(#114), 모의 결제 `simulated_payments`·`payment_cancellations`·`idempotency_keys`와 자체 모의 원장 `ledger_transactions`·`ledger_usage_effects`(#116, 주문 `simulated_payment_id` 열 추가), CODEF 연결 `codef_connections`(#126), CODEF 동기화 `codef_syncs`·`codef_held_cards`·`codef_approval_rows`·`codef_performance_references`(#129)입니다. 인증 계층은 [auth](domains/auth.md)의 세션·CSRF 기반과 `GET /api/v1/auth/csrf`(#124, 세션 테이블 `auth_sessions`), 회원가입·인증메일 재요청·이메일 인증 완료(#128, `members`·`auth_tokens`·`auth_mail_throttles`), 로그인·로그아웃·세션 조회(#132, `auth_login_throttles`·`auth_login_failures`), 비밀번호 재설정 요청·완료(#138, 새 테이블 없음)가 있습니다. `local`·`demo`에서는 업무 API의 현재 사용자 결정에 연결되지 않고, 로컬 회원 프로필 `member`에서만 `/api/v1/me`·보유 카드(#157, `users.member_id` Flyway `V14`), 월간 상태·결제 전 설정(G3a #159), 주문·예정 소비·추천(G3b #160), 원장 조회(#165), 모의 결제(G3c #161, 결정 38), 주문 목록 조회(#178, 결정 38-5) API에 연결됩니다.

## 업무 API 패키지 구조 (D-23, 미구현)

[D-23](decisions.md#d-23-확정-정책)이 정한 구조입니다. `user`, `card`의 상품 조회·보유 카드, `order`, `monthlystate`, `preference`, `recommendation`, `payment`, `ledger`, `codef`의 연결·동기화 계층이 구현됐고(#100·#105·#108·#104·#110·#111·#114·#116·#118·#126·#129, [user 안내](domains/user.md)·[card 안내](domains/card.md)·[order 안내](domains/order.md)·[monthlystate 안내](domains/monthlystate.md)·[preference 안내](domains/preference.md)·[recommendation 안내](domains/recommendation.md)·[payment 안내](domains/payment.md)·[ledger 안내](domains/ledger.md)·[codef 안내](domains/codef.md#codef-연결-api-126)·[codef 동기화](domains/codef.md#codef-조회동기화-api-129)) 나머지는 아직 없습니다.

```text
com.beone.api
├── user            # 현재 사용자 결정 지점(시연 헤더·회원 세션), 시연 사용자, 회원 장부 [구현, #100·#157]
├── auth            # 회원 인증 [U1 세션·CSRF #124, U2 가입·이메일 인증 #128, U3 로그인·세션 #132, U4 비밀번호 재설정 #138, auth 안내: domains/auth.md]
├── card            # 상품 조회 [구현, #105, 테이블 없음], 보유 카드·매핑·GET /me [구현, #108]
├── preference      # 결제 전 설정, 대표 카드 보호(card 확장 지점 구현) [구현, #111, preference 안내: domains/preference.md]
├── monthlystate    # 월간 상태 조회·수동 입력·출처 충돌 선택 [구현, #110·#134, monthlystate 안내: domains/monthlystate.md]
├── order           # 주문, 예정 소비 [구현, #104, order 안내: domains/order.md]
├── recommendation  # 추천 계산·저장·조회, 엔진 입구 호출, 지연 무효화 [구현, #114, recommendation 안내: domains/recommendation.md]
├── payment         # 모의 승인·취소, 멱등 키 [구현, #116, payment 안내: domains/payment.md]
└── ledger          # 원장 쓰기·조회 [구현, #116·#118, CODEF 행·월간 산출은 후속, ledger 안내: domains/ledger.md]
    각 패키지 안: web/(Controller, 요청·응답 DTO), application/(서비스), persistence/(엔티티, 저장소)
```

- `com.beone.api.card`는 보유 카드 기능이고 `com.beone.api.benefit.card`는 엔진의 식별자 타입입니다. 전체 경로가 달라 충돌하지 않습니다.
- 혜택 계산은 [엔진 입구](domains/benefit.md#engine-업무-api의-입구) `benefit.engine`만 부릅니다. 카드 상품 조회의 `RuleCatalog` 읽기만 예외입니다. 금액 판단을 업무 코드에 복제하지 않습니다.
- 응답은 `ApiResponse`, 기능 오류는 패키지별 `ApiErrorCode`와 높은 우선순위의 처리기로 추가합니다. `ApiErrorCode`와 `common.web`은 웹 경계에서만 씁니다.
- 카드 상품 테이블을 두지 않습니다. 규칙 JSON이 원본이고 보유 카드는 `card_product_id` 문자열로 참조합니다. Flyway 번호 규칙은 D-23을 따릅니다.
- 화면 연결은 CORS 허용 목록을 씁니다. `X-BeONE-Demo-User` 때문에 사전 요청이 항상 발생하므로 허용 헤더에 넣습니다.

## 데이터 흐름과 현재 구현의 경계

아래는 모델을 연결하는 개념 흐름이며 완성된 실행 경로가 아닙니다.

```text
업무 API
    → RecommendationEngine(benefit.engine) → DefaultRecommendationEngine [#80, 추천 API 연결 #114: 주문·보유 카드·월간 상태·결제 전 설정 입력, 스냅샷 뒤 모의 원장 승인 차감액 반영 #120, CODEF 거래·예정 지출 입력은 후속]
주문·보유 카드·거래 원장·월별 상태·예정 지출
    → RecommendationInput
카드 약관과 검증된 규칙 버전
    → RuleCatalog → RuleSelection
두 입력을 사용한 카드별 혜택 계산·D-12 선택 [엔진 구현], 월간 최적화 [범위 밖]
    → CardBenefit(CardEvaluation) → EngineResult [엔진 출력]
```

실제 결제는 제품 범위가 아닙니다. CODEF 실거래와 자체 모의 거래 원장은 분리합니다. 확인되지 않은 상태·규칙·혜택 금액을 0원으로 대체하지 않습니다.

## 코드를 읽는 순서

1. [benefit 안내](domains/benefit.md)에서 용어와 패키지 책임을 확인합니다.
2. `RecommendationInput`으로 계산에 필요한 입력을 살펴봅니다.
3. `CardRuleVersion`과 `RuleCatalog`로 규칙 표현과 선택을 살펴보고, [규칙 데이터 안내](rules/README.md)에서 로더가 읽는 JSON을 확인합니다.
4. `Recommendation`과 `BenefitAmount`로 결과와 불확실성 표현을 확인합니다.
5. `backend/src/test/java/com/beone/api/benefit/`에서 모델 검증과 fixture 매핑 테스트를 읽습니다. 매핑 성공은 계산 엔진 정확성이나 공식 약관 검증 완료를 뜻하지 않습니다.

## 문서 유지

도메인 추가·이동·삭제나 도메인 간 관계 변경 시 이 문서를 갱신합니다. 세부 클래스 설명은 해당 도메인 문서에서 관리합니다. 작성·갱신 규칙은 [CLAUDE.md](../CLAUDE.md)의 도메인·구조 문서 유지, PR 확인 항목은 [CONTRIBUTING.md](../CONTRIBUTING.md)를 따릅니다.
