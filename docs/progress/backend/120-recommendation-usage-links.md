# #120 추천 무효화·최근 추천·스냅샷 뒤 사용액 연결

브랜치 `feat/120-recommendation-usage-links`, 시작 기준 `origin/develop` `9d26194`.

## 2026-11-17 · recommendation·monthlystate·order·card·ledger 연결 · PR(`develop` 대상, 병합 전)
- 근거: 관리 기록 결정 27(B2-5 후속), 결정 28 보충, 결정 29, 관리자 답변(의존 방식, 확인 필요 표현, 합산 대상, 요약 규칙, 무효화 목록 기준, 최근 추천 기준), [`docs/api/cards.md`](../../api/cards.md) 415·465행, [`docs/api/orders.md`](../../api/orders.md) 172행, [`docs/api/monthly-states.md`](../../api/monthly-states.md) 97·109·132~137·218행, D-09·D-16·D-17. 새 API는 없습니다.
- 한 일:
  - 추천 입력의 스냅샷 뒤 사용액(recommendation, 신규 `SnapshotUsage`): 월간 상태 `snapshotAt` 뒤에 기록된 같은 카드·주문 월 모의 원장 행을 읽어 승인 차감액을 같은 버킷 사용액에 더합니다. 입력하지 않은 버킷의 차감액은 `otherBucketsUsedWon`에 더합니다. 미입력 값·스냅샷 없음은 미입력 그대로입니다.
  - 월간 상태 `afterSnapshot`(monthlystate): 항상 `{0, 0, 0}`이던 값을 원장에서 셉니다(승인 건수·승인 금액 합·취소 건수).
  - 주문 `latestRecommendationId`(order 포트 `LatestRecommendationLookup`, 구현 recommendation `RecommendationLinks`): 그 주문 추천 중 ID가 가장 큰 것, 없으면 `null`.
  - 보유 카드 `invalidatedRecommendationIds`(card 포트 `RecommendationInvalidationLookup`, 구현 `RecommendationLinks`): 변경 직전 유효했고 이 카드 변경으로 `MAPPING_CHANGED`가 된 추천, ID 오름차순. 무효화 판정은 추천 조회와 같은 지연 판정(`RecommendationService.invalidationOf`, 공개 메서드 추가)입니다.
  - ledger: 공개 읽기 메서드 `LedgerQueryService.activityAfter`와 결과 `LedgerActivity`, 저장소 조회 메서드 하나. 쓰기 부품은 그대로입니다.
  - recommendation 저장소 조회 메서드 2개(주문별 최신 추천, 카드를 근거로 쓴 추천 번호). Flyway 없음, 공유 설정(`common`·yml) 변경 없음. payment 코드 변경 없음.
  - 테스트: 단위 `SnapshotUsageTest`·`RecommendationLinksTest` 신규, `LedgerQueryServiceTest`·`MonthlyStateServiceTest`·`UserCardServiceTest`·`RecommendationServiceTest`에 경우 추가, 웹 `OrderApiWebTest`에 계약 예시(`rec-3001`) 경우 추가, 통합 `RecommendationLinksIntegrationTest` 신규. 기존 단언은 바꾸지 않았고 생성자 인자만 맞췄습니다(주석 한 줄의 "B2f 전" 문구 정정).
  - 문서: [recommendation](../../domains/recommendation.md)·[monthlystate](../../domains/monthlystate.md)·[order](../../domains/order.md)·[card](../../domains/card.md)·[ledger](../../domains/ledger.md)·[payment](../../domains/payment.md) 안내의 한계 절, [구조 안내](../../architecture.md)의 추천 흐름 한 줄(원장 입력 반영).
- 의존 방향: recommendation→ledger, monthlystate→ledger를 새로 읽습니다(ledger는 card만 읽어 순환 없음). order·card는 자기 패키지의 포트만 알고 recommendation을 import하지 않습니다. card·order는 포트 빈을 부를 때 찾아 빈 생성 순환도 없습니다(통합 테스트의 컨텍스트 기동으로 확인).
- 서비스에서 달라진 점: 같은 달에 모의 결제를 한 뒤 다시 추천하면 한도가 있는 서비스의 남은 한도가 줄어든 상태로 계산됩니다. 카드 매핑·추천 제외를 바꾸면 응답에 무효가 된 추천 ID가 나오고, 주문 상세에서 마지막 추천을 볼 수 있으며, 월간 상태 화면에 입력 뒤 모의 승인·취소 건수가 나옵니다(화면 연결은 이 작업 범위 밖, frontend 변경 없음).
- 관리자 승인으로 정한 것(계약 문서는 바꾸지 않음):
  - 차감액을 모르는 승인, 차감액이 0이 아닌 승인의 스냅샷 뒤 취소(스냅샷 전 승인 포함)가 있는 버킷은 엔진 입력에서 `notEntered()`로 넘겨 `확인 필요`로 둡니다. 4종 모두 한도 복원 확정 규칙이 없기 때문이며(D-17) 0으로 대신하지 않습니다.
  - 합산 대상은 같은 카드·거래일이 주문 월·기록 시각 > `snapshotAt`. 같은 초는 넣지 않습니다. 더한 값의 출처는 `MANUAL`, 기간·시각은 스냅샷 값입니다.
  - `afterSnapshot`은 `snapshotAt`이 있을 때만, 승인 금액은 취소를 빼지 않은 승인 합계입니다.
  - 무효화 목록은 이미 무효였던 추천을 넣지 않습니다. 그래서 추천 제외를 되돌린 응답은 `[]`입니다. 배열 노출 규칙(매핑은 늘, 표시 이름만 바꾸면 없음)은 그대로입니다.
  - 최근 추천은 ID 최대값이며 승인 직전 재계산도 포함하고 승인 뒤에도 유지합니다.
- 엔진 사유 추가 후보: 위 `확인 필요` 버킷의 사유가 엔진 입구에서 `NOT_ENTERED`로만 표현됩니다(결과 상태는 맞지만 사유가 부정확). 엔진(`benefit`) 변경은 금지 범위라 하지 않았습니다.
- 한계: CODEF 실제 거래는 반영하지 않습니다(2차). 카드 해제(`DELETE`)는 응답 본문이 없어 무효화 목록을 돌려주지 않습니다. 1차 C01 같은 한도 없는 서비스는 차감액이 없어 사용액 반영이 통합 테스트에 드러나지 않으므로 반영 규칙은 단위 테스트로 확인했습니다.
- 검증: `./gradlew test webTest integrationTest build javadoc --rerun-tasks` 결과는 PR 본문에 기록합니다.
- 로컬·테스트 DB만 사용했고 서버 DB 적용·배포는 하지 않았습니다. frontend 변경 없음.
