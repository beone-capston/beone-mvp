# beone-verify 루프백 검증 환경 런북

서버(EC2) 안에서 운영 서비스와 분리된 `beone-verify` 프로젝트를 루프백(`127.0.0.1:18080`)에만 띄워 회원 API와 웹을 검증하는 절차입니다.

- **근거:** 결정 39-1(N17: 서버 루프백 검증 환경에서 `member` 프로필 사용 허용, N16: 검증 DB에서만 합성 회원 인증 상태 재현), 결정 39-2(N6: SSH 22는 사용자 Mac 공인 IP/32에만 임시로 열고 끝나면 삭제). 상세 설계는 관리 기록 ROADMAP-next-20261125 3-3~3-8·5-1·5-2절입니다.
- **승인 단계:** 서버 명령은 모두 승인 묶음 ② 검증 환경 생성(2~6절, 11절 대안 전송은 4·6절을 대신하므로 ②), ③ 검증 배포·스모크(7~8절, 10절 웹 교체는 같은 검증 환경의 재배포라 ③), ④ 검증 환경 정리(9·12절)에 속합니다. 10절은 새 웹 번들이 생겼을 때 사용자가 ③ 범위인지 다시 확인한 뒤 실행합니다. 승인 없이 실행하지 않습니다.
- **실행 주체:** SG 변경과 서버 명령은 운영 담당(AWS 콘솔·SSM), `.env` 값 입력은 사용자입니다. 이 저장소는 파일과 순서만 제공합니다.
- **파일:** [`compose.yaml`](compose.yaml), [`nginx.conf`](nginx.conf), [`env.template`](env.template)(키 이름만), 이미지 빌드 [`../scripts/build-image.sh`](../scripts/build-image.sh). 스모크 스크립트 [`../scripts/smoke-member.sh`](../scripts/smoke-member.sh)와 그 T-a 훅(`SMOKE_VERIFY_HOOK`)은 PR #199로 develop에 병합됐습니다(`98ab48d`). 산출물에는 검증할 커밋의 이 파일을 복사하고, 서버 쪽에서는 sha256을 `SHA256SUMS`로 대조합니다. 이 스크립트는 서울 기준 이번 달만 지원하며 다른 `SMOKE_MONTH`는 요청 전에 종료 코드 2로 멈춥니다(머리말).
- **검증 기록:** C2 후보(`ef01309`)로 로컬 사전 스모크 43/43. 서버 실행은 운영 담당이 2026-11-25 05:33~05:45에 EC2 `beone-verify`에서 직접 했고 PASS 42 / FAIL 1이었습니다. FAIL 1건(S8)은 jq `// empty`가 `false`를 빈 값으로 바꾼 스크립트 판정 오류이며, 실제 `/api/v1/me` 200·`codefConnected=false`를 직접 확인했습니다(스크립트는 이후 수정). 이 결과는 그 후보에 대한 기록이며, 다른 후보는 다시 확인해야 합니다.

## 산출물 만들기 (로컬)

`build-image.sh final`은 작업 트리가 깨끗하고 `backend/src/main` 아래 무시된 파일이 없어야 합니다. 에이전트 도구가 남긴 상태 파일(예: `.omc/`)이 있으면 거부하므로, 다른 사람의 파일을 지우지 말고 깨끗한 클론에서 빌드합니다.

```bash
git clone -q --no-checkout <저장소 경로> /tmp/beone-c && cd /tmp/beone-c
git checkout -q --detach <후보 전체 SHA>
deploy/scripts/build-image.sh final <산출물 폴더>/beone-api-<sha12>.tar   # tag·image_id·revision·tar_sha256 출력
gzip -9 -n <산출물 폴더>/beone-api-<sha12>.tar
(cd frontend && npm ci && npm run lint && npm test && npm run build)
COPYFILE_DISABLE=1 tar --no-xattrs -C frontend/dist -czf <산출물 폴더>/web-<sha12>.tgz .
cp deploy/verify/compose.yaml deploy/verify/nginx.conf deploy/verify/env.template <산출물 폴더>/
cp deploy/scripts/smoke-member.sh <산출물 폴더>/   # #199 병합본(98ab48d 이후)의 검증 대상 커밋 파일
cd <산출물 폴더> && shasum -a 256 beone-api-<sha12>.tar.gz web-<sha12>.tgz compose.yaml nginx.conf smoke-member.sh > SHA256SUMS
```

서버에 올리기 전에 같은 산출물로 로컬 사전 스모크를 합니다. 이 저장소에는 로컬 사전 스모크 전용 스크립트가 없습니다(`deploy/scripts/verify-local.sh`는 배포 Compose `deploy/compose.yaml` 검증용이라 이 환경과 다릅니다). 절차는 다음과 같습니다.

1. 산출물의 `compose.yaml`·`nginx.conf`를 로컬 폴더에 복사하고 `name:`을 `beone-verify-local`로, 포트를 로컬에서 비어 있는 주소(예: `127.0.0.1:28080:80`)로 바꿉니다. `web/`에 웹 tgz를 풉니다.
2. 합성 값만 넣은 env 파일(권한 600)로 `docker compose -f <로컬 compose> -p beone-verify-local --env-file <합성 env> up -d --wait`.
3. `SMOKE_VERIFY_HOOK=<훅 스크립트> bash smoke-member.sh http://localhost:28080`. 훅은 7절 T-a SQL을 `-p beone-verify-local`로 실행하는 작은 스크립트입니다(훅 인터페이스는 `deploy/scripts/smoke-member.sh` 머리말).
4. 끝나면 `docker compose -f <로컬 compose> -p beone-verify-local --env-file <합성 env> down -v`.

## 0. 먼저 읽을 것

- **SSM 기본 셸은 `sh`다.** 아래 서버 블록은 `bash` 문법(`[[ ]]`, `$(...)` 배열, `pipefail`)을 쓰는 곳이 있으므로 **세션을 열면 맨 처음 `exec bash`를 실행**한다. 블록마다 첫 줄에도 적어 두었다. 사용자 Mac 블록은 zsh·bash 어느 쪽이든 된다.
- **건드리지 않는 것**: 운영 Compose 프로젝트 `beone-app`(backend·DB), 공개 nginx 프로젝트 `beone`, 운영 볼륨 `beone-app_*`, 복원 시험 프로젝트 `beone-app-restore-*`(있으면 상태 그대로), `/opt/beone/compose.yaml`, `/opt/beone/nginx/`, `/opt/beone-backup/`. 아래 명령 중 이들에 쓰는 것은 없다(아래 "운영 대상 미접촉 확인"). `beone-app`은 **읽기(ps·inspect)** 만 한다.
- **모든 compose 명령은 파일(`-f`)·프로젝트(`-p`)·환경 파일(`--env-file`)을 함께 쓴다.** SSM 새 세션의 작업 폴더에 따라 다른 파일을 읽지 않게 하기 위해서다. 서버 블록은 `exec bash` 다음 줄에서 아래 셋을 정의한다. 운영 두 프로젝트는 **`ps`로 읽기만** 한다.

```bash
V='sudo docker compose -f /opt/beone/verify/compose.yaml -p beone-verify --env-file /opt/beone/verify/.env'   # 검증
APP='sudo docker compose -f /opt/beone/app/compose.yaml -p beone-app --env-file /opt/beone/app/.env'          # 운영 backend·DB(읽기만)
PUB='sudo docker compose -f /opt/beone/compose.yaml -p beone'                                                 # 공개 nginx(읽기만)
```

  운영 파일 위치는 [백엔드 배포 기반](../../docs/deployment/backend-compose.md)의 `APP` 함수(`/opt/beone/app/compose.yaml`, `/opt/beone/app/.env`)와 certbot 서비스가 참조하는 `/opt/beone/compose.yaml`을 따른다. 위치가 다르면(파일 없음 오류) 진행하지 않고 사용자에게 확인한다.
- **조회 실패는 실패로 다룬다.** Docker 조회가 실패하면 빈 목록이나 "같음"으로 보지 않고 멈춘다. 파이프 결과는 `set -o pipefail`로 앞 명령의 실패까지 확인한다.
- **중단 조건**(하나라도 해당하면 즉시 9절 원복): 가용 메모리 < 600MiB / `beone-app` 컨테이너 중 healthy 아닌 것 / 공개 nginx 비정상 / 디스크 ≥ 85% / 검증 컨테이너 OOMKilled=true.
- **웹 번들 한계(X5)**: 이 웹(`web-<sha12>.tgz`)은 프론트 P0 병합 전 번들이다. 로그인까지는 되지만 업무 화면(카드·주문·결제)은 401·403이 **기대 동작**이다. API 확인은 `smoke-member.sh`가 담당한다. P0 병합 뒤 웹만 바꾸는 절차는 10절.
- **T-a 범위(관리자 판단: 인증 완료 상태 재현에 필요한 최소 행, 검증 DB 한정)**: 인증 완료 상태 재현에 필요한 최소 행만 만든다. `members.email_verified_at` 갱신 + 인증 때 생기는 `users` 장부 행 INSERT(V14 백필과 같은 형태). **검증 DB(`beone-verify`) 한정, 합성 회원(`*@example.test`)에만.** 결정 39-1 N16 문구(email_verified_at만)보다 넓다. 장부 행이 없으면 로그인 뒤 업무 API가 전부 500이다(2026-11-25 로컬 스모크에서 확인).

## 1. 산출물 (사용자 Mac: `<산출물 폴더>/`)

| 파일 | 용도 |
|---|---|
| `beone-api-<sha12>.tar.gz` | 검증 backend 이미지(amd64). load 뒤 `beone-api:<sha12>` |
| `web-<sha12>.tgz` | 웹 dist(같은 SHA) |
| `compose.yaml`, `nginx.conf` | 최종본. 상한 mysql 512 + backend 768 + web 64 = 1344MiB |
| `env.template` | `.env` 키 이름만 |
| `smoke-member.sh` | 회원 API 스모크(사용자 Mac에서 터널 경유 실행) |
| `SHA256SUMS` | 위 파일 대조값 |

## 2. SG 22 임시 허용 (운영 담당, AWS 콘솔)

| 동작 | 확인값 | 원복 |
|---|---|---|
| 인스턴스 SG 인바운드에 TCP 22, 소스 = 사용자 Mac 공인 IP/32 추가. 설명에 `beone-verify temp <날짜>` | 규칙 1개 추가됨, 0.0.0.0/0 아님 | 규칙 삭제(12절) |

## 3. V0 기준 기록 (서버)

```bash
exec bash
set -o pipefail
V='sudo docker compose -f /opt/beone/verify/compose.yaml -p beone-verify --env-file /opt/beone/verify/.env'
APP='sudo docker compose -f /opt/beone/app/compose.yaml -p beone-app --env-file /opt/beone/app/.env'
PUB='sudo docker compose -f /opt/beone/compose.yaml -p beone'
free -m; df -h /
$APP ps --format '{{.Name}} {{.Image}} {{.Status}}' || echo 'APP 조회 실패, 진행 안 함'
$PUB ps --format '{{.Name}} {{.Status}}' || echo 'PUB 조회 실패, 진행 안 함'
sudo docker ps -a --format '{{.Names}} {{.Status}}' > /tmp/beone-verify-v0-ps.txt || echo 'docker ps 실패, 진행 안 함'
grep -i restore /tmp/beone-verify-v0-ps.txt
# 볼륨 목록: 조회가 성공했을 때만 기준 파일을 확정한다
if sudo docker volume ls --format '{{.Name}}' > /tmp/beone-verify-v0-volumes.raw; then
  sort /tmp/beone-verify-v0-volumes.raw > /tmp/beone-verify-v0-volumes.txt && wc -l /tmp/beone-verify-v0-volumes.txt
else echo 'docker volume ls 실패, 진행 안 함'; rm -f /tmp/beone-verify-v0-volumes.txt; fi
sudo docker image ls --format '{{.Repository}}:{{.Tag}}' | grep -E '^(mysql|nginx)'   # nginx 태그를 .env에 쓴다
sudo ss -tlnp | grep -c ':18080'
```

| 확인값 | 기준 | 미달 시 |
|---|---|---|
| 가용(`available`) | ≥ 1800MiB  | 진행 안 함 |
| 디스크 사용 | < 70% | 진행 안 함 |
| `beone-app` | 전부 healthy, 이미지 `beone-api:fde3592a0934` | 진행 안 함 |
| 복원 시험 프로젝트 `beone-app-restore-*` | 기록한 상태 그대로 | 진행 안 함(사용자 확인) |
| `:18080` | 0 | 진행 안 함 |
| 조회 실패 문구(`… 실패, 진행 안 함`) | 출력 없음, `/tmp/beone-verify-v0-volumes.txt` 존재 | 진행 안 함 |
| `mysql:8.4.11` | 존재(재사용) | 진행 안 함(추가 다운로드는 결정 필요) |

## 4. 전송과 대조 (사용자 Mac → 서버)

```bash
# 사용자 Mac. <KEY>=beone-ec2-key 경로(사용자 확인), <user>@<host>=서버 접속 정보
cd <산출물 폴더>
shasum -a 256 -c SHA256SUMS
ssh -i <KEY> <user>@<host> 'mkdir -p ~/beone-verify-upload'
scp -i <KEY> beone-api-<sha12>.tar.gz web-<sha12>.tgz compose.yaml nginx.conf SHA256SUMS <user>@<host>:~/beone-verify-upload/
```

```bash
# 서버
exec bash
cd ~/beone-verify-upload && sha256sum -c --ignore-missing SHA256SUMS
```

| 확인값 | 실패 시 |
|---|---|
| 전송한 4개 파일 모두 `OK`(smoke-member.sh는 서버로 보내지 않음) | 다시 전송. 계속 다르면 중단하고 `~/beone-verify-upload` 삭제 |

## 5. V1 배치 (서버)

```bash
exec bash
sudo install -d -m 750 /opt/beone/verify
sudo install -d -m 755 /opt/beone/verify/web   # nginx 작업 프로세스(비root)가 읽어야 한다. 750이면 403
sudo install -m 644 ~/beone-verify-upload/compose.yaml ~/beone-verify-upload/nginx.conf /opt/beone/verify/
sudo tar -xzf ~/beone-verify-upload/web-<sha12>.tgz -C /opt/beone/verify/web
sudo install -m 600 /dev/null /opt/beone/verify/.env
# 사용자가 env.template의 키를 /opt/beone/verify/.env에 직접 채운다(sudoedit). 값은 출력하지 않는다
sudo ls -l /opt/beone/verify /opt/beone/verify/web
sudo sha256sum /opt/beone/verify/compose.yaml /opt/beone/verify/nginx.conf
sudo sh -c 'cut -d= -f1 /opt/beone/verify/.env | grep -v "^#" | sort'   # 키 이름만
```

| 확인값 | 실패 시 |
|---|---|
| `.env` 권한 `-rw-------`, 키 6개(MYSQL_DATABASE·MYSQL_USER·MYSQL_PASSWORD·MYSQL_ROOT_PASSWORD·BEONE_VERIFY_API_IMAGE·BEONE_VERIFY_NGINX_IMAGE) | 다시 작성 |
| compose·nginx sha256 = SHA256SUMS | 다시 복사 |
| `web/index.html` 존재, `web` 권한 `drwxr-xr-x` | 다시 풀기 |

원복: `sudo rm -r /opt/beone/verify`(이 디렉터리만).

## 6. docker load와 revision 확인 (서버)

```bash
exec bash
gunzip -c ~/beone-verify-upload/beone-api-<sha12>.tar.gz | sudo docker load
sudo docker image inspect beone-api:<sha12> --format '{{.Id}} {{.Architecture}} {{index .Config.Labels "org.opencontainers.image.revision"}} {{index .Config.Labels "beone.source.state"}}'
```

| 확인값 | 실패 시 |
|---|---|
| `<로컬 build-image가 출력한 image_id> amd64 <후보 전체 SHA> clean` | `sudo docker image rm beone-api:<sha12>` 후 중단 |

load가 끝나면 `rm ~/beone-verify-upload/beone-api-<sha12>.tar.gz`로 디스크를 돌려받는다.

## 7. V3 config → V4 up → V5 Flyway → S7 T-a (서버)

```bash
exec bash
V='sudo docker compose -f /opt/beone/verify/compose.yaml -p beone-verify --env-file /opt/beone/verify/.env'
APP='sudo docker compose -f /opt/beone/app/compose.yaml -p beone-app --env-file /opt/beone/app/.env'
PUB='sudo docker compose -f /opt/beone/compose.yaml -p beone'
if $V config --quiet; then echo CONFIG_OK; $V up -d; else echo 'CONFIG_FAILED: up 하지 않음, 파일 확인'; fi
$V ps --format '{{.Name}} {{.Status}}'          # 3개 healthy까지 반복(backend start_period 240s)
free -m | awk '/^Mem:/{print "available=" $7 "MiB"}'
sudo docker stats --no-stream --format '{{.Name}} {{.MemUsage}}' | grep -E 'beone-verify|beone-app'
```

| 단계 | 확인값 | 실패 시 |
|---|---|---|
| V3 | `CONFIG_OK`(그 전 출력 없음) | 파일 수정, 진행 안 함 |
| V4 | verify-mysql·verify-backend·verify-web 모두 healthy(web은 running), **가용 ≥ 600MiB**, `beone-app` healthy 유지 | 9절 원복 |

```bash
exec bash
V='sudo docker compose -f /opt/beone/verify/compose.yaml -p beone-verify --env-file /opt/beone/verify/.env'
$V exec -T verify-mysql sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -N -e "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank" "$MYSQL_DATABASE"' 2>/dev/null
```

| 단계 | 확인값 | 실패 시 |
|---|---|---|
| V5 | `1`~`14` 모두 `success=1`, 14행 | 9절 원복 |

**S7 T-a(관리자 판단: 인증 완료 상태 재현에 필요한 최소 행, 검증 DB 한정)**: `smoke-member.sh`는 가입 뒤 "인증 완료 대기"에서 멈추고 실행할 SQL을 보여 준다. 그때 서버에서 이메일마다 아래를 실행한 뒤 사용자 Mac에서 Enter를 누른다. `EMAIL`은 스크립트가 보여 준 합성 주소(`*@example.test`)만 쓴다.

```bash
exec bash
EMAIL='smoke-a-XXXXXXXXXX@example.test'   # 스크립트 안내값으로 바꾼다
case "$EMAIL" in *@example.test) ;; *) echo '합성 주소 아님, 중단'; exit 1 ;; esac
V='sudo docker compose -f /opt/beone/verify/compose.yaml -p beone-verify --env-file /opt/beone/verify/.env'
$V exec -T -e EMAIL="$EMAIL" verify-mysql sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -N -e "START TRANSACTION; UPDATE members SET email_verified_at=NOW() WHERE email=\"$EMAIL\" AND email_verified_at IS NULL; SELECT ROW_COUNT(); INSERT INTO users (demo_user, member_id, display_name, portfolio_type, read_only) SELECT NULL, m.member_id, \"내 카드\", \"REAL\", FALSE FROM members m WHERE m.email=\"$EMAIL\" AND NOT EXISTS (SELECT 1 FROM users u WHERE u.member_id=m.member_id); SELECT ROW_COUNT(); COMMIT;" "$MYSQL_DATABASE"' 2>/dev/null
```

| 확인값 | 실패 시 |
|---|---|
| `1` 다음 `1`(인증 갱신 1행, 장부 1행) | `0 0`이면 주소 오타. 운영 DB에는 실행하지 않는다(`$V`의 `-f`·`-p beone-verify` 고정) |

## 8. 터널 → smoke-member.sh → 화면 B1~B5 (사용자 Mac)

```bash
ssh -i <KEY> -N -L 18080:127.0.0.1:18080 <user>@<host>      # 별도 터미널, 끝날 때까지 유지
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:18080/   # 200
bash -o pipefail -c 'bash "$1" http://localhost:18080 | tee "$2"' _ <산출물 폴더>/smoke-member.sh ~/beone-verify-smoke-$(date +%Y%m%d-%H%M).md; echo "smoke_exit=$?"
```

| 확인값 | 실패 시 |
|---|---|
| 결과 표 마지막 줄 `FAIL=0` **그리고** `smoke_exit=0`(pipefail이라 `tee`가 아닌 스모크의 종료 코드). 로컬 사전 스모크와 같은 PASS 수 | `smoke_exit=1`이면 실패 행을 기록하고 9절 원복 여부를 사용자와 정한다. `smoke_exit=2`는 요청 전 거부(주소 인자 없음, `curl`·`jq` 없음, `SMOKE_MONTH`가 서울 기준 이번 달이 아님)라 서버에 아무 요청도 가지 않았으므로, 값을 고쳐 다시 실행한다. 이 스모크는 회원 API의 표시 기대값을 확인할 뿐, 저장 안 됨·격리 전체를 증명하지 않는다 |

화면(Chrome 고정, Safari 아님, `http://localhost:18080`):

| # | 확인 | 기대(이 번들) |
|---|---|---|
| B1 | 스플래시·로그인 화면 | 표시, 콘솔 오류 없음 |
| B2 | 로그인(T-a 뒤 합성 회원) | 성공 |
| B3 | 업무 화면(카드·주문·결제) | **401·403이 기대 동작(X5)**. P0 웹으로 바꾼 뒤 정상 |
| B4 | DevTools → Cookies | `__Host-BEONE_SESSION` 존재 |
| B5 | 결제 | 브라우저에는 브리지가 없어 결제 불가가 기대 동작 |

진행 중 감시(서버, 별도 세션, 30초 간격). 중단 조건 5개를 모두 본다. 걸리면 **검증 프로젝트만** `down -v` 한다. 이 자동 원복은 결정 39-1이 이미 승인한 중단 범위(가용 600MiB 미만·운영 이상 시 즉시 `down -v`)이고, 대상은 `$V`(`-p beone-verify`) 하나로 고정이다. 운영 프로젝트에는 쓰지 않는다. 조회가 실패해도 중단으로 본다. 운영(APP)·공개(PUB) 컨테이너는 **정상 목록**으로 판정한다. 모든 컨테이너가 `running`이고 헬스체크가 있으면 `healthy`여야 하며, 조회 일부·전체 실패, 빈 결과, 서비스 수보다 적은 컨테이너, `paused`·`created`·`exited`·`restarting`·`starting` 같은 그 밖의 상태는 모두 중단이다. 이미지·디렉터리 삭제와 확인은 운영 담당이 이어서 9절로 한다. 감시 세션이 끊기면 자동 원복도 멈추므로, 운영 담당은 검증 중 이 세션 출력을 계속 본다.

```bash
exec bash
set -o pipefail
V='sudo docker compose -f /opt/beone/verify/compose.yaml -p beone-verify --env-file /opt/beone/verify/.env'
APP='sudo docker compose -f /opt/beone/app/compose.yaml -p beone-app --env-file /opt/beone/app/.env'
PUB='sudo docker compose -f /opt/beone/compose.yaml -p beone'
# 정상 목록(allowlist) 판정: 모든 컨테이너가 running+healthy(헬스체크 없으면 running)일 때만 ok.
# 조회 실패·빈 결과·컨테이너 수 부족·그 밖의 상태(paused, created, exited, restarting, starting 등)는 모두 ok가 아니다.
health() {
  local svc ids st n
  svc=$($1 config --services) && [ -n "$svc" ] || { echo fail-config; return; }
  ids=$($1 ps -q -a) || { echo fail-ps; return; }
  [ -n "$ids" ] || { echo empty; return; }
  n=$(printf '%s\n' "$ids" | wc -l | tr -d ' ')
  [ "$n" -ge "$(printf '%s\n' "$svc" | wc -l | tr -d ' ')" ] || { echo "missing:$n"; return; }
  st=$(sudo docker inspect --format '{{.State.Status}}/{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' $ids) || { echo fail-inspect; return; }
  [ "$(printf '%s\n' "$st" | grep -c .)" -eq "$n" ] || { echo partial; return; }
  if printf '%s\n' "$st" | grep -qvx -e 'running/healthy' -e 'running/none'; then
    echo "bad:$(printf '%s\n' "$st" | sort -u | tr '\n' ' ')"
  else
    echo ok
  fi
}
oom() {
  local ids o
  ids=$($V ps -q -a) && [ -n "$ids" ] || { echo fail; return; }
  o=$(sudo docker inspect --format '{{.State.OOMKilled}}' $ids) || { echo fail; return; }
  [ -n "$o" ] && ! printf '%s\n' "$o" | grep -qvx false && echo false || echo "bad:$(printf '%s\n' "$o" | sort -u | tr '\n' ' ')"
}
while sleep 30; do
  m=$(free -m | awk '/^Mem:/{print $7}') || m=''
  d=$(df -P / | awk 'NR==2{print $5}' | tr -d %) || d=''
  a=$(health "$APP"); p=$(health "$PUB"); o=$(oom)
  echo "$(date -u +%T) avail=${m}MiB disk=${d}% app=[$a] pub=[$p] verify_oom=[$o]"
  r=''
  case "$m" in ''|*[!0-9]*) r="$r mem" ;; *) [ "$m" -ge 600 ] || r="$r mem" ;; esac
  case "$d" in ''|*[!0-9]*) r="$r disk" ;; *) [ "$d" -lt 85 ] || r="$r disk" ;; esac
  [ "$a" = ok ] || r="$r app"
  [ "$p" = ok ] || r="$r pub"
  [ "$o" = false ] || r="$r oom"
  if [ -n "$r" ]; then
    echo "STOP:$r"
    if $V down -v; then echo VERIFY_DOWN_OK; else echo 'VERIFY_DOWN_FAILED: 9절 수동 원복, 사용자에게 즉시 보고'; fi
    break
  fi
done
```

감시를 켠 직후 첫 줄이 `app=[ok] pub=[ok] verify_oom=[false]`인지 본다. 운영 프로젝트에 남은 일회성 컨테이너(예: `exited` 상태) 때문에 `bad:`가 나오면 검증을 시작하지 않고 원인을 운영 담당이 먼저 확인한다. 정상 목록 판정이라 이런 경우에도 중단 쪽으로 닫힌다.

## 9. 중단·원복 (서버)

중단 조건에 걸리면 즉시, 정상 종료면 사용자가 "검증 환경 유지 안 함"을 정한 뒤 실행한다. **유지 여부는 사용자가 정한다.**

```bash
exec bash
set -o pipefail
V='sudo docker compose -f /opt/beone/verify/compose.yaml -p beone-verify --env-file /opt/beone/verify/.env'
APP='sudo docker compose -f /opt/beone/app/compose.yaml -p beone-app --env-file /opt/beone/app/.env'
PUB='sudo docker compose -f /opt/beone/compose.yaml -p beone'
# down -v가 성공했을 때만 이미지·디렉터리를 지운다. 실패하면 compose.yaml·.env를 보존한 채 down -v만 다시 시도한다
if $V down -v; then
  sudo docker image rm beone-api:<sha12>          # 운영 이미지(beone-api:fde3592a0934)와 다르다. mysql·nginx 이미지는 지우지 않는다
  sudo rm -r /opt/beone/verify
  rm -r ~/beone-verify-upload
else
  echo 'DOWN_FAILED: 이후 명령 중단, /opt/beone/verify 보존, down -v 재시도'
fi
# 영향 없음 확인
$APP ps --format '{{.Name}} {{.Image}} {{.Status}}'   # fde3592a0934, healthy
$PUB ps --format '{{.Name}} {{.Status}}'
sudo docker ps -a --format '{{.Names}} {{.Status}}' | grep -i restore              # 복원 시험 프로젝트 상태 그대로
# 기준 파일이 있고 현재 조회가 성공했을 때만 비교한다
if [ -s /tmp/beone-verify-v0-volumes.txt ] && sudo docker volume ls --format '{{.Name}}' > /tmp/beone-verify-v1-volumes.raw; then
  sort /tmp/beone-verify-v1-volumes.raw | diff /tmp/beone-verify-v0-volumes.txt - && echo VOLUMES_SAME
else echo 'VOLUMES_UNKNOWN: 기준 파일 없음 또는 조회 실패'; fi
sudo docker volume ls --format '{{.Name}}' | grep -c '^beone-verify_'               # 0
sudo docker network ls --format '{{.Name}}' | grep -c '^beone-verify_'              # 0
sudo ss -tlnp | grep -c ':18080'                                                    # 0
```

| 확인값 | 실패 시 |
|---|---|
| `DOWN_FAILED` 없음, `beone-app` healthy·이미지 동일, 복원 회차 그대로, `VOLUMES_SAME`(`VOLUMES_UNKNOWN`은 통과 아님), verify 볼륨·네트워크·포트 0 | 사용자에게 즉시 보고 |

## 10. 프론트 P0 병합 뒤 웹만 교체

backend·DB는 그대로 두고 웹 파일만 바꾼다(새 `web-<sha12>.tgz`와 sha256은 위 "산출물 만들기"와 같은 방법으로 만든다).

```bash
exec bash
sha256sum ~/beone-verify-upload/web-<sha12>.tgz             # 만든 쪽이 알린 값과 대조
V='sudo docker compose -f /opt/beone/verify/compose.yaml -p beone-verify --env-file /opt/beone/verify/.env'
sudo mv /opt/beone/verify/web /opt/beone/verify/web.prev && sudo install -d -m 755 /opt/beone/verify/web
sudo tar -xzf ~/beone-verify-upload/web-<sha12>.tgz -C /opt/beone/verify/web
# restart는 옮겨 간 예전 디렉터리(web.prev)를 계속 마운트한다. 웹 컨테이너만 다시 만든다(backend·DB 유지)
$V up -d --no-deps --force-recreate verify-web
```

확인: `curl -s -o /dev/null -w '%{http_code}' http://localhost:18080/` = 200, 화면 B3 정상. 원복: `web.prev`를 `web`으로 되돌리고 같은 `$V up -d --no-deps --force-recreate verify-web`.

## 11. (참고) 대안 전송

SSH 파이프로 서버에 tar를 남기지 않을 수도 있다: `gunzip -c beone-api-<sha12>.tar.gz | ssh -i <KEY> <user>@<host> 'sudo docker load'`. 이때도 6절 inspect 값으로 대조한다.

## 12. SG 22 규칙 삭제 (운영 담당, 콘솔)

전송·터널이 끝나면 **즉시** 2절 규칙을 삭제한다(검증 환경을 유지하더라도 삭제). 확인: 인바운드에 22 규칙 없음(기존 규칙 목록과 같음).

## 운영 대상 미접촉 확인

이 런북의 코드 블록에는 운영 프로젝트 `beone-app`·공개 nginx 프로젝트 `beone`·운영 볼륨 `beone-app_*`·복원 시험 프로젝트·`/opt/beone/compose.yaml`·`/opt/beone/nginx/`·`/opt/beone-backup/`에 쓰는 명령(down·rm·stop·restart·up·exec·prune·reload·load 등)이 없습니다. `beone-app`과 `beone`은 `$APP ps`·`$PUB ps`와 `inspect`로 읽기만 합니다. 런북을 고치면 다음으로 다시 확인합니다(세 검사 모두 결과 0줄이어야 합니다).

```bash
awk '/^```/{b=!b;next} b' deploy/verify/RUNBOOK.md \
  | grep -E '\b(down|rm|stop|restart|up|start|exec|prune|reload|kill|load|tar -x|install|mv)\b' \
  | grep -E 'beone-app|-p beone( |$)|restore|/opt/beone/(compose|nginx)|beone-backup'
awk '/^```/{b=!b;next} b' deploy/verify/RUNBOOK.md | grep -E '\$(APP|PUB) ' | grep -vE '\$(APP|PUB) ps|^ *[a-z]=\$\(health'
awk '/^```/{b=!b;next} b' deploy/verify/RUNBOOK.md | grep -E 'docker compose' \
  | grep -vE -- "-f /opt/beone/verify/compose.yaml -p beone-verify --env-file|-f /opt/beone/app/compose.yaml -p beone-app --env-file /opt/beone/app/.env'|-f /opt/beone/compose.yaml -p beone'|<로컬 compose>|^awk "

```
