// 업무 API 공통 규칙: 같은 origin 상대 경로, 세션 쿠키 전송, 쓰기 전 CSRF 토큰, 401 알림
// 근거: docs/api.md 웹 클라이언트 호출 규칙, docs/api/auth.md CSRF

// 기본값은 같은 origin('')이다. 개발 서버는 vite.config.ts의 /api 프록시로 백엔드에 넘긴다.
export const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/+$/, '')

export type CsrfHeader = Record<string, string>

// 쓰기 요청마다 현재 쿠키에 맞는 토큰을 새로 받는다. 저장하지 않으므로 로그인·로그아웃 뒤에도 갱신된다.
// 받지 못하면 null을 돌려주고, 호출한 쪽은 쓰기 요청을 보내지 않는다.
export async function fetchCsrfHeader(): Promise<CsrfHeader | null> {
  try {
    const response = await fetch(apiBaseUrl + '/api/v1/auth/csrf', {
      method: 'GET',
      credentials: 'include',
      cache: 'no-store',
      signal: AbortSignal.timeout(15000),
    })
    if (response.status === 401) notifyUnauthenticated()
    if (!response.ok) return null
    const body: unknown = await response.json().catch(() => null)
    const data = body && typeof body === 'object' ? (body as { data?: unknown }).data : null
    if (!data || typeof data !== 'object') return null
    const { headerName, token } = data as { headerName?: unknown; token?: unknown }
    if (typeof headerName !== 'string' || !headerName || typeof token !== 'string' || !token) return null
    return { [headerName]: token }
  } catch {
    return null
  }
}

// 공통 오류 분류. message가 아니라 HTTP 상태와 code로 나눈다(docs/api.md).
export type CommonFailure = 'unauthenticated' | 'csrf' | 'unavailable'

export function commonFailure(status: number, code: unknown): CommonFailure | null {
  if (status === 401 && code === 'UNAUTHENTICATED') return 'unauthenticated'
  if (status === 403 && code === 'CSRF_INVALID') return 'csrf'
  // 등록하지 않은 경로·허용하지 않은 메서드는 로그인과 무관한 개발 오류다
  if ((status === 404 && code === 'NOT_FOUND') || (status === 405 && code === 'METHOD_NOT_ALLOWED')) return 'unavailable'
  return null
}

// 세션이 끝났음을 화면 진입점(AuthEntry)에 알린다
const unauthenticatedEvent = 'beone:unauthenticated'

export function notifyUnauthenticated() {
  window.dispatchEvent(new Event(unauthenticatedEvent))
}

export function onUnauthenticated(listener: () => void) {
  window.addEventListener(unauthenticatedEvent, listener)
  return () => window.removeEventListener(unauthenticatedEvent, listener)
}
