# 백엔드 배포 기반 (Docker 이미지·배포 Compose)

백엔드(Spring Boot)를 컨테이너 이미지로 만들고, 배포용 Compose로 MySQL과 내부에서 연결하는 방법입니다. 결정 근거는 [D-25](../decisions.md#d-25-확정-정책)입니다.

## 범위와 현재 상태

- 구현된 것: 이미지 빌드 정의, 배포 Compose, 로컬 검증 스크립트, 이 절차 문서.
- 하지 않는 것: `/api`·`/actuator`의 외부 공개(nginx 프록시), 회원 인증·업무 API, 시점 복구(binlog 재생), CI/CD·레지스트리.
- 서버 적용은 사용자가 직접 합니다. 이 문서의 서버 절차는 아직 실제 서버에서 실행·검증되지 않았습니다.
- 이미지를 서버로 옮기는 경로(전달 경로)는 미정입니다. 현재 접속 방식(Session Manager 셸)만으로는 파일을 옮길 수 없어, 별도 결정이 필요합니다.

## 용어

- **Compose 프로젝트**: `docker compose -p <이름>`으로 묶이는 컨테이너·볼륨·네트워크 단위입니다. 이름이 다르면 서로의 볼륨을 쓰지 않습니다.
- **볼륨**: 컨테이너를 지우거나 다시 만들어도 남는 데이터 저장 공간입니다. MySQL 데이터가 여기에 있습니다.
- **Flyway**: 앱이 시작할 때 `backend/src/main/resources/db/migration`의 SQL을 순서대로 적용하고 `flyway_schema_history` 표에 기록하는 도구입니다. 한 번 적용한 것은 다시 적용하지 않고, 되돌리지도 않습니다.
- **binlog**: MySQL의 변경 기록 파일입니다. 이 배포에서는 켜 두고 3일만 보관합니다(2026-11-15 사용자 결정). 지금은 binlog로 특정 시점까지 되돌리는 절차를 만들지 않았고, 백업은 덤프(mysqldump)로만 합니다.

## 구성

```text
서버 /opt/beone
├── compose.yaml        기존. 프로젝트 beone, 서비스 nginx(80·443). 이 문서의 명령 대상이 아님
├── nginx/ certbot/     기존. HTTPS·인증서 자동 갱신(beone-certbot-renew.timer)이 사용
└── app/                새로 만듦. 프로젝트 beone-app
    ├── compose.yaml    저장소 deploy/compose.yaml 사본
    ├── .env            서버에서 직접 작성, 권한 600
    └── backups/        덤프 보관, 권한 700
```

| 저장소 파일 | 역할 |
| --- | --- |
| [`backend/Dockerfile`](../../backend/Dockerfile) | 실행 이미지. 빌드 단계는 빌드 장비 플랫폼, 실행 단계는 linux/amd64. root가 아닌 `beone` 사용자로 실행 |
| [`backend/.dockerignore`](../../backend/.dockerignore) | 빌드 컨텍스트에 wrapper·빌드 스크립트·`src/main`만 허용 |
| [`deploy/compose.yaml`](../../deploy/compose.yaml) | MySQL 8.4.11 + backend. 호스트 포트 공개 없음, 필수 값이 없으면 실패 |
| [`deploy/scripts/build-image.sh`](../../deploy/scripts/build-image.sh) | 이미지 빌드와 식별자 출력 |
| [`deploy/scripts/verify-local.sh`](../../deploy/scripts/verify-local.sh) | 로컬의 분리된 검증 프로젝트에서 V2~V13 확인 |
| [`deploy/verify/`](../../deploy/verify/RUNBOOK.md) | 서버 루프백 검증 환경 `beone-verify`(member 프로필, `127.0.0.1:18080`, 결정 39-1)의 compose·nginx·키 이름 템플릿·런북. 운영 `beone-app`과 분리된 프로젝트 |
| [`deploy/scripts/smoke-member.sh`](../../deploy/scripts/smoke-member.sh) | `member` 프로필 회원 API 스모크(합성 회원 2명, S1~S18). `<BASE_URL>`을 받아 PASS·FAIL 표를 내고 실패하면 종료 코드 1. 이메일 인증 단계는 검증 DB·합성 회원 한정으로 인증 완료 상태를 만든 뒤 계속한다(결정 39-1 T-a, `SMOKE_VERIFY_HOOK` 또는 안내 SQL). 월은 서울 기준 이번 달만 지원한다(서버가 주문일을 이번 달만 받음). 주문일은 오늘, 월간 상태의 전월 실적 기간은 전월 1일~말일로 함께 유도하고, 다른 `SMOKE_MONTH`나 형식이 틀린 값은 요청 전에 종료 코드 2로 멈춘다. S1은 200과 `text/html`, S5·로그인은 마스킹 전 원문에서 이름이 정확히 `__Host-BEONE_SESSION`이고 값이 비어 있지 않은 쿠키 줄 하나를 찾아 정확한 경계로 속성을 본다( `Path=/`, `Secure`, `HttpOnly`, `SameSite=Lax`, Domain 없음. 다른 쿠키 줄의 속성은 합치지 않음). S17은 양쪽 200·유효 세션 쿠키를 먼저 요구한 뒤 속성 동일·Location 없음을 본다. 출력하는 쿠키는 값을 가린다(세미콜론 없는 값 포함). S11b는 `changed` 필드 존재와 JSON null, S16a는 기준(헤더 없는 A `/me`)·비교(시연 헤더) 응답 각각의 필수 필드 존재·타입(`demoUser`는 키 있음·null, `displayName`·`portfolioType` 문자열, `readOnly`·`codefConnected` 불리언, `cardCount` 숫자)을 먼저 확인한 뒤 같은 장부인지 본문 비교로 판정한다(양쪽에서 같은 필드가 빠져 null로 같아 보여도 FAIL). S12b는 주문 상태 관측일 뿐 결제·멱등·원장 미저장의 증거가 아니다. `--self-test`는 서버 없이 필드 읽기, 쿠키 값 마스킹, 월 유도·지원 밖 월 사전 거부를 확인하고, 가짜 응답을 주입하면 판정이 FAIL·종료 1이 되는지 점검한다. 주입하는 응답은 잘못된 Content-Type, 쿠키 속성 누락, S5 잘못된 Path·여러 줄 분산·SameSite 누락·빈 세션 값, 로그인 빈 세션 값, S17 양쪽/한쪽 오류·쿠키 누락·빈 세션 값·속성 다름·Location, `changed` 누락/비null, 다른 장부, S16a 기준/비교/양쪽 필드 누락·타입 다름이다 |
| [`deploy/scripts/deploy.sh`](../../deploy/scripts/deploy.sh) | 서버 배포 스크립트(결정 40). digest pull → 운영만 배포 직전 덤프·짝 기록 → backend 기동·health → 웹 릴리스 링크 전환 → 공개 스모크([`smoke-public.sh`](../../deploy/scripts/smoke-public.sh)), 실패 시 이전 digest·링크로 복귀. 오프라인 자기검사 [`deploy-selftest.sh`](../../deploy/scripts/deploy-selftest.sh). 절차는 [서버 배포 스크립트](server-deploy.md) |
| [`deploy/staging/`](../../deploy/staging/compose.yaml) | 스테이징(결정 40-4). 프로젝트 `beone-staging`, 별도 DB 볼륨, 127.0.0.1 포트만, 메모리 상한 합계 1344MiB·`oom_score_adj`로 운영보다 먼저 종료, `prod` 프로필+실메일, nginx 기본 인증. 절차는 [스테이징](staging.md) |

- 백엔드는 Docker 내부 이름 `mysql:3306`과 앱 전용 계정(`MYSQL_USER`)으로 접속합니다. root 비밀번호는 MySQL 초기화에만 쓰고 백엔드에 넘기지 않습니다.
- 초기 자원 값은 제안값입니다: MySQL·backend 각각 메모리 1GiB, JVM 힙은 컨테이너 메모리의 70%, InnoDB 버퍼 256MiB, 로그는 10MB×3개 회전. 실제 서버(4GiB)에서의 적합성은 적용 후 확인합니다.
- `application.yml`은 바꾸지 않았습니다. 배포 값은 Compose의 환경변수로 넣습니다.

## 환경 파일

서버의 `/opt/beone/app/.env`에 다음 키를 둡니다. 값은 서버에서 만들고 출력하지 않습니다. 값이 빈 예시는 [`deploy/.env.example`](../../deploy/.env.example)입니다.

| 키 | 설명 |
| --- | --- |
| `MYSQL_DATABASE` | 앱 DB 이름(예: `beone`) |
| `MYSQL_USER` | 앱 전용 계정 이름(root 아님) |
| `MYSQL_PASSWORD` | 앱 계정 비밀번호 |
| `MYSQL_ROOT_PASSWORD` | MySQL root 비밀번호. 초기화 전용 |
| `BEONE_API_IMAGE` | `docker load`로 들여온 이미지 태그(예: `beone-api:<커밋 12자리>`) |

**비밀번호 형식 계약:** `MYSQL_PASSWORD`·`MYSQL_ROOT_PASSWORD`는 비어 있지 않은 16진수(`0-9`, `a-f`, `A-F`)만 씁니다. 정상 생성 방법은 `openssl rand -hex 24`입니다. 백업·복원·이력 조회 명령은 비밀번호를 옵션 파일(`[client]` 아래 `password=`)에 그대로 넣기 때문에 `#`·따옴표·공백 같은 문자는 받지 않습니다.

사용 전에 값을 출력하지 않고 형식만 확인합니다. 두 줄 모두 `정상`이어야 합니다. `비어 있음`·`16진수가 아닌 문자 있음`·`키 없음`이면 멈추고, 그 키만 `openssl rand -hex 24` 값으로 직접 바꿉니다(첫 기동 이후라면 DB 계정 비밀번호도 함께 바꿔야 하므로 별도 판단).

```bash
sudo awk 'BEGIN { need["MYSQL_PASSWORD"]; need["MYSQL_ROOT_PASSWORD"] } { i = index($0, "="); k = substr($0, 1, i - 1); v = substr($0, i + 1) } (i > 0) && (k in need) { r[k] = (v == "" ? "비어 있음" : (v ~ /^[0-9A-Fa-f]+$/ ? "정상" : "16진수가 아닌 문자 있음")) } END { for (k in need) print k ": " ((k in r) ? r[k] : "키 없음") }' /opt/beone/app/.env
```

값이 비어 있으면 `docker compose`가 바로 실패합니다. `docker compose config`를 옵션 없이 실행하면 비밀값이 풀려 출력되므로 `--quiet` 또는 `--services`로만 확인합니다.

### 공개 프로필 `prod`의 메일 키 (아직 Compose에 넣지 않음)

지금 Compose는 `prod` 프로필을 켜지 않으며, 아래 키를 backend 컨테이너에 넘기지도 않습니다(프로필 지정·서버 반영은 별도 작업). `prod`를 켤 때는 다음 키가 모두 있어야 하고, 하나라도 없으면 백엔드가 키 이름만 적은 메시지와 함께 기동하지 않습니다(결정 39-6, #207). 앱 비밀번호는 서버 `.env`에만 두고 출력하지 않습니다.

| 키 | 설명 |
| --- | --- |
| `SPRING_MAIL_HOST` | SMTP 호스트(예: Gmail `smtp.gmail.com`) |
| `SPRING_MAIL_PORT` | STARTTLS 포트(예: `587`). `prod`는 STARTTLS 필수 |
| `SPRING_MAIL_USERNAME` | SMTP 계정 |
| `SPRING_MAIL_PASSWORD` | SMTP 앱 비밀번호. 비밀값 |
| `BEONE_MAIL_FROM` | 발신 주소 |
| `BEONE_MAIL_LINK_BASE_URL` | 메일 링크 기준 주소(`https://`로 시작). 권장값은 웹과 `/api`를 함께 두는 공개 HTTPS origin이며 값은 N2 결정 대기 |

## 이미지 빌드와 식별

이미지는 빌드 장비에서 만들고 서버에서는 빌드하지 않습니다(서버 메모리·CPU 크레딧 보호).

1. 커밋 전 검증: `deploy/scripts/build-image.sh uncommitted`
   - 태그 `beone-api:verify-<기준12자리>-uncommitted-<UTC시각>`, 라벨 `beone.source.state=uncommitted`. 소스 리비전 라벨은 붙이지 않습니다. 이 이미지는 배포하지 않습니다.
2. 커밋 후 최종 빌드: `deploy/scripts/build-image.sh final <tar 경로>`
   - 작업 트리가 깨끗할 때만 빌드합니다. 태그 `beone-api:<커밋 12자리>`, 라벨 `org.opencontainers.image.revision=<전체 SHA>`.
   - 출력되는 `image_id`, `platform`, `tar_sha256`을 배포 기록에 남깁니다.

## 로컬 검증

기존 개발 DB(`docker-compose.yml`, 컨테이너 `beone-mysql`)와 분리된 프로젝트 `beone-deploy-verify`·`beone-deploy-restore`를 씁니다. 검증 전용 스키마 `verify_persist`와 계정 `verify_backup`은 이 스크립트에서만 만들고 업무 Flyway·시드에는 넣지 않습니다.

```bash
export VERIFY_ENV=<저장소 밖 폴더>/verify.env
deploy/scripts/verify-local.sh init-env
export BEONE_API_IMAGE=<검증할 이미지 태그>
deploy/scripts/verify-local.sh static
deploy/scripts/verify-local.sh image-check
deploy/scripts/verify-local.sh up
deploy/scripts/verify-local.sh persist
deploy/scripts/verify-local.sh backup
deploy/scripts/verify-local.sh swap <태그A> <태그B>
```

| 단계 | 확인 내용 |
| --- | --- |
| V1 | `cd backend && ./gradlew test webTest integrationTest` |
| V2 | Compose 문법, 서비스가 mysql·backend뿐인지, 포트 공개 선언 0건, MySQL 태그가 로컬 Compose와 같은지, 이미지 플랫폼·실행 사용자·포함 파일·빌드 이력 |
| V4 | MySQL healthcheck |
| V5 | 앱 계정 연결(`CURRENT_USER()`), 권한이 앱 DB로 한정되는지, binlog 켜짐·보관 259200초 |
| V6 | 백엔드 기동 로그 |
| V7 | DB 연결 풀 시작, 연결 오류 없음 |
| V8 | Flyway V1 적용 로그와 `flyway_schema_history` 성공 행 |
| V9 | 컨테이너 안에서 `/actuator/health`의 `status`가 `UP`이고 구성 요소 세부 정보(`components`·`details`)가 없음. Spring Boot 4는 그룹 이름 목록(`groups`)을 함께 보냄 |
| V10 | 실제 컨테이너의 호스트 포트 바인딩이 없음 |
| V11 | 컨테이너 재생성 후 검증 표식 행과 Flyway 이력 유지, Flyway 재적용 없음 |
| V12a | 앱 계정으로 앱 DB 덤프 → 복원 프로젝트에 복원 → Flyway 이력 일치, 그 DB로 백엔드 기동 |
| V12b | 검증 전용 계정으로 `verify_persist` 덤프·복원 → 행·값 일치 |
| V12 | 실행 중 프로세스 명령행·덤프·Compose 로그에 비밀번호 문자열 0건 |
| V13a | 같은 이미지 ID에 다른 태그로 전환·재기동(절차 확인) |
| V13b | 이미지 ID가 다른 두 이미지 사이 전환·재기동 |

- health의 `UP`은 그 시점에 앱과 DB 지표가 정상이라는 뜻입니다. 계정 권한·Flyway·영속성은 V5·V8·V11에서 따로 확인합니다.
- 로컬이 arm64면 amd64 이미지는 에뮬레이션으로 실행됩니다. 동작 확인은 되지만 속도·메모리 수치는 서버를 대표하지 않습니다. 서버의 네이티브 실행 확인은 적용 후 따로 합니다.
- V13은 버전 롤백 검증이 아닙니다. 최초 배포에는 이전 릴리스가 없어 실제 버전 롤백은 두 번째 릴리스부터 확인할 수 있습니다.
- 검증 볼륨은 스크립트가 지우지 않습니다. 지울 때는 프로젝트 이름을 확인하고 따로 판단합니다.

## 서버 적용 절차 (사용자 실행, 미검증)

모든 앱 명령은 파일·프로젝트·환경 파일을 명시합니다. `/opt/beone`에서 `-f` 없이 `docker compose`를 실행하지 않습니다. 기존 프로젝트 `beone`은 어느 단계에서도 명령 대상이 아닙니다.

먼저 셸에서 운영 프로젝트용 `APP` 함수를 정의합니다. 이후의 `APP …` 명령은 모두 이 함수를 실행합니다. 대상은 파일 `/opt/beone/app/compose.yaml`, 프로젝트 `beone-app`, 환경 파일 `/opt/beone/app/.env`로 고정입니다. 새 셸을 열면 다시 정의합니다.

```bash
APP() { sudo docker compose -f /opt/beone/app/compose.yaml -p beone-app --env-file /opt/beone/app/.env "$@"; }
```

### 1. 사전 대조 (읽기만)

| 명령 | 기대 | 다르면 |
| --- | --- | --- |
| `sudo docker compose ls` | `beone`만 running | 멈추고 확인 |
| `systemctl list-timers beone-certbot-renew.timer` | 다음 실행 시각 표시 | 멈추고 확인 |
| `systemctl cat beone-certbot-renew.service` | `/opt/beone/compose.yaml`과 `nginx` 참조를 눈으로 확인(출력은 공유하지 않아도 됨) | 멈추고 확인 |
| `df -h /` · `free -h` · `sudo docker system df` | 남은 디스크·메모리 확인 | 여유가 부족하면 멈춤 |
| `curl -sI https://beonepay.site \| head -1` | `200` | 멈춤 |
| `curl -sI http://beonepay.site \| head -1` | `301` | 멈춤 |

### 2. 기존 설정 보존

```bash
sudo install -d -m 700 /opt/beone-backup
sudo tar -czf /opt/beone-backup/pre-app-$(date -u +%Y%m%dT%H%M%SZ).tgz /opt/beone/compose.yaml /opt/beone/nginx/conf.d
sudo ls -l /opt/beone-backup
```

certbot 폴더에는 개인키가 있으므로 복사·전송하지 않고 그대로 둡니다.

### 3. 배치

```bash
sudo install -d -m 750 /opt/beone/app
sudo install -d -m 700 /opt/beone/app/backups
```

- 저장소의 `deploy/compose.yaml`을 `/opt/beone/app/compose.yaml`로 둡니다.
- `/opt/beone/app/.env`를 직접 만들고 `sudo chmod 600 /opt/beone/app/.env`로 권한을 줄입니다. 비밀번호는 `openssl rand -hex 24`로 만들고, [환경 파일](#환경-파일)의 형식 검사에서 두 키가 모두 `정상`인지 확인합니다.
- 이미지 tar를 서버로 옮긴 뒤(전달 경로 미정) 다음을 실행합니다.

| 명령 | 기대 | 다르면 |
| --- | --- | --- |
| `sha256sum <tar>` | 빌드 기록의 `tar_sha256`과 같음 | 멈춤 |
| `sudo docker load --input <tar>` | `Loaded image: beone-api:<12자리>` | 멈춤 |
| `sudo docker image inspect --format '{{.Id}} {{.Os}}/{{.Architecture}}' beone-api:<12자리>` | 빌드 기록의 `image_id`, `linux/amd64` | 멈춤 |
| `APP config --services` | `mysql`, `backend` 두 줄만 | `nginx`가 보이면 즉시 멈춤 |
| `APP config --quiet` | 출력 없음 | 값 누락. `.env` 확인 |

### 4. 기동과 내부 확인

| 명령 | 기대 | 다르면 |
| --- | --- | --- |
| `APP up -d mysql` | 컨테이너 생성 | 멈춤 |
| `APP ps mysql` | 1~2분 뒤 `healthy` | `APP logs mysql --tail 50` 확인 후 멈춤 |
| `APP up -d backend` | 컨테이너 생성 | 멈춤 |
| `APP ps backend` | 수 분 안에 `healthy` | `APP stop backend` 후 로그 확인 |
| `APP logs backend \| grep -E 'Started BeoneApiApplication\|Successfully applied\|is up to date'` | 기동·Flyway 로그 | `APP stop backend` |
| `APP exec backend bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /actuator/health HTTP/1.0\r\n\r\n" >&3 && cat <&3' \| tail -1` | `"status":"UP"`이 있고 `components`·`details`가 없음 | `APP stop backend` |
| `sudo docker port beone-app-backend-1; sudo docker port beone-app-mysql-1` | 출력 없음 | `APP down` 후 compose 파일 확인 |

### 5. 기존 기능 유지 확인

| 명령 | 기대 |
| --- | --- |
| `curl -sI https://beonepay.site \| head -1` | `200`, 정적 화면 그대로 |
| `curl -sI http://beonepay.site \| head -1` | `301` |
| `curl -s -o /dev/null -w '%{http_code}\n' https://beonepay.site/actuator/health` | 앱 응답이 아님(기존 nginx 응답, 예: 404) |
| `systemctl list-timers beone-certbot-renew.timer` | 다음 실행 시각 유지 |
| 외부(다른 네트워크)에서 서버 IP의 3306·8080 접속 시도 | 연결 안 됨. 보안 그룹은 이 절차로 바꾸지 않음 |

인증서 갱신 dry-run은 기존 service 정의를 확인한 뒤에만 실행합니다. 확인하지 못했다면 미실행으로 기록합니다.

### 6. 재시작 확인

`APP restart backend` 뒤 `APP ps backend`가 다시 `healthy`인지 봅니다. 서버 재부팅 뒤에는 `restart: unless-stopped`로 두 서비스가 다시 뜨는지 확인합니다.

## 백업과 복원

### 백업

**배포 금지 구간:** 덤프를 시작하기 전부터 아래 짝 기록 블록이 `짝 기록 확인`을 출력할 때까지 새 backend 배포(`APP up -d backend`, 이때 Flyway 마이그레이션이 실행됨)·마이그레이션·수동 DDL(`CREATE`·`ALTER`·`DROP`·`RENAME`·`TRUNCATE`)을 실행하지 않습니다. 운영 서비스를 멈추거나 일반 DB 쓰기를 막지는 않습니다. 기록이 실패하면 배포를 보류합니다.

앱 계정으로 앱 DB를 덤프합니다. 비밀번호는 명령행에 넣지 않고 컨테이너 안에서 옵션 파일로 전달합니다.

- 백업마다 `mktemp -d`로 **전용 폴더**(`/opt/beone/app/backups/app-<UTC 시각>-<무작위>`, 권한 700)를 새로 만들고, 덤프(`app.sql`)와 짝 기록을 그 안에 둡니다. 같은 시각에 여러 번 실행해도 폴더가 겹치지 않습니다. 폴더를 만들지 못하면 중단하며, 이전 `B`·`DUMP_OK`는 지운 상태로 남아 다시 쓰이지 않습니다.
- 덤프는 먼저 `app.sql.partial`에 씁니다. 이 파일은 이미 있으면 만들지 않으므로(`noclobber`) 이번 실행이 만든 파일만 다룹니다. 명령이 모두 성공하고 마지막 줄에 덤프 완료 표식이 있으면 `mv -n`으로 `app.sql`로 바꾸고, `.partial`이 사라지고 `app.sql`이 생긴 것까지 확인해야 성공입니다(`mv -n`이 건너뛰고 0을 돌려줘도 성공으로 보지 않음). 기존 `app.sql`은 덮어쓰지 않습니다.
- 실패하면 이번 실행이 만든 `.partial`만 지우고 "백업 실패"를 출력합니다. 다른 실행의 파일·정상 덤프·기록은 건드리지 않습니다. 빈 백업 폴더는 남습니다. 파일 권한은 `umask 077`로 600입니다.

```bash
unset DUMP_OK B BD
BD=$(sudo mktemp -d "/opt/beone/app/backups/app-$(date -u +%Y%m%dT%H%M%SZ)-XXXXXX") || BD=
if [ -z "$BD" ]; then echo "백업 중단: 백업 폴더를 만들지 못했습니다(이전 B·DUMP_OK는 쓰지 않음). 배포를 보류합니다"
else
B="$BD/app.sql"
sudo bash -s -- "$B" <<'EOF' && { DUMP_OK="$B"; echo "백업 완료: $B"; } || echo "백업 실패: 이번 실행의 .partial만 지웠습니다. 배포를 보류합니다"
set -o pipefail -o noclobber
umask 077
B="$1"
[ ! -e "$B" ] || { echo "중단: $B 가 이미 있습니다(덮어쓰지 않음)"; exit 1; }
: > "$B.partial" || { echo "중단: $B.partial 이 이미 있습니다(이번 실행 파일 아님)"; exit 1; }
if docker compose -f /opt/beone/app/compose.yaml -p beone-app --env-file /opt/beone/app/.env exec -T mysql bash -c 'mysqldump --defaults-extra-file=<(printf "[client]\nuser=%s\npassword=%s\n" "$MYSQL_USER" "$MYSQL_PASSWORD") --single-transaction --no-tablespaces "$MYSQL_DATABASE"' < /dev/null >| "$B.partial" \
	&& tail -n 1 "$B.partial" | grep -q "^-- Dump completed" \
	&& mv -n "$B.partial" "$B" && [ ! -e "$B.partial" ] && [ -f "$B" ]; then
	exit 0
fi
rm -f "$B.partial"
exit 1
EOF
fi
```

| 확인 | 기대 | 다르면 |
| --- | --- | --- |
| 위 명령 출력 | `백업 완료: …/app.sql` | "백업 실패"·"백업 중단"이면 배포를 보류하고 MySQL 상태(`APP ps mysql`)를 본 뒤 이 블록부터 다시 실행 |
| `sudo ls -la "$BD"` | 폴더 `drwx------`, `app.sql` 권한 `-rw-------`, `.partial` 없음 | 멈추고 확인 |

#### 덤프 짝 기록

덤프 명령과 **같은 셸**에서 바로 이어서 덤프와 짝지어진 기록을 남깁니다. 이번 덤프 명령이 성공해 `DUMP_OK`가 이번 `B`와 같을 때만 기록을 시작합니다. 파일이 있다는 것만으로 이번 덤프가 성공했다고 보지 않습니다. 복원 확인에서 **덤프 당시 이미지**로 backend를 기동하고 **백업 시점 이력**과 비교하는 데 씁니다. 기록은 덤프와 같은 백업 폴더(`$BD`, 권한 700) 안에 권한 600으로 남깁니다.

- `app.sql.record`: 덤프 파일 이름·sha256, 실제 실행 중인 backend 컨테이너의 이미지 ID·태그·소스 revision 라벨, 이력 파일 sha256
- `app.sql.flyway`: 백업 시점 `flyway_schema_history`(`installed_rank, version, script, checksum, success`)

`.env`에 적힌 태그를 그대로 쓰지 않고, 실행 중인 backend 컨테이너의 이미지 ID를 기준으로 그 태그가 같은 ID를 가리키는지 대조합니다. 비밀번호·환경 파일 내용은 기록하거나 출력하지 않습니다.

```bash
if [ -n "${B:-}" ] && [ "${DUMP_OK:-}" = "$B" ]; then
sudo bash -s -- "$B" <<'EOF' && echo "짝 기록 확인: 배포 금지 구간을 끝낼 수 있습니다" || echo "짝 기록 실패: 배포를 보류합니다(덤프는 남김)"
set -euo pipefail
umask 077
B="$1"
APP() { docker compose -f /opt/beone/app/compose.yaml -p beone-app --env-file /opt/beone/app/.env "$@"; }
[ -f "$B" ] || { echo "기록 중단: 덤프 파일이 없습니다"; exit 1; }
[ ! -e "$B.record" ] || { echo "기록 중단: 기록 파일이 이미 있습니다(덮어쓰지 않음)"; exit 1; }
cid="$(APP ps -q backend)"
[ -n "$cid" ] || { echo "기록 불완전: 실행 중인 backend가 없습니다"; exit 1; }
image_id="$(docker inspect --format '{{.Image}}' "$cid")"
image_tag="$(docker inspect --format '{{.Config.Image}}' "$cid")"
[ "$(docker image inspect --format '{{.Id}}' "$image_tag")" = "$image_id" ] || { echo "기록 불완전: 태그 $image_tag 가 실행 중 이미지 ID와 다릅니다"; exit 1; }
revision="$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$image_id")"
case "$revision" in "" | "<no value>") echo "기록 불완전: 이미지에 소스 revision 라벨이 없습니다"; exit 1 ;; esac
APP exec -T mysql bash -c 'mysql --defaults-extra-file=<(printf "[client]\nuser=%s\npassword=%s\n" "$MYSQL_USER" "$MYSQL_PASSWORD") -N -B -e "SELECT installed_rank, version, script, checksum, success FROM flyway_schema_history ORDER BY installed_rank" "$MYSQL_DATABASE"' < /dev/null > "$B.flyway.partial"
[ -s "$B.flyway.partial" ] || { echo "기록 불완전: Flyway 이력을 읽지 못했습니다"; exit 1; }
{
	echo "dump_file=$(basename "$B")"
	echo "dump_sha256=$(sha256sum "$B" | cut -d' ' -f1)"
	echo "image_id=$image_id"
	echo "image_tag=$image_tag"
	echo "image_revision=$revision"
	echo "flyway_sha256=$(sha256sum "$B.flyway.partial" | cut -d' ' -f1)"
} > "$B.record.partial"
mv "$B.flyway.partial" "$B.flyway"
mv "$B.record.partial" "$B.record"
echo "기록 완료: $B.record ($image_tag)"
EOF
else echo "기록 미시작: 이번 덤프의 성공이 확인되지 않았습니다. 배포를 보류하고 덤프부터 다시 합니다"; fi
```

- 이 블록은 스크립트를 표준 입력으로 읽으므로, 안의 `APP exec`는 `< /dev/null`로 표준 입력을 막아 스크립트 나머지를 읽어 버리지 않게 합니다.
- `백업 완료`(SQL 덤프 성공)와 `짝 기록 확인`(복원 기동 검증에 필요한 짝 기록 완성)은 따로 판정합니다. 배포는 `짝 기록 확인`이 나온 뒤에만 시작합니다.
- `짝 기록 실패`(`기록 불완전`·`기록 중단`)면 정상 덤프는 지우지 않고 배포를 보류합니다. 출력된 원인(실행 중인 backend 없음, 태그·ID 불일치, revision 라벨 없음, 이력 조회 실패 등)을 해결한 뒤, 그 사이 배포·DDL이 없었다면 같은 셸에서 이 블록을 다시 실행해 같은 덤프의 짝 기록을 확보합니다. 그 사이 변경이 없었다고 확신할 수 없으면 덤프부터 다시 합니다. 짝 기록 없는 덤프는 DB 반입·이력 조회 확인에만 쓰고 앱 기동 검증에는 쓰지 않으며, 남은 `.partial` 기록 파일도 쓰지 않습니다.
- 기록이 없는 기존 덤프의 이미지·이력 정보는 추정해서 채우지 않습니다.
- 덤프 이후의 변경은 다음 덤프 전까지 덤프로 복구할 수 없습니다. binlog는 3일 보관하지만 binlog 재생 절차는 아직 없습니다.
- 배포·마이그레이션 전에는 반드시 덤프를 하나 남깁니다. 정기 덤프 주기와 자동화는 미정입니다.
- **순서:** 덤프 → `백업 완료`와 파일 확인 → 짝 기록 → `짝 기록 확인`이 모두 끝난 뒤에만 새 backend 배포·마이그레이션·수동 DDL을 시작합니다(위의 배포 금지 구간).
- `--single-transaction`은 InnoDB 테이블을 한 시점의 일관된 상태로 읽게 할 뿐, 다른 세션의 DDL 동시 실행은 막지 않습니다. 덤프 중 DDL이 실행되면 덤프가 실패하거나 테이블 구조와 데이터가 어긋난 덤프가 남을 수 있습니다.

### 복원 확인 (회차별 분리 프로젝트)

복원은 운영 프로젝트(`beone-app`)에 하지 않습니다. 같은 Compose 파일을 **복원 회차마다 새 프로젝트**(`beone-app-restore-<UTC 시각>`)로 띄워, 그 프로젝트 전용의 새 볼륨에 덤프를 넣습니다. 기존 DB를 비우거나 볼륨을 지우는 방식은 쓰지 않으며, 이미 있는 프로젝트·볼륨은 다시 쓰지 않습니다. 운영 DB에는 쓰지 않고(이력 조회만 참고로 함), 운영 `.env`도 바꾸지 않습니다. 운영 DB를 덤프로 바꾸는 일은 이 확인이 끝난 뒤의 별도 결정이며 이 문서에 명령을 두지 않습니다.

backend는 다음 네 조건을 **모두** 만족할 때만 기동합니다. ① 현재 회차 사전 검사 통과(2단계) ② 선택한 덤프의 반입 성공(5단계) ③ 백업 시점 이력 일치(6단계) ④ 덤프와 짝지어진 이미지 확인(3단계). ②~④는 현재 `RP`와 덤프 `D`에 묶여 있어 회차나 덤프를 바꾸면 무효가 됩니다. `RESTORE`도 같은 조건을 검사해, 조건을 채우지 못하면 `mysql`만 지정한 기동 외의 기동 명령(서비스 이름을 생략한 `up` 포함)을 막습니다. 멈춤(`stop`)과 진단 조회(`ps`·`logs`·`config`·`exec`)는 언제나 됩니다.

backend는 **덤프와 짝지어진 기록의 이미지**로만 기동합니다. 최신 이미지로 대신 기동하거나 복원 DB에 새 마이그레이션을 적용하지 않습니다. 짝 기록이나 같은 이미지를 확보하지 못하면 DB 반입·이력 조회까지만 하고, 앱 기동·health 검증은 **미실행(미검증)**으로 남깁니다. 이 경우 복원·롤백 전체가 끝났다고 기록하지 않습니다.

아래 명령은 모두 **같은 셸**에서 차례로 실행합니다(이 절만 따로 실행해도 됩니다). 여러 줄을 한 번에 붙여 넣을 때 `exec -T`가 뒤의 줄을 표준 입력으로 읽지 않도록, 조회용 `exec`에는 `< /dev/null`을 붙였습니다.

1. 여유 확인: 회차마다 `<회차>_mysql-data` 볼륨이 남아 디스크(gp3 20GiB)에 쌓입니다. 시작 전에 다음을 보고, 디스크나 메모리 여유가 부족하면 시작하지 않습니다. 남은 복원 볼륨을 지울지는 별도로 판단하며 이 문서에 삭제 명령을 두지 않습니다.

```bash
df -h /; free -h; sudo docker system df; sudo docker volume ls --filter name=beone-app-restore-
```

   복원 프로젝트도 MySQL·backend 각각 최대 1GiB 메모리를 씁니다. 메모리가 부족하면 7단계(backend 기동)는 생략하고 6단계까지만 확인합니다. [환경 파일](#환경-파일)의 비밀번호 형식 검사도 이때 다시 봅니다.

2. 함수 정의와 회차 사전 검사: `APP`(운영, 프로젝트 `beone-app` 고정)와 `RESTORE`(이 회차 `RP`만)를 정의하고, 새 회차 이름에 해당하는 컨테이너·볼륨·네트워크가 없는지 조회합니다. 세 조회가 **모두 성공하고 결과가 없을 때만** `새 회차`가 나오고 `RESTORE`가 열립니다. 조회 실패나 기존 자원 발견이면 `중단`이 나오며 `RESTORE`는 계속 차단됩니다. 이 블록은 처음에 이전 준비 상태를 지우므로, 다시 실행하면 이전 회차의 통과 상태로 우회되지 않습니다. `중단`이면 원인을 확인한 뒤 이 블록을 다시 실행합니다(1초 이상 지나 새 이름이 됩니다).

```bash
unset RESTORE_READY_FOR RESTORE_IMAGE RESTORE_IMAGE_OK_FOR RESTORE_EXPECTED_ID RESTORE_IMPORT_OK_FOR RESTORE_IMPORT_TRIED_FOR RESTORE_HISTORY_OK_FOR H_BEFORE
RP=beone-app-restore-$(date -u +%Y%m%d-%H%M%S)
APP() { sudo docker compose -f /opt/beone/app/compose.yaml -p beone-app --env-file /opt/beone/app/.env "$@"; }
restore_backend_ok() {
	local k="${RP:-}|${D:-}"
	[ -n "${RP:-}" ] && [ -n "${D:-}" ] && [ "${RESTORE_READY_FOR:-}" = "$RP" ] && [ "${RESTORE_IMPORT_OK_FOR:-}" = "$k" ] && [ "${RESTORE_HISTORY_OK_FOR:-}" = "$k" ] && [ "${RESTORE_IMAGE_OK_FOR:-}" = "$k" ] && [ -n "${RESTORE_EXPECTED_ID:-}" ]
}
RESTORE() {
	local sub="${1:-}" a svc=""
	[ -n "${RP:-}" ] && [ "${RESTORE_READY_FOR:-}" = "$RP" ] || { echo "RESTORE 차단: 이 셸에서 회차 사전 검사를 통과하지 않았습니다" >&2; return 1; }
	case "$sub" in
		config | ps | logs | stop | exec) ;;
		up | start | restart | create)
			for a in "${@:2}"; do case "$a" in -*) ;; *) svc="$svc $a" ;; esac; done
			[ "$svc" = " mysql" ] || restore_backend_ok || { echo "RESTORE 차단: backend 기동 조건(사전 검사·반입 성공·백업 시점 이력 일치·짝 이미지 확인)을 모두 만족하지 않았습니다. 서비스 이름 없는 기동도 막습니다" >&2; return 1; } ;;
		*) echo "RESTORE 차단: 이 절차에서 쓰지 않는 명령입니다(${sub:-없음})" >&2; return 1 ;;
	esac
	sudo env BEONE_API_IMAGE="${RESTORE_IMAGE:-beone-restore-image-not-verified}" docker compose -f /opt/beone/app/compose.yaml -p "$RP" --env-file /opt/beone/app/.env "$@"
}
q_ok=1
q_c=$(sudo docker ps -aq --filter "label=com.docker.compose.project=$RP") || q_ok=0
q_v=$(sudo docker volume ls --format '{{.Name}} {{.Label "com.docker.compose.project"}}') || q_ok=0
q_n=$(sudo docker network ls --format '{{.Name}} {{.Label "com.docker.compose.project"}}') || q_ok=0
if [ "$q_ok" != 1 ]; then echo "중단: Docker 조회 실패로 자원 유무를 판단할 수 없습니다"
elif [ -n "$q_c" ] || printf '%s\n' "$q_v" "$q_n" | grep -qE "^${RP}_| ${RP}\$"; then echo "중단: $RP 자원이 이미 있습니다"
else RESTORE_READY_FOR=$RP; echo "새 회차: $RP"; fi
```

3. 덤프·짝 기록·이미지 확인(`D`는 복원할 백업 폴더의 `app.sql`): 덤프를 고르면 이전 반입·이력·이미지 확인 상태를 모두 지웁니다. 기록의 체크섬이 덤프·이력 파일과 맞고, 기록된 이미지 ID가 로컬에 있으며, 기록된 태그도 지금 그 ID를 가리킬 때만 `이미지 확인`이 나오고 조건 ④가 채워집니다. 이 회차의 기동 이미지는 **태그가 아니라 검증한 이미지 ID**(`sha256:…`)로 고정합니다(운영 `.env`는 바꾸지 않음). 확인 뒤 태그가 다른 이미지로 옮겨져도 기동 이미지는 바뀌지 않으며, 태그는 기록·설명용으로만 씁니다. 이미지 ID가 없으면 다른 이미지로 대신 기동하지 않고 기동이 실패합니다. 다른 결과면 4~6단계(DB 반입·이력 조회)만 하고 7단계는 미실행으로 남깁니다.

```bash
D=/opt/beone/app/backups/<백업 폴더>/app.sql
unset RESTORE_IMAGE RESTORE_IMAGE_OK_FOR RESTORE_EXPECTED_ID RESTORE_IMPORT_OK_FOR RESTORE_HISTORY_OK_FOR H_BEFORE
rec_get() { sudo sed -n "s/^$1=//p" "$D.record"; }
if ! sudo test -f "$D"; then echo "중단: 덤프 파일이 없습니다"
elif ! sudo test -f "$D.record" || ! sudo test -f "$D.flyway"; then echo "짝 기록 없음: DB 반입·이력 조회만 합니다. 앱 기동·health는 미실행"
elif [ "$(sudo sha256sum "$D" | cut -d' ' -f1)" != "$(rec_get dump_sha256)" ] || [ "$(sudo sha256sum "$D.flyway" | cut -d' ' -f1)" != "$(rec_get flyway_sha256)" ]; then echo "짝 기록 체크섬 불일치: DB 반입·이력 조회만 합니다. 앱 기동·health는 미실행"
elif [ -z "$(rec_get image_id)" ] || [ -z "$(rec_get image_tag)" ] || [ "$(sudo docker image inspect --format '{{.Id}}' "$(rec_get image_id)" 2> /dev/null)" != "$(rec_get image_id)" ] || [ "$(sudo docker image inspect --format '{{.Id}}' "$(rec_get image_tag)" 2> /dev/null)" != "$(rec_get image_id)" ]; then echo "기록된 이미지 없음 또는 ID 불일치($(rec_get image_tag)): 앱 기동·health는 미실행"
else RESTORE_EXPECTED_ID=$(rec_get image_id); RESTORE_IMAGE=$RESTORE_EXPECTED_ID; RESTORE_IMAGE_OK_FOR="$RP|$D"; echo "이미지 확인: $RESTORE_IMAGE (기록 태그 $(rec_get image_tag), revision $(rec_get image_revision))"; fi
```

4. 대상 확인과 MySQL 기동: `RESTORE config --services` → `mysql`, `backend` 두 줄만(다르면 멈춤). `RESTORE up -d mysql` 후 `RESTORE ps mysql`이 `healthy`가 될 때까지 봅니다. 볼륨은 이 회차 전용 `${RP}_mysql-data`로 새로 만들어집니다.
5. 덤프 반입: 시작할 때 이전 반입·이력 확인 상태를 지우고, SQL 반입 명령 전체가 성공했을 때만 조건 ②를 이 `RP`·`D`에 남깁니다. 한 회차에는 한 번만 반입합니다. 반입이 실패한 회차는 그대로 두고(DB를 비우거나 볼륨을 지우지 않음), 다시 할 때는 2단계부터 새 회차·새 볼륨으로 합니다.

```bash
if [ "${RESTORE_IMPORT_TRIED_FOR:-}" = "${RP:-}" ]; then echo "중단: 이 회차($RP)에는 이미 반입을 시도했습니다. 2단계부터 새 회차로 합니다"
else
	unset RESTORE_IMPORT_OK_FOR RESTORE_HISTORY_OK_FOR H_BEFORE; RESTORE_IMPORT_TRIED_FOR=$RP
	if ( set -o pipefail; sudo cat "$D" | RESTORE exec -T mysql bash -c 'mysql --defaults-extra-file=<(printf "[client]\nuser=%s\npassword=%s\n" "$MYSQL_USER" "$MYSQL_PASSWORD") "$MYSQL_DATABASE"' ); then RESTORE_IMPORT_OK_FOR="$RP|$D"; echo "반입 완료: $RP"
	else echo "반입 실패: 6·7단계는 진행하지 않습니다(운영 DB는 건드리지 않음). 이 회차는 보존하고 새 회차로 다시 합니다"; fi
fi
```

6. Flyway 이력 비교: 이 `RP`·`D`의 반입 성공(조건 ②)이 있을 때만 실행합니다. 기준은 덤프와 짝지어진 **백업 시점 이력**(`$D.flyway`)이며, 일치할 때만 조건 ③을 남깁니다. 이력 조회 실패·기록 없음·불일치면 backend를 열지 않습니다. 운영 DB의 현재 이력은 참고로만 출력하며, 배포 후 달라졌다는 이유만으로 복원 실패로 보지 않습니다.

```bash
restore_history() { RESTORE exec -T mysql bash -c 'mysql --defaults-extra-file=<(printf "[client]\nuser=%s\npassword=%s\n" "$MYSQL_USER" "$MYSQL_PASSWORD") -N -B -e "SELECT installed_rank, version, script, checksum, success FROM flyway_schema_history ORDER BY installed_rank" "$MYSQL_DATABASE"' < /dev/null; }
unset RESTORE_HISTORY_OK_FOR H_BEFORE
if [ -z "${RP:-}" ] || [ -z "${D:-}" ] || [ "${RESTORE_IMPORT_OK_FOR:-}" != "$RP|$D" ]; then echo "6단계 미실행: 이 회차·덤프의 반입 성공이 확인되지 않았습니다"
else
	if ! H_BEFORE=$(restore_history) || [ -z "$H_BEFORE" ]; then echo "이력 조회 실패: backend는 열지 않습니다"
	elif ! sudo test -f "$D.flyway"; then echo "백업 시점 이력 기록 없음: 이력 일치 판정 미실행, backend는 열지 않습니다"
	elif [ "$H_BEFORE" = "$(sudo cat "$D.flyway")" ]; then RESTORE_HISTORY_OK_FOR="$RP|$D"; echo "백업 시점 이력과 일치"
	else echo "백업 시점 이력과 불일치: backend는 열지 않습니다. 멈추고 확인"; fi
	echo "[참고] 운영 DB 현재 이력:"; APP exec -T mysql bash -c 'mysql --defaults-extra-file=<(printf "[client]\nuser=%s\npassword=%s\n" "$MYSQL_USER" "$MYSQL_PASSWORD") -N -B -e "SELECT installed_rank, version, script, checksum, success FROM flyway_schema_history ORDER BY installed_rank" "$MYSQL_DATABASE"' < /dev/null
fi
```

7. backend 기동과 health(조건 ①~④를 모두 만족할 때만): `RESTORE up -d backend` 후 `RESTORE ps backend`가 `healthy`가 될 때까지 봅니다. 조건을 채우지 못했으면 `RESTORE`가 기동을 막고, 아래 블록은 7단계 전체를 미실행으로 표시합니다. healthy가 되면 다음을 실행합니다.

```bash
if ! restore_backend_ok; then echo "7단계 미실행(앱 기동·health 미검증): backend 기동 조건(사전 검사·반입 성공·백업 시점 이력 일치·짝 이미지 확인)을 모두 만족하지 않았습니다"
else
	cid=$(RESTORE ps -q backend)
	[ -n "$cid" ] && [ "$(sudo docker inspect --format '{{.Image}}' "$cid")" = "$RESTORE_EXPECTED_ID" ] && echo "실행 이미지 = 기록 이미지" || echo "실행 이미지 불일치 또는 backend 없음: RESTORE stop backend"
	logs=$(RESTORE logs backend) && echo "새 마이그레이션 적용 로그(0이어야 함): $(printf '%s\n' "$logs" | grep -c 'Successfully applied')" || echo "로그 조회 실패"
	H_AFTER=$(restore_history); [ -n "$H_BEFORE" ] && [ -n "$H_AFTER" ] && [ "$H_AFTER" = "$H_BEFORE" ] && echo "기동 전후 이력 유지" || echo "이력 변경 또는 조회 실패: RESTORE stop backend"
	RESTORE exec -T backend bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /actuator/health HTTP/1.0\r\n\r\n" >&3 && cat <&3' < /dev/null | tail -1
fi
```

   기대: 이미지 일치, 적용 로그 0, 이력 유지, 마지막 줄에 `"status":"UP"`이 있고 `components`·`details`가 없음. 하나라도 다르면 `RESTORE stop backend` 후 확인합니다.

8. 확인 뒤 종료와 기록: `RESTORE stop`은 이 회차의 컨테이너를 멈추기만 합니다. 컨테이너·네트워크·볼륨은 남고 삭제되지 않습니다(`down`·`-v`·`volume rm`은 쓰지 않습니다). 반입·이력 확인에 실패한 회차도 그대로 보존합니다. 다음 복원은 이 프로젝트를 다시 쓰지 않고 2단계부터 새 회차·새 볼륨으로 합니다. 결과에는 DB 반입·이력 비교와 앱 기동·health를 나눠 적고, 7단계를 하지 못했으면 앱 기동은 미검증으로 적습니다.

## 롤백

- **이전 이미지가 있을 때**: `.env`의 `BEONE_API_IMAGE`를 직전 태그로 바꾸고 `APP up -d backend`. 서버에는 현재·직전 이미지만 남기고, 지울 때는 태그를 지정해 직접 지웁니다.
- **이미지 롤백은 DB를 되돌리지 않습니다.** 새 마이그레이션이 적용됐다면 DB는 덤프 복원이라는 별도 절차가 필요합니다.
- **최초 배포가 실패했을 때**: 앱만 멈추려면 `APP stop backend`, 앱 전체를 내리려면 `APP down`. `-v`(볼륨 삭제)는 쓰지 않습니다. 기존 nginx·HTTPS는 영향을 받지 않아야 하며, 5절로 다시 확인합니다.
- 기존 설정을 되돌려야 하면 2절의 tar에서 해당 파일만 꺼내 비교한 뒤 복구합니다.

## 미결·제약

- 이미지 전달 경로, 배포 기준(`main` 또는 승인된 `develop` 커밋), 정기 덤프 주기·자동화.
- 서버의 swap 유무, 인증서 갱신 service의 실제 정의는 사전 대조에서 확인합니다.
- `deploy/compose.yaml`의 MySQL 태그는 `MySqlVersionAlignmentTest`가 검사하지 않습니다. `verify-local.sh static`으로 대조합니다.
