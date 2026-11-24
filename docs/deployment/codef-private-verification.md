# CODEF 비공개 실연동 검증 런북 (`beone-codef`)

공개 서비스와 분리된 루프백 검증 환경에서 실제 CODEF에 **연결 1회와 동기화 1회**를 하고, 정제된 결과만 검증 DB에 남는지 확인한 뒤 폐기하는 절차입니다.

- **근거:** 결정 38-8(CODEF는 공개 환경에 등록하지 않음), 결정 39-4(비공개 실연동 승인. 로컬 검증 때와 같은 키·KB 계정으로 연결 1회·동기화 1회), ROADMAP-next-20261125 6절(K0~K7)·7절 승인 ⑨~⑪
- **N14(호출 상한):** API별 최대 **3회**(결정 39-4)
- **N15(보존 기간):** 정제 데이터(비밀번호·카드번호·승인번호 원문 제외)는 검증 DB에 **MVP 시연이 끝날 때까지** 보관하고, **MVP 수락 뒤 폐기**합니다(결정 39-4)
- **실행 주체:** 서버 명령은 운영 담당(SSM), 키 입력은 사용자입니다. 에이전트는 키 값을 보지 않습니다.
- **상태:** 문서만 있습니다. 서버 배치와 실호출은 하지 않았습니다. 서버 배치는 `beone-verify`를 정리한 뒤에만 합니다.

## 1. 코드와 프로필 (K0)

| 항목 | 내용 | 상태 |
|---|---|---|
| 실 게이트웨이 | `LiveCodefGateway`, 프로필 조건 `member & codef-live & !prod`([codef 문서](../domains/codef.md#비공개-실연동-프로필-codef-live-lp-1-193)). `prod`와 함께 켜면 `PublicProfileGuard`가 기동을 멈춤 | develop 병합(#195) |
| 호출 상한 카운터 | 프로세스를 넘는 호출 총량(LP-2) | PR #205 진행 중. 설정 이름은 병합된 코드 기준으로 이 문서에 채운다 |
| 키 환경 변수 | `CODEF_CLIENT_ID`, `CODEF_CLIENT_SECRET`, `CODEF_PUBLIC_KEY`, `BEONE_CODEF_CONNECTED_ID_KEY`, `BEONE_CODEF_APPROVAL_HMAC_KEY`(이름만) | 코드 기준(#195) |

## 2. beone-codef 실행안

### 2-1. 환경 (K1, 승인 ⑨)

- `beone-verify`를 [검증 런북](../../deploy/verify/RUNBOOK.md) 9절대로 **먼저 정리**합니다. 서버 메모리 때문에 두 검증 환경을 동시에 띄우지 않습니다.
- 같은 compose를 별도 프로젝트 이름 `beone-codef`로 띄웁니다. 빈 DB, 별도 볼륨, `127.0.0.1:18080`, 메모리 상한과 중단 조건은 검증 런북과 같습니다.
- 백엔드에만 CODEF 프로필과 키 이름을 넘기는 덮어쓰기 파일(`/opt/beone/codef/compose.codef.yaml`) 초안은 아래와 같습니다. 값은 쓰지 않고 `.env`에서 읽습니다.

```yaml
# /opt/beone/codef/compose.codef.yaml (초안)
services:
  verify-backend:
    environment:
      SPRING_PROFILES_ACTIVE: member,codef-live      # prod 아님
      CODEF_CLIENT_ID: ${CODEF_CLIENT_ID:?}
      CODEF_CLIENT_SECRET: ${CODEF_CLIENT_SECRET:?}
      CODEF_PUBLIC_KEY: ${CODEF_PUBLIC_KEY:?}
      BEONE_CODEF_CONNECTED_ID_KEY: ${BEONE_CODEF_CONNECTED_ID_KEY:?}
      BEONE_CODEF_APPROVAL_HMAC_KEY: ${BEONE_CODEF_APPROVAL_HMAC_KEY:?}
      # LP-2 상한 설정: #205 병합 뒤 이름을 채운다(API별 3회, N14)
```

```bash
exec bash   # SSM 기본 셸은 sh다
C='sudo docker compose -f /opt/beone/verify/compose.yaml -f /opt/beone/codef/compose.codef.yaml -p beone-codef --env-file /opt/beone/codef/.env'
$C config --quiet && echo CONFIG_OK
$C up -d
$C ps --format '{{.Name}} {{.Status}}'
```

### 2-2. 키 입력 (K2, 승인 ⑨)

1. 운영 담당이 빈 파일을 만듭니다: `sudo install -d -m 750 /opt/beone/codef && sudo install -m 600 /dev/null /opt/beone/codef/.env`
2. **사용자가 직접** `sudoedit /opt/beone/codef/.env`로 검증 DB 값(`MYSQL_*`), 이미지 태그(`BEONE_VERIFY_*`), 1절의 키 5개를 입력합니다. 로컬 검증 때와 같은 키를 씁니다.
3. 에이전트와 운영 담당은 값을 보지 않습니다. 이름이 있는지만 확인합니다.

```bash
exec bash
sudo sh -c 'cut -d= -f1 /opt/beone/codef/.env | grep -v "^#" | sort'   # 이름만
sudo sh -c 'awk -F= "length(\$2)==0{print \$1\" 비어 있음\"}" /opt/beone/codef/.env'   # 빈 값만 이름으로
```

- 공개 `beone-app` 환경 파일에는 넣지 않습니다. 저장소·이슈·PR·로그에도 남기지 않습니다.

### 2-3. 호출 (K3, 승인 ⑩)

- 사용자 Mac에서 SSH 터널(검증 런북 8절)로 `http://localhost:18080`에 접속합니다. 합성 회원 1명을 T-a로 인증합니다(검증 런북 7절).
- `POST /api/v1/me/codef/connection`: 기관 `0301`(KB국민카드), 동의한 계정 1개. 비밀번호는 사용자가 화면이나 요청에 직접 입력하고 기록하지 않습니다. **1회.**
- `POST /api/v1/me/codef/syncs`: **1회.**
- 기록은 HTTP 상태와 `CF-` 결과 코드만 남깁니다. 응답 전문은 저장하지 않습니다.
- **상한(N14):** API별 최대 3회입니다. 실패해도 자동 재시도는 없습니다(게이트웨이는 재호출하지 않음). 다시 시도하면 횟수에 셉니다.
- **중단:** 계정 잠김 경고나 인증 실패 계열 `CF-`, 상한 도달, 로그·DB에서 금지 값 발견, 원장 변경, 운영 영향 중 하나라도 해당하면 즉시 멈추고 4절로 갑니다.

## 3. 확인 (K4~K6)

### 3-1. 정제 저장 (K4)

```bash
exec bash
Q() { sudo docker compose -p beone-codef exec -T verify-mysql sh -c "mysql -u\"\$MYSQL_USER\" -p\"\$MYSQL_PASSWORD\" -N -e \"$1\" \"\$MYSQL_DATABASE\"" 2>/dev/null; }
Q 'SELECT COUNT(*) FROM ledger_transactions'                          # 호출 전과 같아야 함(R8)
Q 'SELECT COUNT(*) FROM codef_approval_rows'
Q 'SELECT COUNT(*) FROM codef_held_cards'
Q 'SELECT COUNT(*) FROM codef_performance_references'
Q "SELECT SUM(connected_id_ciphertext NOT LIKE 'v1:%') FROM codef_connections"   # 0
```

| 확인 | 기대 |
|---|---|
| `ledger_transactions` 행 수 | 호출 전과 같음(CODEF는 원장에 쓰지 않음) |
| `codef_connections` | 암호문(`v1:`)만, 평문 0 |
| `codef_approval_rows` | 승인번호는 64자 소문자 16진 지문만(DB CHECK) |

### 3-2. API (K5)

- `GET /api/v1/me` → `codefConnected=true`
- `GET /api/v1/me/cards` → CODEF로 만든 카드(`holdingSource=CODEF`, `UNMAPPED`)

### 3-3. 로그 점검 (K6)

```bash
exec bash
L='sudo docker compose -p beone-codef logs verify-backend'
$L | grep -ciE 'password|passwd|connectedId=|cardNo|card_no|approvalNo'   # 0
$L | grep -oE 'CF-[0-9]{5}' | sort | uniq -c                               # 결과 코드만
$L | grep -cE 'CODEF_(CLIENT_ID|CLIENT_SECRET|PUBLIC_KEY)=|CODEF_[A-Z_]*KEY='                 # 0(키 이름=값 형태 없음)
```

- 첫 번째 결과가 0이 아니면 중단하고 로그를 공유하지 말고 4절로 갑니다.

## 4. 해제·폐기 (K7, 승인 ⑪)

### 4-1. 보관 중 (N15)

- MVP 시연이 끝날 때까지는 검증 DB 볼륨(`beone-codef_verify-mysql-data`)에 정제 데이터만 보관합니다. 컨테이너는 멈춰 둘 수 있습니다(`$C stop`, 볼륨 보존).
- 보관하는 동안 CODEF 키는 `.env`에서 지울 수 있습니다. 다시 띄울 때 사용자가 다시 입력합니다.

### 4-2. MVP 수락 뒤 폐기

기본 방법은 **검증 DB를 통째로 지우는 것**입니다.

```bash
exec bash
# 1) 연결 해제: CODEF 쪽 connectedId 정리를 요청한다(키가 있어야 정리됨)
#    사용자 Mac 터널에서: DELETE /api/v1/me/codef/connection (CSRF) → 204
# 2) 검증 환경과 DB 통째 삭제
$C down -v
# 3) 키 파일 삭제
sudo rm /opt/beone/codef/.env /opt/beone/codef/compose.codef.yaml && sudo rmdir /opt/beone/codef
# 4) 확인
sudo docker volume ls --format '{{.Name}}' | grep -c '^beone-codef_'   # 0
sudo ls /opt/beone/codef 2>&1 | head -1                                 # 없음
```

### 4-3. DB를 남기고 CODEF 데이터만 지울 때 (선택)

검증 DB를 남겨야 할 때만 씁니다. **대상은 `codef_*` 테이블뿐**이고, `beone-codef` 검증 DB에서만 실행합니다. **운영 DB(`beone-app`)와 원장(`ledger_transactions`)은 대상이 아닙니다.** 외래 키 순서는 자식 → 부모입니다.

```sql
-- beone-codef 검증 DB 전용. 운영 DB에서 실행하지 않는다
START TRANSACTION;
DELETE FROM codef_approval_rows;            -- codef_syncs 참조
DELETE FROM codef_performance_references;   -- codef_syncs 참조
DELETE FROM codef_held_cards;               -- codef_syncs·user_cards 참조
DELETE FROM codef_syncs;
DELETE FROM codef_connections;
COMMIT;
```

- 동기화로 만든 `user_cards` 행(`holding_source='CODEF'`)은 `codef_*`가 아니라 위 SQL 범위 밖입니다. 이 행까지 지울지는 **결정 필요**입니다. 4-2의 통째 삭제는 이 행도 함께 지웁니다.
- 실행 전후로 각 테이블의 `COUNT(*)`를 기록합니다. 원장 행 수는 바뀌지 않아야 합니다.

## 5. 남은 것

| 항목 | 상태 |
|---|---|
| LP-2 상한 설정 이름 | #205 병합 뒤 1절·2-1절에 채움 |
| 계정 API 실호출 전 확인 항목 | [codef 문서](../domains/codef.md#비공개-실연동-프로필-codef-live-lp-1-193)의 '실호출 전 확인 필요' 목록 |
| CODEF 카드 `user_cards` 행 폐기 범위 | 결정 필요(4-3) |
| 정식 과금 여부와 비용 상한 | N14의 비용 부분. 정식 과금이면 별도 승인 |
