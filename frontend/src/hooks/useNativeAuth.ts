import { useCallback, useEffect, useRef, useState } from 'react'

interface BeoneBridge {
  postMessage: (message: string) => void
  onmessage: ((event: { data: string }) => void) | null
}

declare global {
  interface Window {
    BeoneBridge?: BeoneBridge
  }
}

const reasons = [
  'ACTIVE',
  'NOT_AUTHENTICATED',
  'EXPIRED',
  'AUTH_FAILED',
  'AUTH_CANCELED',
  'LEFT_APP',
  'SETUP_REQUIRED',
  'UNAVAILABLE',
  'NO_RESPONSE',
] as const
type NativeReason = (typeof reasons)[number]
type AuthReason = NativeReason | 'BROWSER' | 'SYNCING' | 'BRIDGE_ERROR'

export function useNativeAuth(enabled = true) {
  const [reason, setReason] = useState<AuthReason>('NOT_AUTHENTICATED')
  const [active, setActive] = useState(false)
  const [busy, setBusy] = useState(false)
  const [remainingMs, setRemainingMs] = useState(0)
  const [checking, setChecking] = useState(false)
  const [paymentReady, setPaymentReady] = useState(false)
  const pending = useRef(false)
  const pendingCheck = useRef(false)
  const deadline = useRef(0)
  const confirmedActive = useRef(false)

  const send = useCallback((type: 'auth.request' | 'state.get' | 'payment.check') => {
    try {
      if (typeof window.BeoneBridge?.postMessage !== 'function') return false
      window.BeoneBridge.postMessage(JSON.stringify({ v: 1, type }))
      return true
    } catch {
      return false
    }
  }, [])

  const invalidate = useCallback((next: AuthReason) => {
    confirmedActive.current = false
    deadline.current = 0
    pendingCheck.current = false
    setActive(false)
    setRemainingMs(0)
    setChecking(false)
    setPaymentReady(false)
    setReason(next)
  }, [])

  useEffect(() => {
    if (!enabled) return
    const bridge = window.BeoneBridge
    if (!bridge) return
    const previous = bridge.onmessage
    const receive = (event: { data: string }) => {
      let message: Record<string, unknown>
      try {
        message = JSON.parse(event.data)
      } catch {
        return
      }
      if (!message || message.v !== 1) return
      if (message.type === 'state') {
        if (
          typeof message.active !== 'boolean' ||
          typeof message.authenticating !== 'boolean' ||
          typeof message.remainingMs !== 'number' ||
          !Number.isFinite(message.remainingMs) ||
          message.remainingMs < 0 ||
          message.remainingMs > 60000 ||
          !reasons.includes(message.reason as NativeReason)
        )
          return
        // 활성 중 재인증(active·authenticating 동시)은 브리지 v1이 허용하는 조합이므로 받아서 busy로 반영한다(A1)
        if (message.active && (message.reason !== 'ACTIVE' || message.remainingMs === 0)) return
        if (!message.active && (message.reason === 'ACTIVE' || message.remainingMs !== 0)) return
        pending.current = message.authenticating
        setBusy(message.authenticating)
        setPaymentReady(false)
        if (message.active) {
          confirmedActive.current = true
          deadline.current = performance.now() + message.remainingMs
          setReason('ACTIVE')
          setActive(true)
          setRemainingMs(message.remainingMs)
        } else invalidate(message.reason as NativeReason)
      } else if (message.type === 'payment.check.result' && pendingCheck.current) {
        if (
          typeof message.allowed !== 'boolean' ||
          !reasons.includes(message.reason as NativeReason)
        )
          return
        pendingCheck.current = false
        setChecking(false)
        if (
          message.allowed &&
          message.reason === 'ACTIVE' &&
          confirmedActive.current &&
          deadline.current > performance.now()
        )
          setPaymentReady(true)
        else invalidate(message.reason === 'ACTIVE' ? 'SYNCING' : (message.reason as NativeReason))
      } else if (message.type === 'error' && typeof message.code === 'string') {
        pending.current = false
        setBusy(false)
        invalidate('BRIDGE_ERROR')
      }
    }
    bridge.onmessage = receive
    send('state.get')
    function synchronize() {
      if (document.visibilityState !== 'hidden') send('state.get')
      else invalidate('LEFT_APP')
    }
    document.addEventListener('visibilitychange', synchronize)
    window.addEventListener('focus', synchronize)
    const timer = setInterval(() => {
      if (!confirmedActive.current) return
      const remaining = Math.max(0, deadline.current - performance.now())
      setRemainingMs(remaining)
      if (remaining === 0) {
        invalidate('SYNCING')
        send('state.get')
      }
    }, 200)
    return () => {
      clearInterval(timer)
      document.removeEventListener('visibilitychange', synchronize)
      window.removeEventListener('focus', synchronize)
      if (bridge.onmessage === receive) bridge.onmessage = previous
    }
  }, [enabled, send, invalidate])

  const request = useCallback(() => {
    if (pending.current) return
    if (!window.BeoneBridge) {
      invalidate('BROWSER')
      return
    }
    pending.current = true
    setBusy(true)
    invalidate('NOT_AUTHENTICATED')
    if (!send('auth.request')) {
      pending.current = false
      setBusy(false)
      invalidate('BRIDGE_ERROR')
    }
  }, [invalidate, send])

  const checkPayment = useCallback(() => {
    if (pendingCheck.current || !confirmedActive.current || deadline.current <= performance.now())
      return
    pendingCheck.current = true
    setChecking(true)
    setPaymentReady(false)
    if (!send('payment.check')) invalidate('BRIDGE_ERROR')
  }, [invalidate, send])

  useEffect(() => {
    if (!busy) return
    const timeout = setTimeout(() => {
      pending.current = false
      setBusy(false)
      invalidate('NO_RESPONSE')
    }, 125000)
    return () => clearTimeout(timeout)
  }, [busy, invalidate])

  const reset = useCallback((reason: 'LEFT_APP' | 'NO_RESPONSE' = 'LEFT_APP') => invalidate(reason), [invalidate])

  const clearCheck = useCallback(() => setPaymentReady(false), [])

  return { reason, active, busy, remainingMs, checking, paymentReady, request, checkPayment, reset, clearCheck }
}

export const authMessages: Record<AuthReason, string> = {
  BROWSER:
    '가상카드 인증은 Android 앱에서 사용할 수 있어요. 브라우저에서는 인증을 진행하지 않습니다.',
  NOT_AUTHENTICATED: 'Android 기기 인증을 진행해주세요.',
  ACTIVE: '가상카드 사용 준비가 완료됐어요.',
  EXPIRED: '60초가 만료됐어요. 다시 인증해주세요.',
  AUTH_FAILED: '인증에 실패했어요. 기기 인증 안내를 확인해주세요.',
  AUTH_CANCELED: '인증을 취소했어요. 다시 시도할 수 있어요.',
  LEFT_APP: '앱을 벗어나 가상카드가 비활성화됐어요. 다시 인증해주세요.',
  SETUP_REQUIRED: '기기에서 지문 또는 화면 잠금 PIN을 설정해주세요.',
  UNAVAILABLE: '현재 기기 인증을 사용할 수 없어요.',
  NO_RESPONSE: '앱을 완전히 종료한 뒤 다시 열어 주세요.',
  SYNCING: '활성 시간이 끝나 네이티브 상태를 확인하고 있어요.',
  BRIDGE_ERROR: '앱과 인증 상태를 연결하지 못했어요. 앱에서 다시 시도해주세요.',
}
