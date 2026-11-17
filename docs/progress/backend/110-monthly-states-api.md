# #110 카드 월간 상태 조회·수동 입력 API

브랜치 `feat/110-monthly-states-api`, 시작 기준 `origin/develop` `9eb8596`.

## 2026-11-17 · `monthlystate` 패키지 · PR(`develop` 대상, 병합 전)
- 근거: 관리 기록 결정 25(업무 API 1차, 작업선 A의 A3), 결정 27 A3절(가상 월간 시드 없음, 계약 공백 10건은 관리자 판단), [`docs/api/monthly-states.md`](../../api/monthly-states.md), [업무 API 공통 규약](../../api.md#업무-api-공통-규약), [D-04](../../decisions.md#d-03d-04-확정-정책)·[D-16·D-17](../../decisions.md#d-15d-16d-17-확정-정책)·[D-18](../../decisions.md#d-18-확정-정책) K1·[D-19](../../decisions.md#d-19-확정-정책)·[D-22](../../decisions.md#d-22-확정-정책)·[D-23](../../decisions.md#d-23-확정-정책).
- 한 일:
  - `backend/src/main/java/com/beone/api/monthlystate/`(`web`/`application`/`persistence`): `GET`·`PUT /api/v1/me/cards/{userCardId}/monthly-states/{month}`. 기능별 오류 처리기에서 `USER_CARD_NOT_FOUND` 404, `USER_CARD_MAPPING_REQUIRED` 409, 규칙 ID 위반 400을 만듭니다. `local`·`demo` 프로필에서만 등록됩니다.
  - 보유 카드 존재·소유·매핑은 `card`의 `UserCardService.list`로, 버킷·서비스 ID는 `CardProductService.getProduct`로 확인합니다. 둘 다 읽기만 하며 `card`·`benefit`·`common`·`user` 코드는 바꾸지 않았습니다.
  - Flyway `V5__monthly_states.sql`(임시 번호). 테이블은 `monthly_states`(카드·월 유니크, 저장 시 규칙 상품 ID), `monthly_state_bucket_usages`, `monthly_state_service_selections`입니다. 금액과 기간이 함께 있거나 함께 없도록 하는 체크 제약을 두었고, 시드는 없습니다.
  - 테스트:
    - 단위 `MonthlyStateServiceTest`: 실제 규칙 파일 사용
    - 웹 `MonthlyStateApiWebTest`: `controllers` 한정
    - 통합 `MonthlyStateIntegrationTest`
    - 기대 JSON은 계약 예시와 결정 27 A3에서 먼저 옮겼습니다.
  - 문서: [monthlystate 안내](../../domains/monthlystate.md)(신규), [구조 안내](../../architecture.md)의 구현 현황과 `monthlystate` 줄.
- 서비스에서 달라진 점: 시연 화면에서 보유 카드마다 그 달의 전월 실적·버킷 사용액·택1 선택·등록일을 직접 입력하고 다시 볼 수 있습니다. 입력하지 않은 값은 0이 아니라 `UNKNOWN`으로 보입니다.
- 결정 27 A3로 정한 것(계약 문서는 바꾸지 않음). 세부 근거는 [monthlystate 안내](../../domains/monthlystate.md#결정과-근거)에 있습니다.
  - `afterSnapshot`: 입력이 있으면 `{0,0,0}`, 없으면 `null`. 입력 응답에는 없습니다.
  - ID 검사: 그 달 1일·말일 규칙의 합집합과 대조합니다. 선택은 선택형 서비스만 받습니다.
  - 계약의 "(제안)" 400 두 가지를 채택했습니다.
  - 적용일 없는 선택은 저장하되 `NOT_ENTERED`입니다.
  - 등록일은 (카드, 월) 본문의 일부입니다. 권장안에서 바꾼 부분입니다.
  - 요청 순서를 유지하고, 같은 ID 중복은 400입니다.
  - 매핑이 바뀌면 이전 규칙의 버킷·택1 값은 보여 주지 않습니다.
  - 직접 입력한 음수 전월 실적은 `usable=true`입니다.
  - `month`는 `YYYY-MM`만 받습니다. 검사 순서는 400 → 404 → 409 → 400입니다.
- 한계(후속 작업 몫):
  - **스냅샷 뒤 모의 승인 합산 없음.** 모의 결제(B3)가 아직 없어 `afterSnapshot`은 실제로 0건이라 `{0,0,0}`입니다. B3에서 실제 합산을 연결해야 합니다. 체인 버킷 사용액에 더하는 계산은 B3·B2 몫입니다.
  - 가상 포트폴리오에는 월간 시드가 없어, 상태를 입력하기 전까지 추천이 대부분 `확인 필요`입니다(결정 27 A3).
  - 엔진 입력(`benefit` 쪽 `MonthlyStateInput`)으로 바꾸는 변환은 추천(B2)이 합니다.
  - 같은 카드·같은 달을 처음 저장하는 요청 두 개가 동시에 오면 유니크 제약 때문에 한쪽이 실패할 수 있습니다.
- 검증: `./gradlew test webTest integrationTest build javadoc --rerun-tasks` 결과는 PR 본문에 기록합니다.
- 서버 DB 적용·배포는 하지 않았습니다. frontend 변경 없음.
