#!/usr/bin/env bash
# member 프로필 회원 API 스모크(합성 회원 2명). 대상은 같은 origin으로 웹과 /api를 함께 내는 주소다.
#
# 사용:
#   deploy/scripts/smoke-member.sh <BASE_URL>            예: http://localhost:18080
#   deploy/scripts/smoke-member.sh --self-test           서버 없이 판정 함수를 점검(필드 읽기, 가짜 응답 주입 시 FAIL·종료 1)
#
# 이메일 인증 단계: member 프로필의 가짜 메일 발송기는 토큰을 메모리에만 두므로 화면·API로 인증을 끝낼 수 없다.
#   검증 DB에서 인증 완료 상태를 만든 뒤 계속한다(결정 39-1 T-a, 검증 DB·합성 회원 한정).
#   - SMOKE_VERIFY_HOOK이 있으면 이메일마다 "$SMOKE_VERIFY_HOOK <email>"을 실행한다(성공 시 종료 코드 0).
#   - 없으면 실행할 SQL을 안내하고 Enter를 기다린다.
#
# 환경 변수(선택): SMOKE_EMAIL_A, SMOKE_EMAIL_B(기본 실행마다 다른 *@example.test), SMOKE_PASSWORD(합성 비밀번호),
#   SMOKE_MONTH(기본: 서울 기준 이번 달 YYYY-MM). 주문일은 서버가 '이번 달'만 받으므로(InCurrentMonth) 이번 달만 지원한다.
#   다른 월이나 형식이 틀린 값은 요청을 보내기 전에 종료 코드 2로 멈춘다. 주문일은 서울 기준 오늘, 월간 상태의
#   전월 실적 기간은 MONTH의 전월 1일~말일로 함께 유도한다.
# 출력: PASS·FAIL 표. 실패가 하나라도 있으면 종료 코드 1. 쿠키 값·CSRF 토큰·비밀번호는 출력하지 않는다.
set -uo pipefail

BASE="${1:-}"
if [ -z "$BASE" ]; then
	echo "사용: $0 <BASE_URL>" >&2
	exit 2
fi
BASE="${BASE%/}"
command -v curl > /dev/null || { echo "curl이 필요합니다" >&2; exit 2; }
command -v jq > /dev/null || { echo "jq가 필요합니다" >&2; exit 2; }

RUN_ID="$(date +%s)"
EMAIL_A="${SMOKE_EMAIL_A:-smoke-a-${RUN_ID}@example.test}"
EMAIL_B="${SMOKE_EMAIL_B:-smoke-b-${RUN_ID}@example.test}"
PASSWORD="${SMOKE_PASSWORD:-Synthetic-pw-2026!}"
TODAY="$(TZ=Asia/Seoul date +%Y-%m-%d)"
MONTH="${SMOKE_MONTH:-${TODAY:0:7}}"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
RESULTS="$WORK/results"
: > "$RESULTS"

# 요청 1회. 응답 본문은 $WORK/body, 헤더는 $WORK/head에 둔다. 상태 코드를 출력한다.
# req <쿠키 이름|-> <메서드> <경로> [본문] [헤더...]
req() {
	local jar="$1" method="$2" path="$3" body="${4:-}"
	shift 3
	[ $# -gt 0 ] && shift
	local args=(-s -o "$WORK/body" -D "$WORK/head" -w '%{http_code}' -X "$method" --max-time 30)
	[ "$jar" != "-" ] && args+=(-c "$WORK/$jar.jar" -b "$WORK/$jar.jar")
	[ -n "$body" ] && args+=(-H 'Content-Type: application/json' --data "$body")
	local h
	for h in "$@"; do args+=(-H "$h"); done
	curl "${args[@]}" "$BASE$path" || echo 000
}
code() { field '.code'; }
# null·없음만 빈 값으로 바꾼다. jq의 '// empty'는 false·0까지 지우므로 쓰지 않는다
field() { jq -r "$1 | if . == null then empty else . end" "$WORK/body" 2> /dev/null; }
header() { grep -i "^$1:" "$WORK/head" | tr -d '\r'; }
# 쿠키 값은 지우고 속성만 남긴다. 줄마다 첫 '=' 뒤부터 ';' 또는 줄 끝까지를 가린다(세미콜론 없는 값도 가림)
cookie_attrs() { header set-cookie | sed -E 's/^[^:]*: *//; s/^([^=;]*)=[^;]*/\1=<값>/'; }
# 판정용 원문 Set-Cookie 줄(헤더 이름만 뗀다). 값이 들어 있으므로 판정에만 쓰고 출력·기록하지 않는다
cookie_raw() { header set-cookie | sed -E 's/^[^:]*: *//'; }
csrf() {
	req "$1" GET /api/v1/auth/csrf > /dev/null
	field '.data.token'
}
# record <ID> <설명> <기대> <실제> <통과 여부 1|0>
record() {
	local verdict=FAIL
	[ "$5" = 1 ] && verdict=PASS
	printf '| %s | %s | %s | %s | %s |\n' "$1" "$2" "$3" "$4" "$verdict" >> "$RESULTS"
}
same() { [ "$1" = "$2" ] && echo 1 || echo 0; }

verify_email() {
	local email="$1"
	if [ -n "${SMOKE_VERIFY_HOOK:-}" ]; then
		$SMOKE_VERIFY_HOOK "$email" > /dev/null 2>&1
		return $?
	fi
	cat >&2 << EOF

[인증 완료 대기] 검증 DB에서 아래 SQL을 실행한 뒤 Enter를 누르세요(합성 회원 $email 한정).
  START TRANSACTION;
  UPDATE members SET email_verified_at = NOW() WHERE email = '$email' AND email_verified_at IS NULL;
  INSERT INTO users (demo_user, member_id, display_name, portfolio_type, read_only)
    SELECT NULL, m.member_id, '내 카드', 'REAL', FALSE FROM members m
    WHERE m.email = '$email' AND NOT EXISTS (SELECT 1 FROM users u WHERE u.member_id = m.member_id);
  COMMIT;
EOF
	read -r _ < /dev/tty
}

# 가입 → 인증 전 로그인 403 → 인증 완료 → 로그인
member_ready() {
	local jar="$1" email="$2" p="$3" s t
	local login="{\"email\":\"$email\",\"password\":\"$PASSWORD\"}"
	t=$(csrf "$jar")
	s=$(req "$jar" POST /api/v1/auth/sign-up "$login" "X-CSRF-TOKEN: $t")
	record "${p}1" "가입(합성 회원 $p)" 202 "$s" "$(same "$s" 202)"
	t=$(csrf "$jar")
	s=$(req "$jar" POST /api/v1/auth/login "$login" "X-CSRF-TOKEN: $t")
	record "${p}2" "인증 전 로그인" "403 EMAIL_NOT_VERIFIED" "$s $(code)" "$(same "$s $(code)" '403 EMAIL_NOT_VERIFIED')"
	if verify_email "$email"; then
		record "${p}3" "인증 완료 상태 준비(S7)" "완료" "완료" 1
	else
		record "${p}3" "인증 완료 상태 준비(S7)" "완료" "훅 실패" 0
	fi
	step_login "$jar" "$p" "$login"
}

# 로그인 응답의 세션 쿠키 속성까지 판정한다
step_login() {
	local jar="$1" p="$2" login="$3" s t
	t=$(csrf "$jar")
	s=$(req "$jar" POST /api/v1/auth/login "$login" "X-CSRF-TOKEN: $t")
	record "${p}4" "로그인" "200, __Host-BEONE_SESSION(값 있음, Path=/, Secure, HttpOnly, SameSite=Lax, Domain 없음)" "$s; $(cookie_attrs)" \
		"$(check_session_cookie "$s" "$(cookie_raw)")"
}

# check_session_cookie <상태> <원문 Set-Cookie 줄들(cookie_raw)> → 1|0. 마스킹 전 원문으로 판정한다.
# cookie-pair(첫 ';' 앞)의 이름이 정확히 __Host-BEONE_SESSION이고 값이 비어 있지 않은 줄 하나에서, 정확한 경계로
# Path=/, Secure, HttpOnly, SameSite=Lax가 모두 있고 Domain이 없어야 한다. 다른 쿠키 줄의 속성은 합치지 않는다.
# 값은 출력하지 않는다(결과 표에는 cookie_attrs의 마스킹 결과만 쓴다)
check_session_cookie() {
	[ "$1" = 200 ] || { echo 0; return; }
	local line pair value
	while IFS= read -r line; do
		pair="${line%%;*}"
		case "$pair" in __Host-BEONE_SESSION=*) ;; *) continue ;; esac
		value="${pair#__Host-BEONE_SESSION=}"
		value="${value//[[:space:]]/}"
		[ -n "$value" ] || continue
		if echo "$line" | grep -qE '; *Path=/ *(;|$)' && echo "$line" | grep -qiE '; *Secure *(;|$)' \
			&& echo "$line" | grep -qiE '; *HttpOnly *(;|$)' && echo "$line" | grep -qiE '; *SameSite=Lax *(;|$)' \
			&& ! echo "$line" | grep -qiE '; *Domain='; then
			echo 1
			return
		fi
	done <<< "$2"
	echo 0
}

# 월 유도. month_dates <YYYY-MM> → "<전월 1일> <전월 말일>"(달력 계산, date 명령의 GNU·BSD 차이를 피한다)
month_dates() {
	local y=$((10#${1:0:4})) m=$((10#${1:5:2})) py pm last
	if [ "$m" = 1 ]; then py=$((y - 1)); pm=12; else py=$y; pm=$((m - 1)); fi
	case "$pm" in
		1|3|5|7|8|10|12) last=31 ;;
		4|6|9|11) last=30 ;;
		2) if { [ $((py % 4)) = 0 ] && [ $((py % 100)) != 0 ]; } || [ $((py % 400)) = 0 ]; then last=29; else last=28; fi ;;
	esac
	printf '%04d-%02d-01 %04d-%02d-%02d\n' "$py" "$pm" "$py" "$pm" "$last"
}
# check_month <MONTH> <TODAY> → 0이면 지원, 아니면 이유를 표준 오류로 알리고 1
check_month() {
	if ! [[ "$1" =~ ^[0-9]{4}-(0[1-9]|1[0-2])$ ]]; then echo "SMOKE_MONTH 형식이 틀립니다(YYYY-MM): $1" >&2; return 1; fi
	if [ "$1" != "${2:0:7}" ]; then
		echo "SMOKE_MONTH=$1 은 지원하지 않습니다. 주문일은 서버의 이번 달(서울 ${2:0:7})만 받으므로 이번 달만 실행합니다" >&2
		return 1
	fi
}

# check_html <상태> <Content-Type> → 1|0
check_html() {
	[ "$1" = 200 ] && echo "$2" | grep -qiE '^text/html(;|$)' && echo 1 || echo 0
}

# check_changed_null → 1|0. 응답 본문의 recalculation에 changed 필드가 있고 값이 JSON null일 때만 1(누락은 0)
check_changed_null() {
	jq -e '.data.recalculation | type == "object" and has("changed") and .changed == null' "$WORK/body" > /dev/null 2>&1 \
		&& echo 1 || echo 0
}
changed_view() {
	jq -r '.data.recalculation | if type != "object" or (has("changed") | not) then "<없음>" else (.changed | tojson) end' \
		"$WORK/body" 2> /dev/null || echo '<해석 불가>'
}

step_s1() {
	local s ct
	s=$(req - GET /)
	ct=$(header content-type | sed -E 's/^[^:]*: *//')
	record S1 "GET /" "200 text/html" "$s $ct" "$(check_html "$s" "$ct")"
}

# step_s11b <상태>: 승인 응답(본문은 $WORK/body)의 상태와 changed=null을 판정한다
step_s11b() {
	local s="$1" ok=0
	[ "$s $(field '.data.status')" = '201 APPROVED' ] && [ "$(check_changed_null)" = 1 ] && ok=1
	record S11b "A 승인(본문 {})" "201 APPROVED, changed 필드 있음·null" "$s $(field '.data.status') changed=$(changed_view)" "$ok"
}

# /me 응답(본문은 $WORK/body)의 필수 필드가 모두 있고 타입이 맞는지 본다. 필드 누락을 null과 구분한다.
# demoUser는 키가 있고 값이 null, 식별 필드는 키가 있고 기대 타입이어야 1을 출력한다
check_me_shape() {
	jq -e '.data | type == "object"
		and has("demoUser") and .demoUser == null
		and has("displayName") and (.displayName | type) == "string"
		and has("portfolioType") and (.portfolioType | type) == "string"
		and has("readOnly") and (.readOnly | type) == "boolean"
		and has("cardCount") and (.cardCount | type) == "number"
		and has("codefConnected") and (.codefConnected | type) == "boolean"' "$WORK/body" > /dev/null 2>&1 && echo 1 || echo 0
}

# 시연 헤더를 붙여도 헤더 없는 A /me와 같은 장부인지 본다. 기준·비교 응답 각각의 필수 필드 형태를 먼저 확인한 뒤
# 본문 전체(비밀값 없음)를 비교한다. 양쪽에서 같은 필드가 빠져 둘 다 null처럼 보여도 통과하지 않는다
step_s16a() {
	local s1 s2 base forged base_shape forged_shape ok=0
	s1=$(req a GET /api/v1/me)
	base_shape=$(check_me_shape)
	base=$(jq -cS '.data' "$WORK/body" 2> /dev/null)
	s2=$(req a GET /api/v1/me '' 'X-BeONE-Demo-User: VIRTUAL_PORTFOLIO')
	forged_shape=$(check_me_shape)
	forged=$(jq -cS '.data' "$WORK/body" 2> /dev/null)
	[ "$s1 $s2 $base_shape $forged_shape" = '200 200 1 1' ] && [ "$forged" = "$base" ] && ok=1
	record S16a "쿠키 + 시연 헤더" "200·200, 양쪽 필수 필드 있음·타입 일치(demoUser 있음·null), 같은 장부" \
		"$s1/$s2 shape=$base_shape/$forged_shape same=$(same "$forged" "$base")" "$ok"
}

# S5 익명 세션 쿠키. 세션 쿠키 줄 하나에서 속성을 정확한 경계로 본다
step_s5() {
	local s attrs
	s=$(req anon GET /api/v1/auth/csrf)
	attrs=$(cookie_attrs)
	record S5 "익명 세션 쿠키 속성" "200, __Host-BEONE_SESSION 한 줄에 값 있음, Path=/, Secure, HttpOnly, SameSite=Lax, Domain 없음" \
		"$s; $attrs" "$(check_session_cookie "$s" "$(cookie_raw)")"
}

# S17 전달 헤더 위조. 양쪽 모두 200과 유효한 세션 쿠키를 먼저 요구한 뒤 속성(값은 가림) 동일·Location 없음을 본다
step_s17() {
	local s1 s2 plain forged ok1 ok2 loc1 loc2 ok=0
	s1=$(req f1 GET /api/v1/auth/csrf)
	plain=$(cookie_attrs)
	ok1=$(check_session_cookie "$s1" "$(cookie_raw)")
	loc1=$(header location | wc -l | tr -d ' ')
	s2=$(req f2 GET /api/v1/auth/csrf '' 'X-Forwarded-Host: evil.invalid' 'X-Forwarded-Proto: http' 'X-Forwarded-For: 192.0.2.1')
	forged=$(cookie_attrs)
	ok2=$(check_session_cookie "$s2" "$(cookie_raw)")
	loc2=$(header location | wc -l | tr -d ' ')
	[ "$ok1 $ok2" = '1 1' ] && [ "$plain" = "$forged" ] && [ "$loc1 $loc2" = '0 0' ] && ok=1
	record S17 "X-Forwarded-* 위조" "양쪽 200·유효 세션 쿠키, Set-Cookie 속성 동일, Location 없음" \
		"$s1/$s2 cookie=$ok1/$ok2 same=$(same "$plain" "$forged") location=$loc1/$loc2" "$ok"
}

# 결과 표를 출력하고 실패가 있으면 1을 돌려준다
summarize() {
	local pass fail
	printf '| ID | 항목 | 기대 | 실제 | 판정 |\n|---|---|---|---|---|\n'
	cat "$RESULTS"
	pass=$(grep -c '| PASS |$' "$RESULTS")
	fail=$(grep -c '| FAIL |$' "$RESULTS")
	echo
	echo "PASS=$pass FAIL=$fail"
	[ "$fail" -eq 0 ]
}

# 오프라인 자기검사. req를 가짜 응답을 쓰는 함수로 바꿔 각 판정 단계를 실제 경로대로 돌리고,
# 결과 표 판정(summarize)의 종료 코드가 정상 응답은 0, 주입한 잘못된 응답은 1인지 확인한다. 서버를 부르지 않는다.
self_test() {
	local failures=0 got
	printf '{"code":"X","data":{"f":false,"z":0,"t":true,"n":null,"s":"v"}}' > "$WORK/body"
	got="$(field '.data.f'),$(field '.data.z'),$(field '.data.t'),[$(field '.data.n')],[$(field '.data.missing')],$(field '.data.s'),$(code)"
	if [ "$got" = "false,0,true,[],[],v,X" ]; then echo "field PASS ($got)"; else echo "field FAIL ($got)"; failures=$((failures + 1)); fi

	# 쿠키 값 마스킹: 세미콜론 없는 값·있는 값 모두 합성 표식이 출력에 남지 않고 속성은 보존되는지 본다
	local mark='SYNTHMARK-7f3a' masked
	printf 'HTTP/1.1 200\r\nset-cookie: __Host-BEONE_SESSION=%s\r\nset-cookie: other=%s; Path=/; Secure\r\n' "$mark" "$mark" > "$WORK/head"
	masked=$(cookie_attrs)
	if [ "$masked" = $'__Host-BEONE_SESSION=<값>\nother=<값>; Path=/; Secure' ] && ! grep -q "$mark" <<< "$masked"; then
		echo "cookie_attrs 마스킹 PASS (세미콜론 없는 값 포함, 표식 비노출)"
	else
		echo "cookie_attrs 마스킹 FAIL"; failures=$((failures + 1))
	fi

	# 월 유도와 지원 밖 월 사전 거부
	got="$(month_dates 2026-11)|$(month_dates 2027-01)|$(month_dates 2028-03)|$(month_dates 2026-03)"
	if [ "$got" = "2026-10-01 2026-10-31|2026-12-01 2026-12-31|2028-02-01 2028-02-29|2026-02-01 2026-02-28" ]; then
		echo "month_dates PASS ($got)"
	else
		echo "month_dates FAIL ($got)"; failures=$((failures + 1))
	fi
	local mc
	for mc in "2026-11 2026-11-25 0" "2026-10 2026-11-25 1" "2026-12 2026-11-25 1" "2026-13 2026-11-25 1" "2026-1 2026-11-25 1"; do
		set -- $mc
		check_month "$1" "$2" 2> /dev/null; rc=$?
		if [ "$rc" = "$3" ]; then echo "check_month $1(오늘 $2) 기대 $3 → $rc PASS"; else echo "check_month $1 기대 $3 → $rc FAIL"; failures=$((failures + 1)); fi
	done
	# 실제 실행 경로: 지원 밖 월이면 요청 전에 종료 2. 접속할 수 없는 주소를 주어 요청이 나가면 다른 결과가 되게 한다
	local this_month other_out
	this_month="$(TZ=Asia/Seoul date +%Y-%m)"
	other_out=$(SMOKE_MONTH=1999-01 SMOKE_VERIFY_HOOK=/bin/false bash "$0" http://127.0.0.1:9 2>&1); rc=$?
	if [ "$rc" = 2 ] && grep -q '지원하지 않습니다' <<< "$other_out" && ! grep -q '| S1 |' <<< "$other_out"; then
		echo "다른 월(SMOKE_MONTH=1999-01, 이번 달 $this_month) 사전 거부 PASS(종료 2, 요청 없음)"
	else
		echo "다른 월 사전 거부 FAIL(종료 $rc)"; failures=$((failures + 1))
	fi

	FIX_STATUS=200 FIX_HEAD='' FIX_BODY='' FIX_BODY2='' FIX_STATUS2='' FIX_HEAD2=''
	req() {
		local h st="$FIX_STATUS" hd="$FIX_HEAD"
		printf '%s' "$FIX_BODY" > "$WORK/body"
		for h in "$@"; do
			case "$h" in
				X-BeONE-Demo-User:*) printf '%s' "$FIX_BODY2" > "$WORK/body" ;;
				X-Forwarded-Host:*) [ -n "$FIX_STATUS2" ] && st="$FIX_STATUS2"; hd="$FIX_HEAD2" ;;
			esac
		done
		printf 'HTTP/1.1 %s\r\n%s' "$st" "$hd" > "$WORK/head"
		echo "$st"
	}
	csrf() { echo synthetic-csrf; }
	local good_cookie='set-cookie: __Host-BEONE_SESSION=synthetic; Path=/; Max-Age=2592000; Secure; HttpOnly; SameSite=Lax\r\n'
	local empty_cookie='set-cookie: __Host-BEONE_SESSION=; Path=/; Max-Age=2592000; Secure; HttpOnly; SameSite=Lax\r\n'
	local blank_cookie='set-cookie: __Host-BEONE_SESSION= ; Path=/; Secure; HttpOnly; SameSite=Lax\r\n'
	local good_cookie2='set-cookie: __Host-BEONE_SESSION=other-synthetic; Path=/; Max-Age=2592000; Secure; HttpOnly; SameSite=Lax\r\n'
	local me_a='{"data":{"demoUser":null,"displayName":"내 카드","portfolioType":"REAL","readOnly":false,"cardCount":1,"codefConnected":false}}'
	local me_v='{"data":{"demoUser":"VIRTUAL_PORTFOLIO","displayName":"가상 포트폴리오","portfolioType":"VIRTUAL","readOnly":true,"cardCount":3,"codefConnected":false}}'
	local me_no_demo='{"data":{"displayName":"내 카드","portfolioType":"REAL","readOnly":false,"cardCount":1,"codefConnected":false}}'
	local me_no_id='{"data":{"demoUser":null,"readOnly":false,"cardCount":1,"codefConnected":false}}'
	local me_bad_type='{"data":{"demoUser":null,"displayName":"내 카드","portfolioType":"REAL","readOnly":false,"cardCount":"1","codefConnected":false}}'
	local approved='{"data":{"status":"APPROVED","recalculation":{"recommendationId":"rec-1","seenRecommendationId":null,"changed":null}}}'
	# 사례: 이름 | 기대 종료 코드 | 상태 | 헤더 | 본문 | 시연 헤더 본문 | 단계 | 위조 요청 상태 | 위조 요청 헤더
	local cases=(
		"S5 정상|0|200|$good_cookie|||step_s5"
		"S5 빈 세션 값(속성 정상)|1|200|$empty_cookie|||step_s5"
		"S5 공백뿐인 세션 값|1|200|$blank_cookie|||step_s5"
		"S5 값 없는 쿠키 줄 + 다른 쿠키에 값|1|200|${empty_cookie}set-cookie: other=__Host-BEONE_SESSION=x; Path=/; Secure; HttpOnly; SameSite=Lax\r\n|||step_s5"
		"S17 기준 쪽 빈 세션 값|1|200|$empty_cookie|||step_s17|200|$good_cookie2"
		"S17 위조 쪽 빈 세션 값|1|200|$good_cookie|||step_s17|200|$empty_cookie"
		"S17 양쪽 빈 세션 값(속성 같음)|1|200|$empty_cookie|||step_s17|200|$empty_cookie"
		"S5 Path=/api|1|200|set-cookie: __Host-BEONE_SESSION=synthetic; Path=/api; Secure; HttpOnly; SameSite=Lax\r\n|||step_s5"
		"S5 여러 줄에 속성 분산|1|200|set-cookie: __Host-BEONE_SESSION=synthetic; Path=/api; Secure\r\nset-cookie: other=x; Path=/; HttpOnly; SameSite=Lax\r\n|||step_s5"
		"S5 SameSite 누락|1|200|set-cookie: __Host-BEONE_SESSION=synthetic; Path=/; Secure; HttpOnly\r\n|||step_s5"
		"S5 SameSite=Strict|1|200|set-cookie: __Host-BEONE_SESSION=synthetic; Path=/; Secure; HttpOnly; SameSite=Strict\r\n|||step_s5"
		"S5 Domain 있음|1|200|set-cookie: __Host-BEONE_SESSION=synthetic; Path=/; Domain=beonepay.site; Secure; HttpOnly; SameSite=Lax\r\n|||step_s5"
		"S5 세미콜론 없는 쿠키만|1|200|set-cookie: __Host-BEONE_SESSION=synthetic\r\n|||step_s5"
		"S17 정상(값만 다름)|0|200|$good_cookie|||step_s17|200|$good_cookie2"
		"S17 양쪽 500·쿠키 없음|1|500||||step_s17|500|"
		"S17 위조 쪽만 500|1|200|$good_cookie|||step_s17|500|$good_cookie2"
		"S17 위조 쪽 쿠키 누락|1|200|$good_cookie|||step_s17|200|"
		"S17 양쪽 쿠키 누락(같은 빈 값)|1|200||||step_s17|200|"
		"S17 위조 쪽 속성 다름|1|200|$good_cookie|||step_s17|200|set-cookie: __Host-BEONE_SESSION=x; Path=/; Secure; HttpOnly; SameSite=None\r\n"
		"S17 위조 쪽 Location 있음|1|200|$good_cookie|||step_s17|200|${good_cookie2}location: https://evil.invalid/\r\n"
		"S1 정상|0|200|content-type: text/html;charset=UTF-8\r\n|||step_s1"
		"S1 Content-Type 잘못됨|1|200|content-type: application/json\r\n|||step_s1"
		"로그인 쿠키 정상|0|200|$good_cookie|||login"
		"로그인 쿠키 빈 세션 값(속성 정상)|1|200|$empty_cookie|||login"
		"로그인 쿠키 Secure 누락|1|200|set-cookie: __Host-BEONE_SESSION=synthetic; Path=/; HttpOnly; SameSite=Lax\r\n|||login"
		"로그인 쿠키 HttpOnly 누락|1|200|set-cookie: __Host-BEONE_SESSION=synthetic; Path=/; Secure; SameSite=Lax\r\n|||login"
		"로그인 쿠키 Path 다름|1|200|set-cookie: __Host-BEONE_SESSION=synthetic; Path=/api; Secure; HttpOnly\r\n|||login"
		"로그인 쿠키 이름 다름|1|200|set-cookie: BEONE_SESSION=synthetic; Path=/; Secure; HttpOnly\r\n|||login"
		"로그인 쿠키 없음|1|200||||login"
		"S11b changed null|0|201||$approved||s11b"
		"S11b changed 누락|1|201||{\"data\":{\"status\":\"APPROVED\",\"recalculation\":{\"recommendationId\":\"rec-1\"}}}||s11b"
		"S11b changed false|1|201||{\"data\":{\"status\":\"APPROVED\",\"recalculation\":{\"changed\":false}}}||s11b"
		"S11b recalculation 없음|1|201||{\"data\":{\"status\":\"APPROVED\"}}||s11b"
		"S16a 같은 장부|0|200||$me_a|$me_a|step_s16a"
		"S16a 다른 장부 응답|1|200||$me_a|$me_v|step_s16a"
		"S16a 기준 응답 demoUser 누락|1|200||$me_no_demo|$me_a|step_s16a"
		"S16a 비교 응답 demoUser 누락|1|200||$me_a|$me_no_demo|step_s16a"
		"S16a 양쪽 demoUser 누락(같은 본문)|1|200||$me_no_demo|$me_no_demo|step_s16a"
		"S16a 양쪽 식별 필드 누락(같은 본문)|1|200||$me_no_id|$me_no_id|step_s16a"
		"S16a 양쪽 cardCount 타입 다름|1|200||$me_bad_type|$me_bad_type|step_s16a"
		"S16a 양쪽 data 없음|1|200||{}|{}|step_s16a"
	)
	local c name want step rc
	echo "| 주입 사례 | 기대 종료 코드 | 실제 종료 코드 | 판정 |"
	echo "|---|---|---|---|"
	for c in "${cases[@]}"; do
		IFS='|' read -r name want FIX_STATUS FIX_HEAD FIX_BODY FIX_BODY2 step FIX_STATUS2 FIX_HEAD2 <<< "$c"
		FIX_HEAD=$(printf '%b' "$FIX_HEAD")
		FIX_HEAD2=$(printf '%b' "$FIX_HEAD2")
		: > "$RESULTS"
		case "$step" in
			login) step_login fx T '{}' ;;
			s11b) printf '%s' "$FIX_BODY" > "$WORK/body"; step_s11b "$FIX_STATUS" ;;
			*) "$step" ;;
		esac
		summarize > /dev/null
		rc=$?
		if [ "$rc" = "$want" ]; then echo "| $name | $want | $rc | PASS |"; else echo "| $name | $want | $rc | FAIL |"; failures=$((failures + 1)); fi
	done
	echo
	if [ "$failures" -eq 0 ]; then echo "self-test PASS"; return 0; fi
	echo "self-test FAIL ($failures)" >&2
	return 1
}

if [ "$BASE" = "--self-test" ]; then
	self_test
	exit $?
fi

# 월·주문일·전월 기간을 함께 정한다. 지원 밖 월은 요청을 보내기 전에 멈춘다
check_month "$MONTH" "$TODAY" || exit 2
ORDER_DATE="$TODAY"
read -r PREV_FROM PREV_TO <<< "$(month_dates "$MONTH")"

# S1~S5 공통
step_s1
s=$(req - GET /api/v1/me)
nostore=$(header cache-control | grep -ci no-store)
record S2 "GET /api/v1/me 쿠키 없음" "401 UNAUTHENTICATED, no-store" "$s $(code), no-store=$nostore" \
	"$(same "$s $(code) $nostore" '401 UNAUTHENTICATED 1')"
s=$(req - GET /api/v1/card-products)
record S3 "GET /api/v1/card-products" 200 "$s" "$(same "$s" 200)"
s=$(req - GET /actuator/health)
record S4 "GET /actuator/health(프록시 비노출)" 404 "$s" "$(same "$s" 404)"
step_s5

# S6~S8 A 준비와 /me
member_ready a "$EMAIL_A" A
s=$(req a GET /api/v1/me)
record S8 "A GET /api/v1/me" "200, codefConnected=false" "$s codefConnected=$(field '.data.codefConnected')" \
	"$(same "$s $(field '.data.codefConnected')" '200 false')"

# S9 카드 등록·조회 + 월간 상태(없으면 추천이 NOT_DETERMINED로 승인 409, 설계 동작)
t=$(csrf a)
s=$(req a POST /api/v1/me/cards '{"displayName":"합성 카드","cardProductId":"09271"}' "X-CSRF-TOKEN: $t")
card_a=$(field '.data.userCardId')
record S9a "A 카드 등록(CSRF)" 201 "$s $(code)" "$(same "$s" 201)"
s=$(req a GET /api/v1/me/cards)
count=$(jq '(.data.items // .data) | length' "$WORK/body" 2> /dev/null)
record S9b "A 카드 목록" "200, 1장" "$s, ${count}장" "$(same "$s $count" '200 1')"
t=$(csrf a)
s=$(req a PUT "/api/v1/me/cards/$card_a/monthly-states/$MONTH" \
	"{\"previousMonthPerformanceWon\":{\"value\":0,\"period\":{\"from\":\"$PREV_FROM\",\"to\":\"$PREV_TO\"}},\"registeredOn\":{\"value\":\"2026-01-01\",\"period\":{\"from\":\"2026-01-01\",\"to\":\"2026-01-01\"}}}" \
	"X-CSRF-TOKEN: $t")
record S9c "A 월간 상태 입력(CSRF)" 200 "$s $(code)" "$(same "$s" 200)"

# S10 주문 2건과 목록
order="{\"amountWon\":10000,\"orderDate\":\"$ORDER_DATE\",\"orderTime\":null,\"merchantName\":\"합성 가맹점\",\"merchantClass\":\"ORDINARY_DOMESTIC\",\"paymentMethod\":\"DIRECT\"}"
t=$(csrf a)
s=$(req a POST /api/v1/me/orders "$order" "X-CSRF-TOKEN: $t")
order1=$(field '.data.orderId')
record S10a "A 주문 1(CSRF)" "201 CREATED" "$s $(field '.data.status')" "$(same "$s $(field '.data.status')" '201 CREATED')"
t=$(csrf a)
s=$(req a POST /api/v1/me/orders "$order" "X-CSRF-TOKEN: $t")
order2=$(field '.data.orderId')
record S10b "A 주문 2(CSRF)" 201 "$s" "$(same "$s" 201)"

# S11 추천 → 승인(본문 {}) → 같은 키 재전송
t=$(csrf a)
s=$(req a POST "/api/v1/me/orders/$order1/recommendations" '' "X-CSRF-TOKEN: $t")
reco_a=$(field '.data.recommendationId')
record S11a "A 추천(CSRF)" "201 SELECTED" "$s $(field '.data.outcome')" "$(same "$s $(field '.data.outcome')" '201 SELECTED')"
key="smoke-${RUN_ID}-1"
t=$(csrf a)
s=$(req a POST "/api/v1/me/orders/$order1/simulated-payments" '{}' "X-CSRF-TOKEN: $t" "Idempotency-Key: $key")
pay_a=$(field '.data.simulatedPaymentId')
first=$(jq -cS '.data' "$WORK/body" 2> /dev/null)
step_s11b "$s"
t=$(csrf a)
s=$(req a POST "/api/v1/me/orders/$order1/simulated-payments" '{}' "X-CSRF-TOKEN: $t" "Idempotency-Key: $key")
again=$(jq -cS '.data' "$WORK/body" 2> /dev/null)
record S11c "A 같은 키 재전송" "201, 같은 본문" "$s same=$(same "$again" "$first")" \
	"$([ "$s" = 201 ] && [ "$again" = "$first" ] && echo 1 || echo 0)"

# S12 simulateFailure true
t=$(csrf a)
s=$(req a POST "/api/v1/me/orders/$order2/simulated-payments" '{"simulateFailure":true}' "X-CSRF-TOKEN: $t" "Idempotency-Key: smoke-${RUN_ID}-2")
record S12a "A simulateFailure:true" "400 INVALID_REQUEST" "$s $(code)" "$(same "$s $(code)" '400 INVALID_REQUEST')"
s=$(req a GET "/api/v1/me/orders/$order2")
record S12b "A 주문 2 상태 관측(결제·멱등·원장 미저장의 증거 아님)" "200 CREATED" "$s $(field '.data.status')" "$(same "$s $(field '.data.status')" '200 CREATED')"

# S10c 주문 목록
s=$(req a GET '/api/v1/me/orders?page=0&size=1')
record S10c "A 주문 목록 size=1" "200, 1건, hasNext=true, 최신=주문 2" \
	"$s $(jq '.data.items|length' "$WORK/body") hasNext=$(field '.data.hasNext') latest2=$(same "$(field '.data.items[0].orderId')" "$order2")" \
	"$(same "$s $(jq '.data.items|length' "$WORK/body") $(field '.data.hasNext') $(field '.data.items[0].orderId')" "200 1 true $order2")"
s=$(req a GET '/api/v1/me/orders?page=0&size=51')
record S10d "A 주문 목록 size=51" "400 INVALID_REQUEST" "$s $(code)" "$(same "$s $(code)" '400 INVALID_REQUEST')"

# S13 CSRF 없음
s=$(req a POST /api/v1/me/orders "$order")
record S13 "A CSRF 없이 주문" "403 CSRF_INVALID" "$s $(code)" "$(same "$s $(code)" '403 CSRF_INVALID')"

# S14 B 준비와 A 데이터 접근
member_ready b "$EMAIL_B" B
s=$(req b GET "/api/v1/me/orders/$order1")
record S14a "B가 A 주문 조회" "404 ORDER_NOT_FOUND" "$s $(code)" "$(same "$s $(code)" '404 ORDER_NOT_FOUND')"
s=$(req b GET "/api/v1/me/simulated-payments/$pay_a")
record S14b "B가 A 결제 조회" "404 SIMULATED_PAYMENT_NOT_FOUND" "$s $(code)" "$(same "$s $(code)" '404 SIMULATED_PAYMENT_NOT_FOUND')"
s=$(req b GET "/api/v1/me/recommendations/$reco_a")
record S14c "B가 A 추천 조회" "404 RECOMMENDATION_NOT_FOUND" "$s $(code)" "$(same "$s $(code)" '404 RECOMMENDATION_NOT_FOUND')"
t=$(csrf b)
s=$(req b PATCH "/api/v1/me/cards/$card_a" '{"displayName":"x"}' "X-CSRF-TOKEN: $t")
record S14d "B가 A 카드 수정" "404 USER_CARD_NOT_FOUND" "$s $(code)" "$(same "$s $(code)" '404 USER_CARD_NOT_FOUND')"
s=$(req b GET /api/v1/me/orders)
record S14e "B 주문 목록" "200, 0건" "$s $(jq '.data.items|length' "$WORK/body")" "$(same "$s $(jq '.data.items|length' "$WORK/body")" '200 0')"

# S15 미등록 경로·메서드
s=$(req a GET /api/v1/demo-users)
record S15a "GET /api/v1/demo-users" "404 NOT_FOUND" "$s $(code)" "$(same "$s $(code)" '404 NOT_FOUND')"
for m in GET POST DELETE; do
	t=$(csrf a)
	body=""
	[ "$m" = POST ] && body='{}'
	s=$(req a "$m" /api/v1/me/codef/connection "$body" "X-CSRF-TOKEN: $t")
	record "S15-$m" "$m /api/v1/me/codef/connection" "404 NOT_FOUND" "$s $(code)" "$(same "$s $(code)" '404 NOT_FOUND')"
done
t=$(csrf a)
s=$(req a POST /api/v1/me/codef/syncs '{}' "X-CSRF-TOKEN: $t")
record S15e "POST /api/v1/me/codef/syncs" "404 NOT_FOUND" "$s $(code)" "$(same "$s $(code)" '404 NOT_FOUND')"
t=$(csrf a)
s=$(req a DELETE /api/v1/me/orders '' "X-CSRF-TOKEN: $t")
record S15f "DELETE /api/v1/me/orders" "405 METHOD_NOT_ALLOWED" "$s $(code)" "$(same "$s $(code)" '405 METHOD_NOT_ALLOWED')"

# S16 시연 헤더 무시
step_s16a
s=$(req - GET /api/v1/me '' 'X-BeONE-Demo-User: MY_CARDS')
record S16b "시연 헤더만" "401 UNAUTHENTICATED" "$s $(code)" "$(same "$s $(code)" '401 UNAUTHENTICATED')"

# S17 전달 헤더 위조 시 쿠키 속성 불변
step_s17

# S18 로그아웃 뒤 401
t=$(csrf a)
s=$(req a POST /api/v1/auth/logout '' "X-CSRF-TOKEN: $t")
record S18a "A 로그아웃(CSRF)" 204 "$s" "$(same "$s" 204)"
s=$(req a GET /api/v1/me)
record S18b "로그아웃 뒤 /me" "401 UNAUTHENTICATED" "$s $(code)" "$(same "$s $(code)" '401 UNAUTHENTICATED')"

summarize
