import { useMemo, useState } from 'react'
import type { CSSProperties } from 'react'
import { CardCarousel } from './components/CardCarousel'
import { Icon } from './components/Icon'
import { PaymentPreferenceMenu } from './components/PaymentPreferenceMenu'
import { PortfolioModeTabs } from './components/PortfolioModeTabs'
import { Sheet } from './components/Sheet'
import { defaultPreference, mockCardSource } from './data/portfolio'
import type { CardSource, PaymentPreference, PortfolioMode, WalletCard } from './data/portfolio'
import { authMessages, useNativeAuth } from './hooks/useNativeAuth'

const menus = ['혜택', '빠른결제', '모의결제'] as const
type Menu = (typeof menus)[number]
interface ModeState {
  activeIndex: number
  preference: PaymentPreference
}

function App({ cardSource = mockCardSource }: { cardSource?: CardSource }) {
  const [menu, setMenu] = useState<Menu>('빠른결제')
  const [mode, setMode] = useState<PortfolioMode>('MY_CARDS')
  const [modes, setModes] = useState<Record<PortfolioMode, ModeState>>({
    MY_CARDS: { activeIndex: 0, preference: defaultPreference() },
    VIRTUAL_PORTFOLIO: { activeIndex: 0, preference: defaultPreference() },
  })
  const [notice, setNotice] = useState('')
  const [services, setServices] = useState<WalletCard | null>(null)
  const [authOpen, setAuthOpen] = useState(false)
  const auth = useNativeAuth()
  const cards = useMemo(() => cardSource.getCards(mode), [cardSource, mode])
  const activeIndex = Math.max(0, Math.min(modes[mode].activeIndex, cards.length - 1))
  const myMode = mode === 'MY_CARDS'

  function selectMode(value: PortfolioMode) {
    setMode(value)
    setNotice('')
    setServices(null)
    setAuthOpen(false)
  }

  function selectMenu(value: Menu) {
    setMenu(value)
    setNotice('')
  }

  function updatePreference(preference: PaymentPreference) {
    setModes((current) => ({ ...current, [mode]: { ...current[mode], preference } }))
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
        {myMode && (
          <div className="header-actions">
            <button
              aria-label="카드 추가"
              onClick={() =>
                setNotice(
                  '카드 추가 기능은 준비 중이에요. 현재 카드는 디자인 확인용 샘플이며 서버에 등록되지 않습니다.',
                )
              }
            >
              <Icon name="plus" />
            </button>
            <button
              aria-label="마이페이지"
              onClick={() => setNotice('마이페이지 기능은 준비 중이에요.')}
            >
              <Icon name="more" />
            </button>
          </div>
        )}
      </header>

      <PortfolioModeTabs mode={mode} onChange={selectMode} />
      <main id="main">
        <div id="portfolio-panel" role="tabpanel" aria-labelledby={'mode-' + mode}>
          {menu === '빠른결제' ? (
            <section className="quick-payment" key={mode}>
              <h1 className="sr-only">B:ONE 빠른결제</h1>
              <p className="sr-only">
                {myMode
                  ? '디자인 확인용 샘플 카드입니다. 실제 등록 카드가 아닙니다.'
                  : '가상 포트폴리오 시연용 카드입니다.'}
              </p>
              <div className="card-section-heading">
                <h2>
                  나의 카드 <span>{cards.length}</span>
                </h2>
                <PaymentPreferenceMenu
                  key={mode}
                  cards={cards}
                  preference={modes[mode].preference}
                  onChange={updatePreference}
                />
              </div>
              {cards.length > 0 ? (
                <CardCarousel
                  cards={cards}
                  activeIndex={activeIndex}
                  onIndexChange={(index) =>
                    setModes((current) => ({
                      ...current,
                      [mode]: { ...current[mode], activeIndex: index },
                    }))
                  }
                  onCardTap={() => {
                    setAuthOpen(true)
                    auth.request()
                  }}
                  onServices={setServices}
                />
              ) : (
                <div className="empty-state">
                  <Icon name="card" />
                  <h3>등록된 카드가 없어요</h3>
                  <p>카드를 추가하거나 가상 포트폴리오를 체험해보세요.</p>
                  {myMode && (
                    <button
                      className="primary-button"
                      onClick={() => setNotice('카드 추가 기능은 준비 중이에요.')}
                    >
                      카드 추가
                    </button>
                  )}
                </div>
              )}
              <p className="simulation-disclaimer">실제 결제 및 금융거래는 발생하지 않습니다.</p>
            </section>
          ) : (
            <section className="placeholder-page">
              <Icon name={menu === '혜택' ? 'benefit' : 'simulation'} />
              <h1>{menu}</h1>
              <p>
                {menu === '혜택'
                  ? '달별로 받은 혜택과 결제 정보를 확인할 수 있는 공간이에요.'
                  : '가상의 주문으로 결제를 시뮬레이션하는 공간이에요.'}
              </p>
              <span>준비 중이에요</span>
              <button className="primary-button" onClick={() => selectMenu('빠른결제')}>
                빠른결제로 돌아가기
              </button>
            </section>
          )}
        </div>
      </main>

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

      {notice && (
        <div className="toast" role="status">
          <span>{notice}</span>
          <button className="close-button" aria-label="안내 닫기" onClick={() => setNotice('')}>
            ×
          </button>
        </div>
      )}
      {services && (
        <Sheet title={services.name + ' 부가서비스'} onClose={() => setServices(null)}>
          <p className="sheet-description">
            {services.source === 'DESIGN_SAMPLE'
              ? '디자인 확인용 샘플 카드입니다. 실제 등록 데이터가 아닙니다.'
              : '가상 포트폴리오의 시연용 카드입니다.'}
          </p>
          <p className="sheet-description">
            이 카드의 혜택 조건·부가서비스 상세 연결은 준비 중이에요. 확인된 설명 데이터를 연결하기
            전에는 혜택이나 금액을 표시하지 않습니다.
          </p>
        </Sheet>
      )}
      {authOpen && (
        <Sheet title="가상카드 기기 인증" onClose={() => setAuthOpen(false)}>
          <p className="auth-message" role="status">
            {authMessages[auth.reason]}
          </p>
          {auth.active && (
            <p className="auth-countdown" aria-label="남은 활성 시간">
              {Math.ceil(auth.remainingMs / 1000)}초
            </p>
          )}
          <p className="sheet-description">
            실제 결제 및 금융거래는 발생하지 않습니다. 카드 터치는 대표 카드 고정 설정을 변경하지
            않습니다.
          </p>
          {auth.active ? (
            <button
              className="primary-button"
              disabled={auth.checking || auth.paymentReady}
              onClick={auth.checkPayment}
            >
              {auth.checking ? '활성 상태 확인 중' : '가상 주문서 결제'}
            </button>
          ) : (
            <button
              className="primary-button"
              disabled={auth.busy || auth.reason === 'BROWSER' || auth.reason === 'NO_RESPONSE'}
              onClick={auth.request}
            >
              다시 인증
            </button>
          )}
          {auth.paymentReady && (
            <div className="payment-entry" role="status">
              <p>네이티브 활성 상태를 확인했어요. 모의결제 화면으로 이동할 수 있어요.</p>
              <button
                className="primary-button"
                onClick={() => {
                  setAuthOpen(false)
                  selectMenu('모의결제')
                }}
              >
                모의결제 화면 열기
              </button>
              <small>주문 API와 모의 승인 요청은 아직 연결되지 않았습니다.</small>
            </div>
          )}
        </Sheet>
      )}
    </div>
  )
}
export default App
