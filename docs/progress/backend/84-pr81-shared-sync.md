# #84 PR #81 엔진 계약의 공유 문서 정합

브랜치 `docs/84-pr81-shared-sync`, 시작 기준 `origin/develop` `5885454`(PR #81 병합).

## 2026-11-16 · 공유 문서 정합 · PR 전
- 배경: PR #81(#80)로 엔진 입구 계약이 바뀌었지만 `docs/api/`와 `docs/decisions.md`는 바뀌지 않았습니다. 2026-11-16 사용자 결정으로 관리측이 문서만 고치는 PR로 정리하고 팀원 리뷰를 받습니다. 새 정책은 만들지 않고, 정해지지 않은 것은 "미정"으로 둡니다.
- 기준: 코드가 정답입니다. 아래 이름은 `origin/develop`의 `backend/src/main/java/com/beone/api/benefit/engine/`와 `result/ApplicationStatus.java`에서 확인했습니다.
- 바꾼 내용
  - `docs/decisions.md` D-22: 표 행과 확정 정책 머리말의 구현 상태(#71 입구 타입, #81 계산 연결)를 갱신했습니다. "음수 실적의 정책 근거는 입력에 없다" 문장을 음수 전월 실적 문맥과 `policyEvidence`(취소 귀속·할인 매출) 입력 두 줄로 나눴습니다. `PERIOD_MISMATCH`를 엔진에 넘기면 예외라는 점을 기존 문장에 덧붙였습니다. 서비스 상태 순서 끝의 한도 소진·`NO_CONTRIBUTION`, 카드 상태 4의 `NO_CONTRIBUTION` 위치를 코드에 맞췄습니다. 절 끝에 "D-22 2026-11-16 보충"으로 사용자 결정 2건(실적 정책 근거 입력, `NO_CONTRIBUTION`)을 적었습니다. 새 D-번호는 만들지 않았습니다.
  - `docs/api/recommendations.md`: 요청으로 받지 않는 엔진 입력 `policyEvidence` 절, `services[].applicationStatus`의 `NO_CONTRIBUTION`과 미채택 순서, 카드 `applicationStatus`, `excludedCards[]`의 `applicationStatus`·`appliedRule`·`pendingReasons`, `noteCodes` → `notes[]` 대응, `policyNotes` 현재 값을 추가·수정했습니다. 엔진은 `PLANNED_SPENDING_EXCLUDED`를 항상 내므로 0장 응답 예시의 `notes`에 예정 소비 문구를 넣었습니다.
  - `docs/api/monthly-states.md` 구현 시 주의: 낡은 두 문장(판정기의 음수 거부가 선행 작업, `CardMonthlyState`에 선택 없음이 없음)을 엔진 입구 기준으로 바꾸고, `otherBucketsUsedWon` 필수와 `PERIOD_MISMATCH` 금지를 추가했습니다.
  - `docs/api/README.md`: 엔진 입구가 구현됐고 업무 API는 구현 전이라는 상태로 고쳤습니다.
  - `docs/progress/backend/README.md`: #80의 "미병합" 문장을 병합 상태로 고치고 #84를 추가했습니다.
- 미정으로 남긴 것
  - `SINGLE_COMPARED_CARD`의 화면 문구. 규칙("최적"이라고 표시하지 않음)만 있습니다.
  - 업무 API가 `policyEvidence`를 `CONFIRMED_ABSENT`·`PRESENT`로 판정하는 기준(어떤 원장·시드를 완전한 근거로 볼지, 가상 포트폴리오 시드에 값을 둘지). 정해지기 전에는 `UNDETERMINED`입니다.
  - D-23 #1의 "기준일이 승인일이 아닌 카드를 `notes`에 표시"를 만드는 쪽. 엔진 `noteCodes`에는 없습니다.
  - `excludedCards[]` 추가 필드는 필드 설명에만 넣고 JSON 예시에는 넣지 않았습니다.
- 바꾸지 않은 것: `docs/domains/benefit.md`는 이미 코드와 같아 고치지 않았습니다. 예시 JSON의 금액·`VERIFIED`는 합성 예시이며 확정값으로 바꾸지 않았습니다. 제품 코드·테스트·규칙·fixture는 바꾸지 않았습니다.
- Notion 사본과 차이, 미반영: Notion 명세 사본(2026-11-16 수집, Notion 원문 직접 확인 아님)에는 월간 상태의 `PERIOD_MISMATCH` 저장, `serviceSelectionStatus`·`otherBucketsUsedWon` 누락, 추천 응답의 enum 이름·`monthlyTotalBenefit` 예시 차이, 월간 상태 쓰기의 409 `PORTFOLIO_READ_ONLY` 등 저장소 문서와 다른 내용이 있습니다. 저장소 문서를 바꾸는 근거로 쓰지 않았고 Notion도 고치지 않았습니다.
- 검증: 문서만 바뀌어 Gradle·npm은 실행하지 않았습니다. `git diff --check`, 바뀐 문서의 제목 구조·상대 링크, 문서의 클래스·필드·상태값 이름과 `origin/develop` 코드 대조(`git grep`), `git diff --name-only origin/develop`가 `docs/`만인지 확인했습니다.

## 2026-11-16 · 백엔드 진행 README의 #80 병합 상태 정합 (범위 추가)
- 출처: PR #83(#82)의 CodeRabbit 리뷰 스레드(`docs/progress/backend/README.md`, 미해결)가 지적한 내용을 관리자가 이 작업 범위에 추가했습니다.
- 내용: `docs/progress/backend/README.md`에서 PR #81을 "PR #81 브랜치 코드 기준, `develop` 미병합·병합 승인 대기"로 적은 #80 관련 문장(머리 기준 설명, 결과 연결 줄, 현재 작업과 PR의 #80 줄, 다음 작업의 "(미병합)" 세 곳)을 "`develop` 반영(PR #81, `5885454`)"으로 고쳤습니다. 날짜가 붙은 과거 기록은 그대로 두었고, 원장·예정 소비 저장·모의 취소·선택형·공유 한도 배분·API·화면은 계속 미지원으로 적었습니다.
- PR #83이 같은 파일의 #80 줄 바로 아래에 줄 하나를 추가하므로, 이 작업 기록의 링크는 새 줄로 넣지 않고 #80 줄 끝에 붙였습니다. #80 줄 자체는 고쳐야 해서 PR #83과 인접 줄 충돌이 생길 수 있습니다.
