import { StrictMode } from 'react'
import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { AppStartup } from './AppStartup'

const fetchMock = vi.fn<typeof fetch>()
const signedOut = () => Response.json({ code: 'UNAUTHENTICATED', errors: [] }, { status: 401 })
const member = () => Response.json({ data: {
  userId: 'usr-1', email: 'test@example.com', authenticatedAt: '2026-11-24T00:00:00+09:00',
  expiresAt: '2026-11-24T02:00:00+09:00', absoluteExpiresAt: '2026-12-24T00:00:00+09:00',
} })

let now = 0
const clock = () => now

function mount() {
  return render(<StrictMode><AppStartup clock={clock}><button>메인 화면</button></AppStartup></StrictMode>)
}
function splash() {
  return document.querySelector('.brand-splash')
}
async function flush() {
  await act(async () => {})
}
async function advance(milliseconds: number) {
  now += milliseconds
  await act(async () => vi.advanceTimersByTime(milliseconds))
}
function pendingSession() {
  let resolve!: (value: Response) => void
  fetchMock.mockImplementationOnce(() => new Promise<Response>(done => { resolve = done }))
  return (response: Response) => act(async () => resolve(response))
}

beforeEach(() => {
  now = 0
  vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: false })))
})
afterEach(() => {
  history.replaceState(null, '', '/')
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

it('StrictMode에서 스플래시와 세션 조회를 한 번 시작하고 배경 접근을 차단한다', async () => {
  fetchMock.mockResolvedValueOnce(signedOut())
  mount()
  const first = splash()
  expect(first).toBeInTheDocument()
  expect(first?.previousElementSibling).toHaveAttribute('inert')
  expect(screen.queryByRole('heading', { name: '로그인' })).not.toBeInTheDocument()
  await flush()
  expect(fetchMock).toHaveBeenCalledExactlyOnceWith('/api/v1/auth/session',
    expect.objectContaining({ credentials: 'include', cache: 'no-store' }))
  expect(splash()).toBe(first)
  await advance(1899)
  expect(splash()).toBe(first)
  await advance(1)
  expect(splash()).not.toBeInTheDocument()
  const submit = screen.getByRole('button', { name: '로그인' })
  expect(submit).toBeEnabled()
  expect(submit.closest('[inert]')).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: '회원가입' }))
  expect(splash()).not.toBeInTheDocument()
})

it('스플래시 중 확인된 회원은 로그인 화면 노출 없이 메인으로 들어간다', async () => {
  fetchMock.mockResolvedValueOnce(member())
  mount()
  await flush()
  expect(screen.queryByRole('button', { name: '메인 화면' })).not.toBeInTheDocument()
  await advance(1900)
  expect(screen.getByRole('button', { name: '메인 화면' })).toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: '로그인' })).not.toBeInTheDocument()
})

it.each(['member', 'signed-out'])('지연된 %s 세션 응답 전후의 진입 상태를 분리한다', async kind => {
  const respond = pendingSession()
  mount()
  await advance(2000)
  expect(splash()).not.toBeInTheDocument()
  expect(screen.getByText('로그인 상태를 확인하고 있어요.')).toBeVisible()
  const submit = screen.getByRole('button', { name: '로그인' })
  expect(submit).toBeDisabled()
  fireEvent.submit(submit.closest('form')!)
  expect(fetchMock).toHaveBeenCalledTimes(1)
  expect(screen.queryByRole('button', { name: '메인 화면' })).not.toBeInTheDocument()
  await respond(kind === 'member' ? member() : signedOut())
  if (kind === 'member') expect(screen.getByRole('button', { name: '메인 화면' })).toBeVisible()
  else expect(screen.getByRole('button', { name: '로그인' })).toBeEnabled()
})

it.each(['network', 'server', 'timeout'])('%s 오류에서 세션을 다시 확인해도 스플래시를 재생하지 않는다', async kind => {
  if (kind === 'network') fetchMock.mockRejectedValueOnce(new TypeError())
  else if (kind === 'timeout') fetchMock.mockRejectedValueOnce(new DOMException('', 'TimeoutError'))
  else fetchMock.mockResolvedValueOnce(Response.json({}, { status: 500 }))
  const respond = pendingSession()
  mount()
  await advance(1900)
  expect(screen.getByText('로그인 상태를 확인하지 못했어요.')).toBeVisible()
  fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))
  await flush()
  expect(splash()).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: '로그인' })).toBeDisabled()
  await respond(signedOut())
  expect(screen.getByRole('button', { name: '로그인' })).toBeEnabled()
  expect(fetchMock).toHaveBeenCalledTimes(2)
})

it('이전 마운트의 늦은 응답이 새 세션 결과를 덮어쓰지 않는다', async () => {
  const respond = pendingSession()
  const previous = mount()
  await flush()
  const signal = fetchMock.mock.calls[0][1]?.signal
  previous.unmount()
  expect(signal?.aborted).toBe(true)
  fetchMock.mockResolvedValueOnce(signedOut())
  now = 1950
  mount()
  await flush()
  await respond(member())
  expect(screen.getByRole('button', { name: '로그인' })).toBeEnabled()
  expect(screen.queryByRole('button', { name: '메인 화면' })).not.toBeInTheDocument()
})

it('이미지 실패가 타이머 종료를 막지 않는다', async () => {
  fetchMock.mockResolvedValueOnce(signedOut())
  mount()
  fireEvent.error(document.querySelector('.brand-splash-symbol-final')!)
  expect(document.querySelector('.brand-splash-fallback')).toHaveTextContent('B:ONE')
  await advance(1900)
  expect(splash()).not.toBeInTheDocument()
})

it('모션 감소 환경은 정적 로고를 잠깐만 표시한다', async () => {
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true })))
  fetchMock.mockResolvedValueOnce(signedOut())
  mount()
  expect(splash()).toHaveAttribute('data-reduced', 'true')
  await advance(180)
  expect(splash()).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: '로그인' })).toBeEnabled()
})

it('타이머가 밀린 동안 앱에서 복귀하면 경과 시간을 기준으로 닫는다', async () => {
  fetchMock.mockResolvedValueOnce(signedOut())
  mount()
  await flush()
  now = 2800
  fireEvent(window, new Event('pageshow'))
  expect(splash()).not.toBeInTheDocument()
  fireEvent(window, new Event('focus'))
  expect(splash()).not.toBeInTheDocument()
})

it('비밀번호 재설정 딥링크에서는 스플래시를 건너뛰고 AuthEntry가 토큰을 읽는다', () => {
  history.replaceState(null, '', '/password-reset?token=' + 'Z'.repeat(43))
  mount()
  expect(splash()).not.toBeInTheDocument()
  expect(screen.getByRole('heading', { name: '비밀번호 재설정' })).toBeVisible()
  expect(screen.getByRole('button', { name: '비밀번호 변경하기' })).toBeEnabled()
  expect(fetchMock).not.toHaveBeenCalled()
})
