import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import App from '../App'
import { catalog } from './catalog'

vi.mock('./api', async (importOriginal) => ({
  ...await importOriginal<typeof import('./api')>(),
  offlineCatalog: false,
  demoMode: false,
  seoulToday: () => '2026-11-25',
}))

afterEach(() => vi.unstubAllGlobals())

function productResponse(id: keyof typeof catalog, cardName: string) {
  const product = catalog[id]
  return new Response(JSON.stringify({ data: {
    cardProductId: id, cardName, asOf: '2026-11-25', ruleStatus: 'VERIFIED',
    rule: { ruleVersion: product.ruleVersion, verificationStatus: 'VERIFIED' },
    services: product.services.map(service => ({
      serviceId: service.id, name: service.id, benefitKind: service.kind,
      supportStatus: service.requiresSelection ? 'UNSUPPORTED_SELECTION_RULE' : 'SUPPORTED',
      limitBucketIds: service.limitBuckets, requiresSelection: service.requiresSelection,
    })),
    limitBuckets: product.limitBuckets.map(bucket => ({
      limitBucketId: bucket.id, parentLimitBucketId: bucket.parent, period: 'CALENDAR_MONTH',
      tiers: bucket.tiers.map(tier => ({ minimumPerformanceWon: tier.minimumPerformance, monthlyCapWon: tier.monthlyCap })),
    })),
  } }))
}

it('이전 카드 응답을 중단하고 API 오류나 다른 포트폴리오의 화면에 설명을 대체하지 않는다', async () => {
  let resolveDelayed: (response: Response) => void = () => {}
  const delayed = new Promise<Response>(resolve => { resolveDelayed = resolve })
  const fetcher = vi.fn()
    .mockReturnValueOnce(delayed)
    .mockResolvedValueOnce(new Response('', { status: 503 }))
    .mockResolvedValueOnce(productResponse('09174', '서버 톡톡 상품'))
  vi.stubGlobal('fetch', fetcher)
  vi.stubGlobal('matchMedia', () => ({ matches: false }))
  vi.stubGlobal('scrollTo', vi.fn())

  render(<App />)
  fireEvent.click(screen.getByRole('button', { name: '올라운드 티타늄카드 부가서비스' }))
  expect(screen.getByRole('dialog')).toBeInTheDocument()
  expect(fetcher).not.toHaveBeenCalled()
  fireEvent.keyDown(document, { key: 'Escape' })
  fireEvent.click(screen.getByRole('tab', { name: '가상 포트폴리오' }))
  fireEvent.click(screen.getByRole('button', { name: '올라운드 티타늄카드 부가서비스' }))
  fireEvent.click(screen.getByRole('button', { name: '빠른결제로 돌아가기' }))
  expect(fetcher.mock.calls[0][1].signal.aborted).toBe(true)

  fireEvent.click(screen.getByRole('button', { name: '청춘대로 톡톡카드 부가서비스' }))
  expect(await screen.findByText('서버에서 정보를 불러오지 못했어요.')).toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: '혜택 및 이용 조건' })).not.toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))
  expect(await screen.findByRole('heading', { name: '서버 톡톡 상품' })).toBeInTheDocument()

  await act(async () => resolveDelayed(productResponse('09271', '이전 카드의 응답')))
  await waitFor(() => expect(screen.queryByText('이전 카드의 응답')).not.toBeInTheDocument())
  expect(screen.getByRole('heading', { name: '서버 톡톡 상품' })).toBeInTheDocument()
})
