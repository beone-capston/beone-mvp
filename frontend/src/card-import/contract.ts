export type ConnectionRequest = {
  organization: '0301'; loginType: 'ID'; loginId: string; password: string
}
export type ConnectionResponse = {
  organization: '0301'; status: 'CONNECTED' | 'NOT_CONNECTED' | 'RECONNECT_REQUIRED'
  connectedAt: string | null; lastSyncedAt: string | null; reconnectRequired: boolean
}
export type Candidate = {
  userCardId: string
  cardName: string
  candidateCardProductId: string | null
  mappingStatus: 'CANDIDATE' | 'UNMAPPED'
}
export type SyncResponse = {
  syncId: string; syncedAt: string
  heldCards: { fetched: number | null; newCandidates: Candidate[] }
  approvals: {
    imported: number | null; skippedDuplicates: number | null
    incompleteRows: { userCardId: string | null; approvalNoPresent: boolean; reason: string }[]
  }
  performances: {
    userCardId: string | null; label: string; period: { from: string | null; to: string | null }
    currentUseWon: number | null; fetchedAt: string; usedInCalculation: false
  }[]
  partialFailures: { target: 'HELD_CARDS' | 'APPROVALS' | 'PERFORMANCES'; code: string; message: string }[]
}
type ConfirmationRequest = {
  status: 'USER_CONFIRMED'; cardProductId: string; confirmationMethod: 'CODEF_CANDIDATE'
}
export type ConfirmedCard = {
  userCardId: string; displayName: string; holdingSource: 'CODEF'
  excludedFromRecommendation: boolean
  recommendationEligibility: { eligible: boolean; reason: string | null }
  mapping: ConfirmationRequest & {
    confirmedAt: string; candidateCardProductId: null; candidateBasis: null; note: string | null
  }
  invalidatedRecommendationIds: string[]
}

export type ManualCardInput = { displayName: string; cardProductId?: string; notSupported?: true }

export interface CardImportAdapter {
  products?(signal: AbortSignal): Promise<{ cardProductId: string; cardName: string }[]>
  registerManual?(input: ManualCardInput, signal: AbortSignal): Promise<void>
  connect(input: ConnectionRequest, signal: AbortSignal): Promise<ConnectionResponse>
  sync(signal: AbortSignal): Promise<SyncResponse>
  confirm(userCardId: string, input: ConfirmationRequest, signal: AbortSignal): Promise<ConfirmedCard>
}
