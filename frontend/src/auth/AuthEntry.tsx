import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { AuthApiError, authApi } from './api'
import { LocalPreview, MemberIdentity, MemberLogout } from './memberIdentity'
import { AuthForm } from './AuthForm'
import { EmailVerification, emailVerificationPath } from './EmailVerification'
import type { MailDeadlines } from './mailRequests'
import './auth.css'

type EntryState = 'checking' | 'unavailable' | 'signed-out' | 'signed-in'

export function AuthEntry({ children }: { children: ReactNode }) {
  const [enteredVerification] = useState(() => window.location.pathname === emailVerificationPath)
  const [showVerification, setShowVerification] = useState(enteredVerification)
  const [deadlines] = useState<MailDeadlines>(() => new Map())
  const [resetToken] = useState(() => window.location.pathname === '/password-reset'
    ? new URLSearchParams(window.location.search).get('token') ?? ''
    : null)
  const [state, setState] = useState<EntryState>(!enteredVerification && resetToken === null ? 'checking' : 'signed-out')
  const [preview, setPreview] = useState(false)
  const [memberId, setMemberId] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    if (enteredVerification) return
    if (resetToken !== null) {
      window.history.replaceState(window.history.state, '', '/')
      return
    }
    const controller = new AbortController()
    // StrictMode cleans up the rehearsal effect before its microtask runs.
    queueMicrotask(() => {
      if (controller.signal.aborted) return
      authApi.session(controller.signal).then(
        member => {
          if (!controller.signal.aborted) {
            setMemberId(member.userId)
            setState('signed-in')
          }
        },
        (error: unknown) => {
          if (controller.signal.aborted) return
          const signedOut = error instanceof AuthApiError &&
            error.status === 401 && error.code === 'UNAUTHENTICATED'
          setState(signedOut ? 'signed-out' : 'unavailable')
        },
      )
    })
    return () => controller.abort()
  }, [attempt, enteredVerification, resetToken])

  if (showVerification) return (
    <main className="auth-shell">
      <EmailVerification deadlines={deadlines} onReturn={() => {
        window.history.replaceState(window.history.state, '', '/')
        setShowVerification(false)
      }} />
    </main>
  )

  if (state === 'signed-in') return <MemberIdentity.Provider value={memberId}>
    <MemberLogout.Provider value={async () => {
      if (!preview) await authApi.logout()
      setPreview(false)
      setMemberId(null)
      setState('signed-out')
    }}><LocalPreview.Provider value={preview}>{children}</LocalPreview.Provider></MemberLogout.Provider>
  </MemberIdentity.Provider>

  return (
    <main className="auth-shell">
      <AuthForm
        mailDeadlines={deadlines}
        resetToken={resetToken}
        sessionChecking={state === 'checking'}
        onLogin={(userId, local = false) => { setPreview(local); setMemberId(userId); setState('signed-in') }}
        onRetrySession={state === 'unavailable' ? () => {
          setState('checking')
          setAttempt(value => value + 1)
        } : undefined}
      />
    </main>
  )
}
