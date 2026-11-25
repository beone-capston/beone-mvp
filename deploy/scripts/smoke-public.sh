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
#   P4 GET /api/v1/auth/csrf 200, Set-Cookie 한 줄에 __Host-BEONE_SESSION, Path=/, Secure, HttpOnly, SameSite=Lax, Domain 없음
#
# 업무 데이터를 바꾸지 않는다(P4 때문에 세션 저장소에 익명 세션 1행이 생길 수 있고, idle-timeout 뒤 만료된다). T-a·가입·로그인은 하지 않는다.
# 쿠키 값과 응답 본문은 출력하지 않는다.
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
cookies=$(header_of "$r" set-cookie | grep -i '__Host-BEONE_SESSION=' || true)
n=$(printf '%s' "$cookies" | grep -c . || true)
ok=0
if [ "$s" = 200 ] && [ "$n" = 1 ]; then
	# 속성은 대소문자를 구분하지 않고 세미콜론 단위로 본다
	attrs=$(printf '%s' "$cookies" | sed -E 's/^[^:]*:[[:space:]]*//' | tr ';' '\n' | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//')
	printf '%s\n' "$attrs" | grep -qx 'Path=/' \
		&& printf '%s\n' "$attrs" | grep -qix 'Secure' \
		&& printf '%s\n' "$attrs" | grep -qix 'HttpOnly' \
		&& printf '%s\n' "$attrs" | grep -qix 'SameSite=Lax' \
		&& ! printf '%s\n' "$attrs" | grep -qi '^Domain=' \
		&& ok=1
fi
# 값은 가리고 속성만 보여 준다
view=$(printf '%s' "$cookies" | sed -E 's/^[^:]*:[[:space:]]*//; s/=[^;]*;/=<값>;/' | head -n 1)
record P4 "GET /api/v1/auth/csrf 쿠키" "200, __Host-BEONE_SESSION 1줄, Path=/·Secure·HttpOnly·SameSite=Lax, Domain 없음" "$s ${view:-<없음>}" "$ok"

echo
if [ "$failures" -eq 0 ]; then
	echo "smoke-public PASS"
	exit 0
fi
echo "smoke-public FAIL ($failures)" >&2
exit 1
