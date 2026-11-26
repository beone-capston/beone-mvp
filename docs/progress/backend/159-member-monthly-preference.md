# #159 member 프로필에 월간 상태·결제 전 설정 등록 (G3a)

브랜치 `feat/159-member-monthly-preference`, 시작 기준 `origin/feat/157-member-session-mycards` `54b956e`(PR #158 head). #158이 `develop`에 병합되기 전이라 PR은 열지 않았습니다. 병합 뒤 `origin/develop`을 merge하고 `develop` 대상 PR을 엽니다.

## 2026-11-24 · `monthlystate`·`preference` 컨트롤러 프로필, 테스트·문서

- 근거: 결정 36·37, 결정 32 Q1(C1 409·미저장 유지)·Q3(결제 설정 변경은 추천 무효화 없음), G3 명세 패키지 6절 1번(PR-G3a, endpoint #1~5).
- 한 일
  - `MonthlyStateController`·`PaymentPreferenceController`의 `@Profile`에 `member`를 더했습니다. 서비스·DB·Flyway는 바꾸지 않았습니다.
  - 세션 401·쓰기 CSRF 403·`Cache-Control: no-store`·검사 순서는 #158의 `/api/v1/me/**` 인터셉터·필터가 처리합니다. 경로별 설정은 더하지 않았습니다.
  - 출처 충돌 선택(C1)은 `member`에서도 항상 409 `SOURCE_CONFLICT_NOT_FOUND`이고 저장하지 않습니다(코드 불변, 테스트로 고정).
- 테스트
  - `MemberApiTestClient`(테스트 지원 클래스): `MemberProfileIntegrationTest`의 회원 준비·로그인·CSRF·호출 도우미를 옮겼습니다. 스프링 빈이 아니라서 컨텍스트 구성에 영향이 없습니다. 정리 함수가 회원 장부의 설정·추천·주문·월간 상태·카드 행까지 외래 키 순서로 지웁니다.
  - `MemberMonthlyStatePreferenceIntegrationTest`(9, `member` 프로필): `MemberProfileIntegrationTest`와 같은 프로필·가변 시계 조합이라 새 통합 컨텍스트가 없습니다.
    - 회원 장부 저장·조회(입력 없는 달은 UNKNOWN)
    - 회원 A·B 분리, 남의 카드와 없는 카드의 404 본문 동일
    - 시연 장부 카드 404, 시연 헤더 무시
    - 쿠키 없음·익명 세션 401(401이 403·400보다 먼저)
    - CSRF 없음·틀림 403과 미저장(403이 본문 400·C1 409보다 먼저)
    - 400이 404보다 먼저
    - C1 409·미저장, 남의 카드는 404
    - 결제 설정 회원별 분리, 새 회원 미설정 표시
    - 결제 설정 변경 뒤 추천 유효(Q3), 월간 상태 재입력은 그 회원의 추천만 `MONTHLY_STATE_CHANGED`. 추천 API는 아직 `member`에 없어 추천은 서비스로 직접 만들었습니다.
  - `MemberProfileIntegrationTest`: 도우미를 쓰도록 바꿨습니다. 미등록 경로 목록에서 월간 상태·결제 설정을 뺐고, CODEF 4개(POST·DELETE 연결 추가)와 `/demo-users`는 남겼습니다.
  - 기대값은 `docs/api/monthly-states.md`·`preferences.md`·`recommendations.md`, 결정 32·37에서 가져왔습니다. 첫 실행에서 모두 통과해 고친 기대값은 없습니다.
- 검증: `./gradlew test webTest` 통과(단위 1391, 웹 393). `./gradlew integrationTest` 단독 실행 통과(164). 실행 중 컨테이너 메모리 합 최고 3,960MiB(시작 전 배포 컨테이너 4개 약 2,119MiB).
- 문서: `docs/api.md` 회원 세션 절, `docs/api/README.md`, `monthly-states.md`·`preferences.md` 프로필 문장, 도메인 `monthlystate.md`·`preference.md`, `docs/decisions.md` 결정 37 구현 범위. 낡은 "보유 카드까지만" 문장을 `docs/domains/user.md`·`auth.md`, `docs/api/auth.md`에서 고쳤습니다. D8 표기는 바꾸지 않았습니다(N-G3-3 대기).
- 남은 것
  - G3b(주문·예정 소비·추천), G3c(모의 결제·원장·`simulateFailure`)
  - #158 병합 뒤 `origin/develop` merge와 PR
