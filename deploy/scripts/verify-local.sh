#!/usr/bin/env bash
# 배포 Compose를 로컬의 분리된 검증 프로젝트에서 확인한다. 서버에서 실행하지 않는다.
#
# 검증 프로젝트 이름은 고정이다: beone-deploy-verify(원본), beone-deploy-restore(복원 대상).
# 기존 로컬 개발 DB(docker-compose.yml, beone-mysql)와 서버 프로젝트(beone, beone-app)는 대상으로 삼지 않는다.
# 검증 전용 스키마 verify_persist와 계정 verify_backup은 이 스크립트 안에서만 만든다. 업무 Flyway·시드는 건드리지 않는다.
# 비밀번호는 명령행 인자로 넘기지 않는다. 컨테이너 안에서 프로세스 치환으로 만든 옵션 파일(--defaults-extra-file)로 전달한다.
# 이 스크립트는 볼륨·이미지를 지우지 않는다.
#
# 준비:
#   export VERIFY_ENV=<저장소 밖 경로>/verify.env   검증용 환경 파일(init-env로 생성, 권한 600)
#   export BEONE_API_IMAGE=<검증할 이미지 태그>
#
# 명령:
#   init-env              VERIFY_ENV 경로에 무작위 비밀번호로 검증용 환경 파일을 만든다(이미 있으면 거부)
#   static                V2  Compose 문법·포트 선언·MySQL 태그 일치 검사
#   image-check           V2  이미지 플랫폼·실행 사용자·포함 파일·빌드 이력 검사
#   up                    V4~V10 원본 프로젝트 기동과 단계별 확인
#   check [verify|restore] V5~V10 실행 중인 프로젝트 재확인
#   persist               V11 컨테이너 재생성 후 데이터 유지
#   backup                V12a 앱 DB 덤프·복원, V12b 검증 전용 스키마 덤프·복원
#   swap <태그A> <태그B>  V13 backend 이미지 전환·재기동(마지막에 태그B로 끝난다)
#   status                검증 프로젝트의 컨테이너·볼륨 목록
set -euo pipefail

repo="$(git rev-parse --show-toplevel)"
compose_file="$repo/deploy/compose.yaml"
dev_compose_file="$repo/docker-compose.yml"
verify_project="beone-deploy-verify"
restore_project="beone-deploy-restore"
fail_count=0

die() { echo "verify-local: $*" >&2; exit 1; }
pass() { echo "PASS $*"; }
fail() { echo "FAIL $*"; fail_count=$((fail_count + 1)); }
finish() {
	if [ "$fail_count" -gt 0 ]; then
		echo "실패 ${fail_count}건"
		exit 1
	fi
	echo "모두 통과"
}

# 검증용 환경 파일과 그 폴더(덤프도 이 폴더 아래에 만든다)가 저장소 밖인지 실제 경로로 확인한다.
# 상대경로·..·심볼릭 링크 폴더는 pwd -P로 풀어서 비교하고, 환경 파일 자체가 심볼릭 링크면 거부한다.
check_env_location() {
	local dir real_dir real_repo
	[ -n "${VERIFY_ENV:-}" ] || die "VERIFY_ENV를 지정하세요"
	[ ! -L "$VERIFY_ENV" ] || die "VERIFY_ENV가 심볼릭 링크입니다. 실제 파일 경로를 지정하세요"
	dir="$(dirname "$VERIFY_ENV")"
	[ -d "$dir" ] || die "VERIFY_ENV 폴더가 없습니다"
	real_dir="$(cd "$dir" && pwd -P)"
	real_repo="$(cd "$repo" && pwd -P)"
	case "$real_dir/" in
		"$real_repo"/*) die "검증용 환경 파일과 덤프는 저장소 밖에 두세요" ;;
	esac
}

need_env() {
	check_env_location
	[ -f "$VERIFY_ENV" ] || die "VERIFY_ENV 파일이 없습니다"
}

need_image() {
	[ -n "${BEONE_API_IMAGE:-}" ] || die "BEONE_API_IMAGE를 지정하세요"
	docker image inspect "$BEONE_API_IMAGE" > /dev/null 2>&1 || die "이미지가 없습니다: $BEONE_API_IMAGE"
}

# 환경 파일에서 값 하나를 읽는다. 출력하지 않고 변수에만 담는다.
env_value() { sed -n "s/^$1=//p" "$VERIFY_ENV" | tail -n 1; }

project_name() {
	case "$1" in
		verify) echo "$verify_project" ;;
		restore) echo "$restore_project" ;;
		*) die "프로젝트는 verify 또는 restore 입니다" ;;
	esac
}

dc() {
	local project
	project="$(project_name "$1")"
	shift
	docker compose -f "$compose_file" -p "$project" --env-file "$VERIFY_ENV" "$@"
}

# 대상 Compose가 mysql·backend 두 서비스만 가진 배포 Compose인지 확인한다. nginx가 보이면 멈춘다.
guard_services() {
	local services
	services="$(dc "$1" config --services | sort | tr '\n' ' ')"
	[ "$services" = "backend mysql " ] || die "예상과 다른 서비스 목록입니다: $services"
}

container_id() { dc "$1" ps -q "$2"; }

# 상태를 바꾸지 않고 관찰만 한다. healthy면 성공, unhealthy·종료·재시작 발생이면 시간 제한을 기다리지 않고 실패한다.
wait_healthy() {
	local which="$1" service="$2" limit="$3" waited=0 id state first_restarts=""
	while [ "$waited" -lt "$limit" ]; do
		id="$(container_id "$which" "$service")"
		if [ -n "$id" ]; then
			state="$(docker inspect --format '{{.State.Status}} {{.RestartCount}} {{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$id")"
			set -- $state
			[ -n "$first_restarts" ] || first_restarts="$2"
			[ "$3" = "healthy" ] && return 0
			if [ "$3" = "unhealthy" ] || [ "$1" = "exited" ] || [ "$1" = "dead" ] || [ "$2" != "$first_restarts" ]; then
				echo "verify-local: $service 조기 실패 감지(state=$1 restarts=$2 health=$3)" >&2
				return 1
			fi
		fi
		sleep 5
		waited=$((waited + 5))
	done
	return 1
}

# 앱 계정으로 앱 DB에 SQL을 실행한다(SQL은 표준 입력).
mysql_app() {
	dc "$1" exec -T mysql bash -c 'mysql --defaults-extra-file=<(printf "[client]\nuser=%s\npassword=%s\n" "$MYSQL_USER" "$MYSQL_PASSWORD") -N -B "$MYSQL_DATABASE"'
}

# root로 SQL을 실행한다. 검증 전용 스키마·계정 준비에만 쓴다.
mysql_root() {
	dc "$1" exec -T mysql bash -c 'mysql --defaults-extra-file=<(printf "[client]\nuser=root\npassword=%s\n" "$MYSQL_ROOT_PASSWORD") -N -B'
}

# 검증 전용 계정으로 실행한다. 비밀번호는 docker의 환경 전달(-e 이름만)로 넘긴다.
mysql_verify_backup() {
	VERIFY_BACKUP_PASSWORD="$(env_value VERIFY_BACKUP_PASSWORD)" \
		dc "$1" exec -T -e VERIFY_BACKUP_PASSWORD mysql bash -c '[ -n "$VERIFY_BACKUP_PASSWORD" ] || { echo "VERIFY_BACKUP_PASSWORD가 전달되지 않음" >&2; exit 3; }; mysql --defaults-extra-file=<(printf "[client]\nuser=verify_backup\npassword=%s\n" "$VERIFY_BACKUP_PASSWORD") -N -B verify_persist'
}

# 검증 전용 계정을 만든다. 권한은 verify_persist 스키마로 한정한다(2번째 인자).
# SQL은 컨테이너 안에서 조립해 root 옵션 파일로 실행하므로 비밀번호가 명령행에 나오지 않는다.
create_verify_backup_account() {
	VERIFY_BACKUP_PASSWORD="$(env_value VERIFY_BACKUP_PASSWORD)" VERIFY_GRANTS="$2" \
		dc "$1" exec -T -e VERIFY_BACKUP_PASSWORD -e VERIFY_GRANTS mysql bash -s <<'SCRIPT'
[ -n "$VERIFY_BACKUP_PASSWORD" ] || { echo "VERIFY_BACKUP_PASSWORD가 전달되지 않음" >&2; exit 3; }
printf "CREATE USER IF NOT EXISTS 'verify_backup'@'%%' IDENTIFIED BY '%s';\nGRANT %s ON verify_persist.* TO 'verify_backup'@'%%';\n" \
	"$VERIFY_BACKUP_PASSWORD" "$VERIFY_GRANTS" |
	mysql --defaults-extra-file=<(printf "[client]\nuser=root\npassword=%s\n" "$MYSQL_ROOT_PASSWORD")
SCRIPT
}

# Flyway 기대값의 유일한 근거: backend/src/main/resources/db/migration의 버전 마이그레이션 파일(V<버전>__설명.sql).
# 버전의 밑줄은 Flyway 표기대로 점으로 바꾸고, 버전 순서로 정렬한다.
expected_migration_versions() {
	find "$repo/backend/src/main/resources/db/migration" -maxdepth 1 -type f -name 'V*__*.sql' -exec basename {} \; |
		sed -E 's/^V([^_]+(_[0-9]+)*)__.*$/\1/; s/_/./g' | sort -t. -k1,1n -k2,2n -k3,3n -k4,4n
}

expected_flyway_history() {
	expected_migration_versions | sed 's/$/:1/' | tr '\n' ' '
}

# 컨테이너 로그에 패턴이 있는지 본다. pipefail에서 grep -q가 파이프를 먼저 닫아 실패로 보이지 않도록 로그를 변수에 담아 검사한다.
logs_have() {
	local logs
	logs="$(docker logs "$1" 2>&1)"
	grep -qE -- "$2" <<< "$logs"
}

# health 응답 본문을 판정한다. status가 UP이고 구성 요소 세부 정보(components·details)가 없어야 한다.
# Spring Boot 4는 그룹 이름 목록(groups)을 함께 돌려주며, 이것은 세부 정보가 아니다.
health_ok() {
	case "$1" in
		*'"components"'* | *'"details"'*) return 1 ;;
		*'"status":"UP"'*) return 0 ;;
		*) return 1 ;;
	esac
}

sha_stdin() {
	if command -v sha256sum > /dev/null 2>&1; then sha256sum | cut -d' ' -f1; else shasum -a 256 | cut -d' ' -f1; fi
}

# 파일에서 비밀번호 문자열이 나온 줄 수를 센다. 비밀번호는 grep 인자로 넘기지 않고 파일 디스크립터로 준다.
secret_hits() {
	local file="$1" key total=0 value hits
	for key in MYSQL_PASSWORD MYSQL_ROOT_PASSWORD VERIFY_BACKUP_PASSWORD; do
		value="$(env_value "$key")"
		[ -n "$value" ] || continue
		hits="$(grep -cF -f <(printf '%s\n' "$value") "$file" || true)"
		total=$((total + hits))
	done
	echo "$total"
}

flyway_digest() {
	echo "SELECT installed_rank, version, script, checksum, success FROM flyway_schema_history ORDER BY installed_rank;" | mysql_app "$1" | sha_stdin
}

marker_digest() {
	echo "SELECT id, label, amount, IFNULL(note, '<NULL>'), DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s.%f') FROM verify_persist.marker ORDER BY id;" | mysql_root "$1" | sha_stdin
}

cmd_init_env() {
	check_env_location
	[ ! -e "$VERIFY_ENV" ] || die "이미 있습니다. 덮어쓰지 않습니다"
	(
		umask 077
		{
			echo "MYSQL_DATABASE=beone"
			echo "MYSQL_USER=beone"
			echo "MYSQL_PASSWORD=$(openssl rand -hex 24)"
			echo "MYSQL_ROOT_PASSWORD=$(openssl rand -hex 24)"
			echo "VERIFY_BACKUP_PASSWORD=$(openssl rand -hex 24)"
		} > "$VERIFY_ENV"
	)
	echo "생성함(값은 출력하지 않음), 권한 $(stat -c '%a' "$VERIFY_ENV" 2>/dev/null || stat -f '%Lp' "$VERIFY_ENV")"
}

cmd_static() {
	need_env
	BEONE_API_IMAGE="${BEONE_API_IMAGE:-beone-api:static-check}" dc verify config --quiet && pass "V2 compose 문법(값 출력 없음)" || fail "V2 compose 문법"
	BEONE_API_IMAGE="${BEONE_API_IMAGE:-beone-api:static-check}" guard_services verify && pass "V2 서비스 목록 mysql·backend"
	local ports
	ports="$(docker compose -f "$compose_file" config --no-interpolate --format json | grep -c '"published"' || true)"
	[ "$ports" = "0" ] && pass "V2 포트 공개 선언 0건" || fail "V2 포트 공개 선언 ${ports}건"
	local deploy_tag dev_tag
	deploy_tag="$(sed -n 's/^[[:space:]]*image:[[:space:]]*\(mysql:[^[:space:]]*\)[[:space:]]*$/\1/p' "$compose_file")"
	dev_tag="$(sed -n 's/^[[:space:]]*image:[[:space:]]*\(mysql:[^[:space:]]*\)[[:space:]]*$/\1/p' "$dev_compose_file")"
	[ -n "$deploy_tag" ] && [ "$deploy_tag" = "$dev_tag" ] && pass "V2 MySQL 태그 일치 $deploy_tag" || fail "V2 MySQL 태그 불일치 deploy=$deploy_tag dev=$dev_tag"
	finish
}

cmd_image_check() {
	need_image
	local platform user app_files history_hits
	platform="$(docker image inspect --format '{{.Os}}/{{.Architecture}}' "$BEONE_API_IMAGE")"
	[ "$platform" = "linux/amd64" ] && pass "V2 이미지 플랫폼 $platform" || fail "V2 이미지 플랫폼 $platform"
	user="$(docker image inspect --format '{{.Config.User}}' "$BEONE_API_IMAGE")"
	[ -n "$user" ] && [ "$user" != "root" ] && [ "$user" != "0" ] && pass "V2 실행 사용자 $user" || fail "V2 실행 사용자 '$user'"
	app_files="$(docker run --rm --platform linux/amd64 --network none --entrypoint sh "$BEONE_API_IMAGE" -c 'find /app -mindepth 1 | sort | tr "\n" " "')"
	[ "$app_files" = "/app/app.jar " ] && pass "V2 /app 내용은 app.jar 하나" || fail "V2 /app 내용: $app_files"
	if docker run --rm --platform linux/amd64 --network none --entrypoint sh "$BEONE_API_IMAGE" -c 'find / -xdev \( -name ".env" -o -name ".env.*" -o -name "*.jks" -o -name "*.p12" -o -name "*.keystore" \) -not -path "/proc/*" 2>/dev/null | grep -v "^/opt/java/" | grep -q .'; then
		fail "V2 이미지에 비밀값 형식 파일 있음"
	else
		pass "V2 이미지에 .env·키 저장소 파일 없음(JDK 기본 trust store 제외)"
	fi
	history_hits="$(docker history --no-trunc --format '{{.CreatedBy}}' "$BEONE_API_IMAGE" | grep -ciE 'password|secret|token|codef_client' || true)"
	[ "$history_hits" = "0" ] && pass "V2 빌드 이력에 비밀값 이름 없음" || fail "V2 빌드 이력 의심 ${history_hits}건"
	finish
}

check_project() {
	local which="$1" grants current binlog body mysql_id backend_id bindings published
	current="$(echo "SELECT CURRENT_USER();" | mysql_app "$which")"
	case "$current" in
		root@*) fail "V5 앱 연결 계정이 root" ;;
		"$(env_value MYSQL_USER)"@*) pass "V5 앱 계정 연결 $current" ;;
		*) fail "V5 예상 밖 계정 $current" ;;
	esac
	grants="$(echo "SHOW GRANTS;" | mysql_app "$which")"
	if echo "$grants" | grep -v '^GRANT USAGE ON \*\.\*' | grep -q 'ON \*\.\*'; then
		fail "V5 앱 계정에 전역 권한 있음"
	elif echo "$grants" | grep -q "ON \`$(env_value MYSQL_DATABASE)\`\.\*"; then
		pass "V5 앱 계정 권한은 앱 DB로 한정"
	else
		fail "V5 앱 계정 권한 확인 불가"
	fi
	binlog="$(echo "SELECT @@log_bin, @@binlog_expire_logs_seconds;" | mysql_app "$which" | tr '\t' ' ')"
	[ "$binlog" = "1 259200" ] && pass "V5 binlog 켜짐, 보관 259200초" || fail "V5 binlog 설정 '$binlog'"

	backend_id="$(container_id "$which" backend)"
	[ -n "$backend_id" ] || { fail "V6 backend 컨테이너 없음"; return; }
	logs_have "$backend_id" 'Started BeoneApiApplication' && pass "V6 기동 로그" || fail "V6 기동 로그 없음"
	if logs_have "$backend_id" 'HikariPool-1 - Start completed' && ! logs_have "$backend_id" '(Communications link failure|Access denied for user)'; then
		pass "V7 DB 연결 풀 시작, 연결 오류 없음"
	else
		fail "V7 DB 연결 로그 확인 실패"
	fi
	local history
	history="$(echo "SELECT version, success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank;" | mysql_app "$which" | tr '\t' ':' | tr '\n' ' ')"
	[ "$history" = "$(expected_flyway_history)" ] && pass "V8 Flyway 이력이 마이그레이션 폴더와 일치($history)" || fail "V8 Flyway 이력 '$history' 기대 '$(expected_flyway_history)'"

	body="$(docker exec "$backend_id" bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /actuator/health HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3 && cat <&3' | tr -d '\r' | sed '1,/^$/d')"
	health_ok "$body" && pass "V9 내부 health status UP, 구성 요소 세부 정보 없음(응답 $body)" || fail "V9 내부 health 응답 '$body'"

	for id in "$(container_id "$which" mysql)" "$backend_id"; do
		published="$(docker port "$id" | wc -l | tr -d ' ')"
		bindings="$(docker inspect --format '{{json .HostConfig.PortBindings}}' "$id")"
		if [ "$published" = "0" ] && { [ "$bindings" = "{}" ] || [ "$bindings" = "null" ]; }; then
			pass "V10 $(docker inspect --format '{{.Name}}' "$id") 호스트 포트 바인딩 없음"
		else
			fail "V10 $(docker inspect --format '{{.Name}}' "$id") 바인딩 $bindings"
		fi
	done
}

cmd_up() {
	need_env
	need_image
	guard_services verify
	dc verify up -d mysql
	wait_healthy verify mysql 240 && pass "V4 MySQL healthy" || { fail "V4 MySQL readiness 실패 또는 시간 초과"; finish; }
	dc verify up -d backend
	wait_healthy verify backend 300 && pass "V6 backend healthcheck healthy" || fail "V6 backend healthcheck 실패 또는 시간 초과"
	local backend_id
	backend_id="$(container_id verify backend)"
	local count
	count="$(expected_migration_versions | wc -l | tr -d ' ')"
	logs_have "$backend_id" "Successfully applied ${count} migrations?( |$)" && pass "V8 Flyway ${count}건 적용 로그" || fail "V8 Flyway ${count}건 적용 로그 없음"
	check_project verify
	finish
}

cmd_check() {
	need_env
	need_image
	local which="${1:-verify}"
	guard_services "$which"
	check_project "$which"
	finish
}

cmd_persist() {
	need_env
	need_image
	guard_services verify
	mysql_root verify <<'SQL'
CREATE DATABASE IF NOT EXISTS verify_persist CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS verify_persist.marker (
	id INT PRIMARY KEY,
	label VARCHAR(100) NOT NULL,
	amount BIGINT NOT NULL,
	note VARCHAR(50) NULL,
	created_at DATETIME(3) NOT NULL
);
DELETE FROM verify_persist.marker;
INSERT INTO verify_persist.marker VALUES
	(1, 'persist-check', 12345, 'ascii', '2026-11-15 12:00:00.123'),
	(2, '영속성 확인 표식', -987654321, NULL, '2026-11-15 12:00:01.000'),
	(3, 'zero', 0, '', '2026-11-15 12:00:02.999');
SQL
	local marker_before flyway_before marker_after flyway_after backend_id
	marker_before="$(marker_digest verify)"
	flyway_before="$(flyway_digest verify)"
	dc verify up -d --force-recreate
	wait_healthy verify mysql 240 || fail "V11 재생성 후 MySQL readiness 실패 또는 시간 초과"
	wait_healthy verify backend 300 || fail "V11 재생성 후 backend 실패 또는 시간 초과"
	marker_after="$(marker_digest verify)"
	flyway_after="$(flyway_digest verify)"
	[ "$marker_before" = "$marker_after" ] && pass "V11 검증 표식 3행 값 유지" || fail "V11 검증 표식 값 변경"
	[ "$flyway_before" = "$flyway_after" ] && pass "V11 Flyway 이력 유지" || fail "V11 Flyway 이력 변경"
	backend_id="$(container_id verify backend)"
	if logs_have "$backend_id" 'is up to date' && ! logs_have "$backend_id" 'Successfully applied'; then
		pass "V11 재기동 시 Flyway 재적용 없음"
	else
		fail "V11 재기동 Flyway 로그 확인 실패"
	fi
	finish
}

# 비밀번호가 명령행에 보이지 않는지, 실행 중인 프로세스 목록을 떠서 확인한다.
check_cmdline_exposure() {
	local result
	result="$(dc verify exec -T mysql bash -c '
		mysql --defaults-extra-file=<(printf "[client]\nuser=%s\npassword=%s\n" "$MYSQL_USER" "$MYSQL_PASSWORD") -e "SELECT SLEEP(3)" > /dev/null &
		sleep 1
		for f in /proc/[0-9]*/cmdline; do tr "\0" " " < "$f" 2>/dev/null; echo; done > /tmp/beone-cmdlines
		wait
		seen=$(grep -c "^mysql .*--defaults-extra-file=/dev/fd/" /tmp/beone-cmdlines || true)
		hits=$(grep -cF -f <(printf "%s\n" "$MYSQL_PASSWORD") /tmp/beone-cmdlines || true)
		rm -f /tmp/beone-cmdlines
		echo "$seen $hits"')"
	set -- $result
	if [ "${1:-0}" -ge 1 ] && [ "${2:-1}" = "0" ]; then
		pass "V12 실행 중 프로세스 명령행에 비밀번호 없음(mysql 클라이언트 프로세스 관측 ${1}건)"
	else
		fail "V12 명령행 노출 검사 결과 seen=${1:-?} hits=${2:-?}"
	fi
}

cmd_backup() {
	need_env
	need_image
	guard_services verify
	guard_services restore
	local work app_dump verify_dump logs_file flyway_src marker_src
	work="$(dirname "$VERIFY_ENV")/backup-$(date -u +%Y%m%dT%H%M%SZ)"
	mkdir -m 700 "$work"
	app_dump="$work/app-db.sql"
	verify_dump="$work/verify-persist.sql"

	check_cmdline_exposure

	# V12a 앱 DB: 앱 계정으로 덤프한다.
	dc verify exec -T mysql bash -c 'mysqldump --defaults-extra-file=<(printf "[client]\nuser=%s\npassword=%s\n" "$MYSQL_USER" "$MYSQL_PASSWORD") --single-transaction --no-tablespaces "$MYSQL_DATABASE"' > "$app_dump"
	grep -q 'CREATE TABLE `flyway_schema_history`' "$app_dump" && pass "V12a 앱 DB 덤프(앱 계정)" || fail "V12a 덤프에 Flyway 이력 없음"
	flyway_src="$(flyway_digest verify)"

	# V12b 검증 전용 스키마: 전용 계정을 만들고 그 계정으로 덤프한다.
	create_verify_backup_account verify "SELECT, LOCK TABLES, SHOW VIEW"
	marker_src="$(marker_digest verify)"
	VERIFY_BACKUP_PASSWORD="$(env_value VERIFY_BACKUP_PASSWORD)" dc verify exec -T -e VERIFY_BACKUP_PASSWORD mysql bash -c '[ -n "$VERIFY_BACKUP_PASSWORD" ] || exit 3; mysqldump --defaults-extra-file=<(printf "[client]\nuser=verify_backup\npassword=%s\n" "$VERIFY_BACKUP_PASSWORD") --single-transaction --no-tablespaces verify_persist' > "$verify_dump"
	grep -q 'CREATE TABLE `marker`' "$verify_dump" && pass "V12b 검증 스키마 덤프(전용 계정)" || fail "V12b 덤프에 marker 없음"

	# 복원 대상 프로젝트: MySQL만 먼저 띄우고 복원한다.
	dc restore up -d mysql
	wait_healthy restore mysql 240 || { fail "V12 복원 프로젝트 MySQL 실패 또는 시간 초과"; finish; }
	mysql_app restore < "$app_dump"
	echo "CREATE DATABASE IF NOT EXISTS verify_persist CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;" | mysql_root restore
	create_verify_backup_account restore "SELECT, INSERT, CREATE, DROP, ALTER, INDEX, LOCK TABLES, SHOW VIEW, REFERENCES"
	mysql_verify_backup restore < "$verify_dump"

	[ "$(flyway_digest restore)" = "$flyway_src" ] && pass "V12a 복원 DB의 Flyway 이력 일치" || fail "V12a Flyway 이력 불일치"
	[ "$(marker_digest restore)" = "$marker_src" ] && pass "V12b 복원 스키마의 행·값 일치" || fail "V12b 행·값 불일치"

	# 복원 DB에 backend를 붙여 재기동과 Flyway 재적용 여부를 본다.
	dc restore up -d backend
	if wait_healthy restore backend 300; then
		local restore_backend
		restore_backend="$(container_id restore backend)"
		if logs_have "$restore_backend" 'is up to date' && ! logs_have "$restore_backend" 'Successfully applied'; then
			pass "V12a 복원 DB로 기동, Flyway 재적용 없음"
		else
			fail "V12a 복원 DB 기동 로그 확인 실패"
		fi
	else
		fail "V12a 복원 DB backend 실패 또는 시간 초과"
	fi

	# 비밀번호가 덤프·로그에 남지 않았는지 센다.
	logs_file="$work/compose-logs.txt"
	{ dc verify logs --no-color; dc restore logs --no-color; } > "$logs_file" 2>&1
	local hits
	hits=$(( $(secret_hits "$app_dump") + $(secret_hits "$verify_dump") + $(secret_hits "$logs_file") ))
	[ "$hits" = "0" ] && pass "V12 덤프·Compose 로그에 비밀번호 문자열 0건" || fail "V12 비밀번호 문자열 ${hits}건"
	echo "덤프 위치(저장소 밖): $work"
	finish
}

cmd_swap() {
	need_env
	local first="${1:-}" second="${2:-}" tag id expected body
	[ -n "$first" ] && [ -n "$second" ] || die "swap <태그A> <태그B>"
	guard_services verify
	for tag in "$first" "$second"; do
		docker image inspect "$tag" > /dev/null 2>&1 || die "이미지가 없습니다: $tag"
		expected="$(docker image inspect --format '{{.Id}}' "$tag")"
		BEONE_API_IMAGE="$tag" dc verify up -d backend
		if BEONE_API_IMAGE="$tag" wait_healthy verify backend 300; then
			id="$(BEONE_API_IMAGE="$tag" container_id verify backend)"
			[ "$(docker inspect --format '{{.Image}}' "$id")" = "$expected" ] && pass "V13 $tag 로 전환, 이미지 ID 일치" || fail "V13 $tag 이미지 ID 불일치"
			body="$(docker exec "$id" bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /actuator/health HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3 && cat <&3' | tr -d '\r' | sed '1,/^$/d')"
			health_ok "$body" && pass "V13 $tag 내부 health UP" || fail "V13 $tag health '$body'"
		else
			fail "V13 $tag 기동 실패 또는 시간 초과"
		fi
	done
	finish
}

cmd_status() {
	docker ps -a --filter "label=com.docker.compose.project=$verify_project" --format '{{.Names}} {{.Image}} {{.Status}}'
	docker ps -a --filter "label=com.docker.compose.project=$restore_project" --format '{{.Names}} {{.Image}} {{.Status}}'
	docker volume ls --filter "label=com.docker.compose.project=$verify_project" --format '{{.Name}}'
	docker volume ls --filter "label=com.docker.compose.project=$restore_project" --format '{{.Name}}'
}

command="${1:-}"
[ $# -gt 0 ] && shift
case "$command" in
	init-env) cmd_init_env ;;
	static) cmd_static ;;
	image-check) cmd_image_check ;;
	up) cmd_up ;;
	check) cmd_check "$@" ;;
	persist) cmd_persist ;;
	backup) cmd_backup ;;
	swap) cmd_swap "$@" ;;
	status) cmd_status ;;
	*) sed -n '2,26p' "$0"; exit 2 ;;
esac
