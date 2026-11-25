import { useEffect, useId, useRef } from 'react'
import type { ReactNode } from 'react'

export function Sheet({
  title,
  children,
  onClose,
}: {
  title: string
  children: ReactNode
  onClose: () => void
}) {
  const id = useId()
  const panel = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    const first = panel.current?.querySelector<HTMLElement>('button, input, select, [tabindex="0"]')
    first?.focus()
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = overflow
      if (previous?.isConnected) previous.focus()
    }
  }, [])

  return (
    <div
      className="sheet-backdrop"
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose()
      }}
    >
      <div
        className="sheet"
        ref={panel}
        role="dialog"
        aria-modal="true"
        aria-labelledby={id}
        onKeyDown={(event) => {
          if (event.key === 'Escape') {
            event.stopPropagation()
            onClose()
          }
          if (event.key !== 'Tab') return
          const items = [
            ...(panel.current?.querySelectorAll<HTMLElement>(
              'button:not(:disabled), input:not(:disabled), select, [tabindex="0"]',
            ) ?? []),
          ]
          const first = items[0],
            last = items[items.length - 1]
          if (event.shiftKey && document.activeElement === first) {
            event.preventDefault()
            last?.focus()
          }
          if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault()
            first?.focus()
          }
        }}
      >
        <div className="sheet-heading">
          <h2 id={id}>{title}</h2>
          <button className="close-button" aria-label="닫기" onClick={onClose}>
            ×
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}
