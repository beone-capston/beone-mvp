import { useCallback, useEffect, useRef, useState } from 'react'
import { useNativeAuth } from '../hooks/useNativeAuth'

export type AuthSimulation = 'ACTIVE' | 'AUTH_FAILED' | 'AUTH_CANCELED' | 'SETUP_REQUIRED' | 'NO_RESPONSE'
type MockReason = AuthSimulation | 'NOT_AUTHENTICATED' | 'EXPIRED' | 'LEFT_APP'
export const activationDuration = 60000
const monotonicNow = () => performance.now()

export function useMockAuth(enabled: boolean, now = monotonicNow) {
  const deadline = useRef(0)
  const [remainingMs, setRemainingMs] = useState(0)
  const [reason, setReason] = useState<MockReason>('NOT_AUTHENTICATED')
  const [paymentReady, setPaymentReady] = useState(false)
  const reset = useCallback((cause: MockReason = 'LEFT_APP') => {
    deadline.current = 0
    setRemainingMs(0)
    setPaymentReady(false)
    setReason(cause)
  }, [])

  useEffect(() => {
    if (!enabled) return
    function leave() { reset() }
    function visibility() { if (document.visibilityState === 'hidden') leave() }
    window.addEventListener('pagehide', leave)
    document.addEventListener('visibilitychange', visibility)
    const timer = setInterval(() => {
      if (!deadline.current) return
      const remaining = Math.max(0, deadline.current - now())
      if (remaining === 0) reset('EXPIRED')
      else setRemainingMs(remaining)
    }, 150)
    return () => {
      clearInterval(timer)
      window.removeEventListener('pagehide', leave)
      document.removeEventListener('visibilitychange', visibility)
    }
  }, [enabled, now, reset])

  function request(outcome: AuthSimulation = 'ACTIVE') {
    reset(outcome)
    if (outcome === 'ACTIVE') {
      deadline.current = now() + activationDuration
      setRemainingMs(activationDuration)
    }
  }

  function checkPayment() {
    if (deadline.current > now()) setPaymentReady(true)
    else reset('EXPIRED')
  }

  const clearCheck = useCallback(() => setPaymentReady(false), [])

  return { reason, remainingMs, active: remainingMs > 0, busy: false, checking: false,
    paymentReady, request, checkPayment, reset, clearCheck }
}

export function usePaymentAuth(mock: boolean) {
  const native = useNativeAuth(!mock)
  const simulated = useMockAuth(mock)
  return mock ? simulated : native
}

export type PaymentAuth = ReturnType<typeof usePaymentAuth>
