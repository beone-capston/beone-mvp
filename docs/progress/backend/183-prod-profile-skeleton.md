# #183 공개 프로필 prod 골격과 PublicProfileGuard (G5 L1)

브랜치 `feat/183-prod-profile-skeleton`, 시작 기준 `origin/develop` `0dab978`. PR #177(`feat/161`)·#179(`feat/178`)와 파일이 겹치지 않게 했습니다(`docs/architecture.md`는 두 PR이 고치지 않는 줄에만 한 항목을 더함).

## 2026-11-24 · 프로필 그룹, prod 설정 파일, 조합 가드

- 배경: 결정 38-6(O1)은 공개 전용 구성을 만들고 시연·Fake·강제 실패를 공개에서 막되 회원 소유권·세션·CSRF는 재사용하라고 정했습니다. 결정 38-7(O2)은 웹과 `/api`를 같은 HTTPS 주소로 둡니다. G5 공개 구성 설계(관리 폴더) 5절 L1 범위만 구현했습니다.
- 한 일
  - `application.yml`에 `spring.profiles.group.prod: member`를 더했습니다. `prod`를 켜면 `member`도 켜져 회원 부품을 코드 변경 없이 재사용합니다.
  - `application-prod.yml`(신규): `server.forward-headers-strategy: none`(전달 헤더 미해석), `spring.flyway.clean-disabled: true`(명시). actuator는 `application.yml`의 `health`·`show-details: never`를 그대로 씁니다. 비밀값·새 키는 없습니다.
  - `common/config/PublicProfileGuard`(신규): `CurrentUserModeGuard`와 같은 `BeanFactoryPostProcessor` 방식입니다. `prod` & (`local` | `demo`)이면 실패, `prod`인데 `member`가 꺼져 있으면(그룹 해제) 실패합니다. `Ordered.HIGHEST_PRECEDENCE`로 `CurrentUserModeGuard`보다 먼저 실행해 prod 전용 문구가 보입니다.
  - `MemberApiWebConfiguration` 주석의 "공개 배포 프로필 구성(O1)은 미결" 문장을 prod 재사용 설명으로 바꿨습니다(동작 불변).
- 테스트
  - `PublicProfileGuardTest`(16, 단위, Docker 불필요)
    - `ApplicationContextRunner`(그룹을 펼치지 않음): `prod,local`·`prod,demo`·`prod,member,local`·`prod,member,demo`·`prod,local,demo` 실패(prod 문구), `prod,member` 성공, `prod` 단독(그룹 해제 상태) 실패, `member`·`local`·`demo`·`local,demo`·프로필 없음은 검사하지 않음, `check` 직접 호출
    - `SpringApplication`으로 실제 `application.yml`·`application-prod.yml`을 읽어 `prod` → `member` 활성, `forward-headers-strategy=none`, `clean-disabled=true`, actuator `health`·`never` 확인. `member`만 켜면 prod 설정이 없고, `prod,local`은 prod 문구로 먼저 실패
  - 기존 `CurrentUserModeGuardTest` 8건은 그대로 통과합니다.
- 검증: `./gradlew test webTest` 통과(단위 1411, 웹 393, 실패·skip 0). 통합 테스트는 실행하지 않았습니다. 기존 통합 테스트는 `member`·`local`·`demo`만 켜므로 그룹(`prod`일 때만 펼쳐짐)과 가드(`prod`가 아니면 바로 반환)의 영향이 없습니다.
- 하지 않은 것
  - `FakeAuthMailSender`를 prod에서 빼는 작업(L2). 지금은 prod에서도 가짜 메일 발송기가 켜집니다
  - 실메일 발송기(N1 결정 대기). **prod는 실메일 발송기 없이는 공개 배포할 수 없습니다**
  - DB 주소 기본값·공개 origin 검사(설계 G3·G4, 결정 필요), compose `SPRING_PROFILES_ACTIVE`(L8), 서버·nginx·AWS 반영
  - `docs/decisions.md`(결정 38 반영)·`docs/domains/auth.md`·`docs/architecture.md`의 "공개 프로필 미결" 문장 갱신. #177·#179가 같은 파일·줄을 고치므로 두 PR 병합 뒤 정리합니다
- 남은 것: L2(Fake 메일 차단 웹 슬라이스), L3(prod 통합 테스트), L4·L5(CODEF 미등록·전달 헤더 고정), N1 실메일 결정
