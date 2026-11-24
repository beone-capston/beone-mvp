// '내 카드'를 서버 보유 카드로 채우는 카드 소스. 가상 포트폴리오는 기존 시연용 카드를 그대로 쓴다
import { cardDesigns, mockCardSource } from '../data/portfolio'
import type { CardSource, WalletCard } from '../data/portfolio'
import { cardApi } from './cardApi'
import type { CardApi, CardProduct, UserCard } from './cardApi'

const cardTypes: Record<string, string> = { CREDIT: '신용', CHECK: '체크' }

// 상품명이 기존 디자인과 같으면 그 디자인을 쓰고, 아니면 첫 디자인을 쓴다(새 디자인을 만들지 않음)
export function toWalletCard(card: UserCard, products: readonly CardProduct[]): WalletCard {
  const product = products.find(item => item.cardProductId === card.cardProductId)
  const design = cardDesigns.find(item => item.name === product?.cardName) ?? cardDesigns[0]
  return {
    id: card.userCardId,
    name: card.displayName,
    type: product ? cardTypes[product.cardType] ?? '' : '',
    theme: design.theme,
    caption: product ? design.caption : '',
    source: 'MEMBER',
    canBeRepresentative: card.eligible,
  }
}

export function createMemberCardSource(api: CardApi = cardApi): CardSource {
  let products: Promise<CardProduct[]> | null = null
  const loadProducts = () => {
    products ??= api.products().catch(error => {
      products = null
      throw error
    })
    return products
  }
  return {
    getCards: mode => (mode === 'MY_CARDS' ? [] : mockCardSource.getCards(mode)),
    async load(mode) {
      if (mode !== 'MY_CARDS') return mockCardSource.getCards(mode)
      const [cards, catalog] = await Promise.all([api.list(), loadProducts()])
      return cards.map(card => toWalletCard(card, catalog))
    },
    register: {
      products: loadProducts,
      async register(input) {
        await api.register(input)
      },
    },
  }
}

export const memberCardSource = createMemberCardSource()
