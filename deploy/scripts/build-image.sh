#!/usr/bin/env bash
# 백엔드 배포 이미지를 linux/amd64로 빌드하고 식별자(태그·이미지 ID·플랫폼·라벨)를 출력한다.
#
# 사용:
#   deploy/scripts/build-image.sh uncommitted [tar경로]   커밋 전 검증용 임시 이미지
#   deploy/scripts/build-image.sh final [tar경로]         커밋된 깨끗한 소스의 최종 이미지
#
# uncommitted: 태그 beone-api:verify-<기준12자리>-uncommitted-<UTC시각>, 라벨 beone.source.state=uncommitted.
#              소스 리비전 라벨(org.opencontainers.image.revision)은 붙이지 않는다.
# final:       작업 트리가 깨끗할 때만 빌드한다. 태그 beone-api:<커밋12자리>, 리비전 라벨에 전체 SHA.
# tar경로를 주면 docker save로 저장하고 sha256을 출력한다. 비밀값은 다루지 않는다.
set -euo pipefail

mode="${1:-}"
tar_path="${2:-}"
platform="linux/amd64"

die() { echo "build-image: $*" >&2; exit 1; }

repo="$(git rev-parse --show-toplevel)" || die "git 저장소 안에서 실행하세요"
head_sha="$(git -C "$repo" rev-parse HEAD)"
short_sha="$(printf '%s' "$head_sha" | cut -c1-12)"

case "$mode" in
	final)
		if [ -n "$(git -C "$repo" status --porcelain --untracked-files=all)" ]; then
			die "작업 트리가 깨끗하지 않습니다. final은 커밋 후에만 빌드합니다"
		fi
		tag="beone-api:${short_sha}"
		set -- --label "org.opencontainers.image.revision=${head_sha}" --label "beone.source.state=clean"
		;;
	uncommitted)
		tag="beone-api:verify-${short_sha}-uncommitted-$(date -u +%Y%m%dT%H%M%SZ)"
		set -- --label "beone.source.base=${head_sha}" --label "beone.source.state=uncommitted"
		;;
	*)
		die "첫 인자는 uncommitted 또는 final 입니다"
		;;
esac

if [ -n "$tar_path" ] && [ -e "$tar_path" ]; then
	die "이미 있는 파일에 덮어쓰지 않습니다: $tar_path"
fi

# 증명 메타데이터를 붙이지 않아 단일 플랫폼 이미지로 적재한다.
docker buildx build \
	--platform "$platform" \
	--provenance=false \
	--sbom=false \
	--load \
	"$@" \
	--tag "$tag" \
	"$repo/backend"

image_id="$(docker image inspect --format '{{.Id}}' "$tag")"
image_platform="$(docker image inspect --format '{{.Os}}/{{.Architecture}}' "$tag")"
[ "$image_platform" = "$platform" ] || die "플랫폼이 다릅니다: $image_platform"

echo "tag=$tag"
echo "image_id=$image_id"
echo "platform=$image_platform"
echo "labels=$(docker image inspect --format '{{json .Config.Labels}}' "$tag")"
echo "user=$(docker image inspect --format '{{.Config.User}}' "$tag")"

if [ -n "$tar_path" ]; then
	docker save --output "$tar_path" "$tag"
	if command -v sha256sum > /dev/null 2>&1; then
		tar_sha="$(sha256sum "$tar_path" | cut -d' ' -f1)"
	else
		tar_sha="$(shasum -a 256 "$tar_path" | cut -d' ' -f1)"
	fi
	echo "tar=$tar_path"
	echo "tar_sha256=$tar_sha"
	echo "tar_bytes=$(wc -c < "$tar_path" | tr -d ' ')"
fi
