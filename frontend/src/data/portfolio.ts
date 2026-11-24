export type PortfolioMode = 'MY_CARDS' | 'VIRTUAL_PORTFOLIO'
export type SelectionMode = 'AUTO_RECOMMEND' | 'FIXED_REPRESENTATIVE'

export interface WalletCard {
  id: string
  name: string
  type: string
  theme: string
  caption: string
  source: 'DESIGN_SAMPLE' | 'VIRTUAL' | 'MEMBER'
  canBeRepresentative: boolean
}

export interface PaymentPreference {
  selectionMode: SelectionMode
  representativeCardId: string | null
}

// API 연결 시 이 경계만 교체한다. 목 카드를 실제 등록·발급한 것으로 취급하지 않는다.
// load·register가 있으면 '내 카드'를 서버에서 불러오고 등록한다(src/cards/memberCardSource.ts)
export interface CardSource {
  getCards: (mode: PortfolioMode) => readonly WalletCard[]
  load?: (mode: PortfolioMode) => Promise<readonly WalletCard[]>
  register?: CardRegistrar
}

export interface CardRegistrar {
  products: () => Promise<ReadonlyArray<{ cardProductId: string; cardName: string }>>
  register: (input: { displayName: string; cardProductId?: string; notSupported?: true }) => Promise<void>
}

export const cardDesigns = [
  { name: '올라운드 티타늄카드', type: '신용', theme: 'navy', caption: 'ALLROUND TITANIUM' },
  { name: '청춘대로 톡톡카드', type: '신용', theme: 'mint-card', caption: 'YOUTH, EVERY DAY' },
  { name: '노리 체크카드', type: '체크', theme: 'silver', caption: 'ENJOY YOUR EVERYDAY' },
  { name: '첵첵 체크카드', type: '체크', theme: 'forest', caption: 'CHECK YOUR MOMENT' },
]
const mySamples: WalletCard[] = cardDesigns.map((card, index) => ({
  ...card,
  id: 'sample-' + index,
  source: 'DESIGN_SAMPLE',
  canBeRepresentative: true,
}))
const virtualCards: WalletCard[] = cardDesigns.map((card, index) => ({
  ...card,
  id: 'virtual-' + index,
  source: 'VIRTUAL',
  canBeRepresentative: true,
}))

export const mockCardSource: CardSource = {
  getCards: (mode) => (mode === 'MY_CARDS' ? mySamples : virtualCards),
}

export function defaultPreference(): PaymentPreference {
  return { selectionMode: 'AUTO_RECOMMEND', representativeCardId: null }
}
