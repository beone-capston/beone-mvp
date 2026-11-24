# #169 member 웹 클라이언트 호출 규칙과 시연 헤더 회귀 테스트 (G4 B1·B2)

브랜치 `docs/169-member-web-client-rules`, 시작 기준 `origin/feat/165-member-ledger` `0692412`(PR #168, 병합 전). #168과 `docs/api.md`를 함께 고치므로 그 위에 쌓았습니다. PR은 #168 병합 뒤 엽니다.

## 2026-11-24 · B1 호출 규칙 문서, B2 회귀 테스트

- 배경: G4 웹·Android 통합 조사(결정 36)의 백엔드 즉시 후보 B1·B2입니다. 프런트 주문·결제 호출이 쿠키를 보내지 않고(M2), CSRF 토큰이 없으며(M3), 시연 헤더로 장부를 고르고(M4), 401·403 분기가 없습니다(M8). 프런트를 고치기 전에 기준이 될 호출 규칙을 현재 구현 그대로 적고, 프런트가 계속 보내는 `VIRTUAL_PORTFOLIO` 헤더의 동작을 테스트로 고정합니다.
- 한 일
  - B1: `docs/api.md` 회원 세션 절에 「웹 클라이언트 호출 규칙(member)」을 더했습니다. 쿠키 포함 호출, 쓰기 전 `GET /api/v1/auth/csrf` 토큰을 `X-CSRF-TOKEN`에 담기, 시연 헤더 무시, 401 `UNAUTHENTICATED`는 재로그인·403 `CSRF_INVALID`는 토큰 재발급 뒤 재시도(검사 순서 401 → 403 → 400 → 404), `Cache-Control: no-store`, 결제 API 미등록(N-G3-2·D8 대기)입니다.
  - 같은 origin 구성은 확정처럼 쓰지 않았습니다. 로컬 Vite 프록시는 결정 37 O2-L의 방향이지만 프런트 설정은 N-FE-PROXY 대기, 회원 웹 화면의 호출 origin(배포 동일 호스트 여부)은 N-G4-ORIGIN 대기로 적었습니다.
  - B2: `MemberOrderRecommendationIntegrationTest`에 1건을 더했습니다(새 컨텍스트 없음). 코드·DB·Flyway는 바꾸지 않았습니다.
- 테스트
  - `virtualPortfolioDemoHeaderOrderIsStoredInMemberMyCardsLedger`: 회원 A가 `X-BeONE-Demo-User: VIRTUAL_PORTFOLIO`를 붙여 `POST /api/v1/me/orders` → 201·no-store, 저장된 행의 장부가 회원 A의 `REAL` 장부(`member_id` A, `demo_user` 없음), 시연 `VIRTUAL_PORTFOLIO`·`MY_CARDS` 장부의 주문 수 불변, 헤더 없이·`MY_CARDS` 헤더로 같은 주문 조회 200.
  - 기대값은 결정 37 D4와 `docs/api.md` 회원 세션 절(시연 헤더 무시)에서 가져왔습니다.
- 검증: `./gradlew test webTest` 통과(단위 1395, 웹 393, 실패·skip 0). `./gradlew integrationTest` 단독 실행 178 통과(`MemberOrderRecommendationIntegrationTest` 9, 새 1건 포함, 실패·skip 0), 컨테이너 메모리 합 최고 3,977MiB(시작 전 배포 컨테이너 4개 약 2,135MiB, 상한 4,760MiB 미도달). 배포 컨테이너 4개는 그대로 유지했습니다.
- 하지 않은 것: 프런트·Android 수정(팀원 조율 F2·F3·F5), N-G4-ORIGIN·N-FE-PROXY·N-G4-RECO·N-G4-ORDERLIST·N-G3-x 결정.
- 남은 것
  - 프런트 호출 방식 전환(N-G4-ORIGIN·N-FE-PROXY 결정 뒤 팀원 조율)
  - 모의 결제 묶음 등록과 결제 호출 규칙(N-G3-2·D8 결정 뒤)
