# 공개 nginx 보안 헤더·요청 제한 (N4 표준안, T6)

공개 nginx(프로젝트 `beone`, `/opt/beone/nginx/`)에 보안 헤더와 요청 제한을 넣는 설정 조각과 반영 절차입니다(2026-11-27 사용자 결정). [공개 nginx 설정 초안](public-proxy.md) 2절의 `<N4: …>` 자리표시 중 보안 헤더와 요청 제한을 이 표준안으로 채웁니다. 본문 크기는 기존대로 64k입니다.

- **상태:** 조각과 런북만 준비했습니다. **서버에는 반영하지 않았습니다.** 반영은 운영 담당(Aside)이 사용자 확인 뒤 스테이징 → 운영 순서로 합니다.
- **로컬 확인:** [`deploy/scripts/public-nginx-selftest.sh`](../../deploy/scripts/public-nginx-selftest.sh)가 로컬 nginx 이미지로 `nginx -t`, 헤더, 429 경계를 확인합니다. 이 PR 작성 세션에서는 Docker 실행 권한이 없어 **실행하지 못했고 관리자 검증에서 실행**합니다.
- **하지 않는 것:** CSP 본체(`script-src` 등), 인증서·DNS 변경, 앱 코드 변경. 서버 식별자·IP·계정은 이 문서와 출력 기록에 남기지 않습니다.

## 1. 표준안

### 1-1. 보안 헤더

| 헤더 | 값 | 비고 |
|---|---|---|
| `Strict-Transport-Security` | `max-age=31536000` | 1년. `includeSubDomains`는 결정 필요(5절) |
| `X-Content-Type-Options` | `nosniff` | |
| `X-Frame-Options` | `SAMEORIGIN` | 다른 사이트의 iframe 삽입 차단 |
| `Content-Security-Policy` | `frame-ancestors 'self'` | 위와 같은 목적의 표준 헤더. **이 지시어만** 보내고 CSP 본체는 넣지 않음 |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | 업스트림이 이미 정했으면 덧붙이지 않음. 인증 링크 경로는 `no-referrer` 유지(N2) |

백엔드는 이 헤더들을 보내지 않습니다(Spring Security 미사용). 스테이징 web은 인증 링크 두 경로에만 `Referrer-Policy: no-referrer`를 보냅니다([`deploy/staging/nginx.conf`](../../deploy/staging/nginx.conf)).

### 1-2. 요청 제한

| 대상 | 제한 | 초과 응답 |
|---|---|---|
| 인증·가입·메일 요청 6경로(아래) | IP당 분당 10회, 초과분 5회까지 즉시 허용(`burst=5 nodelay`). 일반 `/api` 제한도 함께 셈 | 429, `Retry-After: 6` |
| 그 밖의 `/api/` | IP당 초당 10회, 초과분 20회까지 즉시 허용(`burst=20 nodelay`) | 429, `Retry-After: 1` |

인증 제한 경로(백엔드 auth 컨트롤러와 [인증 API 명세](../api/auth.md) 기준, 모두 POST):

| 경로 | 이유 |
|---|---|
| `/api/v1/auth/sign-up` | 가입(메일 발송) |
| `/api/v1/auth/email-verification-requests` | 인증메일 재요청(메일 발송) |
| `/api/v1/auth/password-reset-requests` | 재설정 요청(메일 발송) |
| `/api/v1/auth/login` | 로그인(비밀번호 대입) |
| `/api/v1/auth/email-verifications` | 인증 완료(토큰 대입) |
| `/api/v1/auth/password-resets` | 재설정 완료(토큰 대입) |

`GET /api/v1/auth/csrf`·`GET /api/v1/auth/session`·`POST /api/v1/auth/logout`은 화면이 자주 부르므로 일반 `/api` 제한만 받습니다.

- **429 응답 형식:** 공통 API 오류 형식과 같습니다(`{"code":"RATE_LIMITED","message":"요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.","errors":[]}`, `Content-Type: application/json; charset=utf-8`, `Cache-Control: no-store`, 보안 헤더 포함). 코드·문구는 백엔드 인증 API의 `RATE_LIMITED`([인증 오류 코드](../api/auth.md#인증-오류-코드))와 같습니다.
- **백엔드 429는 그대로:** 백엔드의 메일 요청 60초 제한·로그인 실패 잠금 429(`Retry-After` 남은 초)는 nginx가 바꾸지 않습니다(`proxy_intercept_errors` 기본 off라 `error_page`가 걸리지 않음).
- **기준 IP:** 공개 nginx가 직접 받은 접속 주소(`$binary_remote_addr`)입니다. 공개 nginx 앞에 다른 프록시·CDN이 없으므로 `real_ip`를 설정하지 않고 `X-Forwarded-For`도 믿지 않습니다. 단 Docker가 접속 주소를 게이트웨이 주소로 바꾸는 구성이면 모든 사용자가 한 주소를 나눠 쓰게 되므로, 반영 전에 2-1로 확인합니다.
- **스테이징:** 스테이징 web(`beone-staging`)은 공개 nginx 뒤에 있어 접속 주소가 항상 공개 nginx입니다. 그래서 요청 제한은 **공개 nginx의 `staging.conf`에만** 걸고 스테이징 web에는 걸지 않습니다. 영역은 운영과 공유하므로 같은 IP는 두 도메인의 횟수를 합산합니다.

### 1-3. 파일

저장소 [`deploy/public-nginx/conf.d/`](../../deploy/public-nginx/conf.d/00-beone-security.conf)의 구조를 서버 `/opt/beone/nginx/conf.d/`(컨테이너 안 `/etc/nginx/conf.d/`)에 그대로 둡니다.

| 파일 | 문맥 | 내용 |
|---|---|---|
| `00-beone-security.conf` | http(`conf.d/*.conf`로 자동 로드) | 요청 제한 영역 2개(`beone_auth`·`beone_api`), Referrer-Policy 기본값 `map`. 이름이 `00-`이라 server 파일보다 먼저 읽힘 |
| `beone/security-headers.conf` | server·location에 include | HSTS·nosniff·X-Frame-Options·CSP `frame-ancestors` |
| `beone/referrer-policy.conf` | server에 include | Referrer-Policy 기본값(업스트림 값이 있으면 생략) |
| `beone/rate-limited.conf` | server에 한 번 include | 429 응답 named location 2개 |
| `beone/limit-auth.conf` | 인증 경로 location에 include | 인증·일반 영역 둘 다, 초과 시 `@beone_auth_limited` |
| `beone/limit-api.conf` | `/api/` location에 include | 일반 영역, 초과 시 `@beone_api_limited` |

`beone/` 하위 폴더는 `conf.d/*.conf` 자동 로드 대상이 아니라서 include한 곳에만 적용됩니다.

- **add_header 상속 주의:** nginx의 `add_header`는 하위 location이 자기 `add_header`를 하나라도 두면 상위(server) 것이 모두 사라집니다. 그래서 인증 링크 location(`no-referrer`)과 429 응답 location은 `security-headers.conf`를 다시 include합니다. 앞으로 `add_header`가 있는 location(예: `index.html`의 `Cache-Control`)을 더할 때도 같이 include합니다.
- **Referrer-Policy 중복 방지:** 인증 링크 location은 `referrer-policy.conf`를 include하지 않고 `no-referrer`만 붙입니다. 스테이징은 업스트림(스테이징 web)이 보낸 `no-referrer`가 있으면 `map`이 빈 값을 줘서 기본값을 붙이지 않습니다. 같은 응답에 Referrer-Policy가 두 개면 브라우저가 뒤의 값을 쓰므로 `no-referrer`가 무력해질 수 있기 때문입니다.

## 2. 반영 전 확인 (읽기만, Aside)

### 2-1. 서버 구성 대조

```bash
exec bash   # SSM 기본 셸은 sh다
# conf.d가 컨테이너의 /etc/nginx/conf.d로 마운트되는지, 이름 충돌이 없는지
sudo grep -nE 'conf\.d|volumes' /opt/beone/compose.yaml
sudo grep -rnE 'limit_req|beone_auth|beone_api|beone_referrer_policy|upstream_http_referrer_policy|Strict-Transport-Security|X-Frame-Options' /opt/beone/nginx/conf.d/ || echo "충돌 후보 없음"
sudo docker compose -f /opt/beone/compose.yaml exec -T nginx nginx -v
```

- 이미 같은 이름의 영역·변수나 같은 헤더가 있으면 반영을 멈추고 관리자에게 알립니다.

접속 주소가 실제 사용자 주소인지 확인합니다. **주소 자체는 출력하지 않고** 사설 대역·공인 대역 개수만 셉니다.

```bash
# block: ip-shape   (최근 접근 로그의 접속 주소 형태만 센다. 주소는 출력하지 않음)
( set -euo pipefail
sudo docker compose -f /opt/beone/compose.yaml logs --no-color --since 24h nginx 2>/dev/null \
	| awk '{ for (i = 1; i <= NF; i++) if ($i ~ /^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$/) { print $i; break } }' \
	| awk '/^(10\.|172\.(1[6-9]|2[0-9]|3[01])\.|192\.168\.|127\.)/ { p[$0] = 1; next } { q[$0] = 1 }
		END { np = 0; nq = 0; for (k in p) np++; for (k in q) nq++; print "private=" np " public=" nq }'
)
```

| 결과 | 판단 |
|---|---|
| `public`이 여러 개, `private`가 0 또는 소수 | 실제 사용자 주소로 셈. 진행 |
| `public=0`이고 `private`가 1~2개 | Docker가 주소를 바꾸는 구성. 요청 제한을 넣으면 모든 사용자가 한 버킷을 씀. **요청 제한은 반영하지 말고** 보안 헤더만 반영할지 관리자에게 확인 |

### 2-2. 백업

```bash
# block: backup   (경로만 출력. 이후 단계는 같은 셸에서 B를 쓴다)
B=/opt/beone/nginx/backup-t6-$(date +%Y%m%d%H%M%S)
sudo mkdir -p "$B" && sudo cp -a /opt/beone/nginx/conf.d "$B/" && echo "백업: $B"
```

## 3. 스테이징 먼저 (`staging.conf`)

### 3-1. 조각 배치

저장소 `deploy/public-nginx/conf.d/00-beone-security.conf`와 `deploy/public-nginx/conf.d/beone/` 폴더를 서버 `/opt/beone/nginx/conf.d/`에 같은 구조로 둡니다(권한은 기존 conf 파일과 같게). 이 단계만으로는 server 블록이 조각을 include하지 않으므로 동작이 바뀌지 않습니다(영역 정의와 `map`만 추가).

### 3-2. `staging.conf` 수정

[공개 nginx 2-1절](public-proxy.md#2-1-스테이징-server-블록-기본-인증은-스테이징-web이-함)의 server 블록에 다음을 더합니다. 기존 `listen`·`ssl_*`·`access_log`·`X-Robots-Tag`·`location /`의 전달 설정은 그대로 둡니다. 두 location의 전달 설정은 기존 `location /`과 **같은 줄**(resolver·변수 `proxy_pass`·`proxy_set_header`·`client_max_body_size`·`proxy_read_timeout`)을 복사합니다.

```nginx
    # server 문맥(기존 add_header X-Robots-Tag 옆)
    include /etc/nginx/conf.d/beone/security-headers.conf;
    include /etc/nginx/conf.d/beone/referrer-policy.conf;
    include /etc/nginx/conf.d/beone/rate-limited.conf;

    # 인증·가입·메일 요청(10r/m). 정규식 location은 /api/ 접두사보다 먼저 고른다
    location ~ ^/api/v1/auth/(sign-up|email-verification-requests|email-verifications|login|password-reset-requests|password-resets)$ {
        include /etc/nginx/conf.d/beone/limit-auth.conf;
        # 아래는 기존 location /의 전달 설정과 같은 줄
        resolver 127.0.0.11;
        set $beone_staging_web http://beone-staging-web-1:8080;
        proxy_pass $beone_staging_web;
        proxy_http_version 1.1;
        proxy_set_header Host              $host;
        proxy_set_header X-Forwarded-For   $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Forwarded         "";
        client_max_body_size 64k;
        proxy_read_timeout 30s;
    }

    # 일반 API(10r/s)
    location /api/ {
        include /etc/nginx/conf.d/beone/limit-api.conf;
        # 기존 location /의 전달 설정과 같은 줄(위와 동일)
    }
```

- 스테이징의 429 응답에는 `X-Robots-Tag`가 붙지 않습니다(429 location이 자기 `add_header`를 가짐). 429 본문은 색인 대상이 아니라 그대로 둡니다.

### 3-3. 검사·반영

```bash
sudo docker compose -f /opt/beone/compose.yaml exec -T nginx nginx -t        # 실패하면 reload하지 않고 7절로 되돌린다
sudo docker compose -f /opt/beone/compose.yaml exec -T nginx nginx -s reload
```

### 3-4. 스테이징 확인

헤더(기본 인증 없이도 401 응답에 붙음):

```bash
curl -sI https://staging.beonepay.site/ | grep -iE '^(HTTP|strict-transport|x-content-type|x-frame|content-security|referrer-policy|x-robots)'
```

| 기대 |
|---|
| `HTTP/… 401`(기본 인증), `Strict-Transport-Security: max-age=31536000`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: SAMEORIGIN`, `Content-Security-Policy: frame-ancestors 'self'`, `Referrer-Policy: strict-origin-when-cross-origin` 1개, `X-Robots-Tag` |

화면 흐름(사용자, 브라우저): 기본 인증 뒤에서 **가입 → 인증 메일 링크 열기 → 로그인 → 로그아웃 → 비밀번호 재설정 요청**을 한 번씩 하고 정상 동작을 확인합니다. 인증 링크(`/email-verification?token=…`)를 연 탭의 개발자 도구 Network에서 문서 응답의 `Referrer-Policy`가 `no-referrer` **하나**인지 봅니다.

429 경계(운영 담당 또는 사용자, 계정·메일에 영향 없음). 기본 인증 없이 보내도 요청 제한은 공개 nginx가 먼저 세므로 백엔드까지 가지 않습니다. 실행한 IP는 약 1분 동안 인증 경로가 막히므로 화면 확인을 먼저 끝낸 뒤 합니다.

```bash
# block: stg-429   (인증 경로 연속 7회: 401×6 뒤 429. 일반 API 연속 40회: 401 뒤 429)
( set -euo pipefail
u=https://staging.beonepay.site
codes=""; for i in $(seq 7); do codes="$codes $(curl -s -o /dev/null -w '%{http_code}' -X POST "$u/api/v1/auth/login")"; done
echo "auth:$codes"
curl -s -D - -o - -X POST "$u/api/v1/auth/login" | grep -iE '^(HTTP|retry-after|content-type|cache-control)|RATE_LIMITED'
sleep 7
n401=0; n429=0
for i in $(seq 40); do case "$(curl -s -o /dev/null -w '%{http_code}' "$u/api/v1/me")" in 401) n401=$((n401+1)) ;; 429) n429=$((n429+1)) ;; esac; done
echo "api: 401=$n401 429=$n429"
)
```

| 출력 | 기대 |
|---|---|
| `auth:` | `401 401 401 401 401 401 429`(기본 인증 401 여섯 번 뒤 nginx 429) |
| 429 응답 | `HTTP/… 429`, `Retry-After: 6`, `Content-Type: application/json; charset=utf-8`, `Cache-Control: no-store`, 본문에 `RATE_LIMITED` |
| `api:` | `401`이 21~30회 안팎, `429`가 1회 이상(네트워크 지연에 따라 통과 수가 조금 늘 수 있음) |

이상하면 7절로 되돌립니다. 스테이징이 정상이면 운영으로 갑니다.

## 4. 운영 (`default.conf`)

운영 server 블록(실제 파일 이름은 2-1 대조 결과, [공개 nginx 2절](public-proxy.md#2-설정-초안) 구성)에 다음을 더합니다. 기존 `listen`·`ssl_*`·certbot 경로·`root`·`location /`·`/actuator/` 404는 그대로 둡니다.

```nginx
    # server 문맥
    include /etc/nginx/conf.d/beone/security-headers.conf;
    include /etc/nginx/conf.d/beone/referrer-policy.conf;
    include /etc/nginx/conf.d/beone/rate-limited.conf;

    # 인증 링크 경로(N2): 기존 add_header no-referrer·access_log·try_files는 그대로 두고 보안 헤더 include만 더한다
    location = /email-verification {
        include /etc/nginx/conf.d/beone/security-headers.conf;
        add_header Referrer-Policy "no-referrer" always;
        # (기존 access_log·try_files 그대로)
    }
    location = /password-reset {
        include /etc/nginx/conf.d/beone/security-headers.conf;
        add_header Referrer-Policy "no-referrer" always;
        # (기존 access_log·try_files 그대로)
    }

    # 인증·가입·메일 요청(10r/m). 전달 설정은 기존 location /api/와 같은 줄을 복사한다
    location ~ ^/api/v1/auth/(sign-up|email-verification-requests|email-verifications|login|password-reset-requests|password-resets)$ {
        include /etc/nginx/conf.d/beone/limit-auth.conf;
        # (기존 location /api/의 proxy_pass·proxy_set_header·client_max_body_size·proxy_read_timeout)
    }

    # 기존 location /api/ 안 맨 앞에 한 줄 추가
    location /api/ {
        include /etc/nginx/conf.d/beone/limit-api.conf;
        # (기존 줄 그대로)
    }
```

- 운영 server 블록에 `add_header`를 가진 다른 location(예: `index.html`의 `Cache-Control`)이 있으면 그 안에도 `security-headers.conf`와 `referrer-policy.conf`를 include합니다.
- 3-3과 같이 `nginx -t` → `reload`.

### 4-1. 운영 확인

```bash
curl -sI https://beonepay.site/ | grep -iE '^(HTTP|strict-transport|x-content-type|x-frame|content-security|referrer-policy)'
curl -sI 'https://beonepay.site/email-verification?token=x' | grep -iE '^(HTTP|strict-transport|referrer-policy)'
deploy/scripts/smoke-public.sh https://beonepay.site    # 저장소 경로 기준. 기존 공개 스모크
```

| 확인 | 기대 |
|---|---|
| `/` | `200`, 보안 헤더 4개, `Referrer-Policy: strict-origin-when-cross-origin` 1개 |
| `/email-verification?token=x` | `200`, `Referrer-Policy: no-referrer` **1개**, HSTS 있음 |
| 공개 스모크 | 통과(`/` 200, `/api/v1/me` 401, `/actuator` 404, CSRF 쿠키 속성) |

429 경계는 3-4 `stg-429`의 `u`를 `https://beonepay.site`로 바꿔 실행합니다. 운영 로그인은 CSRF 없이 보내므로 기대값은 `403 403 403 403 403 403 429`(CSRF 거부 여섯 번 뒤 nginx 429)이고, 일반 API는 `401` 뒤 `429`입니다. 계정·메일에는 영향이 없습니다. 실행한 IP는 약 1분 동안 운영 인증 경로가 막히므로 사용자에게 미리 알리고 사용자가 적은 시간에 합니다.

## 5. 결정 필요 (권장안)

| 항목 | 권장안 |
|---|---|
| HSTS `includeSubDomains` | **넣지 않음**(현재). `beonepay.site`의 모든 하위 도메인이 HTTPS인지 확인한 뒤 넣는다. `preload`도 넣지 않음 |
| X-Frame-Options와 CSP `frame-ancestors` | **둘 다** 보냄(현재). 최신 브라우저는 CSP, 오래된 브라우저는 X-Frame-Options를 따른다. Android WebView는 최상위 문서로 열어 영향 없음 |
| `burst` | 인증 5, 일반 20(현재). 시연장 와이파이처럼 여러 사용자가 한 공인 IP를 쓰면 인증 경로가 분당 10회+5회로 막힐 수 있다. 시연 당일 동시 가입 인원이 많으면 인증 `burst`를 늘리거나 시연 동안만 인증 제한을 완화하는 안을 사용자와 정한다 |
| 인증 제한 경로 | 6경로(현재). 메일 요청 3개 + 로그인 + 토큰 제출 2개. 토큰 제출 2개를 빼려면 결정 |
| `Retry-After` | 인증 6초, 일반 1초(현재, 영역의 재충전 간격) |
| Docker가 접속 주소를 바꾸는 구성일 때 | 요청 제한을 반영하지 않고 보안 헤더만 반영(2-1 판단) |

## 6. 운영 영향과 한계

- 요청 제한은 IP 단위입니다. 같은 IP를 쓰는 사용자들은 횟수를 나눠 씁니다. 계정 단위 제한은 백엔드(메일 60초, 로그인 실패 잠금)가 따로 합니다.
- 영역은 nginx 메모리에 있어 reload 뒤에도 유지되지만, 컨테이너를 다시 만들면 초기화됩니다.
- 헤더·제한은 공개 nginx 응답에만 적용됩니다. 루프백 포트(스테이징 18081·18082, 운영 backend)는 대상이 아닙니다.

## 7. 되돌리기

`nginx -t`가 실패했거나 확인이 기대와 다르면 2-2 백업으로 되돌립니다. 새로 둔 조각은 지우지 않고 백업 폴더로 옮깁니다.

```bash
# block: restore   (같은 셸의 B, 또는 B=<2-2에서 출력한 경로>)
( set -euo pipefail
[ -n "${B:-}" ] && [ -d "$B/conf.d" ] || { echo "중단: 백업 경로 B를 정한다" >&2; exit 1; }
sudo mkdir -p "$B/removed"
for f in /opt/beone/nginx/conf.d/00-beone-security.conf /opt/beone/nginx/conf.d/beone; do
	[ ! -e "$f" ] || [ -e "$B/conf.d/${f##*/}" ] || sudo mv "$f" "$B/removed/"
done
sudo cp -a "$B/conf.d/." /opt/beone/nginx/conf.d/
sudo docker compose -f /opt/beone/compose.yaml exec -T nginx nginx -t
sudo docker compose -f /opt/beone/compose.yaml exec -T nginx nginx -s reload
echo RESTORED
)
```

되돌린 뒤 4-1의 공개 스모크와 스테이징 `curl -sI`로 원래 상태(보안 헤더 없음)를 확인합니다.
