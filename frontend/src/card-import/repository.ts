import { cardDesigns } from '../data/portfolio'
import type { WalletCard } from '../data/portfolio'
import type { ConfirmedCard } from './contract'

type StoredCard = Pick<ConfirmedCard, 'userCardId' | 'displayName'> & { cardProductId: string }

function asWalletCard(item: StoredCard): WalletCard | undefined {
  const design = cardDesigns.find(card => card.cardProductId === item.cardProductId)
  if (!design) return
  return { ...design, id: item.userCardId, name: item.displayName, source: 'MOCK_IMPORTED',
    canBeRepresentative: true }
}

export function createImportedCardRepository(storage: Storage, profile: string) {
  const key = 'beone:dev:imported-cards:v1:' + profile
  function read(): StoredCard[] {
    const raw = storage.getItem(key)
    if (raw === null) return []
    const items: unknown = JSON.parse(raw)
    if (!Array.isArray(items)) throw new Error('INVALID_IMPORTED_CARDS')
    return items.filter(item => item && /^uc-\d+$/.test(item.userCardId) &&
      typeof item.displayName === 'string' && cardDesigns.some(card => card.cardProductId === item.cardProductId))
  }

  return {
    list() { return read().flatMap(item => asWalletCard(item) ?? []) },
    add(card: ConfirmedCard) {
      const cardProductId = card.mapping.cardProductId
      const items = read()
      if (items.some(item => item.cardProductId === cardProductId || item.userCardId === card.userCardId)) return
      const item = { userCardId: card.userCardId, displayName: card.displayName, cardProductId }
      if (card.mapping.status !== 'USER_CONFIRMED' || !asWalletCard(item)) throw new Error('INVALID_CONFIRMED_CARD')
      storage.setItem(key, JSON.stringify([item, ...items]))
    },
  }
}

export type ImportedCardRepository = ReturnType<typeof createImportedCardRepository>
