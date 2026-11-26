import { useCallback, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { AuthEntry } from '../auth/AuthEntry'
import { emailVerificationPath } from '../auth/EmailVerification'
import { BrandSplash } from './BrandSplash'
import { splashTiming } from './splashTiming'

const now = () => performance.now()

export function AppStartup({ children, startedAt = 0, clock = now }: {
  children: ReactNode
  startedAt?: number
  clock?: () => number
}) {
  const [launch] = useState(() => {
    const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
    const duration = Math.min(reduced ? splashTiming.reduced : splashTiming.exit, splashTiming.cap)
    const elapsed = Math.max(0, clock() - startedAt)
    return { reduced, elapsed, end: startedAt + duration,
      show: !['/password-reset', emailVerificationPath].includes(window.location.pathname) && elapsed < duration }
  })
  const [show, setShow] = useState(launch.show)
  const finish = useCallback(() => setShow(false), [])

  useEffect(() => {
    if (!show) return
    function checkDeadline() {
      if (clock() >= launch.end) finish()
    }
    const timer = setTimeout(finish, Math.max(0, launch.end - clock()))
    document.addEventListener('visibilitychange', checkDeadline)
    window.addEventListener('pageshow', checkDeadline)
    window.addEventListener('focus', checkDeadline)
    return () => {
      clearTimeout(timer)
      document.removeEventListener('visibilitychange', checkDeadline)
      window.removeEventListener('pageshow', checkDeadline)
      window.removeEventListener('focus', checkDeadline)
    }
  }, [clock, finish, launch.end, show])

  return (
    <>
      <div inert={show} aria-hidden={show || undefined}>
        <AuthEntry>{children}</AuthEntry>
      </div>
      {show && <BrandSplash elapsed={launch.elapsed} reduced={launch.reduced} onExit={finish} />}
    </>
  )
}
