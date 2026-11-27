import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import App from '../App'
import { createMockPaymentGateway } from '../payment/mockPayment'
import { sampleOrder } from '../payment/fixtures'
import type { Attempt } from '../payment/contract'
import { defaultPreference } from '../data/portfolio'
import { createOrderApi } from '../orders/orderApi'
import { createPaymentGateway } from '../payment/paymentApi'
import { createMockPreferenceApi, preferenceMockCardSource } from './mockPreference'
import { createPreferenceApi, defaultDto, parsePreference, toPreference, toPreferenceInput } from './preferenceApi'
import type { PreferenceApi } from './preferenceApi'

afterEach(() => {
  vi.unstubAllGlobals()
  localStorage.clear()
})

const response = (data: unknown, status = 200) => Response.json({ data }, { status })
const fixed = { selectionMode: 'FIXED_REPRESENTATIVE', representativeUserCardId: 'uc-1002' } as const
const saved = { ...fixed, updatedAt: '2026-11-27T01:00:00Z' }
const signal = new AbortController().signal

it('미설정 응답과 두 모드를 확인하고 정확한 보유 카드 필드를 전송한다', async () => {
  const fetcher = vi.fn().mockResolvedValueOnce(response(defaultDto())).mockResolvedValueOnce(response(saved))
    .mockResolvedValueOnce(response({ ...defaultDto(), updatedAt: saved.updatedAt }))
  vi.stubGlobal('fetch', fetcher)
  const api = createPreferenceApi('demo')
  expect(await api.read('VIRTUAL_PORTFOLIO', signal)).toEqual(defaultDto())
  expect(fetcher).toHaveBeenCalledOnce()
  expect(fetcher.mock.calls[0][1].headers).toEqual({ 'X-BeONE-Demo-User': 'VIRTUAL_PORTFOLIO' })
  expect(await api.save('VIRTUAL_PORTFOLIO', toPreferenceInput(toPreference(saved)))).toEqual(saved)
  expect(JSON.parse(fetcher.mock.calls[1][1].body)).toEqual(fixed)
  await api.save('MY_CARDS', toPreferenceInput(defaultPreference()))
  expect(JSON.parse(fetcher.mock.calls[2][1].body)).toEqual({ selectionMode: 'AUTO_RECOMMEND', representativeUserCardId: null })
  for (const id of [null, 'sample-0', '09174']) {
    await expect(api.save('MY_CARDS', { ...fixed, representativeUserCardId: id })).rejects.toMatchObject({ code: 'INVALID_REQUEST' })
  }
  expect(fetcher).toHaveBeenCalledTimes(3)
  expect(() => parsePreference({ selectionMode: 'AUTO_RECOMMEND', updatedAt: null })).toThrow()
})

it('실제 카드 응답의 보유 ID와 적격 상태를 사용한다', async () => {
  const card = {
    userCardId: 'uc-777', displayName: '가상 카드 이름', holdingSource: 'SYNTHETIC', cardProductId: '09174',
    recommendationEligibility: { eligible: true }, excludedFromRecommendation: false,
  }
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response({ items: [
    card, { ...card, userCardId: 'uc-778', excludedFromRecommendation: true },
    { ...card, userCardId: 'uc-779', holdingSource: 'MANUAL' },
    { ...card, userCardId: 'uc-780', holdingSource: 'MANUAL', mapping: { status: 'USER_CONFIRMED', cardProductId: '09174' } },
  ] })))
  const list = await createPreferenceApi('demo').cards('VIRTUAL_PORTFOLIO', signal)
  expect(list.map(item => [item.id, item.canBeRepresentative])).toEqual([
    ['uc-777', true], ['uc-778', false], ['uc-779', false], ['uc-780', true],
  ])
})

it('회원 설정·주문·모의 승인이 모두 세션과 CSRF를 사용하고 데모 헤더를 보내지 않는다', async () => {
  const csrf = () => response({ headerName: 'X-CSRF-TOKEN', token: 'test-csrf' })
  const attempt: Attempt = { mode: 'MY_CARDS', selection: { order: sampleOrder, source: 'server' },
    body: {}, key: 'test-key', startedAt: 0, preference: toPreference(saved), scenario: 'approved' }
  const approval = await createMockPaymentGateway(0).approve(attempt)
  const fetcher = vi.fn().mockResolvedValueOnce(csrf()).mockResolvedValueOnce(response(saved))
    .mockResolvedValueOnce(csrf()).mockResolvedValueOnce(response(sampleOrder, 201))
    .mockResolvedValueOnce(csrf()).mockResolvedValueOnce(response(approval, 201))
  vi.stubGlobal('fetch', fetcher)
  await createPreferenceApi('member').save('MY_CARDS', fixed)
  await createOrderApi('member').create('MY_CARDS', sampleOrder)
  await createPaymentGateway('member').approve(attempt)
  for (const index of [1, 3, 5]) {
    expect(fetcher.mock.calls[index][1]).toMatchObject({ credentials: 'include', cache: 'no-store',
      headers: { 'X-CSRF-TOKEN': 'test-csrf' } })
    expect(fetcher.mock.calls[index][1].headers).not.toHaveProperty('X-BeONE-Demo-User')
  }
  expect(fetcher.mock.calls[5][1]).toMatchObject({ body: '{}', headers: { 'Idempotency-Key': 'test-key' } })
  await expect(createPreferenceApi('member').read('VIRTUAL_PORTFOLIO', signal)).rejects.toThrow('UNSUPPORTED_PORTFOLIO')
  expect(fetcher).toHaveBeenCalledTimes(6)
})

it('개발용 설정은 새 어댑터에서 복원되고 포트폴리오·사용자별로 분리된다', async () => {
  const api = createMockPreferenceApi(localStorage, 'a')
  await api.save('MY_CARDS', fixed)
  const reloaded = createMockPreferenceApi(localStorage, 'a')
  expect(await reloaded.read('MY_CARDS', signal)).toMatchObject(fixed)
  expect(await reloaded.read('VIRTUAL_PORTFOLIO', signal)).toEqual(defaultDto())
  expect(await createMockPreferenceApi(localStorage, 'b').read('MY_CARDS', signal)).toEqual(defaultDto())
})

it('목 승인 카드는 고정 설정을 쓰고 비교 최적 카드나 미확정 혜택과 구분한다', async () => {
  const gateway = createMockPaymentGateway(0, Date.now, true)
  const approve = (preference = defaultPreference(), id = 'auto') => gateway.approve({
    mode: 'MY_CARDS', selection: { order: { ...sampleOrder, orderId: id }, source: 'mock' },
    body: {}, key: id, startedAt: 0, preference, scenario: 'approved',
  })
  expect(await approve()).toMatchObject({ userCardId: 'uc-1001', currentOrderBenefit: { amountWon: 700 } })
  const result = await approve(toPreference(saved), 'fixed')
  expect(result).toMatchObject({ userCardId: 'uc-1002', currentOrderBenefit: { amountWon: 200 } })
  const recommendation = await gateway.recommendation('MY_CARDS', result.recalculation.recommendationId, 'fixed')
  expect(recommendation.selected?.userCardId).toBe('uc-1001')
  expect(recommendation.fixedCardComparison?.representativeUserCardId).toBe('uc-1002')
  expect(await approve({ ...toPreference(saved), representativeCardId: 'uc-1003' }, 'pending'))
    .toMatchObject({ userCardId: 'uc-1003', currentOrderBenefit: { amountWon: null, status: 'NEEDS_CONFIRMATION' } })
  await expect(approve({ ...toPreference(saved), representativeCardId: 'uc-1004' }, 'ineligible'))
    .rejects.toMatchObject({ code: 'REPRESENTATIVE_CARD_NOT_ELIGIBLE' })
})

it('후보 취소·저장 중복·다른 모드의 늦은 조회·저장 실패를 분리한다', async () => {
  vi.stubGlobal('matchMedia', () => ({ matches: false }))
  vi.stubGlobal('scrollTo', vi.fn())
  let loadVirtual: (value: ReturnType<typeof defaultDto>) => void = () => {}
  let failSave: (reason: unknown) => void = () => {}
  const save = vi.fn().mockReturnValueOnce(new Promise((_, reject) => { failSave = reject }))
    .mockResolvedValue(saved)
  const mock = createMockPreferenceApi(localStorage)
  const api: PreferenceApi = {
    ...mock,
    read: mode => mode === 'MY_CARDS' ? Promise.resolve(defaultDto()) :
      new Promise(resolve => { loadVirtual = resolve }),
    save,
  }
  const click = (name: string, role = 'button') => fireEvent.click(screen.getByRole(role, { name }))
  const choose = () => {
    click('결제 선택 방식')
    click('대표 카드 고정', 'menuitemradio')
    click('청춘대로 톡톡카드', 'radio')
  }
  render(<App preferenceApi={api} cardSource={preferenceMockCardSource} />)
  await screen.findByRole('button', { name: '결제 선택 방식' })
  choose()
  expect(within(screen.getByRole('dialog')).queryByRole('radio', { name: '첵첵 체크카드' })).toBeNull()
  click('닫기')
  expect(save).not.toHaveBeenCalled()
  choose()
  click('대표 카드로 설정')
  expect(save).toHaveBeenCalledOnce()
  expect(within(screen.getByRole('dialog')).getByRole('button', { name: '저장 중…' })).toBeDisabled()
  await act(async () => loadVirtual(defaultDto()))
  await act(async () => failSave(new Error('network')))
  expect(screen.getByRole('button', { name: '결제 선택 방식' })).toHaveTextContent('자동 추천')
  expect(screen.getByRole('alert')).toBeInTheDocument()
  click('대표 카드로 설정')
  expect(await screen.findByText('고정된 대표 카드: 청춘대로 톡톡카드')).toBeInTheDocument()
  click('가상 포트폴리오', 'tab')
  expect(screen.getByRole('button', { name: '결제 선택 방식' })).toHaveTextContent('자동 추천')
})

it('조회 실패 시 자동 추천으로 진행하지 않고 재조회한 저장값을 사용한다', async () => {
  vi.stubGlobal('matchMedia', () => ({ matches: false }))
  vi.stubGlobal('scrollTo', vi.fn())
  const mock = createMockPreferenceApi(localStorage)
  const readOwn = vi.fn().mockRejectedValueOnce(new Error('network')).mockResolvedValue(saved)
  const save = vi.fn().mockResolvedValue({ ...defaultDto(), updatedAt: saved.updatedAt })
  const api: PreferenceApi = {
    ...mock, read: (mode, abort) => mode === 'MY_CARDS' ? readOwn() : mock.read(mode, abort), save,
  }
  render(<App preferenceApi={api} cardSource={preferenceMockCardSource} />)
  await waitFor(() => expect(screen.queryByText('결제 설정 확인 중…')).not.toBeInTheDocument())
  expect(readOwn).toHaveBeenCalledOnce()
  expect(screen.queryByRole('button', { name: '다시 조회' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: '결제 선택 방식' })).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: '올라운드 티타늄카드 가상카드 인증' }))
  expect(screen.queryByText('카드 사용')).toBeNull()
  fireEvent.click(screen.getByRole('tab', { name: '내 카드' }))
  const menu = await screen.findByRole('button', { name: '결제 선택 방식' })
  expect(menu).toHaveTextContent('대표 카드 고정')
  fireEvent.click(menu)
  expect(screen.queryByRole('menuitem', { name: '대표 카드 해제' })).not.toBeInTheDocument()
  fireEvent.click(screen.getByRole('menuitemradio', { name: '자동 추천' }))
  expect(save).toHaveBeenCalledWith('MY_CARDS', { selectionMode: 'AUTO_RECOMMEND', representativeUserCardId: null })
  await screen.findByText('자동 추천', { selector: '.preference-trigger' })
  fireEvent.click(menu)
  fireEvent.click(screen.getByRole('menuitemradio', { name: '자동 추천' }))
  expect(save).toHaveBeenCalledOnce()
})
