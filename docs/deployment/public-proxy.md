# 공개 nginx 같은 origin 설정 초안 (`/`·`/api/`)

공개 주소 `https://beonepay.site` 하나에서 웹 화면(`/`)과 회원 API(`/api/`)를 함께 내기 위한 nginx 설정 **초안**입니다. 회원 세션 쿠키가 `__Host-`·`Secure`·`SameSite=Lax`이고 `member`·`prod`에는 CORS 허용이 없으므로, 화면과 API는 같은 origin이어야 합니다([API 웹 클라이언트 규칙](../api.md)).

- **상태:** 초안입니다. 서버에 반영하지 않았습니다. 실제 반영은 승인 ⑧(공개 배포)에 속하고, 메일(N1)·main 승격·운영 DB 백업과 함께 다시 확인합니다(결정 39 "다시 확인할 것").
- **배포 자동화와의 관계(결정 40):** 웹 파일은 자동 배포가 릴리스 폴더에 풀고 `current` 심볼릭 링크를 바꿔 반영합니다([배포 자동화](cd.md)). 그래서 공개 nginx의 정적 루트는 이 링크로 고정하고, 배포마다 nginx 설정을 바꾸지 않습니다. 스테이징은 별도 도메인에 기본 인증을 걸어 같은 구조로 냅니다(2-1절).
- **근거:** 결정 38-7(웹과 `/api`를 같은 HTTPS 주소에 두고, 앱은 `X-Forwarded-*`를 해석하지 않음. [`application-prod.yml`](../../backend/src/main/resources/application-prod.yml)의 `forward-headers-strategy: none`), ROADMAP-next-20261125 P3~P5.
- **결정 필요:** 보안 헤더 값, 요청 제한, 본문 크기는 **N4** 결정 전이라 `<N4: …>` 자리표시로 둡니다. 0이나 임의 값으로 채우지 않습니다.
- **하지 않는 것:** 인증서·certbot 설정 변경, DNS·보안 그룹 변경, 운영 DB 조작. 이 문서에는 실행 명령이 있지만 승인 전에는 실행하지 않습니다.

## 1. 현재 구성과 확인할 것

[백엔드 배포 기반](backend-compose.md#구성)이 적은 서버 구성은 다음과 같습니다.
- `/opt/beone/compose.yaml`: 프로젝트 `beone`, 서비스 `nginx`(80·443)
- `/opt/beone/nginx/`: 설정
- certbot 인증서 자동 갱신

앱은 별도 프로젝트 `beone-app`입니다. [`deploy/compose.yaml`](../../deploy/compose.yaml)에 서비스 `mysql`·`backend`가 있고, 호스트 포트를 공개하지 않습니다. 네트워크를 따로 선언하지 않았으므로 기본 네트워크는 `beone-app_default`입니다.

배포 자동화(결정 40)가 쓰는 웹 폴더는 다음과 같습니다. 서버 배포 스크립트(`deploy.sh`, #226)가 만들고 바꿉니다.

| 환경 | 호스트 폴더 | `current` 링크 대상 | 서빙 주체 |
|---|---|---|---|
| 운영 | `/opt/beone/web/` | `releases/<GitSha 12자리>-<무작위>`(상대 경로) | 공개 nginx(`beone`) |
| 스테이징 | `/opt/beone/staging/web/` | 같은 형식 | 스테이징 프로젝트의 web 컨테이너(컨테이너 안 `/srv/web/current`, 기본 인증도 이 컨테이너가 함, #230). 공개 nginx는 TLS만 맡고 그 컨테이너로 넘김(2-1절) |

- 링크 대상이 **상대 경로**라서, 폴더를 컨테이너 안 다른 경로에 마운트해도 링크가 풀립니다. 절대 경로(`/opt/beone/web/releases/…`)였다면 컨테이너 안에서 깨집니다.
- nginx는 요청마다 경로를 새로 찾습니다(기본값 `open_file_cache off`, `disable_symlinks off`). 그래서 링크를 바꾸면 reload 없이 다음 요청부터 새 릴리스가 나갑니다. `open_file_cache`를 켜려면 이 동작을 다시 확인해야 합니다.

공개 nginx의 실제 `server_name`·정적 루트·`location` 구성은 저장소에 기록이 없습니다. 아래 초안을 쓰기 전에 서버에서 **읽기만** 해서 대조합니다.

```bash
exec bash   # SSM 기본 셸은 sh다
sudo grep -nE 'server_name|listen|location|root|alias|try_files|return|proxy_pass|ssl_' /opt/beone/nginx/conf.d/*.conf
sudo grep -nE 'image|volumes|networks|ports' /opt/beone/compose.yaml
sudo docker network ls --format '{{.Name}}' | grep -E '^beone'
```

## 2. 설정 초안

`<인증서 경로>`는 1절 대조 결과로 채웁니다. 정적 루트는 컨테이너 안 `/srv/beone/web/current`로 고정합니다. 호스트 `/opt/beone/web`을 읽기 전용으로 마운트하는 방법은 3절에 있습니다. 기존 `server` 블록의 `listen`·`ssl_*`·certbot 경로(`/.well-known/acme-challenge/`)는 **그대로 유지**하고, `location`만 아래처럼 바꾸는 것이 목표입니다.

```nginx
# http 문맥(conf.d 파일 최상위)에 둔다. 인증 링크 경로의 query(token)를 접근 로그에 남기지 않는 형식(N2 조건)
log_format beone_noquery '$remote_addr - [$time_local] "$request_method $uri $server_protocol" $status $body_bytes_sent';

server {
    listen 443 ssl;
    server_name beonepay.site;
    # ssl_certificate·ssl_certificate_key: 기존 값 그대로(<인증서 경로>)

    # 운영 웹 릴리스 링크(결정 40). 호스트 /opt/beone/web을 /srv/beone/web으로 읽기 전용 마운트(3절).
    # current는 releases/<sha12>-<무작위>를 가리키는 상대 링크이고 deploy.sh가 원자적으로 바꾼다.
    root /srv/beone/web/current;

    # 보안 헤더(N4 결정 전 자리표시. 값이 정해지기 전에는 넣지 않는다)
    # add_header Strict-Transport-Security "<N4: max-age 등>" always;
    # add_header Content-Security-Policy-Report-Only "<N4: 정책>" always;
    # add_header X-Content-Type-Options "nosniff" always;            # 후보
    # add_header Referrer-Policy "<N4: 예 same-origin>" always;
    # add_header X-Frame-Options "<N4: 예 DENY>" always;

    location / {
        try_files $uri $uri/ /index.html;   # SPA. 화면 경로(/email-verification 등)를 index.html로
    }

    # 새 릴리스가 바로 보이도록 index.html은 매번 다시 확인하게 한다(권장. 해시가 붙은 정적 파일은 그대로 캐시).
    # 보안 헤더(N4)와 별개인 캐시 설정이다. 서버 반영 여부는 승인 ⑧에서 함께 확인한다.
    # location = /index.html { add_header Cache-Control "no-cache" always; }

    # 이메일 인증·비밀번호 재설정 링크(?token=). N2 조건(관리 판단: query 방식 유지)이라 N4와 별개로 넣는다
    location = /email-verification {
        add_header Referrer-Policy "no-referrer" always;
        access_log /var/log/nginx/access.log beone_noquery;   # 로그 경로는 1절 대조 결과의 기존 값
        try_files /index.html =404;
    }
    location = /password-reset {
        add_header Referrer-Policy "no-referrer" always;
        access_log /var/log/nginx/access.log beone_noquery;
        try_files /index.html =404;
    }

    location /api/ {
        proxy_pass http://beone-app-backend:8080;   # 이름은 3절 네트워크 연결 방식에 따른다
        proxy_http_version 1.1;
        proxy_set_header Host              $host;
        # 클라이언트가 보낸 값을 그대로 넘기지 않고 덮어쓴다
        proxy_set_header X-Forwarded-For   $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Forwarded         "";
        client_max_body_size <N4: 본문 크기, 검증 환경은 64k>;
        proxy_read_timeout 30s;
        # limit_req zone=<N4: 인증 경로 요청 제한> ...;   # N4 권장안: 인증 경로만 제한
    }

    location /actuator/ { return 404; }   # 상태 확인 경로는 외부에 내지 않는다
    location = /api     { return 404; }
}
```

- 앱은 `X-Forwarded-*`를 해석하지 않습니다(`forward-headers-strategy: none`). 쿠키의 `Secure`도 요청 scheme과 관계없이 붙습니다. 그래도 로그·후속 변경에 대비해 nginx에서 전달 헤더를 덮어씁니다.
  - 같은 방식을 쓰는 검증 환경 설정(`deploy/verify/nginx.conf`)은 **PR #206에서 추가하며 아직 develop에 병합되지 않았습니다.** 스모크 스크립트(`deploy/scripts/smoke-member.sh`)도 **PR #199로 미병합**입니다.
  - 위조 헤더를 보내도 Set-Cookie가 같다는 S17 관측은 **C2 후보(`ef01309`) 검증 환경**(로컬 사전 스모크와 운영 담당의 서버 루프백 `beone-verify` 실행)에서 나온 것입니다. 공개 서버에 반영됐다는 증거가 아닙니다. 공개 반영 뒤에는 P5에서 다시 확인합니다.
- **N2 조건:** 인증 링크 경로(`/email-verification`, `/password-reset`)는 `Referrer-Policy: no-referrer`를 붙이고, 공개 접근 로그에 이 경로의 query를 남기지 않습니다. 위 초안은 `$request` 대신 `$uri`를 쓰는 로그 형식으로 처리했습니다. 기존 서버 `access_log` 경로와 형식은 1절에서 대조해 맞춥니다.
- 반영하면 지금의 정적 안내 화면은 SPA로 바뀝니다. 안내 화면은 4절 백업이 원복 대상입니다.

## 2-1. 스테이징 server 블록 (기본 인증은 스테이징 web이 함)

결정 40의 스테이징은 develop 병합마다 자동 반영되는 비공개 확인용 환경입니다. 별도 Compose 프로젝트 `beone-staging`(`/opt/beone/staging/`, #230)이 web·backend·DB를 갖습니다. 스테이징 web 컨테이너가 하는 일은 다음과 같습니다([스테이징](staging.md), #230).

| 호스트 포트 | 역할 |
|---|---|
| `127.0.0.1:18082`(컨테이너 8080) | **기본 인증**(`auth_basic`, 서버의 `/opt/beone/staging/htpasswd`). `/`는 `/srv/web/current`(SPA), `/api/`는 스테이징 backend. backend로 `Authorization`을 넘기지 않음. N2 두 경로(`Referrer-Policy: no-referrer`, query 미기록 로그), `/actuator/` 404 |
| `127.0.0.1:18081`(컨테이너 8081) | 인증 없는 배포 스모크 전용. `/`, `/api/v1/me`, `/api/v1/auth/csrf`만 열고 나머지는 404. **공개 nginx가 넘기지 않음** |

공개 nginx는 스테이징 도메인의 TLS만 맡고, 요청 전체를 기본 인증 리스너(18082에 해당하는 8080)로 넘깁니다. 화면과 `/api`가 스테이징 도메인 하나에서 같은 origin이 됩니다.

```nginx
server {
    listen 443 ssl;
    server_name <스테이징 도메인>;                 # 승인 항목(DNS·인증서). 정해지기 전에는 넣지 않는다
    # ssl_certificate·ssl_certificate_key: 스테이징 도메인 인증서(<스테이징 인증서 경로>, certbot 발급은 승인 항목)

    # 기본 인증은 스테이징 web nginx가 한다(#230). 여기서 auth_basic을 또 걸지 않는다.
    # Authorization 헤더도 지우지 않는다. 지우면 스테이징 web의 기본 인증이 항상 401이 된다.
    # backend로 넘기지 않는 것은 스테이징 web이 한다.

    access_log /var/log/nginx/access.log beone_noquery;   # 인증 링크 query를 남기지 않는다(N2). 로그 경로는 1절 대조값
    add_header X-Robots-Tag "noindex, nofollow" always;   # 검색 노출 방지. 보안 헤더는 운영과 같은 N4 결정을 따른다

    location / {
        # 스테이징 web의 기본 인증 리스너(호스트 127.0.0.1:18082 = 컨테이너 8080). 주소 형식은 아래 참고
        proxy_pass http://<스테이징 web 기본 인증 리스너>;
        proxy_http_version 1.1;
        proxy_set_header Host              $host;
        proxy_set_header X-Forwarded-For   $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Forwarded         "";
        client_max_body_size <N4: 본문 크기, 검증 환경은 64k>;
        proxy_read_timeout 30s;
    }
}
```

- **`<스테이징 web 기본 인증 리스너>` 채우는 법:** 스테이징 web의 기본 인증 포트는 호스트 `127.0.0.1:18082`에만 열려 있습니다. 공개 nginx가 컨테이너라면 컨테이너 안의 `127.0.0.1`은 컨테이너 자신이라서 이 포트에 닿지 않습니다. 그래서 1절 대조 결과에 따라 둘 중 하나로 채웁니다(3절).
  - 공개 nginx가 호스트 네트워크(`network_mode: host`)로 돈다면 `127.0.0.1:18082`.
  - 일반 브리지 네트워크라면 공개 nginx를 스테이징 네트워크에 연결하고 `<web 서비스 별칭>:8080`. compose 서비스 이름 `web`은 다른 프로젝트와 겹칠 수 있으므로, 연결할 때 별칭(예 `beone-staging-web`)을 붙여 씁니다.
- **기본 인증 위치를 한 곳으로 둔 이유:** 스테이징 web(#230)이 이미 기본 인증과 `Authorization` 제거를 합니다. 공개 nginx에서도 걸면 계정 파일이 두 곳에 생기고, 공개 nginx가 헤더를 지우면 인증이 깨집니다. 그래서 공개 nginx는 TLS와 전달만 맡습니다. 루프백에서 18082로 직접 붙어도 기본 인증이 걸려 있습니다.
- **같은 origin과 기본 인증:** 브라우저는 같은 origin 요청(`/api/` 포함)에 기본 인증 헤더를 자동으로 붙이므로 화면에서 부르는 API도 막히지 않습니다. 회원 세션 쿠키(`__Host-`)는 스테이징 도메인에만 묶여 운영 쿠키와 섞이지 않습니다.
- **배포 스모크 리스너(18081):** 인증이 없으므로 공개 nginx에서 절대 넘기지 않습니다. `deploy.sh`가 서버 안에서만 씁니다.
- **운영과의 차이:** 운영 블록(2절)은 공개 nginx가 정적 파일을 직접 서빙하고 `/api/`만 backend로 넘깁니다. 스테이징은 비공개 환경을 한 덩어리로 넘겨, 공개 nginx 설정을 배포마다 바꾸지 않게 했습니다.

## 3. 두 Compose 프로젝트 연결

공개 nginx(`beone`)가 `beone-app`의 backend에 닿아야 합니다. 스테이징을 쓰면 `beone-staging`의 web 컨테이너에도 닿아야 합니다. 방법은 승인 ⑧에서 정합니다.

공개 nginx 컨테이너는 호스트 루프백(`127.0.0.1:18081` 등)에 직접 닿지 않습니다. 컨테이너 안의 `127.0.0.1`은 컨테이너 자신이기 때문입니다. 그래서 아래처럼 Docker 네트워크로 연결합니다.

| 안 | 내용 | 영향 |
|---|---|---|
| A | `beone`의 nginx 서비스에 외부 네트워크 `beone-app_default`를 추가하고, `proxy_pass http://backend:8080`(서비스 이름) | `/opt/beone/compose.yaml` 수정과 nginx 컨테이너 재생성. 짧은 중단 |
| B | 전용 외부 네트워크(예 `beone-edge`)를 만들고 두 프로젝트를 모두 연결, backend에 별칭 부여 | 두 프로젝트의 compose 수정과 재생성 |

어느 안이든 backend의 호스트 포트는 계속 공개하지 않습니다. 스테이징을 쓰면 같은 방식으로 `beone-staging` 네트워크(또는 전용 네트워크)에도 연결하고, 2-1절의 `<스테이징 web 기본 인증 리스너>`를 그 별칭과 컨테이너 포트 8080으로 채웁니다. 공개 nginx가 호스트 네트워크로 돈다면 네트워크 연결 없이 `127.0.0.1:18082`를 씁니다.

**웹 릴리스 폴더 마운트(두 안 공통):** 공개 nginx 서비스에 다음 볼륨을 더합니다. 읽기 전용이라 nginx가 릴리스 폴더를 바꿀 수 없습니다.

```yaml
# /opt/beone/compose.yaml의 nginx 서비스(초안, 승인 ⑧에서 반영)
    volumes:
      - /opt/beone/web:/srv/beone/web:ro
      # 스테이징 기본 인증 파일은 공개 nginx에 마운트하지 않는다(스테이징 web이 갖는다, 2-1절)
```

- `current`가 상대 링크라 컨테이너 안 `/srv/beone/web/current`에서도 `releases/…`로 풀립니다.
- 볼륨 추가는 nginx 컨테이너 재생성이 필요합니다(짧은 중단). 3절 네트워크 변경과 한 번에 합니다.

## 4. 반영·원복 절차 (승인 ⑧)

| # | 단계 | 확인값 | 실패 시 |
|---|---|---|---|
| P3 | 설정 백업: `sudo tar -czf /opt/beone-backup/pre-mvp-<UTC>.tgz /opt/beone/compose.yaml /opt/beone/nginx/conf.d <기존 정적 루트>`(지금의 안내 화면 위치, 1절 대조값) | tgz 존재, 크기 > 0 | 진행 안 함 |
| P4 | `prod` backend 기동과 웹 첫 배치. 운영 첫 배포는 승인 항목이며, 자동 배포(`deploy.sh production`, [배포 자동화](cd.md))나 같은 스크립트의 수동 실행으로 `/opt/beone/web/current`를 만든다 | `beone-app` healthy, `/opt/beone/web/current/index.html` 존재 | 앱·링크만 원복 |
| P5-1 | 설정 수정 → `sudo docker compose -f /opt/beone/compose.yaml -p beone exec nginx nginx -t` | `syntax is ok`, `test is successful` | 파일을 백업본으로 되돌림 |
| P5-2 | `sudo docker compose -f /opt/beone/compose.yaml -p beone exec nginx nginx -s reload` | 아래 확인 통과 | 원복 |
| P5-3 | 확인: `curl -sI https://beonepay.site/` 200, `curl -s -o /dev/null -w '%{http_code}' https://beonepay.site/api/v1/me` 401, `https://beonepay.site/actuator/health` 404, `http://` 301, certbot 갱신 타이머 유지, `curl -sI 'https://beonepay.site/email-verification?token=x'`에 `Referrer-Policy: no-referrer`, 접근 로그의 해당 줄에 `token=` 없음 | 모두 기대값 | 원복 |
| P5-4 | **준비 완료 단계 아님(TODO).** 회원 API 스모크는 공개용 실메일 인증 스모크를 별도로 구현·검증한 뒤에 정한다. 현재 `smoke-member.sh`(#199, 미병합)는 `member` 검증 환경 전용이다. 실메일 대신 검증 DB T-a 훅을 전제로 하고, 운영 DB에는 T-a를 쓰지 않으므로 공개 `prod`에 그대로 실행하지 않는다 | 공개용 스모크가 준비되면 기대값을 정한다 | — |

**원복**

```bash
exec bash
sudo tar -xzf /opt/beone-backup/pre-mvp-<UTC>.tgz -C /
PUB='sudo docker compose -f /opt/beone/compose.yaml -p beone'
$PUB exec nginx nginx -t && $PUB exec nginx nginx -s reload
curl -sI https://beonepay.site/ | head -1    # 200, 이전 안내 화면
```

3절에서 컨테이너를 재생성했다면, 백업한 `/opt/beone/compose.yaml`로 `sudo docker compose -f /opt/beone/compose.yaml -p beone up -d nginx`를 다시 실행합니다.

## 5. 남은 결정

| 항목 | 상태 |
|---|---|
| N4 보안 헤더·요청 제한·본문 크기 | 결정 대기. 권장안은 인증 경로만 제한, HSTS를 짧게 시작, CSP는 Report-Only로 먼저(ROADMAP 8절) |
| 프로젝트 연결 방식(3절 A·B) | 승인 ⑧에서 결정 |
| 공개 환경 스모크의 회원 인증 | TODO. 실메일(N1, 결정 39-6) 구현 뒤 공개용 실메일 인증 스모크를 별도로 구현·검증한다. 운영 DB에는 T-a를 쓰지 않음 |
| 검증 환경 근거(#206·#199) | 미병합. 병합되면 이 문서의 참조를 병합된 경로로 확정 |
| 스테이징 도메인·인증서·기본 인증 값 | 승인 항목(결정 40). 정해지기 전에는 2-1절 `<스테이징 도메인>`·계정 파일을 넣지 않음 |
| `<스테이징 web 기본 인증 리스너>` | 스테이징 web(#230)의 기본 인증 리스너. 공개 nginx가 호스트 네트워크면 `127.0.0.1:18082`, 브리지면 스테이징 네트워크 연결 뒤 `<별칭>:8080`(1절 대조·3절 연결 방식에 따름) |
| `index.html` 캐시 설정 | 권장(2절 주석). 승인 ⑧에서 반영 여부 확인 |
