#!/usr/bin/env bash
# member 프로필 회원 API 스모크(합성 회원 2명). 대상은 같은 origin으로 웹과 /api를 함께 내는 주소다.
#
# 사용:
#   deploy/scripts/smoke-member.sh <BASE_URL>            예: http://localhost:18080
#   deploy/scripts/smoke-member.sh --self-test           서버 없이 응답 필드 읽기(false·0 보존)만 점검
#
# 이메일 인증 단계: member 프로필의 가짜 메일 발송기는 토큰을 메모리에만 두므로 화면·API로 인증을 끝낼 수 없다.
#   검증 DB에서 인증 완료 상태를 만든 뒤 계속한다(결정 39-1 T-a, 검증 DB·합성 회원 한정).
#   - SMOKE_VERIFY_HOOK이 있으면 이메일마다 "$SMOKE_VERIFY_HOOK <email>"을 실행한다(성공 시 종료 코드 0).
#   - 없으면 실행할 SQL을 안내하고 Enter를 기다린다.
#
# 환경 변수(선택): SMOKE_EMAIL_A, SMOKE_EMAIL_B(기본 실행마다 다른 *@example.test), SMOKE_PASSWORD(합성 비밀번호),
#   SMOKE_MONTH(기본 2026-11)
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
MONTH="${SMOKE_MONTH:-2026-11}"

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
# 쿠키 값은 지우고 속성만 남긴다
cookie_attrs() { header set-cookie | sed -E 's/^[^:]*: //; s/=[^;]*;/=<값>;/'; }
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

if [ "$BASE" = "--self-test" ]; then
	printf '{"code":"X","data":{"f":false,"z":0,"t":true,"n":null,"s":"v"}}' > "$WORK/body"
	got="$(field '.data.f'),$(field '.data.z'),$(field '.data.t'),[$(field '.data.n')],[$(field '.data.missing')],$(field '.data.s'),$(code)"
	if [ "$got" = "false,0,true,[],[],v,X" ]; then
		echo "self-test PASS ($got)"
		exit 0
	fi
	echo "self-test FAIL ($got)" >&2
	exit 1
fi

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
	t=$(csrf "$jar")
	s=$(req "$jar" POST /api/v1/auth/login "$login" "X-CSRF-TOKEN: $t")
	record "${p}4" "로그인" "200, __Host- 쿠키" "$s; $(cookie_attrs)" "$(same "$s" 200)"
}

# S1~S5 공통
s=$(req - GET /)
record S1 "GET /" "200 text/html" "$s $(header content-type | cut -d' ' -f2)" "$(same "$s" 200)"
s=$(req - GET /api/v1/me)
nostore=$(header cache-control | grep -ci no-store)
record S2 "GET /api/v1/me 쿠키 없음" "401 UNAUTHENTICATED, no-store" "$s $(code), no-store=$nostore" \
	"$(same "$s $(code) $nostore" '401 UNAUTHENTICATED 1')"
s=$(req - GET /api/v1/card-products)
record S3 "GET /api/v1/card-products" 200 "$s" "$(same "$s" 200)"
s=$(req - GET /actuator/health)
record S4 "GET /actuator/health(프록시 비노출)" 404 "$s" "$(same "$s" 404)"
s=$(req anon GET /api/v1/auth/csrf)
attrs=$(cookie_attrs)
ok=0
if echo "$attrs" | grep -q '__Host-BEONE_SESSION' && echo "$attrs" | grep -q 'Path=/' && echo "$attrs" | grep -q 'Secure' \
	&& echo "$attrs" | grep -q 'HttpOnly' && echo "$attrs" | grep -q 'SameSite=Lax' && ! echo "$attrs" | grep -qi 'domain='; then ok=1; fi
record S5 "익명 세션 쿠키 속성" "__Host-, Path=/, Secure, HttpOnly, SameSite=Lax, Domain 없음" "$s; $attrs" "$ok"

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
	'{"previousMonthPerformanceWon":{"value":0,"period":{"from":"2026-10-01","to":"2026-10-31"}},"registeredOn":{"value":"2026-01-01","period":{"from":"2026-01-01","to":"2026-01-01"}}}' \
	"X-CSRF-TOKEN: $t")
record S9c "A 월간 상태 입력(CSRF)" 200 "$s $(code)" "$(same "$s" 200)"

# S10 주문 2건과 목록
order='{"amountWon":10000,"orderDate":"2026-11-25","orderTime":null,"merchantName":"합성 가맹점","merchantClass":"ORDINARY_DOMESTIC","paymentMethod":"DIRECT"}'
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
record S11b "A 승인(본문 {})" "201 APPROVED, changed=null" \
	"$s $(field '.data.status') changed=$(jq -r '.data.recalculation.changed' "$WORK/body" 2> /dev/null)" \
	"$(same "$s $(field '.data.status')" '201 APPROVED')"
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
record S12b "A 주문 2 미저장 확인" "200 CREATED" "$s $(field '.data.status')" "$(same "$s $(field '.data.status')" '200 CREATED')"

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
s=$(req a GET /api/v1/me '' 'X-BeONE-Demo-User: VIRTUAL_PORTFOLIO')
record S16a "쿠키 + 시연 헤더" "200(A 장부)" "$s" "$(same "$s" 200)"
s=$(req - GET /api/v1/me '' 'X-BeONE-Demo-User: MY_CARDS')
record S16b "시연 헤더만" "401 UNAUTHENTICATED" "$s $(code)" "$(same "$s $(code)" '401 UNAUTHENTICATED')"

# S17 전달 헤더 위조 시 쿠키 속성 불변
req f1 GET /api/v1/auth/csrf > /dev/null
plain=$(cookie_attrs)
req f2 GET /api/v1/auth/csrf '' 'X-Forwarded-Host: evil.invalid' 'X-Forwarded-Proto: http' 'X-Forwarded-For: 192.0.2.1' > /dev/null
forged=$(cookie_attrs)
location=$(header location | wc -l | tr -d ' ')
record S17 "X-Forwarded-* 위조" "Set-Cookie 속성 동일, Location 없음" "same=$(same "$plain" "$forged") location=$location" \
	"$([ "$plain" = "$forged" ] && [ "$location" = 0 ] && echo 1 || echo 0)"

# S18 로그아웃 뒤 401
t=$(csrf a)
s=$(req a POST /api/v1/auth/logout '' "X-CSRF-TOKEN: $t")
record S18a "A 로그아웃(CSRF)" 204 "$s" "$(same "$s" 204)"
s=$(req a GET /api/v1/me)
record S18b "로그아웃 뒤 /me" "401 UNAUTHENTICATED" "$s $(code)" "$(same "$s $(code)" '401 UNAUTHENTICATED')"

printf '| ID | 항목 | 기대 | 실제 | 판정 |\n|---|---|---|---|---|\n'
cat "$RESULTS"
pass=$(grep -c '| PASS |$' "$RESULTS")
fail=$(grep -c '| FAIL |$' "$RESULTS")
echo
echo "PASS=$pass FAIL=$fail"
[ "$fail" -eq 0 ]
