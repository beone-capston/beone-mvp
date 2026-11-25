#!/usr/bin/env bash
# 배포 뒤 무변경 공개 점검(결정 40). 회원·업무 데이터를 만들지 않고 공개 경로의 응답 형태만 본다.
#
# 사용: deploy/scripts/smoke-public.sh <BASE_URL>      예: https://beonepay.site, http://127.0.0.1:18081
#   모든 항목이 기대대로면 종료 0, 하나라도 다르면 1, 인자가 틀리면 2.
#
# 점검 항목
#   P1 GET /                 200, Content-Type text/html
#   P2 GET /api/v1/me        401(쿠키 없음)
#   P3 GET /actuator/health  404(외부 비노출)
#   P4 GET /api/v1/auth/csrf 200, cookie-pair 이름이 정확히 __Host-BEONE_SESSION(대소문자 구분)이고 값이 비어 있지 않은 Set-Cookie 1줄,
#      그 쿠키의 속성에 Path=/, Secure, HttpOnly, SameSite=Lax, Domain 없음
#
# 업무 데이터를 바꾸지 않는다(P4 때문에 세션 저장소에 익명 세션 1행이 생길 수 있고, idle-timeout 뒤 만료된다). T-a·가입·로그인은 하지 않는다.
# 쿠키 값과 응답 본문은 출력하지 않는다. 쿠키는 판정과 무관하게 cookie-pair 값을 첫 ; 또는 줄 끝까지 가린 뒤 출력한다.
set -euo pipefail

BASE="${1:-}"
case "$BASE" in
	http://* | https://*) BASE="${BASE%/}" ;;
	*) echo "사용: $0 <http(s)://BASE_URL>" >&2; exit 2 ;;
esac

failures=0

# fetch <경로>: 헤더(CR 제거)와 마지막 줄의 상태 코드를 출력한다. 본문은 버린다. 연결 실패는 상태 000
fetch() {
	curl -sS --max-time 10 -o /dev/null -D - -w '\n%{http_code}' "$BASE$1" 2> /dev/null | tr -d '\r' || printf '\n000'
}
status_of() { printf '%s\n' "$1" | tail -n 1; }
header_of() { printf '%s\n' "$1" | sed '$d' | grep -i "^$2:" || true; }

COOKIE_NAME='__Host-BEONE_SESSION'

# mask_cookie: Set-Cookie 값 한 줄의 cookie-pair 값을 <값>으로 바꾼다. = 이 없으면 cookie-pair 전체를 가린다
mask_cookie() {
	awk '{
		i = index($0, ";"); pair = (i ? substr($0, 1, i - 1) : $0); rest = (i ? substr($0, i) : "")
		sub(/^[ \t]+/, "", pair); e = index(pair, "=")
		if ($0 == "") print ""; else if (e > 0) print substr(pair, 1, e - 1) "=<값>" rest; else print "<값>" rest
	}'
}

record() {
	local id="$1" what="$2" want="$3" got="$4" ok="$5"
	if [ "$ok" = 1 ]; then echo "| $id | $what | $want | $got | PASS |"; else echo "| $id | $what | $want | $got | FAIL |"; failures=$((failures + 1)); fi
}

echo "| ID | 항목 | 기대 | 실제 | 판정 |"
echo "|---|---|---|---|---|"

r=$(fetch /)
s=$(status_of "$r")
ct=$(header_of "$r" content-type | head -n 1 | sed -E 's/^[^:]*:[[:space:]]*//')
ok=0
[ "$s" = 200 ] && printf '%s' "$ct" | grep -qi '^text/html' && ok=1
record P1 "GET /" "200 text/html" "$s ${ct:-<없음>}" "$ok"

r=$(fetch /api/v1/me)
s=$(status_of "$r")
record P2 "GET /api/v1/me(쿠키 없음)" 401 "$s" "$([ "$s" = 401 ] && echo 1 || echo 0)"

r=$(fetch /actuator/health)
s=$(status_of "$r")
record P3 "GET /actuator/health" 404 "$s" "$([ "$s" = 404 ] && echo 1 || echo 0)"

r=$(fetch /api/v1/auth/csrf)
s=$(status_of "$r")
# Set-Cookie 값 부분(헤더 이름 제거). 줄마다 쿠키 하나
cookie_lines=$(header_of "$r" set-cookie | sed -E 's/^[^:]*:[[:space:]]*//')
# cookie-pair(첫 ; 앞)의 이름이 정확히 __Host-BEONE_SESSION(대소문자 구분)인 줄만 고른다. 부분 문자열·다른 쿠키 값 안의 일치는 제외
target=$(printf '%s\n' "$cookie_lines" | awk -v want="$COOKIE_NAME" '{
	i = index($0, ";"); pair = (i ? substr($0, 1, i - 1) : $0)
	sub(/^[ \t]+/, "", pair); e = index(pair, "=")
	if (e > 0 && substr(pair, 1, e - 1) == want) print
}')
n=$(printf '%s' "$target" | grep -c . || true)
ok=0
if [ "$s" = 200 ] && [ "$n" = 1 ]; then
	value=$(printf '%s\n' "$target" | awk '{ i = index($0, ";"); pair = (i ? substr($0, 1, i - 1) : $0); e = index(pair, "="); v = substr(pair, e + 1); gsub(/^[ \t]+|[ \t]+$/, "", v); print v }')
	# 그 쿠키 한 줄의 속성만 본다. 속성 이름은 대소문자를 구분하지 않는다
	attrs=$(printf '%s\n' "$target" | awk '{ i = index($0, ";"); print (i ? substr($0, i + 1) : "") }' | tr ';' '\n' | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//')
	[ -n "$value" ] \
		&& printf '%s\n' "$attrs" | grep -qx 'Path=/' \
		&& printf '%s\n' "$attrs" | grep -qix 'Secure' \
		&& printf '%s\n' "$attrs" | grep -qix 'HttpOnly' \
		&& printf '%s\n' "$attrs" | grep -qix 'SameSite=Lax' \
		&& ! printf '%s\n' "$attrs" | grep -qi '^Domain=' \
		&& ok=1
fi
# 출력용: 고른 쿠키(없으면 첫 Set-Cookie 줄)의 cookie-pair 값을 첫 ; 또는 줄 끝까지 가린다. 판정과 무관하게 항상 가린다
shown=$target
[ -n "$shown" ] || shown=$cookie_lines
view=$(printf '%s\n' "$shown" | sed -n 1p | mask_cookie)
[ "${n:-0}" -gt 1 ] && view="$view (외 $((n - 1))줄)"
record P4 "GET /api/v1/auth/csrf 쿠키" "200, 이름이 정확히 $COOKIE_NAME인 쿠키 1줄·값 있음, Path=/·Secure·HttpOnly·SameSite=Lax, Domain 없음" "$s ${view:-<없음>}" "$ok"

echo
if [ "$failures" -eq 0 ]; then
	echo "smoke-public PASS"
	exit 0
fi
echo "smoke-public FAIL ($failures)" >&2
exit 1
