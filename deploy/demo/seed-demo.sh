#!/usr/bin/env bash
# 시연 모드 서버(beone-demo) 준비 스크립트. 절차와 근거: docs/deployment/demo-mode.md 4절.
# 같은 순서·값을 backend DemoSeedIntegrationTest가 고정한다(seed/ 파일을 함께 읽음).
#
#   1. 시연 회원 가입(API) → DB에서 이메일 인증 완료 표시(demo는 가짜 메일이라 인증 메일이 오지 않음) → 로그인 확인
#   2. 시연 사용자 MY_CARDS 장부에 예시 카드 4종 등록(이미 있으면 건너뜀)과 월간 상태 입력(대상 월)
#   3. 확인: 일반 국내 100,000원 주문의 추천이 확정(SELECTED, 조건부 아님)이고 카드별 혜택이 확정 값인지 출력
#      --approve를 주면 그 주문을 모의 승인까지 한다(원장 행이 생김)
#
# 회원 장부가 아니라 MY_CARDS에 넣는 이유: main 프론트의 주문·결제 화면은 쿠키 없이 X-BeONE-Demo-User 헤더로 부르고,
# demo 프로필은 그 헤더로 사용자를 정한다(CurrentUserResolver). 회원 로그인은 화면 진입(세션 확인)에만 쓰인다.
#
# 실행 위치: 서버의 /opt/beone/demo(compose.yaml·.env가 있는 곳). 준비 리스너 127.0.0.1:18084만 부른다.
# 필요한 것: curl, python3(JSON 읽기), docker compose. 비밀번호는 read -rs로 받고 출력하지 않는다. 쿠키는 임시 파일에만 두고 끝나면 비운다.
#
# 사용: ./seed-demo.sh --email <시연 이메일> [--month YYYY-MM] [--approve] [--skip-member]
set -euo pipefail

BASE="${BEONE_DEMO_PREP_URL:-http://127.0.0.1:18084}"
HERE="$(cd "$(dirname "$0")" && pwd)"
SEED="$HERE/seed"
PROJECT=(docker compose -f "$HERE/compose.yaml" -p beone-demo --env-file "$HERE/.env")
EMAIL=""
MONTH="$(TZ=Asia/Seoul date +%Y-%m)"
APPROVE=false
SKIP_MEMBER=false

fail() { echo "실패: $*" >&2; exit 1; }

while [ $# -gt 0 ]; do
	case "$1" in
		--email) EMAIL="${2:-}"; shift 2 ;;
		--month) MONTH="${2:-}"; shift 2 ;;
		--approve) APPROVE=true; shift ;;
		--skip-member) SKIP_MEMBER=true; shift ;;
		*) fail "알 수 없는 인자: $1" ;;
	esac
done

command -v curl > /dev/null || fail "curl 없음"
command -v python3 > /dev/null || fail "python3 없음"
[[ "$MONTH" =~ ^[0-9]{4}-(0[1-9]|1[0-2])$ ]] || fail "--month 형식은 YYYY-MM"
[ -f "$SEED/cards.tsv" ] || fail "seed/cards.tsv 없음"

# 날짜: 대상 월 1일~말일, 전월 1일~말일. 주문일은 대상 월이 이번 달이면 오늘, 아니면 대상 월 1일(월간 상태 달 = 주문 날짜 달)
read -r MONTH_FROM MONTH_TO PREV_FROM PREV_TO ORDER_DATE < <(python3 - "$MONTH" <<'PY'
import calendar, datetime, sys
from zoneinfo import ZoneInfo
y, m = map(int, sys.argv[1].split("-"))
first = datetime.date(y, m, 1)
last = datetime.date(y, m, calendar.monthrange(y, m)[1])
prev_last = first - datetime.timedelta(days=1)
prev_first = prev_last.replace(day=1)
today = datetime.datetime.now(ZoneInfo("Asia/Seoul")).date()
order = today if first <= today <= last else first
print(first, last, prev_first, prev_last, order)
PY
)

# 세션 쿠키는 이 실행 동안만 임시 파일에 둔다. 끝나면 내용을 비운다(삭제 명령을 쓰지 않음, 빈 임시 파일만 남음)
JAR="$(mktemp)"
chmod 600 "$JAR"
trap ': > "$JAR"' EXIT

# JSON 응답에서 값 하나를 꺼낸다. 경로는 점으로 잇는다(예: data.userCardId)
json() { python3 -c 'import json,sys
v=json.load(sys.stdin)
for k in sys.argv[1].split("."):
    v=v[int(k)] if isinstance(v,list) else v.get(k)
print("" if v is None else v)' "$1"; }

# 시연 헤더 요청. 본문은 표준 입력으로 넘긴다(명령줄·프로세스 목록에 남기지 않음). 상태 코드를 마지막 줄에 붙인다
demo() {
	local method="$1" path="$2" args=(-sS -X "$1" "$BASE$2" -H 'X-BeONE-Demo-User: MY_CARDS' -w '\n%{http_code}')
	if [ -n "${3:-}" ]; then args+=(-H "Idempotency-Key: $3"); fi
	if [ "$method" = GET ]; then
		curl "${args[@]}" < /dev/null
	else
		curl "${args[@]}" -H 'Content-Type: application/json' --data-binary @-
	fi
}
status() { tail -n1; }
body() { sed '$d'; }

curl -fsS "$BASE/actuator/health" | grep -q '"status":"UP"' || fail "backend 상태 확인 실패($BASE/actuator/health)"

# 1. 시연 회원
if [ "$SKIP_MEMBER" = false ]; then
	EMAIL="$(printf '%s' "$EMAIL" | tr '[:upper:]' '[:lower:]')"
	[[ "$EMAIL" =~ ^[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}$ ]] || fail "--email 형식 확인(작은따옴표 등 특수문자 불가)"
	read -rsp "시연 계정 비밀번호(화면에 표시되지 않음): " PASSWORD; echo
	[ -n "$PASSWORD" ] || fail "비밀번호가 비었음"
	# 비밀번호를 JSON 문자열로 감싼다(따옴표·역슬래시 포함 가능)
	CREDENTIALS="$(EMAIL="$EMAIL" PASSWORD="$PASSWORD" python3 -c 'import json,os; print(json.dumps({"email":os.environ["EMAIL"],"password":os.environ["PASSWORD"]}))')"
	unset PASSWORD

	csrf() { curl -sS -c "$JAR" -b "$JAR" "$BASE/api/v1/auth/csrf" | json data.token; }
	auth() {
		local path="$1" token
		token="$(csrf)"
		[ -n "$token" ] || fail "CSRF 토큰 조회 실패"
		printf '%s' "$CREDENTIALS" | curl -sS -o /dev/null -w '%{http_code}' -c "$JAR" -b "$JAR" -X POST "$BASE$path" \
			-H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $token" --data-binary @-
	}

	code="$(auth /api/v1/auth/sign-up)"
	case "$code" in
		202) echo "가입 요청: 202(이미 가입된 이메일도 같은 응답)" ;;
		*) fail "가입 요청 HTTP $code" ;;
	esac
	# demo는 가짜 메일(메모리 보관, 로그 없음)이라 인증 링크를 받을 수 없다. DB에서 인증 완료로 표시한다(이미 표시됐으면 0행)
	printf "UPDATE members SET email_verified_at = NOW() WHERE email = '%s' AND email_verified_at IS NULL;\nSELECT COUNT(*) FROM members WHERE email = '%s' AND email_verified_at IS NOT NULL;\n" "$EMAIL" "$EMAIL" \
		| "${PROJECT[@]}" exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N "$MYSQL_DATABASE"' \
		| tail -n1 | grep -qx 1 || fail "인증 완료 표시 확인 실패"
	echo "인증 완료 표시: 확인"
	code="$(auth /api/v1/auth/login)"
	[ "$code" = 200 ] || fail "로그인 HTTP $code(비밀번호 확인, 5회 실패 시 잠금)"
	curl -sS -o /dev/null -w '%{http_code}' -b "$JAR" "$BASE/api/v1/auth/session" | grep -qx 200 || fail "세션 확인 실패"
	echo "로그인·세션: 확인(화면에서는 같은 이메일·비밀번호로 로그인)"
	unset CREDENTIALS
fi

# 2. MY_CARDS 카드 4종과 월간 상태
existing="$(printf '' | demo GET /api/v1/me/cards)"
[ "$(printf '%s\n' "$existing" | status)" = 200 ] || fail "보유 카드 조회 실패"
existing="$(printf '%s\n' "$existing" | body)"
while IFS=$'\t' read -r product name; do
	[ -n "$product" ] || continue
	card="$(printf '%s' "$existing" | python3 -c 'import json,sys
items=json.load(sys.stdin)["data"]["items"]
print(next((i["userCardId"] for i in items if i.get("cardProductId")==sys.argv[1]), ""))' "$product")"
	if [ -z "$card" ]; then
		response="$(PRODUCT="$product" NAME="$name" python3 -c 'import json,os; print(json.dumps({"displayName":os.environ["NAME"],"cardProductId":os.environ["PRODUCT"]}))' | demo POST /api/v1/me/cards)"
		[ "$(printf '%s\n' "$response" | status)" = 201 ] || fail "카드 등록 실패 $product: $(printf '%s\n' "$response" | body)"
		card="$(printf '%s\n' "$response" | body | json data.userCardId)"
		echo "카드 등록: $product → $card"
	else
		echo "카드 있음: $product → $card(건너뜀)"
	fi
	response="$(sed -e "s/{{PREV_FROM}}/$PREV_FROM/g" -e "s/{{PREV_TO}}/$PREV_TO/g" -e "s/{{MONTH_FROM}}/$MONTH_FROM/g" \
		-e "s/{{MONTH_TO}}/$MONTH_TO/g" "$SEED/state-$product.json" | demo PUT "/api/v1/me/cards/$card/monthly-states/$MONTH")"
	[ "$(printf '%s\n' "$response" | status)" = 200 ] || fail "월간 상태 입력 실패 $product: $(printf '%s\n' "$response" | body)"
	echo "월간 상태 입력: $product $MONTH(전월 $PREV_FROM~$PREV_TO 실적 600,000원)"
done < "$SEED/cards.tsv"

# 3. 확인: 일반 국내 100,000원 직접 결제 주문 → 추천(→ 선택 시 모의 승인)
order="$(ORDER_DATE="$ORDER_DATE" python3 -c 'import json,os; print(json.dumps({"amountWon":100000,"orderDate":os.environ["ORDER_DATE"],"merchantName":"동네 마트","merchantClass":"ORDINARY_DOMESTIC","paymentMethod":"DIRECT"}))' | demo POST /api/v1/me/orders)"
[ "$(printf '%s\n' "$order" | status)" = 201 ] || fail "확인 주문 생성 실패: $(printf '%s\n' "$order" | body)"
order_id="$(printf '%s\n' "$order" | body | json data.orderId)"
recommendation="$(printf '' | demo POST "/api/v1/me/orders/$order_id/recommendations")"
[ "$(printf '%s\n' "$recommendation" | status)" = 201 ] || fail "추천 실패: $(printf '%s\n' "$recommendation" | body)"
printf '%s\n' "$recommendation" | body | python3 -c 'import json,sys
d=json.load(sys.stdin)["data"]
print("추천(%s): outcome=%s conditional=%s 선택=%s" % (d["orderId"], d["outcome"], d["conditional"], (d.get("selected") or {}).get("cardProductId")))
for c in d["comparedCards"]:
    b=c["currentOrderBenefit"]; print("  비교 %s %s원 %s" % (c["cardProductId"], b["amountWon"], b["status"]))
for c in d["excludedCards"]:
    print("  제외 %s %s %s" % (c.get("cardProductId"), c["reason"], c.get("detail")))
ok = d["outcome"]=="SELECTED" and not d["conditional"] and not d["excludedCards"] and len(d["comparedCards"])>=4
print("판정: " + ("통과(확정 추천, 4장 비교)" if ok else "확인 필요(런북 4-3절)"))
sys.exit(0 if ok else 3)'

if [ "$APPROVE" = true ]; then
	approval="$(printf '{}' | demo POST "/api/v1/me/orders/$order_id/simulated-payments" "seed-$(date +%s)")"
	[ "$(printf '%s\n' "$approval" | status)" = 201 ] || fail "모의 승인 실패: $(printf '%s\n' "$approval" | body)"
	printf '%s\n' "$approval" | body | python3 -c 'import json,sys
d=json.load(sys.stdin)["data"]; b=d["currentOrderBenefit"]
print("모의 승인: %s %s %s원 결제, 혜택 %s원 %s" % (d["status"], d["cardProductId"], d["amountWon"], b["amountWon"], b["status"]))'
fi
echo "완료"
