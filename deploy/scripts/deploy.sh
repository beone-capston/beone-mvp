#!/usr/bin/env bash
# 서버 배포 스크립트(결정 40). SSM 문서 BeoneDeployStaging(환경 인자 staging 고정)·BeoneDeployProduction(production 고정)이
# 서버의 /opt/beone/deploy/deploy.sh로 부른다.
# 절차·종료 코드·상태 파일 설명: docs/deployment/server-deploy.md
#
# 사용: deploy.sh <staging|production> <ApiDigest> <WebDigest> <GitSha>
#   ApiDigest·WebDigest: sha256:<64자리 소문자 16진수>, GitSha: 40자리 소문자 16진수
#
# 순서
#   1. 상태 파일에서 현재 digest를 읽는다(없으면 첫 배포)
#   2. digest로 pull(태그 pull 금지)
#   3. 운영만: 배포 직전 DB 덤프와 짝 기록(백업 id·이전 digest·새 digest·GitSha). 실패하면 배포하지 않는다
#   4. backend up -d(digest 고정 이미지 변수)
#   5. health 대기(시간 상한)
#   6. 웹 dist 추출 → 릴리스 디렉터리 → 심볼릭 링크 원자 전환
#   7. 무변경 공개 스모크(smoke-public.sh)
#   4~7에서 실패하면 이전 digest의 backend와 이전 웹 링크로 복귀하고 health를 다시 본다. DB는 되돌리지 않는다.
#
# 종료 코드
#   0 배포 성공
#   1 배포 실패 뒤 복귀 실패 또는 복귀 대상 없음(사람이 바로 확인해야 함)
#   2 인자 형식 오류(아무것도 하지 않음)
#   3 배포 전 단계 실패(잠금·가드·상태 파일·pull·백업). 실행 중인 서비스는 바꾸지 않음
#   4 배포 실패, 이전 상태로 복귀 성공
#
# 출력에는 상태·digest·단계만 쓴다. 비밀값·env 내용·DB 데이터는 출력하지 않는다.
# 서버에서는 root(SSM)로 실행한다. 이 스크립트는 sudo를 쓰지 않는다.
set -euo pipefail
umask 077

usage() { echo "사용: deploy.sh <staging|production> <sha256:ApiDigest> <sha256:WebDigest> <GitSha 40자리>" >&2; exit 2; }
[ $# -eq 4 ] || usage
ENVIRONMENT="$1" API_DIGEST="$2" WEB_DIGEST="$3" GIT_SHA="$4"
case "$ENVIRONMENT" in staging | production) ;; *) usage ;; esac
[[ "$API_DIGEST" =~ ^sha256:[0-9a-f]{64}$ ]] || usage
[[ "$WEB_DIGEST" =~ ^sha256:[0-9a-f]{64}$ ]] || usage
[[ "$GIT_SHA" =~ ^[0-9a-f]{40}$ ]] || usage

# 경로. BEONE_DEPLOY_ROOT는 오프라인 자기검사가 임시 폴더를 쓰려고 바꾼다(서버 기본값 /opt/beone)
ROOT="${BEONE_DEPLOY_ROOT:-/opt/beone}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
STATE_DIR="$ROOT/deploy/state"
API_REPO=ghcr.io/beone-capston/beone-api
WEB_REPO=ghcr.io/beone-capston/beone-web
# web 이미지 안의 dist 위치. 워크플로의 deploy/web/Dockerfile(COPY . /dist/)과 같다
WEB_DIST_PATH="${BEONE_WEB_DIST_PATH:-/dist}"
HEALTH_TIMEOUT="${BEONE_DEPLOY_HEALTH_TIMEOUT:-300}"
HEALTH_INTERVAL=5

case "$ENVIRONMENT" in
	production)
		APP_DIR="$ROOT/app"
		PROJECT=beone-app
		EXPECTED_SERVICES='backend mysql'
		UP_SERVICES=(backend)          # 운영 MySQL은 다시 만들지 않는다
		WEB_BASE="$ROOT/web"           # 운영 웹 폴더(current 링크). 공개 nginx 정적 루트가 이 링크를 쓴다
		SMOKE_BASE="${BEONE_DEPLOY_SMOKE_BASE:-https://beonepay.site}"
		;;
	staging)
		APP_DIR="$ROOT/staging"
		PROJECT=beone-staging
		EXPECTED_SERVICES='backend mysql web'
		UP_SERVICES=()                 # 전체(mysql·backend·web)
		WEB_BASE="$ROOT/staging/web"   # 스테이징 nginx가 이 폴더를 마운트하고 current를 루트로 쓴다
		SMOKE_BASE="${BEONE_DEPLOY_SMOKE_BASE:-http://127.0.0.1:18081}"   # 기본 인증 없는 루프백 점검 포트
		;;
esac
COMPOSE_FILE="$APP_DIR/compose.yaml"
ENV_FILE="$APP_DIR/.env"
STATE_FILE="$STATE_DIR/$ENVIRONMENT.state"
HISTORY_FILE="$STATE_DIR/$ENVIRONMENT.history"
NEW_API_REF="$API_REPO@$API_DIGEST"
NEW_WEB_REF="$WEB_REPO@$WEB_DIGEST"

log() { echo "[deploy $(date -u +%H:%M:%SZ) $ENVIRONMENT] $*"; }
stop_pre() { log "중단(변경 없음): $*"; exit 3; }
compose() { docker compose -f "$COMPOSE_FILE" -p "$PROJECT" --env-file "$ENV_FILE" "$@"; }
# 이미지 변수만 덮어써서 compose를 실행한다. 셸 환경 값이 --env-file 값보다 우선한다
compose_with() { local ref="$1"; shift; BEONE_API_IMAGE="$ref" compose "$@"; }

log "시작: api=$API_DIGEST web=$WEB_DIGEST git=$GIT_SHA"

# 0. 동시 실행 방지. 두 환경이 같은 서버 메모리를 쓰므로 잠금은 하나다
mkdir -p "$STATE_DIR" || stop_pre "상태 폴더를 만들지 못함"
exec 9> "$STATE_DIR/deploy.lock" || stop_pre "잠금 파일을 열지 못함"
flock -n 9 || stop_pre "다른 배포가 실행 중"

# 0-1. 대상 파일과 CODEF 가드. CODEF는 자동 배포 대상이 아니다(결정 40-7). 값은 출력하지 않는다
[ -f "$COMPOSE_FILE" ] || stop_pre "compose 파일 없음: $COMPOSE_FILE"
[ -f "$ENV_FILE" ] || stop_pre "env 파일 없음: $ENV_FILE"
env_names=$(awk -F= '!/^[[:space:]]*#/ && NF { sub(/^[[:space:]]*(export[[:space:]]+)?/, "", $1); print $1 }' "$ENV_FILE") || stop_pre "env 파일을 읽지 못함"
codef_names=$(printf '%s\n' "$env_names" | grep -E '^(CODEF_|BEONE_CODEF_)' || true)
[ -z "$codef_names" ] || stop_pre "env에 CODEF 키 이름이 있음: $(echo $codef_names)"
for f in "$ENV_FILE" "$COMPOSE_FILE"; do
	rc=0; grep -q 'codef-live' "$f" || rc=$?
	case "$rc" in 0) stop_pre "codef-live 프로필 지정이 있음: $f" ;; 1) ;; *) stop_pre "가드 검사 실패(읽기 오류): $f" ;; esac
done

# 0-2. compose 해석. 값이 풀려 출력되지 않게 --quiet·--services만 쓴다
compose_with "$NEW_API_REF" config --quiet || stop_pre "compose config 실패(값 누락 등)"
services=$(compose_with "$NEW_API_REF" config --services) || stop_pre "compose 서비스 조회 실패"
[ "$(printf '%s\n' "$services" | sort | tr '\n' ' ' | sed 's/ $//')" = "$EXPECTED_SERVICES" ] \
	|| stop_pre "compose 서비스가 기대($EXPECTED_SERVICES)와 다름: $(echo $services)"

# 1. 현재 상태
PREV_API="" PREV_WEB="" PREV_GIT=""
if [ -e "$STATE_FILE" ]; then
	PREV_API=$(sed -n 's/^api_digest=//p' "$STATE_FILE") || stop_pre "상태 파일을 읽지 못함"
	PREV_WEB=$(sed -n 's/^web_digest=//p' "$STATE_FILE") || stop_pre "상태 파일을 읽지 못함"
	PREV_GIT=$(sed -n 's/^git_sha=//p' "$STATE_FILE") || stop_pre "상태 파일을 읽지 못함"
	[[ "$PREV_API" =~ ^sha256:[0-9a-f]{64}$ ]] && [[ "$PREV_WEB" =~ ^sha256:[0-9a-f]{64}$ ]] \
		|| stop_pre "상태 파일 손상(digest 형식 아님): $STATE_FILE"
	log "1. 현재: api=$PREV_API web=$PREV_WEB git=${PREV_GIT:-?}"
else
	log "1. 상태 파일 없음: 첫 배포로 처리(실패해도 backend 복귀 대상 없음)"
fi
PREV_LINK=""
if [ -L "$WEB_BASE/current" ]; then
	PREV_LINK=$(readlink "$WEB_BASE/current") || stop_pre "현재 웹 링크를 읽지 못함"
fi

# 2. digest로 pull
log "2. pull"
docker pull "$NEW_API_REF" > /dev/null || stop_pre "api 이미지 pull 실패"
docker pull "$NEW_WEB_REF" > /dev/null || stop_pre "web 이미지 pull 실패"

# 3. 운영만: 배포 직전 덤프와 짝 기록(docs/deployment/backend-compose.md 백업 방식)
BACKUP_ID=""
if [ "$ENVIRONMENT" = production ]; then
	log "3. DB 백업"
	bd=$(mktemp -d "$APP_DIR/backups/app-$(date -u +%Y%m%dT%H%M%SZ)-XXXXXX") || stop_pre "백업 폴더를 만들지 못함"
	B="$bd/app.sql"
	# `( … ) || …` 안에서는 set -e가 꺼지므로 단계마다 && 로 잇고 실패를 직접 판정한다
	backup_dump() {
		( set -o noclobber; : > "$B.partial" ) \
			&& compose exec -T mysql bash -c 'mysqldump --defaults-extra-file=<(printf "[client]\nuser=%s\npassword=%s\n" "$MYSQL_USER" "$MYSQL_PASSWORD") --single-transaction --no-tablespaces "$MYSQL_DATABASE"' < /dev/null >| "$B.partial" \
			&& tail -n 1 "$B.partial" | grep -q '^-- Dump completed' \
			&& mv -n "$B.partial" "$B" && [ ! -e "$B.partial" ] && [ -f "$B" ]
	}
	backup_dump || { rm -f "$B.partial"; stop_pre "백업 실패(덤프). 배포하지 않음"; }
	# 짝 기록: 실제 실행 중인 backend 이미지와 백업 시점 Flyway 이력
	backup_record() {
		local cid image_id image_tag revision
		cid=$(compose ps -q backend) && [ -n "$cid" ] || { log "짝 기록: 실행 중인 backend 없음"; return 1; }
		image_id=$(docker inspect --format '{{.Image}}' "$cid") && [ -n "$image_id" ] || { log "짝 기록: 이미지 ID 조회 실패"; return 1; }
		image_tag=$(docker inspect --format '{{.Config.Image}}' "$cid") && [ -n "$image_tag" ] || { log "짝 기록: 이미지 이름 조회 실패"; return 1; }
		[ "$(docker image inspect --format '{{.Id}}' "$image_tag")" = "$image_id" ] || { log "짝 기록: 실행 중 이미지 ID와 이름 불일치"; return 1; }
		revision=$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$image_id") || { log "짝 기록: 라벨 조회 실패"; return 1; }
		local dump_sum flyway_sum
		compose exec -T mysql bash -c 'mysql --defaults-extra-file=<(printf "[client]\nuser=%s\npassword=%s\n" "$MYSQL_USER" "$MYSQL_PASSWORD") -N -B -e "SELECT installed_rank, version, script, checksum, success FROM flyway_schema_history ORDER BY installed_rank" "$MYSQL_DATABASE"' < /dev/null > "$B.flyway.partial" \
			&& [ -s "$B.flyway.partial" ] || { log "짝 기록: Flyway 이력을 읽지 못함"; return 1; }
		# 해시는 변수로 먼저 구하고 형식을 확인한다(명령 치환 실패가 echo에 묻히지 않게)
		dump_sum=$(sha256sum "$B" | cut -d' ' -f1) && [[ "$dump_sum" =~ ^[0-9a-f]{64}$ ]] || { log "짝 기록: 덤프 sha256 계산 실패"; return 1; }
		flyway_sum=$(sha256sum "$B.flyway.partial" | cut -d' ' -f1) && [[ "$flyway_sum" =~ ^[0-9a-f]{64}$ ]] || { log "짝 기록: 이력 sha256 계산 실패"; return 1; }
		printf '%s\n' "dump_file=$(basename "$B")" "dump_sha256=$dump_sum" "image_id=$image_id" "image_tag=$image_tag" \
			"image_revision=${revision:-<no value>}" "flyway_sha256=$flyway_sum" "deploy_prev_api_digest=${PREV_API:-none}" \
			"deploy_new_api_digest=$API_DIGEST" "deploy_new_web_digest=$WEB_DIGEST" "deploy_git_sha=$GIT_SHA" > "$B.record.partial" \
			&& mv "$B.flyway.partial" "$B.flyway" && mv "$B.record.partial" "$B.record"
	}
	backup_record || stop_pre "백업 짝 기록 실패(덤프는 남김: $bd). 배포하지 않음"
	BACKUP_ID=$(basename "$bd")
	log "3. 백업 완료: $BACKUP_ID"
else
	log "3. 스테이징: DB 백업 없음"
fi

# 4~7에서 쓰는 health 대기와 복귀
wait_healthy() {
	local want="$1" waited=0 cid st img
	while [ "$waited" -le "$HEALTH_TIMEOUT" ]; do
		if cid=$(compose ps -q backend) && [ -n "$cid" ] \
			&& st=$(docker inspect --format '{{.State.Status}}/{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$cid") \
			&& img=$(docker inspect --format '{{.Config.Image}}' "$cid"); then
			if [ "$img" = "$want" ] && [ "$st" = running/healthy ]; then return 0; fi
			case "$st" in exited/* | dead/*) log "backend 종료됨($st)"; return 1 ;; esac
		fi
		sleep "$HEALTH_INTERVAL"
		waited=$((waited + HEALTH_INTERVAL))
	done
	log "health 시간 초과(${HEALTH_TIMEOUT}s)"
	return 1
}

WEB_SWITCHED=0
switch_link() {
	# 상대 경로 링크를 임시 이름으로 만든 뒤 rename으로 바꾼다(원자 전환). 컨테이너 안에서도 같은 상대 경로로 풀린다
	ln -sfn "$1" "$WEB_BASE/current.next" && mv -T "$WEB_BASE/current.next" "$WEB_BASE/current"
}

rollback() {
	local reason="$1" ok=1
	log "실패: $reason. 복귀 시작"
	if [ "$WEB_SWITCHED" = 1 ]; then
		if [ -n "$PREV_LINK" ] && switch_link "$PREV_LINK"; then log "웹 링크 복귀: $PREV_LINK"
		elif [ -z "$PREV_LINK" ]; then log "웹 복귀 대상 없음(이전 링크 없음)"; ok=0
		else log "웹 링크 복귀 실패"; ok=0; fi
	fi
	if [ -z "$PREV_API" ]; then
		log "backend 복귀 대상 없음(첫 배포). 새 backend 상태를 사람이 확인해야 함"
		ok=0
	else
		local prev_ref="$API_REPO@$PREV_API"
		if docker image inspect "$prev_ref" > /dev/null 2>&1 || docker pull "$prev_ref" > /dev/null; then
			if compose_with "$prev_ref" up -d ${UP_SERVICES[@]+"${UP_SERVICES[@]}"} && wait_healthy "$prev_ref"; then log "backend 복귀: $PREV_API healthy"
			else log "backend 복귀 실패: $PREV_API"; ok=0; fi
		else
			log "backend 복귀 실패: 이전 이미지 없음·pull 실패($PREV_API)"; ok=0
		fi
	fi
	echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) failed api=$API_DIGEST web=$WEB_DIGEST git=$GIT_SHA backup=${BACKUP_ID:-none} reason=$reason rollback=$([ "$ok" = 1 ] && echo ok || echo failed)" >> "$HISTORY_FILE" || true
	if [ "$ok" = 1 ]; then
		log "복귀 완료(DB는 되돌리지 않음. 필요하면 docs/deployment/backend-compose.md 복원 절차)"
		exit 4
	fi
	echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!" >&2
	echo "!! 배포 실패 뒤 복귀 실패 또는 복귀 대상 없음($ENVIRONMENT). 즉시 확인 필요 !!" >&2
	echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!" >&2
	exit 1
}

# 4. backend up
log "4. backend up -d"
compose_with "$NEW_API_REF" up -d ${UP_SERVICES[@]+"${UP_SERVICES[@]}"} || rollback "backend up 실패"

# 5. health
log "5. health 대기(최대 ${HEALTH_TIMEOUT}s)"
wait_healthy "$NEW_API_REF" || rollback "backend health 실패"

# 6. 웹 릴리스
log "6. 웹 릴리스"
mkdir -p "$WEB_BASE/releases" || rollback "웹 릴리스 폴더를 만들지 못함"
# nginx 작업 프로세스(root 아님)가 읽도록 웹 폴더만 755로 둔다(umask 077의 예외)
chmod 755 "$WEB_BASE" "$WEB_BASE/releases" || rollback "웹 폴더 권한 설정 실패"
rel=$(mktemp -d "$WEB_BASE/releases/${GIT_SHA:0:12}-XXXXXX") || rollback "웹 릴리스 폴더를 만들지 못함"
chmod 755 "$rel" || rollback "웹 릴리스 권한 설정 실패"
wcid=$(docker create "$NEW_WEB_REF" noop) || rollback "web 이미지 컨테이너 생성 실패"
if ! docker cp "$wcid:$WEB_DIST_PATH/." "$rel/" > /dev/null; then
	docker rm "$wcid" > /dev/null || true
	rollback "web dist 추출 실패"
fi
docker rm "$wcid" > /dev/null || log "임시 web 컨테이너 삭제 실패(무시): $wcid"
[ -f "$rel/index.html" ] || rollback "web dist에 index.html 없음"
WEB_SWITCHED=1
switch_link "releases/$(basename "$rel")" || rollback "웹 링크 전환 실패"
log "6. 웹 링크: releases/$(basename "$rel")"

# 7. 무변경 공개 스모크
log "7. 공개 스모크: $SMOKE_BASE"
"$SCRIPT_DIR/smoke-public.sh" "$SMOKE_BASE" || rollback "공개 스모크 실패"

# 성공: 상태 기록
{
	echo "api_digest=$API_DIGEST"
	echo "web_digest=$WEB_DIGEST"
	echo "git_sha=$GIT_SHA"
	echo "web_release=releases/$(basename "$rel")"
	echo "backup_id=${BACKUP_ID:-none}"
	echo "deployed_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
} > "$STATE_FILE.next" && mv "$STATE_FILE.next" "$STATE_FILE" || { log "상태 파일 기록 실패(배포는 반영됨). 즉시 확인 필요"; exit 1; }
echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) ok api=$API_DIGEST web=$WEB_DIGEST git=$GIT_SHA prev_api=${PREV_API:-none} backup=${BACKUP_ID:-none}" >> "$HISTORY_FILE" || log "이력 기록 실패(무시)"
log "완료"
exit 0
