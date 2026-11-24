import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { notifyUnauthenticated } from '../api/client'
import { AuthEntry } from './AuthEntry'
import { AuthForm } from './AuthForm'

const fetchMock = vi.fn<typeof fetch>()
const member = { userId: 'usr-0123456789', email: 'test+tag@example.com' }
const date = '2026-11-23T10:00:00+09:00'
const accepted = '처리 가능한 요청이면 이메일로 안내합니다.'

function success(data: unknown, status = 200) {
  return Response.json({ data }, { status })
}

function failure(
  status: number, code: string,
  errors: Array<{ field: string | null; message: string }> = [],
  headers?: HeadersInit,
) {
  return Response.json({ code, message: '서버 오류 문구', errors }, { status, headers })
}

function csrf(token = 'csrf-token') {
  return success({ headerName: 'X-CSRF-TOKEN', token })
}

function mount() {
  render(<AuthEntry><p>메인화면</p></AuthEntry>)
}

function fill(label: string, value: string) {
  fireEvent.change(screen.getByLabelText(label, { exact: true }), { target: { value } })
}

async function enterLogin() {
  mount()
  await waitFor(() => expect(screen.getByRole('button', { name: '로그인' })).toBeEnabled())
  fill('이메일', ' TEST+tag@EXAMPLE.COM ')
  fill('비밀번호', ' a ')
}

async function enterSignUp(password: string, confirmation = password) {
  await enterLogin()
  fireEvent.click(screen.getByRole('button', { name: '회원가입' }))
  fill('비밀번호', password)
  fill('비밀번호 확인', confirmation)
}

function submit(name = '로그인') {
  fireEvent.click(screen.getByRole('button', { name }))
}

beforeEach(() => {
  fetchMock.mockReset()
  fetchMock.mockResolvedValueOnce(failure(401, 'UNAUTHENTICATED'))
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => vi.unstubAllGlobals())

describe('회원 인증 진입', () => {
  it('개발용 로그인에서 입력값이 있으면 서버 호출 없이 메인 진입을 요청한다', () => {
    const onLogin = vi.fn()
    render(<AuthForm resetToken={null} mockLogin onLogin={onLogin} />)
    submit()
    expect(onLogin).not.toHaveBeenCalled()

    fill('이메일', 'test')
    fill('비밀번호', '1234')
    submit()
    expect(onLogin).toHaveBeenCalledOnce()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('현재 서버 세션이 유효하면 메인화면으로 이동한다', async () => {
    fetchMock.mockReset().mockResolvedValueOnce(success({
      ...member, authenticatedAt: date, expiresAt: date, absoluteExpiresAt: date,
    }))
    mount()
    expect(await screen.findByText('메인화면')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/auth/session', expect.objectContaining({
      credentials: 'include',
    }))
  })

  it('세션 조회 통신 실패는 재시도로 복구할 수 있다', async () => {
    fetchMock.mockReset()
      .mockRejectedValueOnce(new TypeError())
      .mockResolvedValueOnce(failure(401, 'UNAUTHENTICATED'))
    mount()
    expect(await screen.findByText('로그인 상태를 확인하지 못했어요.')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '로그인' })).toBeInTheDocument()
    submit('회원가입')
    expect(screen.getByRole('heading', { name: '회원가입' })).toBeInTheDocument()
    submit('다시 시도')
    await waitFor(() => expect(screen.getByRole('button', { name: '이메일 인증하고 가입하기' })).toBeEnabled())
    expect(screen.queryByText('로그인 상태를 확인하지 못했어요.')).not.toBeInTheDocument()
  })

  it('쿠키·CSRF와 함께 이메일을 정규화하고 짧은 로그인 비밀번호 원문을 전송한다', async () => {
    fetchMock.mockResolvedValueOnce(csrf()).mockResolvedValueOnce(success(member))
    await enterLogin()
    submit()
    expect(await screen.findByText('메인화면')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/auth/csrf', expect.objectContaining({
      credentials: 'include',
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/auth/login', expect.objectContaining({
      credentials: 'include',
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': 'csrf-token' },
      body: JSON.stringify({ email: member.email, password: ' a ' }),
    }))
  })

  it('잘못된 이메일과 254자를 초과한 이메일을 전송 전에 검증한다', async () => {
    await enterLogin()
    for (const value of ['example', `${'a'.repeat(245)}@example.com`]) {
      fill('이메일', value)
      submit()
      expect(screen.getByRole('alert')).toHaveTextContent('이메일 주소를 확인해 주세요.')
      expect(fetchMock).toHaveBeenCalledTimes(1)
    }
  })

  it.each([11, 32])('가입 비밀번호 %i자를 허용하고 확인 필드는 전송하지 않는다', async (length) => {
    fetchMock.mockResolvedValueOnce(csrf()).mockResolvedValueOnce(success({ message: accepted }, 202))
    const password = 'a1!' + 'a'.repeat(length - 3)
    await enterSignUp(password)
    submit('이메일 인증하고 가입하기')
    expect(await screen.findByText(/처리 가능한 요청이면 이메일로 안내합니다/)).toBeInTheDocument()
    expect(screen.queryByText('메인화면')).not.toBeInTheDocument()
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/auth/sign-up', expect.objectContaining({
      body: JSON.stringify({ email: member.email, password }),
    }))
    expect(screen.getByLabelText('비밀번호', { exact: true })).toHaveValue('')
  })

  it.each(['a1!aaaaaaa', 'a1!' + 'a'.repeat(30), 'a1!abcdefgh ', 'a1!abcdefgh한', 'a1234567890'])(
    '가입 조건과 다른 비밀번호를 거부한다 (%s)', async (password) => {
      await enterSignUp(password)
      submit('이메일 인증하고 가입하기')
      expect(screen.getByRole('alert')).toHaveTextContent('11~32자')
      expect(fetchMock).toHaveBeenCalledTimes(1)
    },
  )

  it('확인 불일치를 검증하고 로그인으로 전환할 때 비밀번호를 비운다', async () => {
    await enterSignUp('a1!abcdefgh', 'different')
    submit('이메일 인증하고 가입하기')
    expect(screen.getByRole('alert')).toHaveTextContent('비밀번호가 일치하지 않아요.')
    expect(fetchMock).toHaveBeenCalledTimes(1)
    submit('로그인으로 돌아가기')
    expect(screen.getByRole('heading', { name: '로그인' })).toHaveFocus()
    expect(screen.getByLabelText('비밀번호', { exact: true })).toHaveValue('')
  })

  it('400의 필드 상세와 null 상세를 각각 표시하고 편집할 때 지운다', async () => {
    fetchMock.mockResolvedValueOnce(csrf()).mockResolvedValueOnce(failure(400, 'INVALID_REQUEST', [
      { field: 'email', message: '이메일 상세' },
      { field: null, message: '공통 상세' },
    ]))
    await enterLogin()
    submit()
    expect(await screen.findByText('이메일 상세')).toBeInTheDocument()
    expect(screen.getByText('요청 값을 확인해 주세요. 공통 상세')).toBeInTheDocument()
    expect(screen.getByLabelText('이메일')).toHaveAccessibleDescription('이메일 상세')
    fill('이메일', 'second@example.com')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it.each([
    [401, 'INVALID_CREDENTIALS', '이메일 또는 비밀번호를 확인해 주세요.'],
    [403, 'EMAIL_NOT_VERIFIED', '이메일 인증을 완료해 주세요.'],
    [403, 'CSRF_INVALID', '화면을 새로고침한 후 다시 시도해 주세요.'],
    [429, 'RATE_LIMITED', '17초 후 재시도'],
    [500, 'INTERNAL_SERVER_ERROR', '서버 오류가 발생했어요.'],
  ])('오류 %i %s를 구분한다', async (status, code, message) => {
    fetchMock.mockResolvedValueOnce(csrf())
      .mockResolvedValueOnce(failure(status, code, [], { 'Retry-After': '17' }))
    await enterLogin()
    submit()
    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(screen.queryByText('메인화면')).not.toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(3)
  })

  it.each([
    [new TypeError('offline'), '서버에 연결할 수 없어요.'],
    [new DOMException('expired', 'TimeoutError'), '요청 시간이 초과됐어요.'],
  ])('요청 오류 %s를 자격 증명 실패와 구분한다', async (error, message) => {
    fetchMock.mockResolvedValueOnce(csrf()).mockRejectedValueOnce(error)
    await enterLogin()
    submit()
    expect(await screen.findByRole('alert')).toHaveTextContent(message)
  })

  it('CSRF 실패 후 명시적으로 재시도할 때만 새 토큰을 받아 POST한다', async () => {
    fetchMock.mockResolvedValueOnce(csrf('old'))
      .mockResolvedValueOnce(failure(403, 'CSRF_INVALID'))
      .mockResolvedValueOnce(csrf('fresh'))
      .mockResolvedValueOnce(success(member))
    await enterLogin()
    submit()
    expect(await screen.findByRole('alert')).toHaveTextContent('새로고침')
    expect(fetchMock).toHaveBeenCalledTimes(3)

    submit()
    expect(await screen.findByText('메인화면')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenNthCalledWith(5, '/api/v1/auth/login', expect.objectContaining({
      headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': 'fresh' },
    }))
  })

  it('응답 대기 중 폼 재제출과 화면 전환을 막는다', async () => {
    let finish: (response: Response) => void = () => {}
    fetchMock.mockResolvedValueOnce(csrf()).mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
    await enterLogin()
    const form = screen.getByRole('button', { name: '로그인' }).closest('form')!
    fireEvent.submit(form)
    fireEvent.submit(form)
    expect(screen.getByRole('button', { name: '요청 중…' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '회원가입' })).toBeDisabled()
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3))
    finish(success(member))
    expect(await screen.findByText('메인화면')).toBeInTheDocument()
    expect(localStorage.length).toBe(0)
    expect(sessionStorage.length).toBe(0)
  })
})

describe('업무 API 401', () => {
  it('세션이 끝났다는 알림을 받으면 메인화면을 버리고 로그인 화면으로 돌아간다', async () => {
    fetchMock.mockReset()
    fetchMock.mockResolvedValueOnce(success({ ...member, authenticatedAt: date, expiresAt: date, absoluteExpiresAt: date }))
    mount()
    expect(await screen.findByText('메인화면')).toBeInTheDocument()
    act(() => notifyUnauthenticated())
    expect(screen.queryByText('메인화면')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '로그인' })).toBeInTheDocument()
  })
})

describe('비밀번호 재설정과 빈 로그인 입력', () => {
  it('빈 로그인 필드 두 개를 오류로 표시하고 편집한 필드만 해제한다', async () => {
    mount()
    await screen.findByRole('heading', { name: '로그인' })
    submit()

    expect(screen.getByLabelText('이메일')).toHaveAttribute('data-field-error', 'true')
    expect(screen.getByLabelText('비밀번호')).toHaveAttribute('data-field-error', 'true')
    fill('이메일', 'reset@example.com')
    expect(screen.getByLabelText('이메일')).not.toHaveAttribute('data-field-error')
    expect(screen.getByLabelText('비밀번호')).toHaveAttribute('data-field-error', 'true')
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('재설정 메일 요청에는 이메일만 보내고 변경 완료로 표시하지 않는다', async () => {
    fetchMock.mockResolvedValueOnce(csrf()).mockResolvedValueOnce(success({ message: accepted }, 202))
    await enterLogin()
    submit('비밀번호를 잊으셨나요?')
    expect(screen.getByRole('heading', { name: '비밀번호 재설정' })).toBeInTheDocument()
    fill('비밀번호', 'a1!abcdefgh')
    fill('비밀번호 확인', 'a1!abcdefgh')
    submit('이메일 인증하기')

    expect(await screen.findByText(/메일의 링크에서 새 비밀번호를 입력해 주세요/)).toBeInTheDocument()
    expect(screen.queryByText(/비밀번호를 변경했어요/)).not.toBeInTheDocument()
    expect(screen.getByLabelText('비밀번호', { exact: true })).toHaveValue('')
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/auth/password-reset-requests', expect.objectContaining({
      body: JSON.stringify({ email: member.email }),
    }))
  })

  it('메일 링크의 토큰과 새 비밀번호로 변경하고 204 이후 로그인 화면으로 돌아온다', async () => {
    const token = 'a'.repeat(43)
    history.replaceState(null, '', `/password-reset?token=${token}`)
    fetchMock.mockReset().mockResolvedValueOnce(csrf()).mockResolvedValueOnce(new Response(null, { status: 204 }))
    mount()
    expect(location.search).toBe('')
    expect(screen.queryByLabelText('이메일')).not.toBeInTheDocument()
    fill('비밀번호', 'a1!abcdefgh')
    fill('비밀번호 확인', 'a1!abcdefgh')
    submit('비밀번호 변경하기')

    expect(await screen.findByText('비밀번호를 변경했어요. 새 비밀번호로 로그인해 주세요.')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '로그인' })).toBeInTheDocument()
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/auth/password-resets', expect.objectContaining({
      body: JSON.stringify({ token, newPassword: 'a1!abcdefgh' }),
    }))
  })

  it('만료된 토큰은 재설정 메일을 다시 요청할 수 있게 안내한다', async () => {
    history.replaceState(null, '', `/password-reset?token=${'a'.repeat(43)}`)
    fetchMock.mockReset().mockResolvedValueOnce(csrf()).mockResolvedValueOnce(failure(400, 'AUTH_TOKEN_INVALID'))
    mount()
    fill('비밀번호', 'a1!abcdefgh')
    fill('비밀번호 확인', 'a1!abcdefgh')
    submit('비밀번호 변경하기')
    expect(await screen.findByRole('alert')).toHaveTextContent('링크가 유효하지 않거나 만료되었습니다.')
    submit('재설정 메일 다시 요청')
    expect(screen.getByLabelText('이메일')).toBeInTheDocument()
  })
})
