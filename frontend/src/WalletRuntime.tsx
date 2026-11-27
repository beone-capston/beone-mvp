import { useContext, useState } from 'react'
import App from './App'
import { LocalPreview, MemberIdentity } from './auth/memberIdentity'
import { createCardImportApi } from './card-import/api'
import { workspaceContext } from './data/workspaceRequest'
import type { CardSource } from './data/portfolio'
import { createOrderApi } from './orders/orderApi'
import { createPaymentGateway } from './payment/paymentApi'
import { createLocalPreviewAdapters } from './localPreviewAdapters'
import { createPreferenceApi } from './preferences/preferenceApi'

const emptyCardSource: CardSource = { getCards: () => [] }

function Wallet({ preview }: { preview: boolean }) {
  const [adapters] = useState(() => import.meta.env.DEV && preview ? createLocalPreviewAdapters() : ({
    cardSource: emptyCardSource,
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
