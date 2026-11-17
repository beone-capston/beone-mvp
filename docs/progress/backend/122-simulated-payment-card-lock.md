# #122 같은 카드 동시 모의 승인 한도 경합 방지

브랜치 `fix/122-simulated-payment-card-lock`, 시작 기준 `origin/develop` `c02409c`.

## 2026-11-17 · payment 승인 잠금, card 잠금 메서드 · PR(`develop` 대상, 병합 전)
- 근거: PR #121 CodeRabbit Major 지적(승인 트랜잭션이 주문 행만 잠가 같은 카드의 다른 주문을 동시에 승인하면 같은 잔여 한도로 차감액을 정할 수 있음)과 관리자 판단(B2f 병합 뒤 별도 후속 B3f), 관리 기록 결정 25·28·30, [`docs/api/mock-payments.md`](../../api/mock-payments.md)의 "한 트랜잭션으로 처리", D-16·D-17·D-18 K1. 새 API·응답·오류 코드·계약 변경은 없습니다.
- 한 일:
  - card: `UserCardRepository.lockActiveByUserIdOrderByUserCardId`(해제하지 않은 카드 전부를 ID 오름차순 `PESSIMISTIC_WRITE` 잠금 조회)와 `UserCardService.lockHeldCardsForPayment`(`MANDATORY` 트랜잭션) 추가.
  - payment: `SimulatedPaymentService.approve`가 주문 행 잠금 직후, 멱등 키 조회·재계산 전에 보유 카드 행을 잠급니다. 잠금 순서와 읽기 시점 근거를 클래스 설명에 적었습니다.
  - 테스트: 단위 `SimulatedPaymentServiceTest`(잠금 순서, 취소는 카드 잠금 없음), `UserCardServiceTest`(잠금 조회 위임). 통합 `SimulatedPaymentCardLockIntegrationTest`(신규, 차례 승인·래치와 스레드 2개 동시 승인). 기존 단언은 바꾸지 않았습니다.
  - 문서: [payment 안내](../../domains/payment.md)의 동시성과 잠금 순서 절, [card 안내](../../domains/card.md)의 잠금 메서드 한 줄.
- 잠금 순서: 승인은 주문 행 → 보유 카드 행(ID 오름차순), 취소는 결제 행 → (전액이면) 주문 행. 승인이 카드를 잠그는 주문은 `CREATED`이거나 같은 키 재생이고 취소가 잠그는 주문은 이미 승인된 주문이라 순환 대기가 없습니다.
- 읽기 시점: MySQL 기본 REPEATABLE READ는 첫 일반 읽기에서 읽기 시점을 정합니다. 잠금 읽기(주문·카드)를 첫 일반 읽기(멱등 키 조회)보다 앞에 둬서, 카드 잠금을 기다린 승인이 앞 승인이 커밋한 원장을 읽습니다. 카드 잠금을 재계산 직전(멱등 키 조회 뒤)에 두면 이미 정해진 읽기 시점 때문에 앞 승인의 원장을 못 보므로 이 위치가 필요합니다.
- 취소는 카드를 잠그지 않습니다. 다른 결제의 원장을 읽어 금액을 정하지 않아, 동시 승인과의 결과가 "승인 뒤 취소"를 차례로 처리한 것과 같기 때문입니다.
- 시험 설계: 1차 4종 규칙에는 한도 차감액이 확정되는 승인이 없습니다(09174는 취소 귀속 미확정, 01664·01914는 할인 매출 실적 제외 미확정, 09271 한도 서비스는 선택형이라 엔진 범위 밖). 그래서 금액 대신, 앞 승인의 모르는 차감액(`null`)을 #120 규칙으로 읽은 뒤 승인의 재계산이 "GREAT 사용액 모름"을 사유로 내는지로 확인합니다. 읽지 못하면 실적 정책 사유만 나옵니다. 카드 잠금 호출을 뺀 상태에서 동시 승인 시험이 3회 모두 실패함을 로컬에서 확인한 뒤 되돌렸습니다.
- 서비스에서 달라진 점: 같은 사용자가 여러 주문을 동시에 모의 승인해도 뒤 승인이 앞 승인의 한도 사용을 반영합니다. 같은 사용자의 승인은 카드가 겹치지 않아도 차례로 처리됩니다(시연 규모라 그대로 둠). frontend 변경 없음.
- Flyway 없음.
- 검증: `./gradlew test webTest integrationTest build javadoc --rerun-tasks` 결과는 PR 본문에 기록합니다.
- 로컬·테스트 DB만 사용했고 서버 DB 적용·배포는 하지 않았습니다.
