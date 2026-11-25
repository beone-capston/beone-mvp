#!/usr/bin/env bash
# deploy.yml gate 판정 표 시험. 워크플로의 gate 단계 스크립트를 그대로 꺼내 사례별 환경으로 실행한다.
# 사용: .github/scripts/deploy-gate-selftest.sh [워크플로 경로]
# 필요: bash, ruby(YAML 파싱). 네트워크·Docker·AWS를 쓰지 않는다.
set -euo pipefail

wf="${1:-$(cd "$(dirname "$0")/../.." && pwd)/.github/workflows/deploy.yml}"
work="$(mktemp -d)"
cleanup() {
	rm -f "$work/check.sh" "$work/dispatch.sh" "$work/out" "$work/summary" "$work/ifs"
	rmdir "$work" 2>/dev/null || true
}
trap cleanup EXIT

# 단계 스크립트와 job 조건을 파일로 꺼낸다
ruby -ryaml -e '
  wf = YAML.load_file(ARGV[0])
  steps = wf["jobs"]["gate"]["steps"]
  %w[check dispatch].each do |id|
    step = steps.find { |s| s["id"] == id } or abort("gate 단계 없음: #{id}")
    File.write(File.join(ARGV[1], "#{id}.sh"), step["run"])
    File.write(File.join(ARGV[1], "ifs"), "#{id}\t#{step["if"]}\n", mode: "a")
  end
  %w[build staging production].each do |job|
    File.write(File.join(ARGV[1], "ifs"), "#{job}\t#{wf["jobs"][job]["if"]}\n", mode: "a")
  end
  on = wf["on"] || wf[true]
  abort("workflow_dispatch 없음") unless on.key?("workflow_dispatch")
' "$wf" "$work"

fail=0
pass=0
ok() { pass=$((pass + 1)); }
bad() { echo "실패: $*" >&2; fail=$((fail + 1)); }

# 정적 확인: 단계·job 조건
cond() { awk -F'\t' -v k="$1" '$1 == k { print $2 }' "$work/ifs"; }
[ "$(cond check)" = "github.event_name == 'push'" ] && ok || bad "check 단계 조건: $(cond check)"
[ "$(cond dispatch)" = "github.event_name == 'workflow_dispatch'" ] && ok || bad "dispatch 단계 조건: $(cond dispatch)"
for job in staging production; do
	case "$(cond "$job")" in
		"github.event_name == 'push' && "*) ok ;;
		*) bad "$job job 조건에 push 조건이 앞에 없음: $(cond "$job")" ;;
	esac
done
[ "$(cond build)" = "needs.gate.outputs.build == 'true'" ] && ok || bad "build job 조건: $(cond build)"
grep -q 'vars\.' "$work/dispatch.sh" && bad "dispatch 단계가 저장소 변수를 읽음" || ok

ARN_S='arn:aws:iam::123456789012:role/BeoneDeployStaging'
ARN_P='arn:aws:iam::123456789012:role/BeoneDeployProduction'

# 사례: 이름 | 이벤트 | ref | CD_ENABLED | REGION | ROLE_STAGING | ROLE_PRODUCTION | PROD_ENABLED | 기대(build staging production)
# job 실행 기대는 실제 job 조건을 흉내 낸다: staging = push && develop && staging 출력, production = push && main && production 출력
run_case() {
	local name="$1" event="$2" ref="$3" cd="$4" region="$5" rs="$6" rp="$7" pe="$8" expect="$9"
	: > "$work/out"
	: > "$work/summary"
	local script
	if [ "$event" = push ]; then script="$work/check.sh"; else script="$work/dispatch.sh"; fi
	if ! env -i PATH="$PATH" GITHUB_REF="$ref" GITHUB_OUTPUT="$work/out" GITHUB_STEP_SUMMARY="$work/summary" \
		CD_ENABLED="$cd" REGION="$region" ROLE_STAGING="$rs" ROLE_PRODUCTION="$rp" PROD_ENABLED="$pe" \
		bash "$script" >/dev/null; then
		bad "$name: 단계 실행 실패"
		return
	fi
	local b s p
	b="$(sed -n 's/^build=//p' "$work/out")"
	s="$(sed -n 's/^staging=//p' "$work/out")"
	p="$(sed -n 's/^production=//p' "$work/out")"
	[ "$b $s $p" = "$expect" ] && ok || bad "$name: 출력 '$b $s $p', 기대 '$expect'"
	local run_s=false run_p=false
	[ "$event" = push ] && [ "$ref" = refs/heads/develop ] && [ "$s" = true ] && run_s=true
	[ "$event" = push ] && [ "$ref" = refs/heads/main ] && [ "$p" = true ] && run_p=true
	if [ "$event" = workflow_dispatch ]; then
		[ "$run_s $run_p" = "false false" ] && ok || bad "$name: 수동 실행에서 배포 job이 돎"
	fi
	[ -s "$work/summary" ] && ok || bad "$name: step summary 없음"
}

D=refs/heads/develop
M=refs/heads/main
F=refs/heads/feat/1-x
R=ap-northeast-2

# 수동 실행: 변수와 무관하게 develop만 빌드, 배포는 항상 false
run_case "dispatch develop 변수 없음" workflow_dispatch "$D" "" "" "" "" "" "true false false"
run_case "dispatch develop 변수 모두 켬" workflow_dispatch "$D" true "$R" "$ARN_S" "$ARN_P" true "true false false"
run_case "dispatch main" workflow_dispatch "$M" true "$R" "$ARN_S" "$ARN_P" true "false false false"
run_case "dispatch feature" workflow_dispatch "$F" true "$R" "$ARN_S" "$ARN_P" true "false false false"
run_case "dispatch 태그" workflow_dispatch refs/tags/v1 "" "" "" "" "" "false false false"

# push: 기존 판정(변경 전과 같아야 한다)
run_case "push develop 모두 설정" push "$D" true "$R" "$ARN_S" "" "" "true true false"
run_case "push develop CD 꺼짐" push "$D" false "$R" "$ARN_S" "$ARN_P" true "false false false"
run_case "push develop 리전 형식 오류" push "$D" true "Seoul" "$ARN_S" "" "" "false false false"
run_case "push develop 스테이징 역할 없음" push "$D" true "$R" "" "$ARN_P" true "false false false"
run_case "push develop 역할 형식 오류" push "$D" true "$R" "arn:aws:iam::12:role/x" "" "" "false false false"
run_case "push main 모두 설정" push "$M" true "$R" "" "$ARN_P" true "true false true"
run_case "push main 운영 켜짐 없음" push "$M" true "$R" "$ARN_S" "$ARN_P" "" "false false false"
run_case "push main 운영 역할 없음" push "$M" true "$R" "$ARN_S" "" true "false false false"
run_case "push feature 모두 설정" push "$F" true "$R" "$ARN_S" "$ARN_P" true "false false false"

if [ "$fail" -eq 0 ]; then
	echo "PASS ($pass)"
else
	echo "FAIL ($fail / $((pass + fail)))"
	exit 1
fi
