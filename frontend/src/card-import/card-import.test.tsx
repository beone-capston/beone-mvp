import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import App from '../App'
import { createMockPreferenceApi } from '../preferences/mockPreference'
import { createMockPaymentGateway } from '../payment/mockPayment'
import { sampleOrder } from '../payment/fixtures'
import { toPreference } from '../preferences/preferenceApi'
import type { CardSource } from '../data/portfolio'
import { CardImportPage } from './CardImportPage'
import { createMockCardImport } from './mockAdapter'
import { createImportedCardRepository } from './repository'

afterEach(() => {
  localStorage.clear()
  vi.unstubAllGlobals()
})

const connect = () => screen.getByRole('button', { name: '연결하고 카드 가져오기' })

function fillForm() {
  fireEvent.change(screen.getByLabelText('카드사 홈페이지 아이디'), { target: { value: 'test-id' } })
  fireEvent.change(screen.getByLabelText('카드사 홈페이지 비밀번호'), { target: { value: ' test-password ' } })
  fireEvent.click(screen.getByLabelText('조회 항목과 연결 안내 동의'))
}

it('입력과 동의가 모두 맞을 때 조회하고, 선택한 카드만 저장해 메인과 부가서비스에 표시한다', async () => {
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: false })))
  vi.stubGlobal('scrollTo', vi.fn())
  const fetch = vi.fn()
  vi.stubGlobal('fetch', fetch)
  const repository = createImportedCardRepository(localStorage, 'fixture-a')
  const adapter = createMockCardImport('success', 0)
  const confirm = vi.spyOn(adapter, 'confirm')
  const connectAdapter = vi.spyOn(adapter, 'connect')
  const cards: CardSource = { getCards: mode => mode === 'MY_CARDS' ? repository.list() : [] }
  const preferenceApi = createMockPreferenceApi(localStorage, 'fixture-a', undefined, cards)
  const app = render(<App cardImporter={{ adapter, repository }} preferenceApi={preferenceApi} />)
  expect(screen.getByText('등록된 카드가 없어요')).toBeInTheDocument()
  expect(screen.queryByRole('region', { name: '예시 카드 목록' })).not.toBeInTheDocument()
  fireEvent.click(screen.getAllByRole('button', { name: '카드 추가' })[0])
  expect(connect()).toBeDisabled()
  fillForm()
  expect(connect()).toBeEnabled()
  fireEvent.click(screen.getByLabelText('조회 항목과 연결 안내 동의'))
  expect(connect()).toBeDisabled()
  fireEvent.click(screen.getByLabelText('조회 항목과 연결 안내 동의'))
  fireEvent.change(screen.getByLabelText('카드사 홈페이지 아이디'), { target: { value: ' bad-id' } })
  expect(connect()).toBeDisabled()
  fireEvent.change(screen.getByLabelText('카드사 홈페이지 아이디'), { target: { value: 'test-id' } })
  fireEvent.click(connect())

  expect(screen.getByRole('progressbar')).toHaveAccessibleName('카드사 계정 연결')
  expect(screen.queryByDisplayValue(' test-password ')).not.toBeInTheDocument()
  await screen.findByRole('heading', { name: '내 카드를 가져왔어요' })
  expect(connectAdapter).toHaveBeenCalledOnce()
  expect(connectAdapter).toHaveBeenCalledWith({
    organization: '0301', loginType: 'ID', loginId: 'test-id', password: ' test-password ',
  }, expect.any(AbortSignal))
  expect(screen.getByRole('button', { name: '등록하기' })).toBeDisabled()
  expect(screen.getByRole('checkbox', { name: 'ABC초콜릿카드' })).toBeDisabled()
  fireEvent.click(screen.getByRole('checkbox', { name: '노리 체크카드' }))
  fireEvent.click(screen.getByRole('button', { name: '등록하기' }))
  expect(screen.getByRole('button', { name: '등록 중' })).toBeDisabled()
  await waitFor(() => expect(screen.getByRole('tab', { name: '내 카드' })).toHaveAttribute('aria-selected', 'true'))
  expect(confirm).toHaveBeenCalledExactlyOnceWith('uc-93001', {
    status: 'USER_CONFIRMED', cardProductId: '01664', confirmationMethod: 'CODEF_CANDIDATE',
  }, expect.any(AbortSignal))
  expect(within(screen.getByRole('region', { name: '예시 카드 목록' })).getAllByRole('article')).toHaveLength(1)
  expect(repository.list().map(card => card.cardProductId)).toEqual(['01664'])
  expect(JSON.stringify(localStorage)).not.toMatch(/test-password|test-id/)
  expect(createImportedCardRepository(localStorage, 'fixture-b').list()).toEqual([])
  fireEvent.click(await screen.findByRole('button', { name: '결제 선택 방식' }))
  fireEvent.click(screen.getByRole('menuitemradio', { name: '대표 카드 고정' }))
  expect(screen.getAllByRole('radio')).toHaveLength(1)
  fireEvent.click(screen.getByRole('radio', { name: '노리 체크카드' }))
  fireEvent.click(screen.getByRole('button', { name: '대표 카드로 설정' }))
  await screen.findByText('고정된 대표 카드: 노리 체크카드')
  const preference = toPreference(await preferenceApi.read('MY_CARDS', new AbortController().signal))
  const gateway = createMockPaymentGateway(0, Date.now, true, cards)
  expect(await gateway.approve({
    mode: 'MY_CARDS', selection: { order: sampleOrder, source: 'mock' }, body: {}, key: 'import-test',
    startedAt: Date.now(), preference, scenario: 'approved',
  })).toMatchObject({ userCardId: 'uc-93001', cardProductId: '01664', selectionMode: 'FIXED_REPRESENTATIVE' })
  app.unmount()

  render(<App cardImporter={{ adapter, repository: createImportedCardRepository(localStorage, 'fixture-a') }} />)
  fireEvent.click(screen.getByRole('button', { name: '노리 체크카드 부가서비스' }))
  const details = screen.getByRole('region', { name: '혜택 및 이용 조건' })
  await waitFor(() => expect(screen.getByRole('button', { name: '빠른결제로 돌아가기' })).toHaveFocus())
  expect(within(details).getByRole('article', { name: '금융수수료 면제' })).toBeInTheDocument()
  expect(fetch).not.toHaveBeenCalled()
})

it.each([
  ['connection-failure', /연결 정보를 확인/],
  ['query-failure', /잠시 후 카드 가져오기/],
  ['empty', /조회된 카드가 없어요/],
  ['unsupported', /선택할 수 있는 카드가 없어요/],
  ['partial-data', /실적 일부를 조회하지 못했어요/],
])('조회 시나리오 %s를 구분한다', async (scenario, message) => {
  const importer = { adapter: createMockCardImport(scenario, 0),
    repository: createImportedCardRepository(localStorage, 'a') }
  render(<CardImportPage importer={importer} onClose={vi.fn()} />)
  fillForm()
  fireEvent.click(connect())
  expect(await screen.findByText(message)).toBeInTheDocument()
  if (scenario === 'connection-failure') {
    fireEvent.click(screen.getByRole('button', { name: '다시 가져오기' }))
    expect(screen.getByLabelText('카드사 홈페이지 비밀번호')).toHaveValue('')
  }
})

it('부분 등록 성공은 저장하고 실패한 카드만 다시 확인한다', async () => {
  const adapter = createMockCardImport('partial-registration', 0)
  const repository = createImportedCardRepository(localStorage, 'a')
  const close = vi.fn()
  const confirm = vi.spyOn(adapter, 'confirm')
  const importer = { adapter, repository }
  const view = render(<CardImportPage importer={importer} onClose={close} />)
  fillForm()
  fireEvent.click(connect())
  fireEvent.click(await screen.findByRole('checkbox', { name: '노리 체크카드' }))
  fireEvent.click(screen.getByRole('checkbox', { name: '올라운드 티타늄카드' }))
  fireEvent.click(screen.getByRole('button', { name: '등록하기' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('등록하지 못했어요.')
  expect(screen.getByText('이미 등록됨')).toBeInTheDocument()
  expect(close).not.toHaveBeenCalled()
  expect(screen.getByRole('checkbox', { name: '노리 체크카드' })).toBeDisabled()
  expect(screen.getByRole('checkbox', { name: '올라운드 티타늄카드' })).toBeChecked()
  fireEvent.click(screen.getByRole('button', { name: '등록하기' }))
  await waitFor(() => expect(close).toHaveBeenCalledOnce())
  expect(confirm.mock.calls.map(([id]) => id)).toEqual(['uc-93001', 'uc-93002', 'uc-93002'])
  view.unmount()
  render(<CardImportPage importer={importer} onClose={close} />)
  fillForm()
  fireEvent.click(connect())
  expect(await screen.findAllByText('이미 등록됨')).toHaveLength(2)
  expect(screen.getByRole('button', { name: '등록하기' })).toBeDisabled()
})

it('화면 이탈 중 연결 요청을 중단하고 조회나 등록으로 넘어가지 않는다', () => {
  const adapter = createMockCardImport('success', 1000)
  const connectAdapter = vi.spyOn(adapter, 'connect')
  const sync = vi.spyOn(adapter, 'sync')
  const repository = createImportedCardRepository(localStorage, 'a')
  const view = render(<CardImportPage importer={{ adapter, repository }} onClose={vi.fn()} />)
  fillForm()
  fireEvent.click(connect())
  expect(connectAdapter.mock.calls[0][1].aborted).toBe(false)
  view.unmount()
  expect(connectAdapter.mock.calls[0][1].aborted).toBe(true)
  expect(sync).not.toHaveBeenCalled()
  expect(repository.list()).toEqual([])
})
