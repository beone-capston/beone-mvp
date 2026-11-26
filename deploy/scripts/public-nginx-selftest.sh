#!/usr/bin/env bash
# 공개 nginx 보안 헤더·요청 제한 조각(deploy/public-nginx) 로컬 자기검사(T6).
#
# 로컬에 이미 있는 nginx 이미지로 컨테이너 하나를 띄워 nginx -t, 보안 헤더, 인증 링크 경로의 no-referrer, 429 경계와 응답 형식,
# 백엔드가 보낸 429의 통과를 확인한다. 루프백 포트만 쓰고 외부 네트워크·서버에는 접속하지 않는다. 이미지를 내려받지 않는다.
#
# 사용: deploy/scripts/public-nginx-selftest.sh [nginx 이미지]   (기본 nginx:1.28.0-alpine, 모든 확인이 맞으면 종료 0)
# 임시 폴더는 지우지 않고 끝에 경로를 출력한다. 컨테이너는 --rm으로 띄우고 끝날 때 멈춘다.
set -uo pipefail

REPO="$(cd "$(dirname "$0")/../.." && pwd)"
IMAGE="${1:-nginx:1.28.0-alpine}"
CONF="$REPO/deploy/public-nginx/conf.d"
TEST_CONF="$REPO/deploy/public-nginx/selftest/selftest.conf"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/public-nginx-selftest.XXXXXX")"
mkdir -p "$WORK/web" "$WORK/confd"
printf '<!doctype html>\n<title>selftest</title>\n' > "$WORK/web/index.html"
# conf.d 구성: 표준안 http 파일 + 조각 폴더 + 자기검사 server 블록. 이미지의 기본 default.conf는 쓰지 않는다
cp "$CONF/00-beone-security.conf" "$WORK/confd/"
cp -R "$CONF/beone" "$WORK/confd/beone"
cp "$TEST_CONF" "$WORK/confd/default.conf"

docker image inspect "$IMAGE" > /dev/null 2>&1 || { echo "이미지 없음: $IMAGE (내려받지 않는다)" >&2; echo "임시 폴더: $WORK"; exit 2; }
MOUNTS=(-v "$WORK/confd:/etc/nginx/conf.d:ro" -v "$WORK/web:/srv/web:ro")

failures=0
check() { # check <설명> <조건 결과(0이면 통과)>
	if [ "$2" = 0 ]; then echo "PASS $1"; else echo "FAIL $1"; failures=$((failures + 1)); fi
}

out=$(docker run --rm "${MOUNTS[@]}" "$IMAGE" nginx -t 2>&1); rc=$?
check "nginx -t" "$rc"
[ "$rc" = 0 ] || { printf '%s\n' "$out" >&2; echo "임시 폴더: $WORK"; exit 1; }

cid=$(docker run -d --rm "${MOUNTS[@]}" -p 127.0.0.1::8080 -p 127.0.0.1::8082 "$IMAGE") || { echo "컨테이너 시작 실패" >&2; exit 1; }
trap 'docker stop "$cid" > /dev/null 2>&1' EXIT
P=$(docker port "$cid" 8080/tcp | head -n 1 | sed 's/.*://')
S=$(docker port "$cid" 8082/tcp | head -n 1 | sed 's/.*://')
for _ in $(seq 50); do curl -s -o /dev/null "http://127.0.0.1:$P/" && break; sleep 0.1; done

H="$WORK/headers"
head_of() { curl -s -D "$H" -o "$WORK/body" "$@" > /dev/null; }
count() { grep -ci "^$1:" "$H"; }
value() { grep -i "^$1:" "$H" | head -n 1 | sed 's/^[^:]*: *//' | tr -d '\r'; }

# 1. 운영 모양: 화면
head_of "http://127.0.0.1:$P/"
check "운영 / HSTS 1년" "$([ "$(value Strict-Transport-Security)" = 'max-age=31536000' ]; echo $?)"
check "운영 / nosniff" "$([ "$(value X-Content-Type-Options)" = nosniff ]; echo $?)"
check "운영 / X-Frame-Options SAMEORIGIN" "$([ "$(value X-Frame-Options)" = SAMEORIGIN ]; echo $?)"
check "운영 / CSP frame-ancestors만" "$([ "$(value Content-Security-Policy)" = "frame-ancestors 'self'" ]; echo $?)"
check "운영 / Referrer-Policy 1개 strict-origin-when-cross-origin" "$([ "$(count Referrer-Policy)" = 1 ] && [ "$(value Referrer-Policy)" = strict-origin-when-cross-origin ]; echo $?)"

# 2. 인증 링크 경로: no-referrer만, 보안 헤더 유지(add_header 상속 끊김 방지)
for path in /email-verification /password-reset; do
	head_of "http://127.0.0.1:$P$path?token=x"
	check "운영 $path Referrer-Policy no-referrer 1개" "$([ "$(count Referrer-Policy)" = 1 ] && [ "$(value Referrer-Policy)" = no-referrer ]; echo $?)"
	check "운영 $path HSTS·nosniff 유지" "$([ "$(count Strict-Transport-Security)" = 1 ] && [ "$(count X-Content-Type-Options)" = 1 ]; echo $?)"
done

# 3. 스테이징 모양: 업스트림의 no-referrer를 덮지 않고 중복도 없음
head_of "http://127.0.0.1:$S/email-verification?token=x"
check "스테이징 /email-verification 업스트림 no-referrer 1개" "$([ "$(count Referrer-Policy)" = 1 ] && [ "$(value Referrer-Policy)" = no-referrer ]; echo $?)"
check "스테이징 /email-verification HSTS·X-Robots-Tag" "$([ "$(count Strict-Transport-Security)" = 1 ] && [ "$(count X-Robots-Tag)" = 1 ]; echo $?)"
head_of "http://127.0.0.1:$S/"
check "스테이징 / Referrer-Policy 기본값 1개" "$([ "$(count Referrer-Policy)" = 1 ] && [ "$(value Referrer-Policy)" = strict-origin-when-cross-origin ]; echo $?)"

# 4. 인증 경로 10r/m burst 5: 즉시 6회 통과, 7번째 429
codes=""
for _ in $(seq 7); do codes="$codes $(curl -s -o /dev/null -w '%{http_code}' -X POST "http://127.0.0.1:$P/api/v1/auth/login")"; done
check "인증 경로 연속 7회: 200×6 뒤 429 (받은 값:$codes)" "$([ "$codes" = ' 200 200 200 200 200 200 429' ]; echo $?)"
head_of -X POST "http://127.0.0.1:$P/api/v1/auth/sign-up"
check "인증 429 본문이 공통 오류 형식" "$(grep -qx '{"code":"RATE_LIMITED","message":"요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.","errors":\[\]}' "$WORK/body"; echo $?)"
check "인증 429 Content-Type JSON·Retry-After 6·no-store·HSTS" "$(value Content-Type | grep -q '^application/json' && [ "$(value Retry-After)" = 6 ] && [ "$(value Cache-Control)" = no-store ] && [ "$(count Strict-Transport-Security)" = 1 ]; echo $?)"
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "http://127.0.0.1:$S/api/v1/auth/password-reset-requests")
check "스테이징 인증 경로도 같은 영역(같은 IP는 이미 초과, 받은 값 $code)" "$([ "$code" = 429 ]; echo $?)"
code=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$P/api/v1/auth/csrf")
check "CSRF 조회는 인증 제한 밖(받은 값 $code)" "$([ "$code" = 200 ]; echo $?)"

# 5. 일반 /api 10r/s burst 20: 연속 요청에서 21회 안팎 통과 뒤 429
sleep 3
ok=0 limited=0
for _ in $(seq 40); do
	c=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$P/api/v1/me")
	case "$c" in 200) ok=$((ok + 1)) ;; 429) limited=$((limited + 1)) ;; esac
done
check "일반 /api 연속 40회: 통과 21~30, 429 있음 (통과 $ok, 429 $limited)" "$([ "$ok" -ge 21 ] && [ "$ok" -le 30 ] && [ "$limited" -ge 1 ]; echo $?)"
head_of "http://127.0.0.1:$P/api/v1/me"
check "일반 429 Retry-After 1" "$([ "$(value Retry-After)" = 1 ]; echo $?)"

# 6. 백엔드가 보낸 429는 nginx 응답으로 바꾸지 않는다
sleep 3
head_of "http://127.0.0.1:$P/api/v1/backend-429"
check "백엔드 429 통과(Retry-After 30, 백엔드 본문)" "$([ "$(value Retry-After)" = 30 ] && grep -q '"from":"backend"' "$WORK/body"; echo $?)"

echo "임시 폴더: $WORK"
if [ "$failures" = 0 ]; then echo "public nginx self-test PASS"; exit 0; fi
echo "public nginx self-test FAIL ($failures)"
exit 1
