#!/usr/bin/env bash
# SSM 문서 BeoneDeploy로 서버 배포를 요청하고 끝날 때까지 기다린다(결정 40 인터페이스 계약).
# 사용: ssm-deploy.sh <staging|production> <api-digest> <web-digest> <git-sha>
# 필요한 환경 변수: INSTANCE_ID, AWS 자격 증명(OIDC로 받은 임시 자격 증명), AWS_REGION.
# 출력은 상태와 요약만 남긴다. 서버 표준 출력 전문은 남기지 않는다(SSM 콘솔·서버 로그에서 본다).
# 실패 닫힘: 인자 형식이 틀리거나, 요청·조회가 실패하거나, 정해진 시간 안에 Success가 아니면 0이 아닌 값으로 끝난다.
set -euo pipefail

env_name="${1:-}"; api_digest="${2:-}"; web_digest="${3:-}"; git_sha="${4:-}"
document="BeoneDeploy"
wait_seconds="${SSM_WAIT_SECONDS:-1800}"   # 문서 executionTimeout(1500초)보다 길게 둔다
poll_seconds="${SSM_POLL_SECONDS:-10}"
max_lookup_failures="${SSM_MAX_LOOKUP_FAILURES:-12}"

die() { echo "ssm-deploy: $*" >&2; exit 1; }

case "$env_name" in staging|production) ;; *) die "환경은 staging 또는 production이어야 합니다" ;; esac
[[ "$api_digest" =~ ^sha256:[0-9a-f]{64}$ ]] || die "ApiDigest 형식이 틀립니다"
[[ "$web_digest" =~ ^sha256:[0-9a-f]{64}$ ]] || die "WebDigest 형식이 틀립니다"
[[ "$git_sha" =~ ^[0-9a-f]{40}$ ]] || die "GitSha 형식이 틀립니다"
[[ "${INSTANCE_ID:-}" =~ ^i-[0-9a-f]{8,17}$ ]] || die "INSTANCE_ID 형식이 틀립니다"

command_id="$(aws ssm send-command \
	--document-name "$document" \
	--instance-ids "$INSTANCE_ID" \
	--comment "beone ${env_name} ${git_sha:0:12}" \
	--parameters "Environment=${env_name},ApiDigest=${api_digest},WebDigest=${web_digest},GitSha=${git_sha}" \
	--query 'Command.CommandId' --output text)" || die "send-command 실패"
[[ "$command_id" =~ ^[0-9a-f-]{36}$ ]] || die "CommandId를 받지 못했습니다"
echo "command_id=${command_id}"

deadline=$((SECONDS + wait_seconds))
lookup_failures=0
status=""
while :; do
	sleep "$poll_seconds"
	# 요청 직후에는 호출 기록이 아직 없을 수 있다(InvocationDoesNotExist). 조회 실패가 연속으로 쌓이면 실패로 닫는다.
	if status="$(aws ssm get-command-invocation --command-id "$command_id" --instance-id "$INSTANCE_ID" \
		--query 'Status' --output text 2>/dev/null)"; then
		lookup_failures=0
	else
		lookup_failures=$((lookup_failures + 1))
		echo "status=조회 실패(${lookup_failures}/${max_lookup_failures})"
		[ "$lookup_failures" -lt "$max_lookup_failures" ] || die "상태 조회가 계속 실패했습니다"
		[ "$SECONDS" -lt "$deadline" ] || die "대기 시간 초과(조회 실패 중)"
		continue
	fi
	echo "status=${status}"
	case "$status" in
		Success) break ;;
		Pending|InProgress|Delayed) ;;
		*) # Failed, Cancelled, TimedOut, Cancelling 등 정상 완료가 아닌 모든 상태
			aws ssm get-command-invocation --command-id "$command_id" --instance-id "$INSTANCE_ID" \
				--query '{Status:Status,Details:StatusDetails,ResponseCode:ResponseCode}' --output text || true
			die "배포 실패: ${status}" ;;
	esac
	[ "$SECONDS" -lt "$deadline" ] || die "대기 시간 초과(마지막 상태 ${status})"
done

aws ssm get-command-invocation --command-id "$command_id" --instance-id "$INSTANCE_ID" \
	--query '{Status:Status,Details:StatusDetails,ResponseCode:ResponseCode}' --output text || true
echo "배포 완료: ${env_name} ${git_sha}"
