import { useEffect, useRef, useState } from 'react'
import { AuthApiError, authApi } from './api'
import { normalizeEmail, useMailResend } from './mailRequests'
import type { MailDeadlines } from './mailRequests'

export const emailVerificationPath = '/email-verification'

const copy = {
  loading: {
    title: '이메일을 인증하고 있어요',
    description: '인증 링크를 확인하고 있어요. 잠시만 기다려 주세요.',
  },
  success: {
    title: '인증이 완료됐어요',
    description: '이제 B:ONE을 시작할 수 있어요. 가입한 이메일로 로그인해 주세요.',
  },
  invalid: {
    title: '유효하지 않은 인증 링크예요',
    description: '링크가 만료되었거나 이미 사용되었어요. 새 메일을 요청했다면 이전 링크는 사용할 수 없어요.',
  },
  missing: {
    title: '인증 링크를 확인해 주세요',
    description: '인증 정보가 없거나 올바르지 않아요. 메일에 있는 인증 링크로 다시 열어 주세요.',
  },
  network: {
    title: '인증 결과를 확인하지 못했어요',
    description: '연결이 원활하지 않아요. 인터넷 연결을 확인한 뒤 다시 시도해 주세요.',
  },
  csrf: {
    title: '요청의 보안 확인에 실패했어요',
    description: '보안 정보를 새로 받아 인증할 수 있도록 다시 시도해 주세요.',
  },
}

type VerificationState = keyof typeof copy
type RequestState = { token: string | null; pending?: Promise<VerificationState> }

function captureToken(): RequestState {
  const params = new URLSearchParams(window.location.search).getAll('token')
  return { token: params.length === 1 && /^[A-Za-z0-9_-]{43}$/.test(params[0]) ? params[0] : null }
}

async function verify(token: string): Promise<VerificationState> {
  try {
    await authApi.verifyEmail(token)
    return 'success'
  } catch (error) {
    if (error instanceof AuthApiError) {
      if (error.status === 400 && error.code === 'AUTH_TOKEN_INVALID') return 'invalid'
      if (error.status === 403 && error.code === 'CSRF_INVALID') return 'csrf'
    }
    return 'network'
  }
}

function StatusMark({ state }: { state: VerificationState }) {
  const path = state === 'success' ? 'm5 12 5 5 9-11'
    : state === 'loading' ? 'M3 6h18v12H3z M3 7l9 6 9-6'
      : state === 'invalid' ? 'M12 6v6l4 2 M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20'
        : 'M12 5v9m0 4h.01'
  return (
    <div className={`auth-verification-mark auth-verification-mark--${state}`} aria-hidden="true">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <path d={path} />
      </svg>
    </div>
  )
}

export function EmailVerification({ onReturn, deadlines }: { onReturn: () => void; deadlines: MailDeadlines }) {
  const requestRef = useRef<RequestState | null>(null)
  const [status, setStatus] = useState<VerificationState>(() => captureToken().token ? 'loading' : 'missing')
  const [attempt, setAttempt] = useState(0)
  const [email, setEmail] = useState('')
  const resend = useMailResend(deadlines, normalizeEmail(email))

  useEffect(() => {
    const request = requestRef.current ??= captureToken()
    if (window.location.search || window.location.hash) {
      window.history.replaceState(window.history.state, '', emailVerificationPath)
    }
    let active = true
    // The rehearsal cleanup runs before this microtask. A live effect can also
    // subscribe to the same promise if the request has already started.
    queueMicrotask(() => {
      if (!active) return
      if (!request.pending && request.token) request.pending = verify(request.token).then(result => {
        if (result === 'success' || result === 'invalid') request.token = null
        return result
      })
      void request.pending?.then(result => { if (active) setStatus(result) })
    })
    return () => { active = false }
  }, [attempt])

  function retry() {
    const request = requestRef.current
    if (!request?.token || status === 'loading') return
    request.pending = undefined
    setStatus('loading')
    setAttempt(value => value + 1)
  }

  function back() {
    requestRef.current = null
    onReturn()
  }

  const recovery = status === 'missing' || status === 'invalid'
  const retryable = status === 'network' || status === 'csrf'
  const loading = status === 'loading'
  const loginLabel = status === 'success' ? '로그인하기' : '로그인 화면으로'

  return (
    <div className={`auth-frame auth-verification${recovery ? ' auth-verification--recovery' : ''}`}>
      <header className="auth-header">
        <button type="button" className="auth-back" aria-label="로그인으로 돌아가기" onClick={back}>
          <span aria-hidden="true" />
        </button>
        <span className="auth-wordmark" role="img" aria-label="B:ONE" />
      </header>
      <section className="auth-verification-body" aria-labelledby="auth-verification-title" aria-busy={loading}>
        <StatusMark state={status} />
        <div role="status" aria-live="polite" aria-atomic="true">
          <h1 id="auth-verification-title">{copy[status].title}</h1>
          <p className="auth-verification-lead">{copy[status].description}</p>
        </div>
        {status === 'success' && <p className="auth-verification-note">이메일 인증만 완료된 상태예요.<br />자동으로 로그인되지는 않아요.</p>}
        {loading && <div className="auth-verification-progress" aria-hidden="true" />}
        {recovery && (
          <form className="auth-verification-recovery" noValidate aria-busy={resend.busy} onSubmit={event => {
            event.preventDefault()
            void resend.resend()
          }}>
            <div className="auth-field">
              <label htmlFor="auth-verification-email">가입한 이메일</label>
              <input id="auth-verification-email" type="email" autoComplete="email" maxLength={254} required
                value={email} disabled={resend.busy} onChange={event => setEmail(event.target.value)}
                placeholder="이메일 주소를 입력해 주세요" />
            </div>
            <button className="auth-primary" type="submit" disabled={resend.busy || resend.seconds > 0}>
              {resend.busy ? '요청 중…' : resend.seconds ? `다시 받기 · ${resend.seconds}초` : '인증 메일 다시 받기'}
            </button>
            {resend.feedback && <p className="auth-mail-feedback" role={resend.feedback.error ? 'alert' : 'status'}>{resend.feedback.text}</p>}
          </form>
        )}
        <div className="auth-verification-actions">
          {loading && <button className="auth-primary" type="button" disabled>인증 확인 중</button>}
          {retryable && <button className="auth-primary" type="button" onClick={retry}>다시 확인하기</button>}
          {!loading && <button className={status === 'success' ? 'auth-primary' : 'auth-secondary'} type="button" onClick={back}>{loginLabel}</button>}
        </div>
      </section>
    </div>
  )
}
