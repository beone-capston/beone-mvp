import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import App from '../App'
import { cardApi, CardRequestError } from './cardApi'
import type { CardApi, UserCard } from './cardApi'
import { createMemberCardSource, toWalletCard } from './memberCardSource'

const csrf = () => Response.json({ data: { headerName: 'X-CSRF-TOKEN', token: 'csrf-1' } })
const serverCard = {
  userCardId: 'uc-1', displayName: '합성 톡톡', holdingSource: 'MANUAL',
  mapping: { status: 'USER_CONFIRMED', cardProductId: '09174' },
  recommendationEligibility: { eligible: true, reason: null }, registeredAt: '2026-11-25T10:00:00+09:00',
}
const products = [
  { cardProductId: '09174', cardName: '청춘대로 톡톡카드', cardType: 'CREDIT' },
  { cardProductId: '01664', cardName: '노리 체크카드', cardType: 'CHECK' },
]

beforeEach(() => {
  vi.stubGlobal('scrollTo', vi.fn())
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: false })))
})
afterEach(() => vi.unstubAllGlobals())

function fakeApi(initial: UserCard[] = []) {
  const cards = [...initial]
  const api = {
    list: vi.fn(async () => [...cards]),
    products: vi.fn(async () => products),
    register: vi.fn(async (input: { displayName: string; cardProductId?: string }) => {
      const card = { userCardId: 'uc-' + (cards.length + 1), displayName: input.displayName,
        cardProductId: input.cardProductId ?? null, eligible: Boolean(input.cardProductId) }
      cards.push(card)
      return card
    }),
  }
  return api as unknown as CardApi & typeof api
}

it('보유 카드 목록 계약: 쿠키 포함 GET, 매핑 상품과 추천 가능 여부를 읽는다', async () => {
  const fetchMock = vi.fn().mockResolvedValueOnce(Response.json({ data: { items: [serverCard] } }))
  vi.stubGlobal('fetch', fetchMock)
  expect(await cardApi.list()).toEqual([{ userCardId: 'uc-1', displayName: '합성 톡톡', cardProductId: '09174', eligible: true }])
  expect(fetchMock).toHaveBeenCalledWith('/api/v1/me/cards', expect.objectContaining({ method: 'GET', credentials: 'include' }))
})

it('수동 등록은 CSRF 토큰과 함께 표시 이름·상품만 보낸다', async () => {
  const fetchMock = vi.fn().mockResolvedValueOnce(csrf())
    .mockResolvedValueOnce(Response.json({ data: serverCard }, { status: 201 }))
  vi.stubGlobal('fetch', fetchMock)
  await cardApi.register({ displayName: '합성 톡톡', cardProductId: '09174' })
  expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/me/cards', expect.objectContaining({
    method: 'POST', credentials: 'include', body: JSON.stringify({ displayName: '합성 톡톡', cardProductId: '09174' }),
    headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'csrf-1', 'Content-Type': 'application/json' }),
  }))
})

it.each([
  [409, 'USER_CARD_DUPLICATED', 'duplicated'],
  [404, 'CARD_PRODUCT_NOT_FOUND', 'product'],
  [400, 'INVALID_REQUEST', 'validation'],
  [401, 'UNAUTHENTICATED', 'unauthenticated'],
  [403, 'CSRF_INVALID', 'csrf'],
])('등록 %i %s를 구분한다', async (status, code, kind) => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(csrf()).mockResolvedValueOnce(Response.json({ code }, { status })))
  await expect(cardApi.register({ displayName: 'x' })).rejects.toMatchObject({ kind })
})

it('CSRF 토큰을 받지 못하면 등록을 보내지 않는다', async () => {
  const fetchMock = vi.fn().mockRejectedValueOnce(new TypeError('offline'))
  vi.stubGlobal('fetch', fetchMock)
  await expect(cardApi.register({ displayName: 'x' })).rejects.toBeInstanceOf(CardRequestError)
  expect(fetchMock).toHaveBeenCalledTimes(1)
})

it('서버 카드를 기존 디자인과 종류 표시로 바꾼다', () => {
  expect(toWalletCard({ userCardId: 'uc-1', displayName: '내 톡톡', cardProductId: '09174', eligible: true }, products))
    .toEqual({ id: 'uc-1', name: '내 톡톡', type: '신용', theme: 'mint-card', caption: 'YOUTH, EVERY DAY',
      source: 'MEMBER', canBeRepresentative: true })
  expect(toWalletCard({ userCardId: 'uc-2', displayName: '모름', cardProductId: null, eligible: false }, products))
    .toMatchObject({ type: '', caption: '', canBeRepresentative: false })
})

it('새 회원은 빈 상태를 보고, 카드를 등록하면 목록을 다시 불러와 보여 준다', async () => {
  const api = fakeApi()
  render(<App cardSource={createMemberCardSource(api)} />)
  expect(await screen.findByText('등록된 카드가 없어요')).toBeInTheDocument()
  expect(screen.queryByText('올라운드 티타늄카드')).not.toBeInTheDocument()

  fireEvent.click(screen.getAllByRole('button', { name: '카드 추가' })[0])
  const sheet = screen.getByRole('dialog', { name: '카드 추가' })
  await waitFor(() => expect(within(sheet).getByRole('option', { name: '청춘대로 톡톡카드' })).toBeInTheDocument())
  fireEvent.change(within(sheet).getByLabelText('카드 이름'), { target: { value: ' 내 톡톡 ' } })
  fireEvent.change(within(sheet).getByLabelText('카드 상품'), { target: { value: '09174' } })
  fireEvent.click(within(sheet).getByRole('button', { name: '카드 등록' }))

  await waitFor(() => expect(screen.queryByRole('dialog', { name: '카드 추가' })).not.toBeInTheDocument())
  expect(api.register).toHaveBeenCalledWith({ displayName: '내 톡톡', cardProductId: '09174' })
  expect(await screen.findByText('카드를 등록했어요.')).toBeInTheDocument()
  await waitFor(() => expect(screen.getByRole('heading', { name: /나의 카드/ })).toHaveTextContent('1'))
  expect(api.list).toHaveBeenCalledTimes(2)
})

it('목록에 없는 카드는 notSupported로, 아직 모르면 상품 없이 등록한다', async () => {
  const api = fakeApi()
  const source = createMemberCardSource(api)
  render(<App cardSource={source} />)
  await screen.findByText('등록된 카드가 없어요')
  fireEvent.click(screen.getAllByRole('button', { name: '카드 추가' })[0])
  let sheet = screen.getByRole('dialog', { name: '카드 추가' })
  fireEvent.change(within(sheet).getByLabelText('카드 이름'), { target: { value: '회사 카드' } })
  fireEvent.change(within(sheet).getByLabelText('카드 상품'), { target: { value: 'NOT_SUPPORTED' } })
  fireEvent.click(within(sheet).getByRole('button', { name: '카드 등록' }))
  await waitFor(() => expect(api.register).toHaveBeenCalledWith({ displayName: '회사 카드', notSupported: true }))

  await screen.findByText('카드를 등록했어요.')
  fireEvent.click(screen.getAllByRole('button', { name: '카드 추가' })[0])
  sheet = screen.getByRole('dialog', { name: '카드 추가' })
  fireEvent.change(within(sheet).getByLabelText('카드 이름'), { target: { value: '모르는 카드' } })
  fireEvent.click(within(sheet).getByRole('button', { name: '카드 등록' }))
  await waitFor(() => expect(api.register).toHaveBeenLastCalledWith({ displayName: '모르는 카드' }))
})

it('이름이 비면 보내지 않고, 중복이면 시트에 안내를 남긴다', async () => {
  const api = fakeApi()
  api.register.mockRejectedValueOnce(new CardRequestError('duplicated'))
  render(<App cardSource={createMemberCardSource(api)} />)
  await screen.findByText('등록된 카드가 없어요')
  fireEvent.click(screen.getAllByRole('button', { name: '카드 추가' })[0])
  const sheet = screen.getByRole('dialog', { name: '카드 추가' })
  fireEvent.click(within(sheet).getByRole('button', { name: '카드 등록' }))
  expect(within(sheet).getByRole('alert')).toHaveTextContent('카드 이름을 1~30자로')
  expect(api.register).not.toHaveBeenCalled()

  fireEvent.change(within(sheet).getByLabelText('카드 이름'), { target: { value: '톡톡' } })
  fireEvent.click(within(sheet).getByRole('button', { name: '카드 등록' }))
  expect(await within(sheet).findByRole('alert')).toHaveTextContent('같은 상품의 카드가 이미 등록돼 있어요.')
  expect(screen.getByRole('dialog', { name: '카드 추가' })).toBeInTheDocument()
})

it('목록을 불러오지 못하면 안내한다', async () => {
  const api = fakeApi()
  api.list.mockRejectedValueOnce(new CardRequestError('network'))
  render(<App cardSource={createMemberCardSource(api)} />)
  expect(await screen.findByText('카드 목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.')).toBeInTheDocument()
})
