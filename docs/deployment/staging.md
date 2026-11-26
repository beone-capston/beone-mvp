# 스테이징 (`beone-staging`)

develop에 병합된 변경을 사용자가 직접 배포하지 않아도 확인할 수 있게, 같은 서버에 운영과 분리된 스테이징을 둡니다. 결정 40(배포 자동화)의 (4)(6)을 따릅니다. 배포는 [서버 배포 스크립트](server-deploy.md)의 `deploy.sh staging …`이 합니다.

- **상태:** 2026-11-26 사용자 승인 아래 운영 담당(Aside)이 서버 배치, DNS·인증서, 기본 인증, 공개 nginx 연결을 마쳤고 develop 자동 배포가 성공했습니다. 공개 주소는 `https://staging.beonepay.site`(기본 인증)입니다. 단계별 결과는 [활성화 런북 현재 상태](cd-activation.md#현재-상태-2026-11-26-기준)에 있습니다.
- **파일:** [`deploy/staging/compose.yaml`](../../deploy/staging/compose.yaml), [`deploy/staging/nginx.conf`](../../deploy/staging/nginx.conf), [`deploy/staging/env.template`](../../deploy/staging/env.template)(키 이름만)

## 구성

```text
서버 /opt/beone/staging        권한 750
├── compose.yaml   저장소 deploy/staging/compose.yaml
├── nginx.conf     저장소 deploy/staging/nginx.conf
├── .env           서버에서 작성, 권한 600(env.template의 키)
├── htpasswd       기본 인증 파일(사용자·해시), 권한 640 root:101. 저장소에 두지 않음
└── web/           deploy.sh가 관리(releases/<릴리스>, current 링크)
```

| 서비스 | 이미지 | 메모리 상한 | `oom_score_adj` | 호스트 포트 |
|---|---|---|---|---|
| `mysql` | `mysql:8.4.11`(서버에 있는 이미지) | 512MiB | 800 | 없음 |
| `backend` | `ghcr.io/beone-capston/beone-api@<digest>`(deploy.sh가 `BEONE_API_IMAGE`로 덮어씀) | 768MiB | 900 | 없음 |
| `web` | 서버에 있는 nginx 이미지(`BEONE_STAGING_NGINX_IMAGE`) | 64MiB | 900 | `127.0.0.1:18082`(기본 인증), `127.0.0.1:18081`(배포 스모크 전용) |

- **분리:** 프로젝트 이름 `beone-staging`, 네트워크 `beone-staging_staging`, 볼륨 `beone-staging_staging-mysql-data`. 운영(`beone-app`)·검증(`beone-verify`)과는 공유하지 않습니다. 공개 nginx(`beone`)는 스테이징 web(`beone-staging-web-1:8080`)으로 요청을 넘기려고 `beone-staging_staging`에 외부 네트워크로 연결되어 있습니다(2026-11-26, [공개 nginx 2-1절](public-proxy.md#2-1-스테이징-server-블록-기본-인증은-스테이징-web이-함)). 볼륨은 공유하지 않습니다.
- **메모리:** 상한 합계 1344MiB(mysql 512 + backend 768 + web 64)이며 검증 환경 `beone-verify`(PR #206 `deploy/verify/compose.yaml`)와 같은 배분입니다. 스테이징과 `beone-verify`는 동시에 띄우지 않습니다.
- **먼저 종료:** 운영 컨테이너는 `oom_score_adj`가 기본값 0입니다. 스테이징은 800·900이라 서버 메모리가 모자라면 커널이 스테이징을 먼저 종료합니다. 재시작은 `on-failure:3`으로 세 번까지만 해서 운영 메모리를 계속 빼앗지 않게 했습니다. 서버 재부팅 뒤에는 자동으로 뜨지 않고 다음 배포(`up -d`)가 다시 띄웁니다.
- **이미지:** 세 서비스 모두 `pull_policy: never`입니다. backend는 `deploy.sh`가 digest로 먼저 pull하고, mysql·nginx는 서버에 있는 이미지를 씁니다(추가 다운로드 없음).
- **CODEF:** 기본은 꺼짐이며 키를 넣지 않습니다(결정 40-7). 시연용으로 스테이징에서만 켤 수 있습니다(#275, 2026-11-27 사용자 결정, 아직 켜지 않음). `.env`에 `BEONE_STAGING_CODEF_ENABLED=true`와 필수 키를 모두 넣으면 `deploy.sh`가 오버레이 [`deploy/staging/compose.codef.yaml`](../../deploy/staging/compose.codef.yaml)을 붙입니다. 플래그가 없거나 `false`인데 `CODEF_`·`BEONE_CODEF_` 키 이름이나 `codef-live`가 있으면 지금처럼 배포를 거부합니다. 절차는 [스테이징 한정 CODEF](staging-codef.md)에 있습니다.

## nginx

| 리스너 | 접근 | 내용 |
|---|---|---|
| 8080 → `127.0.0.1:18082` | 기본 인증(`auth_basic`, `/etc/nginx/htpasswd`) | `/`는 `/srv/web/current`(SPA), `/api/`는 backend, `/actuator/` 404 |
| 8081 → `127.0.0.1:18081` | 인증 없음, 서버 안에서만 | `deploy.sh` 공개 스모크 전용. `/`, `/api/v1/me`, `/api/v1/auth/csrf`만 열고 나머지는 404 |

- **N2 조건:** `/email-verification`·`/password-reset`에 `Referrer-Policy: no-referrer`를 붙이고, 이 경로의 접근 로그는 `$request` 대신 `$uri`를 쓰는 형식으로 남겨 `?token=`이 로그에 들어가지 않게 합니다.
- 전달 헤더(`X-Forwarded-For`·`X-Forwarded-Proto`)는 덮어쓰고 `Forwarded`는 비웁니다. 기본 인증의 `Authorization` 헤더는 backend로 넘기지 않습니다.
- 본문 크기 64k는 N4 결정 전 검증 환경과 같은 값입니다.
- 8081은 기본 인증이 없으므로 점검 경로만 엽니다. 호스트에서는 127.0.0.1에만 묶여 서버 밖에서 닿지 않습니다.
- **`htpasswd` 권한:** 컨테이너는 파일을 `/etc/nginx/htpasswd`로 읽기 전용 마운트하고, 요청을 처리하는 nginx 워커는 uid/gid 101로 돕니다. 그래서 `640 root:101`로 둡니다. `root:root`면 워커가 읽지 못해 기본 인증이 동작하지 않습니다.

## 프로필과 메일 (권장안)

**권장: 운영과 같은 `prod` 프로필 + 실메일.** compose는 `SPRING_PROFILES_ACTIVE: prod,staging`이고 메일 키 6개를 필수(`:?`)로 받습니다. `staging`은 설정 파일이 없는 스테이징 표지 프로필로, `PublicProfileGuard`가 스테이징 한정 CODEF 예외를 판단할 때만 씁니다(#275). 운영 배포는 프로필 지정에 `staging`이 있으면 거부합니다.

| 근거 | 내용 |
|---|---|
| 운영과 같은 경로 확인 | 스테이징의 목적이 develop 변경을 운영과 같은 조건에서 미리 보는 것입니다. `prod`는 `member` 부품, 쿠키·전달 헤더 설정, 실메일 발송기(`SmtpAuthMailSender`)를 켭니다. `member`만 쓰면 메일이 가짜 발송기로 가서 가입·재설정 흐름을 끝까지 볼 수 없습니다 |
| 운영 DB T-a 금지와 정합 | 스테이징도 운영처럼 T-a를 쓰지 않으므로 이메일 인증은 실메일로만 끝낼 수 있습니다 |
| 메일 키 누락 시 기동 실패 | `prod`는 메일 키가 하나라도 없으면 기동하지 않습니다(결정 39-6). 배포 health가 실패해 자동 복귀하므로 잘못된 설정이 반영된 채 남지 않습니다 |

고려할 점(최종 확정은 PR 본문에서):
- **Gmail 발송 한도:** Gmail 일반 계정은 하루 발송 수 제한이 있습니다(Google 공개 안내 기준 약 500명, Workspace는 더 큼, 변경될 수 있음). 운영과 같은 계정을 쓰면 한도를 나눠 씁니다. 팀 확인 용도라 발송량은 적지만, 운영 메일을 보호하려면 **스테이징 전용 발신 계정**을 권장합니다.
- **수신자 제한:** 스테이징은 기본 인증 뒤에 있어 가입 요청을 보낼 수 있는 사람이 팀으로 제한됩니다. 앱 자체에는 수신 도메인 제한이 없으므로 기본 인증 계정은 팀원에게만 줍니다.
- **링크 주소:** `BEONE_MAIL_LINK_BASE_URL`은 `https://`로 시작해야 합니다. 현재 값은 스테이징 공개 주소 `https://staging.beonepay.site`입니다.
- **현재 메일 설정:** 2026-11-26 구성에서는 메일 키를 운영 `.env`에서 서버 안에서 복사했습니다. 즉 지금은 운영과 같은 발신 계정을 쓰며, 위 스테이징 전용 발신 계정 권장안은 아직 적용하지 않았습니다.

## 서버 배치 (2026-11-26 완료)

1. `/opt/beone/staging`(750)과 `web/`(755)을 만들고 compose·nginx 파일을 둡니다.
2. `.env`를 600으로 만들고 `env.template`의 키를 채웁니다(값은 출력하지 않음). `docker compose … config --quiet`로 누락만 확인합니다.
3. 기본 인증 파일 `htpasswd`를 만듭니다. 서버에 `htpasswd` 도구가 없으므로 `openssl passwd -6`(SHA-512 crypt)으로 해시를 만들고 권한은 `640 root:101`로 둡니다(명령은 [활성화 런북 3단계](cd-activation.md#3-스테이징-폴더)). 저장소에 두지 않습니다.
4. 첫 배포는 `deploy.sh staging <ApiDigest> <WebDigest> <GitSha>`로 합니다. 상태 파일이 없으므로 첫 배포로 처리됩니다.
5. 외부 공개(스테이징 도메인 → 공개 nginx → 스테이징 `web`): 공개 nginx 컨테이너는 호스트 127.0.0.1 포트에 닿지 않으므로 네트워크로 연결했습니다. 공개 nginx에 외부 네트워크 `beone-staging_staging`을 더하고, 별도 파일 `conf.d/staging.conf`에서 `beone-staging-web-1:8080`으로 넘깁니다. 이름은 요청 때 찾게 해서 스테이징이 꺼져 있어도 공개 nginx 기동·reload가 실패하지 않습니다([공개 nginx 2-1절](public-proxy.md#2-1-스테이징-server-블록-기본-인증은-스테이징-web이-함)).
