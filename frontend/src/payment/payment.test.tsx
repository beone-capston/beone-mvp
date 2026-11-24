import { StrictMode } from 'react'
import { act, fireEvent, render, renderHook, screen } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import App from '../App'
import { defaultPreference } from '../data/portfolio'
import { retryWindow } from './contract'
import type { Approval, Attempt, Scenario } from './contract'
import type { AuthSimulation } from './deviceAuth'
import { useMockAuth } from './deviceAuth'
import { sampleOrder } from './fixtures'
import { createMockPaymentGateway } from './mockPayment'
import { apiPaymentGateway } from './paymentApi'

const attempt: Attempt = {
  key: 'test-key', startedAt: 1, preference: defaultPreference(), scenario: 'approved',
  mode: 'MY_CARDS', body: {}, selection: { source: 'mock', order: sampleOrder },
}
const csrf = () => Response.json({ data: { headerName: 'X-CSRF-TOKEN', token: 'csrf-1' } })
const click = (name: string | RegExp) => fireEvent.click(screen.getByRole('button', { name }))
const tapCard = () => click('올라운드 티타늄카드 가상카드 인증')
function prepare() {
  click('모의결제')
  click(/스타벅스 송도점/)
  click('빠른결제로 이동')
  tapCard()
}

function selectScenario(gateway: ReturnType<typeof createMockPaymentGateway>, getScenario: () => Scenario) {
  const approve = gateway.approve.bind(gateway)
  return vi.spyOn(gateway, 'approve').mockImplementation(attempt => approve({ ...attempt, scenario: getScenario() }))
}

beforeEach(() => vi.stubGlobal('scrollTo', vi.fn()))
afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

it('목 결제에서는 내 카드 목록의 예시 주문을 서버 요청 없이 선택할 수 있다', () => {
  const fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
  render(<App mockAuth paymentGateway={createMockPaymentGateway(0)} />)
  click('모의결제')
  expect(screen.getByText('개발용 목 주문')).toBeVisible()
  click(/스타벅스 송도점/)
  click('빠른결제로 이동')
  expect(screen.getByLabelText('선택한 가상 주문')).toHaveTextContent('10,000원')
  tapCard()
  expect(screen.getByRole('button', { name: '가상 주문서 결제' })).toBeEnabled()
  expect(fetchMock).not.toHaveBeenCalled()
})

it('주문과 인증이 없으면 차단하고 목 주문을 불러와도 실제 네트워크 요청을 보내지 않는다', () => {
  const fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
  const gateway = createMockPaymentGateway(0)
  const approve = vi.spyOn(gateway, 'approve')
  render(<App mockAuth paymentGateway={gateway} />)
  tapCard()
  expect(screen.getByLabelText('남은 활성 시간')).toHaveTextContent('60초')
  expect(document.querySelector('.payment-auth-controls')).toBeNull()
  expect(screen.getByRole('button', { name: '가상 주문서 결제' })).toBeDisabled()
  expect(screen.queryByText(/개발용 설정/)).not.toBeInTheDocument()
  expect(fetchMock).not.toHaveBeenCalled()
  expect(approve).not.toHaveBeenCalled()
})

it.each<AuthSimulation>(['AUTH_FAILED', 'AUTH_CANCELED', 'SETUP_REQUIRED', 'NO_RESPONSE'])(
  '인증 결과 %s는 결제 가능 상태가 아니다', state => {
    const { result } = renderHook(() => useMockAuth(true))
    act(() => result.current.request(state))
    expect(result.current.active).toBe(false)
    expect(result.current.paymentReady).toBe(false)
  },
)

it('단조 시계로 만료·재인증·이탈을 처리하고 복귀에서 자동 활성화하지 않는다', () => {
  vi.useFakeTimers()
  let now = 0
  const { result } = renderHook(() => useMockAuth(true, () => now))
  act(() => result.current.request())
  expect(result.current.remainingMs).toBe(60000)
  now = 60000
  act(() => vi.advanceTimersByTime(200))
  expect(result.current.reason).toBe('EXPIRED')
  act(() => result.current.checkPayment())
  expect(result.current.paymentReady).toBe(false)
  act(() => result.current.request())
  expect(result.current.remainingMs).toBe(60000)
  act(() => window.dispatchEvent(new Event('pagehide')))
  act(() => window.dispatchEvent(new Event('pageshow')))
  expect(result.current.reason).toBe('LEFT_APP')
  expect(result.current.active).toBe(false)
})

it('StrictMode와 연속 클릭에서도 한 번 승인하고, 대기 중 만료돼도 성공 결과를 표시한다', async () => {
  vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'performance'] })
  const mock = createMockPaymentGateway(0)
  let resolve!: (value: Approval) => void
  const approve = vi.fn<(_attempt: Attempt) => Promise<Approval>>(() => new Promise<Approval>(done => { resolve = done }))
  render(<StrictMode><App mockAuth paymentGateway={{ ...mock, approve }} /></StrictMode>)
  prepare()
  const submit = screen.getByRole('button', { name: '가상 주문서 결제' })
  act(() => { submit.click(); submit.click() })
  expect(approve).toHaveBeenCalledTimes(1)
  expect(screen.getByRole('button', { name: '처리 중' })).toBeDisabled()
  act(() => vi.advanceTimersByTime(61000))
  const actual = approve.mock.lastCall?.[0]
  if (!actual) throw new Error('Missing attempt')
  const result = await mock.approve(actual)
  await act(async () => resolve(result))
  expect(screen.getByText('모의 승인 완료')).toBeVisible()
  click('주문 목록으로')
  click(/스타벅스 송도점/)
  expect(screen.getByText('개발용 모의 승인됨')).toBeVisible()
  click('빠른결제')
  fireEvent.click(screen.getByRole('tab', { name: '가상 포트폴리오' }))
  expect(screen.queryByLabelText('선택한 가상 주문')).not.toBeInTheDocument()
  click('모의결제')
  expect(screen.getByText('이번 실행에서 생성한 주문이 없어요.')).toBeVisible()
})

it.each([
  ['unknown', '확인 필요'], ['zero', '0원'], ['fixed', '대표 카드 고정'],
  ['conditional', '조건부 추천'], ['changed', '승인 직전 재계산 결과가 달라졌어요.'],
  ['unchanged', '이전 추천과 동일해요.'], ['approved', '이전 추천과 비교하지 않았어요.'],
] as const)('결과 화면은 %s 값을 그대로 구분한다', async (value, label) => {
  const gateway = createMockPaymentGateway(0)
  selectScenario(gateway, () => value)
  render(<App mockAuth paymentGateway={gateway} />)
  prepare()
  click('가상 주문서 결제')
  expect(await screen.findByText('모의 승인 완료')).toBeVisible()
  if (['changed', 'unchanged', 'approved'].includes(value)) fireEvent.click(screen.getByText('승인 정보 · 재계산 내역'))
  expect(screen.getByText(label)).toBeVisible()
})

it('FAILED와 승인 성공 뒤 비교 조회 오류의 경계를 유지한다', async () => {
  const gateway = createMockPaymentGateway(0)
  let activeScenario: Scenario = 'failed'
  const approve = selectScenario(gateway, () => activeScenario)
  render(<App mockAuth paymentGateway={gateway} />)
  prepare()
  click('가상 주문서 결제')
  expect(await screen.findByText('모의 승인 실패')).toBeVisible()
  click('주문 목록으로')
  click(/스타벅스 송도점/)
  expect(screen.getByRole('button', { name: '빠른결제로 이동' })).toBeEnabled()
  click('빠른결제로 이동')
  tapCard()
  activeScenario = 'comparisonError'
  click('가상 주문서 결제')
  expect(await screen.findByText('모의 승인 완료')).toBeVisible()
  fireEvent.click(screen.getByText('선택 이유 · 카드비교'))
  fireEvent.click(await screen.findByRole('button', { name: '비교 다시 조회' }))
  expect(await screen.findByText('비교 정보를 불러오지 못했어요.')).toBeVisible()
  expect(screen.getByText('모의 승인 완료')).toBeVisible()
  expect(approve).toHaveBeenCalledTimes(2)
})

it('응답 유실 후 화면의 결과 다시 확인은 동일한 시도만 재전송한다', async () => {
  const gateway = createMockPaymentGateway(0)
  const approve = selectScenario(gateway, () => 'lost')
  render(<App mockAuth paymentGateway={gateway} />)
  prepare()
  click('가상 주문서 결제')
  expect(await screen.findByText('승인 결과 미확인')).toBeVisible()
  click('결과 다시 확인')
  expect(await screen.findByText('모의 승인 완료')).toBeVisible()
  expect(approve.mock.calls[0][0]).toBe(approve.mock.calls[1][0])
})

it('목 저장소는 키 재생·충돌·모드 분리·보존 기간을 적용한다', async () => {
  let now = 0
  const gateway = createMockPaymentGateway(0, () => now)
  const lost: Attempt = { ...attempt, scenario: 'lost' }
  await expect(gateway.approve(lost)).rejects.toMatchObject({ uncertain: true })
  const original = await gateway.approve(lost)
  original.currentOrderBenefit.amountWon = 123
  expect((await gateway.approve(lost)).currentOrderBenefit.amountWon).toBe(700)
  await expect(gateway.approve({ ...lost, body: { simulateFailure: true } }))
    .rejects.toMatchObject({ status: 409, code: 'IDEMPOTENCY_KEY_REUSED' })
  await expect(gateway.approve({ ...attempt, key: 'other' })).rejects.toMatchObject({ code: 'ORDER_ALREADY_PAID' })
  const other = await gateway.approve({ ...attempt, mode: 'VIRTUAL_PORTFOLIO' })
  expect(other.simulatedPaymentId).not.toBe(original.simulatedPaymentId)
  await expect(gateway.detail('VIRTUAL_PORTFOLIO', original.simulatedPaymentId))
    .rejects.toMatchObject({ code: 'SIMULATED_PAYMENT_NOT_FOUND' })
  now += retryWindow
  await expect(gateway.approve(lost)).rejects.toMatchObject({ code: 'ORDER_ALREADY_PAID' })
})

it('확정 FAILED는 같은 키로 재생되고 새 키로 다른 결과를 받을 수 있다', async () => {
  const gateway = createMockPaymentGateway(0)
  const failed = await gateway.approve({ ...attempt, scenario: 'failed' })
  expect(failed).toMatchObject({ status: 'FAILED', ledgerTransactionId: null, cancellableAmountWon: 0 })
  expect(await gateway.approve(attempt)).toEqual(failed)
  expect((await gateway.approve({ ...attempt, key: 'fresh' })).status).toBe('APPROVED')
})

it('서버 승인에는 계약 헤더와 본문만 보내며 대표 카드의 null 선택 근거를 허용한다', async () => {
  const result = await createMockPaymentGateway(0).approve({ ...attempt, scenario: 'fixed' })
  const fetchMock = vi.fn()
    .mockResolvedValueOnce(csrf())
    .mockResolvedValueOnce(Response.json({ data: result }, { status: 201 }))
  vi.stubGlobal('fetch', fetchMock)
  expect(await apiPaymentGateway.approve({ ...attempt, selection: { ...attempt.selection, source: 'server' } }))
    .toEqual(expect.objectContaining({ selectionBasis: null }))
  // 회원 세션 계약: 쿠키 포함, CSRF 토큰과 Idempotency-Key, 본문 {}(simulateFailure 미전송)
  expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/auth/csrf', expect.objectContaining({ credentials: 'include' }))
  expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/me/orders/ord-5001/simulated-payments',
    expect.objectContaining({ method: 'POST', credentials: 'include', body: '{}', headers: {
      'Content-Type': 'application/json', 'Idempotency-Key': 'test-key', 'X-BeONE-Demo-User': 'MY_CARDS',
      'X-CSRF-TOKEN': 'csrf-1',
    } }))
  await expect(apiPaymentGateway.approve(attempt)).rejects.toMatchObject({ code: 'MOCK_ORDER' })
  expect(fetchMock).toHaveBeenCalledTimes(2)
})

it('잘못된 201 승인 응답은 확정 실패 대신 결과 미확인으로 처리한다', async () => {
  const result = await createMockPaymentGateway(0).approve(attempt)
  vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(csrf())
    .mockResolvedValueOnce(Response.json({ data: { ...result, orderId: 'wrong' } }, { status: 201 })))
  await expect(apiPaymentGateway.approve({ ...attempt, selection: { ...attempt.selection, source: 'server' } }))
    .rejects.toMatchObject({ uncertain: true, status: 201 })
})

it('CSRF 토큰을 받지 못하면 승인을 보내지 않고 결과 미확인으로 두지 않는다', async () => {
  const fetchMock = vi.fn().mockRejectedValueOnce(new TypeError('offline'))
  vi.stubGlobal('fetch', fetchMock)
  await expect(apiPaymentGateway.approve({ ...attempt, selection: { ...attempt.selection, source: 'server' } }))
    .rejects.toMatchObject({ code: 'CSRF_UNAVAILABLE', uncertain: false })
  expect(fetchMock).toHaveBeenCalledTimes(1)
})

it.each([
  [401, 'UNAUTHENTICATED', 1],
  [403, 'CSRF_INVALID', 0],
  [405, 'METHOD_NOT_ALLOWED', 0],
])('승인 %i %s는 저장되지 않은 확정 오류이고 401만 로그인 화면 전환을 알린다', async (status, code, notified) => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(csrf())
    .mockResolvedValueOnce(Response.json({ code, message: 'x', errors: [] }, { status })))
  const signedOut = vi.fn()
  window.addEventListener('beone:unauthenticated', signedOut)
  await expect(apiPaymentGateway.approve({ ...attempt, selection: { ...attempt.selection, source: 'server' } }))
    .rejects.toMatchObject({ code, status, uncertain: false })
  window.removeEventListener('beone:unauthenticated', signedOut)
  expect(signedOut).toHaveBeenCalledTimes(notified)
})
