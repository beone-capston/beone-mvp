import { PaymentError, retryWindow } from './contract'
import type { Approval, PaymentGateway, Recommendation, Scenario } from './contract'
import { preferenceMockCardSource } from '../preferences/mockPreference'
import type { CardSource } from '../data/portfolio'
import { makeFixtures } from './fixtures'

const errors: Partial<Record<Scenario, [number, string]>> = {
  undetermined: [409, 'RECOMMENDATION_NOT_DETERMINED'],
  ineligible: [409, 'REPRESENTATIVE_CARD_NOT_ELIGIBLE'],
  paid: [409, 'ORDER_ALREADY_PAID'],
  missingOrder: [404, 'ORDER_NOT_FOUND'],
  missingRecommendation: [404, 'RECOMMENDATION_NOT_FOUND'],
  invalid: [400, 'INVALID_REQUEST'],
  server: [500, 'INTERNAL_SERVER_ERROR'],
}

export function createMockPaymentGateway(delayMs = 800, now = Date.now, strictFixtures = false, cardSource?: CardSource): PaymentGateway {
  const cache = new Map<string, { signature: string; until: number; approval: Approval }>()
  const recommendations = new Map<string, { data: Recommendation; fail: boolean }>()
  const payments = new Map<string, Approval>()
  const approvedOrders = new Set<string>()
  let sequence = 7001

  return {
    kind: 'mock',
    async approve(attempt) {
      if (delayMs > 0) await new Promise(resolve => setTimeout(resolve, delayMs))
      const { mode, key, selection, body, scenario } = attempt
      const cacheKey = mode + ':' + key
      const orderKey = mode + ':' + selection.source + ':' + selection.order.orderId
      const signature = JSON.stringify([selection.source, selection.order.orderId, body.seenRecommendationId ?? null, body.simulateFailure ?? false])
      const saved = cache.get(cacheKey)
      if (saved && saved.until > now()) {
        if (saved.signature !== signature) throw new PaymentError('IDEMPOTENCY_KEY_REUSED', 409)
        return structuredClone(saved.approval)
      }
      if (saved) cache.delete(cacheKey)
      if (!key.trim() || key.length > 255) throw new PaymentError('INVALID_REQUEST', 400)
      if (approvedOrders.has(orderKey) || selection.order.status === 'PAID') throw new PaymentError('ORDER_ALREADY_PAID', 409)
      if (selection.order.status === 'CANCELLED') throw new PaymentError('ORDER_ALREADY_PAID', 409)
      const error = errors[scenario]
      if (error) throw new PaymentError(error[1], error[0], error[0] >= 500)
      if (scenario === 'timeout') throw new PaymentError('TIMEOUT', 0, true)

      if (strictFixtures && selection.source !== 'mock') throw new PaymentError('INVALID_REQUEST', 400)
      const eligible = (cardSource ?? preferenceMockCardSource).getCards(mode).filter(card => card.canBeRepresentative)
      if (cardSource && !eligible.length && attempt.preference.selectionMode === 'AUTO_RECOMMEND') {
        throw new PaymentError('RECOMMENDATION_NOT_DETERMINED', 409)
      }
      if (strictFixtures && attempt.preference.selectionMode === 'FIXED_REPRESENTATIVE' &&
        !eligible.some(card =>
          card.id === attempt.preference.representativeCardId && card.canBeRepresentative)) {
        throw new PaymentError('REPRESENTATIVE_CARD_NOT_ELIGIBLE', 409)
      }
      const { approval, recommendation } = makeFixtures(attempt, sequence++, cardSource ? eligible : undefined)
      cache.set(cacheKey, { signature, until: now() + retryWindow, approval })
      recommendations.set(mode + ':' + recommendation.recommendationId,
        { data: recommendation, fail: scenario === 'comparisonError' })
      payments.set(mode + ':' + approval.simulatedPaymentId, approval)
      if (approval.status === 'APPROVED') approvedOrders.add(orderKey)
      if (scenario === 'lost') throw new PaymentError('NETWORK', 0, true)
      return structuredClone(approval)
    },
    async recommendation(mode, id, orderId) {
      const entry = recommendations.get(mode + ':' + id)
      if (!entry || entry.data.orderId !== orderId) throw new PaymentError('RECOMMENDATION_NOT_FOUND', 404)
      if (entry.fail) throw new PaymentError('INTERNAL_SERVER_ERROR', 500)
      return structuredClone(entry.data)
    },
    async detail(mode, id) {
      const entry = payments.get(mode + ':' + id)
      if (!entry) throw new PaymentError('SIMULATED_PAYMENT_NOT_FOUND', 404)
      return { ...structuredClone(entry), cancellations: [] }
    },
  }
}
