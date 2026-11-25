#!/usr/bin/env bash
# 서버 배포 스크립트(결정 40). SSM 문서 BeoneDeployStaging(환경 인자 staging 고정)·BeoneDeployProduction(production 고정)이
# 서버의 /opt/beone/deploy/deploy.sh로 부른다.
# 절차·종료 코드·상태 파일 설명: docs/deployment/server-deploy.md
#
# 사용: deploy.sh <staging|production> <ApiDigest> <WebDigest> <GitSha>
#   ApiDigest·WebDigest: sha256:<64자리 소문자 16진수>, GitSha: 40자리 소문자 16진수
#
# 운영 최초 배포(bootstrap)는 공개 nginx가 /api를 아직 연결하지 않아 공개 스모크를 통과할 수 없으므로 세 단계로 나눈다.
# 승인 창 안에서 사람이 서버에서 직접 실행한다. 첫 인자가 bootstrap-*라서 SSM 문서(첫 인자 환경 고정)로는 부를 수 없다.
#   deploy.sh bootstrap-prepare production <ApiDigest> <WebDigest> <GitSha>
#       상태 파일·대기 파일이 없을 때만. 복귀 기준(실행 중 backend 이미지, 없으면 none)·백업 기록(mysql이 있으면 필수,
#       mysql 컨테이너·볼륨이 없으면 backup=none) → 1~6단계 → 비공개 검증 → 대기 파일만 쓴다
#   (사람) 공개 nginx 네트워크·마운트·설정 → nginx -t → reload
#   deploy.sh bootstrap-confirm production   공개 스모크를 통과해야만 상태 파일을 쓴다(실패하면 종료 1, 대기 파일 유지)
#   deploy.sh bootstrap-abort production     backend를 복귀 기준 이미지로(none이면 새 backend 정지), 웹 링크를 이전 값(없었으면 링크 제거)으로 되돌린다
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
# bootstrap 종료 코드
#   prepare: 0 준비 완료(미확정, 대기 파일만), 2·3·4·1은 위와 같음(4·1의 복귀 대상은 복귀 기준 이미지)
#   confirm: 0 확정(상태 파일 기록), 1 미확정(스모크·health 실패 또는 상태 파일 기록 실패), 2 인자 오류, 3 잠금·대기 파일 없음 등
#   abort:   0 복귀 완료(상태 파일 없음), 1 복귀 실패(대기 파일 유지, 다시 실행 가능), 2 인자 오류, 3 잠금·대기 파일 없음 등
#
# 출력에는 상태·digest·단계만 쓴다. 비밀값·env 내용·DB 데이터는 출력하지 않는다.
# 서버에서는 root(SSM)로 실행한다. 이 스크립트는 sudo를 쓰지 않는다.
set -euo pipefail
umask 077

usage() {
	echo "사용: deploy.sh <staging|production> <sha256:ApiDigest> <sha256:WebDigest> <GitSha 40자리>" >&2
	echo "      deploy.sh bootstrap-prepare production <sha256:ApiDigest> <sha256:WebDigest> <GitSha 40자리>" >&2
	echo "      deploy.sh bootstrap-confirm production | deploy.sh bootstrap-abort production" >&2
	exit 2
}
# MODE: deploy(정상 배포) | prepare | confirm | abort(운영 최초 배포 단계)
MODE=deploy
case "${1:-}" in
	bootstrap-prepare | bootstrap-confirm | bootstrap-abort) MODE="${1#bootstrap-}"; shift ;;
esac
API_DIGEST="" WEB_DIGEST="" GIT_SHA=""
if [ "$MODE" = deploy ] || [ "$MODE" = prepare ]; then
	[ $# -eq 4 ] || usage
	ENVIRONMENT="$1" API_DIGEST="$2" WEB_DIGEST="$3" GIT_SHA="$4"
	[[ "$API_DIGEST" =~ ^sha256:[0-9a-f]{64}$ ]] || usage
	[[ "$WEB_DIGEST" =~ ^sha256:[0-9a-f]{64}$ ]] || usage
	[[ "$GIT_SHA" =~ ^[0-9a-f]{40}$ ]] || usage
else
	[ $# -eq 1 ] || usage
	ENVIRONMENT="$1"
fi
case "$ENVIRONMENT" in staging | production) ;; *) usage ;; esac
# bootstrap은 운영 전용이다. 스테이징은 루프백 점검 포트로 첫 배포부터 정상 배포 경로를 쓴다
if [ "$MODE" != deploy ] && [ "$ENVIRONMENT" != production ]; then
	echo "bootstrap-$MODE는 production 전용이다(받은 값: $ENVIRONMENT)" >&2
	usage
fi

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
BOOT_FILE="$STATE_DIR/$ENVIRONMENT.bootstrap"   # bootstrap 대기 파일(준비 완료·미확정)
NEW_API_REF="$API_REPO@$API_DIGEST"
NEW_WEB_REF="$WEB_REPO@$WEB_DIGEST"

LOG_TAG="$ENVIRONMENT"; [ "$MODE" = deploy ] || LOG_TAG="$ENVIRONMENT bootstrap-$MODE"
log() { echo "[deploy $(date -u +%H:%M:%SZ) $LOG_TAG] $*"; }
stop_pre() { log "중단(변경 없음): $*"; exit 3; }
compose() { docker compose -f "$COMPOSE_FILE" -p "$PROJECT" --env-file "$ENV_FILE" "$@"; }
# 이미지 변수만 덮어써서 compose를 실행한다. 셸 환경 값이 --env-file 값보다 우선한다
compose_with() { local ref="$1"; shift; BEONE_API_IMAGE="$ref" compose "$@"; }

# 0. 동시 실행 방지. 두 환경이 같은 서버 메모리를 쓰므로 잠금은 하나다. bootstrap 세 단계도 같은 잠금을 쓴다
mkdir -p "$STATE_DIR" || stop_pre "상태 폴더를 만들지 못함"
exec 9> "$STATE_DIR/deploy.lock" || stop_pre "잠금 파일을 열지 못함"
flock -n 9 || stop_pre "다른 배포가 실행 중"

# health 대기: 실행 중 backend가 want 이미지이고 healthy가 될 때까지(시간 상한)
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

switch_link() {
	# 상대 경로 링크를 임시 이름으로 만든 뒤 rename으로 바꾼다(원자 전환). 컨테이너 안에서도 같은 상대 경로로 풀린다
	ln -sfn "$1" "$WEB_BASE/current.next" && mv -T "$WEB_BASE/current.next" "$WEB_BASE/current"
}

# bootstrap 웹 복귀: 이전 링크가 있으면 그 값으로, 없었으면(안내 화면만 있던 상태) 새 링크만 지운다
restore_boot_link() {
	local prev="$1"
	if [ -n "$prev" ]; then
		switch_link "$prev" && log "웹 링크 복귀: $prev"
	elif [ -L "$WEB_BASE/current" ]; then
		rm -f "$WEB_BASE/current" && log "웹 링크 제거(이전 링크 없음). 릴리스 폴더는 남김"
	else
		return 0
	fi
}

# bootstrap 복귀 기준 이미지 참조. 기록한 이름이 지금도 같은 이미지 ID를 가리키면 이름을, 아니면 ID를 쓴다
boot_base_ref() {
	local tag="$1" id="$2" now
	now=$(docker image inspect --format '{{.Id}}' "$tag" 2> /dev/null) || now=""
	if [ "$now" = "$id" ]; then echo "$tag"; else echo "$id"; fi
}

# bootstrap backend 복귀: 기준이 none이면 새 backend를 멈춘다(볼륨은 지우지 않음), 아니면 기준 이미지로 up·health
boot_restore_backend() {
	local tag="$1" id="$2" ref
	if [ "$tag" = none ]; then
		compose stop backend > /dev/null && log "backend 정지(복귀 기준 none, 볼륨 유지)"
		return
	fi
	ref=$(boot_base_ref "$tag" "$id")
	compose_with "$ref" up -d backend && wait_healthy "$ref" && log "backend 복귀(복귀 기준): $ref healthy"
}

boot_field() { sed -n "s/^$1=//p" "$BOOT_FILE"; }

banner() {
	echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!" >&2
	echo "!! $* !!" >&2
	echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!" >&2
}

# bootstrap-confirm·abort: 대기 파일을 읽어 확정하거나 되돌리고 끝낸다
if [ "$MODE" = confirm ] || [ "$MODE" = abort ]; then
	[ -f "$BOOT_FILE" ] || stop_pre "bootstrap 대기 파일 없음($BOOT_FILE). 먼저 bootstrap-prepare"
	[ ! -e "$STATE_FILE" ] || stop_pre "상태 파일이 이미 있음($STATE_FILE). bootstrap 대상이 아님"
	[ -f "$COMPOSE_FILE" ] || stop_pre "compose 파일 없음: $COMPOSE_FILE"
	[ -f "$ENV_FILE" ] || stop_pre "env 파일 없음: $ENV_FILE"
	API_DIGEST=$(boot_field api_digest) WEB_DIGEST=$(boot_field web_digest) GIT_SHA=$(boot_field git_sha)
	B_RELEASE=$(boot_field web_release) B_PREV_LINK=$(boot_field prev_link) BACKUP_ID=$(boot_field backup_id)
	B_BASE_TAG=$(boot_field base_image_tag) B_BASE_ID=$(boot_field base_image_id)
	[[ "$API_DIGEST" =~ ^sha256:[0-9a-f]{64}$ ]] && [[ "$WEB_DIGEST" =~ ^sha256:[0-9a-f]{64}$ ]] && [[ "$GIT_SHA" =~ ^[0-9a-f]{40}$ ]] \
		&& [[ "$B_RELEASE" =~ ^releases/[^/]+$ ]] && [ -n "$B_BASE_TAG" ] && [ -n "$B_BASE_ID" ] \
		|| stop_pre "bootstrap 대기 파일 손상: $BOOT_FILE"
	NEW_API_REF="$API_REPO@$API_DIGEST"
	log "시작: api=$API_DIGEST web=$WEB_DIGEST git=$GIT_SHA release=$B_RELEASE"
	ts=$(date -u +%Y%m%dT%H%M%SZ)

	if [ "$MODE" = confirm ]; then
		fail_confirm() {
			log "미확정: $1. 상태 파일을 쓰지 않음(대기 파일 유지)"
			echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) bootstrap-confirm-failed api=$API_DIGEST web=$WEB_DIGEST git=$GIT_SHA reason=$1" >> "$HISTORY_FILE" || true
			banner "bootstrap 미확정($1). 원인을 고쳐 confirm을 다시 하거나, 공개 nginx를 P3 백업으로 되돌린 뒤 bootstrap-abort"
			exit 1
		}
		[ "$(readlink "$WEB_BASE/current" 2> /dev/null)" = "$B_RELEASE" ] || fail_confirm "웹 링크가 준비한 릴리스와 다름"
		log "1. backend health(준비한 이미지)"
		wait_healthy "$NEW_API_REF" || fail_confirm "backend health 실패"
		log "2. 공개 스모크: $SMOKE_BASE"
		"$SCRIPT_DIR/smoke-public.sh" "$SMOKE_BASE" || fail_confirm "공개 스모크 실패"
		{
			echo "api_digest=$API_DIGEST"
			echo "web_digest=$WEB_DIGEST"
			echo "git_sha=$GIT_SHA"
			echo "web_release=$B_RELEASE"
			echo "backup_id=${BACKUP_ID:-none}"
			echo "deployed_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
		} > "$STATE_FILE.next" && mv "$STATE_FILE.next" "$STATE_FILE" || fail_confirm "상태 파일 기록 실패"
		mv "$BOOT_FILE" "$BOOT_FILE.confirmed-$ts" || log "대기 파일 이름 변경 실패(무시, 상태 파일은 기록됨)"
		echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) ok bootstrap api=$API_DIGEST web=$WEB_DIGEST git=$GIT_SHA prev_api=none backup=${BACKUP_ID:-none}" >> "$HISTORY_FILE" || log "이력 기록 실패(무시)"
		log "확정 완료. 이후 배포는 정상 경로(deploy.sh production …)를 쓴다"
		exit 0
	fi

	# abort
	ok=1
	if restore_boot_link "$B_PREV_LINK"; then :; else log "웹 링크 복귀 실패"; ok=0; fi
	boot_restore_backend "$B_BASE_TAG" "$B_BASE_ID" || { log "backend 복귀 실패"; ok=0; }
	echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) bootstrap-abort api=$API_DIGEST web=$WEB_DIGEST git=$GIT_SHA backup=${BACKUP_ID:-none} rollback=$([ "$ok" = 1 ] && echo ok || echo failed)" >> "$HISTORY_FILE" || true
	if [ "$ok" = 1 ]; then
		mv "$BOOT_FILE" "$BOOT_FILE.aborted-$ts" || log "대기 파일 이름 변경 실패(무시)"
		log "bootstrap 취소 완료(복귀 기준 $B_BASE_TAG, 상태 파일 없음. DB는 되돌리지 않음)"
		exit 0
	fi
	banner "bootstrap 취소 중 복귀 실패. 대기 파일 유지, 즉시 확인 필요"
	exit 1
fi

log "시작: api=$API_DIGEST web=$WEB_DIGEST git=$GIT_SHA"

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
if [ "$MODE" = prepare ]; then
	[ ! -e "$STATE_FILE" ] || stop_pre "상태 파일이 이미 있음($STATE_FILE). bootstrap은 최초 1회만, 정상 배포를 쓴다"
	[ ! -e "$BOOT_FILE" ] || stop_pre "bootstrap 대기 파일이 이미 있음($BOOT_FILE). bootstrap-confirm 또는 bootstrap-abort 먼저"
fi
# 운영 정상 배포는 bootstrap 확정 뒤에만 한다. 상태 파일이 없거나 bootstrap 대기 중이면 공개 스모크가 반드시 실패하는
# 첫 배포 경로로 들어가지 않고 아무것도 바꾸지 않은 채 멈춘다(SSM 자동 배포가 먼저 실행돼도 안전)
if [ "$MODE" = deploy ] && [ "$ENVIRONMENT" = production ]; then
	[ ! -e "$BOOT_FILE" ] || stop_pre "운영 bootstrap 진행 중($BOOT_FILE). bootstrap-confirm 또는 bootstrap-abort 먼저"
	[ -e "$STATE_FILE" ] || stop_pre "운영 상태 파일 없음. 운영 최초 배포는 bootstrap-prepare·confirm으로 한다(docs/deployment/server-deploy.md)"
fi
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

BASE_IMAGE_TAG="" BASE_IMAGE_ID="" DB_PRESENT=1
# bootstrap-prepare: 복귀 기준(실행 중 backend)과 DB 유무를 판정한다. 조회 명령이 실패하면 없음으로 보지 않고 멈춘다
if [ "$MODE" = prepare ]; then
	bcid=$(compose ps -q backend) || stop_pre "backend 조회 실패(없음으로 보지 않음)"
	if [ -n "$bcid" ]; then
		BASE_IMAGE_ID=$(docker inspect --format '{{.Image}}' "$bcid") && [ -n "$BASE_IMAGE_ID" ] || stop_pre "복귀 기준 이미지 ID 조회 실패"
		BASE_IMAGE_TAG=$(docker inspect --format '{{.Config.Image}}' "$bcid") && [ -n "$BASE_IMAGE_TAG" ] || stop_pre "복귀 기준 이미지 이름 조회 실패"
		log "1-1. 복귀 기준: $BASE_IMAGE_TAG"
	else
		BASE_IMAGE_TAG=none BASE_IMAGE_ID=none
		log "1-1. 복귀 기준: none(실행 중 backend 없음). 실패·취소 시 새 backend를 멈춘다"
	fi
	mcid=$(compose ps -q mysql) || stop_pre "mysql 조회 실패(없음으로 보지 않음)"
	if [ -z "$mcid" ]; then
		# 멈춘 mysql 컨테이너나 기존 볼륨이 있으면 데이터가 있을 수 있으므로 백업 없이 진행하지 않는다
		mall=$(compose ps -a -q mysql) || stop_pre "mysql 컨테이너 조회 실패"
		[ -z "$mall" ] || stop_pre "mysql 컨테이너가 멈춰 있음(데이터가 있을 수 있음). mysql을 띄운 뒤 다시 실행"
		vols=$(compose config --volumes) || stop_pre "compose 볼륨 조회 실패"
		for v in $vols; do
			found=$(docker volume ls -q --filter "name=^${PROJECT}_${v}\$") || stop_pre "볼륨 조회 실패: ${PROJECT}_$v"
			[ -z "$found" ] || stop_pre "볼륨이 이미 있음(${PROJECT}_$v, 데이터가 있을 수 있음). mysql을 띄운 뒤 다시 실행"
		done
		DB_PRESENT=0
	fi
fi

# 2. digest로 pull
log "2. pull"
docker pull "$NEW_API_REF" > /dev/null || stop_pre "api 이미지 pull 실패"
docker pull "$NEW_WEB_REF" > /dev/null || stop_pre "web 이미지 pull 실패"

# 3. 운영만: 배포 직전 덤프와 짝 기록(docs/deployment/backend-compose.md 백업 방식)
BACKUP_ID=""
if [ "$ENVIRONMENT" = production ] && [ "$DB_PRESENT" = 0 ]; then
	# mysql·볼륨이 모두 없음: 덤프할 DB가 없다. 짝 기록은 같은 키로 남기고 값에 none을 쓴다
	log "3. DB 백업: none(mysql 컨테이너·볼륨 없음)"
	rec="$APP_DIR/backups/bootstrap-$(date -u +%Y%m%dT%H%M%SZ)-nobackup.record"
	printf '%s\n' "backup=none" "dump_file=none" "dump_sha256=none" "image_id=$BASE_IMAGE_ID" "image_tag=$BASE_IMAGE_TAG" \
		"image_revision=none" "flyway_sha256=none" "deploy_prev_api_digest=none" \
		"deploy_new_api_digest=$API_DIGEST" "deploy_new_web_digest=$WEB_DIGEST" "deploy_git_sha=$GIT_SHA" > "$rec.partial" \
		&& mv "$rec.partial" "$rec" || stop_pre "백업 none 짝 기록 실패. 배포하지 않음"
	BACKUP_ID=none
elif [ "$ENVIRONMENT" = production ]; then
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
		if [ "$MODE" = prepare ] && [ "$BASE_IMAGE_ID" = none ]; then
			# bootstrap 복귀 기준 none: DB는 있지만 backend가 없음. 이미지 칸은 none
			image_id=none image_tag=none revision=none
		else
		cid=$(compose ps -q backend) && [ -n "$cid" ] || { log "짝 기록: 실행 중인 backend 없음"; return 1; }
		image_id=$(docker inspect --format '{{.Image}}' "$cid") && [ -n "$image_id" ] || { log "짝 기록: 이미지 ID 조회 실패"; return 1; }
		image_tag=$(docker inspect --format '{{.Config.Image}}' "$cid") && [ -n "$image_tag" ] || { log "짝 기록: 이미지 이름 조회 실패"; return 1; }
		[ "$(docker image inspect --format '{{.Id}}' "$image_tag")" = "$image_id" ] || { log "짝 기록: 실행 중 이미지 ID와 이름 불일치"; return 1; }
		revision=$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$image_id") || { log "짝 기록: 라벨 조회 실패"; return 1; }
		fi
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

# 4~7 실패 시 복귀
WEB_SWITCHED=0
rollback() {
	local reason="$1" ok=1
	log "실패: $reason. 복귀 시작"
	if [ "$WEB_SWITCHED" = 1 ] && [ "$MODE" = prepare ]; then
		restore_boot_link "$PREV_LINK" || { log "웹 링크 복귀 실패"; ok=0; }
	elif [ "$WEB_SWITCHED" = 1 ]; then
		if [ -n "$PREV_LINK" ] && switch_link "$PREV_LINK"; then log "웹 링크 복귀: $PREV_LINK"
		elif [ -z "$PREV_LINK" ]; then log "웹 복귀 대상 없음(이전 링크 없음)"; ok=0
		else log "웹 링크 복귀 실패"; ok=0; fi
	fi
	if [ "$MODE" = prepare ]; then
		boot_restore_backend "$BASE_IMAGE_TAG" "$BASE_IMAGE_ID" || { log "backend 복귀 실패(복귀 기준 $BASE_IMAGE_TAG)"; ok=0; }
	elif [ -z "$PREV_API" ]; then
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
	echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) $([ "$MODE" = prepare ] && echo bootstrap-prepare-)failed api=$API_DIGEST web=$WEB_DIGEST git=$GIT_SHA backup=${BACKUP_ID:-none} reason=$reason rollback=$([ "$ok" = 1 ] && echo ok || echo failed)" >> "$HISTORY_FILE" || true
	if [ "$ok" = 1 ]; then
		log "복귀 완료(DB는 되돌리지 않음. 필요하면 docs/deployment/backend-compose.md 복원 절차)"
		exit 4
	fi
	banner "배포 실패 뒤 복귀 실패 또는 복귀 대상 없음($ENVIRONMENT). 즉시 확인 필요"
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

# bootstrap-prepare: 공개 스모크 대신 비공개 검증 뒤 대기 파일만 쓴다(상태 파일은 confirm만 쓴다)
if [ "$MODE" = prepare ]; then
	log "7. 비공개 검증(공개 nginx 연결 전)"
	wait_healthy "$NEW_API_REF" || rollback "비공개 검증: backend health 실패"
	[ -f "$WEB_BASE/current/index.html" ] || rollback "비공개 검증: 웹 링크로 index.html을 찾지 못함"
	{
		echo "api_digest=$API_DIGEST"
		echo "web_digest=$WEB_DIGEST"
		echo "git_sha=$GIT_SHA"
		echo "web_release=releases/$(basename "$rel")"
		echo "prev_link=$PREV_LINK"
		echo "backup_id=${BACKUP_ID:-none}"
		echo "base_image_tag=$BASE_IMAGE_TAG"
		echo "base_image_id=$BASE_IMAGE_ID"
		echo "prepared_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
	} > "$BOOT_FILE.next" && mv "$BOOT_FILE.next" "$BOOT_FILE" || rollback "bootstrap 대기 파일 기록 실패"
	echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) bootstrap-prepared api=$API_DIGEST web=$WEB_DIGEST git=$GIT_SHA backup=${BACKUP_ID:-none} base=$BASE_IMAGE_TAG" >> "$HISTORY_FILE" || log "이력 기록 실패(무시)"
	log "준비 완료(미확정). 다음: 공개 nginx 설정·nginx -t·reload → deploy.sh bootstrap-confirm production"
	log "되돌리려면: deploy.sh bootstrap-abort production"
	exit 0
fi

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
