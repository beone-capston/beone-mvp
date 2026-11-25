#!/usr/bin/env bash
# 운영 회원 스모크 절차서(docs/deployment/prod-member-smoke.md)의 실행 블록을 합성 환경에서 점검한다.
# 운영 서버·실메일·실DB를 쓰지 않는다. 문서의 블록 원문을 그대로 뽑아 실행하고, 바꾸는 것은 아래뿐이다.
#   - 4절: 4-0의 B= 줄(로컬 가짜 API 주소), pbcopy(클립보드를 건드리지 않는 함수)
#   - 4-2 로그 블록: PATH 앞의 가짜 docker·권한 상승 명령(인자를 그대로 실행)
#
# 사용: deploy/scripts/prod-member-smoke-selftest.sh            (모든 사례, 실패가 있으면 종료 1)
#       PMS_DOC=<다른 문서 경로> deploy/scripts/prod-member-smoke-selftest.sh   (변이 시험용)
# 필요: bash, python3, curl, jq, lsof, awk
set -u

HERE="$(cd "$(dirname "$0")" && pwd)"
DOC="${PMS_DOC:-$HERE/../../docs/deployment/prod-member-smoke.md}"
[ -f "$DOC" ] || { echo "문서 없음: $DOC" >&2; exit 2; }
for c in python3 curl jq lsof awk; do command -v "$c" > /dev/null || { echo "$c 필요" >&2; exit 2; }; done

T="$(mktemp -d)" && [ -d "$T" ] || { echo "self-test 준비 실패: 임시 폴더" >&2; exit 1; }
mkdir "$T/bin" || exit 1
SRV_PID=""
cleanup() {
	[ -n "$SRV_PID" ] && kill "$SRV_PID" 2> /dev/null
	rm -f "$T"/bin/*
	rmdir "$T/bin" 2> /dev/null
	rm -f "$T"/*
	rmdir "$T" 2> /dev/null
}
trap cleanup EXIT
FAILS=0
check() {   # check <이름> <조건 종료 코드>
	if [ "$2" = 0 ]; then echo "  PASS $1"; else echo "  FAIL $1"; FAILS=$((FAILS + 1)); fi
}

# ---------- 4-2 발송 실패 로그 블록 ----------
cat > "$T/bin/sudo" << 'EOF'
#!/usr/bin/env bash
# 합성 시험용: 권한 상승 없이 인자를 그대로 실행한다
exec "$@"
EOF
cat > "$T/bin/docker" << 'EOF'
#!/usr/bin/env bash
# 합성 시험용 docker. FAKE_LOG_MODE에 따라 compose logs 결과를 흉내 내고, 표준 출력 대상(블록의 임시 파일)의 권한·경로를 남긴다
f=$(lsof -a -p $$ -d 1 -Fn 2> /dev/null | sed -n 's/^n//p' | head -1)
[ -n "${FAKE_PROBE:-}" ] && printf '%s %s\n' "$(stat -f %Lp "$f" 2> /dev/null || stat -c %a "$f" 2> /dev/null)" "$f" > "$FAKE_PROBE"
case "${FAKE_LOG_MODE:-}" in
	fail) echo "permission denied while trying to connect to the Docker daemon socket (synthetic)" >&2; exit 1 ;;
	zero) printf 'backend-1 | INFO started (synthetic)\nbackend-1 | INFO request ok (synthetic)\n' ;;
	one) printf 'backend-1 | INFO started (synthetic)\nbackend-1 | WARN auth mail send failed purpose=EMAIL_VERIFICATION to=s***@*** reason=synthetic\n' ;;
	partial) printf 'backend-1 | WARN auth mail send failed purpose=EMAIL_VERIFICATION to=s***@*** reason=synthetic\n'; exit 2 ;;
esac
EOF
chmod 755 "$T/bin/sudo" "$T/bin/docker"
awk '/^ *```bash$/{inb=1; buf=""; match($0, /^ */); ind=RLENGTH; next} /^ *```$/{ if (inb && buf ~ /auth mail send failed/) printf "%s", buf; inb=0; next} inb{buf=buf substr($0, ind + 1) "\n"}' "$DOC" > "$T/blk42.sh"
echo "[4-2] 로그 블록 $(wc -l < "$T/blk42.sh" | tr -d ' ')줄"
for mode in fail zero one partial; do
	out=$(cd "$T" && PATH="$T/bin:$PATH" FAKE_PROBE="$T/probe" FAKE_LOG_MODE=$mode bash "$T/blk42.sh" 2>&1)
	perm=""; file=""
	[ -f "$T/probe" ] && read -r perm file < "$T/probe"
	rm -f "$T/probe"
	echo "  [$mode] $(printf '%s' "$out" | tr '\n' '|')"
	case $mode in
		fail | partial) grep -q '조회 실패: 중단' <<< "$out" && grep -q '종료 1' <<< "$out"; check "4-2 $mode: 조회 실패로 중단·종료 1" $? ;;
		zero) grep -q '건수: 0' <<< "$out" && grep -q '종료 0' <<< "$out"; check "4-2 zero: 건수 0·종료 0" $? ;;
		one) grep -q '건수: 1' <<< "$out" && grep -q '종료 0' <<< "$out"; check "4-2 one: 건수 1·종료 0" $? ;;
	esac
	! grep -q 'INFO started\|reason=synthetic' <<< "$out"; check "4-2 $mode: 로그 원문 비출력" $?
	[ "$perm" = 600 ]; check "4-2 $mode: 임시 파일 권한 600(실제 $perm)" $?
	[ -n "$file" ] && [ ! -e "$file" ]; check "4-2 $mode: 임시 파일 삭제" $?
done

# ---------- 4절 사용자 블록 ----------
cat > "$T/fake_api.py" << 'EOF'
# 합성 회원 API. docs/api/auth.md의 상태 코드·오류 코드·쿠키 속성만 흉내 낸다. 사용: fake_api.py <port파일> <mode> <기록>
import http.server, json, sys, secrets
PORTFILE, MODE, LOG = sys.argv[1], sys.argv[2], sys.argv[3]
TOKEN = 'T' * 42 + '_'
sessions, member = {}, {}
CK = '; Path=/; Max-Age=%d; Secure; HttpOnly; SameSite=Lax'
class H(http.server.BaseHTTPRequestHandler):
    def sess(self):
        for part in (self.headers.get('Cookie') or '').split(';'):
            k, _, v = part.strip().partition('=')
            if k == '__Host-BEONE_SESSION' and v in sessions:
                return v
        return None
    def send(self, st, body=None, cookie=None):
        self.send_response(st)
        if cookie: self.send_header('Set-Cookie', cookie)
        out = json.dumps(body).encode() if body is not None else b''
        if body is not None: self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(out))); self.end_headers(); self.wfile.write(out)
    def log(self, s, extra=''):
        with open(LOG, 'a') as f: f.write('%s %s -> %s %s\n' % (self.command, self.path, s, extra))
    def do_GET(self):
        s = self.sess()
        if self.path == '/api/v1/auth/csrf':
            if MODE == 'csrf_down':
                self.log(503); return self.send(503, {'code': 'INTERNAL_ERROR'})
            cookie = None
            if not s:
                s = secrets.token_hex(8); sessions[s] = {'member': False}; cookie = '__Host-BEONE_SESSION=' + s + CK % 7200
            self.log(200)
            return self.send(200, {'data': {'headerName': 'X-CSRF-TOKEN', 'token': 'csrf-' + s if MODE != 'csrf_fail' else 'csrf-stale'}}, cookie)
        if self.path == '/api/v1/me':
            if s and sessions[s]['member']:
                self.log(200)
                return self.send(200, {'data': {'demoUser': None, 'displayName': '내 카드', 'portfolioType': 'REAL', 'readOnly': False, 'cardCount': 0, 'codefConnected': False}})
            self.log(401); return self.send(401, {'code': 'UNAUTHENTICATED'})
        self.log(404); self.send(404, {'code': 'NOT_FOUND'})
    def do_POST(self):
        n = int(self.headers.get('Content-Length') or 0)
        raw = self.rfile.read(n).decode() if n else ''
        s = self.sess()
        if not s or self.headers.get('X-CSRF-TOKEN') != 'csrf-' + s:
            self.log(403, 'CSRF'); return self.send(403, {'code': 'CSRF_INVALID'})
        body = json.loads(raw) if raw else {}
        p = self.path
        if p == '/api/v1/auth/sign-up':
            member.update(email=body['email'], password=body['password'], verified=False)
            self.log(202, 'pw_len=%d' % len(body['password']))
            return self.send(202, {'data': {'message': 'synthetic'}})
        if p == '/api/v1/auth/email-verification-requests':
            self.log(202); return self.send(202, {'data': {'message': 'synthetic'}})
        if p == '/api/v1/auth/email-verifications':
            if body.get('token') == TOKEN and member:
                member['verified'] = True; self.log(204); return self.send(204)
            self.log(400); return self.send(400, {'code': 'AUTH_TOKEN_INVALID'})
        if p == '/api/v1/auth/login':
            if not member or body.get('email') != member['email'] or body.get('password') != member['password']:
                self.log(401); return self.send(401, {'code': 'INVALID_CREDENTIALS'})
            if not member['verified']:
                self.log(403); return self.send(403, {'code': 'EMAIL_NOT_VERIFIED'})
            del sessions[s]; ns = secrets.token_hex(8); sessions[ns] = {'member': True}; self.log(200)
            return self.send(200, {'data': {'userId': 'usr-' + 'a' * 32, 'email': member['email']}}, '__Host-BEONE_SESSION=' + ns + CK % 2592000)
        if p == '/api/v1/auth/logout':
            del sessions[s]; self.log(204)
            return self.send(204, None, '__Host-BEONE_SESSION=; Path=/; Max-Age=0; Secure; HttpOnly; SameSite=Lax')
        self.log(404); self.send(404, {'code': 'NOT_FOUND'})
    def log_message(self, *a): pass
srv = http.server.ThreadingHTTPServer(('127.0.0.1', 0), H)
open(PORTFILE, 'w').write(str(srv.server_address[1]))
srv.serve_forever()
EOF

SEC4="$(awk '/^## 4\./{f=1} /^## 5\./{f=0} f' "$DOC")"
blk() { awk -v pat="$1" '/^ *```bash$/{inb=1; buf=""; match($0, /^ */); ind=RLENGTH; next} /^ *```$/{ if (inb && buf ~ pat) printf "%s", buf; inb=0; next} inb{buf=buf substr($0, ind + 1) "\n"}' <<< "$SEC4"; }
row() { grep "^| $1 |" <<< "$SEC4" | sed -E 's/^\| [^|]+ \| `//; s/` \| .*$//; s/\\\|/|/g'; }
TOK="$(printf 'T%.0s' $(seq 1 42))_"
PW='Pw-synth"q\9x'

# gen <기준 주소> : 4절 블록을 순서대로 이어 붙인 실행 스크립트를 만든다
gen() {
	{
		echo 'pbcopy() { cat > /dev/null; }'
		blk 'B=https://beonepay.site' | sed "s#^B=https://beonepay.site#B=$1#"
		echo "echo \"\$W\" > \"$T/wpath\""
		for m in M0 M1 M2; do printf 'echo "== %s"\n%s\n' "$m" "$(row $m)"; done
		printf 'echo "== RESEND"\n%s\n' "$(blk 'email-verification-requests')"
		printf 'echo "== M4"\n%s\n' "$(blk '인증 링크 전체')"
		for m in M5 M6 M7 M8; do printf 'echo "== %s"\n%s\n' "$m" "$(row $m)"; done
		printf 'echo "== END"\n%s\n' "$(row 끝)"
		echo 'echo "!! exit 뒤에 실행됨"'
	} > "$T/gen.sh"
}
# run4 <사례> <서버 모드|network> [링크]
run4() {
	local name="$1" mode="$2" link="${3:-https://beonepay.site/email-verification?token=$TOK}" base
	rm -f "$T/port" "$T/api.log" "$T/wpath"
	if [ "$mode" = network ]; then
		base="http://localhost:9"
	else
		python3 "$T/fake_api.py" "$T/port" "$mode" "$T/api.log" & SRV_PID=$!
		for _ in $(seq 1 50); do [ -s "$T/port" ] && break; sleep 0.1; done
		[ -s "$T/port" ] || { check "$name: 가짜 API 기동" 1; return; }
		base="http://localhost:$(cat "$T/port")"
	fi
	gen "$base"
	printf 'smoke@example.test\n%s\n%s\n' "$PW" "$link" > "$T/input"
	OUT="$(/bin/bash --norc --noprofile "$T/gen.sh" < "$T/input" 2>&1)"; RC=$?
	[ -n "$SRV_PID" ] && { kill "$SRV_PID" 2> /dev/null; wait "$SRV_PID" 2> /dev/null; SRV_PID=""; }
	echo "  [$name] $(printf '%s' "$OUT" | tr '\n' '~' | sed 's/~== / | /g')"
	[ "$RC" = 0 ] && ! grep -q '!! exit 뒤' <<< "$OUT"; check "$name: 끝(exit)에서 종료, 셸 종료 0" $?
	local w; w="$(cat "$T/wpath" 2> /dev/null)"
	[ -n "$w" ] && [ ! -e "$w" ]; check "$name: 임시 폴더(쿠키 파일) 삭제" $?
	! grep -q 'Pw-synth\|TTTTTTTTTT\|smoke@example' <<< "$OUT"; check "$name: 비밀번호·토큰·주소 비출력" $?
	! grep -q 'jq: error\|No such file' <<< "$OUT"; check "$name: 도구 오류 없음" $?
}
expect() {  # expect <사례> <단계> <기대 줄(정규식)> : 해당 단계 출력에 기대 줄이 있어야 한다
	awk -v st="== $2" '$0 == st {f=1; next} /^== / {f=0} f' <<< "$OUT" | grep -qE "$3"; check "$1: $2 ~ $3" $?
}
echo "[4절] /bin/bash $(/bin/bash -c 'echo $BASH_VERSION'), $(curl --version | head -1 | cut -d' ' -f1-2)"
run4 성공 ok
expect 성공 M0 '^401 UNAUTHENTICATED$'; expect 성공 M1 '^202 $'; expect 성공 M2 '^403 EMAIL_NOT_VERIFIED$'
expect 성공 RESEND '^202 $'; expect 성공 M4 '^204 $'; expect 성공 M5 '^200  userId=true$'
expect 성공 M5 '^__Host-BEONE_SESSION=<값>; Path=/; Max-Age=2592000; Secure; HttpOnly; SameSite=Lax$'
expect 성공 M6 '^\{"demoUser":null,"displayName":"내 카드","portfolioType":"REAL","readOnly":false,"cardCount":0,"codefConnected":false\}$'
expect 성공 M7 '^204$'; expect 성공 M7 'Max-Age=0'; expect 성공 M8 '^401 UNAUTHENTICATED$'
grep -q 'pw_len=13' "$T/api.log"; check "성공: 따옴표·역슬래시 비밀번호가 JSON으로 그대로 전달(13자)" $?
run4 CSRF거부 csrf_fail
expect CSRF거부 M1 '^403 CSRF_INVALID$'; expect CSRF거부 M5 '^403 CSRF_INVALID userId=$'; expect CSRF거부 M7 '^403$'
run4 CSRF발급실패 csrf_down
expect CSRF발급실패 M1 '^CSRF 발급 실패\(상태 503\)$'; expect CSRF발급실패 M1 '^중단 '; expect CSRF발급실패 M7 '^중단$'
run4 네트워크오류 network
expect 네트워크오류 M0 '^000 $'; expect 네트워크오류 M1 '^CSRF 발급 실패\(상태 000\)$'; expect 네트워크오류 M6 '^000$'
run4 링크형식오류 ok 'https://beonepay.site/email-verification?token=short'
expect 링크형식오류 M4 '토큰 형식이 아님'; expect 링크형식오류 M5 '^403 EMAIL_NOT_VERIFIED'

echo
if [ "$FAILS" = 0 ]; then echo "prod-member-smoke self-test PASS"; exit 0; fi
echo "prod-member-smoke self-test FAIL ($FAILS)"
exit 1
