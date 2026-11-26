import { useEffect, useRef, useState } from 'react'
import type { PaymentPreference } from '../data/portfolio'
import type { PreferenceCard } from '../preferences/preferenceApi'
import { Icon } from './Icon'
import { Sheet } from './Sheet'

export function PaymentPreferenceMenu({
  cards,
  preference,
  onChange,
  saving, error, refresh,
}: {
  cards: readonly PreferenceCard[]
  preference: PaymentPreference
  onChange: (preference: PaymentPreference) => boolean | Promise<boolean>
  saving?: boolean
  error?: string
  refresh?: () => void
}) {
  const [open, setOpen] = useState(false)
  const [choosing, setChoosing] = useState(false)
  const [candidate, setCandidate] = useState<string | null>(null)
  const root = useRef<HTMLDivElement>(null)
  const trigger = useRef<HTMLButtonElement>(null)
  const fixed = preference.selectionMode === 'FIXED_REPRESENTATIVE'
  const eligible = cards.filter((card) => card.canBeRepresentative)
  const selectedCard = eligible.find(card => card.id === preference.representativeCardId)
  const unavailable = Boolean(preference.representativeCardId && !selectedCard)

  function saveCandidate() {
    if (!candidate || !eligible.some(card => card.id === candidate)) return
    function finish(success: boolean) {
      if (!success) return
      setChoosing(false)
      trigger.current?.focus()
    }
    const result = onChange({ selectionMode: 'FIXED_REPRESENTATIVE', representativeCardId: candidate })
    if (typeof result === 'boolean') finish(result)
    else void result.then(finish)
  }

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
        disabled={saving}
        onClick={() => setOpen(!open)}
        onKeyDown={(event) => {
          if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
            event.preventDefault()
            setOpen(true)
          }
          if (event.key === 'Escape') close()
        }}
      >
        {saving ? '저장 중…' : fixed ? '대표 카드 고정' : '자동 추천'}
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
              ...(root.current?.querySelectorAll<HTMLButtonElement>('[role="menuitemradio"], [role="menuitem"]') ??
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
              void onChange({ ...preference, selectionMode: 'AUTO_RECOMMEND',
                representativeCardId: unavailable ? null : preference.representativeCardId })
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
          {preference.representativeCardId !== null && <button role="menuitem" onClick={() => {
            void onChange({ selectionMode: 'AUTO_RECOMMEND', representativeCardId: null })
            close()
          }}>대표 카드 해제</button>}
        </div>
      )}
      {fixed && selectedCard && (
        <span className="sr-only">고정된 대표 카드: {selectedCard.name}</span>
      )}
      {unavailable && <p className="preference-status" role="status">대표 카드 확인 필요</p>}
      {!choosing && error && <p className="preference-status" role="alert">{error}</p>}
      {!choosing && error && refresh && <button type="button" className="preference-retry" onClick={refresh}>다시 조회</button>}
      {choosing && (
        <Sheet
          title="대표 카드 선택"
          onClose={() => {
            if (saving) return
            setChoosing(false)
            trigger.current?.focus()
          }}
        >
          <p className="sheet-description">
            결제에 사용할 대표 카드를 선택하세요.
          </p>
          {eligible.length === 0 ? (
            <p className="sheet-description">
              선택 가능한 카드가 없어요. 카드를 먼저 등록해주세요.
            </p>
          ) : (
            <fieldset className="representative-options" disabled={saving}>
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
          {error && <p className="sheet-description" role="alert">{error}</p>}
          {error && refresh && <button type="button" className="preference-retry" onClick={() => {
            setChoosing(false)
            refresh()
          }}>카드 목록 다시 조회</button>}
          <button
            className="primary-button"
            disabled={saving || !eligible.some((card) => card.id === candidate)}
            onClick={saveCandidate}
          >
            {saving ? '저장 중…' : '대표 카드로 설정'}
          </button>
        </Sheet>
      )}
    </div>
  )
}
