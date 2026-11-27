import { AuthApiError } from '../auth/api'
import { cardDesigns } from './portfolio'
import type { PortfolioMode, WalletCard } from './portfolio'
import { workspaceRequest } from './workspaceRequest'
import type { WorkspaceContext } from './workspaceRequest'

export type HeldCard = {
  userCardId: string
  displayName: string
  holdingSource: string
  cardProductId?: string
  mapping: { status: string; cardProductId: string | null; candidateCardProductId: string | null } | null
  recommendationEligibility: { eligible: boolean }
  excludedFromRecommendation: boolean
}

export async function readHeldCards(context: WorkspaceContext, mode: PortfolioMode, signal?: AbortSignal) {
  const response = await workspaceRequest(context, mode, 'cards', { signal })
  const payload = await response.json().catch(() => null)
  if (!response.ok) throw new AuthApiError(response.status, payload, null)
  const items: unknown = payload?.data?.items
  if (!Array.isArray(items)) throw new Error('INVALID_CARDS_RESPONSE')
  const cards: HeldCard[] = []
  for (const card of items) {
    if (!card || typeof card.userCardId !== 'string' || !/^uc-[1-9]\d{0,18}$/.test(card.userCardId) ||
      typeof card.displayName !== 'string' || typeof card.holdingSource !== 'string' ||
      typeof card.excludedFromRecommendation !== 'boolean' ||
      typeof card.recommendationEligibility?.eligible !== 'boolean') throw new Error('INVALID_CARDS_RESPONSE')
    cards.push(card)
  }
  return cards
}

export function toWalletCard(card: HeldCard): WalletCard {
  const synthetic = card.holdingSource === 'SYNTHETIC'
  const product = synthetic ? card.cardProductId
    : card.mapping?.status === 'USER_CONFIRMED' ? card.mapping.cardProductId : null
  const design = cardDesigns.find(value => value.cardProductId === product)
  return {
    id: card.userCardId, name: card.displayName, cardProductId: product ?? undefined,
    source: 'SERVER', theme: design?.theme ?? 'navy', type: design?.type ?? '', caption: design?.caption ?? '',
    canBeRepresentative: Boolean(product && card.recommendationEligibility.eligible && !card.excludedFromRecommendation),
  }
}
