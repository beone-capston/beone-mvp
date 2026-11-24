# 공개 nginx 같은 origin 설정 초안 (`/`·`/api/`)

공개 주소 `https://beonepay.site` 하나에서 웹 화면(`/`)과 회원 API(`/api/`)를 함께 내기 위한 nginx 설정 **초안**입니다. 회원 세션 쿠키가 `__Host-`·`Secure`·`SameSite=Lax`이고 `member`·`prod`에는 CORS 허용이 없으므로, 화면과 API는 같은 origin이어야 합니다([API 웹 클라이언트 규칙](../api.md)).

- **상태:** 초안입니다. 서버에 반영하지 않았습니다. 실제 반영은 승인 ⑧(공개 배포)에 속하고, 메일(N1)·main 승격·운영 DB 백업과 함께 다시 확인합니다(결정 39 "다시 확인할 것").
- **근거:** 결정 38-7(웹과 `/api`를 같은 HTTPS 주소에 두고, 앱은 `X-Forwarded-*`를 해석하지 않음. [`application-prod.yml`](../../backend/src/main/resources/application-prod.yml)의 `forward-headers-strategy: none`), ROADMAP-next-20261125 P3~P5.
- **결정 필요:** 보안 헤더 값, 요청 제한, 본문 크기는 **N4** 결정 전이라 `<N4: …>` 자리표시로 둡니다. 0이나 임의 값으로 채우지 않습니다.
- **하지 않는 것:** 인증서·certbot 설정 변경, DNS·보안 그룹 변경, 운영 DB 조작. 이 문서에는 실행 명령이 있지만 승인 전에는 실행하지 않습니다.

## 1. 현재 구성과 확인할 것

[백엔드 배포 기반](backend-compose.md#구성)이 적은 서버 구성은 다음과 같습니다.
- `/opt/beone/compose.yaml`: 프로젝트 `beone`, 서비스 `nginx`(80·443)
- `/opt/beone/nginx/`: 설정
- certbot 인증서 자동 갱신

앱은 별도 프로젝트 `beone-app`입니다. [`deploy/compose.yaml`](../../deploy/compose.yaml)에 서비스 `mysql`·`backend`가 있고, 호스트 포트를 공개하지 않습니다. 네트워크를 따로 선언하지 않았으므로 기본 네트워크는 `beone-app_default`입니다.

공개 nginx의 실제 `server_name`·정적 루트·`location` 구성은 저장소에 기록이 없습니다. 아래 초안을 쓰기 전에 서버에서 **읽기만** 해서 대조합니다.

```bash
exec bash   # SSM 기본 셸은 sh다
sudo grep -nE 'server_name|listen|location|root|alias|try_files|return|proxy_pass|ssl_' /opt/beone/nginx/conf.d/*.conf
sudo grep -nE 'image|volumes|networks|ports' /opt/beone/compose.yaml
sudo docker network ls --format '{{.Name}}' | grep -E '^beone'
```

## 2. 설정 초안

`<정적 루트>`, `<인증서 경로>`는 1절 대조 결과로 채웁니다. 기존 `server` 블록의 `listen`·`ssl_*`·certbot 경로(`/.well-known/acme-challenge/`)는 **그대로 유지**하고, `location`만 아래처럼 바꾸는 것이 목표입니다.

```nginx
server {
    listen 443 ssl;
    server_name beonepay.site;
    # ssl_certificate·ssl_certificate_key: 기존 값 그대로(<인증서 경로>)

    root <정적 루트>;          # 프론트 빌드(dist)를 푼 위치

    # 보안 헤더(N4 결정 전 자리표시. 값이 정해지기 전에는 넣지 않는다)
    # add_header Strict-Transport-Security "<N4: max-age 등>" always;
    # add_header Content-Security-Policy-Report-Only "<N4: 정책>" always;
    # add_header X-Content-Type-Options "nosniff" always;            # 후보
    # add_header Referrer-Policy "<N4: 예 same-origin>" always;
    # add_header X-Frame-Options "<N4: 예 DENY>" always;

    location / {
        try_files $uri $uri/ /index.html;   # SPA. 화면 경로(/email-verification 등)를 index.html로
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
  - 검증 환경(`deploy/verify/nginx.conf`)도 같은 방식이고, 위조 헤더를 보내도 Set-Cookie가 같다는 것을 스모크 S17로 확인했습니다.
- 반영하면 지금의 정적 안내 화면은 SPA로 바뀝니다. 안내 화면은 4절 백업이 원복 대상입니다.

## 3. 두 Compose 프로젝트 연결

공개 nginx(`beone`)가 `beone-app`의 backend에 닿아야 합니다. 방법은 승인 ⑧에서 정합니다.

| 안 | 내용 | 영향 |
|---|---|---|
| A | `beone`의 nginx 서비스에 외부 네트워크 `beone-app_default`를 추가하고, `proxy_pass http://backend:8080`(서비스 이름) | `/opt/beone/compose.yaml` 수정과 nginx 컨테이너 재생성. 짧은 중단 |
| B | 전용 외부 네트워크(예 `beone-edge`)를 만들고 두 프로젝트를 모두 연결, backend에 별칭 부여 | 두 프로젝트의 compose 수정과 재생성 |

어느 안이든 backend의 호스트 포트는 계속 공개하지 않습니다.

## 4. 반영·원복 절차 (승인 ⑧)

| # | 단계 | 확인값 | 실패 시 |
|---|---|---|---|
| P3 | 설정 백업: `sudo tar -czf /opt/beone-backup/pre-mvp-<UTC>.tgz /opt/beone/compose.yaml /opt/beone/nginx/conf.d <정적 루트>` | tgz 존재, 크기 > 0 | 진행 안 함 |
| P4 | `prod` backend 기동·정적 웹 배치(별도 절차) | `beone-app` healthy | 앱만 원복 |
| P5-1 | 설정 수정 → `sudo docker compose -p beone exec nginx nginx -t` | `syntax is ok`, `test is successful` | 파일을 백업본으로 되돌림 |
| P5-2 | `sudo docker compose -p beone exec nginx nginx -s reload` | 아래 확인 통과 | 원복 |
| P5-3 | 확인: `curl -sI https://beonepay.site/` 200, `curl -s -o /dev/null -w '%{http_code}' https://beonepay.site/api/v1/me` 401, `https://beonepay.site/actuator/health` 404, `http://` 301, certbot 갱신 타이머 유지 | 모두 기대값 | 원복 |
| P5-4 | `deploy/scripts/smoke-member.sh https://beonepay.site`(합성 회원, T-a는 운영 DB에 쓰지 않으므로 **결정 필요**: 공개 환경의 인증은 실메일 N1로 한다) | 전 항목 PASS | 원복 |

**원복**

```bash
exec bash
sudo tar -xzf /opt/beone-backup/pre-mvp-<UTC>.tgz -C /
sudo docker compose -p beone exec nginx nginx -t && sudo docker compose -p beone exec nginx nginx -s reload
curl -sI https://beonepay.site/ | head -1    # 200, 이전 안내 화면
```

3절에서 컨테이너를 재생성했다면, 백업한 `/opt/beone/compose.yaml`로 `sudo docker compose -f /opt/beone/compose.yaml -p beone up -d nginx`를 다시 실행합니다.

## 5. 남은 결정

| 항목 | 상태 |
|---|---|
| N4 보안 헤더·요청 제한·본문 크기 | 결정 대기. 권장안은 인증 경로만 제한, HSTS를 짧게 시작, CSP는 Report-Only로 먼저(ROADMAP 8절) |
| 프로젝트 연결 방식(3절 A·B) | 승인 ⑧에서 결정 |
| 공개 환경 스모크의 회원 인증 | 실메일(N1, 결정 39-6) 구현 뒤. 운영 DB에는 T-a를 쓰지 않음 |
