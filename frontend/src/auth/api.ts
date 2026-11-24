type ApiResponse<T> = { data: T }

type ApiErrorResponse = {
  code: string
  message: string
  errors: Array<{ field: string | null; message: string }>
}

export type Credentials = { email: string; password: string }
type Member = { userId: string; email: string }
type MemberSession = Member & {
  authenticatedAt: string
  expiresAt: string
  absoluteExpiresAt: string
}
type Csrf = { headerName: string; token: string }
type MailAcceptance = { message: string }

export class AuthApiError extends Error {
  code: string
  status: number
  fields: ApiErrorResponse['errors']
  retryAfter: number | null

  constructor(status: number, body: ApiErrorResponse | null, retryAfter: string | null) {
    super(body?.message ?? '인증 서버 응답을 확인할 수 없습니다.')
    this.code = body?.code ?? ''
    this.status = status
    this.fields = Array.isArray(body?.errors) ? body.errors : []
    this.retryAfter = retryAfter && /^\d+$/.test(retryAfter) ? Number(retryAfter) : null
  }
}

// Authentication defaults to the page origin so the browser can send the session cookie.
const baseUrl = (import.meta.env.VITE_AUTH_API_BASE_URL ?? '').replace(/\/+$/, '')

async function request<T>(path: string, options: RequestInit = {}, expectedStatus?: number): Promise<T> {
  const timeout = AbortSignal.timeout(15000)
  const response = await fetch(`${baseUrl}/api/v1/auth/${path}`, {
    ...options,
    credentials: 'include',
    cache: 'no-store',
    signal: options.signal ? AbortSignal.any([options.signal, timeout]) : timeout,
  })

  if (!response.ok) {
    const body = await response.json().catch(() => null) as ApiErrorResponse | null
    throw new AuthApiError(response.status, body, response.headers.get('Retry-After'))
  }

  if (expectedStatus !== undefined && response.status !== expectedStatus) {
    throw new AuthApiError(response.status, null, null)
  }
  if (response.status === 204) return undefined as T

  const body = await response.json() as ApiResponse<T> | null
  if (!body || typeof body !== 'object' || !('data' in body) || body.data == null) {
    throw new AuthApiError(response.status, null, null)
  }
  return body.data
}

async function post<T>(path: string, body: Credentials | { email: string } | { token: string } | { token: string; newPassword: string }, expectedStatus?: number): Promise<T> {
  // Get a token for the current cookie before each explicit attempt. Never replay a POST.
  const csrf = await request<Csrf>('csrf')
  if (typeof csrf.headerName !== 'string' || !csrf.headerName || typeof csrf.token !== 'string' || !csrf.token) {
    throw new AuthApiError(200, null, null)
  }
  return request<T>(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
    body: JSON.stringify(body),
  }, expectedStatus)
}

function checkMember(member: Member) {
  if (typeof member.userId !== 'string' || !member.userId || typeof member.email !== 'string') {
    throw new AuthApiError(200, null, null)
  }
}

function checkAcceptance(result: MailAcceptance) {
  if (typeof result.message !== 'string' || !result.message) throw new AuthApiError(202, null, null)
  return result
}

export const authApi = {
  async session(signal?: AbortSignal) {
    const session = await request<MemberSession>('session', { signal })
    checkMember(session)
    if ([session.authenticatedAt, session.expiresAt, session.absoluteExpiresAt].some(value => typeof value !== 'string')) {
      throw new AuthApiError(200, null, null)
    }
    return session
  },
  async login(credentials: Credentials) {
    const member = await post<Member>('login', credentials)
    checkMember(member)
    return member
  },
  async signUp(credentials: Credentials) {
    return checkAcceptance(await post<MailAcceptance>('sign-up', credentials, 202))
  },
  async requestPasswordReset(email: string) {
    return checkAcceptance(await post<MailAcceptance>('password-reset-requests', { email }, 202))
  },
  async resetPassword(token: string, newPassword: string) {
    await post<void>('password-resets', { token, newPassword }, 204)
  },
  // 메일 링크를 연 것만으로는 인증하지 않는다. 화면에서 확인한 뒤 호출한다(docs/api/auth.md)
  async verifyEmail(token: string) {
    await post<void>('email-verifications', { token }, 204)
  },
  async requestEmailVerification(email: string) {
    return checkAcceptance(await post<MailAcceptance>('email-verification-requests', { email }, 202))
  },
}
