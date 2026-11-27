import { useContext, useState } from 'react'
import App from './App'
import { LocalPreview, MemberIdentity } from './auth/memberIdentity'
import { createCardImportApi } from './card-import/api'
import { workspaceContext } from './data/workspaceRequest'
import { mockCardSource } from './data/portfolio'
import type { CardSource } from './data/portfolio'
import { benefitsApi } from './benefits/config'
import { mockBenefitsApi } from './benefits/fixtures'
import type { BenefitsApi } from './benefits/model'
import { createOrderApi } from './orders/orderApi'
import { createPaymentGateway } from './payment/paymentApi'
import { createLocalPreviewAdapters } from './localPreviewAdapters'
import { createPreferenceApi } from './preferences/preferenceApi'

const memberCardSource: CardSource = {
  getCards: mode => mode === 'MY_CARDS' ? [] : mockCardSource.getCards(mode),
}

const virtualCardIds: Record<string, string> = { 'uc-1001': 'virtual-1', 'uc-1002': 'virtual-2' }
const memberVirtualBenefits: BenefitsApi = {
  ...benefitsApi,
  async cards(mode) {
    return memberCardSource.getCards(mode).map(card => ({
      userCardId: card.id, displayName: card.name,
      holdingSource: 'SYNTHETIC', cardProductId: card.cardProductId, mapping: null,
    }))
  },
  async monthly(mode, id, month, signal) {
    const fixtureId = Object.keys(virtualCardIds).find(key => virtualCardIds[key] === id) ?? id
    const monthly = await mockBenefitsApi.monthly(mode, fixtureId, month, signal)
    return { ...monthly, userCardId: id }
  },
  async ledger(mode, month, signal, card) {
    const ledger = await mockBenefitsApi.ledger(mode, month, signal)
    const items = ledger.items.map(row => ({ ...row, userCardId: virtualCardIds[row.userCardId] ?? row.userCardId }))
    return { ...ledger, items: items.filter(row => !card || row.userCardId === card) }
  },
  async payment(mode, id, signal) {
    const detail = await mockBenefitsApi.payment(mode, id, signal)
    return { ...detail, userCardId: virtualCardIds[detail.userCardId] ?? detail.userCardId }
  },
}

function Wallet({ preview }: { preview: boolean }) {
  const [adapters] = useState(() => import.meta.env.DEV && preview ? createLocalPreviewAdapters() : ({
    cardSource: memberCardSource,
    virtualBenefits: workspaceContext === 'member' ? memberVirtualBenefits : undefined,
    orders: createOrderApi(workspaceContext),
    paymentGateway: createPaymentGateway(workspaceContext),
    preferenceApi: createPreferenceApi(workspaceContext),
    cardImporter: { adapter: createCardImportApi(workspaceContext) },
  }))
  return <App {...adapters} />
}

export function WalletRuntime() {
  const memberId = useContext(MemberIdentity)
  const preview = useContext(LocalPreview)
  return <Wallet key={memberId} preview={preview} />
}
