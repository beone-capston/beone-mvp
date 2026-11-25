import type { PortfolioMode } from '../data/portfolio'
import type { PaymentDetail } from '../payment/contract'
import type { BenefitsApi, HeldCard, LedgerRow, Product, StateValue } from './model'
import { monthPeriod, shiftMonth } from './model'

const fixtureCards: Record<PortfolioMode, HeldCard[]> = {
  VIRTUAL_PORTFOLIO: [
    { userCardId: 'uc-1001', displayName: '청춘대로 톡톡카드', holdingSource: 'SYNTHETIC', cardProductId: '09174', mapping: null },
    { userCardId: 'uc-1002', displayName: '노리 체크카드', holdingSource: 'SYNTHETIC', cardProductId: '01664', mapping: null },
  ],
  MY_CARDS: [{ userCardId: 'uc-2001', displayName: '청춘대로 톡톡카드 · 내 카드', holdingSource: 'MANUAL',
    mapping: { status: 'USER_CONFIRMED', cardProductId: '09174' } }],
}

const products = [
  { cardProductId: '09174', cardName: '청춘대로 톡톡카드', ruleStatus: 'VERIFIED' },
  { cardProductId: '01664', cardName: '노리 체크카드', ruleStatus: 'NO_VERIFIED_RULE' },
]

const state = <T,>(value: T | null, month: string): StateValue<T> => ({
  value, source: value === null ? 'UNKNOWN' : 'MANUAL', period: value === null ? null : monthPeriod(month),
  usable: value !== null, updatedAt: value === null ? null : month + '-04T10:00:00+09:00',
  reason: value === null ? 'NOT_ENTERED' : null,
})

const buckets = ['GREAT', 'ENJOY', 'CHECK', 'BASIC']
const ledgerRows: LedgerRow[] = [
  { ledgerTransactionId: 'lt-701', userCardId: 'uc-1002', source: 'SIMULATION', entryType: 'APPROVAL',
    amountWon: 10000, transactionDate: '2026-11-03', transactionTime: '10:30:00', merchantClass: 'STARBUCKS_STORE',
    merchantName: '스타벅스 송도점', paymentMethod: 'DIRECT', originalTransactionId: null,
    simulatedPaymentId: 'pay-701', attributionMonth: { value: '2026-11', status: 'CONFIRMED' } },
  { ledgerTransactionId: 'lt-702', userCardId: 'uc-1002', source: 'SIMULATION', entryType: 'CANCELLATION',
    amountWon: -7000, transactionDate: '2026-11-04', transactionTime: null, merchantClass: 'STARBUCKS_STORE',
    merchantName: '스타벅스 송도점', paymentMethod: 'DIRECT', originalTransactionId: 'lt-701',
    simulatedPaymentId: 'pay-701', attributionMonth: { value: '2026-11', status: 'CONFIRMED' } },
  { ledgerTransactionId: 'lt-703', userCardId: 'uc-1001', source: 'SIMULATION', entryType: 'APPROVAL',
    amountWon: 7500, transactionDate: '2026-11-03', transactionTime: null, merchantClass: 'CU_STORE',
    merchantName: 'CU 인천대점', paymentMethod: 'DIRECT', originalTransactionId: null,
    simulatedPaymentId: 'pay-703', attributionMonth: { value: '2026-11', status: 'CONFIRMED' } },
]
const details: Record<string, PaymentDetail> = Object.fromEntries(ledgerRows.filter(row => row.entryType === 'APPROVAL').map(row => {
  const cancelled = row.simulatedPaymentId === 'pay-701'
  return [row.simulatedPaymentId!, {
    simulatedPaymentId: row.simulatedPaymentId!, orderId: 'ord-' + row.ledgerTransactionId.slice(3),
    status: cancelled ? 'PARTIALLY_CANCELLED' : 'APPROVED', approvedAt: '2026-11-03T10:30:00+09:00',
    amountWon: row.amountWon, selectionMode: 'AUTO_RECOMMEND', userCardId: row.userCardId,
    cardProductId: cancelled ? '01664' : '09174', conditional: false,
    currentOrderBenefit: cancelled ? { amountWon: null, status: 'NEEDS_CONFIRMATION' } : { amountWon: 0, status: 'CONFIRMED' },
    appliedRule: null, selectionBasis: null, mappingNote: null, ledgerTransactionId: row.ledgerTransactionId,
    recalculation: { recommendationId: 'rec-' + row.ledgerTransactionId.slice(3),
      seenRecommendationId: null, changed: null, differences: [] },
    cancellableAmountWon: cancelled ? 3000 : row.amountWon,
    cancellations: cancelled ? [{ cancellationId: 'cn-702', amountWon: 7000,
      cancelledAt: '2026-11-04T10:00:00+09:00', ledgerTransactionId: 'lt-702' }] : [],
  } satisfies PaymentDetail]
}))

export const mockBenefitsApi: BenefitsApi = {
  context: 'mock',
  async cards(mode) { return structuredClone(fixtureCards[mode]) },
  async products() { return structuredClone(products) },
  async product(id, asOf) {
    const summary = products.find(product => product.cardProductId === id)
    if (!summary) throw new Error('카드 상품을 찾을 수 없어요.')
    const verified = id === '09174'
    return {
      ...summary, asOf,
      rule: verified ? { ruleVersion: '09174-fixture', verificationStatus: 'VERIFIED',
        validFrom: '2026-01-01', validTo: null, performanceWindow: 'PREVIOUS_CALENDAR_MONTH' } : null,
      services: verified ? buckets.map(serviceId => ({ serviceId, name: serviceId, benefitKind: 'BILLING_DISCOUNT',
        supportStatus: 'SUPPORTED_SINGLE_MONTHLY_LIMIT', limitBucketIds: [serviceId], requiresSelection: false })) : [],
      limitBuckets: verified ? buckets.map((limitBucketId, index) => ({
        limitBucketId, period: 'CALENDAR_MONTH', parentLimitBucketId: null, sharedByServiceIds: [limitBucketId],
        tiers: [{ minimumPerformanceWon: 300000, monthlyCapWon: index === 0 ? 10000 : 5000 }],
      })) : [],
    } satisfies Product
  },
  async monthly(mode, id, month) {
    const hasSample = month === '2026-11' && id === fixtureCards[mode][0].userCardId
    return {
      month, userCardId: id, snapshotAt: hasSample ? '2026-11-04T10:00:00+09:00' : null,
      previousMonthPerformanceWon: state(hasSample ? (mode === 'MY_CARDS' ? -5000 : 523000) : null, shiftMonth(month, -1)),
      limitBucketUsages: hasSample ? [
        { limitBucketId: 'GREAT', ...state(mode === 'MY_CARDS' ? 0 : 4000, month) },
        { limitBucketId: 'CHECK', ...state(0, month) },
      ] : [],
      otherBucketsUsedWon: state<number>(null, month), registeredOn: state<string>(null, month),
      selectedServices: [], serviceSelectionStatus: 'UNKNOWN', noServiceSelection: null, afterSnapshot: null,
    }
  },
  async ledger(mode, month, _signal, card) {
    return { month, items: structuredClone(mode === 'VIRTUAL_PORTFOLIO' && month === '2026-11'
      ? ledgerRows.filter(row => !card || row.userCardId === card) : []), coverage: [] }
  },
  async payment(mode, id) {
    if (mode !== 'VIRTUAL_PORTFOLIO' || !details[id]) throw new Error('모의 결제 상세를 찾을 수 없어요.')
    return structuredClone(details[id])
  },
}
