import type { CSSProperties } from 'react'
import type { PortfolioMode } from '../data/portfolio'

const modes = [
  { id: 'MY_CARDS', label: '내 카드' },
  { id: 'VIRTUAL_PORTFOLIO', label: '가상 포트폴리오' },
] as const

export function PortfolioModeTabs({
  mode,
  onChange,
}: {
  mode: PortfolioMode
  onChange: (mode: PortfolioMode) => void
}) {
  return (
    <div
      className="mode-tabs"
      role="tablist"
      aria-label="포트폴리오 모드"
      style={{ '--mode-index': mode === 'MY_CARDS' ? 0 : 1 } as CSSProperties}
    >
      <span className="mode-underline" aria-hidden="true" />
      {modes.map((item, index) => (
        <button
          key={item.id}
          id={'mode-' + item.id}
          role="tab"
          aria-selected={mode === item.id}
          aria-controls="portfolio-panel"
          tabIndex={mode === item.id ? 0 : -1}
          onClick={() => onChange(item.id)}
          onKeyDown={(event) => {
            const next =
              event.key === 'Home'
                ? 0
                : event.key === 'End'
                  ? 1
                  : ['ArrowLeft', 'ArrowRight'].includes(event.key)
                    ? 1 - index
                    : null
            if (next === null) return
            event.preventDefault()
            onChange(modes[next].id)
            document.getElementById('mode-' + modes[next].id)?.focus()
          }}
        >
          {item.label}
        </button>
      ))}
    </div>
  )
}
