#!/usr/bin/env bash
# CODEF 비공개 실연동 런북(docs/deployment/codef-private-verification.md)의 서버 블록을 서버 없이 점검한다.
#
# 런북의 "# block: <이름>" 블록을 그대로 꺼내, 가짜 sudo·docker·free·df·sleep을 PATH 앞에 둔 채 실행한다. 가짜 명령은
# 환경 변수로 실패·응답을 주입받고 실행한 동작을 기록만 한다. 실제 Docker·파일·서버·CODEF에는 손대지 않는다.
# 각 주입 사례의 블록 종료 코드, "실행되면 안 되는 동작"(예: config 실패 뒤 up)이 기록에 없는지,
# "반드시 실행돼야 하는 동작"(예: 감시 중단 시 stop)이 기록에 있는지 확인한다.
#
# 사용: deploy/scripts/codef-runbook-selftest.sh   (모든 사례가 기대대로면 종료 코드 0)
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DOC="$ROOT/docs/deployment/codef-private-verification.md"
# 가짜 명령과 기록만 담는 임시 폴더. 자동으로 지우지 않고 끝에 경로를 출력한다(수십 KB)
WORK="$(mktemp -d "${TMPDIR:-/tmp}/codef-runbook-selftest.XXXXXX")"
mkdir -p "$WORK/bin" "$WORK/home"

# 런북에서 블록 하나를 꺼낸다
block() {
	awk -v name="$1" '
		/^```bash/ { inb = 1; buf = ""; next }
		/^```/ && inb { if (hit) { printf "%s", buf; exit } inb = 0; hit = 0; next }
		inb { if ($0 ~ "^# block: " name "([[:space:]]|$)") hit = 1; buf = buf $0 "\n" }
	' "$DOC"
}

# 가짜 docker: compose 옵션을 건너뛰고 하위 명령별로 주입값을 따른다
cat > "$WORK/bin/docker" << 'EOF'
#!/usr/bin/env bash
log() { echo "$*" >> "$FAKE_LOG"; }
# 프로젝트(-p)별 주입값 이름: beone-codef → codef, beone-app → app, beone → pub, beone-verify → verify
pv() { local v="FAKE_${1}_${proj}"; printf '%s' "${!v-$2}"; }
if [ "${1:-}" = compose ]; then
	shift
	proj=""
	while [ $# -gt 0 ]; do
		case "$1" in
			-p) case "$2" in beone-codef) proj=codef ;; beone-app) proj=app ;; beone) proj=pub ;; beone-verify) proj=verify ;; *) proj=other ;; esac; shift 2 ;;
			-f | --env-file) shift 2 ;;
			*) break ;;
		esac
	done
	sub="$1"; shift
	case "$sub" in
		config)
			if [ "${1:-}" = --services ]; then
				rc=$(pv SVC_RC 0); [ "$rc" = 0 ] || exit "$rc"
				case "$proj" in codef | verify) d='verify-mysql\nverify-backend\nverify-web\n' ;; app) d='mysql\nbackend\n' ;; *) d='nginx\n' ;; esac
				printf '%b' "$(pv SVC "$d")"; exit 0
			fi
			log CONFIG; exit "${FAKE_CONFIG_RC:-0}" ;;
		up) log UP; exit 0 ;;
		ps)
			if [ "${1:-}" = -q ]; then
				rc=$(pv PSQ_RC 0); [ "$rc" = 0 ] || exit "$rc"
				case "$proj" in codef) d='c1\nc2\nc3\n' ;; app) d='a1\na2\n' ;; pub) d='p1\n' ;; *) d='' ;; esac
				printf '%b' "$(pv PSQ "$d")"; exit 0
			fi
			[ "${FAKE_PS_RC:-0}" = 0 ] || exit "$FAKE_PS_RC"; printf '%b' "${FAKE_PS-beone-codef-verify-backend-1\nbeone-codef-verify-mysql-1\nbeone-codef-verify-web-1\n}"; exit 0 ;;
		logs) [ "${FAKE_LOGS_RC:-0}" = 0 ] || exit "$FAKE_LOGS_RC"; printf '%b' "${FAKE_LOGS-started\nCODEF result CF-00000\n}"; exit 0 ;;
		stop) log STOP; exit 0 ;;
		down) log DOWN; exit "${FAKE_DOWN_RC:-0}" ;;
		exec)
			sql="${*: -1}"
			[ "${FAKE_Q_RC:-0}" = 0 ] || exit "$FAKE_Q_RC"
			case "$sql" in
				*"UPDATE members"*) printf '%b' "${FAKE_TA-1\n1\n}" ;;
				*"COUNT(DISTINCT window_start)"*) printf '%b' "${FAKE_BUDGET_STAT-1\t1\n}" ;;
				*"FROM codef_call_budgets"*) printf 'TOKEN\t2026-11-30 15:00:00.000\t1\n' ;;
				*"FROM ledger_transactions"*) printf '%b' "${FAKE_LEDGER-3\tabc\n}" ;;
				*"FROM codef_connections"*) printf '%b' "${FAKE_CONN-1\t0\n}" ;;
				*"FROM codef_approval_rows"*) printf '%b' "${FAKE_FP-5\t0\n}" ;;
				*) printf '2\n' ;;
			esac
			exit 0 ;;
	esac
	exit 0
fi
# 컨테이너 상태·OOM 조회. 기본은 c3(웹)만 헬스체크 없음, 나머지 running/healthy
if [ "${1:-}" = inspect ]; then
	[ "${FAKE_INSPECT_RC:-0}" = 0 ] || exit "$FAKE_INSPECT_RC"
	fmt="$3"; shift 3
	for id in "$@"; do
		case "$fmt" in
			*OOMKilled*) if [ "$id" = "${FAKE_OOM_ID:-}" ]; then echo true; else echo false; fi ;;
			*)
				if [ "$id" = "${FAKE_ST_ID:-}" ]; then [ -n "${FAKE_ST:-}" ] && echo "$FAKE_ST"
				elif [ "$id" = c3 ]; then echo running/none
				else echo running/healthy; fi ;;
		esac
	done
	exit 0
fi
if [ "${1:-}" = volume ]; then
	[ "${FAKE_VOL_RC:-0}" = 0 ] || exit "$FAKE_VOL_RC"
	printf '%b' "${FAKE_VOLS-beone-app_mysql-data\n}"
	exit 0
fi
exit 0
EOF

# 가짜 sudo: docker는 가짜 docker로 넘기고, 파일 작업은 기록과 주입값만 따른다
cat > "$WORK/bin/sudo" << 'EOF'
#!/usr/bin/env bash
log() { echo "$*" >> "$FAKE_LOG"; }
case "$1" in
	docker) shift; exec docker "$@" ;;
	rm) log RM; exit "${FAKE_RM_RC:-0}" ;;
	rmdir) log RMDIR; exit 0 ;;
	test) case "$*" in *compose.yaml*) exit "${FAKE_COMPOSE_RC:-0}" ;; *) exit "${FAKE_DIR_LEFT_RC:-1}" ;; esac ;;
	awk)
		[ "${FAKE_ENV_RC:-0}" = 0 ] || exit "$FAKE_ENV_RC"
		case "$*" in
			*'length($2) == 0'*) printf '%b' "${FAKE_ENV_EMPTY-}" ;;
			*) printf '%b' "${FAKE_ENV_NAMES}" ;;
		esac
		exit 0 ;;
	grep) printf 'BEONE_CODEF_BUDGET_PER_API=3\n'; exit 0 ;;
esac
exit 0
EOF

# 가짜 free·df·sleep. sleep은 FAKE_SLEEP_MAX(기본 1)번까지만 성공해 감시 루프를 끝낸다
cat > "$WORK/bin/free" << 'EOF'
#!/usr/bin/env bash
[ "${FAKE_FREE_RC:-0}" = 0 ] || exit "$FAKE_FREE_RC"
echo "              total        used        free      shared  buff/cache   available"
echo "Mem:           3800        1500         300          10        2000        ${FAKE_MEM-2000}"
EOF
cat > "$WORK/bin/df" << 'EOF'
#!/usr/bin/env bash
echo "Filesystem 1024-blocks Used Available Capacity Mounted"
echo "/dev/root 100 40 60 ${FAKE_DISK:-40}% /"
EOF
cat > "$WORK/bin/sleep" << 'EOF'
#!/usr/bin/env bash
n=$(( $(cat "$FAKE_SLEEP_COUNT" 2> /dev/null || echo 0) + 1 ))
echo "$n" > "$FAKE_SLEEP_COUNT"
[ "$n" -le "${FAKE_SLEEP_MAX:-1}" ]
EOF
chmod +x "$WORK/bin/docker" "$WORK/bin/sudo" "$WORK/bin/free" "$WORK/bin/df" "$WORK/bin/sleep"

ALL_NAMES='MYSQL_DATABASE\nMYSQL_USER\nMYSQL_PASSWORD\nMYSQL_ROOT_PASSWORD\nBEONE_VERIFY_API_IMAGE\nBEONE_VERIFY_NGINX_IMAGE\nCODEF_CLIENT_ID\nCODEF_CLIENT_SECRET\nCODEF_PUBLIC_KEY\nBEONE_CODEF_CONNECTED_ID_KEY\nBEONE_CODEF_APPROVAL_HMAC_KEY\nBEONE_CODEF_PRODUCT_HOST\nBEONE_CODEF_BUDGET_PER_API\nBEONE_CODEF_BUDGET_WINDOW_START\nBEONE_CODEF_BUDGET_WINDOW_END\n'
NO_BUDGET_NAMES='MYSQL_DATABASE\nMYSQL_USER\nMYSQL_PASSWORD\nMYSQL_ROOT_PASSWORD\nBEONE_VERIFY_API_IMAGE\nBEONE_VERIFY_NGINX_IMAGE\nCODEF_CLIENT_ID\nCODEF_CLIENT_SECRET\nCODEF_PUBLIC_KEY\nBEONE_CODEF_CONNECTED_ID_KEY\nBEONE_CODEF_APPROVAL_HMAC_KEY\nBEONE_CODEF_PRODUCT_HOST\n'

failures=0
echo "| 블록 | 주입 사례 | 기대 종료 코드 | 실제 | 실행되면 안 되는 동작 | 판정 |"
echo "|---|---|---|---|---|---|"
# run <블록> <설명> <기대 종료 코드> <금지 동작(쉼표, 없으면 ->)> [환경 변수=값...]
# 앞에 SEED_BEFORE=(원장 기준 파일 내용), REQUIRE=(반드시 실행돼야 하는 동작)를 붙일 수 있다
run() {
	local name="$1" desc="$2" want="$3" forbid="$4" code rc bad="" f
	shift 4
	code="$(block "$name")"
	[ -n "$code" ] || { echo "| $name | 블록 없음 | $want | - | - | FAIL |"; failures=$((failures + 1)); return; }
	: > "$WORK/log"
	rm -f "$WORK/sleepcount"
	rm -f "$WORK/home/beone-codef-ledger-before.txt"
	[ -n "${SEED_BEFORE:-}" ] && printf '%b' "$SEED_BEFORE" > "$WORK/home/beone-codef-ledger-before.txt"
	env -i PATH="$WORK/bin:/usr/bin:/bin" HOME="$WORK/home" FAKE_LOG="$WORK/log" FAKE_SLEEP_COUNT="$WORK/sleepcount" \
		FAKE_ENV_NAMES="$ALL_NAMES" "$@" \
		bash -c "$code" > "$WORK/out" 2>&1
	rc=$?
	if [ "$forbid" != - ]; then
		for f in ${forbid//,/ }; do grep -qx "$f" "$WORK/log" && bad="$bad $f"; done
	fi
	local miss=""
	if [ -n "${REQUIRE:-}" ]; then grep -qx "$REQUIRE" "$WORK/log" || miss=" (필수 동작 $REQUIRE 없음)"; fi
	local verdict=PASS
	{ [ "$rc" = "$want" ] && [ -z "$bad" ] && [ -z "$miss" ]; } || { verdict=FAIL; failures=$((failures + 1)); }
	echo "| $name | $desc | $want | $rc | ${forbid}${bad:+ (실행됨:$bad)}${miss} | $verdict |"
}

run verify-down "정상" 0 -
run verify-down "down -v 실패" 1 - FAKE_DOWN_RC=1
run verify-down "beone-verify 조회 실패" 1 - FAKE_PSQ_RC_verify=1
run verify-down "beone-verify 컨테이너 남음" 1 - FAKE_PSQ_verify='v1\n'
run verify-down "볼륨 조회 실패" 1 - FAKE_VOL_RC=1
run verify-down "beone-verify 볼륨 남음" 1 - FAKE_VOLS='beone-verify_verify-mysql-data\n'
run verify-down "compose.yaml 없음(9절로 지움)" 1 - FAKE_COMPOSE_RC=1
run up "정상" 0 -
run up "config 실패" 1 UP FAKE_CONFIG_RC=1
run ready "정상(web은 running/none)" 0 -
run ready "backend starting" 1 - FAKE_ST_ID=c2 FAKE_ST=running/starting
run ready "beone-codef 컨테이너 수 부족" 1 - FAKE_PSQ_codef='c1\nc2\n'
run ready "beone-codef 조회 실패" 1 - FAKE_PSQ_RC_codef=1
run ready "운영 컨테이너 exited" 1 - FAKE_ST_ID=a2 FAKE_ST=exited/none
run ready "운영 컨테이너 paused" 1 - FAKE_ST_ID=a1 FAKE_ST=paused/healthy
run ready "운영 config 조회 실패" 1 - FAKE_SVC_RC_app=1
run ready "운영 컨테이너 없음" 1 - FAKE_PSQ_app=''
run ready "공개 nginx 조회 실패" 1 - FAKE_PSQ_RC_pub=1
run ready "inspect 실패" 1 - FAKE_INSPECT_RC=1
run ready "inspect 결과 일부 빠짐" 1 - FAKE_ST_ID=a1 FAKE_ST=
run ready "가용 메모리 부족" 1 - FAKE_MEM=500
run ready "메모리 조회 실패" 1 - FAKE_FREE_RC=1
run names "이름·값 모두 있음" 0 -
run names "호출 상한 키 3개 없음" 1 - FAKE_ENV_NAMES="$NO_BUDGET_NAMES"
run names "빈 값 있음" 1 - FAKE_ENV_EMPTY='CODEF_PUBLIC_KEY\n'
run names ".env 읽기 실패" 1 - FAKE_ENV_RC=1
run ta "정상" 0 - EMAIL=smoke-a-1@example.test
run ta "EMAIL 미지정" 1 -
run ta "합성 주소 아님" 1 - EMAIL=user@gmail.com
run ta "0·0(주소 오타)" 1 - EMAIL=smoke-a-1@example.test FAKE_TA='0\n0\n'
run ta "DB 실행 실패" 1 - EMAIL=smoke-a-1@example.test FAKE_Q_RC=1
run watch "정상(한 번 돌고 끝)" 0 STOP,DOWN
REQUIRE=STOP run watch "가용 메모리 부족" 1 DOWN FAKE_MEM=500
REQUIRE=STOP run watch "메모리 값 없음" 1 DOWN FAKE_FREE_RC=1
REQUIRE=STOP run watch "디스크 85%" 1 DOWN FAKE_DISK=85
REQUIRE=STOP run watch "운영 컨테이너 restarting" 1 DOWN FAKE_ST_ID=a1 FAKE_ST=restarting/none
REQUIRE=STOP run watch "운영 조회 실패" 1 DOWN FAKE_PSQ_RC_app=1
REQUIRE=STOP run watch "공개 nginx 없음" 1 DOWN FAKE_PSQ_pub=''
REQUIRE=STOP run watch "beone-codef 컨테이너 unhealthy" 1 DOWN FAKE_ST_ID=c1 FAKE_ST=running/unhealthy
REQUIRE=STOP run watch "beone-codef OOMKilled" 1 DOWN FAKE_OOM_ID=c2
REQUIRE=STOP run watch "inspect 실패" 1 DOWN FAKE_INSPECT_RC=1
run budget "창 1개·최대 1" 0 -
run budget "호출 전 행 0" 0 - FAKE_BUDGET_STAT='0\t0\n'
run budget "창 2개(재기동 때 창 변경)" 1 - FAKE_BUDGET_STAT='2\t1\n'
run budget "API별 3회 초과" 1 - FAKE_BUDGET_STAT='1\t4\n'
run budget "DB 조회 실패" 1 - FAKE_Q_RC=1
run budget "조회 결과 비어 있음" 1 - FAKE_BUDGET_STAT=''
run ledger-before "정상" 0 -
run ledger-before "원장 조회 실패" 1 - FAKE_Q_RC=1
SEED_BEFORE='3\tabc\n' run ledger-after "행 수·내용 같음" 0 -
SEED_BEFORE='3\tabc\n' run ledger-after "행 수 같고 내용 해시 다름" 1 - FAKE_LEDGER='3\tdef\n'
SEED_BEFORE='3\tabc\n' run ledger-after "행 수 다름" 1 - FAKE_LEDGER='4\tabc\n'
run ledger-after "기준 파일 없음" 1 -
SEED_BEFORE='3\tabc\n' run ledger-after "원장 조회 실패" 1 - FAKE_Q_RC=1
SEED_BEFORE='3\tabc\n' run ledger-after "연결 0행(SUM NULL → 0)" 0 - FAKE_CONN='0\t0\n'
SEED_BEFORE='3\tabc\n' run ledger-after "v1 아닌 연결 행" 1 - FAKE_CONN='1\t1\n'
run logs "정상" 0 -
run logs "logs 조회 실패" 1 - FAKE_LOGS_RC=1
run logs "로그 비어 있음" 1 - FAKE_LOGS=''
run logs "금지 패턴 있음" 1 - FAKE_LOGS='password=x\n'
run stop "정상" 0 -
run stop "운영 컨테이너 섞임" 1 STOP FAKE_PS='beone-codef-verify-backend-1\nbeone-app-backend-1\n'
run destroy "정상" 0 -
run destroy "운영 컨테이너 섞임(오식별)" 1 DOWN,RM FAKE_PS='beone-codef-verify-backend-1\nbeone-app-backend-1\n'
run destroy "검증 컨테이너 섞임(오식별)" 1 DOWN,RM FAKE_PS='beone-verify-verify-backend-1\n'
run destroy "대상 조회 실패" 1 DOWN,RM FAKE_PS_RC=1
run destroy "대상 없음" 1 DOWN,RM FAKE_PS=''
run destroy "down -v 실패" 1 RM,RMDIR FAKE_DOWN_RC=1
run destroy "키 파일 삭제 실패" 1 RMDIR FAKE_RM_RC=1
run destroy "볼륨 조회 실패" 1 - FAKE_VOL_RC=1
run destroy "볼륨 남음" 1 - FAKE_VOLS='beone-codef_verify-mysql-data\n'
run destroy "폴더 남음" 1 - FAKE_DIR_LEFT_RC=0

echo
echo "임시 폴더: $WORK"
if [ "$failures" -eq 0 ]; then
	echo "runbook self-test PASS"
	exit 0
fi
echo "runbook self-test FAIL ($failures)" >&2
exit 1
