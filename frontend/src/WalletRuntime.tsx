import { useContext, useState } from 'react'
import App from './App'
import { MemberIdentity } from './auth/memberIdentity'
import { workspaceContext } from './data/workspaceRequest'
import { createMockOrderApi } from './orders/mockOrderApi'
import { createOrderApi } from './orders/orderApi'
import { createMockPaymentGateway } from './payment/mockPayment'
import { createPaymentGateway } from './payment/paymentApi'
import { createMockPreferenceApi, preferenceMockCardSource } from './preferences/mockPreference'
import { createPreferenceApi } from './preferences/preferenceApi'

const preview = import.meta.env.DEV && import.meta.env.VITE_PAYMENT_PREFERENCE_MODE === 'mock'
const mock = preview || (import.meta.env.DEV && import.meta.env.VITE_PAYMENT_API_MODE === 'mock')

function Wallet() {
  const [adapters] = useState(() => {
    if (!mock) return {
      orders: createOrderApi(workspaceContext), paymentGateway: createPaymentGateway(workspaceContext),
      preferenceApi: createPreferenceApi(workspaceContext),
    }
    const failure = import.meta.env.VITE_PAYMENT_PREFERENCE_MOCK_FAILURE
    return {
      cardSource: preferenceMockCardSource,
      orders: createMockOrderApi(),
      paymentGateway: createMockPaymentGateway(
        Number(import.meta.env.VITE_PAYMENT_MOCK_DELAY_MS ?? 800) || 0, Date.now, true,
      ),
      preferenceApi: createMockPreferenceApi(
        window.localStorage, import.meta.env.VITE_PAYMENT_MOCK_PROFILE || 'fixture',
        failure === 'read' || failure === 'save' ? failure : undefined,
      ),
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
