import type { CreatedOrder } from '../orders/order'
import type { Amount, Approval, Attempt, ComparedCard, Recommendation, Rule } from './contract'

export const sampleOrder = {
  orderId: 'ord-5001', amountWon: 10000, orderDate: '2026-03-16', orderTime: null,
  merchantName: '스타벅스 송도점', merchantClass: 'STARBUCKS_STORE', paymentMethod: 'DIRECT',
  status: 'CREATED', createdAt: '2026-11-24T10:40:00+09:00',
} satisfies CreatedOrder

const stamp = '2026-11-24T10:42:00+09:00'
const mockCards = [
  { userCardId: 'uc-1001', cardProductId: '09271', displayName: '올라운드 티타늄카드' },
  { userCardId: 'uc-1002', cardProductId: '09174', displayName: '청춘대로 톡톡카드' },
  { userCardId: 'uc-1003', cardProductId: '01664', displayName: '노리 체크카드' },
  { userCardId: 'uc-1004', cardProductId: '01914', displayName: '첵첵 체크카드' },
]
const fixtureRule = {
  ruleVersion: '09174-v1', verificationStatus: 'VERIFIED',
  officialSources: [{ uri: 'https://img2.kbcard.com/obj/card/download/09174__prdctOpmn_20260714.pdf', locator: 'p.1' }],
} satisfies Rule
const confirmed = (amountWon: number): Amount => ({ amountWon, status: 'CONFIRMED' })
const unknown = { amountWon: null, status: 'NEEDS_CONFIRMATION' } satisfies Amount

export function makeFixtures(attempt: Attempt, sequence: number) {
  const { order } = attempt.selection
  const { scenario, preference } = attempt
  const status = scenario === 'failed' || attempt.body.simulateFailure ? 'FAILED' : 'APPROVED'
  const selectionMode = scenario === 'fixed' ? 'FIXED_REPRESENTATIVE' : preference.selectionMode
  const representative = /^(?:sample|virtual)-(\d+)$/.exec(preference.representativeCardId ?? '')
  const fixedIndex = representative ? Number(representative[1]) : 1
  const selected = (selectionMode === 'FIXED_REPRESENTATIVE' ? mockCards[fixedIndex] : undefined) ?? mockCards[0]
  const alternative = mockCards.find(card => card.userCardId !== selected.userCardId)!
  const benefit = scenario === 'unknown' ? unknown : confirmed(scenario === 'zero' ? 0 : 700)
  const appliedRule = selected.cardProductId === '09174' && scenario !== 'unknown' ? fixtureRule : null
  const conditional = scenario === 'conditional'
  const changed = scenario === 'changed' ? true : scenario === 'unchanged' ? false : null
  const decision = {
    decidedBy: selectionMode === 'AUTO_RECOMMEND' && benefit.amountWon === 700 ? 'MONTHLY_TOTAL' : null,
    arbitraryTieBreak: false, tiedUserCardIds: [],
  }
  const recommendationId = 'rec-' + sequence
  const approval = {
    simulatedPaymentId: 'pay-' + sequence, orderId: order.orderId, status,
    approvedAt: stamp, amountWon: order.amountWon, selectionMode,
    userCardId: selected.userCardId, cardProductId: selected.cardProductId, conditional,
    currentOrderBenefit: status === 'FAILED' ? unknown : benefit, appliedRule,
    selectionBasis: selectionMode === 'FIXED_REPRESENTATIVE' ? null : decision, mappingNote: null,
    recalculation: {
      recommendationId,
      seenRecommendationId: attempt.body.seenRecommendationId ?? (changed === null ? null : 'rec-3001'),
      changed,
      differences: changed ? [{
        field: 'selected.currentOrderBenefit.amountWon', before: 400, after: benefit.amountWon,
        reason: 'MONTHLY_STATE_CHANGED',
      }] : [],
    },
    ledgerTransactionId: status === 'APPROVED' ? 'lt-' + sequence : null,
    cancellableAmountWon: status === 'APPROVED' ? order.amountWon : 0,
  } satisfies Approval

  const comparison = (card: typeof selected, amount: Amount, rule: Rule | null): ComparedCard => ({
    ...card, mapping: null,
    applicationStatus: amount.amountWon === null ? 'NEEDS_CONFIRMATION'
      : amount.amountWon === 0 ? 'NO_BENEFIT' : 'APPLICABLE',
    appliedRule: rule, currentOrderBenefit: amount, monthlyTotalBenefit: amount,
    services: rule ? [{
      serviceId: 'SVC-GREAT', applicationStatus: 'APPLIED_CAP_REMAINING',
      candidateWon: amount.amountWon, contribution: amount, adoption: 'ADOPTED',
      limits: [{ limitBucketId: 'LB-GREAT', parentLimitBucketId: null, limitWon: 10000,
        usedBeforeWon: { value: 1000, source: 'MANUAL' },
        remainingAfterWon: amount.amountWon === null ? null : 9000 - amount.amountWon }],
      limitReason: null, usageBasis: 'USER_ENTERED', mayDifferFromActual: true,
      usageNote: '입력한 사용액 기준, 미매입 전표에 따라 달라질 수 있음',
      fractionalWonTruncated: false, fractionalWonBasis: null, unevaluatedExclusions: [],
    }] : [],
    noBenefitReason: amount.amountWon === 0 ? 'NO_APPLICABLE_SERVICE' : null,
    policyNotes: [], pendingReasons: [],
  })
  const recommendation = {
    recommendationId, orderId: order.orderId, calculatedAt: stamp,
    decisionDate: order.orderDate, selectionMode, outcome: 'SELECTED', conditional,
    selected: { userCardId: selected.userCardId, cardProductId: selected.cardProductId,
      basis: { ...decision, gapToRunnerUpWon: decision.decidedBy ? 500 : null } },
    comparedCards: [comparison(selected, benefit, appliedRule),
      comparison(alternative, confirmed(benefit.amountWon === 700 ? 200 : 0), null)],
    excludedCards: conditional ? [{
      userCardId: 'uc-1099', cardProductId: null, reason: 'NEEDS_CONFIRMATION',
      detail: '전월 실적을 확인할 수 없어요.', candidateCardProductId: null,
      monthlyTotalBenefit: unknown, applicationStatus: null, appliedRule: null, pendingReasons: [],
    }] : [],
    fixedCardComparison: selectionMode === 'FIXED_REPRESENTATIVE'
      ? { representativeUserCardId: selected.userCardId, optimalUserCardId: null, differenceWon: null } : null,
    inputSnapshot: { order: { amountWon: order.amountWon, orderDate: order.orderDate,
      merchantClass: order.merchantClass, paymentMethod: order.paymentMethod },
    representativeUserCardId: selectionMode === 'FIXED_REPRESENTATIVE' ? selected.userCardId : null,
    monthlyStateSnapshotAt: {} },
    notes: ['개발용 합성 혜택 예시입니다.'],
  } satisfies Recommendation
  return { approval, recommendation }
}
