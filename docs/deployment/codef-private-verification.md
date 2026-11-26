# CODEF 비공개 실연동 검증 런북 (`beone-codef`)

공개 서비스와 분리된 루프백 검증 환경에서 실제 CODEF에 **연결 1회와 동기화 1회**를 하고, 정제된 결과만 검증 DB에 남는지 확인한 뒤 폐기하는 절차입니다.

- **근거:** 결정 38-8(CODEF는 공개 환경에 등록하지 않음), 결정 39-4(비공개 실연동 승인. 로컬 검증 때와 같은 키·KB 계정으로 연결 1회·동기화 1회), ROADMAP-next-20261125 6절(K0~K7)·7절 승인 ⑨~⑪
- **N14(호출 상한):** API별 최대 **3회**(결정 39-4). 서버 재기동과 관계없이 DB 카운터(LP-2, #205)로 셉니다.
- **N15(보존 기간):** 정제 데이터(비밀번호·카드번호·승인번호 원문 제외)는 검증 DB에 **MVP 시연이 끝날 때까지** 보관하고, **MVP 수락 뒤 폐기**합니다(결정 39-4)
- **실행 주체:** 서버 명령은 운영 담당(SSM), 키 입력은 사용자입니다. 에이전트는 키 값을 보지 않습니다.
- **상태:** 운영 담당이 1차 연결 시도를 했습니다. `POST /api/v1/me/codef/connection`은 502 `CODEF_UNAVAILABLE`이었습니다. 로그는 `TOKEN http=200`, `ACCOUNT_CREATE http=200 resultCode=CF-04000`이었고, 이 값만으로는 로그인 실패인지 기관 오류인지 알 수 없었습니다. 그래서 실패 관측 로그를 더했습니다([codef 문서](../domains/codef.md#비공개-실연동-프로필-codef-live-lp-1-193)의 "계정 등록 실패 관측"). 다음 시도 전에 3-0 `budget`으로 남은 횟수를 확인합니다. KB 계정 잠김 위험이 있으니 같은 입력으로 바로 다시 시도하지 않습니다.
- **선행 PR:** 이 런북이 참조하는 [검증 런북](../../deploy/verify/RUNBOOK.md)과 `deploy/verify/compose.yaml`은 #206으로 develop에 병합됐습니다. 참조하는 절은 0절(`V`·`APP`·`PUB` 정의, 중단 조건), 5·6절(배치·이미지 적재), 7절(T-a), 8절(터널·감시 기준), 9절(정리)입니다. 검증 런북의 절 번호나 경로가 바뀌면 이 런북도 함께 고칩니다.
- **검증 이미지(`BEONE_VERIFY_API_IMAGE`):** 검증 런북이 기록한 C2 후보 `ef01309`는 #195(`codef-live` 프로필)와 #205(호출 상한 카운터)보다 **앞선 커밋**이라 이 런북에 쓸 수 없습니다. 그 이미지에는 실 게이트웨이와 카운터가 없습니다.
  - 기본으로 **현재 운영 배포 기준 이미지**를 씁니다. 운영 backend는 이미지 revision `6974880`, ApiDigest `sha256:9a887bd8a67b1577eedfea11d5158fc11d3e1545922d3e2992c919eed495cacd`입니다(`ghcr.io/beone-capston/beone-api@<ApiDigest>`, [운영 회원 스모크](prod-member-smoke.md) 기준 표기와 같음). `6974880`에는 #195·#205가 들어 있습니다.
  - 서버에 이미 있는 운영 이미지를 그대로 씁니다. `.env`에는 digest 참조나 그 이미지의 로컬 태그를 넣습니다. 새 이미지를 받거나 만드는 것은 별도 승인입니다.
  - 기록표에 이미지 revision과 digest를 남깁니다. 이 둘은 공개 GHCR 값이라 비밀이 아닙니다.
  - 단, 위 1차 시도 뒤에 더한 실패 관측 로그(`codef call detail …`)는 `6974880` 이미지에 **없습니다.** 이 줄로 원인을 보려면 해당 변경이 들어간 이미지가 필요합니다. 그 이미지를 언제 만들지는 관리자 결정입니다.

## 0. 서버 명령 블록의 공통 규칙

- SSM 기본 셸은 `sh`입니다. 세션을 열면 먼저 `exec bash`를 한 번 실행합니다.
- 아래 서버 블록은 모두 `( set -euo pipefail` … `)`로 감싼 **독립 블록**입니다. 블록 안에서 명령 하나가 실패하면 그 블록의 나머지는 실행되지 않고 블록이 종료 코드 1로 끝납니다. SSM 셸 자체는 닫히지 않습니다.
- 블록끼리 변수를 공유하지 않습니다. 각 블록이 `C`(compose 명령)·`Q`(검증 DB 조회)·`fail`을 스스로 정의합니다. compose 명령은 2-1절과 같이 **두 `-f`, `-p beone-codef`, `--env-file`을 항상 명시**합니다(현재 폴더나 다른 구성에 의존하지 않음).
- **정지·폐기는 compose 구성을 읽지 않습니다.** `C`는 `${CODEF_*:?}` 필수 보간이 있는 `compose.codef.yaml`과 `/opt/beone/codef/.env`를 함께 읽으므로, 키가 비었거나 지워졌거나 `.env`가 없으면 `ps`·`stop`·`down`을 포함한 모든 `C` 명령이 구성 로딩에서 실패합니다. 그래서 4-1 `stop`, 4-2 `destroy`, 2-4 `watch`의 자동 중지는 Compose가 컨테이너·볼륨·네트워크에 붙이는 프로젝트 라벨 `com.docker.compose.project=beone-codef`로 대상을 고르고 `docker stop`·`rm`·`volume rm`·`network rm`을 직접 씁니다. `--filter label=키=값`은 값이 정확히 같은 것만 고르고, 블록은 출력한 라벨 값이 `beone-codef`와 같은지와 이름 접두어(`beone-codef-`, `beone-codef_`)를 다시 확인합니다. 하나라도 어긋나면 아무것도 멈추거나 지우지 않고 중단합니다. 운영 `beone-app`·공개 `beone`·검증 `beone-verify`는 라벨 값이 달라 대상에 들어오지 않습니다.
- 조회 명령(`docker compose ps`·`logs`, `docker volume ls`, DB 조회)이 실패하면 "0건"으로 읽지 않고 즉시 중단합니다. 결과를 세는 `grep -c`는 조회가 성공한 출력에만 씁니다.
- 각 블록은 끝에 `…_OK`를 출력합니다. 이 줄이 없으면 성공이 아닙니다.
- 운영 backend·DB(`beone-app`)와 공개 nginx(`beone`)는 [검증 런북](../../deploy/verify/RUNBOOK.md) 0절과 같은 `APP`·`PUB` 정의(`/opt/beone/app/compose.yaml`·`/opt/beone/app/.env`, `/opt/beone/compose.yaml`)로 **`ps`·`config --services`·`inspect` 읽기만** 합니다. `beone-verify`는 검증 런북과 같은 `V`로 2-1의 `verify-down`에서만 씁니다.
- 컨테이너 상태는 검증 런북 8절과 같은 **정상 목록(allowlist)**으로 판정합니다. 모든 컨테이너가 `running`이고 헬스체크가 있으면 `healthy`(없으면 `running/none`, 예: `verify-web`)일 때만 `ok`입니다. 조회 일부·전체 실패, 빈 결과, 서비스 수보다 적은 컨테이너, `paused`·`created`·`exited`·`restarting`·`starting` 등 그 밖의 상태는 모두 `ok`가 아닙니다(2-1 `ready`, 2-4 `watch`).
- 이 규칙의 실패 경로(설정 실패 뒤 `up` 미실행, 키 없음·빈 값·`.env` 없음에서 `C`의 구성 로딩 실패와 라벨 경로의 정지·폐기, 삭제 실패 뒤 다음 단계 미진행, 대상 오식별, 조회 실패)는 가짜 `docker`·`sudo`로 오프라인 점검했습니다([`deploy/scripts/codef-runbook-selftest.sh`](../../deploy/scripts/codef-runbook-selftest.sh)). 서버에서 실행해 본 것은 아닙니다.

## 1. 코드와 프로필 (K0)

| 항목 | 내용 | 상태 |
|---|---|---|
| 실 게이트웨이 | `LiveCodefGateway`, 프로필 조건 `member & codef-live & !prod`([codef 문서](../domains/codef.md#비공개-실연동-프로필-codef-live-lp-1-193)). `prod`와 함께 켜면 `PublicProfileGuard`가 기동을 멈춤 | develop 병합(#195) |
| 호출 상한 카운터 | 프로세스를 넘는 호출 총량(LP-2, [codef 문서](../domains/codef.md#프로세스를-넘는-호출-총량-lp-2-196)). 표 `codef_call_budgets`(V15)에 API·창 시작 시각별 누적 시도 수. **설정이 하나라도 없거나 창 밖이면 닫힘(호출 0)** | develop 병합(#205, `97fe4c3`) |
| 호출 상한 설정 | `BEONE_CODEF_BUDGET_PER_API`(=`3`), `BEONE_CODEF_BUDGET_WINDOW_START`, `BEONE_CODEF_BUDGET_WINDOW_END`(ISO-8601 오프셋 시각, 시작 포함·끝 제외). Spring 키 `beone.codef.budget.per-api`·`window-start`·`window-end` | 코드 기준(#205) |
| 프로필 | `SPRING_PROFILES_ACTIVE=member,codef-live`(`prod` 없음) | 코드 기준(#195, `LiveCodefGateway.PROFILE`) |
| 키 환경 변수 | `CODEF_CLIENT_ID`, `CODEF_CLIENT_SECRET`, `CODEF_PUBLIC_KEY`, `BEONE_CODEF_CONNECTED_ID_KEY`, `BEONE_CODEF_APPROVAL_HMAC_KEY`(이름만) | 코드 기준(#195) |
| `beone.codef.*` 설정(비밀 아님) | `beone.codef.product-host`(기본 `https://sandbox.codef.io`, 허용 `sandbox`·`development`·`api.codef.io`), `beone.codef.connect-timeout`(기본 5초), `beone.codef.token-response-timeout`(기본 110초), `beone.codef.product-response-timeout`(기본 310초). 환경 변수로는 `BEONE_CODEF_PRODUCT_HOST` 등(Spring 완화 바인딩) | 코드 기준([`CodefTransportConfiguration`](../../backend/src/main/java/com/beone/api/codef/transport/CodefTransportConfiguration.java)) |
| 실 계정용 상품 호스트(N19) | 기본값은 샌드박스라 실 KB 계정에는 맞지 않을 수 있다. `development.codef.io`·`api.codef.io` 중 무엇을 쓸지는 **미결(N19)**. 키 발급 종류와 함께 사용자가 정한다 | 미결 |

## 2. beone-codef 실행안

### 2-1. 환경 (K1, 승인 ⑨)

- `beone-verify`를 **먼저 멈춥니다**. 서버 메모리 때문에 두 검증 환경을 동시에 띄우지 않습니다.
- **`down -v`가 아니라 `stop`입니다(볼륨 보존).** 판단 기준은 다음과 같습니다.
  - 검증 DB(원장 포함)를 남겨 두어야 실패 뒤 재시도와 원인 분석에서 이전 상태를 다시 볼 수 있습니다. `down -v`는 이 상태를 되돌릴 수 없게 지웁니다.
  - 멈춘 컨테이너는 메모리를 쓰지 않으므로, 메모리 때문에 볼륨까지 지울 필요는 없습니다.
  - `beone-verify` 볼륨 삭제는 **사용자가 정합니다.** 운영 담당·에이전트가 판단해 지우지 않습니다. 지우기로 정하면 4-2 뒤 검증 런북 9절로 지웁니다.
- 이때 [검증 런북](../../deploy/verify/RUNBOOK.md) 9절 블록은 **실행하지 않습니다.** 9절은 `down -v` 뒤 검증 이미지(`beone-api:<sha12>`)와 `/opt/beone/verify`(이 런북이 쓰는 `compose.yaml` 포함)까지 지우기 때문입니다. 이미지·`/opt/beone/verify`는 `beone-codef`가 다시 씁니다.
- 다시 쓸 때는 `beone-codef`를 4-1 `stop`으로 멈춘 뒤 `$V start`로 같은 볼륨 그대로 띄웁니다.

```bash
# block: verify-down   (down -v 아님. 컨테이너만 멈추고 볼륨은 남긴다)
( set -euo pipefail
V='sudo docker compose -f /opt/beone/verify/compose.yaml -p beone-verify --env-file /opt/beone/verify/.env'
fail() { echo "중단: $*" >&2; exit 1; }
$V stop || fail "beone-verify stop 실패. beone-codef를 띄우지 않는다"
running=$($V ps -q) || fail "beone-verify 조회 실패(0개와 다름)"
[ -z "$running" ] || fail "실행 중인 beone-verify 컨테이너가 남음"
vols=$(sudo docker volume ls --format '{{.Name}}') || fail "볼륨 조회 실패(0개와 다름)"
n=$(printf '%s\n' "$vols" | grep -c '^beone-verify_' || true)
echo "beone-verify 볼륨 보존: ${n}개(삭제는 사용자 결정)"
sudo test -f /opt/beone/verify/compose.yaml || fail "/opt/beone/verify/compose.yaml 없음(9절로 지웠으면 검증 런북 5·6절 배치부터 다시)"
echo VERIFY_DOWN_OK
)
```

- 같은 compose(`/opt/beone/verify/compose.yaml`)를 별도 프로젝트 이름 `beone-codef`로 띄웁니다. 빈 DB, 별도 볼륨(`beone-codef_verify-mysql-data`), `127.0.0.1:18080`, 메모리 상한과 중단 조건은 검증 런북과 같습니다.
- 백엔드에만 CODEF 프로필·키·호출 상한 설정을 넘기는 덮어쓰기 파일(`/opt/beone/codef/compose.codef.yaml`) 초안은 아래와 같습니다. 값은 쓰지 않고 `.env`에서 읽으며, 하나라도 없으면 `config`가 실패합니다(`:?`).

```yaml
# /opt/beone/codef/compose.codef.yaml (초안)
services:
  verify-backend:
    environment:
      SPRING_PROFILES_ACTIVE: member,codef-live      # prod 아님
      CODEF_CLIENT_ID: ${CODEF_CLIENT_ID:?}
      CODEF_CLIENT_SECRET: ${CODEF_CLIENT_SECRET:?}
      CODEF_PUBLIC_KEY: ${CODEF_PUBLIC_KEY:?}
      BEONE_CODEF_CONNECTED_ID_KEY: ${BEONE_CODEF_CONNECTED_ID_KEY:?}
      BEONE_CODEF_APPROVAL_HMAC_KEY: ${BEONE_CODEF_APPROVAL_HMAC_KEY:?}
      BEONE_CODEF_PRODUCT_HOST: ${BEONE_CODEF_PRODUCT_HOST:?}                 # N19 미결(1절). 비밀 아님
      # 호출 상한(LP-2, N14). 비밀 아님. 빠지면 백엔드는 닫힘(호출 0)
      BEONE_CODEF_BUDGET_PER_API: ${BEONE_CODEF_BUDGET_PER_API:?}             # 3
      BEONE_CODEF_BUDGET_WINDOW_START: ${BEONE_CODEF_BUDGET_WINDOW_START:?}   # 2-2에서 한 번 정하고 바꾸지 않음
      BEONE_CODEF_BUDGET_WINDOW_END: ${BEONE_CODEF_BUDGET_WINDOW_END:?}
```

실행 순서는 `verify-down` → 2-2(`.env` 입력, `names`가 `NAMES_OK`) → `up` → `ready` → 2-4 `watch` → 2-3 호출입니다. `config`가 실패하면 `up`을 실행하지 않습니다.

```bash
# block: up
( set -euo pipefail
C=(sudo docker compose -f /opt/beone/verify/compose.yaml -f /opt/beone/codef/compose.codef.yaml -p beone-codef --env-file /opt/beone/codef/.env)
fail() { echo "중단: $*" >&2; exit 1; }
"${C[@]}" config --quiet || fail "config 실패(값 누락 등). up을 실행하지 않는다"
echo CONFIG_OK
"${C[@]}" up -d || fail "up 실패"
"${C[@]}" ps --format '{{.Name}} {{.Status}}' || fail "ps 조회 실패"
echo UP_OK
)
```

`up` 뒤에는 검증 런북 7절 V4와 같은 기준(3개 컨테이너 정상, 가용 ≥ 600MiB, 운영 정상 유지)을 아래 `ready`로 확인합니다. backend의 `start_period`(240s) 안에는 `starting`이라 실패할 수 있으니 잠시 뒤 다시 실행합니다. 240s가 지나도 `READY_OK`가 없으면 4-1 `stop`으로 갑니다.

```bash
# block: ready
( set -euo pipefail
C=(sudo docker compose -f /opt/beone/verify/compose.yaml -f /opt/beone/codef/compose.codef.yaml -p beone-codef --env-file /opt/beone/codef/.env)
APP='sudo docker compose -f /opt/beone/app/compose.yaml -p beone-app --env-file /opt/beone/app/.env'   # 읽기만
PUB='sudo docker compose -f /opt/beone/compose.yaml -p beone'                                         # 읽기만
fail() { echo "중단: $*" >&2; exit 1; }
# 정상 목록(allowlist) 판정. 검증 런북 8절 health와 같은 기준이며, compose 명령을 인자로 받는다
health() {
	local svc ids st n
	svc=$("$@" config --services) && [ -n "$svc" ] || { echo fail-config; return; }
	ids=$("$@" ps -q -a) || { echo fail-ps; return; }
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
c=$(health "${C[@]}"); a=$(health $APP); p=$(health $PUB)
m=$(free -m | awk '/^Mem:/{print $7}') || fail "메모리 조회 실패"
echo "codef=[$c] app=[$a] pub=[$p] avail=${m}MiB"
[ "$c" = ok ] || fail "beone-codef가 정상 목록이 아님"
[ "$a" = ok ] || fail "운영 beone-app이 정상 목록이 아님. 4-1 stop"
[ "$p" = ok ] || fail "공개 nginx가 정상 목록이 아님. 4-1 stop"
case "$m" in ''|*[!0-9]*) fail "가용 메모리 값을 읽지 못함" ;; esac
[ "$m" -ge 600 ] || fail "가용 ${m}MiB < 600MiB. 4-1 stop"
echo READY_OK
)
```

### 2-2. 키와 호출 상한 입력 (K2, 승인 ⑨)

1. 운영 담당이 아래 `env-file`로 파일을 준비합니다. **`.env`가 이미 있으면 그대로 두고**(4-1 재기동 때 창 값을 보존), 없을 때만 빈 파일(600)을 만듭니다. 기존 파일을 빈 파일로 덮어쓰지 않습니다.

```bash
# block: env-file   (있으면 보존, 없을 때만 빈 파일)
( set -euo pipefail
fail() { echo "중단: $*" >&2; exit 1; }
sudo install -d -m 750 /opt/beone/codef || fail "폴더 준비 실패"
if sudo test -e /opt/beone/codef/.env; then
	echo "기존 .env 보존(덮어쓰지 않음)"
else
	sudo install -m 600 /dev/null /opt/beone/codef/.env || fail ".env 생성 실패"
fi
mode=$(sudo stat -c '%a %U' /opt/beone/codef/.env) || fail ".env 권한 조회 실패"
[ "$mode" = '600 root' ] || fail ".env 권한·소유자가 600 root가 아님: $mode"
echo ENV_FILE_OK
)
```

2. **사용자가 직접** `sudoedit /opt/beone/codef/.env`로 다음을 입력합니다. 로컬 검증 때와 같은 키를 씁니다.
   - 검증 DB 값(`MYSQL_DATABASE`·`MYSQL_USER`·`MYSQL_PASSWORD`·`MYSQL_ROOT_PASSWORD`), 이미지 태그(`BEONE_VERIFY_API_IMAGE`·`BEONE_VERIFY_NGINX_IMAGE`)
   - 1절의 키 5개, 상품 호스트(`BEONE_CODEF_PRODUCT_HOST`, N19)
   - 호출 상한: `BEONE_CODEF_BUDGET_PER_API=3`, `BEONE_CODEF_BUDGET_WINDOW_START`, `BEONE_CODEF_BUDGET_WINDOW_END`. 창은 연결·동기화를 할 검증 기간 전체를 덮도록 **처음 한 번만** 정합니다(예: `2026-12-01T00:00:00+09:00` ~ `2026-12-08T00:00:00+09:00`).
3. **창은 한 번 정하면 바꾸지 않습니다.** 카운터는 창 시작 시각별로 따로 세므로, 재기동할 때 시작 시각을 바꾸면 새 행에서 0부터 다시 세어 상한이 무력해집니다. 재기동·키 재입력(4-1) 때도 같은 값을 그대로 둡니다. 창이 끝나면 호출은 닫히며, 창을 늘리거나 새로 정하는 것은 사용자 승인 사항입니다.
4. 에이전트와 운영 담당은 키 값을 보지 않습니다. 이름이 있는지와 빈 값이 없는지만 확인합니다. 호출 상한 세 값은 비밀이 아니므로 기록표에 남깁니다(창 고정 확인용).

```bash
# block: names
( set -euo pipefail
fail() { echo "중단: $*" >&2; exit 1; }
names=$(sudo awk -F= '!/^#/ && NF { print $1 }' /opt/beone/codef/.env) || fail ".env를 읽지 못함"
empty=$(sudo awk -F= '!/^#/ && NF && length($2) == 0 { print $1 }' /opt/beone/codef/.env) || fail ".env를 읽지 못함"
missing=""
for k in MYSQL_DATABASE MYSQL_USER MYSQL_PASSWORD MYSQL_ROOT_PASSWORD BEONE_VERIFY_API_IMAGE BEONE_VERIFY_NGINX_IMAGE \
	CODEF_CLIENT_ID CODEF_CLIENT_SECRET CODEF_PUBLIC_KEY BEONE_CODEF_CONNECTED_ID_KEY BEONE_CODEF_APPROVAL_HMAC_KEY \
	BEONE_CODEF_PRODUCT_HOST BEONE_CODEF_BUDGET_PER_API BEONE_CODEF_BUDGET_WINDOW_START BEONE_CODEF_BUDGET_WINDOW_END; do
	printf '%s\n' "$names" | grep -qx "$k" || missing="$missing $k"
done
[ -z "$missing" ] || fail "이름 없음:$missing"
[ -z "$empty" ] || fail "빈 값: $(echo $empty)"
sudo grep -E '^BEONE_CODEF_BUDGET_' /opt/beone/codef/.env || fail "호출 상한 값을 읽지 못함"   # 비밀 아님. 기록표에 남긴다
echo NAMES_OK
)
```

- 공개 `beone-app` 환경 파일에는 넣지 않습니다. 저장소·이슈·PR·로그에도 남기지 않습니다.

### 2-3. 호출 (K3, 승인 ⑩)

- 호출 전에 3-1의 `ledger-before`와 3-0의 `budget`(행 0)을 실행해 기준을 남깁니다.
- 2-4 `watch`를 별도 세션에서 켜고 첫 줄이 `codef=[ok] app=[ok] pub=[ok] codef_oom=[false]`인지 봅니다.
- 사용자 Mac에서 SSH 터널(검증 런북 8절 첫 줄, 같은 `127.0.0.1:18080`)로 `http://localhost:18080`에 접속해 합성 회원 1명을 가입시킵니다. SSH를 쓸 수 없으면 아래 2-3-1의 **SSM 세션 안 루프백 curl 경로**를 씁니다.
- 인증 완료 상태는 검증 런북 7절과 같은 T-a 범위(검증 DB 한정, 합성 회원 `*@example.test`만, `members.email_verified_at` 갱신과 `users` 장부 행 1개)로 만듭니다. **검증 런북 7절의 T-a 블록은 `$V`(`-p beone-verify`)라 쓰지 않고** 아래 `ta`를 씁니다. 먼저 같은 셸에서 `EMAIL='<합성 주소>'`를 정한 뒤 실행합니다.

```bash
# block: ta   (먼저 EMAIL='smoke-…@example.test' 지정)
( set -euo pipefail
C=(sudo docker compose -f /opt/beone/verify/compose.yaml -f /opt/beone/codef/compose.codef.yaml -p beone-codef --env-file /opt/beone/codef/.env)
fail() { echo "중단: $*" >&2; exit 1; }
: "${EMAIL:?EMAIL을 먼저 지정}"
case "$EMAIL" in *@example.test) ;; *) fail "합성 주소 아님" ;; esac
out=$("${C[@]}" exec -T -e EMAIL="$EMAIL" verify-mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --default-character-set=utf8mb4 -u"$MYSQL_USER" -N -B -e "START TRANSACTION; UPDATE members SET email_verified_at=NOW() WHERE email=\"$EMAIL\" AND email_verified_at IS NULL; SELECT ROW_COUNT(); INSERT INTO users (demo_user, member_id, display_name, portfolio_type, read_only) SELECT NULL, m.member_id, \"내 카드\", \"REAL\", FALSE FROM members m WHERE m.email=\"$EMAIL\" AND NOT EXISTS (SELECT 1 FROM users u WHERE u.member_id=m.member_id); SELECT ROW_COUNT(); COMMIT;" "$MYSQL_DATABASE"') || fail "T-a 실행 실패"
[ "$(printf '%s\n' "$out" | tr '\n' ' ')" = '1 1 ' ] || fail "기대 1·1(인증 1행, 장부 1행)이 아님: $(echo $out)"
echo TA_OK
)
```

- `POST /api/v1/me/codef/connection`: 기관 `0301`(KB국민카드), 동의한 계정 1개. 비밀번호는 사용자가 화면이나 요청에 직접 입력하고 기록하지 않습니다. **1회.**
- `POST /api/v1/me/codef/syncs`: **1회.**
- 기록은 HTTP 상태와 `CF-` 결과 코드만 남깁니다. 응답 전문은 저장하지 않습니다.
- **상한(N14):** API별 최대 3회이며 DB 카운터가 막습니다. 실패해도 자동 재시도는 없습니다(게이트웨이는 재호출하지 않음). 다시 시도하면 횟수에 셉니다. 연결이 `502 CODEF_UNAVAILABLE`이고 `codef_call_budgets`에 행이 없으면 상한 설정 누락·창 밖(닫힘)을 먼저 의심합니다(2-2 `names`).
- **중단:** 계정 잠김 경고나 인증 실패 계열 `CF-`, 상한 도달, 로그·DB에서 금지 값 발견, 원장 변경, 운영 영향 중 하나라도 해당하면 즉시 멈추고 4절로 갑니다.

### 2-3-1. SSM 세션 안에서 루프백 curl로 호출 (SSH 대신)

SSH 터널 없이 서버 안에서 `http://localhost:18080`(검증 nginx → `verify-backend`)으로 직접 부르는 경로입니다. 순서·횟수·중단 조건은 위 2-3과 같습니다.

- **Session Manager 대화형 세션에서만 씁니다.** 비밀번호를 입력하는 단계(아래 B·D)는 Run Command로 보내지 않습니다. Run Command의 명령 문구는 SSM 실행 기록에 남기 때문입니다.
  - Run Command로 해도 되는 것은 값이 없는 조회 블록뿐입니다(`ready`·`budget`·`ledger-*`·`logs`, 각 블록 그대로).
- 비밀번호는 `read -rs`로만 받습니다. 화면·셸 기록·명령줄 인자에 남지 않고, JSON 본문은 `jq`의 환경 변수로만 만듭니다(운영 회원 스모크와 같은 방식).
  - 세션 기록(Session Manager 로깅)이 켜져 있어도 `read -rs` 입력은 화면에 표시되지 않습니다. 출력에는 HTTP 상태와 오류 코드만 찍습니다.
- `localhost`를 씁니다. 세션 쿠키 `__Host-BEONE_SESSION`은 `Secure`이고, curl은 `http://localhost`를 보안 문맥으로 보고 이 쿠키를 저장합니다. 검증 런북의 서버 스모크도 같은 주소로 실행했습니다.
- 서버에 `jq`가 없으면 이 경로를 쓰지 않고 운영 담당에게 알립니다(설치는 별도 승인).

**A. 준비** (`exec bash` 뒤, 같은 셸에서)

```bash
command -v jq > /dev/null && command -v curl > /dev/null && echo "도구 OK" || echo "jq·curl 없음: 중단"
curl --version | head -1   # 7.79 이상이어야 localhost에서 Secure 쿠키를 저장한다. 낮으면 중단
B=http://localhost:18080
W=$(mktemp -d) && [ -d "$W" ] && chmod 700 "$W" && echo "준비 OK" || { W=; echo "준비 실패: 중단"; }
trap '[ -n "$W" ] && rm -f "$W/body" "$W/head" "$W/jar" "$W/csrf" && rmdir "$W"' EXIT
req() {   # req <메서드> <경로> [-] : '-'이면 표준 입력을 JSON 본문으로 보낸다. 상태 코드만 출력한다
	[ -n "${W:-}" ] && [ -d "$W" ] || { echo "준비 안 됨(A부터 다시)" >&2; echo 000; return 1; }   # 빈 W로 /body 등에 쓰지 않는다
	local a=(-s -o "$W/body" -D "$W/head" -w '%{http_code}' -X "$1" --max-time 330 -c "$W/jar" -b "$W/jar")
	[ "${3:-}" = - ] && a+=(-H 'Content-Type: application/json' --data-binary @-)
	[ -s "$W/csrf" ] && a+=(-H @"$W/csrf")
	rm -f "$W/body" "$W/head"
	local c; c=$(curl "${a[@]}" "$B$2") || c=000
	echo "${c:-000}"
}
csrf() {  # CSRF 토큰을 헤더 파일로만 만든다. 토큰은 출력하지 않는다
	rm -f "$W/csrf"
	local st; st=$(req GET /api/v1/auth/csrf)
	[ "$st" = 200 ] || { echo "CSRF 발급 실패(상태 $st)" >&2; return 1; }
	local t; t=$(jq -r '.data.token // empty' "$W/body" 2> /dev/null)
	[ -n "$t" ] || { echo "CSRF 토큰 없음" >&2; return 1; }
	printf 'X-CSRF-TOKEN: %s\n' "$t" > "$W/csrf"
}
code() { jq -r '.code // empty' "$W/body" 2> /dev/null; }
```

- `--max-time 330`은 백엔드의 상품 응답 대기(기본 310초, 1절)보다 길게 잡은 값입니다. 중간에 끊으면 결과를 모른 채 다시 시도하게 되어 호출 횟수만 씁니다.

**B. 합성 회원 가입과 인증** (합성 주소 `*@example.test`, 회원 비밀번호는 이번 검증용 새 값)

```bash
read -r -p '합성 회원 주소(*@example.test): ' EM
read -rs -p '합성 회원 비밀번호(표시 안 됨): ' PW; echo
mcred() { EM="$EM" PW="$PW" jq -n '{email: env.EM, password: env.PW}'; }
# 합성 주소이고 비밀번호가 있을 때만 가입 요청을 보낸다(아니면 보내지 않고 멈춘다)
case "$EM" in
	?*@example.test) if [ -n "$PW" ]; then s=; csrf && s=$(mcred | req POST /api/v1/auth/sign-up -); echo "${s:-중단} $(code)"; else echo "비밀번호 없음: 보내지 않음"; fi ;;   # 기대 202
	*) echo "합성 주소 아님: 보내지 않음" ;;
esac
```

- 가입 뒤 같은 세션에서 `EMAIL="$EM"`으로 정하고 2-3의 `ta` 블록을 실행합니다(`TA_OK`).
- 이어서 로그인합니다. 기대는 `200`입니다.

```bash
s=; csrf && s=$(mcred | req POST /api/v1/auth/login -); echo "${s:-중단} $(code)"   # 기대 200
s=$(req GET /api/v1/me); echo "$s codefConnected=$(jq -r '.data.codefConnected // empty' "$W/body" 2> /dev/null)"   # 기대 200 false
```

**C. 호출 전 기준** — 다른 셸 또는 Run Command로 `ledger-before`, `budget`을 실행하고 2-4 `watch`가 `ok`인지 봅니다. `budget`의 `ACCOUNT_CREATE` 값으로 남은 횟수를 확인합니다(3 − 사용 수). 남은 횟수가 1이면 사용자에게 다시 확인합니다.

**D. 연결 1회** (KB 아이디·비밀번호는 사용자가 직접 입력)

```bash
read -rs -p 'KB 아이디(표시 안 됨): ' KID; echo
read -rs -p 'KB 비밀번호(표시 안 됨): ' KPW; echo
s=; csrf && s=$(KID="$KID" KPW="$KPW" jq -n '{organization: "0301", loginType: "ID", loginId: env.KID, password: env.KPW}' | req POST /api/v1/me/codef/connection -); echo "${s:-중단} $(code)"
unset KID KPW
```

- 기대는 `201`입니다. 다른 결과면 상태와 `code`만 기록하고 **다시 보내지 않습니다.**
- 결과와 상관없이 `budget`과 `logs`를 실행합니다. 실패 관측 로그가 있는 이미지라면 `codef call detail api=ACCOUNT_CREATE …` 줄(코드·항목 수·`extraMessage` 유무만)을 기록합니다.
- `400 CODEF_LOGIN_FAILED`·`502 CODEF_UNAVAILABLE` 뒤 재시도는 이 기록을 보고 사용자가 정합니다.

**E. 동기화 1회** (연결 `201` 뒤에만)

```bash
s=; csrf && s=$(req POST /api/v1/me/codef/syncs); echo "${s:-중단} $(code)"
jq -c '{held: .data.heldCards.fetched, imported: .data.approvals.imported, skipped: .data.approvals.skippedDuplicates, performances: (.data.performances | length?), partialFailures: [.data.partialFailures[]?.target]}' "$W/body" 2> /dev/null   # 건수·실패 대상만(카드 이름·금액 출력 안 함)
```

- 이어서 3절 확인으로 갑니다. 끝나면 `unset PW EM`을 실행하고 세션을 닫습니다. 닫을 때 `trap`이 쿠키·CSRF 파일을 지웁니다.

### 2-4. 진행 중 감시 (검증 런북 8절과 같은 기준)

`ready` 뒤 별도 세션에서 켜고, 호출·확인이 끝날 때까지 출력을 봅니다. 중단 조건은 검증 런북 0절과 같습니다(가용 < 600MiB, 디스크 ≥ 85%, 운영·공개 nginx가 정상 목록 아님, 검증 컨테이너 OOMKilled). 여기에 `beone-codef` 자신이 정상 목록이 아닌 경우를 더합니다. 조회 실패·빈 결과·값 없음은 모두 중단입니다.

중단 시 동작은 검증 런북과 다르게 **`down -v`가 아니라 `stop`**입니다. 중지는 4-1 `stop`과 같은 라벨 경로(`com.docker.compose.project=beone-codef` 정확 일치, 0절)로 하므로 그 순간 `.env`의 키가 비었거나 없어도 멈춥니다. 키가 없으면 `codef` 판정 자체가 `fail-config`라 바로 중지 쪽으로 닫힙니다. 호출 상한 카운터 `codef_call_budgets`가 검증 DB 볼륨에 있어서, 볼륨을 지우면 상한이 0부터 다시 세어지기 때문입니다(4-3 마지막 줄과 같은 이유). 볼륨 삭제는 사용자가 4-2로 정합니다. 블록은 중단하면 종료 코드 1로 끝나고, 감시 세션이 끊기면 자동 중지도 멈춥니다.

```bash
# block: watch
( set -euo pipefail
C=(sudo docker compose -f /opt/beone/verify/compose.yaml -f /opt/beone/codef/compose.codef.yaml -p beone-codef --env-file /opt/beone/codef/.env)
APP='sudo docker compose -f /opt/beone/app/compose.yaml -p beone-app --env-file /opt/beone/app/.env'   # 읽기만
PUB='sudo docker compose -f /opt/beone/compose.yaml -p beone'                                         # 읽기만
# 정상 목록(allowlist) 판정. 검증 런북 8절 health와 같은 기준이며, compose 명령을 인자로 받는다
health() {
	local svc ids st n
	svc=$("$@" config --services) && [ -n "$svc" ] || { echo fail-config; return; }
	ids=$("$@" ps -q -a) || { echo fail-ps; return; }
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
# 라벨 경로 중지(0절). compose 구성·.env를 읽지 않는다. 라벨 값·이름 접두어가 하나라도 어긋나면 아무것도 멈추지 않는다
stop_codef() {
	local L='label=com.docker.compose.project=beone-codef' rows id name proj ids=() running
	rows=$(sudo docker ps -a --filter "$L" --format '{{.ID}}|{{.Names}}|{{.Label "com.docker.compose.project"}}') || { echo "대상 조회 실패" >&2; return 1; }
	[ -n "$rows" ] || { echo "beone-codef 컨테이너가 없음" >&2; return 1; }
	while IFS='|' read -r id name proj; do
		[ "$proj" = beone-codef ] || { echo "프로젝트 라벨이 beone-codef가 아님: $name" >&2; return 1; }
		case "$name" in beone-codef-*) ;; *) echo "beone-codef가 아닌 이름: $name" >&2; return 1 ;; esac
		ids+=("$id")
	done <<< "$rows"
	sudo docker stop "${ids[@]}" > /dev/null || { echo "stop 실패" >&2; return 1; }
	running=$(sudo docker ps -q --filter "$L") || { echo "정지 확인 조회 실패" >&2; return 1; }
	[ -z "$running" ] || { echo "실행 중인 beone-codef 컨테이너가 남음" >&2; return 1; }
}
oom() {
	local ids o
	ids=$("$@" ps -q -a) && [ -n "$ids" ] || { echo fail; return; }
	o=$(sudo docker inspect --format '{{.State.OOMKilled}}' $ids) || { echo fail; return; }
	[ -n "$o" ] && ! printf '%s\n' "$o" | grep -qvx false && echo false || echo "bad:$(printf '%s\n' "$o" | sort -u | tr '\n' ' ')"
}
while sleep 30; do
	m=$(free -m | awk '/^Mem:/{print $7}') || m=''
	d=$(df -P / | awk 'NR==2{print $5}' | tr -d %) || d=''
	c=$(health "${C[@]}"); a=$(health $APP); p=$(health $PUB); o=$(oom "${C[@]}")
	echo "$(date -u +%T) avail=${m}MiB disk=${d}% codef=[$c] app=[$a] pub=[$p] codef_oom=[$o]"
	r=''
	case "$m" in ''|*[!0-9]*) r="$r mem" ;; *) [ "$m" -ge 600 ] || r="$r mem" ;; esac
	case "$d" in ''|*[!0-9]*) r="$r disk" ;; *) [ "$d" -lt 85 ] || r="$r disk" ;; esac
	[ "$c" = ok ] || r="$r codef"
	[ "$a" = ok ] || r="$r app"
	[ "$p" = ok ] || r="$r pub"
	[ "$o" = false ] || r="$r oom"
	if [ -n "$r" ]; then
		echo "STOP:$r"
		if stop_codef; then echo CODEF_STOP_OK; else echo 'CODEF_STOP_FAILED: 사용자에게 즉시 보고'; fi
		exit 1
	fi
done
)
```

## 3. 확인 (K4~K6)

### 3-0. 호출 상한 누적 (LP-2)

호출 전, 연결 뒤, 동기화 뒤, 재기동 뒤마다 실행합니다. 창 시작 시각이 하나뿐이고 어느 API도 3을 넘지 않아야 합니다.

```bash
# block: budget
( set -euo pipefail
C=(sudo docker compose -f /opt/beone/verify/compose.yaml -f /opt/beone/codef/compose.codef.yaml -p beone-codef --env-file /opt/beone/codef/.env)
fail() { echo "중단: $*" >&2; exit 1; }
Q() { "${C[@]}" exec -T verify-mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --default-character-set=utf8mb4 -u"$MYSQL_USER" -N -B -e "$1" "$MYSQL_DATABASE"' sh "$1"; }
rows=$(Q 'SELECT api, window_start, used FROM codef_call_budgets ORDER BY window_start, api') || fail "조회 실패"
stat=$(Q 'SELECT COUNT(DISTINCT window_start), COALESCE(MAX(used), 0) FROM codef_call_budgets') || fail "조회 실패"
[ -n "$stat" ] || fail "조회 결과가 비어 있음(0건과 다름)"
read -r windows maxused <<< "$stat"
printf '%s\n' "$rows"
[ "$windows" -le 1 ] || fail "창 시작 시각이 ${windows}개(재기동 때 창을 바꿈). 호출을 멈춘다"
[ "$maxused" -le 3 ] || fail "API별 3회 초과(${maxused})"
echo "windows=$windows maxUsed=$maxused"
echo BUDGET_OK
)
```

| 시점 | 기대 |
|---|---|
| 호출 전 | 행 0(`windows=0 maxUsed=0`) |
| 연결 뒤 | `ACCOUNT_CREATE` 1, `TOKEN` 1 이상 3 이하, 창 시작 시각 1개이며 2-2에서 기록한 시작 시각(UTC로 저장)과 같음 |
| 동기화 뒤 | 위에 더해 `HELD_CARDS`·`APPROVALS`·`CARD_PERFORMANCES` 각 1 |
| 재기동 뒤 | 이전과 같은 행·값(창이 새로 생기지 않음) |

### 3-1. 정제 저장과 원장 불변 (K4)

원장(`ledger_transactions`)은 행 수만이 아니라 **모든 열의 내용 해시**로 호출 전후를 비교합니다. 같은 행이 수정돼도 해시가 바뀝니다. 기준값(행 수·해시, 비밀 아님)은 서버 홈 폴더 파일에 남깁니다.

```bash
# block: ledger-before   (2-3 호출 전에 한 번)
( set -euo pipefail
C=(sudo docker compose -f /opt/beone/verify/compose.yaml -f /opt/beone/codef/compose.codef.yaml -p beone-codef --env-file /opt/beone/codef/.env)
fail() { echo "중단: $*" >&2; exit 1; }
Q() { "${C[@]}" exec -T verify-mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --default-character-set=utf8mb4 -u"$MYSQL_USER" -N -B -e "$1" "$MYSQL_DATABASE"' sh "$1"; }
SNAP="SET SESSION group_concat_max_len = 67108864; SELECT COUNT(*), COALESCE(SHA2(GROUP_CONCAT(CONCAT_WS('|', ledger_id, user_id, user_card_id, source, entry_type, amount_won, transaction_date, COALESCE(transaction_time, '-'), merchant_class, payment_method, COALESCE(transaction_nature, '-'), COALESCE(merchant_name, '-'), COALESCE(original_ledger_id, '-'), COALESCE(simulated_payment_id, '-'), attribution_month, attribution_status, recorded_at) ORDER BY ledger_id SEPARATOR '\n'), 256), 'EMPTY') FROM ledger_transactions"
snap=$(Q "$SNAP") || fail "원장 조회 실패"
[ -n "$snap" ] || fail "원장 조회 결과가 비어 있음"
printf '%s\n' "$snap" > ~/beone-codef-ledger-before.txt
echo "ledger before: $snap"
echo LEDGER_BEFORE_OK
)
```

```bash
# block: ledger-after   (동기화 뒤)
( set -euo pipefail
C=(sudo docker compose -f /opt/beone/verify/compose.yaml -f /opt/beone/codef/compose.codef.yaml -p beone-codef --env-file /opt/beone/codef/.env)
fail() { echo "중단: $*" >&2; exit 1; }
Q() { "${C[@]}" exec -T verify-mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --default-character-set=utf8mb4 -u"$MYSQL_USER" -N -B -e "$1" "$MYSQL_DATABASE"' sh "$1"; }
SNAP="SET SESSION group_concat_max_len = 67108864; SELECT COUNT(*), COALESCE(SHA2(GROUP_CONCAT(CONCAT_WS('|', ledger_id, user_id, user_card_id, source, entry_type, amount_won, transaction_date, COALESCE(transaction_time, '-'), merchant_class, payment_method, COALESCE(transaction_nature, '-'), COALESCE(merchant_name, '-'), COALESCE(original_ledger_id, '-'), COALESCE(simulated_payment_id, '-'), attribution_month, attribution_status, recorded_at) ORDER BY ledger_id SEPARATOR '\n'), 256), 'EMPTY') FROM ledger_transactions"
before=$(cat ~/beone-codef-ledger-before.txt) || fail "호출 전 기준 파일 없음(ledger-before를 먼저 실행)"
after=$(Q "$SNAP") || fail "원장 조회 실패"
[ -n "$after" ] || fail "원장 조회 결과가 비어 있음"
echo "ledger before: $before"
echo "ledger after:  $after"
[ "$before" = "$after" ] || fail "원장 행 수 또는 내용이 바뀜(R8 위반). 4절로 간다"
conn=$(Q "SELECT COUNT(*), COALESCE(SUM(connected_id_ciphertext NOT LIKE 'v1:%'), 0) FROM codef_connections") || fail "조회 실패"
fp=$(Q "SELECT COUNT(*), COALESCE(SUM(NOT REGEXP_LIKE(approval_fingerprint, '^[0-9a-f]{64}\$', 'c')), 0) FROM codef_approval_rows") || fail "조회 실패"
held=$(Q 'SELECT COUNT(*) FROM codef_held_cards') || fail "조회 실패"
perf=$(Q 'SELECT COUNT(*) FROM codef_performance_references') || fail "조회 실패"
echo "connections(행 비v1)=$conn approvals(행 형식밖)=$fp held=$held performance=$perf"
read -r _ notv1 <<< "$conn"; read -r _ badfp <<< "$fp"
[ "$notv1" = 0 ] && [ "$badfp" = 0 ] || fail "형식 밖 행 발견"
echo LEDGER_AFTER_OK
)
```

| 확인 | 기대 | 이 확인이 보장하지 않는 것 |
|---|---|---|
| 원장 행 수·내용 해시 | 호출 전과 같음(CODEF는 원장에 쓰지 않음, R8) | `ledger_transactions` 밖(예: `simulated_payments`)의 변화. 비교 범위는 원장 표 하나 |
| `codef_connections` | 1행, `v1:` 접두어가 아닌 행 0(`SUM`은 행이 0개면 NULL이라 `COALESCE`로 0 처리) | 암호문 안의 내용이 올바른 암호화인지. DB CHECK도 같은 접두어만 보므로 형식 확인일 뿐 평문 부재의 증명이 아님 |
| `codef_approval_rows` | 형식(64자 소문자 16진) 밖 행 0 | 다른 열(가맹점명 등)에 원문이 섞였는지. DB CHECK가 이미 막는 형식이라 행 존재·형식 확인에 그침 |
| `codef_held_cards`·`codef_performance_references` | 동기화 결과 행이 있음(행 수 기록) | 값의 정확성 |

### 3-2. API (K5)

- `GET /api/v1/me` → `codefConnected=true`
- `GET /api/v1/me/cards` → CODEF로 만든 카드(`holdingSource=CODEF`, `UNMAPPED`)

### 3-3. 로그 점검 (K6)

`logs` 조회가 실패하거나 출력이 비어 있으면 "금지 패턴 0건"으로 읽지 않고 중단합니다.

```bash
# block: logs
( set -euo pipefail
C=(sudo docker compose -f /opt/beone/verify/compose.yaml -f /opt/beone/codef/compose.codef.yaml -p beone-codef --env-file /opt/beone/codef/.env)
fail() { echo "중단: $*" >&2; exit 1; }
logs=$("${C[@]}" logs --no-color verify-backend) || fail "logs 조회 실패(0건과 다름)"
[ -n "$logs" ] || fail "로그가 비어 있음(백엔드 미기동 의심, 0건과 다름)"
sens=$(printf '%s\n' "$logs" | grep -ciE 'password|passwd|connectedId=|cardNo|card_no|approvalNo' || true)
keys=$(printf '%s\n' "$logs" | grep -cE 'CODEF_(CLIENT_ID|CLIENT_SECRET|PUBLIC_KEY)=|CODEF_[A-Z_]*KEY=' || true)
printf '%s\n' "$logs" | grep -oE 'CF-[0-9]{5}' | sort | uniq -c || true   # 결과 코드만
echo "sensitive=$sens keyform=$keys"
[ "$sens" = 0 ] && [ "$keys" = 0 ] || fail "금지 패턴 발견. 로그를 공유하지 말고 4절로 간다"
echo LOGS_OK
)
```

- 이 점검은 **알려진 이름 패턴**(비밀번호·connectedId·카드번호·승인번호 필드명, 키 이름=값 형태)만 셉니다. 0건이어도 이름 없이 찍힌 값, 다른 형식, nginx·MySQL 등 다른 컨테이너 로그의 민감값이 없다는 보장은 아닙니다. 결과를 공유할 때는 이 숫자와 결과 코드만 씁니다.

## 4. 해제·폐기 (K7, 승인 ⑪)

### 4-1. 보관 중 (N15)

- MVP 시연이 끝날 때까지는 검증 DB 볼륨(`beone-codef_verify-mysql-data`)에 정제 데이터만 보관합니다. 컨테이너는 멈춰 둘 수 있습니다(아래 `stop`, 볼륨 보존).
- 보관하는 동안 CODEF 키는 `.env`에서 지울 수 있습니다. 순서는 **먼저 아래 `stop`으로 `STOP_OK`를 확인하고, 그 뒤에 키를 지웁니다.** `stop`·4-2 `destroy`는 compose 구성과 `.env`를 읽지 않는 라벨 경로라(0절), 키를 지운 뒤나 `.env`가 없어진 뒤에도 그대로 동작합니다. 정지·폐기를 위해 키를 다시 넣거나 더미 값을 넣지 않습니다.
- 키가 없는 동안 `C`를 쓰는 블록(`up`·`ready`·`ta`·`budget`·`ledger-*`·`logs`)은 구성 로딩에서 실패합니다. 이것은 의도한 닫힘입니다(키 없이 기동하지 않음). 다시 띄울 때는 2-2 `env-file`(기존 `.env` 보존)로 시작해 사용자가 키만 다시 입력하고 `names` → `up` 순서로 하며, **호출 상한 세 값(특히 창)은 그대로 둡니다**(2-2의 3번). 다시 띄운 뒤 3-0 `budget`으로 창이 하나인지 확인합니다. 볼륨(카운터 포함)은 `stop`이 지우지 않습니다.

```bash
# block: stop   (compose 구성·.env를 읽지 않음. 키가 없어도 동작)
( set -euo pipefail
L='label=com.docker.compose.project=beone-codef'
fail() { echo "중단: $*" >&2; exit 1; }
# 1) 대상 식별: 라벨 값 정확 일치 + 이름 접두어. 운영 beone-app·공개 beone·검증 beone-verify가 섞이면 아무것도 멈추지 않는다
rows=$(sudo docker ps -a --filter "$L" --format '{{.ID}}|{{.Names}}|{{.Label "com.docker.compose.project"}}') || fail "대상 조회 실패"
[ -n "$rows" ] || fail "beone-codef 컨테이너가 없음"
ids=()
while IFS='|' read -r id name proj; do
	[ "$proj" = beone-codef ] || fail "프로젝트 라벨이 beone-codef가 아님: $name"
	case "$name" in beone-codef-*) ;; *) fail "beone-codef가 아닌 이름: $name" ;; esac
	ids+=("$id")
done <<< "$rows"
# 2) 정지(볼륨·컨테이너는 남김)
sudo docker stop "${ids[@]}" > /dev/null || fail "stop 실패"
running=$(sudo docker ps -q --filter "$L") || fail "정지 확인 조회 실패(0개와 다름)"
[ -z "$running" ] || fail "실행 중인 beone-codef 컨테이너가 남음"
echo STOP_OK
)
```

### 4-2. MVP 수락 뒤 폐기

기본 방법은 **검증 DB를 통째로 지우는 것**입니다. 대상 식별이 어긋나거나 조회·삭제가 실패하면 그 자리에서 멈추고 다음 단계로 가지 않습니다.

1. 연결 해제: CODEF 쪽 connectedId 정리를 요청합니다(키가 있고 backend가 떠 있어야 정리됨). 사용자 Mac 터널에서 `DELETE /api/v1/me/codef/connection`(CSRF) → 204. 4-1에서 이미 키를 지웠다면 이 단계를 위해 키를 다시 넣을지는 사용자가 정합니다(다시 넣는다면 2-2·`names`·`up`). 다시 넣지 않으면 connectedId 정리는 하지 못한 것으로 기록합니다.
2. 아래 블록으로 검증 환경·DB·키 파일을 지우고 확인합니다. 이 블록은 compose 구성·`.env`를 읽지 않는 라벨 경로라(0절) 키가 없거나 `.env`가 없어도 동작합니다. `down -v`와 같은 범위(이 프로젝트의 컨테이너·볼륨·네트워크)를 지웁니다. 중간 단계(볼륨·네트워크 삭제 등)에서 멈췄으면 원인을 고친 뒤 **같은 블록을 다시 실행**합니다. 컨테이너가 이미 없으면 남은 볼륨·네트워크·키 파일만 정리하고, 이미 없는 키 파일은 실패로 보지 않습니다. 운영·공개·검증 대상이 섞이거나 라벨이 맞지 않는 `beone-codef_` 볼륨이 있으면 아무것도 지우지 않고 멈춥니다.

```bash
# block: destroy   (compose 구성·.env를 읽지 않음. 키가 없어도 동작. 중간에 멈췄으면 다시 실행해 남은 것만 정리)
( set -euo pipefail
L='label=com.docker.compose.project=beone-codef'
fail() { echo "중단: $*" >&2; exit 1; }
# 1) 대상 식별: 라벨 값 정확 일치 + 이름 접두어. 운영 beone-app·공개 beone·검증 beone-verify가 하나라도 섞이면 아무것도 지우지 않는다.
#    컨테이너가 없어도(이전 실행이 컨테이너까지 지우고 멈춘 경우) 남은 볼륨·네트워크·키 파일 정리를 계속한다
rows=$(sudo docker ps -a --filter "$L" --format '{{.ID}}|{{.Names}}|{{.Label "com.docker.compose.project"}}') || fail "컨테이너 조회 실패(0개와 다름)"
ids=()
while IFS='|' read -r id name proj; do
	[ -n "$id" ] || continue
	[ "$proj" = beone-codef ] || fail "프로젝트 라벨이 beone-codef가 아님: $name"
	case "$name" in beone-codef-*) ;; *) fail "beone-codef가 아닌 이름: $name" ;; esac
	ids+=("$id")
done <<< "$rows"
vrows=$(sudo docker volume ls --filter "$L" --format '{{.Name}}|{{.Label "com.docker.compose.project"}}') || fail "볼륨 조회 실패(0개와 다름)"
vols=()
while IFS='|' read -r name proj; do
	[ -n "$name" ] || continue
	[ "$proj" = beone-codef ] || fail "볼륨 라벨이 beone-codef가 아님: $name"
	case "$name" in beone-codef_*) ;; *) fail "beone-codef가 아닌 볼륨: $name" ;; esac
	vols+=("$name")
done <<< "$vrows"
# 이름은 beone-codef_인데 라벨이 다르거나 없는 볼륨이 있으면 지우지 않고 멈춘다(라벨로 못 찾는 데이터를 남긴 채 키를 지우지 않기 위해)
allv=$(sudo docker volume ls --format '{{.Name}}') || fail "볼륨 조회 실패(0개와 다름)"
while IFS= read -r name; do
	case "$name" in beone-codef_*) printf '%s\n' "${vols[@]:-}" | grep -qx "$name" || fail "라벨이 맞지 않는 beone-codef 볼륨: $name. 수동 확인" ;; esac
done <<< "$allv"
nrows=$(sudo docker network ls --filter "$L" --format '{{.Name}}|{{.Label "com.docker.compose.project"}}') || fail "네트워크 조회 실패(0개와 다름)"
nets=()
while IFS='|' read -r name proj; do
	[ -n "$name" ] || continue
	[ "$proj" = beone-codef ] || fail "네트워크 라벨이 beone-codef가 아님: $name"
	case "$name" in beone-codef_*) ;; *) fail "beone-codef가 아닌 네트워크: $name" ;; esac
	nets+=("$name")
done <<< "$nrows"
echo "대상: 컨테이너 ${#ids[@]}, 볼륨 ${#vols[@]}, 네트워크 ${#nets[@]}"
# 2) 검증 환경과 DB 통째 삭제(down -v와 같은 범위). 하나라도 실패하면 키 파일 삭제·확인으로 넘어가지 않는다
if [ "${#ids[@]}" -gt 0 ]; then
	sudo docker stop "${ids[@]}" > /dev/null || fail "stop 실패. 키 파일과 볼륨을 그대로 두고 보고한다"
	sudo docker rm "${ids[@]}" > /dev/null || fail "컨테이너 삭제 실패. 키 파일과 볼륨을 그대로 두고 보고한다"
fi
if [ "${#vols[@]}" -gt 0 ]; then sudo docker volume rm "${vols[@]}" > /dev/null || fail "볼륨 삭제 실패. 키 파일을 그대로 두고 보고한다(다시 실행하면 남은 것만 정리)"; fi
if [ "${#nets[@]}" -gt 0 ]; then sudo docker network rm "${nets[@]}" > /dev/null || fail "네트워크 삭제 실패. 키 파일을 그대로 두고 보고한다(다시 실행하면 남은 것만 정리)"; fi
# 3) 키 파일 삭제. 이미 없는 파일은 허용하고(rm -f), 그 밖의 삭제 오류만 실패로 본다
sudo rm -f /opt/beone/codef/.env /opt/beone/codef/compose.codef.yaml || fail "키 파일 삭제 실패"
if sudo test -d /opt/beone/codef; then sudo rmdir /opt/beone/codef || fail "폴더 삭제 실패(다른 파일이 남았는지 확인)"; fi
# 4) 확인. 조회 실패는 0개로 읽지 않는다
left=$(sudo docker ps -a -q --filter "$L") || fail "컨테이너 조회 실패(0개와 다름)"
[ -z "$left" ] || fail "beone-codef 컨테이너가 남음"
vols=$(sudo docker volume ls --format '{{.Name}}') || fail "볼륨 조회 실패(0개와 다름)"
n=$(printf '%s\n' "$vols" | grep -c '^beone-codef_' || true)
[ "$n" = 0 ] || fail "beone-codef 볼륨이 ${n}개 남음"
if sudo test -e /opt/beone/codef; then fail "/opt/beone/codef가 남음"; fi
rm -f ~/beone-codef-ledger-before.txt
echo DESTROY_OK
)
```

- `DESTROY_OK` 뒤 검증 런북 9절 블록 실행 여부는 **사용자에게 먼저 확인합니다.** 2-1 `verify-down`은 `stop`이라 `beone-verify` 볼륨(검증 DB·원장)이 남아 있고, 9절의 `$V down -v`가 그 볼륨을 지우기 때문입니다. 사용자가 삭제를 정하면 9절을 **그대로 전체** 실행합니다. 9절은 `beone-verify` 컨테이너·볼륨을 지우고, 이어서 검증 이미지·`/opt/beone/verify`·업로드 폴더를 지운 뒤 "영향 없음 확인"(운영 `beone-app` healthy·이미지 동일, 복원 회차 그대로, `VOLUMES_SAME`, 포트 18080 0)까지 합니다. `VOLUMES_SAME`은 검증 런북 3절 기준 파일(`/tmp/beone-verify-v0-volumes.txt`)이 남아 있어야 나오며, 서버 재부팅 등으로 지워져 `VOLUMES_UNKNOWN`이면 통과가 아닙니다. `beone-verify`를 다시 쓸 계획이면 9절을 실행하지 않고 사용자에게 확인합니다.

### 4-3. DB를 남기고 CODEF 데이터만 지울 때 (선택, N20 미결)

검증 DB를 남겨야 할 때만 씁니다. **대상은 `codef_*` 테이블뿐**이고, `beone-codef` 검증 DB에서만 실행합니다. **운영 DB(`beone-app`)와 원장(`ledger_transactions`)은 대상이 아닙니다.** 외래 키 순서는 자식 → 부모입니다.

```sql
-- beone-codef 검증 DB 전용. 운영 DB에서 실행하지 않는다
START TRANSACTION;
DELETE FROM codef_approval_rows;            -- codef_syncs 참조
DELETE FROM codef_performance_references;   -- codef_syncs 참조
DELETE FROM codef_held_cards;               -- codef_syncs·user_cards 참조
DELETE FROM codef_syncs;
DELETE FROM codef_connections;
COMMIT;
```

- 동기화로 만든 `user_cards` 행(`holding_source='CODEF'`)은 `codef_*`가 아니라 위 SQL 범위 밖입니다. 이 행까지 지울지는 **미결(N20)**입니다. 4-2의 통째 삭제는 이 행도 함께 지웁니다.
- 실행 전후로 각 테이블의 `COUNT(*)`를 기록하고, 3-1의 `ledger-after`로 원장 행 수·내용 해시가 그대로인지 확인합니다.
- `codef_call_budgets`(호출 상한 누적)는 이 SQL 범위 밖입니다. 지우면 상한이 다시 0부터 세어지므로 검증 기간 중에는 지우지 않습니다.

## 5. 남은 것

| 항목 | 상태 |
|---|---|
| 실 계정용 상품 호스트 | 미결(N19, 1절) |
| CODEF 카드 `user_cards` 행 폐기 범위 | 미결(N20, 4-3) |
| 계정 API 실호출 전 확인 항목 | [codef 문서](../domains/codef.md#비공개-실연동-프로필-codef-live-lp-1-193)의 '실호출 전 확인 필요' 목록 |
| 정식 과금 여부와 비용 상한 | N14의 비용 부분. 정식 과금이면 별도 승인 |
| 서버 블록의 실제 실행 확인 | 오프라인 스텁 점검만 함(0절). 서버 실행은 운영 담당 |
| 감시 중단 시 `stop`(검증 런북은 `down -v`) | 호출 상한 카운터 보존을 위한 차이(2-4). 다르게 정하려면 사용자 결정 |
| `beone-verify` 정지 시 `stop`(볼륨 보존, 2-1) | 재시도·원인 분석용 원장 보존. 볼륨 삭제(검증 런북 9절)는 사용자 결정 |
| 실패 관측 로그가 들어간 검증 이미지 | 운영 기준 `6974880`에는 없음(상단 "검증 이미지"). 이미지 준비 시점은 관리자 결정 |
| 정지·폐기의 라벨 경로 | 키·`.env` 없이도 멈추고 지우기 위한 방식(0절). 실 Docker에서 라벨 필터의 실행은 서버 실행 때 확인(오프라인은 `docker compose config` 보간과 가짜 `docker`로만 확인) |
