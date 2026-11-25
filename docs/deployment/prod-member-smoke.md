# 운영 회원 스모크 절차 (B8, 초안)

운영 주소 `https://beonepay.site`에서 실제 메일 1통으로 회원 흐름 하나를 확인하는 절차 **초안**입니다. [공개 nginx 4-1](public-proxy.md#4-1-최초-반영-bootstrap-1회-승인-창-안)의 B8 항목입니다.

- **상태:** 초안입니다. 아직 실행하지 않았습니다. 실행 여부와 시점은 Aside(운영 담당)가 사용자와 정합니다.
- **기준 두 가지:** 아래 둘을 구분합니다. 실행 전에 둘 사이에 backend 변경이 없는지 2절에서 확인합니다.
  - **문서 조사 기준 커밋:** `develop` `23bca21`(Flyway 마이그레이션 V15까지). 이 문서를 쓸 때 대조한 소스입니다.
  - **운영 이미지 기준:** 지금 운영 backend는 GitSha·이미지 revision `6974880`, ApiDigest `sha256:9a887bd8a67b1577eedfea11d5158fc11d3e1545922d3e2992c919eed495cacd`입니다(첫 운영 배포, 2026-11-25). `6974880`은 `23bca21`의 조상이고, 둘 사이의 변경은 `tools/rule-draft`·`docs/rules`뿐입니다. `backend/`·`frontend/` 변경은 없어서 이 문서의 대조 결과가 그대로 적용됩니다.
- **결정 필요(실행 전):** 이메일 인증 화면이 아직 없습니다(1-3절). 인증 단계를 어떤 방식으로 할지 정해야 실행할 수 있습니다.

## 1. 목적과 범위

### 1-1. 확인하는 흐름

공개 주소에서 아래 순서가 끝까지 되는지 봅니다. 회원 1명, 실제 메일 1통(재요청하면 최대 2통)입니다.

```text
가입(202) → 인증 전 로그인 403 → 인증 메일 수신 → 이메일 인증(204) → 로그인(200, 세션 쿠키) → GET /api/v1/me 200 → 로그아웃(204) → /me 401
```

- **하지 않는 것:** 카드 등록·주문·추천·모의 결제 같은 업무 API, CODEF 경로, 부하 확인. 업무 데이터를 만들면 정리할 행이 늘고, 그 행이 있으면 회원 장부 행을 지울 수 없습니다(6절).
- 쿠키 속성·`X-Forwarded-*` 위조·`/actuator/health` 404·`Referrer-Policy`는 B6·B7(공개 확인·공개 스모크)이 이미 봅니다. 여기서는 로그인 세션 쿠키 속성만 다시 봅니다.

### 1-2. 기존 `smoke-member.sh`를 쓰지 않는 이유

[`deploy/scripts/smoke-member.sh`](../../deploy/scripts/smoke-member.sh)(#199, LP-7)는 `member` 검증 환경 전용입니다. 소스를 대조한 결과, 운영에는 그대로 쓸 수 없습니다.

| 항목 | `smoke-member.sh`의 전제 | 운영(`prod`) | 판단 |
|---|---|---|---|
| 메일 발송기 | `member` 프로필의 가짜 발송기. 토큰을 서버 메모리에만 둠([`FakeAuthMailSender`](../../backend/src/main/java/com/beone/api/auth/application/FakeAuthMailSender.java)) | Gmail SMTP로 실제 발송([`SmtpMailConfiguration`](../../backend/src/main/java/com/beone/api/auth/application/SmtpMailConfiguration.java), 결정 39-6) | 다름 |
| 인증 완료 방법 | 검증 DB에서 `UPDATE members SET email_verified_at …`(T-a 훅 `SMOKE_VERIFY_HOOK` 또는 안내 SQL) | T-a는 검증 DB·합성 회원 한정(결정 39-1). 운영 DB에서 쓰지 않음 | **쓸 수 없음** |
| 가입 주소 | 실행마다 `smoke-a-…@example.test`, `smoke-b-…@example.test` 2명 | 운영에서는 이 주소로 실제 메일 발송을 시도함(`.test`는 예약 도메인이라 전달되지 않음) | **쓸 수 없음** |
| 비밀번호 | 기본값 `Synthetic-pw-2026!` 고정 또는 환경 변수 `SMOKE_PASSWORD` | 실제 계정 비밀번호는 환경 변수·명령줄에 두지 않음 | 다름 |
| 범위 | S1~S18: 카드 등록, 월간 상태, 주문 2건, 추천, 모의 결제, 다른 회원 접근 등 | 운영 DB에 업무 행이 남음. 회원 장부 행 삭제를 막음(V14 `ON DELETE RESTRICT`) | 범위 밖 |
| 기본 인증 | `Authorization` 헤더를 보낼 방법이 없음 | 운영에는 기본 인증이 없음. 스테이징(`auth_basic`, [`deploy/staging/nginx.conf`](../../deploy/staging/nginx.conf))에는 있어서 스테이징에도 그대로 못 씀 | 운영에는 영향 없음 |
| 월 제한 | 서울 기준 이번 달만 지원(주문일 검사) | 업무 API를 부르지 않으므로 해당 없음 | — |

이 절차는 `smoke-member.sh`의 판정 방식 일부만 가져옵니다. 쿠키 값을 가리고 속성만 출력하는 방식과, 상태 코드와 오류 코드를 함께 보는 방식입니다. 스크립트는 고치지 않습니다.

### 1-3. 이메일 인증 화면이 없음 (결정 필요)

- **링크 형식:** 메일 링크는 `<BEONE_MAIL_LINK_BASE_URL>/email-verification?token=<토큰>`입니다([`SmtpAuthMailSender`](../../backend/src/main/java/com/beone/api/auth/application/SmtpAuthMailSender.java)). 토큰은 43자이고 30분 동안 유효하며 1회만 쓸 수 있습니다.
- **링크만 열어서는 인증되지 않음:** 인증은 화면이 토큰을 `POST /api/v1/auth/email-verifications`로 보내야 끝납니다([API 문서](../api/auth.md)).
- **화면이 없음:** `develop` `23bca21`의 frontend에는 이 화면이 없습니다. 운영 웹 `6974880`도 같습니다(둘 사이 `frontend/` 변경 없음). [`AuthEntry.tsx`](../../frontend/src/auth/AuthEntry.tsx)는 `/password-reset`의 토큰만 읽습니다. [`api.ts`](../../frontend/src/auth/api.ts)에도 `email-verifications` 호출이 없습니다.
  - 그래서 브라우저나 메일 앱에서 링크를 열어도 첫 화면만 뜨고, 인증되지 않습니다.
  - 링크를 열면 토큰이 브라우저 방문 기록에도 남습니다. 공개 nginx 접근 로그는 `$uri` 형식이라 query를 남기지 않습니다(public-proxy 2절 `beone_noquery`).

선택지는 두 가지입니다. 실행 전에 Aside와 사용자가 정합니다.

| 방식 | 내용 | 조건 |
|---|---|---|
| **A. 화면 대기** | frontend에 인증 화면이 생긴 뒤 브라우저에서 링크를 열어 인증 | frontend 작업이 먼저 필요. 그때까지 B8 실행을 미룸 |
| **B. 터미널 입력** | 메일 앱에서 링크를 **열지 않고 복사**한 뒤, 4절 M4에서 `read -rs`로 붙여 넣음. 스크립트가 토큰만 떼어 표준 입력으로 API에 보냄 | 토큰을 화면·셸 이력·명령줄 인자·파일에 남기지 않음. 클립보드는 바로 비움 |

B도 "토큰을 출력·로그·문서에 남기지 않는다"는 조건은 지킵니다. 다만 "브라우저나 메일 앱에서 직접 연다"는 방식은 아닙니다. 그래서 B를 쓰려면 승인이 필요합니다.

## 2. 실행 전 조건

모두 충족해야 시작합니다. 하나라도 아니면 실행하지 않습니다.

| 조건 | 확인 방법·기준 |
|---|---|
| 승인 창 | Aside와 사용자가 실행 시각과 끝 시각을 정함. 배포(수동·자동)가 진행 중이 아님. 4-1의 B7까지 끝나 `production.state`가 있음 |
| 운영 이미지와 조사 기준 대조 | 실행 시점에 승인된 실제 운영 이미지의 revision(GitSha)과 ApiDigest를 확인함. 확인 방법은 승인 창의 배포 기록이나 운영 상태 파일입니다. 지금 값은 머리말의 `6974880`과 `sha256:9a887bd8…`입니다. 이어서 저장소에서 `git diff --stat <운영 revision> 23bca21 -- backend/`의 출력이 비어 있는지 봄(지금 `6974880`이면 비어 있음). 비어 있지 않으면 바뀐 backend 파일을 이 문서의 1·4·6절과 다시 대조하기 전에는 실행하지 않음. 새 배포나 다른 운영 실행은 요구하지 않음 |
| Flyway 최신 버전 | 운영 DB의 Flyway 최신 성공 버전이 15임(6-3절 C0과 같은 확인). 15보다 크면 6-2절의 외래키를 새 마이그레이션과 다시 대조하기 전에는 6절 정리를 하지 않음 |
| 메일 발송 | 결정 39-6의 Gmail SMTP 설정이 운영 `.env`에 있음(backend가 떠 있으면 충족. 없으면 기동하지 않음). 이 절차는 최대 2통(가입 1, 재요청 1)을 보냄 |
| 발송 한도·비용 | 사용자가 프로젝트 Gmail 계정의 일일 발송 한도와 남은 양, 추가 비용 여부를 확인함. 값은 계정 종류마다 다르므로 이 문서에 정하지 않음. 실행 기록에 "확인함"만 적음 |
| 대상 메일 주소 | 1개. **사용자가 소유한 전용 테스트 주소**(3절) |
| 인증 방식 | 1-3절의 A 또는 B가 정해짐 |
| 실행자 | 사용자: Mac에서 4절(메일함 소유, 비밀번호 입력). Aside: 서버에서 5절 로그 확인과 6절 정리. 같은 시간에 연락이 됨 |
| 도구 | 사용자 Mac에 `bash`, `curl`, `jq`. 서버에는 `/opt/beone/app/compose.yaml`(프로젝트 `beone-app`) |

### 중단 기준

다음 중 하나라도 생기면 다음 단계로 가지 않습니다. 회원 행이 생겼다면 6절 정리로 갑니다.

- 5xx 응답이나 응답 없음(`000`)
- 각 단계의 "실패 시" 칸에서 중단하라고 한 결과
- 재요청 1회 뒤에도 메일이 오지 않음
- 로그인 잠금(429)
- 승인 창이 끝남
- 운영에 다른 이상이 보임(backend health 실패, 다른 사용자 영향)

## 3. 대상 계정

- **주소:** 사용자가 소유한 전용 테스트 메일 주소 1개를 씁니다.
  - 실제 개인 주소와 팀원 주소는 쓰지 않습니다.
  - 주소는 이 문서·이슈·PR·기록에 쓰지 않습니다. 실행할 때 입력합니다.
  - 소문자로 입력합니다. 서버는 앞뒤 공백을 지우고 소문자로 바꿔 저장합니다.
- **이미 가입된 주소는 쓰지 않음:** 이미 인증된 계정이면 가입 요청에도 메일이 가지 않고, 같은 202가 옵니다([`MemberRegistrationService`](../../backend/src/main/java/com/beone/api/auth/application/MemberRegistrationService.java)). M2에서 이를 걸러냅니다.
- **비밀번호:** 실행할 때 `read -rs`로 입력합니다.
  - 규칙은 11~32자이고 영문·숫자·특수기호를 각각 1개 이상 넣어야 합니다.
  - 화면·파일·로그·셸 이력·명령줄 인자에 남기지 않습니다. 이 계정 밖에서 쓰는 비밀번호는 쓰지 않습니다.

## 4. 단계 (사용자 Mac)

> **실행 검증 상태:** 4-0, M0~M8, 4-2, 4-3, "끝" 블록은 **운영에서 실행 검증되지 않았습니다.** 실행 전에 합성 로컬 검증이 필요합니다(운영 서버와 실제 메일은 쓰지 않음).
> - 작성자가 한 합성 시험: 로컬 가짜 HTTP 서버로 성공·CSRF 실패·네트워크 오류 경로를 돌렸습니다(8절). 시험 출처와 HEAD는 8절에 적었습니다.
> - 8절의 미검증 범위는 실행 전에 Aside나 사용자가 같은 방식으로 다시 확인합니다.

### 4-0. 준비

zsh의 `read` 옵션은 bash와 다릅니다. 그래서 새 bash를 열고 그 안에서 아래 블록들을 차례로 붙여 넣습니다. 블록끼리 변수를 공유하므로 **같은 bash 창**을 끝까지 씁니다.

```bash
bash --norc --noprofile
```

```bash
# 공용 함수. 응답 본문은 $W/body, 헤더는 $W/head에 둔다. 쿠키는 $W/jar, CSRF 헤더는 $W/csrf 파일로만 넘긴다(명령줄 인자에 값 없음)
B=https://beonepay.site
W=$(mktemp -d) && [ -d "$W" ] && chmod 700 "$W" && echo "준비 OK" || echo "준비 실패: 중단"
trap 'rm -f "$W/body" "$W/head" "$W/jar" "$W/csrf"; rmdir "$W"' EXIT   # 만든 파일 4개만 지우고 빈 폴더를 지운다
req() {   # req <메서드> <경로> [-] : '-'이면 표준 입력을 JSON 본문으로 보낸다. 상태 코드를 출력한다
	local a=(-s -o "$W/body" -D "$W/head" -w '%{http_code}' -X "$1" --max-time 30 -c "$W/jar" -b "$W/jar")
	[ "${3:-}" = - ] && a+=(-H 'Content-Type: application/json' --data-binary @-)
	[ -s "$W/csrf" ] && a+=(-H @"$W/csrf")
	rm -f "$W/body" "$W/head"   # 이전 응답이 남아 다음 판정에 섞이지 않게 한다
	local c; c=$(curl "${a[@]}" "$B$2") || c=000   # 연결 실패면 000 하나만 출력한다
	echo "${c:-000}"
}
csrf() {  # 현재 쿠키의 CSRF 토큰을 헤더 파일로 만든다. 토큰은 출력하지 않는다
	rm -f "$W/csrf"
	local st; st=$(req GET /api/v1/auth/csrf)
	[ "$st" = 200 ] || { echo "CSRF 발급 실패(상태 $st)" >&2; return 1; }
	local t; t=$(jq -r '.data.token // empty' "$W/body" 2> /dev/null)
	[ -n "$t" ] || { echo "CSRF 토큰 없음" >&2; return 1; }
	printf 'X-CSRF-TOKEN: %s\n' "$t" > "$W/csrf"
}
code() { jq -r '.code // empty' "$W/body" 2> /dev/null; }
cookies() { grep -i '^set-cookie:' "$W/head" 2> /dev/null | tr -d '\r' | sed -E 's/^[^:]*: *//; s/^([^=;]*)=[^;]*/\1=<값>/'; }
cred() { EM="$EM" PW="$PW" jq -n '{email: env.EM, password: env.PW}'; }   # 값은 jq 환경 변수로만 넘긴다
read -r -p '테스트 메일 주소(소문자, 표시됨·기록 안 함): ' EM
read -rs -p '비밀번호(표시 안 됨): ' PW; echo
[ -n "$EM" ] && [ ${#PW} -ge 11 ] && echo "입력 OK" || echo "입력 확인 필요"
```

- **확인값:** `준비 OK`, `입력 OK`
- **실패 시:** 다시 붙여 넣습니다. `jq`가 없으면 설치한 뒤 다시 합니다.
- 화면에 주소가 보이므로 화면 공유나 녹화는 하지 않습니다.

### 4-1. 단계별 명령

표 안의 `\|`는 Markdown 표 때문에 넣은 것입니다. 원문 파일에서 복사할 때는 `|`로 바꿔 입력합니다(GitHub 화면에서 복사하면 이미 `|`입니다).

- 상태 칸이 `000`이면 네트워크 오류입니다(연결 실패·시간 초과). 해당 단계의 "실패 시" 대신 중단 기준을 따릅니다.
- 상태 칸이 `중단`이고 그 앞에 `CSRF 발급 실패(상태 …)`나 `CSRF 토큰 없음`이 나오면, 요청은 보내지 않은 것입니다. CSRF 발급 단계에서 멈춘 것이므로 같은 명령을 1회만 다시 하고, 다시 실패하면 중단합니다.

| # | 명령(같은 bash 창) | 확인값 | 실패 시 |
|---|---|---|---|
| M0 | `rm -f "$W/csrf"; s=$(req GET /api/v1/me); echo "$s $(code)"` | `401 UNAUTHENTICATED` | 공개 스모크(B7) 상태부터 다시 봄. 중단 |
| M1 | `s=; csrf && s=$(cred \| req POST /api/v1/auth/sign-up -); echo "${s:-중단} $(code)"` | `202`(코드 없음). 메일 1통 발송 | `429 RATE_LIMITED`: 같은 주소의 60초 안 재요청. `grep -i retry-after "$W/head"`의 초만큼 기다린 뒤 1회만 다시. `400 INVALID_REQUEST`: 주소 형식이나 비밀번호 규칙. 4-0부터 다시. `403 CSRF_INVALID`: 같은 명령 1회 다시. 5xx·`000`: 중단 |
| M2 | `s=; csrf && s=$(cred \| req POST /api/v1/auth/login -); echo "${s:-중단} $(code)"` | `403 EMAIL_NOT_VERIFIED`(실패 횟수에 세지 않음, [`MemberLoginService`](../../backend/src/main/java/com/beone/api/auth/application/MemberLoginService.java)) | `200`: 이미 인증된 계정이 있었음. 메일이 가지 않으니 M7로 로그아웃하고 중단. 6절로 정리할지 정함. `401 INVALID_CREDENTIALS`: 이미 있던 미인증 계정의 비밀번호가 다름. 가입 요청은 기존 비밀번호를 바꾸지 않음. 중단하고 6절 |
| M3 | 메일함 확인(아래 4-2) | 30분 안에 제목 `[BeONE] 이메일 인증을 완료해 주세요` 메일 수신 | 4-2 |
| M4 | 1-3절에서 정한 방식으로 인증(아래 4-3) | `204` | `400 AUTH_TOKEN_INVALID`: 만료(30분), 이미 사용, 또는 재요청으로 바뀐 이전 토큰. 재요청은 4-2의 1회 한도 안에서만. 그 뒤에도 실패하면 중단하고 6절 |
| M5 | `s=; csrf && s=$(cred \| req POST /api/v1/auth/login -); echo "${s:-중단} $(code) userId=$(jq -r '.data.userId // empty \| startswith("usr-")' "$W/body" 2> /dev/null)"; cookies` | `200  userId=true`. 쿠키 줄은 `__Host-BEONE_SESSION=<값>; Path=/; Max-Age=2592000; Secure; HttpOnly; SameSite=Lax` 하나이고 `Domain` 없음 | `401`: 비밀번호를 잘못 입력함. `read -rs -p '비밀번호: ' PW; echo`로 다시 입력한 뒤 다시. 15분 동안 실패 5번이면 15분 잠금이므로 2번까지만. `429`: 잠김. 중단하고 6절. `403 EMAIL_NOT_VERIFIED`: M4가 끝나지 않음 |
| M6 | `s=$(req GET /api/v1/me); echo "$s"; jq -c '.data' "$W/body" 2> /dev/null` | `200`과 `{"demoUser":null,"displayName":"내 카드","portfolioType":"REAL","readOnly":false,"cardCount":0,"codefConnected":false}`. 회원 장부는 인증할 때 만들어짐([`MemberUserService`](../../backend/src/main/java/com/beone/api/user/application/MemberUserService.java)) | `401`: M5 쿠키가 저장되지 않음. M5부터 1회 다시. 값이 다르거나 5xx: M7로 로그아웃하고 중단, Aside가 backend 로그 확인 |
| M7 | `s=; csrf && s=$(req POST /api/v1/auth/logout); echo "${s:-중단}"; cookies` | `204`, 쿠키 줄에 `Max-Age=0` | 1회 다시. 그래도 실패하면 세션 행이 남으므로 6절에서 함께 지움 |
| M8 | `rm -f "$W/csrf"; s=$(req GET /api/v1/me); echo "$s $(code)"` | `401 UNAUTHENTICATED` | `200`: 로그아웃이 세션을 지우지 못함. 기록하고 Aside에게 알림 |
| 끝 | `unset PW EM; exit` | bash가 닫히고 `$W`(쿠키 파일)가 지워짐 | 창을 닫아도 됨. 임시 폴더가 남았으면 그 안의 파일 4개와 폴더를 지움 |

### 4-2. 메일 수신 (M3)

- 받은편지함과 스팸함을 봅니다. 보낸 사람은 운영 `BEONE_MAIL_FROM` 주소이고, 본문의 링크는 `https://beonepay.site/email-verification?token=…` 형식입니다.
- 링크를 **클릭하지 않습니다.** 방식 A는 화면이 생긴 뒤에만 엽니다. 방식 B는 메일 앱의 "링크 복사"만 씁니다.
- **10분이 지나도 안 오면**
  - Aside가 서버에서 발송 실패 건수만 확인합니다. 로그에는 가린 주소만 있습니다. 로그 원문은 출력하거나 남기지 않고, 건수만 출력합니다.
    - 로그는 권한 600의 임시 파일에 받습니다. 같은 블록이 끝날 때 지웁니다.
    - 로그 조회가 실패하면 건수를 내지 않고 `조회 실패: 중단`을 출력합니다. 그래서 조회 실패가 "0건"으로 보이지 않습니다.

    ```bash
    ( set -uo pipefail; umask 077
      APP() { sudo docker compose -f /opt/beone/app/compose.yaml -p beone-app --env-file /opt/beone/app/.env "$@"; }
      L=$(mktemp) && [ -f "$L" ] || { echo "MAIL_LOG 임시 파일 실패: 중단"; exit 1; }
      trap 'rm -f "$L"' EXIT
      APP logs --since 30m --no-color backend > "$L" || { echo "MAIL_LOG 조회 실패: 중단"; exit 1; }
      n=$(awk '/auth mail send failed/ {c++} END {print c + 0}' "$L") || { echo "MAIL_LOG 집계 실패: 중단"; exit 1; }
      echo "MAIL_LOG 발송 실패 건수: $n"
    ); echo "종료 $?"
    ```

  - `MAIL_LOG 발송 실패 건수: 0`과 `종료 0`이면 전송은 됐을 수 있습니다. 메일함을 5분 더 봅니다.
  - 건수가 1 이상이면 SMTP 문제입니다(결정 39-6 설정). 재요청하지 않고 중단합니다.
  - `조회 실패`·`임시 파일 실패`·`집계 실패`와 `종료 1`이 나오면 발송 여부를 판단할 수 없습니다. 재요청하지 않고 중단합니다. 표준 오류에는 compose의 오류 메시지만 나오고 로그 원문은 나오지 않습니다.
- **재요청은 1회만** 합니다. 가입 뒤 60초가 지나야 합니다.

  ```bash
  s=; csrf && s=$(EM="$EM" jq -n '{email: env.EM}' | req POST /api/v1/auth/email-verification-requests -); echo "${s:-중단} $(code)"
  ```

  확인값은 `202`입니다. 이전 토큰은 무효가 되므로 새 메일의 링크만 씁니다. 그래도 안 오면 중단하고 6절로 갑니다.

### 4-3. 이메일 인증 (M4)

**방식 A(화면이 생긴 뒤):** 같은 브라우저에서 메일 링크를 열고 화면 안내대로 인증합니다. 그 뒤 터미널로 돌아와 M5부터 합니다.

- 확인값은 화면의 완료 표시입니다. 이 절의 명령은 쓰지 않습니다.
- 화면이 생기면 이 절을 그 화면 기준으로 고칩니다.

**방식 B(승인된 경우만):** 메일 앱에서 링크를 복사한 뒤 아래를 붙여 넣습니다. 링크와 토큰은 표시되지 않습니다.

```bash
read -rs -p '인증 링크 전체를 붙여 넣기(표시 안 됨): ' LK; echo
TK=${LK##*token=}; TK=${TK%%[&#]*}; unset LK
pbcopy < /dev/null   # 클립보드를 비운다
case "$TK" in *[!A-Za-z0-9_-]*|'') ok=0 ;; *) [ ${#TK} -eq 43 ] && ok=1 || ok=0 ;; esac
if [ "$ok" = 1 ]; then s=; csrf && s=$(TK="$TK" jq -n '{token: env.TK}' | req POST /api/v1/auth/email-verifications -); echo "${s:-중단} $(code)"
else echo "토큰 형식이 아님(43자 Base64URL). 링크 복사부터 다시"; fi
unset TK ok
```

- **확인값:** `204`
- **실패 시:** 형식 오류는 복사를 다시 합니다. `400 AUTH_TOKEN_INVALID`는 M4 행을 따릅니다.

## 5. 결과 판정

- M0~M8이 모두 확인값이면 B8 **통과**입니다.
- 하나라도 다르면 **실패**입니다. 어느 단계에서 무엇이 나왔는지(상태 코드·오류 코드)만 기록합니다.
- M3에서 메일이 오지 않은 것은 회원 API가 아니라 메일 발송(N1) 문제로 따로 적습니다.

## 6. 정리 (테스트 계정)

### 6-1. 코드에 있는 삭제·비활성화 경로

**없습니다.**

- 회원 탈퇴·삭제·비활성화 API와 서비스 메서드가 없습니다(`backend/src/main/java`의 auth·user 패키지 확인).
- V14 마이그레이션 주석에도 "탈퇴·보존 정책(D10)은 미결"이라고 적혀 있습니다.

그래서 정리 방법은 둘 중 하나입니다. 실행 전에 Aside와 사용자가 고릅니다.

| 대안 | 내용 | 고려할 점 |
|---|---|---|
| 1. 남겨 두기 | 이 계정을 운영 스모크 전용으로 남김. 다음에는 M5~M8만 함(메일 발송 없음) | 운영 DB에 회원 1행·장부 1행이 남음. 비밀번호는 사용자가 관리 |
| 2. DB에서 삭제 | Aside가 서버에서 6-2절 SQL 실행 | 이메일로 1행만 잡히는지 먼저 확인하고, 한 트랜잭션 안에서 영향 행 수를 확인 |

### 6-2. 관련 테이블과 삭제 순서 (V15 기준)

Flyway 마이그레이션 [`backend/src/main/resources/db/migration`](../../backend/src/main/resources/db/migration)에서 확인한 관계입니다.

| 테이블 | 회원과의 연결 | 삭제 때 동작 | 이 스모크가 만드는 행 |
|---|---|---|---|
| `members`(V11) | 본체. `email` 유일 | — | 1 |
| `auth_tokens`(V11) | `member_id` → `members` | `ON DELETE CASCADE` | 인증하면 지워짐. 인증 전에 멈췄으면 1 |
| `auth_sessions`(V9·V13) | `member_id` → `members`(익명 세션은 NULL) | `ON DELETE CASCADE` | 로그아웃하면 지워짐. 못 했으면 1 |
| `users`(V2·V14) | `member_id` → `members` | `ON DELETE RESTRICT`(먼저 지워야 함) | 인증하면 1(회원 장부 '내 카드') |
| 업무 테이블 | `user_id` → `users`: `user_cards`, `orders`, `planned_spends`, `monthly_states`, `payment_preferences`, `recommendations`, `simulated_payments`, `ledger_transactions`, `idempotency_keys`, `codef_connections`, `codef_syncs`, `codef_held_cards`, `codef_approval_rows`, `codef_performance_references` | 삭제 동작 지정 없음(RESTRICT). 하위 표(`monthly_state_*`, `recommendation_card_bases`, `payment_cancellations`, `ledger_usage_effects`)는 이 표들을 거쳐 연결됨 | 0(업무 API를 부르지 않음) |
| `auth_mail_throttles`, `auth_login_throttles`, `auth_login_failures`(V11·V13) | 외래키 없음. 정규화 이메일의 SHA-256(소문자 16진수)이 키 | — | 메일 요청 기록 1. 실패가 있었으면 실패 기록 |

**순서**는 다음과 같습니다. 업무 행은 0이어야 하고, 0이 아니면 멈춥니다.

```text
auth_sessions(회원 세션) → auth_tokens → users(회원 장부) → members → 이메일 해시 표 3개
```

- 세션과 토큰은 회원을 지우면 함께 지워지지만, 영향 행 수를 보려고 먼저 지웁니다.
- **익명 세션:** 로그인 전 CSRF 발급으로 생긴 익명 세션 행(`member_id` NULL)은 회원과 연결할 수 없습니다. 2시간 동안 쓰지 않으면 만료되고, 해시와 시각만 있으므로 지우지 않습니다.

### 6-3. 서버 명령 (Aside)

서버에서 새 셸을 열고 실행합니다. 이메일은 `read`로 입력하므로 셸 이력과 명령줄 인자에 남지 않습니다. SQL은 표준 입력으로 MySQL에 넘깁니다. 출력에는 이메일을 넣지 않습니다.

```bash
# C0. 준비와 Flyway 이력(최신이 15인지)
APP() { sudo docker compose -f /opt/beone/app/compose.yaml -p beone-app --env-file /opt/beone/app/.env "$@"; }
DB() { APP exec -T mysql bash -c 'mysql --defaults-extra-file=<(printf "[client]\nuser=%s\npassword=%s\n" "$MYSQL_USER" "$MYSQL_PASSWORD") -N -B "$MYSQL_DATABASE"'; }
echo "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success = 1;" | DB
read -r -p '대상 이메일(소문자): ' EM
[[ "$EM" =~ ^[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]+$ ]] && echo "형식 OK" || { echo "형식 아님: 중단"; unset EM; }
```

- **확인값:** `15`, `형식 OK`
- **실패 시:** 15보다 크면 새 마이그레이션의 외래키를 6-2절 표와 다시 대조하기 전에는 진행하지 않습니다. 형식이 틀리면 다시 입력합니다. 형식 검사는 따옴표 같은 SQL 특수문자를 막기 위한 것입니다.

```bash
# C1. 조회만(변경 없음). 기대: members 1, users 0 또는 1, business 0
DB << SQL
SET @em = '$EM'; SET @mid = NULL; SET @uid = NULL;
SELECT member_id INTO @mid FROM members WHERE email = @em;
SELECT user_id INTO @uid FROM users WHERE member_id = @mid AND demo_user IS NULL;
SELECT 'members', COUNT(*) FROM members WHERE email = @em;
SELECT 'users', COUNT(*) FROM users WHERE member_id = @mid;
SELECT 'auth_sessions', COUNT(*) FROM auth_sessions WHERE member_id = @mid;
SELECT 'auth_tokens', COUNT(*) FROM auth_tokens WHERE member_id = @mid;
SELECT 'business', (SELECT COUNT(*) FROM user_cards WHERE user_id = @uid) + (SELECT COUNT(*) FROM orders WHERE user_id = @uid)
  + (SELECT COUNT(*) FROM planned_spends WHERE user_id = @uid) + (SELECT COUNT(*) FROM monthly_states WHERE user_id = @uid)
  + (SELECT COUNT(*) FROM payment_preferences WHERE user_id = @uid) + (SELECT COUNT(*) FROM recommendations WHERE user_id = @uid)
  + (SELECT COUNT(*) FROM simulated_payments WHERE user_id = @uid) + (SELECT COUNT(*) FROM ledger_transactions WHERE user_id = @uid)
  + (SELECT COUNT(*) FROM idempotency_keys WHERE user_id = @uid) + (SELECT COUNT(*) FROM codef_connections WHERE user_id = @uid)
  + (SELECT COUNT(*) FROM codef_syncs WHERE user_id = @uid) + (SELECT COUNT(*) FROM codef_held_cards WHERE user_id = @uid)
  + (SELECT COUNT(*) FROM codef_approval_rows WHERE user_id = @uid) + (SELECT COUNT(*) FROM codef_performance_references WHERE user_id = @uid);
SELECT 'email_hash_rows', (SELECT COUNT(*) FROM auth_mail_throttles WHERE email_hash = SHA2(@em, 256))
  + (SELECT COUNT(*) FROM auth_login_throttles WHERE email_hash = SHA2(@em, 256))
  + (SELECT COUNT(*) FROM auth_login_failures WHERE email_hash = SHA2(@em, 256));
SQL
```

- **확인값:** `members 1`, `users 0` 또는 `1`, `business 0`. 세션·토큰·해시 행은 0 이상입니다.
- **실패 시:**
  - `members 0`이면 지울 것이 없습니다. 해시 표 행만 있으면 C2를 하지 않아도 됩니다.
  - `business`가 0이 아니면 이 절차의 범위를 벗어납니다. 멈추고 사용자에게 알립니다.

```bash
# C2. 삭제(한 트랜잭션). 확인 표 smoke_guard의 CHECK(n = 1)에 어긋나면 그 자리에서 오류로 멈추고, 커밋 없이 연결이 끊겨 전부 되돌아간다
DB << SQL
SET @em = '$EM'; SET @mid = NULL; SET @uid = NULL;
CREATE TEMPORARY TABLE smoke_guard (step VARCHAR(40) NOT NULL, n BIGINT NOT NULL, CONSTRAINT ck_smoke_guard CHECK (n = 1));
START TRANSACTION;
SELECT member_id INTO @mid FROM members WHERE email = @em FOR UPDATE;
INSERT INTO smoke_guard SELECT 'members=1', COUNT(*) FROM members WHERE email = @em;
SELECT user_id INTO @uid FROM users WHERE member_id = @mid AND demo_user IS NULL FOR UPDATE;
INSERT INTO smoke_guard SELECT 'users<=1', COUNT(*) <= 1 FROM users WHERE member_id = @mid;
DELETE FROM auth_sessions WHERE member_id = @mid;   SELECT 'deleted auth_sessions', ROW_COUNT();
DELETE FROM auth_tokens WHERE member_id = @mid;     SELECT 'deleted auth_tokens', ROW_COUNT();
DELETE FROM users WHERE member_id = @mid AND demo_user IS NULL;   SELECT 'deleted users', ROW_COUNT();
DELETE FROM members WHERE member_id = @mid;
SET @n = ROW_COUNT(); SELECT 'deleted members', @n;
INSERT INTO smoke_guard VALUES ('members deleted=1', @n);
DELETE FROM auth_mail_throttles WHERE email_hash = SHA2(@em, 256);   SELECT 'deleted auth_mail_throttles', ROW_COUNT();
DELETE FROM auth_login_throttles WHERE email_hash = SHA2(@em, 256);  SELECT 'deleted auth_login_throttles', ROW_COUNT();
DELETE FROM auth_login_failures WHERE email_hash = SHA2(@em, 256);   SELECT 'deleted auth_login_failures', ROW_COUNT();
COMMIT;
SELECT 'committed', 1;
SQL
echo "종료 $?"
```

- **확인값**
  - 마지막 줄들이 `committed 1`과 `종료 0`입니다.
  - `deleted users`는 C1의 `users` 값과 같습니다. `deleted members`는 1입니다. 세션·토큰·해시 표는 C1 값 이하입니다.
- **실패 시:** `종료`가 0이 아니고 `committed`가 없으면 아무것도 지워지지 않은 것입니다.
  - 오류 3819(`ck_smoke_guard`): 대상이 1행이 아닙니다.
  - 오류 1451(외래키): 업무 행이 있습니다.
  - 원인을 확인하기 전에는 다시 실행하지 않습니다.

```bash
# C3. 확인 후 정리
DB << SQL
SET @em = '$EM';
SELECT 'members', COUNT(*) FROM members WHERE email = @em;
SELECT 'email_hash_rows', (SELECT COUNT(*) FROM auth_mail_throttles WHERE email_hash = SHA2(@em, 256))
  + (SELECT COUNT(*) FROM auth_login_throttles WHERE email_hash = SHA2(@em, 256))
  + (SELECT COUNT(*) FROM auth_login_failures WHERE email_hash = SHA2(@em, 256));
SQL
unset EM
```

- **확인값:** `members 0`, `email_hash_rows 0`
- **확인 방법:** 이 SQL은 테스트용 합성 스키마(V1~V15를 적용한 로컬 MySQL 8.4.11)에서만 확인했습니다. 운영 DB에서는 실행하지 않았습니다(PR 본문의 검증 결과).

## 7. 기록할 것과 기록하지 않을 것

| 기록함 | 기록하지 않음 |
|---|---|
| 실행 일시(UTC), 실행자 역할(사용자·Aside), 승인 창 | 이메일 주소(일부만 가린 형태도 쓰지 않음) |
| 운영 backend 이미지 커밋, Flyway 최신 버전 | 비밀번호 |
| M0~M8 각 단계의 상태 코드·오류 코드와 통과 여부 | 인증 링크와 토큰, 메일 본문 |
| M5 쿠키 속성(값을 가린 `cookies` 출력) | 쿠키 값, CSRF 토큰, `userId`·`member_id` 값 |
| 메일 수신까지 걸린 대략의 시간, 재요청 여부, 발송 한도·비용 확인 여부("확인함") | backend 로그 줄 원문(건수만) |
| 정리 방식(남김·삭제)과 C1·C2 영향 행 수 | 화면 캡처(주소가 보이므로) |

기록은 B8 결과로 Aside가 운영 기록에 남깁니다. 이슈·PR에는 통과 여부와 단계별 상태 코드만 씁니다.

## 8. 합성 검증 기록과 미검증 범위

운영 서버와 실제 메일 없이 문서 블록을 시험한 기록입니다. 실행 전에 Aside나 사용자가 같은 명령으로 다시 확인합니다.

### 8-1. 다시 실행하는 방법

```bash
deploy/scripts/prod-member-smoke-selftest.sh    # 저장소 루트에서. 실패가 있으면 종료 1
```

- **출처:** [`deploy/scripts/prod-member-smoke-selftest.sh`](../../deploy/scripts/prod-member-smoke-selftest.sh). 이 문서의 블록 원문을 그대로 뽑아 실행합니다.
- **바꾸는 것:**
  - 4절: 4-0의 `B=` 줄을 로컬 가짜 API 주소로 바꿉니다. `pbcopy`는 클립보드를 건드리지 않는 함수로 바꿉니다.
  - 4-2 로그 블록: PATH 앞에 가짜 `docker`와 권한 상승 명령(인자를 그대로 실행)을 둡니다.
- **가짜 API:** 스크립트 안의 Python 서버입니다. [API 문서](../api/auth.md)의 상태 코드·오류 코드·쿠키 속성만 흉내 냅니다.
- **필요한 도구:** `bash`, `python3`, `curl`, `jq`, `lsof`

### 8-2. 시험한 경우와 결과

시험한 HEAD는 `ecf1ba7`입니다. macOS의 `/bin/bash` 3.2.57과 curl 8.7.1을 썼습니다. 59개 항목이 모두 통과했습니다(종료 0).

| 대상 | 경우 | 결과 |
|---|---|---|
| 4-2 로그 블록 | 조회 실패(가짜 docker 종료 1) | `MAIL_LOG 조회 실패: 중단`, `종료 1` |
| 4-2 로그 블록 | 매칭 0 | `MAIL_LOG 발송 실패 건수: 0`, `종료 0` |
| 4-2 로그 블록 | 매칭 1 | `MAIL_LOG 발송 실패 건수: 1`, `종료 0` |
| 4-2 로그 블록 | 일부 출력 뒤 실패(종료 2) | `MAIL_LOG 조회 실패: 중단`, `종료 1`(건수를 내지 않음) |
| 4-2 로그 블록 | 네 경우 공통 | 로그 원문 비출력, 임시 파일 권한 600, 블록이 끝나면 삭제 |
| 4절 | 성공 | M0~M8과 재요청·M4(방식 B)가 4-1 확인값과 같음. 따옴표·역슬래시가 든 비밀번호가 JSON으로 그대로 전달됨 |
| 4절 | CSRF 거부(서버가 토큰 불일치로 403) | 각 POST가 `403 CSRF_INVALID` |
| 4절 | CSRF 발급 실패(발급 503) | `CSRF 발급 실패(상태 503)` 뒤 `중단`. 요청을 보내지 않음 |
| 4절 | 네트워크 오류(닫힌 포트) | M0 `000`, 이후 `CSRF 발급 실패(상태 000)` 뒤 `중단` |
| 4절 | 링크 형식 오류 | M4 `토큰 형식이 아님…`, M5 `403 EMAIL_NOT_VERIFIED` |
| 4절 | 모든 경우 공통 | "끝"의 `exit`에서 셸 종료, 임시 폴더(쿠키 파일) 삭제, 비밀번호·토큰·주소 비출력, `jq`·파일 오류 없음 |

- **변이 확인:** 블록을 일부러 망가뜨린 문서로 실행하면 self-test가 종료 1로 실패합니다(`PMS_DOC=<변이 문서>`).
  - 4-2를 이전의 `logs | grep -c` 구조로 되돌리면 10개 항목이 실패합니다.
  - `req`를 이전의 `|| echo 000`으로 되돌리면 3개 항목이 실패합니다(네트워크 오류 때 `000000`).
  - CSRF 발급 실패 때 이전 상태를 다시 출력하던 형태로 되돌리면 2개 항목이 실패합니다.
- **이번 시험으로 고친 결함**
  - 네트워크 오류 때 상태가 `000000`으로 나오던 문제
  - CSRF 발급에 실패해도 직전 단계의 상태를 다시 출력하던 문제
  - 응답 파일이 없을 때 `jq` 오류가 출력되던 문제
- **6-3 정리 SQL:** 이 스크립트에는 들어 있지 않습니다. 로컬 임시 `mysql:8.4.11`에 V1~V15를 적용한 합성 스키마에서 따로 시험했습니다(HEAD `ecf1ba7`).
  - 정상: `committed 1`, 종료 0
  - 회원 없음: 오류 3819, 종료 1, 변경 없음
  - 업무 행 있음: 오류 1451, 종료 1, 전부 되돌림
  - 시험 하네스는 저장소에 넣지 않았습니다.

### 8-3. 미검증 범위

다음은 아직 확인하지 않았습니다. 실행 전에 확인하거나, 실행할 때 확인값과 다르면 중단합니다.

- 운영 `https://beonepay.site`와 실제 backend. 가짜 API는 계약만 흉내 냅니다. 운영의 TLS와 공개 nginx도 거치지 않았습니다.
- 쿠키 처리 차이. 시험은 `http://localhost`에서 했습니다. curl은 localhost를 보안 문맥으로 보고 `Secure`·`__Host-` 쿠키를 저장합니다. 운영의 HTTPS 경로와는 다를 수 있습니다.
- 사람이 직접 입력하는 경우. 입력을 파일에서 넣었으므로 `read -s`가 화면에 입력을 표시하지 않는지는 보지 않았습니다. zsh에서 붙여 넣는 경우와 실제 `pbcopy`도 보지 않았습니다.
- M3(실제 메일 수신)와 방식 A(인증 화면)
- 4-2 블록의 실제 `docker compose logs`, 권한 상승, 서버의 `mktemp`
- 6-3 SQL을 운영 DB에서 실행하는 것
