/// <reference types="node" />
import { act, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import App from '../App'
import { merchantClasses, merchantLabels, searchMerchantClasses } from './merchantClasses'
import { emptyDraft, validateOrder } from './order'
import type { CreatedOrder, OrderInput } from './order'
import { orderApi, OrderRequestError } from './orderApi'

const draft = { ...emptyDraft, amountWon: '10000', orderDate: '2026-11-24', merchantClass: 'STARBUCKS_STORE' }
const response = (input: OrderInput, id = 'ord-5001'): CreatedOrder => ({
  ...input, orderId: id, status: 'CREATED', createdAt: '2026-11-24T15:00:00+09:00',
})
const detail = (order: CreatedOrder) => ({ ...order, simulatedPaymentId: null, latestRecommendationId: null })
const click = (name: string | RegExp) => fireEvent.click(screen.getByRole('button', { name }))
const fill = (input: HTMLElement, value: string) => fireEvent.change(input, { target: { value } })

function enterOrderForm() {
  click('모의결제')
  click('주문 생성')
}
function fillOrder() {
  fill(screen.getByLabelText('결제 금액'), '10000')
  fill(screen.getByLabelText('결제일'), '2026-11-24')
  click('가맹점 업종 분류')
  fill(screen.getByRole('searchbox'), 'starbucks')
  click('스타벅스 매장')
}
function environment() {
  vi.stubGlobal('scrollTo', vi.fn())
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: false })))
}

afterEach(() => {
  vi.unstubAllGlobals()
  delete window.BeoneBridge
})

it('분류 50개의 코드를 백엔드와 대조하고 한글 정렬과 검색을 지원한다', () => {
  const allowed = readFileSync('../backend/src/main/resources/rules/merchant-classes.txt', 'utf8')
  const expected = allowed.split('\n').map(line => line.trim()).filter(line => line && !line.startsWith('#'))
  expect([...merchantClasses].sort()).toEqual(expected.sort())
  expect(merchantClasses).toHaveLength(50)
  expect(merchantClasses.map(code => merchantLabels[code])).toEqual(
    Object.values(merchantLabels).sort(new Intl.Collator('ko').compare),
  )
  expect(searchMerchantClasses('스타벅스매장')).toEqual(['STARBUCKS_STORE'])
  expect(searchMerchantClasses('starbucks_store')).toEqual(['STARBUCKS_STORE'])
})

it.each(['', '0', '-1', '0.5', 'hello', '1e4', '9007199254740993'])('금액 %s을 거부한다', amountWon => {
  expect(validateOrder({ ...draft, amountWon }).errors).toHaveProperty('amountWon')
})

it('달력, 필수값, 선택 입력과 자정의 계약을 지킨다', () => {
  expect(validateOrder(emptyDraft).errors).toEqual(expect.objectContaining({
    amountWon: expect.any(String), orderDate: expect.any(String), merchantClass: expect.any(String),
  }))
  expect(validateOrder({ ...draft, orderDate: '2026-02-29', orderTime: '24:00' }).errors)
    .toEqual({ orderDate: expect.any(String), orderTime: expect.any(String) })
  expect(validateOrder({ ...draft, merchantName: ' ' }).input).toMatchObject({
    amountWon: 10000, orderDate: '2026-11-24', merchantName: null, orderTime: null,
  })
  expect(validateOrder({ ...draft, orderDate: '2024-02-29', orderTime: '00:00' }).input?.orderTime).toBe('00:00:00')
  expect(validateOrder({ ...draft, orderTime: '03:21:04' }).input?.orderTime).toBe('03:21:04')
  expect(validateOrder({ ...draft, merchantClass: 'invented', paymentMethod: 'OTHER' }).errors)
    .toMatchObject({ merchantClass: expect.any(String), paymentMethod: expect.any(String) })
})

it.each(['DIRECT', 'ELIGIBLE_SIMPLE_PAY', 'AUTOPAY'])('주문 계약대로 %s를 보내고 단건만 조회한다', async paymentMethod => {
  const input = validateOrder({ ...draft, paymentMethod }).input!
  const order = response(input)
  const fetchMock = vi.fn()
    .mockResolvedValueOnce(Response.json({ data: order }, { status: 201 }))
    .mockResolvedValueOnce(Response.json({ data: detail(order) }))
  vi.stubGlobal('fetch', fetchMock)

  expect(await orderApi.create('VIRTUAL_PORTFOLIO', input)).toEqual(order)
  expect(await orderApi.detail('VIRTUAL_PORTFOLIO', order.orderId)).toEqual(detail(order))
  expect(fetchMock.mock.calls).toEqual([
    [expect.stringMatching(/\/api\/v1\/me\/orders$/), expect.objectContaining({
      method: 'POST', credentials: 'omit',
      headers: { 'X-BeONE-Demo-User': 'VIRTUAL_PORTFOLIO', 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    })],
    [expect.stringMatching(/\/api\/v1\/me\/orders\/ord-5001$/), expect.objectContaining({
      method: 'GET', credentials: 'omit', headers: { 'X-BeONE-Demo-User': 'VIRTUAL_PORTFOLIO' },
    })],
  ])
})

it.each([['ORDER_NOT_FOUND', 'missing'], ['NOT_FOUND', 'unavailable']])('404 %s를 구분한다', async (code, kind) => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(Response.json({ code, errors: [] }, { status: 404 })))
  await expect(orderApi.detail('MY_CARDS', 'ord-1')).rejects.toMatchObject({ kind })
})

it('검색어 지우기, 선택값 유지, 시트 닫기와 폼 필수 검증을 지원한다', () => {
  environment()
  const create = vi.fn()
  render(<App orders={{ create, detail: vi.fn() }} />)
  enterOrderForm()
  click('주문 생성')
  expect(screen.getAllByRole('alert')).toHaveLength(3)
  expect(create).not.toHaveBeenCalled()
  fillOrder()
  const trigger = screen.getByRole('button', { name: '가맹점 업종 분류' })
  expect(trigger).toHaveTextContent('스타벅스 매장')
  fireEvent.click(trigger)
  expect(screen.getByRole('status')).toHaveTextContent('전체 50개')
  fill(screen.getByRole('searchbox'), '없는분류')
  expect(screen.getByText('검색 결과가 없어요.')).toBeInTheDocument()
  click('검색어 지우기')
  expect(screen.getByRole('button', { name: '스타벅스 매장' })).toHaveAttribute('aria-pressed', 'true')
  click('분류 검색 닫기')
  expect(trigger).toHaveFocus()
  expect(trigger).toHaveTextContent('스타벅스 매장')
})

it('서버 주문을 목록에 추가하고 단건의 상태를 확인한 뒤 빠른결제로 전달한다', async () => {
  environment()
  const create = vi.fn(async (_mode, input) => response(input))
  const get = vi.fn(async () => detail(response(validateOrder(draft).input!)))
  const postMessage = vi.fn()
  window.BeoneBridge = { postMessage, onmessage: null }
  render(<App orders={{ create, detail: get }} />)
  postMessage.mockClear()
  enterOrderForm()
  fillOrder()
  click('주문 생성')
  const accordion = await screen.findByRole('button', { name: /스타벅스 매장/ })
  expect(accordion).toHaveAttribute('aria-expanded', 'false')
  fireEvent.click(accordion)
  expect(accordion).toHaveAttribute('aria-expanded', 'true')
  expect(screen.getByText('입력 안 함')).toBeVisible()
  fireEvent.click(accordion)
  expect(accordion).toHaveAttribute('aria-expanded', 'false')
  fireEvent.click(accordion)
  click('빠른결제로 이동')
  expect(await screen.findByLabelText('선택한 가상 주문')).toHaveTextContent('10,000원')
  expect(get).toHaveBeenCalledWith('MY_CARDS', 'ord-5001')
  expect(postMessage).not.toHaveBeenCalled()

  fireEvent.click(screen.getByRole('tab', { name: '가상 포트폴리오' }))
  expect(screen.queryByLabelText('선택한 가상 주문')).not.toBeInTheDocument()
  click('모의결제')
  expect(screen.getByText('이번 실행에서 생성한 주문이 없어요.')).toBeVisible()
})

it('중복 제출과 자동 재시도를 막고 실패한 입력을 유지한다', async () => {
  environment()
  let reject!: (reason: unknown) => void
  const create = vi.fn(() => new Promise<CreatedOrder>((_, failure) => { reject = failure }))
  render(<App orders={{ create, detail: vi.fn() }} />)
  enterOrderForm()
  fillOrder()
  const form = screen.getByRole('button', { name: '주문 생성' }).closest('form')!
  fireEvent.submit(form)
  fireEvent.submit(form)
  expect(create).toHaveBeenCalledTimes(1)
  await act(async () => reject(new OrderRequestError('network')))
  expect(screen.getByRole('alert')).toHaveTextContent('다시 시도하면 주문이 중복될 수 있어요.')
  expect(screen.getByLabelText('결제 금액')).toHaveValue('10,000')
  expect(create).toHaveBeenCalledTimes(1)
})

it('모드를 바꾼 뒤 도착한 생성 응답을 요청 당시 목록에만 넣는다', async () => {
  environment()
  let resolve!: (order: CreatedOrder) => void
  const create = vi.fn(() => new Promise<CreatedOrder>(done => { resolve = done }))
  render(<App orders={{ create, detail: vi.fn() }} />)
  enterOrderForm()
  fillOrder()
  click('주문 생성')
  click('빠른결제')
  fireEvent.click(screen.getByRole('tab', { name: '가상 포트폴리오' }))
  click('모의결제')
  await act(async () => resolve(response(validateOrder(draft).input!)))
  expect(screen.getByText('이번 실행에서 생성한 주문이 없어요.')).toBeVisible()

  click('빠른결제')
  fireEvent.click(screen.getByRole('tab', { name: '내 카드' }))
  click('모의결제')
  expect(screen.getByRole('button', { name: /스타벅스 매장/ })).toBeVisible()
  expect(create).toHaveBeenCalledWith('MY_CARDS', expect.any(Object))
})

it('단건 확인에서 PAID면 빠른결제로 이동하지 않는다', async () => {
  environment()
  const order = response(validateOrder(draft).input!)
  render(<App orders={{ create: vi.fn().mockResolvedValue(order),
    detail: vi.fn().mockResolvedValue({ ...detail(order), status: 'PAID' }) }} />)
  enterOrderForm()
  fillOrder()
  click('주문 생성')
  fireEvent.click(await screen.findByRole('button', { name: /스타벅스 매장/ }))
  click('빠른결제로 이동')
  expect(await screen.findByRole('alert')).toHaveTextContent('이미 처리된 주문')
  expect(screen.queryByLabelText('선택한 가상 주문')).not.toBeInTheDocument()
  expect(within(screen.getByRole('article')).getByText('모의 승인됨')).toBeVisible()
})
