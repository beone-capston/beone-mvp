# 배포 자동화 활성화 런북 (결정 40)

[배포 자동화(CD)](cd.md)는 저장소 변수가 꺼진 상태로 병합되어 있다. 이 문서는 승인 묶음이 내려온 뒤, 스테이징 자동 배포부터 운영 첫 승인 배포까지 **어떤 순서로 켜는지**를 적은 실행 순서표(런북)다.

- **상태:** 문서만 있다. 아래 단계는 **하나도 실행하지 않았다.** 서버·AWS·GitHub 설정은 바뀌지 않았다.
- **실행 주체:** 서버·AWS·GitHub 설정은 운영 담당(Aside)이 승인 뒤 실행한다. 비밀값 입력과 운영 승인은 사용자가 한다. 단계마다 담당을 적었다.
- **자리표시:** `<…>`는 실제 값으로 바꿀 자리다. 계정 ID·도메인·비밀번호 같은 실값은 이 문서와 저장소에 적지 않는다.
- **제외 범위:** CODEF는 자동 배포 대상이 아니다(결정 40-7). 서버 `deploy.sh`는 env에 `CODEF_`·`BEONE_CODEF_` 키가 있으면 배포를 거부한다([서버 배포 스크립트](server-deploy.md#codef-가드)). 회원 전체 스모크도 이 런북에 넣지 않는다([공개 nginx 4절 P5-4](public-proxy.md#4-반영원복-절차-승인-⑧)).

## 선행 PR

이 런북은 아래 PR의 파일을 근거로 한다. 아직 develop에 없으면 1단계에서 멈춘다. 이 PR은 그 파일들을 수정하지 않는다.

| PR | 내용 | 이 런북에서 쓰는 곳 |
|---|---|---|
| #226 | `deploy/scripts/deploy.sh`, `smoke-public.sh`, `deploy-selftest.sh`, [서버 배포 스크립트](server-deploy.md) | 2·4·8·9단계 |
| #229 | [공개 nginx 초안](public-proxy.md) 2-1절(스테이징 server 블록)·3절(네트워크 연결) | 7·8단계 |

develop에 이미 있는 근거: [배포 자동화](cd.md)(#225), [스테이징](staging.md)·`deploy/staging/`(#230), [백엔드 배포 기반](backend-compose.md).

## 용어

- **digest:** 이미지 내용의 해시(`sha256:` + 64자리). 배포는 태그가 아니라 digest로 한다([cd.md 용어](cd.md#용어)).
- **게이트(gate):** 배포 워크플로의 첫 job. 저장소 변수가 모두 맞을 때만 다음 job을 돌린다([cd.md 게이트](cd.md#게이트꺼진-상태로-병합)).
- **스모크:** 배포 직후 데이터를 바꾸지 않고 공개 경로의 응답 형태만 보는 점검(`smoke-public.sh`, P1~P4).
- **Environment `production`:** GitHub의 배포 환경 설정. 필수 승인자를 두면 운영 job이 승인 전까지 멈춘다.

## 한눈에 보기

```text
1 사전 점검 ─▶ 2 서버 스크립트 배치 ─▶ 3 스테이징 폴더 ─▶ 4 수동 1회 배포·롤백 시험
                                                              │
   ┌──────────────────────────────────────────────────────────┘
   ▼
5 AWS(OIDC·스테이징 역할·BeoneDeployStaging) ─▶ 6 GitHub 변수 → 첫 자동 배포(스테이징)
   │
   ▼
7 스테이징 공개 접근(DNS·인증서·공개 nginx, 승인) ─▶ 8 운영(역할·문서·Environment·변수·승격·승인 1회)

9 끄기와 되돌리기: 어느 단계에서든 쓴다
```

각 단계는 앞 단계의 확인값이 모두 기대대로일 때만 시작한다. 기대와 다르면 그 단계의 「실패하면」을 따르고 멈춘다.

## 공통 약속

- 서버 명령은 `sudo`로 실행하고, 값이 출력되는 명령(`cat .env`, `docker compose config` 전체 출력 등)은 쓰지 않는다.
- 명령 출력에 비밀값이 보이면 그 출력은 기록·공유하지 않는다.
- 실행 기록에는 날짜(KST), 실행자, 단계 번호, 확인값(digest·종료 코드·HTTP 상태)만 남긴다.

---

## 1. 사전 점검 (읽기만)

| 항목 | 내용 |
|---|---|
| 실행 | 운영 담당(서버), 사용자 또는 운영 담당(GitHub) |
| 전제 | 승인 묶음(결정 40 활성화)이 내려옴 |

| 확인 | 명령 | 기대 |
|---|---|---|
| 선행 PR 병합 | `gh pr view 226 --json state -q .state`, `gh pr view 229 --json state -q .state` | 둘 다 `MERGED` |
| develop에 파일 있음 | `git ls-tree --name-only origin/develop deploy/scripts/ deploy/staging/` | `deploy.sh`, `smoke-public.sh`, `compose.yaml`, `env.template`, `nginx.conf` 포함 |
| 디스크 여유 | `df -h / /var/lib/docker` | 사용률 80% 미만, 이미지 2개(api·web)와 스테이징 DB를 둘 여유(대략 5GB 이상) |
| 메모리 | `free -m` | 스테이징 상한 합계 1344MiB를 더해도 `available`이 남음([스테이징 구성](staging.md#구성)) |
| 기존 운영 앱 | `sudo docker compose -f /opt/beone/app/compose.yaml -p beone-app ps` | `backend`·`mysql`이 `running`(healthy) |
| 기존 공개 화면 | `curl -sI https://<운영 도메인>/ \| head -1` | `200` |
| 포트 충돌 없음 | `sudo ss -ltnp \| grep -E ':1808[12] '` | 출력 없음 |

**실패하면:** 아무것도 바꾸지 않았으므로 되돌릴 것이 없다. 선행 PR이 미병합이면 병합을 기다린다. 디스크·메모리가 부족하면 진행하지 않고 관리자에게 알린다.

## 2. 서버 스크립트 배치

| 항목 | 내용 |
|---|---|
| 실행 | 운영 담당(승인 뒤) |
| 전제 | 1단계 통과. 설치할 develop 커밋 `<GitSha>`를 정함 |

설치는 develop의 특정 커밋 파일을 그대로 옮기는 것이다. 먼저 PC에서 체크섬을 기록한다.

```bash
# PC, 저장소 루트
git fetch origin
for f in deploy.sh smoke-public.sh; do
  git show <GitSha>:deploy/scripts/$f | shasum -a 256    # Linux는 sha256sum
done
```

서버에 파일을 옮긴 뒤(전송 방법은 운영 담당이 정함) 설치한다.

```bash
sudo install -d -m 755 -o root -g root /opt/beone/deploy
sudo install -m 755 -o root -g root deploy.sh smoke-public.sh /opt/beone/deploy/
sudo sha256sum /opt/beone/deploy/deploy.sh /opt/beone/deploy/smoke-public.sh
ls -l /opt/beone/deploy/
sudo /opt/beone/deploy/deploy.sh; echo "exit=$?"
```

| 확인 | 기대 |
|---|---|
| 체크섬 | PC에서 기록한 값과 두 파일 모두 같음 |
| 권한·소유자 | `-rwxr-xr-x root root` |
| 인자 없이 실행 | `사용: deploy.sh …` 한 줄, 아무것도 바꾸지 않음(종료 코드 2, [종료 코드](server-deploy.md#종료-코드)) |

**실패하면:** 체크섬이 다르면 설치한 두 파일을 지우고(`sudo rm /opt/beone/deploy/deploy.sh /opt/beone/deploy/smoke-public.sh`) 다시 옮긴다. 이 단계는 실행 중인 서비스를 바꾸지 않는다.

## 3. 스테이징 폴더

| 항목 | 내용 |
|---|---|
| 실행 | 운영 담당(폴더·파일 배치), 사용자(비밀값 입력) |
| 전제 | 2단계 통과. GHCR 가시성 결정([cd.md 실행 승인 항목](cd.md#실행-승인-항목)). private이면 서버 읽기 전용 토큰 로그인을 사용자가 먼저 함 |

구성 설명은 [스테이징](staging.md)에 있다. 여기서는 순서와 확인값만 적는다.

```bash
S=/opt/beone/staging
sudo install -d -m 750 -o root -g root $S
sudo install -d -m 755 -o root -g root $S/web $S/web/releases
sudo install -m 644 -o root -g root compose.yaml nginx.conf $S/      # deploy/staging/의 <GitSha> 파일
```

**`.env`:** 키 이름은 `deploy/staging/env.template` 기준이다. 파일은 운영 담당이 권한 600으로 만들고, **값은 사용자가 서버에서 직접 입력한다.** 이 문서와 채팅에 값을 적지 않는다.

```bash
sudo install -m 600 -o root -g root env.template $S/.env    # 키 이름만 있는 틀
sudoedit $S/.env                                             # 사용자가 값 입력
# 키 이름 대조(값은 출력하지 않음)
diff <(grep -oE '^[A-Z_]+=' env.template | sort) <(sudo grep -oE '^[A-Z_]+=' $S/.env | sort) && echo "키 일치"
# 비어 있는 키 이름만 출력
sudo awk -F= '/^[A-Z_]+=/{ if ($2 == "") print "빈 값:", $1 }' $S/.env
# CODEF 키가 없어야 함
sudo grep -cE '^(BEONE_)?CODEF_' $S/.env
```

**`htpasswd`(기본 인증):** 생성 명령만 적는다. 사용자 이름과 비밀번호는 실행하는 사람이 프롬프트로 입력하고 기록하지 않는다.

```bash
sudo htpasswd -B -c $S/htpasswd <기본 인증 사용자 이름>      # 비밀번호는 프롬프트 입력
sudo chmod 640 $S/htpasswd
```

서버에 `htpasswd`가 없으면 패키지 설치가 필요하다. 패키지 설치는 별도 승인 항목이므로 이때 멈추고 알린다.

```bash
# 전체 점검(값이 풀려 나오지 않게 --quiet·--services만)
STG="sudo docker compose -f $S/compose.yaml -p beone-staging --env-file $S/.env"
$STG config --quiet && echo "config ok"
$STG config --services | sort
sudo ls -ld $S $S/.env $S/htpasswd $S/web $S/web/releases
```

| 확인 | 기대 |
|---|---|
| 키 대조 | `키 일치`, `빈 값:` 줄 없음(`BEONE_API_IMAGE`는 deploy.sh가 덮어쓰므로 첫 배포 전에는 자리값 허용) |
| CODEF 키 | `0` |
| `config --quiet` | `config ok` |
| 서비스 | `backend`, `mysql`, `web` 세 줄 |
| 권한 | 폴더 750, `.env` 600, `htpasswd` 640, `web`·`releases` 755 |

**실패하면:** 아직 컨테이너를 띄우지 않았으므로 `/opt/beone/staging`을 통째로 다른 이름으로 옮겨(`sudo mv $S $S.failed-<UTC>`) 다시 시작한다. 지우는 것은 사용자가 정한다. 운영(`/opt/beone/app`)에는 영향이 없다.

## 4. 수동 1회 배포와 롤백 시험 (스테이징)

| 항목 | 내용 |
|---|---|
| 실행 | 운영 담당(승인 뒤). 이미지 수동 푸시는 사용자 또는 운영 담당 |
| 전제 | 3단계 통과. GHCR에 api·web 이미지가 **digest로 받을 수 있게** 올라가 있음 |

**이미지 준비:** `deploy.sh`는 `ghcr.io/beone-capston/beone-api@<digest>` 형식으로 레지스트리에서 pull한다. 그래서 로컬 [`build-image.sh`](../../deploy/scripts/build-image.sh) 결과(서버에 `docker load`한 태그 이미지)는 그대로 쓸 수 없다. 빌드한 이미지를 GHCR에 수동으로 한 번 푸시해 digest를 얻는다.

- api: `build-image.sh final`로 만든 이미지에 `ghcr.io/beone-capston/beone-api:<GitSha>` 태그를 붙여 푸시한다.
- web: `frontend/`에서 `npm ci && npm run build`를 한 뒤, `frontend/dist`를 빌드 컨텍스트로 [`deploy/web/Dockerfile`](../../deploy/web/Dockerfile)을 빌드해 `beone-web:<GitSha>`로 푸시한다.
- 플랫폼은 `linux/amd64`, 증명 메타데이터 없음([cd.md 이미지](cd.md#이미지)). 푸시용 로그인 토큰은 사용자가 자기 자격으로 쓰고 기록하지 않는다.
- 푸시 결과의 `digest: sha256:…`을 `<ApiDigest1>`, `<WebDigest1>`로 기록한다. 롤백 시험을 하려면 다른 커밋으로 한 쌍(`<ApiDigest2>`, `<WebDigest2>`)을 더 만든다.

**배포 1회:**

```bash
sudo /opt/beone/deploy/deploy.sh staging <ApiDigest1> <WebDigest1> <GitSha1>; echo "exit=$?"
sudo cat /opt/beone/deploy/state/staging.state       # digest·릴리스 이름만 들어 있음
sudo /opt/beone/deploy/smoke-public.sh http://127.0.0.1:18081; echo "exit=$?"
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:18082/     # 기본 인증 리스너
```

| 확인 | 기대 |
|---|---|
| `deploy.sh` | 종료 0. 출력은 단계·digest뿐 |
| 상태 파일 | `api_digest=<ApiDigest1>`, `web_digest=<WebDigest1>`, `git_sha=<GitSha1>` |
| 스모크(18081) | P1~P4 모두 PASS, 종료 0 |
| 기본 인증 리스너(18082) | `401`(인증 없이) |
| 운영 영향 없음 | 1단계의 운영 앱·공개 화면 확인을 다시 해서 같음 |

**롤백 시험 1회:** 두 번째 쌍으로 배포한 뒤, 첫 번째 쌍으로 다시 배포한다. 수동 롤백은 "이전 digest로 `deploy.sh`를 다시 실행"하는 것이다([cd.md 롤백과 비활성화](cd.md#롤백과-비활성화)).

```bash
sudo /opt/beone/deploy/deploy.sh staging <ApiDigest2> <WebDigest2> <GitSha2>; echo "exit=$?"
sudo /opt/beone/deploy/deploy.sh staging <ApiDigest1> <WebDigest1> <GitSha1>; echo "exit=$?"
sudo grep -E '^(api_digest|git_sha)=' /opt/beone/deploy/state/staging.state
sudo tail -3 /opt/beone/deploy/state/staging.history
sudo /opt/beone/deploy/smoke-public.sh http://127.0.0.1:18081; echo "exit=$?"
```

| 확인 | 기대 |
|---|---|
| 두 실행 | 둘 다 종료 0 |
| 상태 파일 | 다시 `<ApiDigest1>`·`<GitSha1>` |
| 이력 | 성공 줄이 차례로 남음 |
| 스모크 | 종료 0 |

**실패하면:** [종료 코드](server-deploy.md#종료-코드)를 본다.

- 2·3: 바뀐 것이 없다. 원인(이미지 pull 권한, env 누락 등)을 고친 뒤 다시 한다.
- 4: 이전 상태로 복귀했다. 원인을 고친 뒤 다시 한다.
- 1: 사람이 바로 확인한다. 첫 배포에서는 복귀 대상이 없으므로 `sudo docker compose -f /opt/beone/staging/compose.yaml -p beone-staging --env-file /opt/beone/staging/.env stop`으로 스테이징만 멈춘다. `down -v`(볼륨 삭제)는 쓰지 않는다.

스테이징 DB는 운영과 별도 볼륨이므로 이 단계는 운영 DB를 바꾸지 않는다.

## 5. AWS: OIDC·스테이징 역할·SSM 문서

| 항목 | 내용 |
|---|---|
| 실행 | 운영 담당(승인 뒤) |
| 전제 | 4단계 통과. 서버 인스턴스에 SSM 에이전트가 동작하고 관리형 인스턴스로 보임 |

설정 내용(신뢰·권한 정책, SSM 문서 JSON)은 [배포 자동화](cd.md)에만 둔다. 여기에 복제하지 않는다.

1. [OIDC 공급자](cd.md#1-oidc-공급자)
2. [스테이징 역할](cd.md#2-스테이징-역할): 신뢰 `sub`는 develop 브랜치만 허용한다.
3. [SSM 문서](cd.md#4-ssm-문서-2개) 중 `BeoneDeployStaging`만 만든다. 운영 문서는 8단계에서 만든다.

| 확인 | 명령(읽기만) | 기대 |
|---|---|---|
| 관리형 인스턴스 | `aws ssm describe-instance-information --query 'InstanceInformationList[].PingStatus'` | `Online` |
| 문서 | `aws ssm describe-document --name BeoneDeployStaging --query 'Document.Status'` | `Active` |
| 역할 신뢰 | `aws iam get-role --role-name <스테이징 역할 이름> --query 'Role.AssumeRolePolicyDocument'` | `sub`가 `repo:beone-capston/beone-mvp:ref:refs/heads/develop` 하나 |
| 문서 수동 실행(선택) | 콘솔 Run Command에서 `BeoneDeployStaging`을 4단계의 `<ApiDigest1>`·`<WebDigest1>`·`<GitSha1>`로 실행 | 명령 상태 `Success`, 상태 파일 digest는 그대로(같은 digest 재배포), 이력에 성공 한 줄 추가 |

**실패하면:** 만든 IAM 역할·SSM 문서를 지운다. 아직 GitHub 변수가 없으므로 워크플로는 이 역할을 쓰지 않는다.

## 6. GitHub 변수와 첫 자동 배포 (스테이징)

| 항목 | 내용 |
|---|---|
| 실행 | 운영 담당(승인 뒤 변수 설정), 사용자 또는 관리자(develop 병합) |
| 전제 | 5단계 통과 |

[GitHub 설정](cd.md#github-설정운영-담당이-승인-뒤-설정)의 2번이다. 저장소 변수(Settings → Secrets and variables → Actions → Variables)를 넣는다. 비밀값(Secrets)에는 아무것도 넣지 않는다.

| 변수 | 값 |
|---|---|
| `AWS_REGION` | `<리전>` |
| `AWS_DEPLOY_ROLE_ARN_STAGING` | `<스테이징 역할 ARN>` |
| `BEONE_CD_ENABLED` | `true` |

`BEONE_CD_PRODUCTION_ENABLED`와 운영 ARN은 아직 넣지 않는다. 그래서 main push는 계속 `gate`에서 멈춘다.

**첫 자동 배포:** 다음 develop 병합을 기다리거나, 마지막 develop 실행의 `Deploy` 워크플로를 다시 실행한다(Actions → Deploy → Re-run all jobs).

| 확인 | 어디서 | 기대 |
|---|---|---|
| gate | job 요약 | 스테이징 켜짐, 빠진 변수 없음 |
| build | job 요약 | GHCR 푸시 성공, api·web digest 기록 |
| staging | job 로그 | OIDC 역할 맡기 성공, SSM 명령 ID, 명령 상태 `Success`, job 성공 |
| 서버 | `sudo grep -E '^(api_digest\|git_sha)=' /opt/beone/deploy/state/staging.state` | job 요약의 api digest와 develop 커밋 SHA |
| 스모크 | `sudo /opt/beone/deploy/smoke-public.sh http://127.0.0.1:18081` | 종료 0 |
| 운영 영향 없음 | 1단계 운영 확인 | 같음 |

첫 활성화에서 볼 세 가지는 [cd.md 위험과 분리 이유](cd.md#위험과-분리-이유)의 「첫 활성화」와 같다: GHCR 첫 푸시 권한, digest 기록, OIDC 역할 맡기.

**실패하면:** `BEONE_CD_ENABLED`를 지운다(9단계). 다음 push부터 `gate`만 돈다. 서버가 실패했다면 `deploy.sh`가 이미 복귀를 시도했으므로 종료 코드를 4단계 기준으로 본다. GHCR 첫 푸시가 권한으로 실패하면 패키지 권한·가시성 결정으로 돌아간다.

## 7. 스테이징 공개 접근 (승인 항목)

| 항목 | 내용 |
|---|---|
| 실행 | 사용자 승인, 운영 담당 실행 |
| 전제 | 6단계 통과. 스테이징 도메인·DNS·인증서 발급 승인 |

설정은 [공개 nginx 초안 2-1절](public-proxy.md#2-1-스테이징-server-블록-기본-인증은-스테이징-web이-함)과 [3절](public-proxy.md#3-두-compose-프로젝트-연결)을 따른다. 반영·원복은 같은 문서 [4절](public-proxy.md#4-반영원복-절차-승인-⑧)의 P3(설정 백업)·P5-1(`nginx -t`)·P5-2(reload) 형식과 같다.

1. DNS: `<스테이징 도메인>` → 서버 공인 IP.
2. 인증서: `<스테이징 도메인>` 발급(certbot). 갱신 타이머 유지 확인.
3. 공개 nginx에 2-1절 server 블록 추가, 스테이징 web 기본 인증 리스너 주소를 3절 방식으로 채움.
4. 스테이징 `.env`의 `BEONE_MAIL_LINK_BASE_URL`을 `https://<스테이징 도메인>`으로 사용자가 입력. 다음 배포부터 반영.

| 확인 | 명령 | 기대 |
|---|---|---|
| 인증 없음 | `curl -s -o /dev/null -w '%{http_code}\n' https://<스테이징 도메인>/` | `401` |
| 인증 있음 | `curl -s -o /dev/null -w '%{http_code}\n' -u '<기본 인증 사용자 이름>' https://<스테이징 도메인>/`(비밀번호 프롬프트) | `200` |
| API | 같은 인증으로 `/api/v1/me` | `401`(세션 없음) |
| actuator | 같은 인증으로 `/actuator/health` | `404` |
| 검색 차단 | `curl -sI -u '<…>' https://<스테이징 도메인>/ \| grep -i x-robots-tag` | `noindex, nofollow` |
| 점검 리스너 비공개 | 공개 주소로 18081 경로가 넘어가지 않음 | 2-1절 설정에 18081이 없음 |
| 운영 영향 없음 | `curl -sI https://<운영 도메인>/ \| head -1` | `200`, 이전과 같은 화면 |

**실패하면:** 공개 nginx 설정을 P3 백업본으로 되돌리고 `nginx -t` 뒤 reload한다([4절 원복](public-proxy.md#4-반영원복-절차-승인-⑧)). DNS·인증서는 그대로 두어도 서비스에 영향이 없다.

## 8. 운영 첫 승인 배포

| 항목 | 내용 |
|---|---|
| 실행 | 운영 담당(AWS·GitHub 설정), 사용자(Environment 승인자, 운영 배포 승인), 관리자(승격 PR) |
| 전제 | 6단계(필요하면 7단계)까지 통과. 운영 첫 배포 승인(main 승격, Flyway V2~V15, 공개 nginx 변경, [cd.md 실행 승인 항목](cd.md#실행-승인-항목)). 공개 nginx 정적 루트가 `/opt/beone/web/current`를 보도록 하는 일([공개 nginx 3절](public-proxy.md#3-두-compose-프로젝트-연결))의 승인·순서가 정해짐 |

순서가 중요하다. **Environment 보호 규칙을 먼저 만들고, 운영 스위치는 마지막에 켠다.** 보호 규칙 없이 운영 job이 돌면 GitHub이 Environment를 보호 규칙 없이 자동으로 만들어 승인 없이 배포된다([cd.md 게이트](cd.md#게이트꺼진-상태로-병합)).

1. AWS: [운영 역할](cd.md#3-운영-역할)과 [SSM 문서](cd.md#4-ssm-문서-2개) `BeoneDeployProduction`을 만든다. 신뢰 `sub`는 `environment:production` 하나다.
2. GitHub Environment `production`: 필수 승인자는 사용자, 배포 브랜치는 `main`만([GitHub 설정 1번](cd.md#github-설정운영-담당이-승인-뒤-설정)).
3. 저장소 변수 `AWS_DEPLOY_ROLE_ARN_PRODUCTION=<운영 역할 ARN>`.
4. 저장소 변수 `BEONE_CD_PRODUCTION_ENABLED=true`.
5. develop → main 승격 PR을 만들고 병합한다(관리자, [CONTRIBUTING](../../CONTRIBUTING.md)).
6. `Deploy` 실행의 `production` job이 승인 대기에 들어가면 사용자가 **한 번** 승인한다.

| 확인 | 어디서 | 기대 |
|---|---|---|
| 보호 규칙 | Settings → Environments → production | 필수 승인자 사용자, 배포 브랜치 `main` 하나 |
| 승인 대기 | Actions 실행 화면 | `production` job이 `Waiting`(승인 전 SSM 호출 없음) |
| 운영 역할 신뢰 | `aws iam get-role --role-name <운영 역할 이름> --query 'Role.AssumeRolePolicyDocument'` | `sub`가 `repo:beone-capston/beone-mvp:environment:production` 하나 |
| 배포 | job 로그 | SSM 명령 상태 `Success`, job 성공 |
| 배포 직전 백업 | `sudo grep -E '^backup_id=' /opt/beone/deploy/state/production.state`, `sudo ls /opt/beone/app/backups/<backup_id>/` | `app.sql`, `app.sql.flyway`, `app.sql.record` 세 파일 |
| 짝 기록 | `sudo grep -E '^deploy_(prev_api\|new_api\|new_web)_digest=\|^deploy_git_sha=' /opt/beone/app/backups/<backup_id>/app.sql.record` | 새 digest가 job 요약과 같음. 첫 자동 배포면 `deploy_prev_api_digest=none` |
| 스모크 | `sudo /opt/beone/deploy/smoke-public.sh https://<운영 도메인>` | 종료 0 |
| 웹 | `sudo test -f /opt/beone/web/current/index.html && echo ok` | `ok` |

운영의 첫 자동 배포는 상태 파일이 없어 복귀 대상이 없다([첫 배포](server-deploy.md#첫-배포)). 지금 운영 backend는 `docker load`한 태그 이미지이므로, 승인 시각에 운영 담당과 사용자가 함께 지켜본다.

**실패하면:**

- 승인 전이면 승인 화면에서 **거절**한다. 아무것도 배포되지 않는다.
- `deploy.sh`가 종료 4면 이전 backend와 웹 링크로 복귀했다. DB는 새 마이그레이션이 적용된 상태일 수 있다(아래 9단계 DB 항목).
- 종료 1이면 사람이 바로 확인한다. backend 이미지는 [백엔드 배포 기반 롤백](backend-compose.md#롤백)처럼 `docker load`했던 직전 태그로 되돌린다.
- 운영 자동 배포를 멈추려면 `BEONE_CD_PRODUCTION_ENABLED`를 지운다.

## 9. 끄기와 되돌리기

| 항목 | 내용 |
|---|---|
| 실행 | 운영 담당(변수·SSM), 사용자(운영 결정) |
| 전제 | 없음. 어느 단계에서든 쓴다 |

| 하려는 것 | 방법 | 확인 |
|---|---|---|
| 자동 배포 전체 즉시 정지 | 저장소 변수 `BEONE_CD_ENABLED`를 지우거나 `false`로 바꿈 | 다음 push의 `Deploy` 실행이 `gate`만 성공, 요약에 비활성 표시 |
| 운영만 정지 | `BEONE_CD_PRODUCTION_ENABLED` 삭제 | main push가 `gate`에서 멈춤 |
| 더 강하게 막기 | Actions에서 `Deploy` 워크플로 비활성화, 또는 IAM 역할 신뢰 정책 삭제 | 실행 없음 / 역할 맡기 실패 |
| 진행 중 운영 배포 막기 | Environment 승인 화면에서 거절 | job `Rejected` |
| 이전 버전 재배포 | 되돌릴 커밋 실행의 job 요약에서 api·web digest와 GitSha를 확인하고, 해당 환경 문서(`BeoneDeployStaging`/`BeoneDeployProduction`)를 그 값으로 실행(콘솔 또는 `aws ssm send-command`). "Re-run jobs"는 이미지를 다시 빌드해 digest가 달라질 수 있으므로 쓰지 않음 | 명령 상태 `Success`, 상태 파일 digest가 되돌린 값, 스모크 종료 0 |
| DB 되돌리기 | **자동 복원 없음.** 운영 배포 직전 백업(`/opt/beone/app/backups/<backup_id>/`)과 짝 기록으로 [복원 확인](backend-compose.md#복원-확인-회차별-분리-프로젝트) 절차를 따름. 운영 DB 교체는 별도 결정 | 복원 회차 프로젝트에서 이력·앱 기동 확인 |

새 마이그레이션 뒤 구 앱으로 되돌릴 때의 제약(추가만 한 마이그레이션은 구 앱이 기동, 이름 변경·삭제는 기동 실패)은 [서버 배포 스크립트](server-deploy.md#새-마이그레이션-뒤-구-앱으로-복귀할-때)에 있다.

## 승인 항목 대조

| 단계 | 승인 항목([cd.md](cd.md#실행-승인-항목)) |
|---|---|
| 2 | 서버 스크립트 설치 |
| 3 | 스테이징 `/opt/beone/staging/` 구성, 기본 인증 값, GHCR 가시성(사용자 결정) |
| 4 | GHCR 수동 푸시(가시성 결정 뒤) |
| 5 | OIDC·IAM(스테이징), SSM 문서 `BeoneDeployStaging` |
| 6 | 저장소 변수(스테이징 3개) |
| 7 | DNS, 인증서, 공개 nginx 변경 |
| 8 | OIDC·IAM(운영), `BeoneDeployProduction`, Environment 보호 규칙, 저장소 변수(운영 2개), 운영 첫 배포(main 승격, Flyway, 공개 nginx), 실메일 |
