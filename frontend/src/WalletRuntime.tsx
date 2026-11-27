import { useContext, useState } from 'react'
import App from './App'
import { createMockCardImport } from './card-import/mockAdapter'
import { createImportedCardRepository } from './card-import/repository'
import { MemberIdentity } from './auth/memberIdentity'
import { mockCardSource } from './data/portfolio'
import type { CardSource } from './data/portfolio'
import { mockWorkspace, workspaceContext } from './data/workspaceRequest'
import { createMockOrderApi } from './orders/mockOrderApi'
import { createOrderApi } from './orders/orderApi'
import { createMockPaymentGateway } from './payment/mockPayment'
import { createPaymentGateway } from './payment/paymentApi'
import { createMockPreferenceApi, preferenceMockCardSource } from './preferences/mockPreference'
import { createPreferenceApi } from './preferences/preferenceApi'

const preview = import.meta.env.DEV && (import.meta.env.VITE_PAYMENT_PREFERENCE_MODE === 'mock' || import.meta.env.VITE_ADD_CARD_MODE === 'mock')

const walletCardSource: CardSource = {
  getCards: mode => mode === 'MY_CARDS' ? [] : mockCardSource.getCards(mode),
}

function Wallet() {
  const [adapters] = useState(() => {
    if (!mockWorkspace) return {
      cardSource: walletCardSource,
      orders: createOrderApi(workspaceContext), paymentGateway: createPaymentGateway(workspaceContext),
      preferenceApi: createPreferenceApi(workspaceContext),
    }
    const profile = import.meta.env.VITE_PAYMENT_MOCK_PROFILE || 'fixture'
    const repository = createImportedCardRepository(window.localStorage, profile)
    const registeredCardSource: CardSource = {
      getCards: mode => mode === 'MY_CARDS' ? repository.list() : preferenceMockCardSource.getCards(mode),
    }
    const failure = import.meta.env.VITE_PAYMENT_PREFERENCE_MOCK_FAILURE
    return {
      cardSource: preferenceMockCardSource,
      orders: createMockOrderApi(),
      paymentGateway: createMockPaymentGateway(
        Number(import.meta.env.VITE_PAYMENT_MOCK_DELAY_MS ?? 800) || 0, Date.now, true, registeredCardSource,
      ),
      preferenceApi: createMockPreferenceApi(
        window.localStorage, profile,
        failure === 'read' || failure === 'save' ? failure : undefined, registeredCardSource,
      ),
      cardImporter: {
        adapter: createMockCardImport(import.meta.env.VITE_ADD_CARD_SCENARIO),
        repository,
      },
      mockOrders: true,
      mockAuth: preview ? true : undefined,
    }
  })
  return <App {...adapters} />
}

export function WalletRuntime() {
  const memberId = useContext(MemberIdentity)
  return <Wallet key={workspaceContext === 'member' ? memberId : 'demo'} />
}
