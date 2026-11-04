# 규칙 데이터 안내

사용자가 공식 문서와 대조해 승인한 카드 규칙 4종의 데이터와 근거입니다. [규칙 로더](../../backend/src/main/java/com/beone/api/benefit/rule/loading/RuleCatalogLoader.java)가 서버 시작 때 이 JSON을 읽어 `RuleCatalog`를 만듭니다. 파일이 잘못되면 서버가 뜨지 않습니다.

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

- **확정 값:** `{"value": ..., "evidence": "E1"}`. `evidence`는 필수이며, `fractionalWon`만 `basis`·`decision`으로 대신합니다.
- **미확정 값:** `{"unresolved": "이유"}`. 코드의 `RuleTerm.Unresolved`이고 `확인 필요`를 뜻합니다. 0원이나 기본값으로 바꾸지 않습니다.
- **대상 가맹점:** `{"type": "ALL_MERCHANTS"}` 또는 `{"type": "SPECIFIC_CLASSES", "classes": [...]}`
- **계산 방식:** `{"type": "RATE", "basisPoints": 100}` 또는 `{"type": "FIXED_AMOUNT", "won": 2500}`

## 판정·표시에 쓰는 값과 아직 쓰지 않는 값

로더가 읽어 모델에 담습니다. "구현"이 #65인 값은 판정이나 결과 표시에 쓰고, 나머지는 값이 있어도 계산 결과가 달라지지 않습니다.

| 필드 | 뜻 | 구현 | 근거 |
| --- | --- | --- | --- |
| `fractionalWon.basis`, `fractionalWon.decision` | 절사가 문서 대조가 아닌 사용자 결정임을 표시 | #65. 원 미만을 실제로 버린 서비스의 결과에 표시 | [D-03](../decisions.md#d-03d-04-확정-정책) |
| `stackingBasis` | 중복 합산의 계산 기준. 없으면 합산하지 않음 | 합산 조건 판정에 사용 | [D-13 #35 8번](../decisions.md#d-13-확정-정책) |
| `inclusionUnconfirmed` | 포함 관계가 확인되지 않은 코드 쌍 | 서비스 쪽은 #65. `policy` 쪽은 미구현(원장·실적 작업) | 아래 "포함 관계 확인 필요" |
| `unevaluatedExclusions` | 주문 입력에 없어 판정하지 못하는 제외 조건의 문서 표현 | #65. 서비스 결과에 그대로 담음. API 응답은 미구현 | [D-20](../decisions.md#d-20-확정-정책) |
| `displayName` | 화면 이름. 내부 서비스를 나눠도 문서 이름을 유지(09174 `BASIC`) | 미구현(API) | [D-20](../decisions.md#d-20-확정-정책) |
| `newCardGrace.value.deemedPerformance` | 유예 중 간주 실적. 09174의 30만원은 문서 결과와 같은 효과의 모델 표현이다 | #65 | [D-20](../decisions.md#d-20-확정-정책) |
| `newCardGrace.value.partialPerformance` | 유예 중 전월 실적이 0원 초과 간주 실적 미만일 때의 처리. `NEEDS_CONFIRMATION`(문구 "실적이 없는 경우에도": 09174·01664·09271) 또는 `DEEMED_TIER`(문구 "1구간 서비스 제공": 01914). 판정 결과를 바꾸는 값이다. `NEEDS_CONFIRMATION`이면 0원 초과 간주 실적 미만을 확인 필요로 두고, `DEEMED_TIER`면 간주 구간을 쓴다. 근거는 `newCardGrace.evidence`다 | #65 | [D-20](../decisions.md#d-20-확정-정책) |

`evidence`와 `performanceExcludedClassesEvidence`는 근거 표와 연결하는 ID입니다. 로더는 형식만 확인하고 모델에 담지 않습니다. 근거 표에 ID가 있는지는 테스트(`RuleDataConsistencyTest`)가 확인합니다.

## 포함 관계 확인 필요

서비스와 `policy`의 `inclusionUnconfirmed`는 포함 관계가 확인되지 않은 코드 쌍입니다. 코드가 서로 달라도 불일치로 단정하지 않습니다.

```json
{"merchantClass": "STARBUCKS_STORE", "relatedClass": "COFFEE_BEVERAGE"}
```

- **뜻:** 주문이나 거래의 코드가 `merchantClass`이면, `relatedClass`에 드는지 모릅니다.
- **서비스에 있을 때:** 그 서비스는 그 주문에서 `확인 필요`입니다. [D-18 U2 ① 예외](../decisions.md#d-18-확정-정책)의 대상 업종 불일치로 보지 않습니다.
- **`policy`에 있을 때:** 그 거래가 전월 실적에서 제외되는지 `확인 필요`입니다. 제외나 포함으로 단정하지 않습니다.
- 값은 2026-11-03 사용자 결정으로 표시했습니다. 쌍별 근거는 각 근거 표에 있습니다.
- **로더 검증:** 두 코드가 허용 목록에 있어야 합니다. 서비스의 쌍은 `relatedClass`가 그 서비스의 대상이나 제외 코드여야 하고, `policy`의 쌍은 실적 제외 코드여야 합니다. `merchantClass`가 이미 그 목록에 있으면 거부합니다.
- **서비스 쪽 판정은 #65에서 구현했습니다.** 주문 코드가 쌍의 `merchantClass`이면 그 서비스는 그 주문에서 `확인 필요`입니다. 다른 미충족이 확인되거나 택1이 `NONE`·다른 서비스이면 그 사유가 우선합니다. `policy` 쪽(실적 제외)은 아직 읽지 않습니다.

## 키가 없을 때

**필수 키가 빠지거나 모르는 키가 있으면 로더가 거부하고 서버가 뜨지 않습니다.** 기본값을 채우지 않습니다. 빈 값은 "제외 없음", "결제 방식 없음"이라는 다른 뜻이 되기 때문입니다.

- `paymentMethods`: 필수이고 1개 이상입니다. 문서에 제한 표기가 없으면 3종을 적습니다([D-20](../decisions.md#d-20-확정-정책)).
- `excludedClasses`, `unevaluatedExclusions`: 필수입니다. `[]`는 그런 조건이 없다는 뜻입니다. 코드가 있는 조건만 앞쪽에, 나머지는 뒤쪽에 문서 표현으로 적습니다.
- 생략할 수 있는 키는 `displayName`, `stackingBasis`, `inclusionUnconfirmed`뿐입니다. `stackingBasis`가 없으면 합산하지 않는다는 뜻입니다.

## 코드 값

`merchantClass` 코드는 규칙 데이터의 49개와 평가 fixture의 `ORDINARY_DOMESTIC`이고, 2026-11-03 사용자가 승인했습니다. 목록은 [`merchant-classes.txt`](../../backend/src/main/resources/rules/merchant-classes.txt)에 있고, 로더가 규칙 데이터를 이 목록과 대조합니다. 주문 입력(API)을 이 목록으로 거를지는 업무 API 단계에서 정합니다. 약관에 코드가 없고, KB 업종 분류와의 일치는 확인하지 않았습니다. 코드와 원문의 대응은 각 근거 표의 "코드 표"를 봅니다.
