import { expect, it } from 'vitest'
import { defaultPreference } from './data/portfolio'
import { createLocalPreviewAdapters } from './localPreviewAdapters'
import type { Attempt } from './payment/contract'
import { sampleOrder } from './payment/fixtures'

it('shows the approved preview payment once in the order month and matching card history', async () => {
  const { benefits, paymentGateway } = createLocalPreviewAdapters()
  const signal = new AbortController().signal
  const mode = 'VIRTUAL_PORTFOLIO'
  const order = { ...sampleOrder, orderDate: '2026-10-16', merchantName: '테스트 가맹점' }
  const attempt: Attempt = {
    mode, selection: { order, source: 'mock' }, body: {}, key: 'preview-order',
    startedAt: Date.now(), preference: defaultPreference(), scenario: 'approved',
  }

  const approval = await paymentGateway.approve(attempt)
  await paymentGateway.approve(attempt)

  const ledger = await benefits.ledger(mode, '2026-10', signal, approval.userCardId)
  expect(ledger.items).toEqual([expect.objectContaining({
    simulatedPaymentId: approval.simulatedPaymentId, merchantName: order.merchantName,
    transactionDate: order.orderDate, amountWon: order.amountWon,
  })])
  expect((await benefits.ledger(mode, '2026-10', signal, 'uc-1004')).items).toHaveLength(0)
  expect((await benefits.ledger('MY_CARDS', '2026-10', signal)).items).toHaveLength(0)
  expect((await benefits.ledger(mode, '2026-09', signal)).items).toHaveLength(0)
  await expect(benefits.payment(mode, approval.simulatedPaymentId, signal)).resolves.toMatchObject(approval)
})
