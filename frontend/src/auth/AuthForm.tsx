import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { tryLocalPreview } from './localPreview'
import { AuthApiError, authApi } from './api'
import { applyMailRetry, isValidEmail, normalizeEmail, rememberMailRequest, useMailResend } from './mailRequests'
import type { MailDeadlines } from './mailRequests'

type Screen = 'login' | 'signup' | 'reset-request' | 'reset-complete'
type Field = 'email' | 'password' | 'confirmation'
type FieldErrors = Partial<Record<Field, string>>
type FormProblem = { text: string; code?: string }

const passwordHint = '11~32자, 영문, 숫자, 특수문자 포함, 공백 제외'
const meetsPasswordRules = (value: string) =>
  /^[!-~]{11,32}$/.test(value) && /[A-Za-z]/.test(value) &&
  /[0-9]/.test(value) && /[^A-Za-z0-9]/.test(value)

const invalidCredentials = '이메일 또는 비밀번호를 확인해 주세요.'

export function AuthForm({ onLogin, onRetrySession, resetToken, sessionChecking = false, mailDeadlines }: {
  onLogin: (userId: string, preview?: boolean) => void
  onRetrySession?: () => void
  resetToken: string | null
  sessionChecking?: boolean
  mailDeadlines?: MailDeadlines
}) {
  const [ownDeadlines] = useState<MailDeadlines>(() => new Map())
  const deadlines = mailDeadlines ?? ownDeadlines
  const [screen, setScreen] = useState<Screen>(resetToken === null ? 'login' : 'reset-complete')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [problem, setProblem] = useState<FormProblem>()
  const [signupEmail, setSignupEmail] = useState('')
  const sent = screen === 'signup' && Boolean(signupEmail)
  const resend = useMailResend(deadlines, sent ? signupEmail : '')
  const [accepted, setAccepted] = useState('')
  const [resetComplete, setResetComplete] = useState(false)
  const [loading, setLoading] = useState(false)
  const submitting = useRef(false)
  const heading = useRef<HTMLHeadingElement>(null)
  const focusHeading = useRef(false)
  const login = screen === 'login'
  const signUp = screen === 'signup'
  const passwordValid = sent || meetsPasswordRules(password)
  const completing = screen === 'reset-complete'
  const validToken = resetToken !== null && /^[A-Za-z0-9_-]{43}$/.test(resetToken)
  const title = login ? '로그인' : signUp ? '회원가입' : '비밀번호 재설정'
  const submitLabel = login ? '로그인' : signUp ? '이메일 인증하고 가입하기'
    : completing ? '비밀번호 변경하기' : '이메일 인증하기'

  useEffect(() => {
    if (!focusHeading.current) return
    focusHeading.current = false
    heading.current?.focus()
  }, [screen])

  function changeScreen(next: Screen) {
    focusHeading.current = true
    setScreen(next)
    setSignupEmail('')
    resend.clearFeedback()
    setPassword('')
    setConfirmation('')
    setFieldErrors({})
    setProblem(undefined)
    setAccepted('')
    setResetComplete(false)
  }

  function clearError(field: Field) {
    setFieldErrors(current => ({
      ...current,
      [field]: undefined,
      ...(field === 'password' ? { confirmation: undefined } : {}),
    }))
    setProblem(undefined)
    setAccepted('')
    setResetComplete(false)
  }

  function handleFailure(error: unknown) {
    if (!(error instanceof AuthApiError) || error.status >= 500) {
      const timeout = error instanceof DOMException && error.name === 'TimeoutError'
      const text = timeout ? '요청 시간이 초과됐어요. 다시 시도해 주세요.'
        : error instanceof AuthApiError ? '서버 오류가 발생했어요. 잠시 후 다시 시도해 주세요.'
        : '서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.'
      setProblem({ text })
      return
    }

    const { code, status } = error
    let text = '요청을 처리할 수 없어요. 잠시 후 다시 시도해 주세요.'
    if (status === 400 && code === 'INVALID_REQUEST') {
      const fields: FieldErrors = {}
      const common = []
      for (const detail of error.fields) {
        if (detail.field === 'email' || detail.field === 'password') fields[detail.field] = detail.message
        else if (completing && detail.field === 'newPassword') fields.password = detail.message
        else common.push(detail.message)
      }
      setFieldErrors(fields)
      text = ['요청 값을 확인해 주세요.', ...common].join(' ')
    } else if (login && status === 401 && code === 'INVALID_CREDENTIALS') {
      text = invalidCredentials
    } else if (login && status === 403 && code === 'EMAIL_NOT_VERIFIED') {
      text = '이메일 인증을 완료해 주세요.'
    } else if (completing && status === 400 && code === 'AUTH_TOKEN_INVALID') {
      text = '링크가 유효하지 않거나 만료되었습니다. 다시 요청해 주세요.'
    } else if (status === 403 && code === 'CSRF_INVALID') {
      text = '요청을 확인할 수 없습니다. 화면을 새로고침한 후 다시 시도해 주세요.'
    } else if (status === 429 && code === 'RATE_LIMITED') {
      text = '요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.'
      if (error.retryAfter !== null) text += ` (${error.retryAfter}초 후 재시도)`
    }
    setProblem({ text, code })
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (sent || sessionChecking || submitting.current || (completing && !validToken)) return

    const normalizedEmail = normalizeEmail(email)
    const errors: FieldErrors = {}
    if (!completing && !normalizedEmail) errors.email = '이메일을 입력해 주세요.'
    else if (!completing && !isValidEmail(normalizedEmail)) {
      errors.email = '이메일 주소를 확인해 주세요.'
    }

    if (!password) errors.password = '비밀번호를 입력해 주세요.'
    else if (!login && !passwordValid) errors.password = passwordHint
    else if (password.length > 32) errors.password = '비밀번호는 32자 이하여야 합니다.'

    if (!login && !confirmation) errors.confirmation = '비밀번호를 한 번 더 입력해 주세요.'
    else if (!login && confirmation !== password) errors.confirmation = '비밀번호가 일치하지 않아요.'

    setFieldErrors(errors)
    setProblem(undefined)
    setAccepted('')
    const firstError = Object.keys(errors)[0]
    if (firstError) {
      event.currentTarget.querySelector<HTMLInputElement>(`#auth-${firstError}`)?.focus()
      return
    }

    submitting.current = true
    setLoading(true)
    try {
      const credentials = { email: normalizedEmail, password }
      if (signUp) {
        await authApi.signUp(credentials)
        rememberMailRequest(deadlines, normalizedEmail)
        setSignupEmail(normalizedEmail)
        setEmail(normalizedEmail)
        setPassword('')
        setConfirmation('')
      } else if (screen === 'reset-request') {
        const result = await authApi.requestPasswordReset(normalizedEmail)
        rememberMailRequest(deadlines, normalizedEmail)
        setAccepted(result.message)
        setPassword('')
        setConfirmation('')
      } else if (completing && resetToken !== null) {
        await authApi.resetPassword(resetToken, password)
        changeScreen('login')
        setResetComplete(true)
      } else {
        if (await tryLocalPreview(credentials)) {
          setPassword('')
          onLogin('local-preview', true)
          return
        }
        const member = await authApi.login(credentials)
        setPassword('')
        onLogin(member.userId)
      }
    } catch (error) {
      if (signUp || screen === 'reset-request') applyMailRetry(deadlines, normalizedEmail, error)
      handleFailure(error)
    } finally {
      submitting.current = false
      setLoading(false)
    }
  }

  function field(
    name: Field, label: string, placeholder: string, value: string,
    update: (value: string) => void, hint?: string,
  ) {
    const id = `auth-${name}`
    const credentialError = login && problem?.code === 'INVALID_CREDENTIALS'
    const description = [
      hint && `${id}-hint`,
      fieldErrors[name] && `${id}-error`,
      credentialError && 'auth-form-problem',
    ].filter(Boolean).join(' ')
    return (
      <div className={`auth-field auth-field--${name}`}>
        <label htmlFor={id}>{label}</label>
        <input
          id={id}
          name={name}
          type={name === 'email' ? 'email' : 'password'}
          autoComplete={name === 'email' ? 'email' : login ? 'current-password' : 'new-password'}
          autoCapitalize="none"
          spellCheck={false}
          required
          placeholder={sent && name !== 'email' ? '입력 완료' : placeholder}
          value={value}
          data-field-error={Boolean(fieldErrors[name]) || undefined}
          aria-invalid={Boolean(fieldErrors[name] || credentialError) || undefined}
          aria-describedby={description || undefined}
          onChange={event => {
            update(event.target.value)
            clearError(name)
          }}
        />
        {hint && <p id={`${id}-hint`} className="auth-hint"
          data-password-valid={signUp ? passwordValid : undefined}>
          <span aria-hidden="true">{signUp && !passwordValid ? '✕ ' : '✓ '}</span>{hint}</p>}
        {fieldErrors[name] && <p id={`${id}-error`} className="auth-field-error" role="alert">{fieldErrors[name]}</p>}
      </div>
    )
  }

  return (
    <div className={`auth-frame ${login ? 'auth-frame--login' : 'auth-frame--signup'}${signUp ? ' auth-frame--join' : ''}${sent ? ' auth-frame--join-sent' : ''}`}>
      {!login ? (
        <header className="auth-header">
          <button type="button" className="auth-back" aria-label="로그인으로 돌아가기" disabled={loading || resend.busy}
            onClick={() => changeScreen('login')}>
            <span aria-hidden="true" />
          </button>
          <span className="auth-wordmark" role="img" aria-label="B:ONE" />
        </header>
      ) : (
        <div className="auth-brand" role="img" aria-label="B:ONE">
          <span className="auth-symbol" />
          {problem?.code !== 'INVALID_CREDENTIALS' && <span className="auth-wordmark" />}
        </div>
      )}

      <form className="auth-form" onSubmit={submit} noValidate aria-busy={loading || sessionChecking}>
        <h1 ref={heading} tabIndex={-1}>{title}</h1>
        <fieldset className="auth-fields" disabled={loading || sent}>
          {!completing && field('email', '이메일', '이메일 주소를 입력해 주세요', email, setEmail)}
          {(!completing || validToken) && field(
            'password', '비밀번호', login ? '비밀번호를 입력해 주세요'
              : signUp ? '사용할 비밀번호를 입력해 주세요' : '변경할 비밀번호를 입력해 주세요',
            password, setPassword, login ? undefined : passwordHint,
          )}
          {login ? (
            <button type="button" className="auth-reset" onClick={() => changeScreen('reset-request')}>
              비밀번호를 잊으셨나요?
            </button>
          ) : (!completing || validToken) && (
            field('confirmation', '비밀번호 확인', '비밀번호를 한 번 더 입력해 주세요', confirmation, setConfirmation)
          )}
        </fieldset>
        <div className="auth-actions">
          {sessionChecking && <p className="auth-session-notice" role="status">로그인 상태를 확인하고 있어요.</p>}
          {onRetrySession && (
            <div className="auth-session-notice">
              <p role="status">로그인 상태를 확인하지 못했어요.</p>
              <button type="button" onClick={onRetrySession} disabled={loading || resend.busy}>다시 시도</button>
            </div>
          )}
          <div className="auth-feedback">
            {problem && <p id="auth-form-problem" role="alert" className="auth-problem">{problem.text}</p>}
            {accepted && (
              <p role="status">
                {accepted} 메일의 링크에서 새 비밀번호를 입력해 주세요.
              </p>
            )}
            {resetComplete && <p role="status">비밀번호를 변경했어요. 새 비밀번호로 로그인해 주세요.</p>}
            {completing && !validToken && <p role="alert" className="auth-problem">재설정 링크를 확인하거나 다시 요청해 주세요.</p>}
          </div>
          {sent && (
            <div className="auth-mail-resend">
              <button type="button" disabled={resend.busy || resend.seconds > 0} onClick={resend.resend}>
                {resend.busy ? '요청 중…' : '이메일 인증 재전송'}
                {resend.seconds > 0 && ` · ${resend.seconds}초`}
                <span aria-hidden="true"> ↻</span>
              </button>
              {resend.feedback && <p className="auth-mail-feedback" role={resend.feedback.error ? 'alert' : 'status'}>
                {resend.feedback.text}
              </p>}
            </div>
          )}
          {completing && (!validToken || problem?.code === 'AUTH_TOKEN_INVALID') ? (
            <button className="auth-primary" type="button" onClick={() => changeScreen('reset-request')}>
              재설정 메일 다시 요청
            </button>
          ) : (
            <button className="auth-primary" type="submit" disabled={loading || sessionChecking || sent}>
              {sent ? '이메일 인증 대기 중' : loading ? '요청 중…' : submitLabel}
            </button>
          )}
          {(login || signUp) && (
            <p className="auth-switch">
              {sent ? '메일에서 인증을 완료하셨나요?' : signUp ? '이미 계정이 있으신가요?' : 'B:ONE이 처음이신가요?'}
              <button type="button" disabled={loading || resend.busy} onClick={() => changeScreen(signUp ? 'login' : 'signup')}>
                {sent ? '로그인하기' : signUp ? '로그인' : '회원가입'}
              </button>
            </p>
          )}
        </div>
      </form>
    </div>
  )
}
