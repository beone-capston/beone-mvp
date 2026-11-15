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

값이 비어 있으면 `docker compose`가 바로 실패합니다. `docker compose config`를 옵션 없이 실행하면 비밀값이 풀려 출력되므로 `--quiet` 또는 `--services`로만 확인합니다.

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

아래에서 `APP` 명령은 다음 한 줄의 줄임입니다.

```bash
sudo docker compose -f /opt/beone/app/compose.yaml -p beone-app --env-file /opt/beone/app/.env
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
- `/opt/beone/app/.env`를 직접 만들고 `sudo chmod 600 /opt/beone/app/.env`로 권한을 줄입니다. 비밀번호는 `openssl rand -hex 24`로 만듭니다.
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

백업은 앱 계정으로 앱 DB를 덤프합니다. 비밀번호는 명령행에 넣지 않고 컨테이너 안에서 옵션 파일로 전달합니다.

```bash
APP exec -T mysql bash -c 'mysqldump --defaults-extra-file=<(printf "[client]\nuser=%s\npassword=%s\n" "$MYSQL_USER" "$MYSQL_PASSWORD") --single-transaction --no-tablespaces "$MYSQL_DATABASE"' | sudo tee /opt/beone/app/backups/app-$(date -u +%Y%m%dT%H%M%SZ).sql > /dev/null
```

- 덤프 이후의 변경은 다음 덤프 전까지 덤프로 복구할 수 없습니다. binlog는 3일 보관하지만 binlog 재생 절차는 아직 없습니다.
- 배포·마이그레이션 전에는 반드시 덤프를 하나 남깁니다. 정기 덤프 주기와 자동화는 미정입니다.
- 복원은 운영 DB에 바로 하지 않습니다. 별도 프로젝트(예: `-p beone-app-restore`)에 먼저 복원해 Flyway 이력과 기동을 확인한 뒤 교체 여부를 판단합니다.

## 롤백

- **이전 이미지가 있을 때**: `.env`의 `BEONE_API_IMAGE`를 직전 태그로 바꾸고 `APP up -d backend`. 서버에는 현재·직전 이미지만 남기고, 지울 때는 태그를 지정해 직접 지웁니다.
- **이미지 롤백은 DB를 되돌리지 않습니다.** 새 마이그레이션이 적용됐다면 DB는 덤프 복원이라는 별도 절차가 필요합니다.
- **최초 배포가 실패했을 때**: 앱만 멈추려면 `APP stop backend`, 앱 전체를 내리려면 `APP down`. `-v`(볼륨 삭제)는 쓰지 않습니다. 기존 nginx·HTTPS는 영향을 받지 않아야 하며, 5절로 다시 확인합니다.
- 기존 설정을 되돌려야 하면 2절의 tar에서 해당 파일만 꺼내 비교한 뒤 복구합니다.

## 미결·제약

- 이미지 전달 경로, 배포 기준(`main` 또는 승인된 `develop` 커밋), 정기 덤프 주기·자동화.
- 서버의 swap 유무, 인증서 갱신 service의 실제 정의는 사전 대조에서 확인합니다.
- `deploy/compose.yaml`의 MySQL 태그는 `MySqlVersionAlignmentTest`가 검사하지 않습니다. `verify-local.sh static`으로 대조합니다.
