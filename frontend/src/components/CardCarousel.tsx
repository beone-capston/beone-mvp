import { useLayoutEffect, useRef } from 'react'
import type { WalletCard } from '../data/portfolio'

export function CardCarousel({
  cards,
  activeIndex,
  onIndexChange,
  onCardTap,
  onServices,
}: {
  cards: readonly WalletCard[]
  activeIndex: number
  onIndexChange: (index: number) => void
  onCardTap: (card: WalletCard) => void
  onServices: (card: WalletCard) => void
}) {
  const track = useRef<HTMLDivElement>(null)
  const currentIndex = useRef(activeIndex)
  useLayoutEffect(() => {
    currentIndex.current = activeIndex
  }, [activeIndex])
  const pointer = useRef<{ x: number; y: number; moved: boolean } | null>(null)
  const scrolling = useRef(false)
  const scrollEnd = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

  function scrollToIndex(index: number, smooth = true) {
    const container = track.current
    const first = container?.children[0] as HTMLElement | undefined
    const target = container?.children[index] as HTMLElement | undefined
    if (!container || !target || !first) return
    const left = target.offsetLeft - first.offsetLeft
    if (typeof container.scrollTo === 'function')
      container.scrollTo({
        left,
        behavior:
          smooth && !window.matchMedia('(prefers-reduced-motion: reduce)').matches
            ? 'smooth'
            : 'instant',
      })
    else container.scrollLeft = left
  }

  useLayoutEffect(() => {
    scrollToIndex(currentIndex.current, false)
    const observer =
      typeof ResizeObserver === 'undefined'
        ? null
        : new ResizeObserver(() => scrollToIndex(currentIndex.current, false))
    if (track.current) observer?.observe(track.current)
    return () => {
      observer?.disconnect()
      clearTimeout(scrollEnd.current)
    }
  }, [])

  return (
    <>
      <div
        className="card-carousel"
        ref={track}
        role="region"
        aria-roledescription="카드 캐러셀"
        aria-label="예시 카드 목록"
        onScroll={() => {
          const container = track.current
          if (!container) return
          scrolling.current = true
          clearTimeout(scrollEnd.current)
          scrollEnd.current = setTimeout(() => {
            scrolling.current = false
          }, 130)
          let closest = 0,
            distance = Infinity
          const first = container.children[0] as HTMLElement | undefined
          if (!first) return
          Array.from(container.children).forEach((node, index) => {
            const delta = Math.abs(
              (node as HTMLElement).offsetLeft - first.offsetLeft - container.scrollLeft,
            )
            if (delta < distance) {
              closest = index
              distance = delta
            }
          })
          onIndexChange(closest)
        }}
      >
        {cards.map((card, index) => (
          <article
            className="card-slide"
            key={card.id}
            aria-label={card.name + ' · 예시 카드 ' + (index + 1) + '/' + cards.length}
          >
            <button
              className={'payment-card ' + card.theme}
              aria-label={card.name + ' 가상카드 인증'}
              onPointerDown={(event) => {
                pointer.current = { x: event.clientX, y: event.clientY, moved: scrolling.current }
              }}
              onPointerMove={(event) => {
                if (
                  pointer.current &&
                  Math.hypot(event.clientX - pointer.current.x, event.clientY - pointer.current.y) >
                    8
                )
                  pointer.current.moved = true
              }}
              onPointerCancel={() => {
                if (pointer.current) pointer.current.moved = true
              }}
              onClick={(event) => {
                if (event.detail === 0 || (!pointer.current?.moved && !scrolling.current))
                  onCardTap(card)
                pointer.current = null
              }}
            >
              <span className="card-topline">
                <span>
                  KB국민카드 <small>{card.type}</small>
                </span>
                <span className="example-label">DEMO</span>
              </span>
              <span className="card-name">{card.name}</span>
              <span className="card-decoration" aria-hidden="true">
                <i />
                <i />
              </span>
              <span className="chip" aria-hidden="true">
                <span />
              </span>
              <span className="card-footline">
                <span>{card.caption}</span>
                <strong>
                  B<span>:</span>ONE
                </strong>
              </span>
            </button>
            <button
              className="card-services"
              aria-label={card.name + ' 부가서비스'}
              onClick={(event) => {
                event.stopPropagation()
                onServices(card)
              }}
            >
              부가서비스
            </button>
          </article>
        ))}
      </div>
      <div className="card-pagination" aria-label="카드 선택">
        {cards.map((card, index) => (
          <button
            key={card.id}
            aria-label={card.name + ' 보기'}
            aria-pressed={activeIndex === index}
            onClick={() => scrollToIndex(index)}
          >
            <span />
          </button>
        ))}
      </div>
      <p className="card-caption" aria-live="polite">
        {cards[activeIndex]?.name}
      </p>
    </>
  )
}
