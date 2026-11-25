#!/usr/bin/env bash
# deploy.sh·smoke-public.sh 오프라인 자기검사(결정 40, A-CD1).
#
# 가짜 docker·curl·flock·sleep을 PATH 앞에 두고, 사례마다 임시 폴더를 서버 루트(BEONE_DEPLOY_ROOT)로 써서 실제 스크립트를 실행한다.
# 가짜 명령은 환경 변수로 실패·응답을 주입받고 실행한 동작을 기록만 한다. 실제 Docker·레지스트리·서버·네트워크에는 손대지 않는다.
# 각 사례의 종료 코드, 실행되면 안 되는 동작·반드시 실행돼야 하는 동작, 끝난 뒤 상태 파일·웹 링크, 출력에 비밀값이 없는지를 확인한다.
#
# 사용: deploy/scripts/deploy-selftest.sh   (모든 사례가 기대대로면 종료 0)
# 임시 폴더는 지우지 않고 끝에 경로를 출력한다.
set -uo pipefail

REPO="$(cd "$(dirname "$0")/../.." && pwd)"
DEPLOY="$REPO/deploy/scripts/deploy.sh"
SMOKE="$REPO/deploy/scripts/smoke-public.sh"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/deploy-selftest.XXXXXX")"
mkdir -p "$WORK/bin" "$WORK/cases"

OLD_API="sha256:$(printf 'a%.0s' $(seq 64))"
OLD_WEB="sha256:$(printf 'b%.0s' $(seq 64))"
NEW_API="sha256:$(printf 'c%.0s' $(seq 64))"
NEW_WEB="sha256:$(printf 'd%.0s' $(seq 64))"
GIT="$(printf '1234567890%.0s' 1 2 3 4)"
SECRET=selftest-secret-value-must-not-appear

# 가짜 docker. 실행 중 backend 이미지는 $FAKE_DIR/running 파일로 흉내 낸다
cat > "$WORK/bin/docker" << 'EOF'
#!/usr/bin/env bash
log() { echo "$*" >> "$FAKE_LOG"; }
NEW_API_REF="ghcr.io/beone-capston/beone-api@$FAKE_NEW_API"
OLD_API_REF="ghcr.io/beone-capston/beone-api@$FAKE_OLD_API"
running() { cat "$FAKE_DIR/running" 2> /dev/null; }
case "$1" in
	pull)
		case "$2" in
			"$NEW_API_REF") log PULL_API; exit "${FAKE_PULL_API_RC:-0}" ;;
			"$OLD_API_REF") log PULL_API_OLD; exit "${FAKE_PULL_OLD_RC:-0}" ;;
			*beone-web*) log PULL_WEB; exit "${FAKE_PULL_WEB_RC:-0}" ;;
		esac
		log "PULL_OTHER $2"; exit 0 ;;
	compose)
		shift
		proj=""
		while [ $# -gt 0 ]; do
			case "$1" in -p) proj="$2"; shift 2 ;; -f | --env-file) shift 2 ;; *) break ;; esac
		done
		sub="$1"; shift
		case "$sub" in
			config)
				if [ "${1:-}" = --services ]; then
					case "$proj" in beone-app) d='mysql\nbackend\n' ;; *) d='mysql\nbackend\nweb\n' ;; esac
					printf '%b' "${FAKE_SERVICES-$d}"; exit 0
				fi
				exit "${FAKE_CONFIG_RC:-0}" ;;
			up)
				case "${BEONE_API_IMAGE:-}" in
					"$NEW_API_REF") log UP_NEW; [ "${FAKE_UP_NEW_RC:-0}" = 0 ] || exit 1 ;;
					"$OLD_API_REF") log UP_OLD; [ "${FAKE_UP_OLD_RC:-0}" = 0 ] || exit 1 ;;
					*) log "UP_OTHER ${BEONE_API_IMAGE:-}" ;;
				esac
				echo "$BEONE_API_IMAGE" > "$FAKE_DIR/running"; exit 0 ;;
			ps) [ -s "$FAKE_DIR/running" ] && echo bk1; exit 0 ;;
			exec)
				case "$*" in
					*mysqldump*)
						log DUMP
						[ "${FAKE_DUMP_RC:-0}" = 0 ] || exit 1
						printf 'CREATE TABLE t (id INT);\n'
						[ -n "${FAKE_DUMP_INCOMPLETE:-}" ] || printf -- '-- Dump completed on 2026-11-25\n'
						exit 0 ;;
					*flyway_schema_history*) printf '1\t1\tV1__init.sql\t1\t1\n'; exit 0 ;;
				esac
				exit 0 ;;
		esac
		exit 0 ;;
	inspect)
		fmt="$3"; r="$(running)"
		case "$fmt" in
			*State.Status*)
				case "$r" in
					"$NEW_API_REF") echo "${FAKE_NEW_HEALTH:-running/healthy}" ;;
					"$OLD_API_REF") echo "${FAKE_OLD_HEALTH:-running/healthy}" ;;
					*) echo running/healthy ;;
				esac ;;
			*Config.Image*) echo "$r" ;;
			*) echo "sha256:id-$r" ;;
		esac
		exit 0 ;;
	image)
		# image inspect [--format F] <ref>
		if [ "${3:-}" = --format ]; then
			case "$4" in *revision*) echo '<no value>' ;; *) echo "sha256:id-$5" ;; esac
			exit 0
		fi
		[ "$3" = "$OLD_API_REF" ] && exit "${FAKE_PREV_IMAGE_RC:-0}"
		exit 0 ;;
	create) log CREATE; echo wc1; exit "${FAKE_CREATE_RC:-0}" ;;
	cp)
		[ "${FAKE_CP_RC:-0}" = 0 ] || exit 1
		# web 이미지 dist 위치 계약(/dist)과 다르면 실제 docker cp처럼 실패한다
		[ "$2" = "wc1:/dist/." ] || { log "CP_WRONG_SRC $2"; exit 1; }
		dest="$3"
		[ -n "${FAKE_NO_INDEX:-}" ] || echo '<!doctype html>' > "$dest/index.html"
		echo 'app' > "$dest/app.js"
		exit 0 ;;
	rm) log RM_CONTAINER; exit 0 ;;
esac
exit 0
EOF

# 가짜 curl: 마지막 인자의 경로별로 헤더와 상태 코드를 낸다(-D - -o /dev/null -w '\n%{http_code}' 형태)
cat > "$WORK/bin/curl" << 'EOF'
#!/usr/bin/env bash
url="${*: -1}"
path="/${url#*://*/}"
[ "$path" = "/$url" ] && path=/
if [ "${FAKE_CURL_RC:-0}" != 0 ]; then printf '\n000'; exit 7; fi
code=404; hdr=''
case "$path" in
	/) code="${FAKE_ROOT_CODE:-200}"; hdr="content-type: ${FAKE_ROOT_CT:-text/html}" ;;
	/api/v1/me) code="${FAKE_ME_CODE:-401}"; hdr='content-type: application/json' ;;
	/actuator/health) code="${FAKE_ACTUATOR_CODE:-404}" ;;
	/api/v1/auth/csrf) code=200; hdr="${FAKE_CSRF_COOKIE-set-cookie: __Host-BEONE_SESSION=synthetic; Path=/; Max-Age=7200; Secure; HttpOnly; SameSite=Lax}" ;;
esac
printf 'HTTP/1.1 %s\r\n' "$code"
[ -n "$hdr" ] && printf '%b\r\n' "$hdr"
printf '\r\n\n%s' "$code"
EOF

cat > "$WORK/bin/flock" << 'EOF'
#!/usr/bin/env bash
exit "${FAKE_FLOCK_RC:-0}"
EOF
cat > "$WORK/bin/sleep" << 'EOF'
#!/usr/bin/env bash
exit 0
EOF
# GNU mv -T(macOS mv에는 없음)를 rename으로 흉내 낸다. 그 밖의 mv는 시스템 mv로 넘긴다
cat > "$WORK/bin/mv" << 'EOF'
#!/usr/bin/env bash
if [ "${1:-}" = -T ]; then exec perl -e 'rename($ARGV[0], $ARGV[1]) or exit 1' "$2" "$3"; fi
exec /bin/mv "$@"
EOF
chmod +x "$WORK/bin/"*
# sha256sum: FAKE_SHA_RC로 실패를 주입하고, 아니면 시스템 sha256sum(없으면 macOS shasum)으로 넘긴다
cat > "$WORK/bin/sha256sum" << 'EOF2'
#!/usr/bin/env bash
[ "${FAKE_SHA_RC:-0}" = 0 ] || exit "$FAKE_SHA_RC"
if [ -x /usr/bin/sha256sum ]; then exec /usr/bin/sha256sum "$@"; fi
exec shasum -a 256 "$@"
EOF2
chmod +x "$WORK/bin/sha256sum"

failures=0
n=0
echo "| # | 사례 | 기대 종료 코드 | 실제 | 상태 파일 | 웹 링크 | 확인 | 판정 |"
echo "|---|---|---|---|---|---|---|---|"

# run_deploy <설명> <기대 종료 코드> <금지 동작(쉼표, 없으면 -)> <필수 동작(쉼표, 없으면 -)> <상태 기대 new|old|none> <링크 기대 new|old|none> [-- deploy 인자...]
# 사례 설정(앞에 붙이는 변수): WANT_MSG=<출력에 있어야 할 사유 문구>, SEED_STATE=no, STATE_CORRUPT=1, INIT_RUNNING=<이미지>, ENV_EXTRA=<줄>, COMPOSE_EXTRA=<줄>, WANT_RECORD=1
# 나머지 FAKE_* 변수는 env로 넘긴다
run_deploy() {
	local desc="$1" want="$2" forbid="$3" need="$4" want_state="$5" want_link="$6" rc f bad="" miss="" st="none" ln="none" extra=""
	shift 6
	n=$((n + 1))
	local c="$WORK/cases/$n"
	mkdir -p "$c/fake" "$c/app/backups" "$c/staging" "$c/deploy/state"
	local e target_env="${TARGET_ENV:-staging}"
	for e in app staging; do
		printf 'MYSQL_DATABASE=beone\nMYSQL_USER=beone\nMYSQL_PASSWORD=%s\nMYSQL_ROOT_PASSWORD=%s\nBEONE_API_IMAGE=beone-api:old\n%b' "$SECRET" "$SECRET" "${ENV_EXTRA:-}" > "$c/$e/.env"
		printf 'services: {}\n%b' "${COMPOSE_EXTRA:-}" > "$c/$e/compose.yaml"
	done
	local webbase="$c/staging/web"
	[ "$target_env" = production ] && webbase="$c/web"
	if [ "${SEED_STATE:-yes}" = yes ]; then
		mkdir -p "$webbase/releases/old"
		echo old > "$webbase/releases/old/index.html"
		ln -s releases/old "$webbase/current"
		printf 'api_digest=%s\nweb_digest=%s\ngit_sha=%s\n' "$OLD_API" "$OLD_WEB" "$(printf 'f%.0s' $(seq 40))" > "$c/deploy/state/$target_env.state"
		echo "ghcr.io/beone-capston/beone-api@$OLD_API" > "$c/fake/running"
	fi
	[ -n "${STATE_CORRUPT:-}" ] && printf 'api_digest=broken\n' > "$c/deploy/state/$target_env.state"
	[ -n "${INIT_RUNNING+x}" ] && printf '%s' "$INIT_RUNNING" > "$c/fake/running"
	: > "$c/log"
	local args=("$target_env" "$NEW_API" "$NEW_WEB" "$GIT")
	if [ "${1:-}" = -- ]; then shift; args=("$@"); set --; fi
	local envs=()
	while [ $# -gt 0 ]; do envs+=("$1"); shift; done
	env -i PATH="$WORK/bin:/usr/bin:/bin" BEONE_DEPLOY_ROOT="$c" BEONE_DEPLOY_HEALTH_TIMEOUT=20 \
		FAKE_LOG="$c/log" FAKE_DIR="$c/fake" FAKE_NEW_API="$NEW_API" FAKE_OLD_API="$OLD_API" ${envs[@]+"${envs[@]}"} \
		bash "$DEPLOY" "${args[@]}" > "$c/out" 2>&1
	rc=$?
	if [ "$forbid" != - ]; then for f in ${forbid//,/ }; do grep -qx "$f" "$c/log" && bad="$bad $f"; done; fi
	if [ "$need" != - ]; then for f in ${need//,/ }; do grep -qx "$f" "$c/log" || miss="$miss $f"; done; fi
	local sf="$c/deploy/state/$target_env.state"
	if [ -f "$sf" ]; then
		case "$(sed -n 's/^api_digest=//p' "$sf")" in "$NEW_API") st=new ;; "$OLD_API") st=old ;; *) st=other ;; esac
	fi
	if [ -L "$webbase/current" ]; then
		case "$(readlink "$webbase/current")" in releases/old) ln=old ;; "releases/${GIT:0:12}-"*) ln=new ;; *) ln=other ;; esac
	fi
	grep -q "$SECRET" "$c/out" && extra="$extra 비밀값출력"
	if [ -n "${WANT_MSG:-}" ]; then grep -qF "$WANT_MSG" "$c/out" || extra="$extra 사유문구없음($WANT_MSG)"; fi
	if [ -n "${WANT_RECORD:-}" ]; then
		local rec
		rec=$(ls "$c"/app/backups/*/app.sql.record 2> /dev/null | head -n 1)
		{ [ -n "$rec" ] && grep -qx "deploy_prev_api_digest=$OLD_API" "$rec" && grep -qx "deploy_new_api_digest=$NEW_API" "$rec" \
			&& grep -qx "deploy_git_sha=$GIT" "$rec" && grep -qE '^dump_sha256=[0-9a-f]{64}$' "$rec" && grep -qE '^flyway_sha256=[0-9a-f]{64}$' "$rec" && [ -f "${rec%.record}" ] && [ -f "${rec%.record}.flyway" ]; } || extra="$extra 짝기록없음"
	fi
	local verdict=PASS
	{ [ "$rc" = "$want" ] && [ -z "$bad" ] && [ -z "$miss" ] && [ "$st" = "$want_state" ] && [ "$ln" = "$want_link" ] && [ -z "$extra" ]; } \
		|| { verdict=FAIL; failures=$((failures + 1)); }
	local note="금지:${forbid} 필수:${need}${bad:+ (실행됨:$bad)}${miss:+ (빠짐:$miss)}${extra}"
	echo "| $n | $target_env $desc | $want | $rc | $st(기대 $want_state) | $ln(기대 $want_link) | $note | $verdict |"
}

# 정상
run_deploy "정상" 0 DUMP,UP_OLD PULL_API,PULL_WEB,UP_NEW,CREATE,RM_CONTAINER new new
TARGET_ENV=production WANT_RECORD=1 run_deploy "정상(배포 직전 백업·짝 기록)" 0 UP_OLD DUMP,PULL_API,PULL_WEB,UP_NEW,CREATE new new
SEED_STATE=no run_deploy "첫 배포(상태 파일 없음) 정상" 0 UP_OLD UP_NEW new new
# 배포 전 단계 실패: 서비스 변경 없음(종료 3)
run_deploy "api pull 실패" 3 UP_NEW,DUMP,CREATE PULL_API old old FAKE_PULL_API_RC=1
run_deploy "web pull 실패" 3 UP_NEW,DUMP,CREATE PULL_WEB old old FAKE_PULL_WEB_RC=1
TARGET_ENV=production WANT_MSG="백업 실패(덤프)" run_deploy "백업 실패(덤프 명령 실패)" 3 UP_NEW,CREATE DUMP old old FAKE_DUMP_RC=1
TARGET_ENV=production WANT_MSG="백업 실패(덤프)" run_deploy "백업 실패(완료 표식 없음)" 3 UP_NEW,CREATE DUMP old old FAKE_DUMP_INCOMPLETE=1
TARGET_ENV=production WANT_MSG="덤프 sha256 계산 실패" run_deploy "백업 짝 기록 실패(sha256 계산 실패)" 3 UP_NEW,CREATE DUMP old old FAKE_SHA_RC=1
TARGET_ENV=production INIT_RUNNING='' WANT_MSG="실행 중인 backend 없음" run_deploy "백업 짝 기록 실패(실행 중 backend 없음)" 3 UP_NEW,CREATE DUMP old old
WANT_MSG="다른 배포가 실행 중" run_deploy "동시 실행(잠금 실패)" 3 PULL_API,UP_NEW - old old FAKE_FLOCK_RC=1
ENV_EXTRA='CODEF_CLIENT_ID=x\n' WANT_MSG="CODEF 키 이름이 있음: CODEF_CLIENT_ID" run_deploy "env에 CODEF 키 이름" 3 PULL_API,UP_NEW - old old
ENV_EXTRA='BEONE_CODEF_CONNECTED_ID_KEY=\n' run_deploy "env에 CODEF 키 이름(빈 값)" 3 PULL_API,UP_NEW - old old
TARGET_ENV=production ENV_EXTRA='SPRING_PROFILES_ACTIVE=prod,codef-live\n' run_deploy "env에 codef-live" 3 PULL_API,UP_NEW,DUMP - old old
COMPOSE_EXTRA='# SPRING_PROFILES_ACTIVE: member,codef-live\n' run_deploy "compose에 codef-live" 3 PULL_API,UP_NEW - old old
run_deploy "compose config 실패" 3 PULL_API,UP_NEW - old old FAKE_CONFIG_RC=1
TARGET_ENV=production run_deploy "compose 서비스 다름(nginx 포함)" 3 PULL_API,UP_NEW,DUMP - old old FAKE_SERVICES='mysql\nbackend\nnginx\n'
run_deploy "compose 서비스 조회 결과 비어 있음" 3 PULL_API,UP_NEW - old old FAKE_SERVICES=''
STATE_CORRUPT=1 WANT_MSG="상태 파일 손상" run_deploy "상태 파일 손상" 3 PULL_API,UP_NEW - other old
# 4~7 실패: 이전 digest·이전 링크로 복귀(종료 4)
run_deploy "backend up 실패 → 복귀" 4 CREATE UP_NEW,UP_OLD old old FAKE_UP_NEW_RC=1
WANT_MSG="복귀 완료" run_deploy "health unhealthy → 복귀" 4 CREATE UP_NEW,UP_OLD old old FAKE_NEW_HEALTH=running/unhealthy
run_deploy "health starting 시간 초과 → 복귀" 4 CREATE UP_NEW,UP_OLD old old FAKE_NEW_HEALTH=running/starting
run_deploy "backend 종료(exited) → 복귀" 4 CREATE UP_NEW,UP_OLD old old FAKE_NEW_HEALTH=exited/none
run_deploy "web dist 추출 실패 → 복귀" 4 - UP_NEW,UP_OLD,RM_CONTAINER old old FAKE_CP_RC=1
run_deploy "web dist에 index.html 없음 → 복귀" 4 - UP_NEW,UP_OLD old old FAKE_NO_INDEX=1
run_deploy "스모크 실패(/api/v1/me 200) → 복귀" 4 - UP_NEW,CREATE,UP_OLD old old FAKE_ME_CODE=200
run_deploy "스모크 실패(csrf 쿠키 Secure 없음) → 복귀" 4 - UP_NEW,UP_OLD old old 'FAKE_CSRF_COOKIE=set-cookie: __Host-BEONE_SESSION=s; Path=/; HttpOnly; SameSite=Lax'
TARGET_ENV=production run_deploy "스모크 실패(/ application/json) → 복귀" 4 - DUMP,UP_NEW,UP_OLD old old FAKE_ROOT_CT=application/json
run_deploy "스모크 연결 실패 → 복귀" 4 - UP_NEW,UP_OLD old old FAKE_CURL_RC=1
# 복귀 실패·복귀 대상 없음(종료 1)
WANT_MSG="즉시 확인 필요" run_deploy "health 실패 뒤 복귀 health도 실패" 1 - UP_NEW,UP_OLD old old FAKE_NEW_HEALTH=running/unhealthy FAKE_OLD_HEALTH=running/unhealthy
run_deploy "스모크 실패 뒤 이전 이미지 없음·pull 실패" 1 UP_OLD UP_NEW,PULL_API_OLD old old FAKE_ME_CODE=200 FAKE_PREV_IMAGE_RC=1 FAKE_PULL_OLD_RC=1
run_deploy "스모크 실패 뒤 이전 backend up 실패" 1 - UP_NEW,UP_OLD old old FAKE_ME_CODE=200 FAKE_UP_OLD_RC=1
SEED_STATE=no WANT_MSG="backend 복귀 대상 없음(첫 배포)" run_deploy "첫 배포 health 실패(복귀 대상 없음)" 1 UP_OLD,CREATE UP_NEW none none FAKE_NEW_HEALTH=running/unhealthy
SEED_STATE=no run_deploy "첫 배포 스모크 실패(복귀 대상 없음)" 1 UP_OLD UP_NEW,CREATE none new FAKE_ME_CODE=200
# 인자 형식 오류(종료 2, 아무것도 하지 않음)
run_deploy "환경 이름 오류(prod)" 2 PULL_API,UP_NEW - old old -- prod "$NEW_API" "$NEW_WEB" "$GIT"
run_deploy "digest 대문자" 2 PULL_API,UP_NEW - old old -- staging "sha256:$(printf 'C%.0s' $(seq 64))" "$NEW_WEB" "$GIT"
run_deploy "digest 길이 부족" 2 PULL_API,UP_NEW - old old -- staging "sha256:abc" "$NEW_WEB" "$GIT"
run_deploy "web digest가 태그" 2 PULL_API,UP_NEW - old old -- staging "$NEW_API" latest "$GIT"
run_deploy "GitSha 12자리" 2 PULL_API,UP_NEW - old old -- staging "$NEW_API" "$NEW_WEB" "${GIT:0:12}"
run_deploy "인자 3개" 2 PULL_API,UP_NEW - old old -- staging "$NEW_API" "$NEW_WEB"

# smoke-public.sh 단독
run_smoke() {
	local desc="$1" want="$2" rc verdict=PASS
	shift 2
	n=$((n + 1))
	env -i PATH="$WORK/bin:/usr/bin:/bin" "$@" bash "$SMOKE" "${SMOKE_ARG-http://127.0.0.1:18081}" > "$WORK/cases/smoke-$n.out" 2>&1
	rc=$?
	[ "$rc" = "$want" ] || { verdict=FAIL; failures=$((failures + 1)); }
	echo "| $n | smoke-public $desc | $want | $rc | - | - | - | $verdict |"
}
run_smoke "정상" 0
run_smoke "/ 상태 500" 1 FAKE_ROOT_CODE=500
run_smoke "/ Content-Type JSON" 1 FAKE_ROOT_CT=application/json
run_smoke "/api/v1/me 200" 1 FAKE_ME_CODE=200
run_smoke "/actuator/health 200" 1 FAKE_ACTUATOR_CODE=200
run_smoke "csrf 쿠키 없음" 1 FAKE_CSRF_COOKIE=
run_smoke "csrf 쿠키 HttpOnly 없음" 1 'FAKE_CSRF_COOKIE=set-cookie: __Host-BEONE_SESSION=s; Path=/; Secure; SameSite=Lax'
run_smoke "csrf 쿠키 Path 다름" 1 'FAKE_CSRF_COOKIE=set-cookie: __Host-BEONE_SESSION=s; Path=/api; Secure; HttpOnly; SameSite=Lax'
run_smoke "csrf 쿠키 Domain 있음" 1 'FAKE_CSRF_COOKIE=set-cookie: __Host-BEONE_SESSION=s; Path=/; Domain=beonepay.site; Secure; HttpOnly; SameSite=Lax'
run_smoke "csrf 쿠키 SameSite 다름" 1 'FAKE_CSRF_COOKIE=set-cookie: __Host-BEONE_SESSION=s; Path=/; Secure; HttpOnly; SameSite=None'
run_smoke "csrf 쿠키 2줄" 1 'FAKE_CSRF_COOKIE=set-cookie: __Host-BEONE_SESSION=s; Path=/; Secure; HttpOnly; SameSite=Lax\r\nset-cookie: __Host-BEONE_SESSION=t; Path=/; Secure; HttpOnly; SameSite=Lax'
run_smoke "연결 실패" 1 FAKE_CURL_RC=1
SMOKE_ARG='' run_smoke "인자 없음" 2
SMOKE_ARG='beonepay.site' run_smoke "scheme 없는 인자" 2

echo
echo "임시 폴더: $WORK"
if [ "$failures" -eq 0 ]; then
	echo "deploy self-test PASS ($n)"
	exit 0
fi
echo "deploy self-test FAIL ($failures/$n)" >&2
exit 1
