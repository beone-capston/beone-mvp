import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { onUnauthenticated } from '../api/client'
import { AuthApiError, authApi } from './api'
import { AuthForm } from './AuthForm'
import './auth.css'

type EntryState = 'checking' | 'unavailable' | 'signed-out' | 'signed-in'

export function AuthEntry({ children }: { children: ReactNode }) {
  const [resetToken] = useState(() => window.location.pathname === '/password-reset'
    ? new URLSearchParams(window.location.search).get('token') ?? ''
    : null)
  const mockLogin = import.meta.env.DEV && resetToken === null &&
    ['localhost', '127.0.0.1', '[::1]'].includes(window.location.hostname) &&
    window.location.port === '5173'
  const [state, setState] = useState<EntryState>(resetToken === null && !mockLogin ? 'checking' : 'signed-out')
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    if (resetToken !== null) {
      window.history.replaceState(window.history.state, '', '/')
      return
    }
    if (mockLogin) return
    const controller = new AbortController()
    // StrictMode cleans up the rehearsal effect before its microtask runs.
    queueMicrotask(() => {
      if (controller.signal.aborted) return
      authApi.session(controller.signal).then(
        () => { if (!controller.signal.aborted) setState('signed-in') },
        (error: unknown) => {
          if (controller.signal.aborted) return
          const signedOut = error instanceof AuthApiError &&
            error.status === 401 && error.code === 'UNAUTHENTICATED'
          setState(signedOut ? 'signed-out' : 'unavailable')
        },
      )
    })
    return () => controller.abort()
  }, [attempt, mockLogin, resetToken])

  // 업무 API가 401을 받으면 진행 중 화면을 버리고 로그인 화면으로 돌아간다
  useEffect(() => onUnauthenticated(() => setState('signed-out')), [])

  if (state === 'signed-in') return children

  return (
    <main className="auth-shell">
      <AuthForm
        mockLogin={mockLogin}
        resetToken={resetToken}
        sessionChecking={state === 'checking'}
        onLogin={() => setState('signed-in')}
        onRetrySession={state === 'unavailable' ? () => {
          setState('checking')
          setAttempt(value => value + 1)
        } : undefined}
      />
    </main>
  )
}
