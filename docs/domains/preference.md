# preference 도메인 안내

## 목적과 범위

`preference`는 사용자의 **결제 전 설정**을 저장·조회하는 업무 API 패키지입니다(#111). 결제 전 설정은 결제할 때 카드를 매번 추천으로 고를지(자동 추천), 정해 둔 대표 카드로 고정할지와 그 대표 카드를 정합니다([D-12](../decisions.md#d-10d-12-확정-정책)). 결제 중에 카드를 다시 고르는 단계는 없으므로 사용자 통제는 이 설정으로만 합니다(PRD §4). 계약은 [`docs/api/preferences.md`](../api/preferences.md)입니다.

같은 패키지가 보유 카드 쪽 **대표 카드 보호**도 맡습니다. 보유 카드 API(`card`)가 열어 둔 확장 지점 [`RepresentativeCardGuard`](../../backend/src/main/java/com/beone/api/card/application/RepresentativeCardGuard.java)를 구현해, 쓰고 있는 대표 카드를 추천에서 빼거나 해제하지 못하게 합니다([`docs/api/cards.md`](../api/cards.md), [D-23](../decisions.md#d-23-확정-정책)).

구현된 범위:

- `GET /api/v1/me/payment-preference`: 설정 조회. 설정한 적이 없으면 `AUTO_RECOMMEND`, 대표 카드 `null`, `updatedAt` `null`
- `PUT /api/v1/me/payment-preference`: 설정 변경. 가상 포트폴리오 사용자도 바꿀 수 있습니다(docs/api.md 업무 오류 코드 아래 문장)
- 보유 카드 `PATCH /api/v1/me/cards/{userCardId}`(추천 제외)·`DELETE`(해제)의 409 `REPRESENTATIVE_CARD_IN_USE`와 자동 추천 모드 대표 카드 해제 시 비우기
- 로컬 회원 프로필 `member` 등록(G3a #159): 같은 컨트롤러를 `member`에도 등록했습니다. 현재 사용자는 회원 세션의 '내 카드' 장부이고, 세션 401·쓰기 CSRF 403·`Cache-Control: no-store`는 `/api/v1/me/**` 공통 인터셉터·필터가 컨트롤러 전에 처리합니다([docs/api.md 회원 세션](../api.md#회원-세션member-프로필), 결정 37). 설정은 `user_id` 기준이므로 회원마다 나뉘고, 새 회원은 행 없이 미설정 표시입니다. 남의 카드나 시연 장부의 카드를 대표 카드로 고르면 404 `USER_CARD_NOT_FOUND`입니다. 설정 변경은 기존 추천을 무효화하지 않습니다(결정 32 Q3). 서비스 코드는 바꾸지 않았습니다.
- Flyway `V6__payment_preferences.sql`: `payment_preferences` 테이블. 시드는 없습니다. 번호는 다른 작업선과 나눈 **임시 번호**이며 병합 직전 관리자가 정한 순서에 따라 바뀔 수 있습니다(D-23 12항).

구현하지 않은 범위:

- 추천(B2)·모의 승인(B3)에서 이 설정을 읽어 쓰는 일. 지금은 저장만 하고 어떤 계산도 이 값을 읽지 않습니다.
- 대표 카드의 혜택 `확인 필요` 처리([D-19](../decisions.md#d-19-확정-정책)). 주문마다 달라지므로 대표 카드 선택 제한이 아니며, 추천·모의 승인이 다룹니다.

## 핵심 용어

- **카드 선택 방식(`PaymentSelectionMode`)**: `AUTO_RECOMMEND`(자동 추천)·`FIXED_REPRESENTATIVE`(대표 카드 고정). 엔진의 `benefit.input.SelectionMode`와 값은 같지만 업무 모델로 따로 둡니다. 엔진으로 옮기는 일은 추천 작업이 합니다.
- **대표 카드(`representativeUserCardId`)**: 보유 카드 하나의 외부 ID(`uc-`+번호). 고정 모드에서는 결제 카드이고, 자동 추천 모드에서는 D-12 동률 처리 3단계(혜택이 같을 때 대표 카드 우선)에만 씁니다.
- **추천 비교 대상(적격)**: 보유 카드 응답의 `recommendationEligibility.eligible`. 매핑이 `USER_CONFIRMED`이거나 가상 포트폴리오 카드(`SYNTHETIC`)이고, 사용자가 추천에서 빼지 않은 카드입니다([D-15](../decisions.md#d-15d-16d-17-확정-정책)). 대표 카드는 이 카드만 고를 수 있습니다.
- **미설정 표시**: 설정 행이 없을 때 조회가 돌려주는 `AUTO_RECOMMEND`·`null`·`null`. 저장된 기본값이 아니며 행을 만들지 않습니다.

## 패키지 구조

D-23 13항의 `web`/`application`/`persistence` 구조입니다.

```text
com.beone.api.preference
├── web/
│   ├── PaymentPreferenceController            API 2개, local·demo·member 프로필
│   ├── PaymentPreferenceRequest               요청 본문. 대표 카드 필드 누락과 null을 구분, 모드·대표 카드 조합 검사
│   ├── StrictTextDeserializer                 문자열 필드에 숫자·불리언 거부
│   ├── PaymentPreferenceResponse              응답 data(+09:00 시각 문자열)
│   └── PaymentPreferenceErrorCode · PaymentPreferenceExceptionHandler  USER_CARD_NOT_FOUND 404, REPRESENTATIVE_CARD_NOT_ELIGIBLE 409
├── application/
│   ├── PaymentPreferenceService               조회·변경, 대표 카드 존재·적격 확인
│   ├── PaymentPreferenceCardGuard             card의 RepresentativeCardGuard 구현(대표 카드 보호)
│   ├── PaymentSelectionMode                   카드 선택 방식
│   ├── PaymentPreferenceView · PaymentPreferenceChange  결과·입력 타입
│   └── RepresentativeCardNotEligibleException 추천 비교 대상이 아닌 대표 카드
└── persistence/
    ├── PaymentPreferenceEntity                payment_preferences 한 행(기본 키 = 사용자 ID)
    └── PaymentPreferenceRepository            카드 행·사용자 행 잠금, 설정 행 잠금 조회, 넣기·덮어쓰기(upsert)
```

소스: [web](../../backend/src/main/java/com/beone/api/preference/web/), [application](../../backend/src/main/java/com/beone/api/preference/application/), [persistence](../../backend/src/main/java/com/beone/api/preference/persistence/), [마이그레이션](../../backend/src/main/resources/db/migration/V6__payment_preferences.sql).

의존 방향: `preference` → `card.application`(`UserCardService.list`, `UserCardId`, `UserCardNotFoundException`, `RepresentativeCardGuard`·`RepresentativeCardInUseException`), `user.application`(`CurrentUser`), `common.web`(웹 경계). `card`는 `preference`를 모르고 확장 지점 인터페이스로만 만납니다.

## 처리 흐름

### 설정 변경(PUT)

1. 웹 계층이 요청 형식을 봅니다. `selectionMode` 누락·허용 밖 값, `representativeUserCardId` 필드 누락(모드와 무관), 고정 모드인데 대표 카드 `null`은 400 `INVALID_REQUEST`이고 필드 오류를 담습니다. 숫자·불리언처럼 문자열이 아닌 값과 깨진 본문은 400 `errors: []`입니다.
2. 대표 카드가 있으면 `uc-` 형식을 해석합니다. 형식이 틀리면 404 `USER_CARD_NOT_FOUND`입니다.
3. 대표 카드 행, 사용자 행(`users`) 순으로 쓰기 잠금을 겁니다(아래 동시성). 그다음 현재 사용자의 해제하지 않은 보유 카드 목록에서 대표 카드를 찾습니다. 없거나 다른 사용자·해제한 카드는 404 `USER_CARD_NOT_FOUND`입니다(보유 카드 API와 같은 규칙).
4. 찾은 카드가 추천 비교 대상이 아니면 409 `REPRESENTATIVE_CARD_NOT_ELIGIBLE`입니다.
5. 설정 행을 넣거나 덮어쓰고(`INSERT ... ON DUPLICATE KEY UPDATE`), 저장 시각(서울, 초 단위)을 `updatedAt`으로 돌려줍니다.

```java
PaymentPreferenceView saved = paymentPreferenceService.change(currentUser,
		new PaymentPreferenceChange(PaymentSelectionMode.FIXED_REPRESENTATIVE, "uc-1001"));
```

### 대표 카드 보호(보유 카드 PATCH·DELETE)

`UserCardService`는 추천 제외로 바꾸기 전에 `beforeExclusion`, 해제 전에 `beforeRelease`를 같은 트랜잭션에서 부릅니다. `PaymentPreferenceCardGuard`가 유일한 구현 빈이라 기본 구현(`NONE`)을 대신합니다.

| 설정 | 그 카드를 추천에서 뺌(PATCH) | 그 카드를 해제(DELETE) |
| --- | --- | --- |
| 대표 카드 고정 모드의 대표 카드 | 409 `REPRESENTATIVE_CARD_IN_USE` | 409 `REPRESENTATIVE_CARD_IN_USE` |
| 자동 추천 모드의 대표 카드 | 막지도 비우지도 않음 | 대표 카드를 비우고 `updatedAt`을 해제 시각으로 바꿈 |
| 대표 카드가 아니거나 설정 없음 | 그대로 진행 | 그대로 진행 |

409 응답은 보유 카드 API에서 나가므로 예외와 오류 코드는 `card` 패키지에 둡니다(아래 card 연결).

## 관리자 판단으로 정한 것(결정 28, 2026-11-17)

- **자동 추천 모드의 대표 카드를 추천에서 빼는 경우**: 계약에 문장이 없습니다(cards.md는 고정 모드 409와 자동 추천 모드 "해제 시 비움"만 적음). 막지도 비우지도 않고 저장된 대표 카드를 유지합니다. 다시 추천에 넣으면 그대로 대표 카드입니다. 이 상태에서 같은 카드를 새로 대표 카드로 저장하려 하면 409 `REPRESENTATIVE_CARD_NOT_ELIGIBLE`입니다.
- **B2 연결 한계**: 위 경우 저장된 대표 카드가 추천 비교 대상이 아닐 수 있습니다. 추천(B2)은 동률 처리 3단계에서 추천 비교 대상이 아닌 대표 카드를 무시해야 합니다(D-15 "추천 비교에서 빠진 카드는 동률 처리의 대표 카드 후보도 될 수 없다"). 이 패키지는 그 판단을 하지 않습니다.
- **미설정 조회**: 계약 응답 필드 문장대로 `AUTO_RECOMMEND`·`null`·`null`.
- **적격 기준**: card의 `RecommendationEligibility`를 그대로 씁니다. 가상 포트폴리오 시드 카드 4장(결정 27 A2-1)은 매핑 없이 적격이라 대표 카드로 고를 수 있습니다.
- **검사 순서**: 400 → 404 → 409. 형식이 틀린 `uc-` 값은 404입니다(결정 27 A2와 같음).

## card 연결(card 패키지 최소 변경, 관리자 승인)

`UserCardService`는 이미 `ObjectProvider<RepresentativeCardGuard>`로 구현 빈을 찾으므로 빈 교체를 위한 변경은 없습니다. 다만 card의 기능별 처리기는 `UserCardController`에만 적용되고, 모르는 예외는 500이 됩니다. 그래서 다음 네 곳만 바꿨습니다.

1. [`RepresentativeCardInUseException`](../../backend/src/main/java/com/beone/api/card/application/RepresentativeCardInUseException.java) 추가: 가드 구현이 던질 예외
2. `UserCardErrorCode`에 `REPRESENTATIVE_CARD_IN_USE`(409) 한 줄
3. `UserCardExceptionHandler`에 처리 메서드 하나
4. `UserCardApiWebTest`에 PATCH 추천 제외·DELETE 409 사례 2개(기존 단언은 바꾸지 않음)

preference 쪽에서 `UserCardController`를 지목하는 처리기를 두는 대안은 "기능 패키지가 자기 오류를 처리한다"는 F0 방식과 어긋나 쓰지 않았습니다.

## 동시성

- 잠금 순서는 모든 경로에서 **카드 행 → 사용자 행 → 설정 행**입니다. 보유 카드 변경(`UserCardService`)은 대상 카드 행을 쓰기 잠금으로 읽은 뒤 가드를 부르고, 가드는 사용자 행(`users`)과 설정 행을 잠급니다. 설정 변경은 대표 카드가 있으면 그 카드 행(`user_cards`)을 먼저 잠그고, 그다음 사용자 행을 잠급니다. `card`·`user` 패키지 코드는 쓰지 않고 행만 잠급니다.
- 카드 행을 먼저 잠그는 이유: 설정 저장의 외래 키 검사가 대표 카드 행에 공유 잠금을 겁니다. 설정 변경이 사용자 행을 먼저 잡으면, 같은 카드를 해제하는 요청(카드 행을 잡고 사용자 행을 기다림)과 서로 기다려 MySQL이 한쪽을 교착으로 끊습니다(CodeRabbit 지적, 통합 테스트로 재현·확인).
- 설정 행이 아니라 사용자 행을 잠그는 이유: 설정 행은 없을 수 있는데, 없는 행을 잠그면 MySQL이 간격 잠금을 걸어 처음 저장하는 요청끼리 교착이 생길 수 있습니다.
- 설정 변경은 잠금을 모두 얻은 뒤 보유 카드를 처음 읽으므로 앞서 커밋된 해제·추천 제외를 봅니다. 그래서 "고정 모드로 저장"과 "그 카드 해제"가 겹쳐도 해제된 카드가 고정 대표 카드로 남지 않습니다. 나중에 잠금을 얻은 쪽이 404 또는 409로 끝납니다.
- 형식이 맞지만 없는 카드 번호를 보내면 카드 행 잠금이 간격 잠금이 되어, 그 사이 번호로 새 카드를 등록하는 요청이 이 트랜잭션이 끝날 때(곧 404)까지 잠시 기다릴 수 있습니다.

## 제약과 한계

- 가상 포트폴리오 추천은 사용자가 월간 상태를 입력하기 전까지 대부분 `확인 필요`입니다. 가상 포트폴리오 카드 4장의 월간 상태 시드가 없기 때문입니다(결정 27 A3). 대표 카드를 정해 두어도 이 한계는 그대로입니다.
- 이 설정을 쓰는 추천·모의 승인이 아직 없습니다(B2·B3).
- `updatedAt`은 사용자가 저장한 시각 또는 자동 추천 모드 대표 카드가 해제로 비워진 시각입니다.

## 테스트

- `test`: [`PaymentPreferenceServiceTest`](../../backend/src/test/java/com/beone/api/preference/application/PaymentPreferenceServiceTest.java)(미설정 표시, 카드 행 잠금 → 사용자 행 잠금 → 카드 조회 → 저장 순서, 형식 오류·없는 카드 404, 매핑 미확인·지원 안 함·추천 제외 409, 가상 포트폴리오 카드 허용), [`PaymentPreferenceCardGuardTest`](../../backend/src/test/java/com/beone/api/preference/application/PaymentPreferenceCardGuardTest.java)(보호 표의 모든 칸, 잠금 순서)
- `webTest`: [`PaymentPreferenceApiWebTest`](../../backend/src/test/java/com/beone/api/preference/web/PaymentPreferenceApiWebTest.java)(계약 예시 응답, 실패 응답 전부, 헤더 없음·잘못된 값), [`PaymentPreferenceProfileOffWebTest`](../../backend/src/test/java/com/beone/api/preference/web/PaymentPreferenceProfileOffWebTest.java)(등록 프로필 밖 404), `UserCardApiWebTest`의 409 두 사례
- `integrationTest`: [`PaymentPreferenceIntegrationTest`](../../backend/src/test/java/com/beone/api/preference/PaymentPreferenceIntegrationTest.java)(Flyway `V6` 적용, 저장·조회, 보유 카드 API 409와 해제 시 비우기, 사용자별 분리, 카드 변경과 같은 잠금 순서에서 교착 없음, DB 검사 제약), [`MemberMonthlyStatePreferenceIntegrationTest`](../../backend/src/test/java/com/beone/api/auth/web/MemberMonthlyStatePreferenceIntegrationTest.java)(`member`: 회원 A·B 분리, 시연 카드 404, 401, CSRF 403·미저장, 남의 카드와 없는 카드의 404 본문 동일, 설정 변경 뒤 추천 유효, no-store)
