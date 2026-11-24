# ledger 도메인 안내

## 목적과 범위

`ledger`는 카드 거래를 한 형식으로 모으는 **원장**(거래 기록 장부) 패키지입니다. 모의 승인·취소가 일어날 때 `payment`가 이 패키지로 **자체 모의 원장**(`source = SIMULATION`) 행을 남기고(#116, 쓰기), 사용자는 원장 조회 API로 그 행과 기간별 **완전성**(그 기간 거래를 빠짐없이 확보했는지)을 봅니다(#118, 조회). 계약은 [`docs/api/ledger.md`](../api/ledger.md)입니다.

구현된 범위:

- 쓰기(#116): 모의 승인 행(양수)과 모의 취소 행(음수, 원 승인 행 연결) 기록, 승인 행마다 엔진이 계산한 한도 버킷 차감액 저장(`ledger_usage_effects`, 모르는 차감액은 0이 아닌 `NULL`, D-18 K1).
- 조회(#118): `GET /api/v1/me/ledger-transactions?month=YYYY-MM[&userCardId][&source]`. `local`·`demo`와 로컬 회원 프로필 `member`(#165)에서 등록합니다. `member`에서는 회원 세션의 장부가 현재 사용자이고, 세션 401·`Cache-Control: no-store`는 `/api/v1/me/**` 공통 인터셉터·필터가 처리합니다(결정 37). `member`의 원장 행은 G3c(#161, 결정 38)에서 등록한 모의 결제 API가 만들고, CODEF 원장은 연결하지 않았습니다(R8). 페이지 나눔은 계약의 "명세 단계 미정"대로 없습니다.
- 스냅샷 뒤 활동 읽기(#120): `LedgerQueryService.activityAfter(user, userCardId, month, snapshotAt)`는 같은 카드에서 기록 시각(`recorded_at`)이 `snapshotAt`보다 뒤인 행 중 승인은 자기 거래일이, 취소는 원 승인 행의 거래일이 그 달인 행을 행 ID 오름차순으로 돌려줍니다. 취소는 원 승인 행의 거래일 월에 귀속합니다. 11월 승인을 12월에 취소하면 11월 활동이고(그 달 버킷은 확인 필요), 10월 승인을 11월에 취소한 행은 11월 활동이 아닙니다([monthly-states.md](../api/monthly-states.md) 218행, [D-17](../decisions.md#d-15d-16d-17-확정-정책), 관리자 승인 규칙). 조회 API(`GET /ledger-transactions`)의 달 조건은 그대로 거래일입니다. 승인 행에는 그 행의 버킷 차감액, 취소 행에는 원 승인 행의 버킷 차감액을 붙입니다(모르는 차감액은 `null` 그대로). monthlystate의 `afterSnapshot`과 recommendation의 사용액 반영이 씁니다. 카드 소유 확인은 호출자가 합니다.
- Flyway `V8__simulated_payments_ledger.sql`의 `ledger_transactions`·`ledger_usage_effects` 테이블. 조회는 새 마이그레이션이 없습니다.

구현하지 않은 범위(후속):

- CODEF 실제 조회 거래 행과 그 불완전 사유(`PARTIAL_CANCELLATION_SUSPECTED` 등 5종). 도메인 원장 연결은 하지 않기로 했고(2026-11-16 사용자 결정 R8), 행을 넣고 사유를 채울지는 원장 연결을 다시 정할 때 함께 정합니다(아래 「완전성 값」). 실제 조회 거래와 자체 모의 원장은 `source`로 구분합니다(CLAUDE.md 보안).
- 원장에서 전월 실적·버킷 사용액을 자동으로 산출하는 기능(D-16에 따라 하지 않음).

실패 응답:

| 상황 | 상태 | 코드 |
|---|---|---|
| `month` 누락 | 400 | `INVALID_REQUEST`(`month` 필수 값, 공통 처리기) |
| `month` 형식 오류(`2026-1`, `2026-13` 등) | 400 | `INVALID_REQUEST`(`month` 형식 오류) |
| `source`가 `SIMULATION`·`CODEF`가 아님(빈 값·소문자 포함) | 400 | `INVALID_REQUEST`(`source` 형식 오류) |
| 지정한 `userCardId`의 카드가 없거나, 형식이 틀렸거나, 해제했거나, 다른 사용자의 카드(생략하면 전체 카드를 조회하며 404가 아님) | 404 | `USER_CARD_NOT_FOUND` |
| `local`·`demo`: 시연 사용자 헤더 없음·틀림 | 400 | `INVALID_REQUEST`(공통) |
| `member`: 회원 세션 없음·만료(헤더는 보지 않고 세션으로 식별, `month` 검사보다 먼저) | 401 | `UNAUTHENTICATED`(공통) |

## 핵심 용어

- **원장 행(`LedgerTransaction`)**: 거래 한 건. 승인(`APPROVAL`)은 양수, 취소(`CANCELLATION`)는 음수이고 행 종류는 `entryType`으로 판단합니다. ID는 `lt-`+번호입니다.
- **귀속월(`attributionMonth`)**: 이 행이 실적에 잡히는 달과 확정 여부. 모의 행은 승인 행이 주문 월, 취소 행이 취소한 달이며 상태는 `CONFIRMED`입니다. 실적 영향의 확정 여부는 카드 규칙에 따라 `payment`의 취소 영향이 따로 판단합니다.
- **완전성(`coverage`)**: 카드·출처별로 그 달 원장을 빠짐없이 확보했는지. 출처를 합쳐 계산하지 않습니다.
- **버킷 차감액(`LedgerUsageEffect`)**: 승인 한 건이 한도 버킷에 더한 사용액. 엔진 `BucketUsageEffect`를 그대로 옮깁니다.

## 패키지 구조

```text
ledger
├── web
│   ├── LedgerTransactionController   # 조회 API 1개, CurrentUser 인자만 받음
│   ├── LedgerQueryParameters         # month·source 형식 검사
│   ├── LedgerErrorCode               # USER_CARD_NOT_FOUND
│   └── LedgerExceptionHandler        # 기능별 처리기(@Order 최우선)
├── application
│   ├── LedgerWriter                  # 쓰기: recordApproval·recordCancellation(호출자 트랜잭션 필수), usageEffectsOf
│   ├── NewLedgerEntry                # 기록할 행. 부호·원 승인 행 연결을 생성 때 검사
│   ├── LedgerUsageEffect             # 버킷 ID + 차감액(모르면 null)
│   ├── LedgerQueryService            # 조회: 한 달의 행과 카드·출처별 완전성(공개 부품)
│   ├── LedgerActivity                # 스냅샷 뒤 행 한 건(종류·금액·버킷 차감액, #120)
│   ├── LedgerMonthView               # 조회 결과(계약 응답 data 모양)
│   ├── LedgerCardNotFoundException
│   ├── LedgerTransactionId           # lt- + 번호
│   └── LedgerSource(SIMULATION·CODEF), LedgerEntryType, AttributionStatus
└── persistence
    ├── LedgerTransactionEntity / LedgerTransactionRepository
    └── LedgerUsageEffectEntity / LedgerUsageEffectRepository
```

주요 파일: [LedgerWriter](../../backend/src/main/java/com/beone/api/ledger/application/LedgerWriter.java), [LedgerQueryService](../../backend/src/main/java/com/beone/api/ledger/application/LedgerQueryService.java), [LedgerTransactionController](../../backend/src/main/java/com/beone/api/ledger/web/LedgerTransactionController.java).

## 다른 패키지와의 관계

- `payment` → `ledger`: 모의 승인·취소 때 `LedgerWriter`로 행을 남깁니다.
- `ledger` → `card`: 조회 대상 카드와 `userCardId` 404 판단에 `UserCardService.list`(해제 카드 제외)와 `UserCardId`를 읽기만 합니다. card 코드는 바꾸지 않았습니다.
- `ledger`는 `payment`를 참조하지 않습니다. 그래서 응답의 `simulatedPaymentId`는 `pay-` 접두사를 `LedgerQueryService` 안에 따로 두고 붙입니다(payment `PaymentIds`와 같은 값).

## 조회 규칙

관리자 승인(결정 28 보충, #118)으로 정했습니다. 계약 문서는 바꾸지 않았습니다.

- **달 조건은 거래일 기준**: `transactionDate`가 그 달인 행입니다. 2차 CODEF 취소 행은 귀속월이 `NEEDS_CONFIRMATION`이라 귀속월 기준으로는 행을 둘 곳이 없고, 완전성 기간도 같은 달력 월이기 때문입니다.
- **정렬**: 거래일 → 행 ID 오름차순. 완전성은 카드 ID 오름차순, 같은 카드 안에서는 `SIMULATION` → `CODEF`.
- **대상 카드**: 해제하지 않은 이 사용자의 보유 카드 전부, `userCardId`를 주면 그 카드만. 해제한 카드의 행은 보이지 않습니다.
- **완전성 값**: `SIMULATION`은 모의 승인·취소를 모두 기록하므로 `complete=true`입니다. CODEF 조회·동기화(#131)는 승인 행과 실적 참고값을 codef 전용 테이블에만 저장하고 이 원장에 행을 넣지 않습니다(도메인 원장 미연결 유지, 2026-11-16 사용자 결정 R8). 새로 조회된 보유 카드만 card 도메인에 `UNMAPPED` 사용자 카드로 등록하고, 대조 정보는 codef 전용 테이블에 둡니다. 그래서 `CODEF`는 행이 없고 완전하다고 선언하지 않으며 `complete=false`, `incompleteReasons=[]`입니다. 사유 코드는 새로 만들지 않았습니다. 완전 선언과 사유 채우기는 원장 연결을 다시 정할 때 함께 정합니다. `source`를 생략하면 출처마다 한 항목씩, 지정하면 그 출처만 담습니다.
- `transactionTime`은 `HH:mm:ss` 또는 `null`(00:00을 만들지 않음), `transactionNature`는 1차 모의 행에서 `null`입니다.

## 사용 예

```java
// payment의 승인 흐름 안(같은 트랜잭션)
long ledgerId = ledgerWriter.recordApproval(entry, List.of(new LedgerUsageEffect("GREAT", 2100L)));

// 다른 패키지에서 한 달 원장 읽기(읽기 전용 트랜잭션)
LedgerMonthView view = ledgerQueryService.find(user, YearMonth.of(2026, 11), "uc-1001", LedgerSource.SIMULATION);
```

```bash
# local·demo 예시. member에서는 시연 헤더 대신 회원 세션 쿠키(__Host-BEONE_SESSION)로 현재 사용자를 정합니다.
curl "http://localhost:8080/api/v1/me/ledger-transactions?month=2026-11&source=SIMULATION" \
  -H "X-BeONE-Demo-User: MY_CARDS"
```

## 제약과 테스트

- 원장 행은 보유 카드·모의 결제를 참조하며, 그 행을 지우면(테스트 정리) 함께 지워집니다.
- 조회는 한 달 단위로 사용자 행을 모두 읽은 뒤 카드·출처를 거릅니다. 1차는 건수가 작아 페이지 나눔이 없습니다.
- 단위: `LedgerWriterTest`(출처·부호·차감액 `null` 보존·ID 형식), `LedgerQueryServiceTest`(계약 예시 변환, 출처별 완전성, CODEF 빈 목록, 해제 카드 숨김, 시각 형식, 404), `LedgerQueryParametersTest`.
- 웹(`controllers=` 한정): `LedgerTransactionApiWebTest`(계약 예시 JSON, 400·404, 헤더 400), `LedgerTransactionProfileOffWebTest`(`local`·`demo`·`member` 밖 404).
- 통합: `LedgerTransactionIntegrationTest`(모의 승인·부분취소가 남긴 행 조회, 출처별 완전성, 다른 달, 사용자별 분리, 해제 카드 404). `payment`의 `SimulatedPaymentIntegrationTest`도 쓰기 행을 확인합니다. `member` 프로필은 `MemberLedgerIntegrationTest`(#165)가 확인합니다. 결제 API가 없으므로 원장 저장소에 행을 직접 넣는 테스트 픽스처를 쓰고, 회원 A·B 분리, 시연 원장 미노출·시연 헤더 무시, 401·no-store, 남의·없는·형식 오류 카드 404 본문 동일, `CODEF` `complete=false`(R8)를 봅니다.
