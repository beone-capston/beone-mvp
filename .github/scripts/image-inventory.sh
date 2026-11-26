#!/usr/bin/env bash
# 수동 이미지 발행(deploy.yml workflow_dispatch)에서 푸시한 이미지의 내용 목록을 Markdown으로 출력한다.
# 사용: image-inventory.sh <api 이미지@sha256:…> <web 이미지@sha256:…> <git sha>
# 출력은 경로·크기·이름만이다. 파일 내용과 Env 값은 출력하지 않는다(Env는 '=' 뒤를 지운다).
# revision 라벨이 git sha와 다르면 실패한다(종료 1).
set -euo pipefail

api_ref="${1:?api 이미지 참조}"
web_ref="${2:?web 이미지 참조}"
sha="${3:?git sha}"
ref_re='^ghcr\.io/[a-z0-9._/-]+@sha256:[0-9a-f]{64}$'
[[ "$api_ref" =~ $ref_re ]] || { echo "api 참조 형식 오류" >&2; exit 2; }
[[ "$web_ref" =~ $ref_re ]] || { echo "web 참조 형식 오류" >&2; exit 2; }
[[ "$sha" =~ ^[0-9a-f]{40}$ ]] || { echo "git sha 형식 오류" >&2; exit 2; }

# 목록이 지나치게 길면 step summary 한도를 넘지 않게 자른다
max_lines=1000
work="$(mktemp -d)"
cids=()
cleanup() {
	for c in ${cids[@]+"${cids[@]}"}; do docker rm -f "$c" >/dev/null 2>&1 || true; done
	rm -f "$work/app.jar"
	rmdir "$work" 2>/dev/null || true
}
trap cleanup EXIT

limit() {
	awk -v max="$max_lines" 'NR <= max { print } END { if (NR > max) print "… (" NR - max "줄 생략)" }'
}

# tar 스트림에서 일반 파일의 경로와 크기만 출력한다(내용은 읽어 버린다). tar -tv 형식은 구현마다 달라 python으로 읽는다
tar_paths() {
	python3 -c 'import sys, tarfile
with tarfile.open(fileobj=sys.stdin.buffer, mode="r|") as t:
    for m in t:
        if m.isfile():
            print(f"{m.name}\t{m.size}")'
}

describe() {
	local ref="$1"
	docker image inspect "$ref" | jq -r '.[0] | "- 플랫폼: \(.Os)/\(.Architecture)"'
	echo "- 라벨:"
	echo '```text'
	docker image inspect "$ref" | jq -r '.[0].Config.Labels // {} | to_entries[] | "\(.key)=\(.value)"'
	echo '```'
	echo "- Env 이름(값 제외):"
	echo '```text'
	docker image inspect "$ref" | jq -r '.[0].Config.Env // [] | .[]' | sed 's/=.*//'
	echo '```'
}

check_revision() {
	local ref="$1" name="$2" rev
	rev="$(docker image inspect "$ref" | jq -r '.[0].Config.Labels["org.opencontainers.image.revision"] // ""')"
	if [ "$rev" != "$sha" ]; then
		echo "$name revision 라벨이 git sha와 다릅니다" >&2
		return 1
	fi
	echo "- revision 라벨: \`$rev\` (git sha와 일치)"
}

docker pull -q "$api_ref" >/dev/null
docker pull -q "$web_ref" >/dev/null

echo "## 이미지 내용 목록 (수동 발행)"
echo
echo "### api \`$api_ref\`"
echo "- 태그: \`$sha\`"
check_revision "$api_ref" api
describe "$api_ref"
api_cid="$(docker create "$api_ref")"
cids+=("$api_cid")
echo "- /app 파일(경로, 크기 바이트):"
echo '```text'
docker cp "$api_cid:/app" - | tar_paths | limit
echo '```'
docker cp "$api_cid:/app/app.jar" "$work/app.jar"
echo "- app.jar의 BOOT-INF/classes 리소스(.class 제외, 이름만):"
echo '```text'
unzip -Z1 "$work/app.jar" | grep '^BOOT-INF/classes/' | grep -v '/$' | grep -v '\.class$' | limit || true
echo '```'
echo
echo "### web \`$web_ref\`"
echo "- 태그: \`$sha\`"
check_revision "$web_ref" web
describe "$web_ref"
web_cid="$(docker create "$web_ref")"
cids+=("$web_cid")
echo "- /dist 파일(경로, 크기 바이트):"
echo '```text'
docker cp "$web_cid:/dist" - | tar_paths | limit
echo '```'
