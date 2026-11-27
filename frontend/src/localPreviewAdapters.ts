import { createMockCardImport } from './card-import/mockAdapter'
import { createImportedCardRepository } from './card-import/repository'
import { mockBenefitsApi } from './benefits/fixtures'
import type { BenefitsApi, LedgerRow } from './benefits/model'
import type { CardSource, PortfolioMode } from './data/portfolio'
import { createMockOrderApi } from './orders/mockOrderApi'
import { createMockPaymentGateway } from './payment/mockPayment'
import { createMockPreferenceApi, preferenceMockCardSource } from './preferences/mockPreference'

const sampleCardIds: Record<string, string> = { 'uc-1001': 'uc-1002', 'uc-1002': 'uc-1003' }

export function createLocalPreviewAdapters() {
  const storage = window.localStorage
  const profile = 'local-preview'
  const repository = createImportedCardRepository(storage, profile)
  const cards: CardSource = {
    getCards: mode => mode === 'MY_CARDS' ? repository.list() : preferenceMockCardSource.getCards(mode),
  }
  const entries: Record<PortfolioMode, LedgerRow[]> = { MY_CARDS: [], VIRTUAL_PORTFOLIO: [] }
  const paymentGateway = createMockPaymentGateway(500, Date.now, true, cards, ({ mode, selection }, approval) => {
    if (!approval.ledgerTransactionId) return
    const order = selection.order
    entries[mode].unshift({
      ledgerTransactionId: approval.ledgerTransactionId,
      userCardId: approval.userCardId,
      source: 'SIMULATION',
      entryType: 'APPROVAL',
      amountWon: approval.amountWon,
      transactionDate: order.orderDate,
      transactionTime: order.orderTime,
      merchantClass: order.merchantClass,
      merchantName: order.merchantName,
      paymentMethod: order.paymentMethod,
      originalTransactionId: null,
      simulatedPaymentId: approval.simulatedPaymentId,
      attributionMonth: { value: order.orderDate.slice(0, 7), status: 'CONFIRMED' },
    })
  })
  const benefits: BenefitsApi = {
    ...mockBenefitsApi,
    async cards(mode) {
      return cards.getCards(mode).map(card => ({
        userCardId: card.id,
        displayName: card.name,
        holdingSource: mode === 'MY_CARDS' ? 'MANUAL' : 'SYNTHETIC',
        cardProductId: card.cardProductId,
        mapping: null,
      }))
    },
    async ledger(mode, month, signal, card) {
      const fixture = await mockBenefitsApi.ledger(mode, month, signal)
      const sampleItems = fixture.items.map(row => ({
        ...row, userCardId: sampleCardIds[row.userCardId] ?? row.userCardId,
      }))
      const approvals = entries[mode].filter(row => row.transactionDate.startsWith(month + '-'))
      return { ...fixture, items: [...approvals, ...sampleItems].filter(row => !card || row.userCardId === card) }
    },
    async payment(mode, id, signal) {
      if (entries[mode].some(row => row.simulatedPaymentId === id)) return paymentGateway.detail(mode, id)
      const detail = await mockBenefitsApi.payment(mode, id, signal)
      return { ...detail, userCardId: sampleCardIds[detail.userCardId] ?? detail.userCardId }
    },
    async monthly(mode, id, month, signal) {
      const fixtureId = mode === 'VIRTUAL_PORTFOLIO'
        ? Object.keys(sampleCardIds).find(key => sampleCardIds[key] === id) ?? '' : id
      const monthly = await mockBenefitsApi.monthly(mode, fixtureId, month, signal)
      return { ...monthly, userCardId: id }
    },
  }
  return {
    cardImporter: { adapter: createMockCardImport(), repository },
    cardSource: preferenceMockCardSource,
    orders: createMockOrderApi(),
    paymentGateway,
    preferenceApi: createMockPreferenceApi(storage, profile, undefined, cards),
    benefits,
    mockAuth: true,
    mockOrders: true,
  }
}
