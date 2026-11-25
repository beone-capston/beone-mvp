# 배포 자동화(CD): 스테이징 자동 반영과 운영 승인 배포

[결정 40](../decisions.md#결정-40-배포-자동화스테이징-자동-반영-운영은-승인-1회)을 구현한 워크플로와 그것을 켜기 위해 필요한 AWS·GitHub 설정을 설명한다. 이 문서의 AWS·GitHub 설정은 **아직 하나도 만들지 않았다.** 아래 「실행 승인 항목」이 모두 승인되고 운영 담당이 설정하기 전까지 워크플로는 꺼진 상태로 동작한다.

- 워크플로: [`.github/workflows/deploy.yml`](../../.github/workflows/deploy.yml)
- SSM 요청·대기 스크립트: [`.github/scripts/ssm-deploy.sh`](../../.github/scripts/ssm-deploy.sh)
- 웹 이미지 정의: [`deploy/web/Dockerfile`](../../deploy/web/Dockerfile)
- 서버 배포 스크립트: `deploy/scripts/deploy.sh`(별도 PR, 서버에는 `/opt/beone/deploy/deploy.sh`로 설치)
- PR CI는 별도 워크플로 `ci.yml`이다. 비밀값 없이 PR마다 돈다.

## 용어

- **GHCR:** GitHub 컨테이너 저장소(`ghcr.io`). 빌드한 이미지를 올려 두는 곳이다.
- **digest:** 이미지 내용의 해시(`sha256:` + 64자). 태그와 달리 바뀌지 않으므로 배포는 digest로 한다.
- **OIDC:** GitHub 실행이 장기 키 없이 AWS 역할을 잠깐 빌리는 방식. AWS는 GitHub이 발급한 토큰의 `sub`(어느 저장소·브랜치·환경에서 왔는지)를 보고 허용한다.
- **SSM Run Command:** AWS가 서버의 에이전트에게 정해진 문서(명령)를 실행시키는 기능. SSH가 필요 없다.

## 흐름

```text
PR ──▶ ci.yml (backend test·webTest, frontend lint·test·build)          외부 작업 없음
 │
 ▼ 병합
develop push ──▶ deploy.yml
                  gate ── 변수 꺼짐 ──▶ "비활성" 요약만 남기고 성공(여기서 끝)
                   │ 켜짐
                   ▼
                  build: api·web 이미지 빌드 → GHCR 푸시 → digest 기록
                   │
                   ▼
                  staging: OIDC로 역할 획득 → SSM BeoneDeploy(Environment=staging, digest 2개, GitSha)
                           → 서버 deploy.sh 끝날 때까지 대기, 실패면 job 실패

main push ────▶ deploy.yml
                  gate → build → production: Environment production 승인(사용자 1회)
                           → OIDC → SSM BeoneDeploy(Environment=production, …) → 대기
```

서버 쪽 `deploy.sh`가 하는 일(인터페이스 계약, 자세한 동작은 그 PR 기준): digest로 이미지를 받는다. 운영이면 배포 직전에 DB를 백업한다([backend-compose](backend-compose.md) 방식). 그다음 기동, health 확인, 공개 점검 스모크를 한다. 실패하면 이전 digest와 이전 웹 릴리스 링크로 되돌린다. DB는 자동으로 되돌리지 않는다.

## 게이트(꺼진 상태로 병합)

`gate` job이 저장소 변수를 읽어 다음 job을 돌릴지 정한다. 변수는 비밀이 아닌 값만 둔다.

| 저장소 변수 | 값 | 없거나 틀리면 |
|---|---|---|
| `BEONE_CD_ENABLED` | 정확히 `true` | 빌드·배포 모두 건너뜀 |
| `AWS_DEPLOY_ROLE_ARN` | `arn:aws:iam::<12자리>:role/<이름>` | 빌드·배포 모두 건너뜀 |
| `AWS_REGION` | 서버 인스턴스 리전(예: `ap-northeast-2` 형식) | 빌드·배포 모두 건너뜀 |
| `BEONE_CD_PRODUCTION_ENABLED` | 정확히 `true` | 운영 job만 건너뜀 |

- 변수가 없으면 `gate`만 돌고 성공한다. 이때 GHCR 푸시, AWS 호출, 서버 배포는 일어나지 않는다. 그래서 워크플로를 병합하는 것만으로는 아무것도 배포되지 않는다.
- `AWS_REGION`과 `BEONE_CD_PRODUCTION_ENABLED`는 결정 40 계약의 두 변수에 더한 것이다. 리전은 AWS 호출에 꼭 필요하다. 운영 변수를 따로 둔 이유는 아래와 같다. GitHub은 `environment: production` job이 처음 돌 때 그 Environment가 없으면 **보호 규칙 없이** 자동으로 만든다. 그러면 승인 없이 운영에 배포된다. 그래서 운영은 Environment 보호 규칙을 먼저 설정한 뒤에만 켜도록 따로 막았다.

## 권한과 고정

| job | 권한 | 비고 |
|---|---|---|
| `gate` | `contents: read` | 변수만 읽는다 |
| `build` | `contents: read`, `packages: write` | GHCR 로그인은 실행마다 자동 발급되는 `GITHUB_TOKEN`만 쓴다 |
| `staging` | `contents: read`, `id-token: write` | `environment`를 두지 않는다(아래 신뢰 정책의 `sub`와 맞추기 위해) |
| `production` | `contents: read`, `id-token: write` | `environment: production` |

- 동시 실행: `deploy-staging`, `deploy-production` 그룹으로 환경마다 하나만 돈다. 진행 중인 배포는 취소하지 않고, 다음 배포가 기다린다.
- 서드파티 action은 커밋 SHA로 고정했다. `actions/checkout` v7.0.1, `actions/setup-node` v7.1.0, `aws-actions/configure-aws-credentials` v6.3.0.
- digest 값은 `run:` 안에 식으로 넣지 않고 `env`로 넘긴다. `ssm-deploy.sh`가 형식을 다시 검사한다.

## 이미지

| 이미지 | 내용 | 빌드 방식 |
|---|---|---|
| `ghcr.io/beone-capston/beone-api` | 백엔드 실행 이미지 | [`build-image.sh`](../../deploy/scripts/build-image.sh)와 같은 방식이다. `backend/Dockerfile`, `linux/amd64` 단일 플랫폼, 증명 메타데이터 없음, 리비전 라벨에 전체 SHA |
| `ghcr.io/beone-capston/beone-web` | frontend `dist`만(`/dist`) | [`deploy/web/Dockerfile`](../../deploy/web/Dockerfile), `scratch` 기반 |

- 태그는 커밋 전체 SHA이고, 배포는 digest로 한다. digest는 job 요약과 output에 남긴다.
- 웹 이미지를 `scratch`로 만든 이유: 웹은 기존 공개 nginx가 서빙한다. 그래서 이미지는 파일을 옮기는 용도일 뿐이다. 셸·패키지가 없으면 받는 크기와 취약점 대상이 가장 작다. 서버는 `docker create`로 컨테이너를 만들기만 하고(실행하지 않음) `docker cp`로 `/dist`를 꺼낸다. `docker create`가 명령 없는 이미지를 거부하므로 자리표시 `CMD`만 넣었다.
- frontend 빌드는 워크플로가 `npm ci`·`npm run build`로 한다. frontend 코드는 바꾸지 않는다.

## AWS 설정 템플릿(운영 담당이 승인 뒤 설정)

`<ACCOUNT_ID>`, `<REGION>`은 자리표시다. 실제 값은 저장소에 적지 않는다(비밀은 아니지만 공개 저장소에 계정 식별자를 늘리지 않는다).

### 1. OIDC 공급자

IAM에 `token.actions.githubusercontent.com` OIDC 공급자를 추가한다. 대상(audience)은 `sts.amazonaws.com`이다.

### 2. 역할 신뢰 정책

develop 브랜치의 실행(스테이징)과 Environment production의 실행(운영)만 역할을 맡을 수 있다.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::<ACCOUNT_ID>:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
          "token.actions.githubusercontent.com:sub": [
            "repo:beone-capston/beone-mvp:ref:refs/heads/develop",
            "repo:beone-capston/beone-mvp:environment:production"
          ]
        }
      }
    }
  ]
}
```

- 스테이징 job에 `environment`를 붙이면 `sub`가 `…:environment:<이름>`으로 바뀌어 위 조건과 맞지 않는다. 그래서 스테이징 job에는 `environment`를 두지 않았다.
- 저장소나 조직에서 OIDC `sub` 형식을 바꿨다면(사용자 지정 클레임) 위 값도 같이 바꿔야 한다. 기본 형식을 가정했다.

### 3. 역할 권한 정책

`ssm:SendCommand`는 서버 인스턴스 1대와 문서 `BeoneDeploy` 1개로만 제한한다. `ssm:GetCommandInvocation`은 리소스 수준 제한을 지원하지 않아 `*`이며, 조회만 할 수 있다.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "SendBeoneDeployToOneInstance",
      "Effect": "Allow",
      "Action": "ssm:SendCommand",
      "Resource": [
        "arn:aws:ec2:<REGION>:<ACCOUNT_ID>:instance/i-02c6e351ba6662e2e",
        "arn:aws:ssm:<REGION>:<ACCOUNT_ID>:document/BeoneDeploy"
      ]
    },
    {
      "Sid": "ReadCommandResult",
      "Effect": "Allow",
      "Action": "ssm:GetCommandInvocation",
      "Resource": "*"
    }
  ]
}
```

이 역할에는 EC2·IAM·S3·SG 권한을 주지 않는다. 서버 안에서 하는 일은 문서가 실행하는 `deploy.sh`로만 한정된다.

### 4. SSM 문서 `BeoneDeploy`

문서는 `/opt/beone/deploy/deploy.sh` 하나만 실행한다. 파라미터는 `allowedPattern`으로 형식을 고정해, 셸에 들어가는 값이 정해진 문자만 갖게 한다.

```json
{
  "schemaVersion": "2.2",
  "description": "BeONE 배포: 지정 digest로 deploy.sh 실행(결정 40)",
  "parameters": {
    "Environment": {
      "type": "String",
      "allowedValues": ["staging", "production"]
    },
    "ApiDigest": {
      "type": "String",
      "allowedPattern": "^sha256:[0-9a-f]{64}$"
    },
    "WebDigest": {
      "type": "String",
      "allowedPattern": "^sha256:[0-9a-f]{64}$"
    },
    "GitSha": {
      "type": "String",
      "allowedPattern": "^[0-9a-f]{40}$"
    }
  },
  "mainSteps": [
    {
      "action": "aws:runShellScript",
      "name": "deploy",
      "inputs": {
        "timeoutSeconds": "1500",
        "runCommand": [
          "/opt/beone/deploy/deploy.sh '{{ Environment }}' '{{ ApiDigest }}' '{{ WebDigest }}' '{{ GitSha }}'"
        ]
      }
    }
  ]
}
```

- 인자 순서(`Environment`, `ApiDigest`, `WebDigest`, `GitSha`)는 서버 `deploy.sh` PR의 인자 계약과 같아야 한다. 그 PR이 다른 형식을 쓰면 이 문서와 `runCommand`를 그쪽에 맞춘다.
- `timeoutSeconds`(1500초)는 워크플로 대기 시간(`ssm-deploy.sh` 기본 1800초)보다 짧게 둔다. 그래서 서버 쪽 시간 초과가 먼저 `TimedOut`으로 보고된다.
- 문서와 `deploy.sh`를 바꾸는 것도 승인 절차를 거친다(결정 40).

## GitHub 설정(운영 담당이 승인 뒤 설정)

1. Environment `production`을 만들고 필수 승인자를 사용자로 정한다. 배포 브랜치는 `main`으로만 제한하는 것을 권장한다.
2. 그다음 저장소 변수 `AWS_DEPLOY_ROLE_ARN`, `AWS_REGION`, `BEONE_CD_ENABLED=true`를 설정한다(스테이징·빌드가 켜진다).
3. 운영 첫 배포 승인 뒤 `BEONE_CD_PRODUCTION_ENABLED=true`를 설정한다.

GitHub에는 비밀값을 두지 않는다. DB 비밀번호, 메일 앱 비밀번호, CODEF 키는 서버 `.env`에만 둔다. 역할 ARN과 리전은 비밀이 아니라서 변수로 둔다.

## 실행 승인 항목

| 항목 | 내용 | 담당 |
|---|---|---|
| OIDC·IAM | OIDC 공급자, 배포 역할(위 신뢰·권한 정책), SSM 문서 `BeoneDeploy` 등록 | 운영 담당(승인 뒤) |
| Environment 보호 규칙 | `production` 필수 승인자(사용자), 배포 브랜치 `main` | 운영 담당(승인 뒤) |
| 저장소 변수 | 위 4개. 운영 변수는 운영 첫 배포 승인 뒤에 설정 | 운영 담당(승인 뒤) |
| 스테이징 | DNS, 인증서, 기본 인증 값, `/opt/beone/staging/` 구성 | 운영 담당(승인 뒤) |
| GHCR 가시성 | public 또는 private과 서버 읽기 전용 토큰(**결정 필요**, 관리 권장 public) | 사용자 결정 |
| 서버 스크립트 설치 | `/opt/beone/deploy/deploy.sh` 설치·갱신 | 운영 담당(승인 뒤) |
| 운영 첫 배포 | `main` 승격, Flyway V2~V15, 공개 Nginx 변경 | 사용자 승인, 운영 담당 실행 |
| 실메일 | Gmail 앱 비밀번호를 서버 `.env`에 입력(결정 39-6) | 사용자 |

GHCR 가시성 참고: 처음 푸시한 패키지는 기본이 private이다. private으로 두면 서버에 `read:packages` 토큰이 필요하고, 그 토큰은 서버에만 둔다. public으로 바꾸는 것은 패키지 설정에서 하는 승인 항목이다.

## 롤백과 비활성화

- **자동 복귀:** 서버 `deploy.sh`가 health 또는 스모크 실패 시 이전 digest와 이전 웹 링크로 되돌린다. 이때 워크플로 job은 실패로 끝난다.
- **이전 버전으로 수동 복귀:** 되돌릴 커밋의 job 요약에서 digest를 확인한다. 그다음 운영 담당이 같은 `BeoneDeploy` 문서를 그 digest로 실행한다(콘솔 또는 `aws ssm send-command`). 이전 실행의 "Re-run jobs"는 이미지를 다시 빌드하므로 digest가 같다고 보장할 수 없다. 그래서 digest를 직접 넘기는 쪽을 쓴다.
- **DB:** 자동으로 되돌리지 않는다. 운영 배포 직전 백업으로 [backend-compose](backend-compose.md)의 복원 절차를 따른다.
- **배포 자동화 끄기:** 저장소 변수 `BEONE_CD_ENABLED`를 지우거나 `true`가 아닌 값으로 바꾼다. 다음 push부터 `gate`만 돈다. 운영만 끄려면 `BEONE_CD_PRODUCTION_ENABLED`를 지운다. 더 강하게 막으려면 Actions에서 `Deploy` 워크플로를 비활성화하거나, IAM 역할 신뢰 정책을 지운다.
- **진행 중 운영 배포 막기:** 승인 대기 중이면 Environment 승인 화면에서 거절한다.

## 제외 범위

- CODEF는 자동 배포 대상이 아니다. 공개 경로·키 차단(결정 38-8, D9)은 그대로다.
- 스테이징 compose·서버 디렉터리 구성은 서버 스크립트 PR과 공개 nginx 문서([public-proxy](public-proxy.md))에서 다룬다.
- 회원 전체 스모크는 실메일 인증 경로가 생긴 뒤 따로 한다. 자동 배포 스모크는 데이터를 바꾸지 않는 공개 점검이다.
