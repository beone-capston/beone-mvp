# #143 S1B-A: integrationTest 컨텍스트 캐시 상한 4

브랜치 `test/143-integration-cache-limit`, 시작 기준 `origin/develop` `7c192d2`. 결정 33(사용자 직접 승인)에 따른 S1B 명세 A의 적용이다.

## 2026-11-23 · `integrationTest`에만 `spring.test.context.cache.maxSize=4` · PR(`develop` 대상, 병합 전)

### 배경 (S1B 조사 요약)
- `DemoUserIntegrationTest`가 가끔 DB 연결을 얻지 못해 실패했다([#136 기록](136-codef-connected.md)의 간헐 실패 조사).
- S1B 조사(2026-11-18, 관리자 보고서 `S1B-investigation.md`)에서 원인 기제를 확인했다.
  - 통합 테스트 한 번이 Spring 테스트 컨텍스트(테스트용으로 띄운 애플리케이션 설정 묶음) 18개를 만든다.
  - 컨텍스트 캐시의 기본 상한은 32개다. 그래서 18개가 모두 JVM 종료까지 남는다.
  - 컨텍스트마다 [`TestcontainersConfiguration`](../../../backend/src/test/java/com/beone/api/support/TestcontainersConfiguration.java)의 MySQL 컨테이너가 하나씩 붙어 있다. 컨테이너 하나는 약 450MiB를 쓴다.
  - 다른 worktree와 겹치지 않은 단독 실행에서도 MySQL이 17개까지 동시에 떴다. 메모리 합은 7,371MiB(Docker VM 7.75GiB)였다.
  - 커널 OOM(메모리가 부족할 때 커널이 프로세스를 강제 종료하는 기능)이 캐시된 컨테이너 3개를 종료했다(exit 137).
  - 종료된 컨테이너를 쓰던 연결 풀은 앞선 실패와 같은 `No operations allowed after connection closed`를 냈다.
  - `DemoUserIntegrationTest`는 맨 마지막에 오래된 CardProduct 컨텍스트를 다시 쓰는 유일한 클래스여서 피해를 본다. 테스트 코드나 단언의 결함이 아니다.
- 근거가 된 동작(spring-test 7.0.9·Spring Boot 4.1.1 소스 확인):
  - 컨텍스트 캐시는 LRU(가장 오래 쓰지 않은 것부터 내보내는 방식)다.
  - 상한을 넘어 내보낸 컨텍스트는 닫힌다. 컨텍스트가 닫히면 Boot가 그 컨텍스트의 Testcontainers 컨테이너를 멈춘다.

### 바꾼 것
- [`backend/build.gradle`](../../../backend/build.gradle)의 `integrationTest` 태스크에 `systemProperty 'spring.test.context.cache.maxSize', '4'`와 이유 주석을 넣었다.
- 바꾸지 않은 것: 제품 코드·설정, `test`·`webTest` 태스크, `spring.properties`(이 파일은 `test`·`webTest`에도 적용되어 쓰지 않음), `TestcontainersConfiguration`(명세 B인 MySQL 메모리 축소는 적용하지 않음), 테스트 단언.
- 값 4를 고른 이유(결정 33): 실행 중인 deploy 계열 컨테이너 4개가 약 1.9GiB를 쓴다. 테스트 컨테이너에 남는 몫은 VM의 60%까지 약 2.8GiB다. MySQL 4개(약 1.9GiB)는 이 안에 들어간다.
- 효과: 동시에 떠 있는 테스트 MySQL은 최대 4개(+ Ryuk 1개)다. Ryuk는 Testcontainers가 남은 컨테이너를 치우려고 띄우는 정리용 컨테이너다. 통합 테스트 클래스가 늘어도 이 상한은 그대로다.
- 비용: 내보낸 뒤 다시 쓰는 컨텍스트는 새로 만든다. 이번 실행에서는 컨텍스트 18개에 2개가 더 만들어졌다(연결 풀 번호 1~20).
- 서비스에서 달라진 점: 없음. 테스트 실행 설정만 바뀐다. `build`와 CI의 `integrationTest`도 같은 설정을 쓴다.

### 검증 (단독 실행, 다른 worktree의 Gradle·Testcontainers 없음)
- 실행 전 Docker VM은 MemTotal 8,319,238,144B였다. 중단 기준은 실행 중인 전체 컨테이너 메모리 합 4,760MiB(VM의 60%)였다. deploy 4개는 실행 전 1,928MiB였다.

| 단계 | 명령 | 수(클래스) | 실패 | skip | 시간 |
|---|---|---|---|---|---|
| test | `./gradlew test webTest integrationTest --rerun-tasks` | 1372(166) | 0 | 0 | 5.6초 |
| webTest | 같은 실행 | 389(28) | 0 | 0 | 6.3초 |
| integrationTest | 같은 실행 | 145(23) | 0 | 0 | 140.8초 |
| 전체 | 같은 실행 | — | — | — | BUILD SUCCESSFUL 2분 44초 |
| build·javadoc | `./gradlew build javadoc` | `test`·`webTest`·`integrationTest`는 바로 앞 실행과 입력이 같아 UP-TO-DATE(다시 실행하지 않음). `bootJar`·`jar`·`build`·`javadoc` 실행 | 0 | — | BUILD SUCCESSFUL 3초 |

- 감시 결과(5초 간격 `docker stats --no-stream`, `docker events` 조회):
  - OOM 0건. `die exitCode=137` 20건은 모두 앞에 Testcontainers의 `kill`이 있었다. 컨텍스트를 내보내거나 JVM이 끝날 때의 정상 정리다. Ryuk는 exit 0으로 끝났다.
  - 메모리 합 최대값은 3,763MiB로, 기준 4,760MiB보다 낮았다. 테스트 컨테이너 몫은 약 1,835MiB였다.
  - 동시 MySQL은 최대 4개였다(Testcontainers 라벨 기준 5개 = MySQL 4 + Ryuk 1).
  - 가장 느린 클래스는 10.3초였다(`ApplicationIntegrationTest`, `MemberSessionIntegrationTest`). 30초를 넘는 클래스는 없었다.
  - 조사 때 integrationTest는 OOM과 기동 재시도를 겪으며 전체 5분 40초가 걸렸다. 이번에는 전체 2분 44초였다.
- 측정 한계: 표본 간격이 5초에 `docker stats` 시간이 더해져 약 7초다. 그 사이의 순간 최대값은 놓쳤을 수 있다.
- 로컬 Testcontainers만 사용했다. 서버 DB 적용·배포는 하지 않았다.

### 남은 일
- 명세 B(MySQL 메모리 축소)는 적용하지 않는다. 이번 측정에서 필요하다는 근거가 나오지 않았다.
- 두 worktree의 통합 테스트를 동시에 돌리지 않는 운영 규칙(§15 직렬화)은 그대로 유지한다.
