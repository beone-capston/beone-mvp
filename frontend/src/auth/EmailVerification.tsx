import { useRef, useState } from 'react'
import { AuthApiError, authApi } from './api'

// 메일 링크의 이메일 인증 화면. 기존 인증 화면 스타일을 그대로 쓰는 최소 구성이다
type Phase = 'ready' | 'sending' | 'done' | 'failed'

export function EmailVerification({ token, onDone }: { token: string; onDone: () => void }) {
  const validToken = /^[A-Za-z0-9_-]{43}$/.test(token)
  const [phase, setPhase] = useState<Phase>('ready')
  const [problem, setProblem] = useState('')
  const sending = useRef(false)

  async function verify() {
    if (!validToken || sending.current) return
    sending.current = true
    setPhase('sending')
    setProblem('')
    try {
      await authApi.verifyEmail(token)
      setPhase('done')
    } catch (error) {
      setPhase('failed')
      if (error instanceof AuthApiError && error.status === 400 && error.code === 'AUTH_TOKEN_INVALID') {
        setProblem('링크가 유효하지 않거나 만료되었습니다. 로그인 화면에서 인증 메일을 다시 받아 주세요.')
      } else if (error instanceof AuthApiError && error.status === 403 && error.code === 'CSRF_INVALID') {
        setProblem('요청을 확인할 수 없습니다. 다시 시도해 주세요.')
      } else {
        setProblem('인증을 완료하지 못했어요. 잠시 후 다시 시도해 주세요.')
      }
    } finally {
      sending.current = false
    }
  }

  const retryable = validToken && phase === 'failed' && !problem.startsWith('링크가')
  return (
    <div className="auth-frame auth-frame--signup">
      <header className="auth-header">
        <button type="button" className="auth-back" aria-label="로그인으로 돌아가기" disabled={phase === 'sending'}
          onClick={onDone}>
          <span aria-hidden="true" />
        </button>
        <span className="auth-wordmark" role="img" aria-label="B:ONE" />
      </header>
      <div className="auth-form" aria-busy={phase === 'sending'}>
        <h1>이메일 인증</h1>
        <div className="auth-actions">
          <div className="auth-feedback">
            {!validToken && <p role="alert" className="auth-problem">인증 링크를 확인하거나 로그인 화면에서 다시 요청해 주세요.</p>}
            {problem && <p role="alert" className="auth-problem">{problem}</p>}
            {phase === 'done' && <p role="status">이메일 인증을 완료했어요. 로그인해 주세요.</p>}
          </div>
          {validToken && (phase === 'ready' || phase === 'sending' || retryable) ? (
            <button className="auth-primary" type="button" disabled={phase === 'sending'} onClick={verify}>
              {phase === 'sending' ? '요청 중…' : '이메일 인증 완료하기'}
            </button>
          ) : (
            <button className="auth-primary" type="button" onClick={onDone}>로그인하기</button>
          )}
        </div>
      </div>
    </div>
  )
}
