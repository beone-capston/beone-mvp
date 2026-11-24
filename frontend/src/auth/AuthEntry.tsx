import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { AuthApiError, authApi } from './api'
import { AuthForm } from './AuthForm'
import { EmailVerification } from './EmailVerification'
import './auth.css'

type EntryState = 'checking' | 'unavailable' | 'signed-out' | 'signed-in'

export function AuthEntry({ children }: { children: ReactNode }) {
  const [resetToken] = useState(() => window.location.pathname === '/password-reset'
    ? new URLSearchParams(window.location.search).get('token') ?? ''
    : null)
  // 메일 링크 경로는 미정(결정 28·N2)이라 재설정 링크와 같은 형태로 둔다
  const [verifyToken, setVerifyToken] = useState(() => window.location.pathname === '/email-verification'
    ? new URLSearchParams(window.location.search).get('token') ?? ''
    : null)
  const mockLogin = import.meta.env.DEV && resetToken === null && verifyToken === null &&
    ['localhost', '127.0.0.1', '[::1]'].includes(window.location.hostname) &&
    window.location.port === '5173'
  const [state, setState] = useState<EntryState>(resetToken === null && verifyToken === null && !mockLogin ? 'checking' : 'signed-out')
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    if (resetToken !== null || verifyToken !== null) {
      // 토큰을 주소창에 남기지 않는다. 링크 화면 동안에는 세션을 조회하지 않는다
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
  }, [attempt, mockLogin, resetToken, verifyToken])

  // 이미 로그인한 상태여도 메일 링크로 들어오면 인증 화면을 먼저 보여 준다
  if (verifyToken !== null) {
    return (
      <main className="auth-shell">
        <EmailVerification token={verifyToken} onDone={() => setVerifyToken(null)} />
      </main>
    )
  }

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
