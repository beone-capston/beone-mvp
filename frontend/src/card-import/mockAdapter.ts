import type { Candidate, CardImportAdapter, SyncResponse } from './contract'

const fixtures: Candidate[] = [
  { userCardId: 'uc-93001', cardName: '노리 체크카드', candidateCardProductId: '01664', mappingStatus: 'CANDIDATE' },
  { userCardId: 'uc-93002', cardName: '올라운드 티타늄카드', candidateCardProductId: '09271', mappingStatus: 'CANDIDATE' },
  { userCardId: 'uc-93003', cardName: 'ABC초콜릿카드', candidateCardProductId: null, mappingStatus: 'UNMAPPED' },
]
const unavailable = { code: 'CODEF_UNAVAILABLE', message: '카드사 정보를 조회할 수 없어요.' }

function wait(ms: number, signal: AbortSignal) {
  signal.throwIfAborted()
  return new Promise<void>((resolve, reject) => {
    const timer = window.setTimeout(() => {
      signal.removeEventListener('abort', abort)
      resolve()
    }, ms)
    function abort() {
      window.clearTimeout(timer)
      reject(signal.reason)
    }
    signal.addEventListener('abort', abort, { once: true })
  })
}

export function createMockCardImport(scenario = 'success', delay = 1000): CardImportAdapter {
  let needsRetry = scenario === 'partial-registration'
  return {
    async connect(_input, signal) {
      await wait(delay, signal)
      if (scenario === 'connection-failure') throw new Error('CODEF_CONNECTION_FAILED')
      return { organization: '0301', status: 'CONNECTED', connectedAt: new Date().toISOString(),
        lastSyncedAt: null, reconnectRequired: false }
    },
    async sync(signal) {
      await wait(delay * 1.3, signal)
      const queryFailed = scenario === 'query-failure'
      const partial = scenario === 'partial-data'
      const cards = queryFailed || scenario === 'empty' ? []
        : scenario === 'unsupported' ? fixtures.filter(card => card.mappingStatus === 'UNMAPPED') : fixtures
      const result: SyncResponse = {
        syncId: 'sync-93001', syncedAt: new Date().toISOString(),
        heldCards: { fetched: queryFailed ? null : cards.length, newCandidates: cards },
        approvals: { imported: partial ? null : 0, skippedDuplicates: partial ? null : 0, incompleteRows: [] },
        performances: [],
        partialFailures: queryFailed ? [{ target: 'HELD_CARDS', ...unavailable }]
          : partial ? [{ target: 'APPROVALS', ...unavailable }, { target: 'PERFORMANCES', ...unavailable }] : [],
      }
      return result
    },
    async confirm(userCardId, input, signal) {
      await wait(delay * .65, signal)
      const candidate = fixtures.find(card => card.userCardId === userCardId && card.mappingStatus === 'CANDIDATE')
      if (!candidate || candidate.candidateCardProductId !== input.cardProductId) {
        throw new Error('CARD_MAPPING_CONFLICT')
      }
      if (needsRetry && userCardId === 'uc-93002') {
        needsRetry = false
        throw new Error('MOCK_CONFIRM_FAILURE')
      }
      // Confirmation can be retried if local persistence fails after this response.
      return {
        userCardId, displayName: candidate.cardName, holdingSource: 'CODEF',
        excludedFromRecommendation: false, recommendationEligibility: { eligible: true, reason: null },
        mapping: { ...input, confirmedAt: new Date().toISOString(), candidateCardProductId: null,
          candidateBasis: null, note: null },
        invalidatedRecommendationIds: [],
      }
    },
  }
}
