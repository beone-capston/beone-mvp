import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
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
    let active = true

    authApi.session().then(
      () => { if (active) setState('signed-in') },
      (error: unknown) => {
        if (!active) return
        const signedOut = error instanceof AuthApiError &&
          error.status === 401 && error.code === 'UNAUTHENTICATED'
        setState(signedOut ? 'signed-out' : 'unavailable')
      },
    )

    return () => { active = false }
  }, [attempt, mockLogin, resetToken])

  if (state === 'signed-in') return children

  return (
    <main className="auth-shell">
      {state === 'checking' ? (
        <div className="auth-frame auth-session">
          <p role="status">로그인 상태를 확인하고 있어요.</p>
        </div>
      ) : (
        <AuthForm
          mockLogin={mockLogin}
          resetToken={resetToken}
          onLogin={() => setState('signed-in')}
          onRetrySession={state === 'unavailable' ? () => {
            setState('checking')
            setAttempt(value => value + 1)
          } : undefined}
        />
      )}
    </main>
  )
}
