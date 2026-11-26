import { AuthApiError } from '../auth/api'
import { mockCardSource } from '../data/portfolio'
import type { PortfolioMode } from '../data/portfolio'
import { defaultDto, parsePreference } from './preferenceApi'
import type { PreferenceApi } from './preferenceApi'

type Failure = 'read' | 'save'

export const mockCardId = (index: number) => 'uc-' + (1001 + index)

export const preferenceMockCardSource = {
  getCards(mode: PortfolioMode) {
    return mockCardSource.getCards(mode).map((card, index) => ({
      ...card, id: mockCardId(index), canBeRepresentative: index !== 3,
    }))
  },
}

export function createMockPreferenceApi(storage: Storage, fixtureUser = 'fixture', failure?: Failure): PreferenceApi {
  const key = (mode: PortfolioMode) => 'beone:dev:payment-preference:v1:' + fixtureUser + ':' + mode
  return {
    supports: () => true,
    async read(mode) {
      if (failure === 'read') throw new Error('MOCK_READ')
      const value = storage.getItem(key(mode))
      return value === null ? defaultDto() : parsePreference(JSON.parse(value))
    },
    async cards(mode) { return preferenceMockCardSource.getCards(mode) },
    async save(mode, input) {
      if (failure === 'save') throw new Error('MOCK_SAVE')
      const card = preferenceMockCardSource.getCards(mode).find(candidate => candidate.id === input.representativeUserCardId)
      if (input.representativeUserCardId && !card?.canBeRepresentative) {
        throw new AuthApiError(card ? 409 : 404, {
          code: card ? 'REPRESENTATIVE_CARD_NOT_ELIGIBLE' : 'USER_CARD_NOT_FOUND', message: '', errors: [],
        }, null)
      }
      const result = parsePreference({ ...input, updatedAt: new Date().toISOString() })
      storage.setItem(key(mode), JSON.stringify(result))
      return result
    },
  }
}
