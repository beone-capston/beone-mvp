# 스테이징 한정 CODEF 켜기·끄기 (#275)

시연을 위해 **스테이징(`https://staging.beonepay.site`, 기본 인증 뒤)에서만** CODEF 실연동을 켜고 끄는 절차입니다(2026-11-27 사용자 결정). 운영(`beone-app`, `https://beonepay.site`)은 계속 CODEF를 쓰지 않습니다.

- **상태:** 코드·설정·런북만 준비했습니다. 서버 파일 반영, 키 입력, 재기동 같은 **켜는 실행은 하지 않았습니다.** 실행은 운영 담당(Aside)이 사용자에게 다시 확인받은 뒤 따로 합니다.
- **결정 기록:** [결정 40 변경 이력 초안](../decisions.md#d-25-확정-정책)(결정 40-7·38-8의 스테이징 예외). 최종 문구는 결정 필요입니다.
- **관련 문서:** [스테이징](staging.md), [서버 배포 스크립트](server-deploy.md#codef-가드), [CODEF 비공개 검증 런북](codef-private-verification.md)(호출 상한·로그 점검 블록의 원본)

## 0. 동작 원리

스테이징도 운영과 같은 공개 프로필 `prod`로 뜹니다. 그래서 다음 세 겹이 모두 맞아야만 CODEF가 켜집니다. 하나라도 어긋나면 닫힌 쪽(꺼짐 또는 기동·배포 거부)으로 끝납니다.

| 층 | 켜지는 조건 | 운영에서 |
|---|---|---|
| 배포 스크립트 `deploy.sh staging` | 스테이징 `.env`에 `BEONE_STAGING_CODEF_ENABLED=true`(따옴표 없이)가 한 번 있고, 필수 키 9개(아래 1-2)가 모두 비어 있지 않게 한 번씩 있으며, `/opt/beone/staging/compose.codef.yaml`이 있을 때만 그 오버레이를 `-f`로 붙입니다. 플래그가 없거나 `false`면 오버레이를 붙이지 않고, 그때 env에 `CODEF_`·`BEONE_CODEF_` 키가 있으면 배포를 거부합니다. 플래그 값이 `true`·`false`가 아니거나 두 번 있으면 거부합니다 | `deploy.sh production`은 운영 env에 플래그 이름이 있거나(값 무관), 운영 env·compose의 프로필 지정 줄에 `staging`이 있거나, 운영 폴더에 `compose.codef.yaml`이 있으면 거부합니다. 기존 CODEF 키·`codef-live` 거부도 그대로입니다 |
| 오버레이 [`deploy/staging/compose.codef.yaml`](../../deploy/staging/compose.codef.yaml) | 프로필을 `prod,staging,codef-live`로 바꾸고 플래그·키·상품 호스트·호출 상한을 backend에 넘깁니다. 하나라도 비면 `compose config`가 실패합니다 | 운영 compose([`deploy/compose.yaml`](../../deploy/compose.yaml))에는 이런 항목이 없습니다 |
| 백엔드 [`PublicProfileGuard`](../../backend/src/main/java/com/beone/api/common/config/PublicProfileGuard.java) | `prod`에서 `codef-live`와 CODEF 키 환경 변수는 스테이징 표지 프로필 `staging`과 `BEONE_STAGING_CODEF_ENABLED=true`(정확히 소문자 `true`)가 함께 있을 때만 허용합니다. 실 게이트웨이 조건은 `member & codef-live & (!prod \| staging)`입니다 | 운영은 `staging` 프로필이 없으므로 지금처럼 기동을 멈춥니다. 플래그가 `staging` 없이 있으면 값과 관계없이 기동을 멈춥니다 |

- **운영 compose에 `staging`이 실수로 들어가는 경우:** 백엔드 가드만으로는 막지 못합니다(`staging`과 플래그를 함께 넣으면 가드는 스테이징으로 봅니다). 이 경우는 `deploy.sh production`이 운영 env·compose의 프로필 지정 줄에서 `staging` 단어를, env에서 플래그 이름을 찾아 배포 전에 거부합니다([`deploy-selftest.sh`](../../deploy/scripts/deploy-selftest.sh)로 고정). 운영 서버에서 사람이 compose를 손으로 띄우는 경로는 이 검사를 거치지 않으므로 쓰지 않습니다.
- **접근:** CODEF API(`/api/v1/me/codef/**`)는 스테이징 web의 기본 인증 리스너(`127.0.0.1:18082`, 공개 nginx가 `staging.beonepay.site`로 연결)로만 들어옵니다. 배포 스모크 리스너(`127.0.0.1:18081`)는 점검 경로만 넘기므로 CODEF 경로가 없습니다. 기본 인증을 통과한 뒤에도 회원 세션·CSRF가 필요합니다.
- **호출 상한:** 카운터 표 `codef_call_budgets`(V15)는 스테이징 DB(`beone-staging_staging-mysql-data`)에 따로 있습니다. 비공개 검증(`beone-codef`)·운영 DB와 공유하지 않습니다.
- **자동 배포와의 관계:** 플래그가 켜진 동안 develop 자동 배포(`BeoneDeployStaging` → `deploy.sh staging`)도 같은 판단으로 오버레이를 붙여 키를 유지합니다. SSM 문서·워크플로는 env를 건드리지 않으므로 바꾸지 않았습니다.

## 1. 켜기

### 1-1. 서버 파일 반영 (Aside, 사용자 재확인 뒤)

이 PR이 develop에 병합된 뒤, 운영 담당이 서버 파일을 저장소 내용으로 갱신합니다. 서버 스크립트 갱신은 기존과 같은 승인 절차입니다.

| 서버 경로 | 저장소 파일 | 바뀐 점 |
|---|---|---|
| `/opt/beone/deploy/deploy.sh` | `deploy/scripts/deploy.sh` | 스테이징 플래그 판단, 운영 방어 이중화 |
| `/opt/beone/staging/compose.yaml` | `deploy/staging/compose.yaml` | 프로필 `prod,staging` |
| `/opt/beone/staging/compose.codef.yaml` | `deploy/staging/compose.codef.yaml` | 새 파일(권한 640) |

- 운영 폴더(`/opt/beone/app/`)에는 `compose.codef.yaml`을 두지 않습니다. 있으면 운영 배포가 거부됩니다.
- 백엔드 이미지는 이 PR의 백엔드 변경(가드·실 게이트웨이 조건)을 포함한 develop 이미지여야 합니다. 이전 이미지에 플래그·키를 넣으면 가드가 기동을 멈춥니다(닫힘).

### 1-2. 키 입력 (사용자 직접)

값은 **사용자가 서버에서 직접** 넣습니다. 에이전트와 운영 담당은 값을 보지 않고, 화면·로그·이슈·PR에 남기지 않습니다. 아래 블록은 입력을 화면에 표시하지 않고(`read -rs`) `.env` 끝에 붙입니다. 같은 이름이 이미 있으면 멈추므로 그때는 `sudoedit /opt/beone/staging/.env`로 정리한 뒤 다시 실행합니다.

```bash
# block: stg-codef-keys   (비밀값 5개. 입력은 화면에 보이지 않음)
( set -euo pipefail
E=/opt/beone/staging/.env
fail() { echo "중단: $*" >&2; exit 1; }
for k in CODEF_CLIENT_ID CODEF_CLIENT_SECRET CODEF_PUBLIC_KEY BEONE_CODEF_CONNECTED_ID_KEY BEONE_CODEF_APPROVAL_HMAC_KEY; do
	if sudo grep -qE "^[[:space:]]*(export[[:space:]]+)?$k=" "$E"; then fail "이미 있음: $k(sudoedit로 정리)"; fi
	IFS= read -rs -p "$k: " v; echo
	[ -n "$v" ] || fail "빈 값: $k"
	printf '%s=%s\n' "$k" "$v" | sudo tee -a "$E" > /dev/null
	unset v
done
echo KEYS_APPENDED
)
```

비밀이 아닌 값은 `sudoedit /opt/beone/staging/.env`로 넣습니다. 플래그는 **맨 마지막에** 넣습니다.

| 이름 | 값 | 비고 |
|---|---|---|
| `BEONE_CODEF_PRODUCT_HOST` | `https://sandbox.codef.io`·`https://development.codef.io`·`https://api.codef.io` 중 하나 | 사용할 키 종류에 맞춥니다(비공개 검증 런북 1절 N19) |
| `BEONE_CODEF_BUDGET_PER_API` | 권장 `10` | API별 최대 시도 수. 결정 필요(아래 5절) |
| `BEONE_CODEF_BUDGET_WINDOW_START` | 권장 `2026-11-26T00:00:00+09:00` | ISO-8601 오프셋 시각, 시작 포함 |
| `BEONE_CODEF_BUDGET_WINDOW_END` | 권장 `2026-12-10T00:00:00+09:00` | 끝 제외. 지나면 호출이 닫힘 |
| `BEONE_STAGING_CODEF_ENABLED` | `true` | 따옴표 없이 소문자. 다른 값은 배포 거부 |

- **창은 한 번 정하면 바꾸지 않습니다.** 카운터는 창 시작 시각별로 세므로 시작 시각을 바꾸면 0부터 다시 세어 상한이 무력해집니다. 끄고 다시 켤 때도 같은 값을 씁니다. 창을 늘리거나 새로 정하는 것은 사용자 승인 사항입니다.
- 형식은 비공개 검증과 같습니다(Spring 키 `beone.codef.budget.per-api`·`window-start`·`window-end`). 설정이 하나라도 없거나 창 밖이면 호출 0입니다.

### 1-3. 이름 확인

값은 출력하지 않고 이름이 한 번씩 있는지와 빈 값이 없는지만 봅니다. 비밀이 아닌 상한·호스트·플래그 값만 출력합니다.

```bash
# block: stg-codef-names
( set -euo pipefail
E=/opt/beone/staging/.env
fail() { echo "중단: $*" >&2; exit 1; }
bad=""
for k in BEONE_STAGING_CODEF_ENABLED CODEF_CLIENT_ID CODEF_CLIENT_SECRET CODEF_PUBLIC_KEY BEONE_CODEF_CONNECTED_ID_KEY \
	BEONE_CODEF_APPROVAL_HMAC_KEY BEONE_CODEF_PRODUCT_HOST BEONE_CODEF_BUDGET_PER_API BEONE_CODEF_BUDGET_WINDOW_START BEONE_CODEF_BUDGET_WINDOW_END; do
	sudo awk -F= -v k="$k" '!/^[[:space:]]*#/ && NF { n=$1; sub(/^[[:space:]]*(export[[:space:]]+)?/, "", n); sub(/[[:space:]]+$/, "", n)
		if (n == k) { c++; v=substr($0, index($0, "=") + 1); gsub(/^[[:space:]"\047]+|[[:space:]"\047]+$/, "", v); if (v != "") f++ } }
		END { exit !(c == 1 && f == 1) }' "$E" || bad="$bad $k"
done
[ -z "$bad" ] || fail "없음·빈 값·중복:$bad"
sudo grep -E '^(BEONE_STAGING_CODEF_ENABLED|BEONE_CODEF_PRODUCT_HOST|BEONE_CODEF_BUDGET_[A-Z_]+)=' "$E"   # 비밀 아님
sudo grep -qx 'BEONE_STAGING_CODEF_ENABLED=true' "$E" || fail "플래그가 정확히 true가 아님"
echo NAMES_OK
)
```

### 1-4. 재기동

현재 배포된 digest로 `deploy.sh staging`을 다시 실행합니다. 같은 이미지여도 backend 환경이 바뀌었으므로 컨테이너를 다시 만듭니다. 이 경로는 배포 스크립트의 가드를 그대로 거치므로 `docker compose up`을 손으로 실행하지 않습니다. 다음 develop 자동 배포를 기다려도 같은 결과입니다.

```bash
# block: stg-redeploy   (켜기·끄기 공통)
( set -euo pipefail
fail() { echo "중단: $*" >&2; exit 1; }
S=/opt/beone/deploy/state/staging.state
api=$(sudo sed -n 's/^api_digest=//p' "$S"); web=$(sudo sed -n 's/^web_digest=//p' "$S"); git=$(sudo sed -n 's/^git_sha=//p' "$S")
[ -n "$api" ] && [ -n "$web" ] && [ -n "$git" ] || fail "상태 파일을 읽지 못함"
sudo /opt/beone/deploy/deploy.sh staging "$api" "$web" "$git"
)
```

- 켤 때 출력에 `스테이징 CODEF 켬: 오버레이 사용`이 있어야 합니다. 종료 3이면 서비스는 바뀌지 않았고 사유(이름만)가 출력됩니다.
- 스모크가 실패하면 `deploy.sh`가 이전 digest로 복귀합니다. 이 경우 env는 그대로이므로 3절로 끕니다.

### 1-5. 기동 확인 (ready)

```bash
# block: stg-codef-ready
( set -euo pipefail
C=(sudo docker compose -f /opt/beone/staging/compose.yaml -f /opt/beone/staging/compose.codef.yaml -p beone-staging --env-file /opt/beone/staging/.env)
fail() { echo "중단: $*" >&2; exit 1; }
prof=$("${C[@]}" exec -T backend printenv SPRING_PROFILES_ACTIVE | tr -d '\r') || fail "프로필 조회 실패"
flag=$("${C[@]}" exec -T backend printenv BEONE_STAGING_CODEF_ENABLED | tr -d '\r') || fail "플래그 조회 실패"
[ "$prof" = prod,staging,codef-live ] || fail "프로필이 다름: $prof"
[ "$flag" = true ] || fail "플래그가 true가 아님"
# 세션 없이 CODEF 연결 조회: 등록됐으면 401, 미등록이면 404
code=$("${C[@]}" exec -T backend bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /api/v1/me/codef/connection HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3 && head -n1 <&3' | awk '{print $2}') || fail "경로 확인 실패"
echo "profile=$prof flag=$flag codefRoute=$code"
[ "$code" = 401 ] || fail "CODEF 경로가 등록되지 않음(기대 401, 받은 값 $code)"
echo READY_OK
)
```

### 1-6. 호출 상한 (budget)

[비공개 검증 런북 3-0 `budget`](codef-private-verification.md#3-0-호출-상한-누적-lp-2)과 같은 판단을 스테이징 서비스 이름(`mysql`·`backend`)으로 합니다. 켠 직후에는 행 0(`windows=0 maxUsed=0`)이어야 합니다. 이전에 켠 적이 있으면 그때 행이 남아 있고, 창 시작 시각은 하나여야 합니다.

```bash
# block: stg-codef-budget
( set -euo pipefail
C=(sudo docker compose -f /opt/beone/staging/compose.yaml -f /opt/beone/staging/compose.codef.yaml -p beone-staging --env-file /opt/beone/staging/.env)
fail() { echo "중단: $*" >&2; exit 1; }
Q() { "${C[@]}" exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --default-character-set=utf8mb4 -u"$MYSQL_USER" -N -B -e "$1" "$MYSQL_DATABASE"' sh "$1"; }
rows=$(Q 'SELECT api, window_start, used FROM codef_call_budgets ORDER BY window_start, api') || fail "조회 실패"
stat=$(Q 'SELECT COUNT(DISTINCT window_start), COALESCE(MAX(used), 0) FROM codef_call_budgets') || fail "조회 실패"
[ -n "$stat" ] || fail "조회 결과가 비어 있음(0건과 다름)"
read -r windows maxused <<< "$stat"
printf '%s\n' "$rows"
[ "$windows" -le 1 ] || fail "창 시작 시각이 ${windows}개(창을 바꿈). 호출을 멈춘다"
limit=$("${C[@]}" exec -T backend printenv BEONE_CODEF_BUDGET_PER_API | tr -d '\r\n') || fail "상한 설정 조회 실패"
case "$limit" in ''|*[!0-9]*) fail "상한 설정이 양의 정수가 아님: ${limit:-빈 값}" ;; esac
limit=$((10#$limit))
[ "$limit" -gt 0 ] || fail "상한 설정이 양의 정수가 아님: 0"
[ "$maxused" -le "$limit" ] || fail "API별 상한 ${limit}회 초과(${maxused})"
echo "windows=$windows maxUsed=$maxused limit=$limit remaining=$((limit - maxused))"
echo BUDGET_OK
)
```

## 2. 확인 (시연 전 1회)

1. 브라우저에서 `https://staging.beonepay.site`에 기본 인증으로 들어가 회원으로 로그인합니다. 스테이징은 실메일 인증을 쓰며 T-a(검증 DB 전용 인증 갱신)는 쓰지 않습니다.
2. CODEF 연결을 **1회**, 동기화를 **1회** 합니다. 실패해도 자동 재시도는 없고, 다시 시도하면 횟수에 셉니다.
3. 1-6 `stg-codef-budget`: 연결 뒤 `ACCOUNT_CREATE` 1·`TOKEN` 1 이상, 동기화 뒤 `HELD_CARDS`·`APPROVALS`·`CARD_PERFORMANCES` 각 1, 모두 `limit` 이하.
4. 아래 `stg-codef-logs`로 로그 금지 패턴 0건(`sensitive=0 keyform=0`)을 확인합니다. 패턴은 비공개 검증 런북 3-3과 같습니다(진단 로그 `CARD_NOT_LINKED` 오탐 정정 포함).
5. 아래 4절 메모리 확인을 연결·동기화 직후에 한 번 합니다.

```bash
# block: stg-codef-logs
( set -euo pipefail
C=(sudo docker compose -f /opt/beone/staging/compose.yaml -f /opt/beone/staging/compose.codef.yaml -p beone-staging --env-file /opt/beone/staging/.env)
fail() { echo "중단: $*" >&2; exit 1; }
logs=$("${C[@]}" logs --no-color backend) || fail "logs 조회 실패(0건과 다름)"
[ -n "$logs" ] || fail "로그가 비어 있음(0건과 다름)"
sens=$(printf '%s\n' "$logs" | grep -ciE 'password|passwd|connectedId=|card_?no([^A-Za-z0-9_]|$)|approvalNo' || true)
keys=$(printf '%s\n' "$logs" | grep -cE 'CODEF_(CLIENT_ID|CLIENT_SECRET|PUBLIC_KEY)=|CODEF_[A-Z_]*KEY=' || true)
printf '%s\n' "$logs" | grep -oE 'CF-[0-9]{5}' | sort | uniq -c || true   # 결과 코드만
echo "sensitive=$sens keyform=$keys"
[ "$sens" = 0 ] && [ "$keys" = 0 ] || fail "금지 패턴 발견. 로그를 공유하지 말고 3절로 끈다"
echo LOGS_OK
)
```

- 이 점검은 알려진 이름 패턴만 셉니다. 0건이 민감값이 전혀 없다는 보장은 아닙니다. 공유할 때는 숫자와 결과 코드만 씁니다.

## 3. 끄기

플래그와 CODEF 키를 **한 번에** 지운 뒤 재기동합니다. 플래그만 끄고 키를 남기면 다음 배포가 거부됩니다(의도한 닫힘). 백업 파일을 만들지 않습니다(키가 남기 때문).

```bash
# block: stg-codef-off
( set -euo pipefail
E=/opt/beone/staging/.env
fail() { echo "중단: $*" >&2; exit 1; }
sudo sed -i -E '/^[[:space:]]*(export[[:space:]]+)?(CODEF_|BEONE_CODEF_|BEONE_STAGING_CODEF_ENABLED[[:space:]]*=)/d' "$E" || fail "env 수정 실패"
left=$(sudo grep -cE '^[[:space:]]*(export[[:space:]]+)?(CODEF_|BEONE_CODEF_|BEONE_STAGING_CODEF_ENABLED)' "$E" || true)
[ "$left" = 0 ] || fail "CODEF·플래그 줄이 남음($left)"
echo ENV_CLEARED
)
```

그다음 1-4 `stg-redeploy`를 실행하고(출력에 `스테이징 CODEF 켬`이 **없어야** 함) 아래로 확인합니다.

```bash
# block: stg-codef-off-check
( set -euo pipefail
C=(sudo docker compose -f /opt/beone/staging/compose.yaml -p beone-staging --env-file /opt/beone/staging/.env)
fail() { echo "중단: $*" >&2; exit 1; }
prof=$("${C[@]}" exec -T backend printenv SPRING_PROFILES_ACTIVE | tr -d '\r') || fail "프로필 조회 실패"
n=$("${C[@]}" exec -T backend sh -c 'env | grep -cE "^(CODEF_|BEONE_CODEF_|BEONE_STAGING_CODEF_ENABLED=)" || true' | tr -d '\r')
code=$("${C[@]}" exec -T backend bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /api/v1/me/codef/connection HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3 && head -n1 <&3' | awk '{print $2}') || fail "경로 확인 실패"
echo "profile=$prof codefEnv=$n codefRoute=$code"
[ "$prof" = prod,staging ] && [ "$n" = 0 ] && [ "$code" = 404 ] || fail "꺼지지 않음"
echo OFF_OK
)
```

- **급히 멈춰야 할 때:** 위 절차를 기다릴 수 없으면 먼저 `sudo docker compose -f /opt/beone/staging/compose.yaml -p beone-staging --env-file /opt/beone/staging/.env stop backend`로 스테이징 backend만 멈춥니다(운영 영향 없음). 그 뒤 `stg-codef-off`부터 진행합니다.
- 호출 상한 카운터(`codef_call_budgets`)는 스테이징 DB에 남깁니다. 지우면 다시 켤 때 0부터 세어 상한이 무력해집니다.
- CODEF 연결 데이터(암호화된 connectedId 등)는 스테이징 DB에 남습니다. 지우는 범위·시점은 결정 필요입니다(5절).

## 4. 메모리 확인

스테이징 상한 합계는 1344MiB(mysql 512 + backend 768 + web 64, 약 1.4GB)이고 CODEF를 켜도 늘리지 않습니다. CODEF 호출은 응답 대기 시간이 길지만(상품 응답 최대 310초) 동기화 1회 단위라 힙 상한(`-Xmx384m`) 안에서 동작해야 합니다. 켠 직후와 연결·동기화 직후에 확인합니다.

```bash
# block: stg-mem
( set -euo pipefail
L='label=com.docker.compose.project=beone-staging'
ids=$(sudo docker ps -q --filter "$L")
[ -n "$ids" ] || { echo "중단: 실행 중인 스테이징 컨테이너 없음" >&2; exit 1; }
sudo docker stats --no-stream --format '{{.Name}} {{.MemUsage}} {{.MemPerc}}' $ids
for id in $ids; do
	sudo docker inspect -f '{{.Name}} oomKilled={{.State.OOMKilled}} restarts={{.RestartCount}} status={{.State.Status}}' "$id"
	pid=$(sudo docker inspect -f '{{.State.Pid}}' "$id")
	[ "$pid" != 0 ] && echo "  oom_score_adj=$(sudo cat /proc/$pid/oom_score_adj) oom_score=$(sudo cat /proc/$pid/oom_score)"
done
sudo docker events --since 2h --until 0s --filter "$L" --filter event=oom --format '{{.Time}} {{.Actor.Attributes.name}} oom' || true
)
```

| 확인 | 기대 |
|---|---|
| `MemUsage` | backend 768MiB, mysql 512MiB, web 64MiB 상한 안 |
| `oomKilled`·`restarts` | `false`·`0`(늘었으면 메모리 부족 의심) |
| `oom_score_adj` | mysql 800, backend·web 900(운영 컨테이너는 0이라 스테이징이 먼저 종료됨) |
| `oom` 이벤트 | 없음 |

메모리 부족으로 backend가 재시작되면 `on-failure:3` 뒤 멈춥니다. 이때는 3절로 끄고 사용자에게 알립니다.

## 5. 결정 필요 (권장안)

| 항목 | 권장안 |
|---|---|
| 플래그 꺼짐(없음·`false`)인데 CODEF 키가 있을 때 | **배포 거부**(현재 구현). 키를 지우지 않고 조용히 무시하면 끄는 절차가 반쯤 끝난 상태가 남는다 |
| 호출 상한·창 | API별 `10`, `2026-11-26T00:00:00+09:00` ~ `2026-12-10T00:00:00+09:00`. 비공개 검증(API별 3)과 별도 카운터 |
| 상품 호스트 | 사용할 키 종류에 맞춘다(N19 미결과 같음) |
| 시연 종료 뒤 스테이징 DB의 CODEF 연결 데이터 | 끄기와 함께 사용자가 정한다. 권장: 시연 종료 뒤 연결 해제(`DELETE /api/v1/me/codef/connection`)를 먼저 하고 끈다 |
| 결정 40-7·38-8 변경 문구 | [결정 기록 초안](../decisions.md#d-25-확정-정책) 그대로(스테이징 한정, 운영 불변) |
