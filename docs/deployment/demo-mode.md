# 시연 모드 서버(beone-demo) 런북

시연 영상 촬영 동안만 띄우는 서버입니다(2026-11-27 사용자 결정, 개발 마감 11:00 KST). 백엔드는 `demo` 프로필만 켜고, 화면은 운영과 같은 `main` 프론트 빌드를 씁니다. 공개 접속은 기존 스테이징 도메인을 촬영 시간 동안만 빌립니다.

- **파일:**
  - [`deploy/demo/compose.yaml`](../../deploy/demo/compose.yaml): 시연 서버 본체
  - [`deploy/demo/compose.staging-domain.yaml`](../../deploy/demo/compose.staging-domain.yaml): 스테이징 도메인을 빌릴 때만 붙이는 오버레이
  - [`deploy/demo/nginx.conf`](../../deploy/demo/nginx.conf)
  - [`deploy/demo/env.template`](../../deploy/demo/env.template): 키 이름만 있음
  - [`deploy/demo/seed-demo.sh`](../../deploy/demo/seed-demo.sh)와 [`deploy/demo/seed/`](../../deploy/demo/seed): 시연 데이터 준비
- **서버 작업은 운영 담당(Aside)이 합니다.** 이 문서의 명령은 서버 안에서 실행하는 형태입니다. 서버 주소·계정·비밀값은 적지 않습니다.
- **실제 CODEF·실제 메일은 쓰지 않습니다.** `demo` 프로필은 가짜 메일 발송기와 가짜 CODEF 게이트웨이를 씁니다.
  - 가짜 메일은 메모리에만 담고 로그를 남기지 않습니다(`FakeAuthMailSender`).
  - 가짜 CODEF는 합성 fixture로 동작합니다(`FakeCodefGateway`).

## 0. 경고: 운영에는 demo를 켜지 않습니다

| 금지 | 이유와 막는 장치 |
|---|---|
| 운영(`beone-app`)·스테이징 env나 compose에 `demo`·`local`을 넣는 것 | `demo`는 `X-BeONE-Demo-User` 헤더만으로 사용자를 정합니다. 로그인 없이 시연 장부를 읽고 바꿀 수 있습니다. `prod`와 함께 켜면 `PublicProfileGuard`가 기동을 멈춥니다. `member`와 함께 켜면 `CurrentUserModeGuard`가 멈춥니다 |
| 시연 서버를 기본 인증 없이 공개하는 것 | 위와 같은 이유입니다. 공개 nginx는 시연 web의 기본 인증 리스너(컨테이너 8080)만 넘깁니다 |
| 시연 서버가 운영 DB·볼륨을 쓰는 것 | 시연 서버는 자기 MySQL과 볼륨 `beone-demo_demo-mysql-data`만 씁니다 |
| 준비 리스너 `127.0.0.1:18084`를 공개 nginx로 넘기는 것 | 인증이 없습니다. 서버 안의 준비 스크립트 전용입니다 |

확인 명령(읽기만, 언제든):

```bash
# block: profile-guard   운영·스테이징에 demo·local이 없고, 시연 서버만 demo인지
sudo docker inspect beone-app-backend-1 beone-staging-backend-1 beone-demo-backend-1 \
  --format '{{.Name}} {{range .Config.Env}}{{if eq (index (split . "=") 0) "SPRING_PROFILES_ACTIVE"}}{{.}}{{end}}{{end}}' 2>/dev/null
sudo grep -hE 'SPRING_PROFILES_ACTIVE' /opt/beone/app/compose.yaml /opt/beone/app/.env 2>/dev/null \
  | grep -qwE 'demo|local' && echo "중단: 운영에 demo/local" || echo "운영: demo/local 없음"
```

- 기대값: `/beone-app-backend-1 SPRING_PROFILES_ACTIVE=prod`, 스테이징 `prod,staging`, 시연 `demo`.
- 컨테이너가 없으면 그 줄은 나오지 않습니다. 운영 줄에 `demo`나 `local`이 보이면 아무것도 하지 말고 보고합니다.
- `.env`는 값을 출력하지 않도록 `grep -n`으로 프로필 줄만 봅니다.

## 1. 구성과 사전 확인

| 서비스 | 이미지 | 메모리 상한 | 호스트 포트 |
|---|---|---|---|
| `mysql` | 서버에 있는 `mysql:8.4.11` | 512MiB | 없음 |
| `backend` | 운영과 같은 `ghcr.io/beone-capston/beone-api@sha256:<운영 api digest>`, `SPRING_PROFILES_ACTIVE=demo` | 768MiB | 없음 |
| `web` | 서버에 있는 nginx 이미지 | 64MiB | `127.0.0.1:18083`(기본 인증), `127.0.0.1:18084`(준비 스크립트 전용, 인증 없음, `/api/`만) |

- **분리:** 시연 서버는 운영(`beone-app`)·스테이징(`beone-staging`)·검증(`beone-verify`)과 이름·DB·볼륨을 공유하지 않습니다.
  - 프로젝트 이름은 `beone-demo`, 네트워크는 `beone-demo_demo`, 볼륨은 `beone-demo_demo-mysql-data`입니다.
  - 스테이징 도메인을 빌릴 때만 web이 `beone-staging_staging` 네트워크에도 붙습니다(3절).
- **메모리:**
  - 시연 서버의 상한 합계는 1344MiB로 스테이징과 같습니다.
  - 스테이징을 `stop`한 자리에 띄웁니다. `beone-verify`와는 함께 띄우지 않습니다.
  - `oom_score_adj`가 800~900이라, 메모리가 모자라면 운영보다 시연 서버가 먼저 종료됩니다.
- **기본 인증:** 스테이징의 `htpasswd`를 그대로 씁니다. compose 기본값이 `../staging/htpasswd`이고 읽기 전용으로 마운트합니다.
  - 그래서 계정을 새로 만들지 않고, 공개 nginx와 시연 web에 인증이 이중으로 걸리지 않습니다.
  - 따로 계정을 쓰려면 `.env`에 `BEONE_DEMO_HTPASSWD=<경로>`를 넣습니다. 파일은 `htpasswd -B -c`로 만들고 권한은 `640 root:101`입니다.

```bash
# block: precheck   읽기만
free -m | sed -n '1,2p'                                   # available이 1400 이상이 바람직(스테이징 stop 뒤)
sudo docker ps --format '{{.Names}}' | grep -E '^beone-(verify|demo)-' && echo "중단: verify 또는 이전 demo가 떠 있음" || echo "OK"
sudo ss -ltn | grep -E '127\.0\.0\.1:1808[34] ' && echo "중단: 18083/18084 사용 중" || echo "포트 OK"
sudo docker image ls --format '{{.Repository}}:{{.Tag}}' | grep -E '^(mysql:8\.4\.11|nginx:)'   # 서버에 있는 태그 확인
```

## 2. 기동

### 2-1. 폴더와 env

```bash
# block: dirs
sudo install -d -m 750 /opt/beone/demo /opt/beone/demo/web /opt/beone/demo/web/releases
# 저장소 deploy/demo의 compose.yaml, compose.staging-domain.yaml, nginx.conf, env.template, seed-demo.sh, seed/를 /opt/beone/demo로 복사
sudo install -m 600 /opt/beone/demo/env.template /opt/beone/demo/.env   # 값은 편집기로 채우고 출력하지 않는다
```

`.env`에 채울 키(값은 출력하지 않음):

- `MYSQL_*`: 시연 전용 값입니다. 비밀번호는 `openssl rand -hex 24`로 만듭니다.
- `BEONE_DEMO_NGINX_IMAGE`: 1절에서 확인한 nginx 태그입니다.
- `BEONE_DEMO_PROFILES`: 비워 둡니다. 비우면 `demo`입니다.
- `BEONE_API_IMAGE`: 아래 2-2에서 정합니다.

### 2-2. 이미지(digest 지정)

운영과 같은 이미지를 씁니다. 운영 digest는 `deploy.sh`가 남긴 상태 파일에서 읽습니다. 사용자 결정 때 전달된 운영 web digest는 `sha256:83aa402a…`로 시작합니다. 전체 값은 아래 명령으로 서버에서 확인합니다.

```bash
# block: digests
sudo grep -E '^(api_digest|web_digest|git_sha)=' /opt/beone/deploy/state/production.state
# 예: api_digest=sha256:… web_digest=sha256:83aa402a… git_sha=…
API=sha256:<위 api_digest>; WEB=sha256:<위 web_digest>
sudo docker pull "ghcr.io/beone-capston/beone-api@$API"
sudo docker pull "ghcr.io/beone-capston/beone-web@$WEB"
# .env의 BEONE_API_IMAGE를 ghcr.io/beone-capston/beone-api@$API로 둔다(태그가 아니라 digest)
```

web 이미지는 dist만 담은 이미지입니다([`deploy/web/Dockerfile`](../../deploy/web/Dockerfile)). `deploy.sh`처럼 `/dist`를 꺼내 릴리스 폴더에 두고 `current` 링크를 겁니다.

```bash
# block: web-dist
cd /opt/beone/demo/web
rel="releases/${WEB#sha256:}"; rel="${rel:0:21}"         # releases/<digest 앞 12자>
sudo install -d -m 755 "$rel"
cid=$(sudo docker create "ghcr.io/beone-capston/beone-web@$WEB" noop)
sudo docker cp "$cid:/dist/." "$rel/" && sudo docker rm "$cid" > /dev/null
sudo ln -sfn "$rel" current && ls current/index.html
```

### 2-3. up

```bash
# block: up   스테이징 도메인을 빌리지 않고 루프백만으로 먼저 띄운다
cd /opt/beone/demo
D="sudo docker compose -f compose.yaml -p beone-demo --env-file .env"
$D config --quiet && $D up -d
until [ "$(sudo docker inspect -f '{{.State.Health.Status}}' beone-demo-backend-1)" = healthy ]; do sleep 5; done
curl -s http://127.0.0.1:18084/actuator/health                    # {"status":"UP"...}
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:18083/  # 401(기본 인증)
```

Flyway가 V1부터 적용되며 첫 기동은 몇 분 걸립니다. backend가 `healthy`가 되지 않으면 `$D logs --tail 80 backend`로 확인합니다(비밀값은 로그에 나오지 않음).

## 3. 스테이징 도메인 빌리기

공개 nginx의 `staging.conf`는 이미 `resolver 127.0.0.11`과 변수 `proxy_pass`를 씁니다([공개 nginx 2-1절](public-proxy.md#2-1-스테이징-server-블록-기본-인증은-스테이징-web이-함)). 그래서 업스트림 주소 문자열 `http://beone-staging-web-1:8080`만 `http://beone-demo-web-1:8080`으로 바꿉니다.

- 공개 nginx는 이미 `beone-staging_staging` 네트워크에 붙어 있습니다. 오버레이가 시연 web을 그 네트워크에 붙이므로, 공개 nginx의 compose·네트워크는 바꾸지 않습니다.
- N4 보안 헤더·요청 제한 조각(#283, [공개 nginx 보안](public-nginx-security.md) 3-2절)이 이미 반영됐어도 충돌하지 않습니다.
  - 반영됐으면 `staging.conf` 안에 같은 업스트림 줄이 3곳(`location /`, 인증 경로, `/api/`)에 있습니다.
  - 아래 명령은 그 줄을 모두 바꾸고, 바뀐 줄 수를 전후로 셉니다.
  - 보안 헤더·요청 제한은 그대로 시연 도메인에도 적용됩니다.

```bash
# block: borrow   순서대로. 실패하면 7절로 되돌린다
C=/opt/beone/nginx/conf.d/staging.conf
B=$(sudo mktemp /opt/beone/nginx/staging.conf.before-demo.XXXXXX) && sudo cp -p "$C" "$B" && echo "백업: $B"
before=$(sudo grep -c 'http://beone-staging-web-1:8080' "$C"); echo "스테이징 업스트림 줄: $before"   # 1(N4 전) 또는 3(N4 뒤)
# 1) 스테이징 멈춤(볼륨·네트워크 보존. down 금지)
sudo docker compose -f /opt/beone/staging/compose.yaml -p beone-staging --env-file /opt/beone/staging/.env stop
# 2) 시연 web을 스테이징 네트워크에도 붙인다(오버레이)
cd /opt/beone/demo && sudo docker compose -f compose.yaml -f compose.staging-domain.yaml -p beone-demo --env-file .env up -d web
# 3) 업스트림 문자열만 바꾼다
sudo sed -i 's#http://beone-staging-web-1:8080#http://beone-demo-web-1:8080#g' "$C"
echo "시연 업스트림 줄: $(sudo grep -c 'http://beone-demo-web-1:8080' "$C") (= $before 이어야 함)"
# 4) 검사·반영
sudo docker compose -f /opt/beone/compose.yaml exec -T nginx nginx -t && \
  sudo docker compose -f /opt/beone/compose.yaml exec -T nginx nginx -s reload
```

확인(외부에서):

```bash
curl -s -o /dev/null -w '%{http_code}\n' https://staging.beonepay.site/   # 401(기본 인증, 시연 web이 응답)
```

- 브라우저에서는 스테이징 기본 인증 계정으로 들어갑니다.
- 화면 첫 진입에서 로그인 화면이 보이면 시연 web이 응답한 것입니다. 스테이징 회원 계정으로는 로그인되지 않습니다. 시연 DB가 따로이기 때문입니다.

## 4. 시연 데이터 준비

```bash
# block: seed   비밀번호는 입력 프롬프트로만 받는다(화면 표시 없음)
cd /opt/beone/demo
sudo ./seed-demo.sh --email <시연 이메일> --month <촬영 달 YYYY-MM> --approve
```

### 4-1. 스크립트가 하는 일

| 단계 | 내용 | 근거 |
|---|---|---|
| 회원 가입 | `POST /api/v1/auth/sign-up`(CSRF 포함) | `demo`에도 인증 API가 등록됨(`@Profile({local, demo, member})`). main 프론트의 `AuthEntry`가 진입 때 세션을 확인하므로 로그인 계정이 필요 |
| 인증 완료 표시 | `members.email_verified_at`을 DB에서 채움(이미 채워졌으면 그대로) | 가짜 메일이라 인증 메일이 오지 않음. 로그는 없음. 그래서 DB 표시가 유일한 방법 |
| 로그인 확인 | `POST /api/v1/auth/login` → `GET /api/v1/auth/session` 200 | 화면에서는 같은 이메일·비밀번호로 로그인 |
| 카드 4종 | 시연 사용자 `MY_CARDS`에 `09271`·`09174`·`01664`·`01914` 등록. 같은 상품이 이미 있으면 건너뜀 | main 프론트의 주문·결제 화면은 쿠키 없이 `X-BeONE-Demo-User` 헤더로 부르고(`orders/orderApi.ts`·`payment/paymentApi.ts`), `demo`는 그 헤더로 사용자를 정함(`CurrentUserResolver`). 그래서 회원 장부가 아니라 `MY_CARDS`에 넣어야 화면과 맞음. `MY_CARDS` 시드에는 카드가 0장(V2·V4. 시드 카드 4장은 `VIRTUAL_PORTFOLIO`) |
| 월간 상태 | 카드마다 대상 월에 전월 실적 600,000원, 모든 한도 묶음 사용액 0원, 등록일 2026-01-01([`seed/state-*.json`](../../deploy/demo/seed)) | 600,000원은 네 카드의 최소 실적 구간(01664 200,000 · 09174·01914 300,000 · 09271 400,000)을 모두 넘는 값. 월간 상태의 달은 주문 날짜의 달과 같아야 함 |
| 확인 | 일반 국내 100,000원 직접 결제 주문 → 추천 → (`--approve`면) 모의 승인 | 아래 4-2 |

- 한도 묶음·서비스 ID는 문서 예시의 `LB-`·`SVC-` 접두사가 붙지 않은 규칙 원래 ID(`STORE`, `GREAT` 등)입니다. 접두사를 붙이면 400 `카드 규칙에 없는 값`입니다.
- 택1 서비스(09271의 `FUEL`·`STORE`)는 선택하지 않습니다. 선택을 넣으면 그 서비스가 걸리는 주문에서 09271이 `UNSUPPORTED`(`REQUIRES_SELECTION`)가 됩니다. 그러면 추천을 확정하지 못합니다.
- 같은 순서·값을 backend [`DemoSeedIntegrationTest`](../../backend/src/test/java/com/beone/api/auth/web/DemoSeedIntegrationTest.java)가 `demo` 프로필·MySQL로 고정합니다. 이 시험은 같은 `seed/` 파일을 읽습니다.

### 4-2. 기대 출력과 한계

```text
추천(ord-N): outcome=SELECTED conditional=False 선택=09271
  비교 09271 1000원 CONFIRMED
  비교 09174 0원 CONFIRMED
  비교 01664 0원 CONFIRMED
  비교 01914 0원 CONFIRMED
판정: 통과(확정 추천, 4장 비교)
모의 승인: APPROVED 09271 100000원 결제, 혜택 1000원 CONFIRMED
```

- **혜택이 0원보다 큰 카드는 09271뿐입니다.** 기본 적립 1%는 실적 조건이 없습니다. 나머지 3장은 일반 업종에 해당 서비스가 없어 확정 0원입니다.
- **스타벅스 같은 특정 업종 주문은 촬영에 쓰지 않습니다.** 09174·01664·01914의 서비스는 모두 전월 실적 조건이 있습니다. 업무 API는 실적 정책 근거를 `UNDETERMINED`로 넘깁니다([추천 API](../api/recommendations.md)). 그래서 그런 주문에서는 세 카드가 "확인 필요"가 됩니다.
  - 09174는 취소 귀속월이 미확정입니다.
  - 01664·01914는 할인받은 매출의 실적 제외 여부가 미확정입니다.
  - 확인 필요 카드가 있으면 추천이 조건부가 되거나 `NOT_DETERMINED`가 됩니다.
  - 카드 규칙이나 엔진을 바꾸지 않는 한 4장 모두 0원보다 큰 확정 혜택은 나오지 않습니다.
- **판정이 `확인 필요`로 끝나면(종료 코드 3):**
  - 출력의 제외 카드 사유를 봅니다.
  - 월간 상태의 달이 주문 날짜의 달과 다르면 `--month`를 촬영 날짜의 달로 다시 실행합니다. 카드는 건너뛰고 월간 상태만 다시 입력됩니다.
- `--approve`를 빼면 원장 행을 만들지 않고 추천까지만 확인합니다. 확인용 주문은 화면의 주문 목록에 남습니다.

## 5. 화면 흐름 확인(촬영 전)

`https://staging.beonepay.site`(기본 인증) 또는 서버 안에서 `127.0.0.1:18083`으로 접속합니다.

1. **로그인:** 4절의 시연 이메일·비밀번호로 로그인하면 홈이 열립니다.
2. **내 카드:** 카드 목록이 보이는지 확인합니다.
3. **주문:** 일반 업종(예: 동네 마트) 금액 주문을 만듭니다.
4. **추천:** 추천 카드가 확정(조건부 아님)으로 보이는지 확인합니다.
5. **모의결제:** 승인 결과에 결제 금액과 혜택 금액이 보이는지 확인합니다.

- **운영 web 이미지 그대로일 때 알려진 한계:**
  - main 프론트의 혜택 조회(`benefits/config.ts`)는 production 빌드에서 `VITE_BENEFITS_CONTEXT=demo`가 아니면 회원 경로를 씁니다. 회원 경로는 데모 헤더 없이 쿠키로 부릅니다.
  - 그래서 `demo` 백엔드에서 혜택·월간 상태를 읽는 화면은 실패할 것으로 봅니다. `demo`는 헤더가 없으면 400입니다(`DemoUserHeader`). 서버에서 화면으로 직접 확인하지는 않았습니다.
  - 주문·추천·모의결제 화면은 헤더로 부르므로 영향이 없습니다.
  - 촬영 흐름에 혜택·월간 화면이 꼭 필요하면 부록 A의 별도 빌드로 web만 바꿉니다.
- 위 1~5 중 막히는 단계가 있으면 브라우저 개발자 도구의 네트워크 탭에서 실패한 `/api/` 요청의 상태 코드만 기록해 보고합니다(쿠키·토큰은 기록하지 않음).

## 6. 끄기와 스테이징 복구

순서가 중요합니다. 공개 nginx를 원래 업스트림으로 되돌린 뒤 스테이징을 다시 켭니다.

```bash
# block: restore   같은 셸의 C·B, 또는 B=<3절에서 출력한 백업 경로>
C=/opt/beone/nginx/conf.d/staging.conf
# 1) 시연 서버 끄기. 볼륨 보존(다시 촬영할 수 있음)
cd /opt/beone/demo && sudo docker compose -f compose.yaml -f compose.staging-domain.yaml -p beone-demo --env-file .env down
#    볼륨까지 지울 때만(시연 DB 전체 삭제, 되돌릴 수 없음): 위 명령 끝에 -v. 사용자 확인 뒤에만 한다
# 2) staging.conf 원복(백업 복사)과 검사·반영
sudo cp -p "$B" "$C"
sudo grep -c 'http://beone-demo-web-1:8080' "$C"    # 0이어야 함
sudo docker compose -f /opt/beone/compose.yaml exec -T nginx nginx -t && \
  sudo docker compose -f /opt/beone/compose.yaml exec -T nginx nginx -s reload
# 3) 스테이징 다시 켜기와 확인
sudo docker compose -f /opt/beone/staging/compose.yaml -p beone-staging --env-file /opt/beone/staging/.env start
until [ "$(sudo docker inspect -f '{{.State.Health.Status}}' beone-staging-backend-1)" = healthy ]; do sleep 5; done
curl -s -o /dev/null -w '%{http_code}\n' https://staging.beonepay.site/            # 401(스테이징 기본 인증)
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:18081/api/v1/me          # 401(스테이징 backend 응답)
```

- `down`은 시연 web을 `beone-staging_staging` 네트워크에서도 뗍니다. 스테이징 네트워크 자체는 스테이징 프로젝트 것이라 남습니다.
- 시연 서버를 다시 띄우려면 2-3절과 3절을 반복합니다. 볼륨을 보존했으면 4절은 확인만 다시 합니다.
- **되돌리기가 필요한 경우:** `nginx -t`가 실패하거나 3절 확인에서 502·운영 이상이 보이면 위 2)~3)만 먼저 실행합니다. 시연 서버는 그대로 둬도 운영에 영향이 없습니다(루프백 포트만 열림).
- 운영 주소 확인: 운영 `/`는 200이고 `/api/v1/me`는 401이어야 합니다. 시연 작업은 운영 server 블록과 `beone-app`을 건드리지 않습니다.

## 부록 A. 혜택·월간 화면까지 시연 백엔드로 보이게 하는 별도 빌드

프론트 소스는 바꾸지 않고 빌드 변수만 줍니다. 같은 `main` 커밋을 개발 PC나 CI에서 빌드합니다.

```bash
# frontend/ (main 커밋 체크아웃)
npm ci
VITE_BENEFITS_CONTEXT=demo VITE_CARD_SERVICES_CONTEXT=demo VITE_WORKSPACE_CONTEXT=demo npm run build
# VITE_API_BASE_URL·VITE_AUTH_API_BASE_URL은 주지 않는다(빈 값 = 같은 origin)
```

- **변수의 효과:**
  - `VITE_BENEFITS_CONTEXT=demo`이면 혜택 조회가 데모 헤더(`MY_CARDS` 등)로 부릅니다.
  - `VITE_CARD_SERVICES_CONTEXT=demo`는 카드 서비스 화면에 쓰입니다.
  - `VITE_WORKSPACE_CONTEXT`는 main에는 없고 develop 이후 코드에서 쓰입니다. 있어도 main 빌드에는 영향이 없습니다.
- **반영:** 결과 `dist`를 [`deploy/web/Dockerfile`](../../deploy/web/Dockerfile)로 이미지화(`docker build -f deploy/web/Dockerfile frontend/dist`)하거나 그대로 서버에 옮깁니다.
  - 2-2절처럼 `/opt/beone/demo/web/releases/<이름>`에 두고 `current` 링크만 바꿉니다. web 컨테이너는 재시작할 필요가 없습니다.
  - 되돌리려면 링크를 운영 dist 릴리스로 다시 겁니다.
- 이 빌드는 운영·스테이징에 배포하지 않습니다.
- 로컬 확인(2026-11-27): 이 변수로 production 빌드가 끝나고, 번들에 데모 헤더 경로가 포함됩니다.

## 부록 B. 쓰지 않는 대안

- **`member`+`demo` 동시 켜기:** `CurrentUserModeGuard`가 기동을 멈춥니다. 백엔드를 고치지 않고는 불가능합니다.
- **DEV 빌드의 개발용 로그인(mockLogin):**
  - mockLogin은 `import.meta.env.DEV`이고, 주소가 `localhost`·`127.0.0.1`·`[::1]`이며, 포트가 정확히 `5173`일 때만 켜집니다(`frontend/src/auth/AuthEntry.tsx`).
  - 공개 도메인으로는 뜨지 않습니다.
  - 쓰려면 `NODE_ENV=development vite build --mode development`로 빌드하고, 촬영 PC에서 `ssh -L 5173:127.0.0.1:18083`로 터널을 연 뒤 `http://localhost:5173`으로 접속해야 합니다.
  - `demo`에서는 실제 로그인이 되므로(4절) 필요 없습니다.
