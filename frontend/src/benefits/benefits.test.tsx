import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import App from '../App'
import { createBenefitsApi } from './api'
import { mockBenefitsApi } from './fixtures'
import { bucketRows, productId } from './model'
import type { BenefitsApi, HeldCard } from './model'

const signal = new AbortController().signal
const click = (name: string | RegExp) => fireEvent.click(screen.getByRole('button', { name }))
const tab = (name: string) => fireEvent.click(screen.getByRole('tab', { name }))
beforeEach(() => vi.setSystemTime(new Date('2026-11-25T12:00:00+09:00')))
afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

it('빠른결제 모드를 사용하고 월과 선택 카드가 해당 범위에 맞게 바뀐다', async () => {
  const monthly = vi.fn(mockBenefitsApi.monthly)
  const product = vi.fn(mockBenefitsApi.product)
  render(<App benefits={{ ...mockBenefitsApi, monthly, product }} />)
  tab('가상 포트폴리오')
  click('혜택')
  await screen.findByText('523,000')
  expect(screen.queryByRole('tab', { name: '내 카드' })).not.toBeInTheDocument()
  expect(monthly).toHaveBeenCalledWith('VIRTUAL_PORTFOLIO', 'uc-1001', '2026-11', expect.any(AbortSignal))
  expect(product).toHaveBeenCalledWith('09174', expect.stringMatching(/^2026-11-/), expect.any(AbortSignal))
  expect(await screen.findByRole('button', { name: /간편 결제.*0\s*원/ })).toBeVisible()
  expect(screen.getByRole('button', { name: /버거.*확인 필요/ })).toBeVisible()
  fireEvent.change(screen.getByRole('combobox', { name: '조회 카드' }), { target: { value: 'uc-1002' } })
  expect(await screen.findByText('조회일에 적용할 검증 규칙이 없어요.')).toBeVisible()
  click('이전 달')
  await waitFor(() => expect(monthly).toHaveBeenCalledWith('VIRTUAL_PORTFOLIO', 'uc-1002', '2026-10', expect.any(AbortSignal)))
  click('빠른결제')
  tab('내 카드')
  click('혜택')
  expect(await screen.findByText('-5,000')).toBeVisible()
  expect(screen.getByRole('combobox', { name: '조회 카드' })).toHaveValue('uc-2001')
  expect(screen.queryByRole('button', { name: /입력|수정|삭제/ })).not.toBeInTheDocument()
})

it('느린 이전 모드 카드 응답을 취소하고 새 모드의 카드만 보여준다', async () => {
  let respond!: (cards: HeldCard[]) => void
  const cards = vi.fn<BenefitsApi['cards']>()
    .mockImplementationOnce(() => new Promise(resolve => { respond = resolve }))
    .mockImplementation(mockBenefitsApi.cards)
  render(<App benefits={{ ...mockBenefitsApi, cards }} />)
  click('혜택')
  const pending = cards.mock.calls[0][1]
  click('빠른결제')
  tab('가상 포트폴리오')
  click('혜택')
  await screen.findByText('523,000')
  expect(pending.aborted).toBe(true)
  const old = await mockBenefitsApi.cards('MY_CARDS', signal)
  await act(async () => respond(old))
  expect(screen.getByRole('combobox', { name: '조회 카드' })).toHaveValue('uc-1001')
  expect(screen.queryByText('-5,000')).not.toBeInTheDocument()
})

it('서버 상품 ID를 보존하며 미확인 매핑을 조회하지 않는다', () => {
  const card = { userCardId: 'uc-1', displayName: '카드', holdingSource: 'MANUAL',
    mapping: { status: 'USER_CONFIRMED', cardProductId: '09174' } }
  expect(productId(card)).toBe('09174')
  expect(productId({ ...card, mapping: { ...card.mapping, status: 'UNMAPPED' } })).toBeNull()
})

it('부모와 하위 한도를 각각 표시하고 합산하지 않으며 기간이 불명확하면 참고 한도를 숨긴다', async () => {
  const month = '2026-11'
  const [product, monthly] = await Promise.all([
    mockBenefitsApi.product('09174', '2026-11-20', signal),
    mockBenefitsApi.monthly('VIRTUAL_PORTFOLIO', 'uc-1001', month, signal),
  ])
  const modified = { ...product, limitBuckets: product.limitBuckets.map(bucket => bucket.limitBucketId === 'CHECK'
    ? { ...bucket, parentLimitBucketId: 'GREAT' } : bucket) }
  const rows = bucketRows(modified, monthly, month)
  expect(rows.find(row => row.id === 'CHECK')).toMatchObject({ parent: 'GREAT', usage: { value: 0 }, limit: 5000 })
  expect(rows.find(row => row.id === 'GREAT')).toMatchObject({ title: '스타벅스', usage: { value: 4000 }, limit: 10000 })
  expect(bucketRows({ ...modified, rule: { ...modified.rule!, validFrom: '2026-11-02' } }, monthly, month)
    .every(row => row.limit === null)).toBe(true)
  expect(bucketRows({ ...product, cardProductId: '00999' }, monthly, month)[0].title).toBe('혜택 한도 1')
})

it('원장 유형으로 필터링하고 각 행의 상세 ID와 원 승인 ID를 연결한다', async () => {
  const payment = vi.fn(mockBenefitsApi.payment)
  const ledger = vi.fn(mockBenefitsApi.ledger)
  render(<App benefits={{ ...mockBenefitsApi, payment, ledger }} />)
  tab('가상 포트폴리오')
  click('혜택')
  const overview = screen.getByRole('tab', { name: '혜택 현황' })
  fireEvent.keyDown(overview, { key: 'ArrowRight' })
  expect(screen.getByRole('tab', { name: '모의 결제 내역' })).toHaveFocus()
  expect(await screen.findByRole('button', { name: '전체 3' })).toBeVisible()
  expect(ledger).toHaveBeenLastCalledWith('VIRTUAL_PORTFOLIO', '2026-11', expect.any(AbortSignal), undefined)
  click('취소 1')
  click(/스타벅스 송도점/)
  const cancellation = screen.getByRole('dialog', { name: '모의 취소 상세' })
  expect(within(cancellation).getByText('원 승인 ID · lt-701')).toBeVisible()
  expect(await within(cancellation).findByText('승인 시 예상 혜택 · 확인 필요')).toBeVisible()
  fireEvent.keyDown(cancellation, { key: 'Escape' })
  expect(screen.getByRole('button', { name: /스타벅스 송도점/ })).toHaveFocus()
  click('승인 2')
  click(/CU 인천대점/)
  expect(await screen.findByText('승인 시 예상 혜택 · 0원')).toBeVisible()
  expect(payment.mock.calls.map(call => call[1])).toEqual(['pay-701', 'pay-703'])
  fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' })
  fireEvent.change(screen.getByLabelText('모의 결제 내역 조회 카드'), { target: { value: 'uc-1001' } })
  expect(await screen.findByRole('button', { name: '전체 1' })).toBeVisible()
  expect(ledger).toHaveBeenLastCalledWith('VIRTUAL_PORTFOLIO', '2026-11', expect.any(AbortSignal), 'uc-1001')
  click('취소 0')
  expect(screen.getByText('이 필터에 해당하는 내역이 없어요.')).toBeVisible()
})

it('demo와 member는 조회 권한 경계를 지키고 GET만 요청한다', async () => {
  const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(Response.json({ data: { month: '2026-11', items: [], coverage: [] } })))
  vi.stubGlobal('fetch', fetchMock)
  await createBenefitsApi('demo').ledger('VIRTUAL_PORTFOLIO', '2026-11', signal)
  await createBenefitsApi('member').ledger('MY_CARDS', '2026-11', signal, 'uc-544')
  expect(fetchMock.mock.calls).toEqual([
    [expect.stringMatching(/ledger-transactions\?month=2026-11&source=SIMULATION$/),
      expect.objectContaining({ method: 'GET', credentials: 'omit', headers: { 'X-BeONE-Demo-User': 'VIRTUAL_PORTFOLIO' } })],
    ['/api/v1/me/ledger-transactions?month=2026-11&source=SIMULATION&userCardId=uc-544',
      expect.objectContaining({ method: 'GET', credentials: 'include', headers: {} })],
  ])
  await expect(createBenefitsApi('member').cards('VIRTUAL_PORTFOLIO', signal)).rejects.toThrow('가상 포트폴리오')
  await expect(createBenefitsApi('demo').monthly('MY_CARDS', 'sample-0', '2026-11', signal)).rejects.toThrow('응답')
  expect(fetchMock).toHaveBeenCalledTimes(2)
})

it('카드 목록 조회가 실패해도 회원 원장의 결제 내역을 표시한다', async () => {
  const sample = await mockBenefitsApi.ledger('VIRTUAL_PORTFOLIO', '2026-11', signal)
  const cards = vi.fn().mockRejectedValue(new Error('카드 조회 실패'))
  render(<App benefits={{ ...mockBenefitsApi, context: 'member', cards, ledger: async () => sample }} />)
  click('혜택')
  await screen.findByText('카드 조회 실패')
  tab('모의 결제 내역')
  expect(await screen.findByRole('button', { name: '전체 3' })).toBeVisible()
  expect(screen.queryByText('카드 조회 실패')).not.toBeInTheDocument()
})

it.each([[401, 'UNAUTHENTICATED', '로그인 상태가 만료'], [403, 'FORBIDDEN', '권한'],
  [404, 'NOT_FOUND', '조회 API'], [500, 'INTERNAL_SERVER_ERROR', '다시 시도']] as const)(
  '조회 오류 %i %s를 빈 목록과 구분한다', async (status, code, message) => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(Response.json({ code }, { status })))
    await expect(createBenefitsApi('demo').cards('MY_CARDS', signal)).rejects.toThrow(message)
  },
)

it('서버의 숫자 문자열을 거래 금액으로 표시하지 않는다', async () => {
  const data = await mockBenefitsApi.ledger('VIRTUAL_PORTFOLIO', '2026-11', signal)
  const items = [{ ...data.items[0], amountWon: '7000' }]
  vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(Response.json({ data: { ...data, items } })))
  await expect(createBenefitsApi('demo').ledger('VIRTUAL_PORTFOLIO', '2026-11', signal)).rejects.toThrow('응답')
})
