import type { PortfolioMode } from '../data/portfolio'
import { PaymentError } from './contract'
import type { Approval, PaymentDetail, PaymentGateway, Recommendation } from './contract'

const base = (import.meta.env.VITE_API_BASE_URL ??
  (import.meta.env.DEV ? 'http://localhost:8080' : '')).replace(/\/+$/, '')

const record = (value: unknown): value is Record<string, unknown> =>
  !!value && typeof value === 'object' && !Array.isArray(value)
const string = (v: unknown): v is string => typeof v === 'string'
const nullableString = (v: unknown) => v === null || string(v)
const boolean = (v: unknown) => typeof v === 'boolean'
const won = (v: unknown) => typeof v === 'number' && Number.isSafeInteger(v) && v >= 0
const nullableWon = (v: unknown) => v === null || won(v)
const list = (v: unknown, check: (entry: unknown) => unknown) => Array.isArray(v) && v.every(check)
const selectionMode = (v: unknown) => v === 'AUTO_RECOMMEND' || v === 'FIXED_REPRESENTATIVE'
const amount = (v: unknown) => record(v) && (v.status === 'CONFIRMED' ? won(v.amountWon) :
  v.status === 'NEEDS_CONFIRMATION' && v.amountWon === null)
const rule = (v: unknown) => v === null || (record(v) && string(v.ruleVersion) && string(v.verificationStatus) &&
  list(v.officialSources, src => record(src) && string(src.uri) && string(src.locator)))
const basis = (v: unknown) => record(v) && nullableString(v.decidedBy) &&
  boolean(v.arbitraryTieBreak) && list(v.tiedUserCardIds, string)
const diffValue = (v: unknown) => nullableString(v) || won(v)
const recalculation = (v: unknown) => record(v) && string(v.recommendationId) &&
  nullableString(v.seenRecommendationId) && (v.changed === null || boolean(v.changed)) &&
  list(v.differences, item => record(item) && string(item.field) && diffValue(item.before) &&
    diffValue(item.after) && string(item.reason))

function approval(v: unknown, statuses: string[]) {
  return record(v) && string(v.status) && statuses.includes(v.status) &&
    string(v.orderId) && string(v.simulatedPaymentId) && string(v.approvedAt) &&
    won(v.amountWon) && selectionMode(v.selectionMode) && string(v.userCardId) &&
    nullableString(v.cardProductId) && boolean(v.conditional) && amount(v.currentOrderBenefit) &&
    rule(v.appliedRule) && (v.selectionBasis === null || basis(v.selectionBasis)) && nullableString(v.mappingNote) &&
    recalculation(v.recalculation) && nullableString(v.ledgerTransactionId) && won(v.cancellableAmountWon)
}
const limit = (v: unknown) => record(v) && string(v.limitBucketId) &&
  nullableWon(v.limitWon) && nullableWon(v.remainingAfterWon) &&
  record(v.usedBeforeWon) && nullableWon(v.usedBeforeWon.value) && string(v.usedBeforeWon.source)
const service = (v: unknown) => record(v) && string(v.serviceId) && string(v.applicationStatus) &&
  amount(v.contribution) && list(v.limits, limit) && nullableString(v.usageNote)

function recommendation(v: unknown): v is Recommendation {
  return record(v) && string(v.recommendationId) && string(v.orderId) && selectionMode(v.selectionMode) &&
    (v.outcome === 'SELECTED' || v.outcome === 'NOT_DETERMINED') && boolean(v.conditional) &&
    list(v.comparedCards, c => record(c) && string(c.userCardId) && string(c.displayName) &&
      amount(c.currentOrderBenefit) && rule(c.appliedRule) && list(c.services, service)) &&
    list(v.excludedCards, c => record(c) && string(c.userCardId) && string(c.reason) && nullableString(c.detail)) &&
    list(v.notes, string)
}

async function read(mode: PortfolioMode, path: string, post?: { key: string; body: object }): Promise<unknown> {
  const headers: Record<string, string> = { 'X-BeONE-Demo-User': mode }
  if (post) {
    headers['Content-Type'] = 'application/json'
    headers['Idempotency-Key'] = post.key
  }
  let response: Response
  try {
    response = await fetch(base + '/api/v1/me/' + path, {
      method: post ? 'POST' : 'GET', headers, credentials: 'omit', cache: 'no-store',
      body: post ? JSON.stringify(post.body) : undefined, signal: AbortSignal.timeout(15000),
    })
  } catch {
    throw new PaymentError('NETWORK', 0, Boolean(post))
  }
  const body: unknown = await response.json().catch(() => null)
  if (!response.ok) {
    const code = record(body) && string(body.code) ? body.code : 'UNEXPECTED'
    throw new PaymentError(code, response.status, Boolean(post) && response.status >= 500)
  }
  if (response.status !== (post ? 201 : 200) || !record(body) || !('data' in body)) {
    throw new PaymentError('UNEXPECTED', response.status, Boolean(post))
  }
  return body.data
}

export const apiPaymentGateway: PaymentGateway = {
  kind: 'api',
  async approve(attempt) {
    const { selection, mode, key, body } = attempt
    if (selection.source !== 'server') throw new PaymentError('MOCK_ORDER')
    const id = selection.order.orderId
    const data = await read(mode, 'orders/' + encodeURIComponent(id) + '/simulated-payments', { key, body })
    const result = data as Approval
    if (!approval(data, ['APPROVED', 'FAILED']) || result.orderId !== id ||
      result.amountWon !== selection.order.amountWon || (result.status === 'FAILED' &&
        (result.ledgerTransactionId !== null || result.currentOrderBenefit.amountWon !== null ||
          result.cancellableAmountWon !== 0))) {
      throw new PaymentError('UNEXPECTED', 201, true)
    }
    return result
  },
  async recommendation(mode, id, orderId) {
    const data = await read(mode, 'recommendations/' + encodeURIComponent(id))
    if (!recommendation(data) || data.recommendationId !== id || data.orderId !== orderId) {
      throw new PaymentError('UNEXPECTED')
    }
    return data
  },
  async detail(mode, id) {
    const data = await read(mode, 'simulated-payments/' + encodeURIComponent(id)) as PaymentDetail
    if (!approval(data, ['APPROVED', 'FAILED', 'PARTIALLY_CANCELLED', 'CANCELLED']) ||
      data.simulatedPaymentId !== id || !list(data.cancellations, item => record(item) &&
      string(item.cancellationId) && won(item.amountWon) && string(item.cancelledAt) && string(item.ledgerTransactionId))) {
      throw new PaymentError('UNEXPECTED')
    }
    return data
  },
}
