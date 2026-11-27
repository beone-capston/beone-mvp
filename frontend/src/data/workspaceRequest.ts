import { authBaseUrl, getCsrf } from '../auth/api'
import type { PortfolioMode } from './portfolio'

export type WorkspaceContext = 'demo' | 'member'
const configuredContext = import.meta.env.VITE_WORKSPACE_CONTEXT
export const mockWorkspace = import.meta.env.DEV && configuredContext !== 'member' &&
  (import.meta.env.VITE_PAYMENT_PREFERENCE_MODE === 'mock' ||
    import.meta.env.VITE_ADD_CARD_MODE === 'mock' || import.meta.env.VITE_PAYMENT_API_MODE === 'mock')

export const workspaceContext: WorkspaceContext = configuredContext === 'demo' || mockWorkspace ? 'demo' : 'member'

const demoBase = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/+$/, '')

export async function workspaceRequest(context: WorkspaceContext, mode: PortfolioMode, path: string, options: Omit<RequestInit, 'headers'> & { headers?: Record<string, string> } = {}) {
  const headers = { ...options.headers }
  if (context === 'member') {
    if (mode !== 'MY_CARDS') throw new Error('UNSUPPORTED_PORTFOLIO')
    if (options.method && options.method !== 'GET') {
      const csrf = await getCsrf()
      if (csrf.headerName !== 'X-CSRF-TOKEN' || !csrf.token) throw new Error('INVALID_CSRF_RESPONSE')
      headers[csrf.headerName] = csrf.token
    }
  } else {
    headers['X-BeONE-Demo-User'] = mode
  }
  const timeout = AbortSignal.timeout(15000)
  options.signal?.throwIfAborted()
  return fetch((context === 'member' ? authBaseUrl : demoBase) + '/api/v1/me/' + path, {
    ...options, headers, cache: 'no-store',
    credentials: context === 'member' ? 'include' : 'omit',
    signal: options.signal ? AbortSignal.any([options.signal, timeout]) : timeout,
  })
}
