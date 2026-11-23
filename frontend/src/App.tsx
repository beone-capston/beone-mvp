import { useRef, useState } from 'react'
import type { CSSProperties } from 'react'

const cards = [
  {
    name: '올라운드 티타늄카드',
    type: '신용',
    theme: 'navy',
    caption: 'ALLROUND TITANIUM',
  },
  {
    name: '청춘대로 톡톡카드',
    type: '신용',
    theme: 'mint-card',
    caption: 'YOUTH, EVERY DAY',
  },
  {
    name: '노리 체크카드',
    type: '체크',
    theme: 'silver',
    caption: 'ENJOY YOUR EVERYDAY',
  },
  {
    name: '첵첵 체크카드',
    type: '체크',
    theme: 'forest',
    caption: 'CHECK YOUR MOMENT',
  },
]
const menus = ['혜택', '빠른결제', '모의결제'] as const
type Menu = (typeof menus)[number]

function Icon({
  name,
}: {
  name: 'plus' | 'more' | 'card' | 'benefit' | 'simulation' | 'arrow' | 'shield'
}) {
  const paths = {
    plus: 'M12 4v16M4 12h16',
    more: 'M12 5h.01M12 12h.01M12 19h.01',
    card: 'M3 9h18M4 4h16a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1Z',
    benefit: 'M12 3v18M16 7h-6a3 3 0 0 0 0 6h4a3 3 0 0 1 0 6H7',
    simulation:
      'm9 8 7 4-7 4V8ZM5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z',
    arrow: 'M5 12h14m-5-5 5 5-5 5',
    shield: 'm12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6l8-3Zm-4 9 3 3 5-6',
  }
  return (
    <svg
      width="24"
      height="24"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={name === 'more' ? 3.5 : 1.7}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d={paths[name]} />
    </svg>
  )
}

function App() {
  const [menu, setMenu] = useState<Menu>('빠른결제')
  const [activeCard, setActiveCard] = useState(0)
  const [notice, setNotice] = useState('')
  const carousel = useRef<HTMLDivElement>(null)

  function selectMenu(value: Menu) {
    setMenu(value)
    setNotice('')
    if (value === '빠른결제') setActiveCard(0)
  }

  function comingSoon(feature: string) {
    setNotice(feature + ' 기능은 준비 중이에요.')
  }
  function moveCard(index: number) {
    const track = carousel.current
    if (!track) return
    const target = track.children[index] as HTMLElement | undefined
    if (!target) return
    track.scrollTo({
      left: target.offsetLeft - (track.children[0] as HTMLElement).offsetLeft,
      behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches
        ? 'instant'
        : 'smooth',
    })
  }

  return (
    <div className="wallet-app">
      <header className="wallet-header">
        <a
          className="brand"
          href="#main"
          aria-label="B:ONE 빠른결제 홈"
          onClick={() => selectMenu('빠른결제')}
        >
          <span className="brand-symbol">
            <img src="/beone-brand.png" alt="" />
          </span>
          <span>
            B<span className="brand-colon">:</span>ONE
          </span>
        </a>
        <div className="header-actions">
          <button
            aria-label="카드 추가"
            onClick={() => comingSoon('카드 추가')}
          >
            <Icon name="plus" />
          </button>
          <button
            aria-label="마이페이지"
            onClick={() => comingSoon('마이페이지')}
          >
            <Icon name="more" />
          </button>
        </div>
      </header>

      <main id="main">
        {menu === '빠른결제' ? (
          <section className="quick-payment" aria-labelledby="quick-title">
            <div className="wallet-intro">
              <div>
                <p className="eyebrow">ONE STEP AHEAD</p>
                <h1 id="quick-title">나의 모든 카드, 한곳에.</h1>
              </div>
            </div>
            <div className="tip-banner">
              <span className="tip-icon">
                <Icon name="shield" />
              </span>
              <p>
                모든 결제에, <strong>더 현명한 한 장.</strong>
                <small>내 카드의 혜택을 B:ONE과 함께 확인해보세요.</small>
              </p>
            </div>
            <div className="card-section-heading">
              <h2>
                나의 카드 <span>4</span>
              </h2>
            </div>
            <div
              className="card-carousel"
              ref={carousel}
              aria-label="예시 카드 목록"
              onScroll={() => {
                const track = carousel.current
                if (!track || track.children.length < 2) return
                const step =
                  (track.children[1] as HTMLElement).offsetLeft -
                  (track.children[0] as HTMLElement).offsetLeft
                if (step > 0)
                  setActiveCard(
                    Math.min(
                      cards.length - 1,
                      Math.max(0, Math.round(track.scrollLeft / step)),
                    ),
                  )
              }}
            >
              {cards.map((card, index) => (
                <article
                  className="card-slide"
                  key={card.name}
                  aria-label={card.name + ' · 예시 카드 ' + (index + 1) + '/4'}
                >
                  <div className={'payment-card ' + card.theme}>
                    <div className="card-topline">
                      <span>
                        KB국민카드 <small>{card.type}</small>
                      </span>
                      <span className="example-label">DEMO</span>
                    </div>
                    <h3>{card.name}</h3>
                    <div className="card-decoration" aria-hidden="true">
                      <i />
                      <i />
                    </div>
                    <div className="chip" aria-hidden="true">
                      <span />
                    </div>
                    <div className="card-footline">
                      <span>{card.caption}</span>
                      <strong>
                        B<span>:</span>ONE
                      </strong>
                    </div>
                  </div>
                  <button
                    className="card-services"
                    aria-label={card.name + ' 부가서비스'}
                    onClick={() => comingSoon(card.name + ' 부가서비스')}
                  >
                    부가서비스
                  </button>
                </article>
              ))}
            </div>
            <div className="card-pagination" aria-label="카드 선택">
              {cards.map((card, index) => (
                <button
                  key={card.name}
                  aria-label={card.name + ' 보기'}
                  aria-pressed={activeCard === index}
                  onClick={() => moveCard(index)}
                >
                  <span />
                </button>
              ))}
            </div>
            <div className="card-caption">
              <strong>{cards[activeCard].name}</strong>
            </div>
            <p className="simulation-disclaimer">
              실제 결제 및 금융거래는 발생하지 않습니다.
            </p>
          </section>
        ) : (
          <section className="placeholder-page" aria-labelledby="page-title">
            <span className="placeholder-icon">
              <Icon name={menu === '혜택' ? 'benefit' : 'simulation'} />
            </span>
            <p className="eyebrow">COMING SOON</p>
            <h1 id="page-title">{menu}</h1>
            <p>
              {menu === '혜택'
                ? '달별로 받은 혜택과 결제 정보를 확인할 수 있는 공간이에요.'
                : '가상의 주문으로 카드 혜택과 결제를 시뮬레이션하는 공간이에요.'}
            </p>
            <span className="preparing-label">준비 중이에요</span>
            <button
              className="return-button"
              onClick={() => selectMenu('빠른결제')}
            >
              빠른결제로 돌아가기 <Icon name="arrow" />
            </button>
          </section>
        )}
      </main>
      {notice && (
        <div className="toast" role="status">
          <span>{notice}</span>
          <button aria-label="안내 닫기" onClick={() => setNotice('')}>
            ×
          </button>
        </div>
      )}
      <nav
        className="bottom-nav"
        aria-label="주요 메뉴"
        style={{ '--active-index': menus.indexOf(menu) } as CSSProperties}
      >
        <span className="nav-indicator" aria-hidden="true" />
        {menus.map((item, index) => (
          <button
            key={item}
            aria-current={menu === item ? 'page' : undefined}
            onClick={() => selectMenu(item)}
          >
            <Icon name={(['benefit', 'card', 'simulation'] as const)[index]} />
            <span>{item}</span>
          </button>
        ))}
      </nav>
    </div>
  )
}
export default App
