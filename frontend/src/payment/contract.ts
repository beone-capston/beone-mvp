import type { PaymentPreference, PortfolioMode, SelectionMode } from '../data/portfolio'
import type { CreatedOrder } from '../orders/order'

export type Amount = { amountWon: number | null; status: 'CONFIRMED' | 'NEEDS_CONFIRMATION' }
export type Rule = {
  ruleVersion: string
  verificationStatus: string
  officialSources: { uri: string; locator: string }[]
}
export type Basis = { decidedBy: string | null; arbitraryTieBreak: boolean; tiedUserCardIds: string[] }
export type Difference = { field: string; before: string | number | null; after: string | number | null; reason: string }
export type Approval = {
  simulatedPaymentId: string
  orderId: string
  status: 'APPROVED' | 'FAILED'
  approvedAt: string
  amountWon: number
  selectionMode: SelectionMode
  userCardId: string
  cardProductId: string | null
  conditional: boolean
  currentOrderBenefit: Amount
  appliedRule: Rule | null
  selectionBasis: Basis | null
  mappingNote: string | null
  recalculation: { recommendationId: string; seenRecommendationId: string | null; changed: boolean | null; differences: Difference[] }
  ledgerTransactionId: string | null
  cancellableAmountWon: number
}
export type PaymentDetail = Omit<Approval, 'status'> & {
  status: Approval['status'] | 'PARTIALLY_CANCELLED' | 'CANCELLED'
  cancellations: { cancellationId: string; amountWon: number; cancelledAt: string; ledgerTransactionId: string }[]
}
type PendingReason = { kind: string; serviceIds: string[]; detail: string | null }
type Service = {
  serviceId: string; applicationStatus: string; candidateWon: number | null
  contribution: Amount; adoption: string
  limits: { limitBucketId: string; parentLimitBucketId: string | null; limitWon: number | null;
    usedBeforeWon: { value: number | null; source: string }; remainingAfterWon: number | null }[]
  limitReason: string | null; usageBasis: string | null; mayDifferFromActual: boolean; usageNote: string | null
  fractionalWonTruncated: boolean; fractionalWonBasis: { kind: string; decision: string } | null
  unevaluatedExclusions: string[]
}
export type ComparedCard = {
  userCardId: string; cardProductId: string; displayName: string
  mapping: { status: string; note: string | null } | null
  applicationStatus: string; appliedRule: Rule | null
  currentOrderBenefit: Amount; monthlyTotalBenefit: Amount; services: Service[]
  noBenefitReason: string | null; policyNotes: string[]; pendingReasons: PendingReason[]
}
export type Recommendation = {
  recommendationId: string; orderId: string; calculatedAt: string; decisionDate: string
  selectionMode: SelectionMode; outcome: 'SELECTED' | 'NOT_DETERMINED'; conditional: boolean
  selected: { userCardId: string; cardProductId: string; basis: Basis & { gapToRunnerUpWon: number | null } } | null
  comparedCards: ComparedCard[]
  excludedCards: { userCardId: string; cardProductId: string | null; reason: string; detail: string | null;
    candidateCardProductId: string | null; monthlyTotalBenefit: Amount; applicationStatus: string | null;
    appliedRule: Rule | null; pendingReasons: PendingReason[] }[]
  fixedCardComparison: { representativeUserCardId: string; optimalUserCardId: string | null; differenceWon: number | null } | null
  inputSnapshot: { order: Pick<CreatedOrder, 'amountWon' | 'orderDate' | 'merchantClass' | 'paymentMethod'>;
    representativeUserCardId: string | null; monthlyStateSnapshotAt: Record<string, string> } | null
  notes: string[]
}
export type OrderSelection = { order: CreatedOrder; source: 'server' | 'mock' }
export type ApprovalBody = { seenRecommendationId?: string; simulateFailure?: boolean }
export type Attempt = {
  mode: PortfolioMode; selection: OrderSelection; body: ApprovalBody
  key: string; startedAt: number; preference: PaymentPreference; scenario: Scenario
}

export type Scenario = 'approved' | 'conditional' | 'fixed' | 'unknown' | 'zero' | 'changed' |
  'unchanged' | 'failed' | 'undetermined' | 'ineligible' | 'paid' | 'missingOrder' |
  'missingRecommendation' | 'invalid' | 'server' | 'lost' | 'timeout' | 'comparisonError'

export const retryWindow = 24 * 60 * 60 * 1000

export class PaymentError extends Error {
  readonly code: string
  readonly status: number
  readonly uncertain: boolean

  constructor(code: string, status = 0, uncertain = false) {
    super(code)
    this.code = code
    this.status = status
    this.uncertain = uncertain
  }
}

export interface PaymentGateway {
  readonly kind: 'mock' | 'api'
  cancel?(mode: PortfolioMode, id: string, amountWon: number, key: string): Promise<void>
  approve(attempt: Attempt): Promise<Approval>
  recommendation(mode: PortfolioMode, id: string, orderId: string): Promise<Recommendation>
  detail(mode: PortfolioMode, id: string): Promise<PaymentDetail>
}
