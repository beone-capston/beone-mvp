# 규칙 데이터 안내

사용자가 공식 문서와 대조해 승인한 카드 규칙 4종의 데이터와 근거입니다. **규칙 로더는 아직 없어, 이 파일을 읽는 코드는 없습니다.**

| 카드 | 근거 표 | 로더 JSON |
| --- | --- | --- |
| `09174` 청춘대로 톡톡 | [09174.md](09174.md) | [JSON](../../backend/src/main/resources/rules/09174/09174-A-20170126-9120-00109-07.json) |
| `09271` 올라운드 티타늄 | [09271.md](09271.md) | [JSON](../../backend/src/main/resources/rules/09271/09271-A-20181030-9120-00489-02.json) |
| `01664` 노리 체크 | [01664.md](01664.md) | [JSON](../../backend/src/main/resources/rules/01664/01664-B-20101223-9162-00045-12.json) |
| `01914` 첵첵 체크 | [01914.md](01914.md) | [JSON](../../backend/src/main/resources/rules/01914/01914-B-20200608-9162-00529-07.json) |

공식 PDF는 저장소에 넣지 않습니다. 근거 표에 URL과 SHA-256만 적습니다.

## `VERIFIED`의 뜻

공식 문서와 대조해 사용자가 승인했다는 뜻까지만 가집니다. [D-01](../decisions.md)(실제 카드의 공식 SKU·약관 버전 일치)과 평가 사례의 조건부 상태는 미결입니다. `validity.from`은 사용자 지정 적용 시작일이며 약관 효력일이 아닙니다.

## JSON 형식

필드 이름은 `com.beone.api.benefit.rule`의 모델을 따릅니다. 메모는 넣지 않고 근거 ID(`evidence`)로 근거 표와 연결합니다.

- **확정 값:** `{"value": ..., "evidence": "E1"}`
- **미확정 값:** `{"unresolved": "이유"}`. 코드의 `RuleTerm.Unresolved`이고 `확인 필요`를 뜻합니다. 0원이나 기본값으로 바꾸지 않습니다.
- **대상 가맹점:** `{"type": "ALL_MERCHANTS"}` 또는 `{"type": "SPECIFIC_CLASSES", "classes": [...]}`
- **계산 방식:** `{"type": "RATE", "basisPoints": 100}` 또는 `{"type": "FIXED_AMOUNT", "won": 2500}`

## 모델에 아직 없는 값

아래는 로더를 만들 때 모델에 더하거나 옮기는 방식을 정합니다.

| 필드 | 뜻 | 근거 |
| --- | --- | --- |
| `fractionalWon.basis`, `fractionalWon.decision` | 절사가 문서 대조가 아닌 사용자 결정임을 표시 | [D-03](../decisions.md#d-03d-04-확정-정책) |
| `stackingBasis` | 중복 합산의 계산 기준. 없으면 합산하지 않음 | [D-13 #35 8번](../decisions.md#d-13-확정-정책) |
| `performanceExcludedClassesEvidence` | 실적 제외 업종의 근거 ID | 근거 표 |
| `inclusionUnconfirmed` | 포함 관계가 확인되지 않은 코드 쌍 | 아래 "포함 관계 확인 필요" |
| `newCardGrace.value.deemedPerformance.unresolved` | 간주 실적 금액 미확정(09174). 모델은 양수 금액만 받음 | 09174 근거 표 |

## 포함 관계 확인 필요

서비스와 `policy`의 `inclusionUnconfirmed`는 포함 관계가 확인되지 않은 코드 쌍입니다. 코드가 서로 달라도 불일치로 단정하지 않습니다.

```json
{"merchantClass": "STARBUCKS_STORE", "relatedClass": "COFFEE_BEVERAGE"}
```

- **뜻:** 주문이나 거래의 코드가 `merchantClass`이면, `relatedClass`에 드는지 모릅니다.
- **서비스에 있을 때:** 그 서비스는 그 주문에서 `확인 필요`입니다. [D-18 U2 ① 예외](../decisions.md#d-18-확정-정책)의 대상 업종 불일치로 보지 않습니다.
- **`policy`에 있을 때:** 그 거래가 전월 실적에서 제외되는지 `확인 필요`입니다. 제외나 포함으로 단정하지 않습니다.
- 값은 2026-11-03 사용자 결정으로 표시했습니다. 코드에는 아직 없고, 쌍별 근거는 각 근거 표에 있습니다.

## 키가 없을 때

아래 두 키는 일부 또는 전체 서비스에 없습니다. **빈 값으로 읽지 않습니다.** 빈 값은 "제외 없음", "결제 방식 없음"이라는 다른 뜻이 됩니다.

- `paymentMethods`: 문서에 결제 방식 표기가 없거나 일부만 있습니다.
- `excludedClasses`: 서비스별 제외 조건은 근거 표에 원문으로 있고 코드를 정하지 않았습니다.

## 코드 값

`merchantClass` 코드 49개는 2026-11-03 사용자가 승인했습니다. 약관에 코드가 없고, KB 업종 분류와의 일치는 확인하지 않았습니다. 코드와 원문의 대응은 각 근거 표의 "코드 표"를 봅니다.
