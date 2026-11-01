# BeONE 구조 안내

## 이 문서의 목적

처음 참여한 팀원이 저장소의 역할 분담과 코드를 읽는 순서를 이해하기 위한 안내입니다. 요구사항은 [PRD](PRD.md), 확정 결정과 미결 사항은 [결정 기록](decisions.md), 실행·검증 명령은 [README](../README.md)를 기준으로 합니다. 이 문서는 요구사항을 새로 결정하지 않습니다.

## 모듈과 역할

- [android/](../android/): Kotlin Android 앱. 제품에서는 React 화면을 표시할 WebView 셸과 기기 인증을 담당할 예정입니다. 현재 구성만으로 WebView·생체인증 연동이 구현되었다고 판단하지 않습니다.
- [frontend/](../frontend/): Vite·React·TypeScript 웹. 주문, 카드 상태, 추천 결과를 보여줄 화면 영역이며 현재는 초기 구성 단계입니다.
- [backend/](../backend/): Java 21·Spring Boot API. 카드 규칙과 계산 입출력 모델, 공통 API 응답·예외 처리를 보유합니다. DB 마이그레이션은 Flyway로 관리합니다.
- [docs/](./): 요구사항, 결정, 조사·평가 자료와 개발 안내.
- [fixtures/](../fixtures/): 비식별 평가·시연 데이터. 테스트용 합성 값은 실제 관측값과 구분합니다.

세 애플리케이션은 독립적으로 빌드·테스트합니다. 실행 방법을 이 문서에 중복해서 관리하지 않습니다.

## 도메인이란?

도메인은 제품에서 다루는 문제 영역입니다. 패키지는 관련 코드를 묶는 단위입니다. 하나의 도메인 안에 식별자, 입력, 규칙, 결과 등 여러 책임의 패키지가 들어갈 수 있습니다.

현재 설명 문서가 있는 도메인:

- [benefit: 카드 혜택 규칙과 계산 입출력](domains/benefit.md)
  - 위치: `backend/src/main/java/com/beone/api/benefit/`
  - 하위 패키지: `card`, `money`, `state`, `input`, `rule`, `primitive`, `result`
  - 책임: 안전한 데이터 표현, 입력·결과 일관성 검사, 날짜에 맞는 검증 규칙 선택, 서비스 한 건의 산식·개별 조건 판정(`primitive`), 서비스 후보·채택·기여액 결과 표현(`result`).
  - 범위 밖: 판정을 묶어 카드별 혜택을 내는 계산·최적화 엔진, HTTP 추천 API, CODEF 통신 구현.
- [codef: CODEF 카드 응답 해석 경계](domains/codef.md)
  - 위치: `backend/src/main/java/com/beone/api/codef/`
  - 책임: 승인내역·보유카드·실적조회 응답 본문에서 결과 코드 분류와 비민감 필드만 해석. 빈 값·형식 오류를 0원이나 0건으로 바꾸지 않고, 민감 필드를 옮기지 않습니다.
  - 범위 밖: HTTP 호출·OAuth·요청 DTO·추가 인증 요청, DB 저장, BeONE 상태값으로의 변환. `benefit`·`common.web`에 의존하지 않습니다.

도메인이 아닌 공통 웹 기반 코드:

- `common.web`: 공통 API 응답 형식과 예외 처리
  - 위치: `backend/src/main/java/com/beone/api/common/web/`
  - 책임: 성공·실패 응답 형식, 공통 오류 코드, 요청 오류·예상하지 못한 오류의 HTTP 응답 변환과 안전한 로그.
  - 의존 방향: 컨트롤러·예외 처리기 같은 웹 경계에서만 사용합니다. 도메인 코드는 이 패키지에 의존하지 않습니다.
  - 설명 문서: [API 공통 응답·오류 처리](api.md)

## 데이터 흐름과 현재 구현의 경계

아래는 모델을 연결하는 개념 흐름이며 완성된 실행 경로가 아닙니다.

```text
주문·보유 카드·거래 원장·월별 상태·예정 지출
    → RecommendationInput
카드 약관과 검증된 규칙 버전
    → RuleCatalog → RuleSelection
두 입력을 사용한 혜택 계산·최적화 [후속 구현]
    → CardBenefit → Recommendation [결과 모델]
```

실제 결제는 제품 범위가 아닙니다. CODEF 실거래와 자체 모의 거래 원장은 분리합니다. 확인되지 않은 상태·규칙·혜택 금액을 0원으로 대체하지 않습니다.

## 코드를 읽는 순서

1. [benefit 안내](domains/benefit.md)에서 용어와 패키지 책임을 확인합니다.
2. `RecommendationInput`으로 계산에 필요한 입력을 살펴봅니다.
3. `CardRuleVersion`과 `RuleCatalog`로 규칙 표현과 선택을 살펴봅니다.
4. `Recommendation`과 `BenefitAmount`로 결과와 불확실성 표현을 확인합니다.
5. `backend/src/test/java/com/beone/api/benefit/`에서 모델 검증과 fixture 매핑 테스트를 읽습니다. 매핑 성공은 계산 엔진 정확성이나 공식 약관 검증 완료를 뜻하지 않습니다.

## 문서 유지

도메인 추가·이동·삭제나 도메인 간 관계 변경 시 이 문서를 갱신합니다. 세부 클래스 설명은 해당 도메인 문서에서 관리합니다. 작성·갱신 규칙은 [CLAUDE.md](../CLAUDE.md)의 도메인·구조 문서 유지, PR 확인 항목은 [CONTRIBUTING.md](../CONTRIBUTING.md)를 따릅니다.
