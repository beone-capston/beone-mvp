import { useEffect, useRef, useState } from 'react'
import { AuthApiError, authApi } from './api'

export type MailDeadlines = Map<string, number>

export const normalizeEmail = (email: string) => email.trim().toLowerCase()
export const isValidEmail = (email: string) =>
  email.length <= 254 && /^[^\s@]+@[^\s@]+\.[^\s@.]+$/.test(email)

export function rememberMailRequest(deadlines: MailDeadlines, email: string, seconds = 60) {
  const now = Date.now()
  for (const [address, deadline] of deadlines) {
    if (deadline <= now) deadlines.delete(address)
  }
  deadlines.set(email, now + seconds * 1000)
}

export function applyMailRetry(deadlines: MailDeadlines, email: string, error: unknown) {
  if (error instanceof AuthApiError && error.status === 429 && error.code === 'RATE_LIMITED' &&
    error.retryAfter !== null && Number.isSafeInteger(error.retryAfter)) {
    rememberMailRequest(deadlines, email, error.retryAfter)
  }
}

const remaining = (deadlines: MailDeadlines, email: string) =>
  Math.max(0, Math.ceil(((deadlines.get(email) ?? 0) - Date.now()) / 1000))

export function useMailResend(deadlines: MailDeadlines, email: string) {
  const inFlight = useRef(false)
  const [busy, setBusy] = useState(false)
  const [feedback, setFeedback] = useState<{ email: string; text: string; error?: boolean }>()
  const [, tick] = useState(0)
  const seconds = remaining(deadlines, email)

  const waiting = seconds > 0
  useEffect(() => {
    if (!waiting) return
    const update = () => tick(value => value + 1)
    const timer = window.setInterval(update, 250)
    document.addEventListener('visibilitychange', update)
    window.addEventListener('pageshow', update)
    return () => {
      clearInterval(timer)
      document.removeEventListener('visibilitychange', update)
      window.removeEventListener('pageshow', update)
    }
  }, [waiting])

  async function resend() {
    if (inFlight.current || remaining(deadlines, email) > 0) return
    if (!isValidEmail(email)) {
      setFeedback({ email, text: '이메일 주소를 확인해 주세요.', error: true })
      return
    }

    inFlight.current = true
    setBusy(true)
    setFeedback(undefined)
    try {
      await authApi.requestEmailVerification(email)
      rememberMailRequest(deadlines, email)
      setFeedback({ email, text: '다시 요청했어요. 인증 대상 주소라면 새 메일을 보내드려요. 가장 최근 메일을 확인해 주세요.' })
    } catch (error) {
      applyMailRetry(deadlines, email, error)
      const text = error instanceof AuthApiError && error.code === 'RATE_LIMITED'
        ? '요청이 너무 많아요. 잠시 후 다시 시도해 주세요.'
        : error instanceof AuthApiError && error.code === 'CSRF_INVALID'
          ? '요청의 보안 확인에 실패했어요. 다시 시도해 주세요.'
          : '메일 요청을 확인하지 못했어요. 잠시 후 다시 시도해 주세요.'
      setFeedback({ email, text, error: true })
    } finally {
      setBusy(false)
      inFlight.current = false
    }
  }

  return { busy, seconds, resend, clearFeedback: () => setFeedback(undefined), feedback: feedback?.email === email ? feedback : undefined }
}
