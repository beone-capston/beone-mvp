# #196 CODEF 프로세스를 넘는 호출 상한 카운터 (LP-2, N14)

브랜치 `feat/196-codef-call-budget`, LP-1 브랜치(`feat/193-codef-live-gateway`, PR #195, `92ed605`) 위에 쌓았습니다. codef 패키지가 겹치기 때문입니다. #195가 병합되면 이 PR의 diff는 LP-2 변경만 남습니다.

## 2026-11-25 · 호출 총량 카운터

- 근거: 결정 10(API별 3회), 결정 39-4(N14: 검증 기간에 연결 1회·동기화 1회, API별 최대 3회, 설정 없으면 닫힘 유지), 결정 38-8. 기존 상한은 관측 1회 안에서만 셌습니다.
- 한 일
  - 전송 계층에 `CodefCallBudget`(보내기 직전 API마다 한 번 묻는 경계)과 실패 이유 `BUDGET_EXHAUSTED`를 더했습니다. `CodefClient`가 총량을 받아 관측에 넘기고, 토큰은 발급할 때만, 상품·계정 API는 관측 상한 다음에 묻습니다.
  - `JdbcCodefCallBudget`(`@Profile` codef-live 조건)이 `codef_call_budgets`(V15)에 API·창 시작별 누적을 세고, 조건부 갱신 한 문장으로 원자적으로 확인·증가합니다. 저장소 오류면 거절합니다.
  - `CodefCallBudgetSettings`: `beone.codef.budget.per-api`·`window-start`·`window-end`. 없거나 잘못되거나 창 밖이면 닫힘.
  - `CodefTransportConfiguration`은 총량 빈이 있으면 쓰고 없으면 세지 않습니다(codef-live 밖에는 전송 계층을 쓰는 게이트웨이가 없음).
- **기본값 판단(결정 필요 N14의 반영)**: 코드에 숫자 기본값을 넣지 않았습니다. 설정 누락이 "상한 없음"이나 "임의 숫자"가 되지 않고 호출 0이 되도록 하는 쪽이 안전하다고 봤습니다. 검증 환경 권장값(API별 3)과 설정 예시는 `docs/domains/codef.md` LP-2 절에 적었습니다. 관리자 런북 반영은 관리자 몫입니다.
- 기존 테스트 수정: `CodefLiveMemberIntegrationTest`에 창·API별 3 설정과 `codef_call_budgets` 정리를 더했습니다. `PublicProfileIntegrationTest`(#190)의 마이그레이션 목록 기대값에 15를 더했습니다.
- 하지 않은 것: CODEF 실호출, 실키·환경 비밀 파일, 서버 접속, 비용 상한, 연결·동기화 API의 새 공개 오류 코드(기존 `CODEF_UNAVAILABLE` 유지), frontend·Android.
- 검증
  - `./gradlew test webTest --rerun-tasks` 통과(단위 1472 = #195 1462 + 10, 웹 436, 실패·skip 0).
  - `./gradlew integrationTest --rerun-tasks` 단독 실행: 204건 중 1건 실패(`PublicProfileIntegrationTest`가 마이그레이션 목록을 1~14로 고정). 기대값을 고친 뒤 그 클래스만 다시 실행해 4/4 통과. 나머지 203건은 전체 실행에서 통과(peak 3,798MiB). 실행 전 다른 작업선의 Docker 스모크가 없음을 확인했습니다.
