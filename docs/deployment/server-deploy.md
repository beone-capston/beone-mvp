# 서버 배포 스크립트 (`deploy.sh`)

GitHub Actions가 SSM 문서 `BeoneDeployStaging`(스테이징) 또는 `BeoneDeployProduction`(운영)으로 서버의 `/opt/beone/deploy/deploy.sh`를 부르면, 이 스크립트가 digest로 고정한 이미지를 받아 backend와 웹을 바꾸고 공개 점검을 한 뒤, 실패하면 이전 상태로 되돌립니다. 결정 40(배포 자동화)의 (3)(7)과 인터페이스 계약을 따릅니다.

- **상태:** 스크립트와 오프라인 자기검사만 있습니다. 서버 설치, SSM 문서, IAM, 워크플로 연결은 하지 않았습니다. 서버 설치·갱신은 승인 절차(Aside)입니다.
- **파일:** [`deploy/scripts/deploy.sh`](../../deploy/scripts/deploy.sh), [`deploy/scripts/smoke-public.sh`](../../deploy/scripts/smoke-public.sh), [`deploy/scripts/deploy-selftest.sh`](../../deploy/scripts/deploy-selftest.sh)
- **서버 설치(승인 절차, Aside):** `deploy.sh`와 `smoke-public.sh`를 **같은 폴더 `/opt/beone/deploy/`에 실행 권한과 함께** 설치합니다(예: `install -m 755 deploy.sh smoke-public.sh /opt/beone/deploy/`). `deploy.sh`는 자기 폴더의 `smoke-public.sh`를 부릅니다. SSM 문서 `BeoneDeployStaging`·`BeoneDeployProduction`이 이 경로를 실행합니다. SSM은 root로 실행하므로 스크립트 안에서 `sudo`를 쓰지 않습니다. 스크립트 갱신도 같은 승인 절차입니다.

## 용어

- **digest:** 이미지 내용의 해시(`sha256:<64자리>`)입니다. 태그(`latest`, `v1`)는 다른 이미지로 옮겨질 수 있지만 digest는 같은 내용만 가리킵니다. 그래서 pull과 기동을 모두 digest로 합니다.
- **릴리스 디렉터리와 링크:** 웹 파일은 배포마다 새 폴더(`releases/<GitSha 12자리>-<무작위>`)에 풀고, nginx가 보는 `current` 링크만 새 폴더로 바꿉니다. 링크 교체는 `rename` 한 번이라 중간 상태가 없고, 복귀는 링크를 이전 폴더로 되돌리는 것입니다.

## 사용법

```bash
deploy.sh <staging|production> <ApiDigest> <WebDigest> <GitSha>
# 예: deploy.sh staging sha256:<64자리> sha256:<64자리> <40자리 커밋>
```

환경 인자는 SSM 문서가 고정해서 넘깁니다(`BeoneDeployStaging` → `staging`, `BeoneDeployProduction` → `production`, 결정 40 계약 수정: 문서·IAM 역할을 환경별로 나눠 스테이징 권한으로 운영 배포를 못 하게 함). digest·GitSha 형식은 SSM 문서의 `allowedPattern`과 같습니다. 환경은 `staging`·`production`만, digest는 `sha256:[0-9a-f]{64}`, GitSha는 `[0-9a-f]{40}`입니다. 다르면 아무것도 하지 않고 종료 2입니다.

| 환경 | compose·env | 프로젝트 | 기동 서비스 | 웹 폴더 | 스모크 기준 주소 |
|---|---|---|---|---|---|
| `staging` | `/opt/beone/staging/compose.yaml`, `.env` | `beone-staging` | 전체(`mysql`·`backend`·`web`) | `/opt/beone/staging/web` | `http://127.0.0.1:18081`(기본 인증 없는 루프백 점검 포트, A-CD2) |
| `production` | `/opt/beone/app/compose.yaml`, `.env` | `beone-app` | `backend`만(MySQL은 다시 만들지 않음) | `/opt/beone/web` | `https://beonepay.site` |

- 이미지: `ghcr.io/beone-capston/beone-api@<ApiDigest>`, `ghcr.io/beone-capston/beone-web@<WebDigest>`. backend는 compose의 `BEONE_API_IMAGE`를 셸 환경으로 덮어써 digest 이미지로 띄웁니다(셸 환경이 `--env-file`보다 우선). 서버 `.env`는 바꾸지 않습니다.
- web 이미지 안 dist 위치는 `/dist`입니다(`WEB_DIST_PATH`). 워크플로의 `deploy/web/Dockerfile`(`COPY . /dist/`, #225)과 같습니다.
- 운영 웹 폴더는 `/opt/beone/web`(`releases/`와 `current` 링크)으로 확정입니다. 공개 nginx 정적 루트를 이 링크에 맞추는 일은 공개 nginx 정합 작업(B-CD3, [공개 nginx 초안](public-proxy.md))이 합니다. 그 반영 전에는 운영 웹 링크를 바꿔도 공개 화면이 바뀌지 않습니다.

## 순서

| 단계 | 하는 일 | 실패하면 |
|---|---|---|
| 0 | 잠금(`flock`, 두 환경 공통 1개), compose·env 파일 확인, CODEF 가드, `config --quiet`·`config --services`(기대 서비스와 정확히 같아야 함) | 종료 3(변경 없음) |
| 1 | 상태 파일(`/opt/beone/deploy/state/<환경>.state`)에서 현재 digest를 읽음. 없으면 첫 배포. 있는데 형식이 틀리면 손상으로 보고 멈춤 | 종료 3 |
| 2 | api·web 이미지를 digest로 pull | 종료 3 |
| 3 | **운영만:** [백업](backend-compose.md#백업) 방식으로 덤프와 짝 기록(아래) | 종료 3, 배포 안 함 |
| 4 | backend `up -d` | 복귀 |
| 5 | health 대기: 실행 중 컨테이너의 이미지가 새 digest이고 `running/healthy`일 때까지(기본 300초 상한). `exited`·`dead`면 바로 실패 | 복귀 |
| 6 | web 이미지에서 dist를 새 릴리스 폴더로 꺼내고 `index.html`을 확인한 뒤 `current` 링크 원자 전환 | 복귀 |
| 7 | 무변경 공개 스모크(`smoke-public.sh`) | 복귀 |
| 끝 | 상태 파일 갱신과 이력(`<환경>.history`) 한 줄 추가 | 상태 기록 실패는 종료 1 |

### CODEF 가드

CODEF는 자동 배포 대상이 아닙니다(결정 40-7). env 파일에 `CODEF_`·`BEONE_CODEF_`로 시작하는 키 **이름**이 있거나, env·compose 파일에 `codef-live`라는 글자가 있으면 배포를 거부합니다. 값은 출력하지 않습니다. 앱의 `PublicProfileGuard`(prod에서 CODEF 키·`codef-live` 거부)보다 앞에서 막는 보강입니다.

### 운영 배포 직전 백업과 짝 기록

[백엔드 배포 기반의 백업](backend-compose.md#백업)과 같은 방식입니다. `/opt/beone/app/backups/app-<UTC 시각>-<무작위>/`에 `app.sql`(앱 계정, `--single-transaction`, 완료 표식 확인, `.partial` → `mv -n`), `app.sql.flyway`(백업 시점 이력), `app.sql.record`를 남깁니다. `app.sql.record`에는 문서의 항목(덤프·이력 sha256, 실행 중 backend 이미지 ID·이름·revision 라벨)에 더해 다음을 짝으로 적습니다.

```text
deploy_prev_api_digest=<이전 digest 또는 none>
deploy_new_api_digest=<새 digest>
deploy_new_web_digest=<새 web digest>
deploy_git_sha=<GitSha>
```

백업 id(폴더 이름)는 상태 파일의 `backup_id`와 이력 줄에도 남습니다. 덤프나 짝 기록이 하나라도 실패하면 배포하지 않습니다(종료 3). 배포 이미지에 revision 라벨이 없으면 `image_revision=<no value>`로 적습니다. [복원 확인](backend-compose.md#복원-확인-회차별-분리-프로젝트)은 `image_id`와 `image_tag`(digest 참조)로 이미지를 고르므로 그대로 쓸 수 있습니다.

## 실패와 복귀

4~7단계에서 실패하면 다음을 합니다.

1. 웹 링크를 바꿨다면 이전 링크 대상으로 되돌립니다.
2. 상태 파일의 이전 api digest로 backend를 다시 `up -d`하고 health를 다시 기다립니다. 이전 이미지가 서버에 없으면 digest로 pull합니다.
3. 둘 다 성공하면 종료 4, 하나라도 실패하거나 되돌릴 대상이 없으면 종료 1이며 눈에 띄는 경고를 출력합니다.

**DB는 자동으로 되돌리지 않습니다.** 새 마이그레이션이 적용된 뒤 복귀하면 DB는 새 스키마 그대로입니다. 덤프로 되돌려야 하면 [복원 확인](backend-compose.md#복원-확인-회차별-분리-프로젝트) 절차를 따르고, 운영 DB 교체는 별도 결정입니다.

### 새 마이그레이션 뒤 구 앱으로 복귀할 때

- **Flyway:** 이 저장소는 Flyway 12.4.0을 씁니다(`spring-boot-starter-flyway`). Flyway의 `ignoreMigrationPatterns` 기본값은 `*:future`로, DB에는 적용됐지만 앱에는 없는 "미래" 버전을 검증에서 무시합니다(flyway-core 12.4.0의 `FlywayModel` 기본값, Flyway 설정 문서의 `ignoreMigrationPatterns` 항목). `application.yml`·`application-prod.yml`은 이 값을 바꾸지 않습니다. 그래서 구 앱은 V(N+1)이 적용된 DB에서도 Flyway 단계에서 멈추지 않습니다.
- **Hibernate 검증:** 앱은 `spring.jpa.hibernate.ddl-auto: validate`입니다. 새 마이그레이션이 열·표를 **추가만** 했다면 구 앱의 검증은 통과합니다. 열 이름 변경·삭제·타입 변경이 있었다면 구 앱이 기동하지 못해 복귀가 실패합니다(종료 1).
- **데이터 호환:** 검증을 통과해도 새 열이 `NOT NULL`이고 기본값이 없으면 구 앱의 INSERT가 실패할 수 있습니다. 그래서 마이그레이션은 한 릴리스 동안 구 앱과 함께 동작하도록(추가 위주, 기본값 지정) 쓰는 것을 권장합니다. 이 스크립트가 이를 검사하지는 않습니다.

### 첫 배포

상태 파일이 없으면 첫 배포로 처리합니다(스테이징. 운영은 아래와 같이 거부). 이때 backend 복귀 대상이 없으므로 4~7단계가 실패하면 새 backend를 그대로 둔 채 종료 1입니다. 웹은 이전 `current` 링크가 있으면 되돌리고, 없으면 그대로 둡니다. 스테이징은 루프백 점검 포트로 스모크를 하므로 첫 배포도 이 경로를 씁니다. **운영의 첫 배포는 이 경로를 쓰지 않고 [운영 최초 배포(bootstrap)](#운영-최초-배포-bootstrap)를 씁니다.**

## 운영 최초 배포 (bootstrap)

운영 첫 배포는 정상 경로로 할 수 없습니다. `deploy.sh production`은 `https://beonepay.site` 공개 스모크(P2 `/api/v1/me` 401, P4 CSRF 쿠키)를 통과해야 성공하는데, 공개 nginx의 `/api` 프록시와 웹 링크 마운트는 그 뒤 nginx 변경([공개 nginx 초안](public-proxy.md) 4절)에서 처음 연결됩니다. 그대로 실행하면 첫 배포가 반드시 실패하고, 복귀 대상이 없어 새 backend와 웹 링크가 남은 채 종료 1이 됩니다(순환 의존). 그래서 운영 최초 1회는 세 단계로 나눕니다.

- **실행 주체:** 승인 창 안에서 사람(Aside)이 서버에서 직접 실행합니다. 첫 인자가 `bootstrap-*`라서 첫 인자를 환경으로 고정하는 SSM 문서로는 부를 수 없습니다. 정상 배포 계약(`deploy.sh <staging|production> …`, SSM 문서 2개)은 바뀌지 않습니다.
- **운영 전용:** 환경 인자는 `production`만 받습니다. `staging`이면 종료 2입니다.
- **잠금:** 세 단계 모두 정상 배포와 같은 `deploy.lock`을 씁니다. 다른 배포가 실행 중이면 종료 3입니다.
- **대기 파일:** `/opt/beone/deploy/state/production.bootstrap`. "준비 완료, 아직 미확정"을 뜻합니다. 상태 파일(`production.state`)은 `bootstrap-confirm`이 공개 스모크를 통과했을 때만 씁니다. 사람이 상태 파일을 손으로 쓰지 않습니다.

```bash
deploy.sh bootstrap-prepare production <ApiDigest> <WebDigest> <GitSha>
# (사람) 공개 nginx 네트워크·마운트·설정 적용 → nginx -t → reload
deploy.sh bootstrap-confirm production
# 되돌릴 때
deploy.sh bootstrap-abort production
```

| 단계 | 하는 일 | 종료 코드 |
|---|---|---|
| `bootstrap-prepare` | 상태 파일이나 대기 파일이 있으면 거부합니다. 정상 배포와 같은 가드·compose 확인을 합니다. pull 전에 **복귀 기준**과 DB 유무를 판정합니다(아래 표). 이어서 digest pull, 배포 직전 백업·짝 기록을 합니다. 짝 기록은 [정상 배포와 같은 형식](#운영-배포-직전-백업과-짝-기록)이고 `deploy_prev_api_digest=none`입니다. 복귀 기준(`base_image_tag`·`base_image_id`)은 대기 파일에 적습니다. 이어서 backend `up -d`, health, 웹 릴리스와 `current` 링크 전환을 합니다. 공개 nginx가 아직 이 링크를 서빙하지 않으므로 공개 화면은 바뀌지 않습니다. 공개 스모크 대신 비공개 검증(backend healthy, 링크로 `index.html` 확인)을 하고 대기 파일만 씁니다. | 0 준비 완료(미확정). 3 변경 없음(상태·대기 파일 있음, 잠금, 가드, 조회 오류, 멈춘 mysql·기존 볼륨, pull, 백업 실패). 4 실패 뒤 복귀 성공(기준 이미지로 복귀, 기준 none이면 새 backend 정지). 1 복귀 실패. 2 인자 오류 |
| (사람) nginx | [공개 nginx 초안](public-proxy.md) 4절의 순서대로 적용합니다. `nginx -t`가 실패하면 reload하지 않고 nginx 설정을 백업으로 복원한 뒤 `bootstrap-abort`를 실행합니다. | - |
| `bootstrap-confirm` | 대기 파일이 필요합니다. 웹 링크가 준비한 릴리스인지, backend가 준비한 digest로 healthy인지 확인한 뒤 공개 스모크(`smoke-public.sh https://beonepay.site`)를 합니다. 통과해야만 상태 파일을 쓰고, 대기 파일 이름을 `production.bootstrap.confirmed-<UTC>`로 바꿉니다. | 0 확정. 1 미확정(상태 파일 없음, 대기 파일 유지). 원인을 고쳐 다시 confirm하거나, nginx를 백업으로 복원한 뒤 abort합니다. 3 대기 파일 없음·손상, 상태 파일 이미 있음, 잠금 |
| `bootstrap-abort` | 대기 파일이 필요합니다. 웹 링크를 이전 값으로 되돌리고, 이전 링크가 없었으면(안내 화면만 있던 상태) 새 링크만 지웁니다. 릴리스 폴더는 남깁니다. backend는 복귀 기준 이미지로 `up -d`하고 health를 기다립니다. 기록한 이름이 지금 다른 이미지를 가리키면 기록한 이미지 ID로 띄웁니다. 복귀 기준이 none이면 새 backend를 `compose stop backend`로 멈춥니다(컨테이너·볼륨은 지우지 않음). 상태 파일은 쓰지 않습니다. | 0 복귀 완료(기준 none이면 정리 완료. 대기 파일은 `.aborted-<UTC>`). 1 복귀 실패(대기 파일 유지, 다시 실행 가능). 3 대기 파일 없음·손상, 잠금 |

**prepare의 복귀 기준과 백업 판정**

조회 명령(`compose ps`, `docker volume ls` 등)이 실패하면 "없음"으로 보지 않고 종료 3으로 멈춥니다.

| 상황 | 복귀 기준 | 백업 |
|---|---|---|
| `beone-app` backend 실행 중 | 그 컨테이너의 이미지 이름·ID | 아래 mysql 판정을 따름 |
| backend 없음 | `none`(실패·취소 시 새 backend 정지) | 아래 mysql 판정을 따름 |
| mysql 실행 중 | - | 덤프·짝 기록 필수. 실패하면 종료 3. backend가 없으면 짝 기록의 이미지 칸이 `none` |
| mysql 컨테이너가 멈춰 있음, 또는 컨테이너는 없지만 프로젝트 볼륨이 있음 | - | 데이터가 있을 수 있으므로 종료 3. mysql을 띄운 뒤 다시 실행 |
| mysql 컨테이너·볼륨 모두 없음 | - | `backup=none`. 같은 키의 짝 기록(`backups/bootstrap-<UTC>-nobackup.record`, 덤프 관련 값 `none`)을 남기고 `backup_id=none` |

- 정상 배포는 대기 파일의 `none` 값을 복귀 대상으로 쓰지 않습니다. 정상 배포의 복귀 대상은 confirm이 쓴 `production.state`뿐입니다.
- **운영 정상 배포의 보호:** `deploy.sh production …`은 `production.state`가 없거나 대기 파일이 있으면 아무것도 바꾸지 않고 종료 3입니다. 그래서 bootstrap 전이나 도중에 SSM 자동 배포가 실행돼도 반드시 실패하는 첫 배포 경로로 들어가지 않습니다. 스테이징은 이 보호가 없고, 첫 배포를 정상 경로로 합니다.
- DB는 어느 단계에서도 되돌리지 않습니다. prepare가 새 마이그레이션을 적용했다면 [구 앱으로 복귀할 때](#새-마이그레이션-뒤-구-앱으로-복귀할-때)의 확인을 똑같이 따릅니다.
- 확정 뒤에는 정상 경로(`deploy.sh production …`, SSM)만 씁니다. 상태 파일이 있으면 prepare는 거부됩니다.

## 종료 코드

정상 배포(`deploy.sh <staging|production> …`)의 종료 코드입니다. bootstrap 단계별 종료 코드는 [위 표](#운영-최초-배포-bootstrap)를 봅니다.


| 코드 | 뜻 | 서비스 상태 |
|---|---|---|
| 0 | 배포 성공 | 새 digest |
| 1 | 배포 실패 뒤 복귀 실패, 복귀 대상 없음, 또는 성공 뒤 상태 기록 실패 | 사람이 바로 확인 |
| 2 | 인자 형식 오류 | 변경 없음 |
| 3 | 배포 전 단계 실패(잠금·가드·compose·상태 파일·pull·백업). 운영은 상태 파일이 없거나 bootstrap 대기 중일 때도 3 | 변경 없음 |
| 4 | 배포 실패, 이전 상태로 복귀 성공 | 이전 digest(DB는 그대로) |

## 공개 스모크 (`smoke-public.sh`)

| ID | 확인 |
|---|---|
| P1 | `GET /` 200, `Content-Type: text/html` |
| P2 | `GET /api/v1/me` 401(쿠키 없음) |
| P3 | `GET /actuator/health` 404 |
| P4 | `GET /api/v1/auth/csrf` 200. cookie-pair(첫 `;` 앞) 이름이 정확히 `__Host-BEONE_SESSION`(대소문자 구분)이고 값이 비어 있지 않은 Set-Cookie가 1줄. 그 쿠키의 속성에 `Path=/`·`Secure`·`HttpOnly`·`SameSite=Lax`가 있고 `Domain`이 없음 |

- 가입·로그인·T-a는 하지 않습니다. 운영·스테이징 DB에는 T-a를 쓰지 않습니다.
- 업무 데이터를 바꾸지 않습니다. 다만 P4 때문에 세션 저장소(`auth_sessions`)에 CSRF용 익명 세션 1행이 생길 수 있고, idle-timeout 뒤 만료로 정리됩니다(관리 판단으로 허용).
- P4의 이름 판정은 줄 안의 부분 문자열로 하지 않습니다. 따라서 다음 쿠키는 통과하지 못합니다.
  - 다른 쿠키의 값 안에 이름이 든 경우(`other=__Host-BEONE_SESSION=…`)
  - 접두어가 붙은 이름(`prefix__Host-…`)
  - 대소문자가 다른 이름
  - 속성이 다른 쿠키 줄에만 있는 경우
- 쿠키 값과 응답 본문은 출력하지 않습니다. 쿠키는 판정 결과와 무관하게 cookie-pair 값을 첫 `;` 또는 줄 끝까지 `<값>`으로 가린 뒤 출력합니다. 세미콜론 없는 값, `=`이 없는 cookie-pair, 다중 Set-Cookie도 마찬가지입니다.

## 출력과 상태 파일

- 출력은 단계·digest·백업 id뿐입니다. `docker compose config`는 `--quiet`·`--services`로만 실행해 env 값이 풀려 나오지 않게 합니다.
- `/opt/beone/deploy/state/`(권한 700): `<환경>.state`(현재 `api_digest`·`web_digest`·`git_sha`·`web_release`·`backup_id`·`deployed_at`), `<환경>.history`(성공·실패 한 줄씩), `deploy.lock`. 운영 bootstrap 중에는 `production.bootstrap`(준비한 digest·릴리스·이전 링크·`backup_id`·복귀 기준 `base_image_tag`·`base_image_id`)이 있고, 끝나면 `.confirmed-<UTC>` 또는 `.aborted-<UTC>`로 이름이 바뀝니다.
- 오래된 웹 릴리스 폴더와 이전 이미지는 지우지 않습니다. 릴리스 보존 개수 정리는 후속 과제입니다.

## 오프라인 자기검사

```bash
deploy/scripts/deploy-selftest.sh
```

가짜 `docker`·`curl`·`flock`·`sleep`을 PATH 앞에 두고, 사례마다 임시 폴더를 서버 루트로 써서 실제 `deploy.sh`·`smoke-public.sh`를 실행합니다. 각 사례의 종료 코드, 실행되면 안 되는 동작과 꼭 실행돼야 하는 동작(pull·백업·`up`·복귀), 끝난 뒤 상태 파일·웹 링크, 운영 짝 기록 내용, 중단 사유 문구, 출력(stdout·stderr)에 비밀값과 가짜 쿠키 값이 없는지를 봅니다. `smoke-public.sh` 단독 사례도 종료 코드와 함께 쿠키 값 미출력을 확인합니다. bootstrap 사례는 한 임시 폴더에서 단계(prepare → confirm·abort, 이어지는 정상 배포)를 차례로 실행합니다. 각 단계 종료 코드, 상태 파일·대기 파일, 웹 링크, 끝난 뒤 실행 중인 backend(새 이미지 또는 복귀 기준)를 확인합니다. 안내 화면만 있고 `/api`가 연결되지 않은 공개 응답에서 confirm이 상태 파일을 쓰지 않는 사례도 포함합니다. 임시 폴더는 지우지 않고 경로만 출력합니다. 실제 Docker·레지스트리·서버·네트워크에는 손대지 않습니다.
