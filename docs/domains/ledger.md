# ledger 도메인 안내

## 목적과 범위

`ledger`는 카드 거래를 한 형식으로 모으는 **원장**(거래 기록 장부) 패키지입니다. 지금은 **쓰기 쪽만** 구현했습니다(#116). 모의 승인·취소가 일어날 때 `payment`가 이 패키지로 **자체 모의 원장**(`source = SIMULATION`) 행을 남깁니다. 계약은 [`docs/api/ledger.md`](../api/ledger.md)입니다.

구현된 범위:

- 모의 승인 행(양수)과 모의 취소 행(음수, 원 승인 행 연결) 기록.
- 승인 행마다 엔진이 계산한 한도 버킷 차감액 저장(`ledger_usage_effects`). 모르는 차감액은 0이 아닌 `NULL`입니다(D-18 K1).
- Flyway `V8__simulated_payments_ledger.sql`의 `ledger_transactions`·`ledger_usage_effects` 테이블.

구현하지 않은 범위(후속):

- 원장 조회 API(`docs/api/ledger.md`의 목록 조회). HTTP 경로는 없습니다.
- 스냅샷 뒤 승인·취소의 월간 합산(`afterSnapshot`, B2f).
- CODEF 실제 조회 거래 행. 실제 조회 거래와 자체 모의 원장은 `source`로 구분하며, 지금은 `SIMULATION`만 씁니다.
- 원장에서 월간 상태를 자동으로 산출하는 기능(D-16에 따라 하지 않음).

## 핵심 용어

- **원장 행(`LedgerTransaction`)**: 거래 한 건. 승인(`APPROVAL`)은 양수, 취소(`CANCELLATION`)는 음수이고 행 종류는 `entryType`으로 판단합니다. ID는 `lt-`+번호입니다.
- **귀속월(`attributionMonth`)**: 이 행이 실적에 잡히는 달. 모의 행은 승인 행이 주문 월, 취소 행이 취소한 달이며 상태는 `CONFIRMED`입니다. 실적 영향의 확정 여부는 카드 규칙에 따라 `payment`의 취소 영향이 따로 판단합니다.
- **버킷 차감액(`LedgerUsageEffect`)**: 승인 한 건이 한도 버킷에 더한 사용액. 엔진 `BucketUsageEffect`를 그대로 옮깁니다.

## 패키지 구조

```text
ledger
├── application
│   ├── LedgerWriter            # recordApproval·recordCancellation(호출자 트랜잭션 필수), usageEffectsOf
│   ├── NewLedgerEntry          # 기록할 행. 부호·원 승인 행 연결을 생성 때 검사
│   ├── LedgerUsageEffect       # 버킷 ID + 차감액(모르면 null)
│   ├── LedgerTransactionId     # lt- + 번호
│   └── LedgerSource, LedgerEntryType, AttributionStatus
└── persistence
    ├── LedgerTransactionEntity / LedgerTransactionRepository
    └── LedgerUsageEffectEntity / LedgerUsageEffectRepository
```

주요 파일: [LedgerWriter](../../backend/src/main/java/com/beone/api/ledger/application/LedgerWriter.java), [NewLedgerEntry](../../backend/src/main/java/com/beone/api/ledger/application/NewLedgerEntry.java).

## 사용 예

```java
// payment의 승인 흐름 안(같은 트랜잭션)
long ledgerId = ledgerWriter.recordApproval(entry, List.of(new LedgerUsageEffect("GREAT", 2100L)));
// 취소는 음수 금액과 원 승인 행 ID를 담은 entry로
long cancelId = ledgerWriter.recordCancellation(cancellationEntry);
```

## 제약과 한계

- 다른 패키지는 `payment`만 씁니다. 조회 메서드는 취소 영향에 쓰는 `usageEffectsOf` 하나입니다.
- 원장 행은 보유 카드·모의 결제를 참조하며, 그 행을 지우면(테스트 정리) 함께 지워집니다.
- 테스트: `LedgerWriterTest`(출처·부호·차감액 `null` 보존·ID 형식), 통합은 `payment`의 `SimulatedPaymentIntegrationTest`가 실제 행을 확인합니다.
