# #189 공개(prod) 프로필 가짜 메일 차단과 prod 통합 테스트 (G5 L2·L3)

브랜치 `feat/189-prod-public-guards`, 시작 기준 `origin/feat/183-prod-profile-skeleton` `21103a01`(PR #185). #185가 `develop` `875611d`로 병합된 뒤 `origin/develop`을 merge해 지금 기준은 `develop`입니다.

## 2026-11-25 · 가짜 메일 prod 제외, prod 웹 슬라이스·통합 테스트

- 근거: 결정 38-6(공개 운영 구성에서 시연·Fake·강제 실패 차단, 회원 세션·CSRF 재사용), G5 공개 구성 설계 5절 L2·L3.
- 한 일
  - `FakeAuthMailSender`의 프로필을 `(local | demo | member) & !prod`로 바꿨습니다. 지시서 문구는 `member & !prod`였지만, 그대로 쓰면 `local`·`demo`에서 가짜 발송기가 빠져 기동하지 못하므로 기존 범위에서 `prod`만 빼는 식으로 했습니다(보고 대상).
  - 실메일 발송기는 만들지 않았습니다(N1 미결). 그래서 `prod`는 메일 발송기 빈이 없어 기동에 실패하고, 이것이 의도된 동작입니다. 실패 형태는 Spring 기본 실패(`authTokenMailer` 생성 실패, 근본 원인 `NoSuchBeanDefinitionException: No qualifying bean of type 'com.beone.api.auth.application.AuthMailSender'`)이며 새 가드는 넣지 않았습니다(`PublicProfileGuard`는 `common`에 있어 `auth` 타입을 검사하려면 패키지 의존이 새로 생김).
  - 테스트
    - `AuthMailSenderProfileTest`(단위 5): `prod,member` + 메일 빈 없음 → 기동 실패와 원인, 다른 발송기가 있어도 `prod`에는 가짜 발송기 없음, `member`·`local`·`demo`는 가짜 발송기 유지
    - `PublicProfileWebTest`(웹 슬라이스 4, `prod,member`): 시연 사용자 해석기·시연 사용자 API·local/demo CORS·가짜 발송기 빈 없음, `/api/v1/demo-users` 404, 시연 헤더만 보낸 `/api/v1/me` 401, 교차 origin preflight에 `Access-Control-Allow-*` 없음. CODEF·전달 헤더는 #187(L4·L5)이 맡아 중복하지 않았습니다.
    - `PublicProfileIntegrationTest`(통합 4, `prod,member`, 테스트 전용 메일 발송기 `@TestConfiguration`): Flyway V1~V14 전부 성공, 가입 → 인증 → 로그인 → `/me` 200, 시연 장부 도달 불가(헤더만 401, 회원 세션에 붙여도 무시), `simulateFailure: true` 400·결제/원장/멱등 키 0건, 생략 201 승인, 본인 주문 목록 200
  - 문서: `docs/domains/auth.md`(가짜 발송기 범위), `docs/architecture.md`(#185가 더한 `common.config` 절의 prod 메일 상태)
- 하지 않은 것: 실메일 구현·의존성, 서버·compose 반영, frontend·Android.
- 결정 33 컨텍스트 캐시(maxSize=4)에 `prod,member` 통합 컨텍스트가 1개 늘어납니다. 메모리 관측은 아래 검증 기록에 적습니다.

## 2026-11-25 · #185 병합 뒤 develop 동기화
- `origin/develop`(`875611d`)을 merge했습니다(rebase 없음). 충돌은 `docs/architecture.md`의 `common.config` 현재 범위 문장(develop 문장에 이번 prod 메일 상태를 반영)과 progress README 목록입니다.
- `docs/architecture.md`·progress README는 PR #187(L4·L5)과도 겹칩니다. #187이 먼저 병합되면 다시 동기화가 필요할 수 있습니다.
