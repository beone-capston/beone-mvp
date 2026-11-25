# 약관 초안 오프라인 도구 (`tools/rule-draft`)

PRD FR-08(AI 약관 초안 추출·사람 검증·수동 작성 비교)을 위한 **오프라인 명령줄 도구**입니다(결정 41 후보). 공식 상품설명서 PDF나 그 추출 텍스트를 받아 두 가지를 만듭니다.

- 기존 규칙 JSON(`backend/src/main/resources/rules/`)과 같은 키 구조의 **규칙 초안**
- 초안 필드마다 원문의 쪽 번호와 인용문을 적은 **근거 파일**

서비스 업무 API에는 넣지 않았고(D-14) 배포 대상도 아닙니다.

- **현재 범위:**
  - D41-1: 텍스트 추출, 초안 생성 흐름, 스키마 검사, 근거 검사, 녹화 응답 재생, 실제 제공자 어댑터(컴파일만)
  - D41-2: 초안·수동 규칙 비교(`compare`), 사람 승인 기록(`approve`)
- **아직 없는 것:**
  - 실제 카드의 비교·승인 기록. 도구만 있고 기록은 아직 하나도 없습니다.
  - 실제 AI 호출. 제공자·모델·비용은 사용자 승인 전이라 한 번도 호출하지 않았습니다.

## 구조적 분리: AI 출력이 계산에 들어가지 않는 이유

| 장치 | 내용 |
|---|---|
| 별도 Gradle 프로젝트 | backend `settings.gradle`에 포함하지 않습니다. 이 도구의 클래스와 의존성(PDFBox, Jackson)은 backend 클래스패스에 없습니다. |
| 규칙 경로 쓰기 금지 | [`OutputGuard`](src/main/java/com/beone/ruledraft/check/OutputGuard.java)가 다음 경로에 쓰려고 하면 실패합니다(종료 1). 경로의 존재하는 앞부분은 운영체제 방식으로 풀어 링크와 그 뒤의 `..`까지 반영하고, 아직 없는 부분의 `.`·`..`는 거부합니다. 도구는 검사가 돌려준 경로에만 씁니다. 규칙 JSON은 읽기만 합니다.<br>- 기준 표본 디렉터리 아래<br>- 이름이 `backend/src/main/resources/rules`인 경로 아래 |
| 상태 고정 | 초안의 `status`는 모델이 무엇을 내든 `AI_DRAFT`로 바꿉니다. backend [`VerificationStatus`](../../backend/src/main/java/com/beone/api/benefit/rule/VerificationStatus.java)의 값이며, 계산에는 `VERIFIED`만 씁니다. 모델이 다른 값을 냈으면 근거 파일의 `modelStatus`에 남깁니다. |
| 사람 반영 | `VERIFIED` 반영은 사람이 원문과 대조해 rules JSON을 고친 뒤 별도 커밋으로만 합니다. `compare`·`approve`도 규칙 파일을 읽기만 하고, 출력 경로는 같은 `OutputGuard` 검사를 거칩니다. 승인 기록은 "이렇게 고치기로 했다"는 기록일 뿐 규칙을 바꾸지 않습니다. 테스트([`ReviewCliTest`](src/test/java/com/beone/ruledraft/ReviewCliTest.java))가 모든 명령 전후로 기준 규칙 디렉터리 내용이 같은지와, 규칙 경로 출력이 종료 1인지 확인합니다. |

## 실행

Java 21이 필요합니다. 이 디렉터리에서 실행합니다.

```bash
cd tools/rule-draft
./gradlew test        # 단위·CLI 테스트(네트워크 호출 없음)
./gradlew build
```

### 1. PDF → 쪽 표시 텍스트 (`text`)

```bash
./gradlew run --args="text --source /경로/상품설명서.pdf --out build/rule-draft/01664.txt"
```

PDF를 쪽마다 따로 추출해 각 쪽 앞에 `[[page N]]` 줄을 붙입니다. 본문에 `[[page N]]`처럼 보이는 줄이 있으면 앞에 역슬래시를 붙여(`\[[page N]]`) 쪽 표시와 구분하고, 읽을 때 하나를 뗍니다. 손으로 쓰는 텍스트도 같은 규칙을 따릅니다. 사람이 추출 결과를 확인하거나, 스캔본처럼 추출이 안 되는 PDF를 손으로 옮겨 적을 때 이 형식을 씁니다.

### 2. 초안 생성 (`draft`)

```bash
# 녹화 응답 재생(네트워크 없음)
./gradlew run --args="draft --source src/test/resources/fixtures/synthetic-99999.txt --card 99999 \
  --out build/rule-draft --recorded src/test/resources/fixtures/recorded-99999.json"
```

| 옵션 | 뜻 |
|---|---|
| `--source` | PDF(`.pdf`) 또는 쪽 표시 텍스트 |
| `--card` | 카드 ID 5자리. 초안의 `cardId`와 같아야 합니다 |
| `--out` | 출력 디렉터리. `<카드ID>.draft.json`, `<카드ID>.evidence.json`을 씁니다 |
| `--recorded` | 녹화된 모델 응답 파일을 재생합니다 |
| `--live` | 실제 제공자를 호출합니다(아래 조건) |
| `--rules-dir` | 기준 표본 디렉터리. 기본값 `../../backend/src/main/resources/rules` |

처리 순서는 다음과 같습니다([`DraftCommand`](src/main/java/com/beone/ruledraft/DraftCommand.java)).

1. 모델 선택 조건을 먼저 확인합니다. 맞지 않으면 아무 파일도 읽거나 쓰지 않습니다.
2. 기준 표본 규칙 JSON 4개에서 키 구조를 뽑습니다.
3. 지시문을 모델에 넘기고, 받은 응답에서 `{"draft": …, "evidence": …}`를 읽습니다. 지시문은 [고정 안내](src/main/resources/draft-instructions.txt), 허용 구조 경로, 형식 예시 1개로 이루어집니다.
4. 스키마를 검사합니다. 맞지 않으면 아무것도 쓰지 않고 종료 1로 끝납니다.
5. 근거를 검사한 뒤 두 파일을 짝으로 씁니다. 같은 폴더의 임시 파일에 먼저 다 쓰고 원자적 이름 변경으로 옮기며, 두 번째 이동이 실패하면 새 초안을 지우고 종료 1입니다. 근거 파일의 `draftSha256`은 같은 실행의 초안 내용 sha256입니다. 근거가 빠지거나 확인되지 않은 값이 있으면 종료 3입니다.

형식 예시는 **대상이 아닌 다른 카드**의 규칙을 씁니다. 대상 카드의 수동 규칙을 모델에 보여 주면 수동 작성과 AI 초안의 비교가 무의미해지기 때문입니다.

종료 코드는 다음과 같습니다.

| 코드 | 뜻 |
|---|---|
| 0 | 성공 |
| 1 | 실패. 스키마 불일치, `cardId` 불일치, 규칙 경로 출력, 입출력·모델 오류 |
| 2 | 인자·실행 조건 오류. `--live`·키 조건도 여기에 해당합니다 |
| 3 | 초안·근거 파일은 썼지만 근거가 빠졌거나 확인되지 않은 값이 있음 |

### 3. 초안·수동 규칙 비교 (`compare`)

```bash
./gradlew run --args="compare --draft build/rule-draft/99999.draft.json --evidence build/rule-draft/99999.evidence.json \
  --manual <수동 규칙 JSON> --out build/rule-review"
```

[`RuleComparator`](src/main/java/com/beone/ruledraft/review/RuleComparator.java)가 자식이 없는 값마다 JSON Pointer 경로로 비교하고, `<카드ID>.compare.json`과 `<카드ID>.compare.md`(Markdown 표)를 씁니다.

| 분류 | 뜻 |
|---|---|
| 일치 | 두 값이 같습니다. 숫자는 표기와 상관없이 값으로 비교합니다(`10000`과 `10000.0`은 같음) |
| 불일치 | 값이나 값 종류가 다릅니다(`"1"`과 `1`, `null`과 `0`은 다름) |
| 누락(초안에 없음) | 수동 규칙에만 있습니다 |
| 누락(수동에 없음) | 초안에만 있습니다 |
| 근거 없음 | 초안 값에 확인된 근거가 자기·조상 경로 어디에도 없습니다. 값 비교 결과는 JSON의 `comparison`과 표의 괄호에 남깁니다 |

- **배열:** 번호끼리 비교합니다. 순서가 다르면 불일치·누락으로 나옵니다.
- **`/status`:** 비교하지 않습니다. 초안은 항상 `AI_DRAFT`이기 때문입니다.
- **근거:** 근거 파일의 `fields`(draft가 원문과 대조해 확인한 근거)만 씁니다. 근거 파일의 `draftSha256`이 초안 파일 내용과 다르면 다른 실행의 짝이므로 비교하지 않습니다(종료 1). compare는 원문을 다시 읽지 않습니다.
- **근거를 요구하지 않는 필드:** 근거 검사와 같습니다(`/evaluationOnly`, `/sources` 아래, `evidence` 키).
- **종료 코드:** 근거 없음 항목이 있으면 결과 파일은 쓰고 종료 3, 없으면 0입니다.

### 4. 사람 승인 기록 (`approve`)

```bash
./gradlew run --args="approve --compare build/rule-review/99999.compare.json --edits <수정 항목 JSON> --reviewer <승인자>"
```

승인자·시각·수정 항목을 `docs/rules/reviews/<카드ID>-<날짜>.json`(기본값, `--out`으로 바꿈)에 남깁니다. 날짜는 서울 시간 기준이고 시각은 UTC ISO-8601입니다. 수정 항목 파일은 다음 모양입니다. 고친 것이 없으면 `"edits": []`입니다.

```json
{
  "edits": [
    { "path": "/services/0/maximumTargetAmount", "before": 20000, "after": 30000, "reason": "원문 2쪽 한도 표 재확인" }
  ]
}
```

- `before`·`after` 키를 **생략**하면 "그 경로가 없음"(추가·삭제)이고, JSON `null`은 null 값입니다.
- 다음 경우는 거부하고 아무것도 쓰지 않습니다(종료 1). 문제를 모두 모아 알립니다.
  - 비교 결과에 근거 없음 항목이 남음
  - 승인자·수정 사유가 빔
  - 비교 결과에 없는 경로, `/`로 시작하지 않는 경로, 같은 경로 중복
  - `before`가 비교 결과의 초안 값과 다름, `before`와 `after`가 같음
- 기록에는 비교 결과 파일의 sha256, 초안 sha256, 수동 규칙 파일 이름·sha256, 분류별 건수를 함께 남깁니다.
- 기존 기록은 덮어쓰지 않습니다. 같은 날 기록이 있으면 `-2`, `-3` … 을 붙입니다.
- **근거 없음 해소:** 지금은 원문 근거를 채워 `draft`부터 다시 만드는 방법뿐입니다. 사람이 찾은 근거를 따로 넣는 기능은 없습니다.

## 실제 호출 조건 (`--live`)

[`LiveGate`](src/main/java/com/beone/ruledraft/LiveGate.java)가 정합니다.

| 상황 | 결과 |
|---|---|
| `--recorded`만 | 녹화 응답 재생, 네트워크 없음 |
| `--live` + 환경 변수 `BEONE_RULE_DRAFT_API_KEY` | 실제 호출(사용자 승인 전 금지) |
| `--live`인데 키가 없거나 빈 값 | 종료 2 |
| 키는 있는데 `--live`가 없음 | 종료 2. 키가 설정된 셸이어도 호출하지 않습니다 |
| `--recorded`와 `--live`를 함께 | 종료 2 |

- **키 취급:** 키는 환경 변수로만 받습니다. 요청 헤더 밖으로 내보내지 않고, 로그·출력·예외 메시지에는 변수 이름만 씁니다. Gradle `test` 작업은 이 변수를 테스트 프로세스에서 지웁니다.
- **제공자 어댑터:** 현재 후보 구현은 [`AnthropicMessagesModel`](src/main/java/com/beone/ruledraft/model/AnthropicMessagesModel.java) 하나입니다.
  - Messages API를 JDK HTTP 클라이언트로 호출합니다. 모델 기본값은 `claude-opus-5-5`이고 `BEONE_RULE_DRAFT_MODEL`로 바꿀 수 있습니다.
  - 컴파일과 요청 본문 조립·응답 해석만 테스트하며, 전송은 호출하지 않습니다.
  - 제공자·모델·예상 비용·호출 수는 승인 요청 항목입니다.
- **다른 제공자:** [`RuleDraftModel`](src/main/java/com/beone/ruledraft/model/RuleDraftModel.java)을 구현하면 추가할 수 있습니다.

## 스키마 검사

[`RuleKeySchema`](src/main/java/com/beone/ruledraft/check/RuleKeySchema.java)가 기준 표본으로 `rules/<카드ID 5자리>/<카드ID>-*.json`만 읽습니다. 같은 디렉터리의 다른 JSON(로컬 도구 상태 등)은 표본에서 뺍니다. 경로는 배열 번호를 `[]`로 바꿔 비교합니다(예: `/services/[]/tiers/[]/minimumPerformance`).

- **모르는 키:** 표본에 없는 경로가 있으면 실패합니다.
- **값 종류:** 경로마다 표본에서 본 값 종류(object·array·string·number·boolean·null) 중 하나여야 합니다.
- **필수 키:** 객체마다 표본의 같은 경로 객체들에 공통으로 있던 키는 초안에도 있어야 합니다. 예를 들어 `/validity`에는 `from`·`to`가 필요합니다.
- **값:** 금액·비율 같은 값 자체는 검사하지 않습니다. 값 비교는 `compare` 몫입니다.

## 근거 검사

[`EvidenceChecker`](src/main/java/com/beone/ruledraft/check/EvidenceChecker.java)가 맡습니다. 근거 파일의 키는 JSON Pointer(RFC 6901)입니다. JSON 안의 위치를 `/limitBuckets/0/tiers/1/monthlyCap`처럼 `/`로 이어 적는 표준 표기입니다.

- **덮는 범위:** 근거는 값 경로 자신이나 조상 경로에 답니다. 예를 들어 `/limitBuckets`의 근거는 그 아래 값 전체를 덮습니다.
- **확인 방식:** 인용문이 지정한 쪽 텍스트에 공백을 무시하고 그대로 있어야 확인된 근거로 칩니다. 공백은 `Character.isWhitespace`·`isSpaceChar`(NBSP·U+2003 등)와 서식 문자(U+200B 등)를 모두 뜻하며, 인용문과 원문에 같은 정규화를 씁니다. 쪽 번호는 정수 JSON 숫자만 받고, 소수·문자열은 잘라 쓰지 않고 없는 쪽(0)으로 처리합니다. 다음 경우는 `unverified`에 사유와 함께 남기고, 그 근거는 아무 값도 덮지 않습니다.
  - 초안에 없는 경로
  - 원문에 없는 쪽
  - 빈 인용문. 공백을 지운 결과가 비는 인용도 여기에 듭니다
  - 인용문을 원문에서 찾지 못함
- **누락:** 확인된 근거가 자기·조상 경로 어디에도 없는 값은 `missing`에 남습니다.
- **근거를 요구하지 않는 필드:** 원문이 아닌 프로젝트가 정하는 값들입니다.
  - `/status`
  - `/evaluationOnly`
  - `/sources` 아래
  - 이름이 `evidence`이거나 `Evidence`로 끝나는 키(docs/rules 근거 표 번호)

근거 파일(`<카드ID>.evidence.json`)의 모양은 다음과 같습니다.

```json
{
  "cardId": "99999",
  "source": { "fileName": "synthetic-99999.txt", "sha256": "…", "pageCount": 2 },
  "draftSha256": "…",
  "model": "recorded:recorded-99999.json",
  "status": "AI_DRAFT",
  "modelStatus": "VERIFIED",
  "fields": { "/limitBuckets": { "page": 2, "quote": "전월 이용실적 30만원 이상 시 통합 월 할인한도 1만원" } },
  "missing": [],
  "unverified": []
}
```

## 테스트

| 테스트 | 확인 내용 |
|---|---|
| [`MainTest`](src/test/java/com/beone/ruledraft/MainTest.java) | 녹화 응답 → 초안·근거 생성, 근거 누락·인용 불일치(종료 3), 스키마 불일치(종료 1, 파일 없음), `--live` 없음·키 없음·빈 키·동시 지정(종료 2), 규칙 경로 출력 거부 |
| [`RuleKeySchemaTest`](src/test/java/com/beone/ruledraft/check/RuleKeySchemaTest.java) | 표본 4개 로드, 표본 자신 통과, 모르는 키·종류 불일치·필수 키 누락, 표본 파일 선택 규칙 |
| [`EvidenceCheckerTest`](src/test/java/com/beone/ruledraft/check/EvidenceCheckerTest.java) | 조상 경로 덮기, 누락, 확인 실패 사유, NBSP·공백만 있는 인용 거부 |
| [`RuleComparatorTest`](src/test/java/com/beone/ruledraft/review/RuleComparatorTest.java) | 다섯 분류, 숫자 값 비교, 근거 없음과 원래 분류, 관리 필드, `/status` 제외, 표 칸 처리 |
| [`ApprovalRecorderTest`](src/test/java/com/beone/ruledraft/review/ApprovalRecorderTest.java) | 승인 기록 내용, 근거 없음 거부, 수정 항목 형식 오류 |
| [`ReviewCliTest`](src/test/java/com/beone/ruledraft/ReviewCliTest.java) | draft → compare → approve 흐름, 짝이 아닌 초안 거부, 근거 없음(compare 종료 3, approve 거부), 같은 날 재승인 번호, 규칙 경로 출력 거부, 규칙 디렉터리 불변 |
| [`OutputGuardTest`](src/test/java/com/beone/ruledraft/check/OutputGuardTest.java) | 규칙 경로·하위·링크 거부 |
| [`SourceLoaderTest`](src/test/java/com/beone/ruledraft/source/SourceLoaderTest.java) | 쪽 표시 텍스트 해석, PDF 쪽별 추출(테스트 안에서 만든 영문 PDF) |
| [`AnthropicMessagesModelTest`](src/test/java/com/beone/ruledraft/model/AnthropicMessagesModelTest.java) | 요청 본문에 키 없음, 응답 텍스트 해석, `end_turn` 외 종료 실패 |

- **실호출 0회:** 모든 CLI 테스트는 [`FailingTransport`](src/test/java/com/beone/ruledraft/FailingTransport.java)를 넣습니다. 이 전송은 만들어지거나 호출되면 테스트가 실패합니다. 출력에 가짜 키 값이 없는지도 확인합니다.
- **합성 fixture:** [`synthetic-99999.txt`](src/test/resources/fixtures/synthetic-99999.txt)와 녹화 응답은 실제 카드가 아닌 합성 자료입니다. 실제 상품설명서 원문이나 개인 정보는 들어 있지 않습니다.
