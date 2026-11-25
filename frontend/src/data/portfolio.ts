export type PortfolioMode = 'MY_CARDS' | 'VIRTUAL_PORTFOLIO'
export type SelectionMode = 'AUTO_RECOMMEND' | 'FIXED_REPRESENTATIVE'

export interface WalletCard {
  id: string
  cardProductId?: string
  name: string
  type: string
  theme: string
  caption: string
  source: 'DESIGN_SAMPLE' | 'VIRTUAL'
  canBeRepresentative: boolean
}

export interface PaymentPreference {
  selectionMode: SelectionMode
  representativeCardId: string | null
}

// API 연결 시 이 경계만 교체한다. 목 카드를 실제 등록·발급한 것으로 취급하지 않는다.
export interface CardSource {
  getCards: (mode: PortfolioMode) => readonly WalletCard[]
}

const designs = [
  { cardProductId: '09271', name: '올라운드 티타늄카드', type: '신용', theme: 'navy', caption: 'ALLROUND TITANIUM' },
  { cardProductId: '09174', name: '청춘대로 톡톡카드', type: '신용', theme: 'mint-card', caption: 'YOUTH, EVERY DAY' },
  { cardProductId: '01664', name: '노리 체크카드', type: '체크', theme: 'silver', caption: 'ENJOY YOUR EVERYDAY' },
  { cardProductId: '01914', name: '첵첵 체크카드', type: '체크', theme: 'forest', caption: 'CHECK YOUR MOMENT' },
]
const mySamples: WalletCard[] = designs.map((card, index) => ({
  ...card,
  id: 'sample-' + index,
  source: 'DESIGN_SAMPLE',
  canBeRepresentative: true,
}))
const virtualCards: WalletCard[] = designs.map((card, index) => ({
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
