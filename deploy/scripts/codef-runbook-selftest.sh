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
# mktemp가 실패하거나 빈 값을 주면 WORK가 비어 스텁이 /bin 아래로 써질 수 있으므로, 블록 실행 전에 멈춘다
die() { echo "self-test 준비 실패: $*" >&2; exit 1; }
WORK="$(mktemp -d "${TMPDIR:-/tmp}/codef-runbook-selftest.XXXXXX")" || die "임시 폴더를 만들지 못함"
[ -n "$WORK" ] && [ -d "$WORK" ] || die "임시 폴더 경로가 비었거나 폴더가 아님"
case "$WORK" in /*) ;; *) die "임시 폴더 경로가 절대 경로가 아님: $WORK" ;; esac
mkdir -p "$WORK/bin" "$WORK/home" || die "하위 폴더를 만들지 못함"

# 런북에서 블록 하나를 꺼낸다
block() {
	awk -v name="$1" '
		/^```bash/ { inb = 1; buf = ""; next }
		/^```/ && inb { if (hit) { printf "%s", buf; exit } inb = 0; hit = 0; next }
		inb { if ($0 ~ "^# block: " name "([[:space:]]|$)") hit = 1; buf = buf $0 "\n" }
	' "$DOC"
}

# 가짜 docker: compose 옵션을 읽고 하위 명령별로 주입값을 따른다.
# compose.codef.yaml을 -f로 받으면 ${CODEF_*:?} 필수 보간을 흉내 낸다. FAKE_KEYS가 present가 아니면(empty·deleted·nofile)
# 실제 Compose처럼 하위 명령(ps·stop·down·config 포함)에 닿기 전에 구성 로딩 실패(종료 1)로 끝난다.
# 라벨 경로(docker ps/stop/rm/volume/network --filter label=…)는 compose 구성을 읽지 않으므로 FAKE_KEYS와 무관하다
cat > "$WORK/bin/docker" << 'EOF' || die "docker 스텁을 쓰지 못함"
#!/usr/bin/env bash
log() { echo "$*" >> "$FAKE_LOG"; }
# 프로젝트(-p)별 주입값 이름: beone-codef → codef, beone-app → app, beone → pub, beone-verify → verify
pv() { local v="FAKE_${1}_${proj}"; printf '%s' "${!v-$2}"; }
if [ "${1:-}" = compose ]; then
	shift
	proj="" overlay=0
	while [ $# -gt 0 ]; do
		case "$1" in
			-p) case "$2" in beone-codef) proj=codef ;; beone-app) proj=app ;; beone) proj=pub ;; beone-verify) proj=verify ;; *) proj=other ;; esac; shift 2 ;;
			-f) case "$2" in *compose.codef.yaml) overlay=1 ;; esac; shift 2 ;;
			--env-file) shift 2 ;;
			*) break ;;
		esac
	done
	sub="$1"; shift
	if [ "$overlay" = 1 ]; then
		case "${FAKE_KEYS:-present}" in
			present) ;;
			nofile) log COMPOSE_LOAD_FAIL; echo "couldn't find env file: /opt/beone/codef/.env" >&2; exit 1 ;;
			*) log COMPOSE_LOAD_FAIL; echo 'error while interpolating services.verify-backend.environment.CODEF_CLIENT_ID: required variable CODEF_CLIENT_ID is missing a value' >&2; exit 1 ;;
		esac
	fi
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
		stop) log STOP; exit "${FAKE_STOP_RC:-0}" ;;
		down) log DOWN; exit "${FAKE_DOWN_RC:-0}" ;;
		exec)
			sql="${*: -1}"
			[ "${FAKE_Q_RC:-0}" = 0 ] || exit "$FAKE_Q_RC"
			case "$sql" in
				BEONE_CODEF_BUDGET_PER_API)   # budget 블록의 printenv(실행 중인 백엔드의 상한 설정)
					[ "${FAKE_LIMIT_RC:-0}" = 0 ] || exit "$FAKE_LIMIT_RC"; printf '%b' "${FAKE_LIMIT-3\n}" ;;
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
# 라벨 경로. 기본 대상은 beone-codef 컨테이너 3개·볼륨 1개·네트워크 1개. 삭제 뒤 조회는 남은 것 없음
has() { grep -qx "$1" "$FAKE_LOG"; }
case "${1:-} ${2:-}" in
	"ps -a" | "ps -q")
		case "$*" in *label=com.docker.compose.project=beone-codef*) ;; *) exit 0 ;; esac
		if [ "$2" = -q ]; then
			[ "${FAKE_LRUN_RC:-0}" = 0 ] || exit "$FAKE_LRUN_RC"; printf '%b' "${FAKE_LRUN-}"; exit 0
		fi
		if [ "$3" = -q ]; then   # 삭제 뒤 확인(ps -a -q)
			has DRM && { printf '%b' "${FAKE_LPS_AFTER-}"; exit 0; }
		fi
		[ "${FAKE_LPS_RC:-0}" = 0 ] || exit "$FAKE_LPS_RC"
		printf '%b' "${FAKE_LPS-c1|beone-codef-verify-mysql-1|beone-codef\nc2|beone-codef-verify-backend-1|beone-codef\nc3|beone-codef-verify-web-1|beone-codef\n}"; exit 0 ;;
	"stop "*) log DSTOP; exit "${FAKE_DSTOP_RC:-0}" ;;
	"rm "*) log DRM; exit "${FAKE_DRM_RC:-0}" ;;
	"network ls") [ "${FAKE_LNET_RC:-0}" = 0 ] || exit "$FAKE_LNET_RC"; printf '%b' "${FAKE_LNETS-beone-codef_default|beone-codef\n}"; exit 0 ;;
	"network rm") log DNRM; exit "${FAKE_DNRM_RC:-0}" ;;
	"volume rm") log DVRM; exit "${FAKE_DVRM_RC:-0}" ;;
esac
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
	case "$*" in
		*label=com.docker.compose.project=beone-codef*)
			[ "${FAKE_LVOL_RC:-0}" = 0 ] || exit "$FAKE_LVOL_RC"
			printf '%b' "${FAKE_LVOLS-beone-codef_verify-mysql-data|beone-codef\n}"; exit 0 ;;
	esac
	[ "${FAKE_VOL_RC:-0}" = 0 ] || exit "$FAKE_VOL_RC"
	printf '%b' "${FAKE_VOLS-beone-app_mysql-data\n}"
	exit 0
fi
exit 0
EOF

# 가짜 sudo: docker는 가짜 docker로 넘기고, 파일 작업은 기록과 주입값만 따른다
cat > "$WORK/bin/sudo" << 'EOF' || die "sudo 스텁을 쓰지 못함"
#!/usr/bin/env bash
log() { echo "$*" >> "$FAKE_LOG"; }
case "$1" in
	docker) shift; exec docker "$@" ;;
	# .env가 없을 때(FAKE_KEYS=nofile) -f 없는 rm은 실제처럼 실패한다
	rm) log RM; case " $* " in *" -f "*) ;; *) [ "${FAKE_KEYS:-present}" = nofile ] && exit 1 ;; esac; exit "${FAKE_RM_RC:-0}" ;;
	rmdir) log RMDIR; exit 0 ;;
	install) case "$*" in *codef/.env*) log INSTALL_ENV; exit "${FAKE_INSTALL_RC:-0}" ;; *) exit "${FAKE_INSTALL_DIR_RC:-0}" ;; esac ;;
	stat) [ "${FAKE_STAT_RC:-0}" = 0 ] || exit "$FAKE_STAT_RC"; echo "${FAKE_STAT-600 root}"; exit 0 ;;
	test)
		case "$*" in
			*compose.yaml*) exit "${FAKE_COMPOSE_RC:-0}" ;;
			*codef/.env*) exit "${FAKE_ENVFILE_RC:-1}" ;;          # 기본: .env 없음
			"test -d /opt/beone/codef") exit "${FAKE_DIR_RC:-0}" ;;  # 삭제 전 폴더 있음
			*) exit "${FAKE_DIR_LEFT_RC:-1}" ;;                     # 삭제 뒤 남았는지
		esac ;;
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
cat > "$WORK/bin/free" << 'EOF' || die "free 스텁을 쓰지 못함"
#!/usr/bin/env bash
[ "${FAKE_FREE_RC:-0}" = 0 ] || exit "$FAKE_FREE_RC"
echo "              total        used        free      shared  buff/cache   available"
echo "Mem:           3800        1500         300          10        2000        ${FAKE_MEM-2000}"
EOF
cat > "$WORK/bin/df" << 'EOF' || die "df 스텁을 쓰지 못함"
#!/usr/bin/env bash
echo "Filesystem 1024-blocks Used Available Capacity Mounted"
echo "/dev/root 100 40 60 ${FAKE_DISK:-40}% /"
EOF
cat > "$WORK/bin/sleep" << 'EOF' || die "sleep 스텁을 쓰지 못함"
#!/usr/bin/env bash
n=$(( $(cat "$FAKE_SLEEP_COUNT" 2> /dev/null || echo 0) + 1 ))
echo "$n" > "$FAKE_SLEEP_COUNT"
[ "$n" -le "${FAKE_SLEEP_MAX:-1}" ]
EOF
chmod +x "$WORK/bin/docker" "$WORK/bin/sudo" "$WORK/bin/free" "$WORK/bin/df" "$WORK/bin/sleep" || die "스텁 실행 권한을 주지 못함"

ALL_NAMES='MYSQL_DATABASE\nMYSQL_USER\nMYSQL_PASSWORD\nMYSQL_ROOT_PASSWORD\nBEONE_VERIFY_API_IMAGE\nBEONE_VERIFY_NGINX_IMAGE\nCODEF_CLIENT_ID\nCODEF_CLIENT_SECRET\nCODEF_PUBLIC_KEY\nBEONE_CODEF_CONNECTED_ID_KEY\nBEONE_CODEF_APPROVAL_HMAC_KEY\nBEONE_CODEF_PRODUCT_HOST\nBEONE_CODEF_BUDGET_PER_API\nBEONE_CODEF_BUDGET_WINDOW_START\nBEONE_CODEF_BUDGET_WINDOW_END\n'
NO_BUDGET_NAMES='MYSQL_DATABASE\nMYSQL_USER\nMYSQL_PASSWORD\nMYSQL_ROOT_PASSWORD\nBEONE_VERIFY_API_IMAGE\nBEONE_VERIFY_NGINX_IMAGE\nCODEF_CLIENT_ID\nCODEF_CLIENT_SECRET\nCODEF_PUBLIC_KEY\nBEONE_CODEF_CONNECTED_ID_KEY\nBEONE_CODEF_APPROVAL_HMAC_KEY\nBEONE_CODEF_PRODUCT_HOST\n'

failures=0
echo "| 블록 | 주입 사례 | 기대 종료 코드 | 실제 | 실행되면 안 되는 동작 | 판정 |"
echo "|---|---|---|---|---|---|"
# run <블록> <설명> <기대 종료 코드> <금지 동작(쉼표, 없으면 ->)> [환경 변수=값...]
# 앞에 SEED_BEFORE=(원장 기준 파일 내용), REQUIRE=(반드시 실행돼야 하는 동작), EXPECT_OUT=(출력에 있어야 하는 줄의 일부)를 붙일 수 있다
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
	if [ -n "${EXPECT_OUT:-}" ]; then grep -qF -- "$EXPECT_OUT" "$WORK/out" || miss="$miss (출력 '$EXPECT_OUT' 없음)"; fi
	local verdict=PASS
	{ [ "$rc" = "$want" ] && [ -z "$bad" ] && [ -z "$miss" ]; } || { verdict=FAIL; failures=$((failures + 1)); }
	echo "| $name | $desc | $want | $rc | ${forbid}${bad:+ (실행됨:$bad)}${miss} | $verdict |"
}

# verify-down은 stop(볼륨 보존)이다. down(-v)은 어떤 경우에도 실행되면 안 된다
REQUIRE=STOP run verify-down "정상(stop, down 안 함)" 0 DOWN
run verify-down "stop 실패" 1 DOWN FAKE_STOP_RC=1
run verify-down "beone-verify 조회 실패" 1 DOWN FAKE_PSQ_RC_verify=1
run verify-down "실행 중 beone-verify 컨테이너 남음" 1 DOWN FAKE_PSQ_verify='v1\n'
run verify-down "볼륨 조회 실패" 1 DOWN FAKE_VOL_RC=1
REQUIRE=STOP run verify-down "beone-verify 볼륨 남음(보존, 통과)" 0 DOWN FAKE_VOLS='beone-verify_verify-mysql-data\n'
run verify-down "compose.yaml 없음(9절로 지움)" 1 DOWN FAKE_COMPOSE_RC=1
run up "정상" 0 -
run up "config 실패" 1 UP FAKE_CONFIG_RC=1
run up "키 빈 값(보간 실패, 닫힘)" 1 UP FAKE_KEYS=empty
run up "키 삭제(보간 실패, 닫힘)" 1 UP FAKE_KEYS=deleted
run up ".env 없음(구성 로딩 실패, 닫힘)" 1 UP FAKE_KEYS=nofile
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
run ready "키 삭제(구성 로딩 실패)" 1 - FAKE_KEYS=deleted
REQUIRE=INSTALL_ENV run env-file ".env 없음 → 빈 파일 생성" 0 -
run env-file ".env 있음 → 보존(덮어쓰지 않음)" 0 INSTALL_ENV FAKE_ENVFILE_RC=0
run env-file "권한 다름" 1 - FAKE_ENVFILE_RC=0 FAKE_STAT='644 root'
run env-file "권한 조회 실패" 1 - FAKE_STAT_RC=1
run env-file ".env 생성 실패" 1 - FAKE_INSTALL_RC=1
run env-file "폴더 준비 실패" 1 INSTALL_ENV FAKE_INSTALL_DIR_RC=1
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
REQUIRE=DSTOP run watch "가용 메모리 부족" 1 DOWN FAKE_MEM=500
REQUIRE=DSTOP run watch "메모리 값 없음" 1 DOWN FAKE_FREE_RC=1
REQUIRE=DSTOP run watch "디스크 85%" 1 DOWN FAKE_DISK=85
REQUIRE=DSTOP run watch "운영 컨테이너 restarting" 1 DOWN FAKE_ST_ID=a1 FAKE_ST=restarting/none
REQUIRE=DSTOP run watch "운영 조회 실패" 1 DOWN FAKE_PSQ_RC_app=1
REQUIRE=DSTOP run watch "공개 nginx 없음" 1 DOWN FAKE_PSQ_pub=''
REQUIRE=DSTOP run watch "beone-codef 컨테이너 unhealthy" 1 DOWN FAKE_ST_ID=c1 FAKE_ST=running/unhealthy
REQUIRE=DSTOP run watch "beone-codef OOMKilled" 1 DOWN FAKE_OOM_ID=c2
REQUIRE=DSTOP run watch "inspect 실패" 1 DOWN FAKE_INSPECT_RC=1
REQUIRE=DSTOP run watch "감시 중 키 삭제(codef fail-config → 라벨 경로 중지)" 1 DOWN,STOP FAKE_KEYS=deleted
REQUIRE=DSTOP run watch "감시 중 .env 없음(라벨 경로 중지)" 1 DOWN,STOP FAKE_KEYS=nofile
run watch "중지 대상에 운영 컨테이너 섞임(중지 안 함)" 1 DSTOP,DOWN FAKE_MEM=500 FAKE_LPS='c1|beone-codef-verify-mysql-1|beone-codef\na1|beone-app-backend-1|beone-app\n'
run budget "창 1개·최대 1" 0 -
run budget "호출 전 행 0" 0 - FAKE_BUDGET_STAT='0\t0\n'
run budget "창 2개(재기동 때 창 변경)" 1 - FAKE_BUDGET_STAT='2\t1\n'
run budget "API별 3회 초과(상한 3)" 1 - FAKE_BUDGET_STAT='1\t4\n'
run budget "상한 5로 승인, 사용 4(통과)" 0 - FAKE_BUDGET_STAT='1\t4\n' FAKE_LIMIT='5\n'
run budget "상한 5, 사용 6(초과)" 1 - FAKE_BUDGET_STAT='1\t6\n' FAKE_LIMIT='5\n'
run budget "상한 설정 조회 실패" 1 - FAKE_LIMIT_RC=1
run budget "상한 설정 빈 값" 1 - FAKE_LIMIT=''
run budget "상한 설정 숫자 아님" 1 - FAKE_LIMIT='abc\n'
run budget "상한 설정 0" 1 - FAKE_LIMIT='0\n'
run budget "상한 설정 000(정규화 뒤 0)" 1 - FAKE_LIMIT='000\n'
EXPECT_OUT='limit=10 remaining=9' run budget "상한 010(십진 10), 사용 1" 0 - FAKE_BUDGET_STAT='1\t1\n' FAKE_LIMIT='010\n'
EXPECT_OUT='limit=8 remaining=8' run budget "상한 008(8진수로 읽으면 오류), 사용 0" 0 - FAKE_BUDGET_STAT='0\t0\n' FAKE_LIMIT='008\n'
EXPECT_OUT='limit=3 remaining=2' run budget "기본 상한 3, 사용 1의 남은 횟수" 0 -
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
REQUIRE=DSTOP run stop "정상(키 있음)" 0 STOP,COMPOSE_LOAD_FAIL
REQUIRE=DSTOP run stop "키 빈 값" 0 STOP,COMPOSE_LOAD_FAIL FAKE_KEYS=empty
REQUIRE=DSTOP run stop "키 삭제" 0 STOP,COMPOSE_LOAD_FAIL FAKE_KEYS=deleted
REQUIRE=DSTOP run stop ".env 없음" 0 STOP,COMPOSE_LOAD_FAIL FAKE_KEYS=nofile
run stop "운영 라벨 행 섞임" 1 DSTOP FAKE_LPS='c1|beone-codef-verify-mysql-1|beone-codef\na1|beone-app-backend-1|beone-app\n'
run stop "공개 nginx 라벨 행 섞임" 1 DSTOP FAKE_LPS='p1|beone-nginx-1|beone\n'
run stop "라벨은 같고 이름이 다름" 1 DSTOP FAKE_LPS='x1|beone-verify-verify-backend-1|beone-codef\n'
run stop "대상 조회 실패" 1 DSTOP FAKE_LPS_RC=1
run stop "대상 없음" 1 DSTOP FAKE_LPS=''
run stop "stop 실패" 1 - FAKE_DSTOP_RC=1
run stop "정지 뒤에도 실행 중" 1 - FAKE_LRUN='c2\n'
run stop "정지 확인 조회 실패" 1 - FAKE_LRUN_RC=1
REQUIRE=DVRM run destroy "정상(키 있음)" 0 DOWN,COMPOSE_LOAD_FAIL
REQUIRE=DVRM run destroy "키 빈 값" 0 DOWN,COMPOSE_LOAD_FAIL FAKE_KEYS=empty
REQUIRE=DVRM run destroy "키 삭제" 0 DOWN,COMPOSE_LOAD_FAIL FAKE_KEYS=deleted
REQUIRE=RM run destroy ".env 없음(키 파일 없음 허용, DESTROY_OK)" 0 DOWN,COMPOSE_LOAD_FAIL FAKE_KEYS=nofile
run destroy "운영 라벨 행 섞임(오식별)" 1 DSTOP,DRM,DVRM,DNRM,RM FAKE_LPS='c1|beone-codef-verify-mysql-1|beone-codef\na1|beone-app-backend-1|beone-app\n'
run destroy "검증 이름 섞임(오식별)" 1 DSTOP,DRM,DVRM,DNRM,RM FAKE_LPS='v1|beone-verify-verify-backend-1|beone-codef\n'
run destroy "대상 조회 실패" 1 DSTOP,DRM,DVRM,DNRM,RM FAKE_LPS_RC=1
REQUIRE=DVRM run destroy "부분 폐기 뒤 재실행(컨테이너 없음, 볼륨·네트워크 남음)" 0 DSTOP,DRM FAKE_LPS=''
REQUIRE=DNRM run destroy "부분 폐기 뒤 재실행(볼륨까지 지움, 네트워크 남음)" 0 DSTOP,DRM,DVRM FAKE_LPS='' FAKE_LVOLS=''
REQUIRE=RM run destroy "모두 정리된 뒤 재실행(키 파일·폴더도 없음)" 0 DSTOP,DRM,DVRM,DNRM,RMDIR FAKE_LPS='' FAKE_LVOLS='' FAKE_LNETS='' FAKE_KEYS=nofile FAKE_DIR_RC=1
run destroy "컨테이너 없음 + 운영 볼륨 섞임" 1 DSTOP,DRM,DVRM,DNRM,RM FAKE_LPS='' FAKE_LVOLS='beone-app_mysql-data|beone-app\n'
run destroy "라벨 없는 beone-codef 볼륨(지우지 않고 멈춤)" 1 DSTOP,DRM,DVRM,DNRM,RM FAKE_VOLS='beone-codef_orphan\nbeone-app_mysql-data\n'
run destroy "운영 볼륨 섞임" 1 DSTOP,DRM,DVRM,DNRM,RM FAKE_LVOLS='beone-codef_verify-mysql-data|beone-codef\nbeone-app_mysql-data|beone-app\n'
run destroy "볼륨 라벨 조회 실패" 1 DSTOP,DRM,DVRM,DNRM,RM FAKE_LVOL_RC=1
run destroy "공개 네트워크 섞임" 1 DSTOP,DRM,DVRM,DNRM,RM FAKE_LNETS='beone_default|beone\n'
run destroy "네트워크 조회 실패" 1 DSTOP,DRM,DVRM,DNRM,RM FAKE_LNET_RC=1
run destroy "stop 실패" 1 DRM,DVRM,DNRM,RM,RMDIR FAKE_DSTOP_RC=1
run destroy "컨테이너 삭제 실패" 1 DVRM,DNRM,RM,RMDIR FAKE_DRM_RC=1
run destroy "볼륨 삭제 실패" 1 DNRM,RM,RMDIR FAKE_DVRM_RC=1
run destroy "네트워크 삭제 실패" 1 RM,RMDIR FAKE_DNRM_RC=1
run destroy "키 파일 삭제 실패" 1 RMDIR FAKE_RM_RC=1
run destroy "삭제 뒤 컨테이너 남음" 1 - FAKE_LPS_AFTER='c1\n'
run destroy "확인 볼륨 조회 실패" 1 - FAKE_VOL_RC=1
run destroy "볼륨 남음" 1 - FAKE_VOLS='beone-codef_verify-mysql-data\n'
run destroy "폴더 남음" 1 - FAKE_DIR_LEFT_RC=0

# 준비 단계 실패: 가짜 mktemp가 실패하거나 빈 값을 주면 스텁을 쓰기 전에 종료 1로 멈춰야 한다
mkdir -p "$WORK/mk-fail" "$WORK/mk-empty" || die "mktemp 사례 폴더를 만들지 못함"
printf '#!/usr/bin/env bash\nexit 1\n' > "$WORK/mk-fail/mktemp" && printf '#!/usr/bin/env bash\nexit 0\n' > "$WORK/mk-empty/mktemp" \
	&& chmod +x "$WORK/mk-fail/mktemp" "$WORK/mk-empty/mktemp" || die "가짜 mktemp를 쓰지 못함"
for mk in mk-fail mk-empty; do
	PATH="$WORK/$mk:$PATH" bash "$0" > "$WORK/$mk/out" 2>&1
	rc=$?
	# 스텁 쓰기 가드(뒤 단계)로 멈춘 것이 아니라 임시 폴더 가드에서 멈췄는지 문구로 확인한다
	if [ "$rc" = 1 ] && grep -q 'self-test 준비 실패: 임시 폴더' "$WORK/$mk/out" && ! grep -q '^| ' "$WORK/$mk/out"; then
		echo "| (준비) | mktemp ${mk#mk-}: 블록 실행 전 종료 | 1 | $rc | 스텁 쓰기·블록 실행 | PASS |"
	else
		echo "| (준비) | mktemp ${mk#mk-}: 블록 실행 전 종료 | 1 | $rc | 스텁 쓰기·블록 실행 | FAIL |"; failures=$((failures + 1))
	fi
done

echo
echo "임시 폴더: $WORK"
if [ "$failures" -eq 0 ]; then
	echo "runbook self-test PASS"
	exit 0
fi
echo "runbook self-test FAIL ($failures)" >&2
exit 1
