# #136 F1: GET /me codefConnected 실제 연결 반영, CODEF 키 이름 안내, DemoUserIntegrationTest 간헐 실패 조사

브랜치 `fix/136-codef-connected`, 시작 기준 `origin/develop` `94f655a`.

## 2026-11-18 · codefConnected 연결 행 반영, README 키 이름, 간헐 실패 기록 · PR(`develop` 대상, 병합 전)
- 근거: [`docs/api/cards.md`](../../api/cards.md) GET `/api/v1/me`, [`docs/api/codef.md`](../../api/codef.md) 연결 상태 GET(`status` `NOT_CONNECTED`·`CONNECTED`·`RECONNECT_REQUIRED`), C2(#126) 연결 행 규칙, 관리자 F1 지시와 결정 28 답변(A: `.env.example`은 빼고 README만, B: 간헐 실패는 기록만).
- 한 일:
  - `card.application`: 조회 포트 `CodefConnectionLookup`(구현이 없으면 `NONE` = 연결 없음), `UserCardService.summarize`가 상수 `false` 대신 포트 결과를 씁니다. `user` 패키지는 바꾸지 않았습니다(`codefConnected`는 card 패키지의 현재 사용자 응답에 있음).
  - `codef.application`: `CodefConnectionStatusLookup`(연결 행 `existsById`). card는 codef를 참조하지 않으므로 순환이 없습니다(codef → card 의존만).
  - 계약 `docs/api/cards.md`의 "1차에서는 항상 `false`"를 연결 행 기준으로 바꿨습니다. `docs/domains/card.md`·`docs/domains/codef.md`, `docs/architecture.md` codef 줄, `README.md` 환경변수 절(두 키 이름·형식·없을 때 동작)도 갱신했습니다.
- 정한 동작: 연결 행이 있으면 `true`입니다. `RECONNECT_REQUIRED`도 연결 행이 있는 상태(연결 상태 GET의 `status`가 `NOT_CONNECTED`가 아님)이므로 `true`이고, 재연결 필요 여부는 연결 상태 GET의 `reconnectRequired`로 봅니다. 가상 포트폴리오는 연결할 수 없어 항상 `false`입니다.
- `.env.example`(후속): 이 작업 환경의 가드레일("Protect secrets")이 `.env.example` 읽기를 막았고, 우회·권한 변경은 하지 않았습니다(결정 28 A). `BEONE_CODEF_CONNECTED_ID_KEY=`·`BEONE_CODEF_APPROVAL_HMAC_KEY=` 두 줄(빈 값, Base64 32바이트 설명 주석, 실값·예시 키 없음)은 사용자가 직접 넣거나 허용한 뒤 후속으로 반영합니다.

### DemoUserIntegrationTest 간헐 실패 조사(원인 미확정, 기록만)
- 재현: 이 세션에서 두 번 재현됐습니다. PR #131 develop merge 검증 첫 실행과 PR #135(C1) 검증 첫 실행에서 `DemoUserIntegrationTest` 6건만 실패했고, 같은 트리 재실행은 모두 통과했습니다. 두 번 모두 다른 worktree의 integrationTest(Testcontainers)가 같은 Docker에서 동시에 돌고 있었습니다(C1 첫 실행은 평소 약 2분 30초 대신 7분 55초, 시작 시점 Docker에 다른 실행의 Testcontainers 컨테이너 9개·볼륨 8개).
- ① 관측: 실패 시 테스트 출력에서 `HikariPool-1`·`HikariPool-3`·`HikariPool-4`(앞선 테스트 클래스들이 만든, 캐시된 서로 다른 Spring 테스트 컨텍스트의 연결 풀)가 같은 시각에 함께 `No operations allowed after connection closed`를 내고, 새 연결은 `Communications link failure` ← `java.net.ConnectException: Connection refused`로 실패했습니다(풀 `total=0`, 30초 대기 시간 초과). 즉 연결 풀 고갈이 아니라 **캐시된 컨텍스트의 MySQL 컨테이너 자체가 실행 중 사라졌고**, `DemoUserIntegrationTest`는 새 컨테이너를 띄우지 않고 그 캐시 컨텍스트(`HikariPool-4`)를 재사용해 실패했습니다. 테스트 코드나 단언의 문제가 아닙니다.
- 구조: MySQL 컨테이너는 [`TestcontainersConfiguration`](../../../backend/src/test/java/com/beone/api/support/TestcontainersConfiguration.java)의 `@Bean`이라 Spring 테스트 컨텍스트마다 하나입니다. 통합 테스트 22개 클래스가 `@Import`·`@MockitoBean` 차이로 약 18개 컨텍스트를 만들고, 컨텍스트 캐시(기본 최대 32)가 JVM이 끝날 때까지 들고 있어 한 실행에서 MySQL 컨테이너가 최대 약 18개 동시에 떠 있습니다. Docker VM 메모리는 8GB(`docker info` MemTotal 8,319,238,144)입니다. 두 worktree가 겹치면 수십 개가 됩니다.
- ② 가설(둘 다 미확정): (가) Docker VM 메모리 압박으로 오래된 컨테이너의 mysqld가 종료됨. 컨테이너 수와 메모리 한도가 맞아떨어지고 다른 실행과 겹칠 때만 재현됐습니다. 다만 Testcontainers 컨테이너에는 메모리 한도가 없고 실행 중 실제 사용량을 재지 못했으며(실행 중이던 다른 MySQL 8.4 컨테이너는 유휴 약 100MiB), 죽은 컨테이너가 이미 지워져 종료 코드(OOM 137)를 확인하지 못했습니다. (나) 부하가 커서 이 JVM과 Ryuk(Testcontainers 정리 컨테이너)의 연결이 끊겨 Ryuk가 세션 컨테이너를 정리함. 반증할 로그도 확인할 로그도 남아 있지 않습니다.
- ③ 완화 후보(적용하지 않음): `src/test/resources/spring.properties`에 `spring.test.context.cache.maxSize`를 작게(예 4) 두면 오래된 컨텍스트가 닫히며 그 컨테이너도 멈춰 동시 컨테이너 수가 줄어듭니다. 단언을 바꾸지 않고 시간에 의존하지 않습니다. 비용은 다시 쓰이는 컨텍스트(시연 사용자 헤더만 쓰는 기본 `local` 컨텍스트 등)를 새로 만들어 컨테이너 기동·Flyway가 늘어 실행이 느려질 수 있다는 점입니다. 모든 컨텍스트가 컨테이너 하나를 공유하는 방식은 컨텍스트 사이 데이터 격리가 깨질 수 있어 제외했습니다.
- 운영상 완화: 관리자가 앞으로 두 worktree의 integrationTest가 겹치지 않게 조정합니다(결정 28 B). 다시 재현되면 실패 직후 `docker events`·`docker inspect`로 종료 코드와 `OOMKilled`를 먼저 확인하면 가설을 가를 수 있습니다.
- 서비스에서 달라진 점: 내 카드 화면 상단 등 `GET /api/v1/me`를 쓰는 곳이 CODEF 연결 뒤 실제 연결 여부를 받습니다. frontend 변경 없음.
- 검증: `./gradlew test webTest integrationTest build javadoc --rerun-tasks` 결과는 PR 본문에 기록합니다.
- 로컬·테스트 DB만 사용했고 서버 DB 적용·배포는 하지 않았습니다.
