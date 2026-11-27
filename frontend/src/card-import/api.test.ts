import { afterEach, expect, it, vi } from 'vitest'
import { createCardImportApi } from './api'

afterEach(() => vi.unstubAllGlobals())

it('동기화 후 보유 카드 목록에서 미확정 후보를 받고 선택한 보유 ID로 확인한다', async () => {
  const response = (data: unknown) => Response.json({ data })
  const candidate = {
    userCardId: 'uc-901', displayName: '노리 체크카드', holdingSource: 'CODEF',
    excludedFromRecommendation: false, recommendationEligibility: { eligible: false },
    mapping: { status: 'CANDIDATE', cardProductId: null, candidateCardProductId: '01664' },
  }
  const fetcher = vi.fn().mockResolvedValueOnce(response({ heldCards: { fetched: 1, newCandidates: [] } }))
    .mockResolvedValueOnce(response({ items: [candidate] }))
    .mockResolvedValueOnce(response({ userCardId: candidate.userCardId }))
  vi.stubGlobal('fetch', fetcher)
  const api = createCardImportApi('demo')
  const { signal } = new AbortController()
  const synced = await api.sync(signal)
  expect(synced.heldCards.newCandidates).toEqual([{
    userCardId: 'uc-901', cardName: '노리 체크카드', mappingStatus: 'CANDIDATE', candidateCardProductId: '01664',
  }])
  expect(fetcher.mock.calls[0][1].body).toBeUndefined()
  const body = { cardProductId: '01664', confirmationMethod: 'CODEF_CANDIDATE', status: 'USER_CONFIRMED' } as const
  await api.confirm(candidate.userCardId, body, signal)
  expect(fetcher).toHaveBeenLastCalledWith('/api/v1/me/cards/uc-901/mapping', expect.objectContaining({
    method: 'PATCH', body: JSON.stringify(body),
  }))
})

it('카드사 연결 경로가 제공되지 않으면 서버 오류를 유지한다', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json({ code: 'NOT_FOUND', errors: [] }, { status: 404 })))
  await expect(createCardImportApi('demo').connect({
    organization: '0301', loginType: 'ID', loginId: 'fixture', password: 'fixture',
  }, new AbortController().signal)).rejects.toMatchObject({ status: 404, code: 'NOT_FOUND' })
})
