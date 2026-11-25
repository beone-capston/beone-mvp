import { useLayoutEffect, useRef, useState } from 'react'
import type { PointerEvent, RefObject } from 'react'
import { Icon } from '../components/Icon'
import { merchantLabels, searchMerchantClasses } from './merchantClasses'
import type { MerchantClass } from './merchantClasses'

export function MerchantSheet({ anchor, selected, onSelect, onClose }: {
  anchor: RefObject<HTMLButtonElement | null>
  selected: string
  onSelect: (code: MerchantClass) => void
  onClose: () => void
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const drag = useRef<{ id: number; start: number; time: number; delta: number } | null>(null)
  const frame = useRef(0)
  const moved = useRef(false)
  const sizes = useRef({ compact: 0, tall: 0 })
  const [expanded, setExpanded] = useState(false)
  const [query, setQuery] = useState('')
  const results = searchMerchantClasses(query)

  useLayoutEffect(() => {
    const panel = dialog.current
    const trigger = anchor.current
    if (!panel) return
    if (panel.showModal) panel.showModal()
    else panel.setAttribute('open', '')
    panel.querySelector<HTMLButtonElement>('.merchant-close')?.focus({ preventScroll: true })
    const previous = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const viewport = window.visualViewport
    function resize() {
      const height = viewport?.height ?? window.innerHeight
      const top = viewport?.offsetTop ?? 0
      const belowAnchor = height + top - (trigger?.getBoundingClientRect().bottom ?? height / 2) - 12
      const tall = Math.max(0, height - 28)
      const compact = Math.min(tall, Math.max(Math.min(290, height * 0.72), belowAnchor))
      sizes.current = { compact, tall }
      panel?.style.setProperty('--sheet-compact', compact + 'px')
      panel?.style.setProperty('--sheet-tall', tall + 'px')
      panel?.style.setProperty('--sheet-bottom', Math.max(0, window.innerHeight - height - top) + 'px')
    }
    resize()
    window.addEventListener('resize', resize)
    viewport?.addEventListener('resize', resize)
    viewport?.addEventListener('scroll', resize)
    return () => {
      window.removeEventListener('resize', resize)
      viewport?.removeEventListener('resize', resize)
      viewport?.removeEventListener('scroll', resize)
      document.body.style.overflow = previous
      cancelAnimationFrame(frame.current)
      if (panel.close && panel.open) panel.close()
      else panel.removeAttribute('open')
      trigger?.focus({ preventScroll: true })
    }
  }, [anchor])

  function start(event: PointerEvent<HTMLElement>) {
    if (!event.isPrimary || event.button !== 0 || (event.target as HTMLElement).closest('button')) return
    moved.current = false
    drag.current = { id: event.pointerId, start: event.clientY, time: performance.now(), delta: 0 }
    dialog.current?.setPointerCapture(event.pointerId)
  }

  function move(event: PointerEvent<HTMLElement>) {
    const gesture = drag.current
    const panel = dialog.current
    if (!gesture || gesture.id !== event.pointerId || !panel) return
    gesture.delta = event.clientY - gesture.start
    if (Math.abs(gesture.delta) < 5 && !moved.current) return
    moved.current = true
    panel.dataset.dragging = 'true'
    cancelAnimationFrame(frame.current)
    frame.current = requestAnimationFrame(() => {
      const { compact, tall } = sizes.current
      const base = expanded ? tall : compact
      panel.style.height = Math.min(tall, Math.max(140, base - gesture.delta)) + 'px'
      panel.style.translate = gesture.delta > base - 140 ? '0 ' + (gesture.delta - base + 140) + 'px' : ''
    })
  }

  function finish(event: PointerEvent<HTMLElement>, cancelled = false) {
    const gesture = drag.current
    if (!gesture || gesture.id !== event.pointerId) return
    drag.current = null
    cancelAnimationFrame(frame.current)
    const panel = dialog.current
    if (panel) {
      delete panel.dataset.dragging
      panel.style.height = ''
      panel.style.translate = ''
    }
    if (cancelled || !moved.current) return
    const velocity = gesture.delta / Math.max(1, performance.now() - gesture.time)
    const snap = Math.max(40, Math.min(96, (sizes.current.tall - sizes.current.compact) / 2))
    if (!expanded && (gesture.delta > 115 || (gesture.delta > 55 && velocity > 0.6))) onClose()
    else if (expanded && gesture.delta > snap) setExpanded(false)
    else if (gesture.delta < -snap || (gesture.delta < -35 && velocity < -0.6)) setExpanded(true)
  }

  return (
    <dialog
      ref={dialog}
      className="merchant-sheet"
      data-expanded={expanded}
      aria-modal="true"
      aria-labelledby="merchant-sheet-title"
      onCancel={event => { event.preventDefault(); onClose() }}
      onPointerDown={event => {
        if (event.target === event.currentTarget && !drag.current) moved.current = false
      }}
      onPointerMove={move}
      onPointerUp={event => finish(event)}
      onPointerCancel={event => finish(event, true)}
      onLostPointerCapture={event => finish(event, true)}
      onClick={event => {
        if (event.target !== event.currentTarget || event.detail === 0) return
        if (moved.current) { moved.current = false; return }
        const rect = event.currentTarget.getBoundingClientRect()
        if (event.clientX < rect.left || event.clientX > rect.right ||
          event.clientY < rect.top || event.clientY > rect.bottom) onClose()
      }}
    >
      <header className="merchant-sheet-header" onPointerDown={start}>
        <span className="merchant-grip" aria-hidden="true" />
        <h2 id="merchant-sheet-title">가맹점 · 업종 분류</h2>
        <button type="button" className="merchant-close" aria-label="분류 검색 닫기" onClick={onClose}>×</button>
      </header>
      <div className="merchant-search">
        <Icon name="search" />
        <input type="search" value={query} onChange={event => setQuery(event.target.value)}
          aria-label="가맹점, 업종 검색" placeholder="가맹점, 업종 검색" />
        {query && <button type="button" aria-label="검색어 지우기" onClick={() => setQuery('')}>×</button>}
      </div>
      <p className="merchant-result-count" role="status">
        {query.trim() ? '검색 결과' : '전체'} {results.length}개 · 가나다순
      </p>
      <div className="merchant-results">
        {results.length === 0 && <p className="merchant-empty">검색 결과가 없어요.</p>}
        {results.map(code => (
          <button key={code} type="button" aria-pressed={code === selected} onClick={() => onSelect(code)}>
            {merchantLabels[code]}{code === selected && <Icon name="check" />}
          </button>
        ))}
      </div>
    </dialog>
  )
}
