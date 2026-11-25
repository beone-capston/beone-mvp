import { useEffect, useRef, useState } from 'react'
import type { PaymentPreference, WalletCard } from '../data/portfolio'
import { Icon } from './Icon'
import { Sheet } from './Sheet'

export function PaymentPreferenceMenu({
  cards,
  preference,
  onChange,
}: {
  cards: readonly WalletCard[]
  preference: PaymentPreference
  onChange: (preference: PaymentPreference) => void
}) {
  const [open, setOpen] = useState(false)
  const [choosing, setChoosing] = useState(false)
  const [candidate, setCandidate] = useState<string | null>(null)
  const root = useRef<HTMLDivElement>(null)
  const trigger = useRef<HTMLButtonElement>(null)
  const fixed = preference.selectionMode === 'FIXED_REPRESENTATIVE'
  const eligible = cards.filter((card) => card.canBeRepresentative)
  const selectedCard = cards.find((card) => card.id === preference.representativeCardId)

  function close() {
    setOpen(false)
    trigger.current?.focus()
  }

  useEffect(() => {
    if (!open) return
    root.current?.querySelector<HTMLElement>('[role="menuitemradio"][aria-checked="true"]')?.focus()
    function outside(event: PointerEvent) {
      if (!root.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('pointerdown', outside)
    return () => document.removeEventListener('pointerdown', outside)
  }, [open])

  return (
    <div className="preference" ref={root}>
      <button
        className="preference-trigger"
        ref={trigger}
        aria-label="결제 선택 방식"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={open ? 'preference-menu' : undefined}
        onClick={() => setOpen(!open)}
        onKeyDown={(event) => {
          if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
            event.preventDefault()
            setOpen(true)
          }
          if (event.key === 'Escape') close()
        }}
      >
        {fixed ? '대표 카드 고정' : '자동 추천'}
        <span className={open ? 'chevron open' : 'chevron'}>
          <Icon name="chevron" />
        </span>
      </button>
      {open && (
        <div
          className="preference-popover"
          id="preference-menu"
          role="menu"
          aria-label="결제 선택 방식"
          onKeyDown={(event) => {
            const items = [
              ...(root.current?.querySelectorAll<HTMLButtonElement>('[role="menuitemradio"]') ??
                []),
            ]
            const index = items.indexOf(document.activeElement as HTMLButtonElement)
            if (event.key === 'Escape') {
              event.preventDefault()
              event.stopPropagation()
              close()
            }
            if (['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) {
              event.preventDefault()
              const next =
                event.key === 'Home'
                  ? 0
                  : event.key === 'End'
                    ? items.length - 1
                    : (index + (event.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length
              items[next]?.focus()
            }
          }}
        >
          <button
            role="menuitemradio"
            aria-checked={!fixed}
            onClick={() => {
              onChange({ ...preference, selectionMode: 'AUTO_RECOMMEND' })
              close()
            }}
          >
            자동 추천{!fixed && <Icon name="check" />}
          </button>
          <button
            role="menuitemradio"
            aria-checked={fixed}
            onClick={() => {
              setCandidate(preference.representativeCardId)
              setChoosing(true)
              setOpen(false)
            }}
          >
            대표 카드 고정{fixed && <Icon name="check" />}
          </button>
        </div>
      )}
      {fixed && selectedCard && (
        <span className="sr-only">고정된 대표 카드: {selectedCard.name}</span>
      )}
      {choosing && (
        <Sheet
          title="대표 카드 선택"
          onClose={() => {
            setChoosing(false)
            trigger.current?.focus()
          }}
        >
          <p className="sheet-description">
            캐러셀에서 보는 카드와 별개로 결제에 사용할 대표 카드를 선택하세요. 이 설정은 현재
            화면의 목 설정입니다.
          </p>
          {eligible.length === 0 ? (
            <p className="sheet-description">
              선택 가능한 카드가 없어요. 카드를 먼저 등록해주세요.
            </p>
          ) : (
            <fieldset className="representative-options">
              <legend className="sr-only">대표 카드</legend>
              {eligible.map((card) => (
                <label key={card.id}>
                  <input
                    type="radio"
                    name="representative"
                    value={card.id}
                    checked={candidate === card.id}
                    onChange={() => setCandidate(card.id)}
                  />
                  <span>{card.name}</span>
                </label>
              ))}
            </fieldset>
          )}
          <button
            className="primary-button"
            disabled={!eligible.some((card) => card.id === candidate)}
            onClick={() => {
              if (!candidate || !eligible.some((card) => card.id === candidate)) return
              onChange({ selectionMode: 'FIXED_REPRESENTATIVE', representativeCardId: candidate })
              setChoosing(false)
              trigger.current?.focus()
            }}
          >
            대표 카드로 설정
          </button>
        </Sheet>
      )}
    </div>
  )
}
