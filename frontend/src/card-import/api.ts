import { createBenefitsApi } from '../benefits/api'
import { seoulToday } from '../card-services/api'
import { AuthApiError } from '../auth/api'
import { readHeldCards } from '../data/heldCards'
import { workspaceRequest } from '../data/workspaceRequest'
import type { WorkspaceContext } from '../data/workspaceRequest'
import type { CardImportAdapter, ConfirmedCard, ConnectionResponse, SyncResponse } from './contract'

export function createCardImportApi(context: WorkspaceContext): CardImportAdapter {
  async function request<T>(path: string, method: 'POST' | 'PATCH', signal: AbortSignal, input?: object): Promise<T> {
    const response = await workspaceRequest(context, 'MY_CARDS', path, {
      method, signal, headers: input ? { 'Content-Type': 'application/json' } : undefined,
      body: input ? JSON.stringify(input) : undefined,
    })
    const body = await response.json().catch(() => null)
    if (!response.ok) throw new AuthApiError(response.status, body, null)
    if (!body?.data) throw new Error('INVALID_CARD_IMPORT_RESPONSE')
    return body.data
  }

  return {
    products(signal) { return createBenefitsApi(context).products(seoulToday(), signal) },
    async registerManual(input, signal) {
      const result = await request<{ userCardId?: string }>('cards', 'POST', signal, input)
      if (!result.userCardId || !/^uc-[1-9]\d*$/.test(result.userCardId)) throw new Error('INVALID_CARD_RESPONSE')
    },
    connect(input, signal) { return request<ConnectionResponse>('codef/connection', 'POST', signal, input) },
    async sync(signal) {
      const data = await request<SyncResponse>('codef/syncs', 'POST', signal)
      if (data.heldCards.fetched === null) return data
      const cards = await readHeldCards(context, 'MY_CARDS', signal)
      return {
        ...data,
        heldCards: {
          ...data.heldCards,
          newCandidates: cards.filter(card => card.holdingSource === 'CODEF' &&
            (card.mapping?.status === 'CANDIDATE' || card.mapping?.status === 'UNMAPPED'))
            .map(card => ({ userCardId: card.userCardId, cardName: card.displayName,
              mappingStatus: card.mapping?.status === 'CANDIDATE' ? 'CANDIDATE' : 'UNMAPPED',
              candidateCardProductId: card.mapping?.candidateCardProductId ?? null })),
        },
      }
    },
    confirm(id, input, signal) {
      return request<ConfirmedCard>('cards/' + encodeURIComponent(id) + '/mapping', 'PATCH', signal, input)
    },
  }
}
