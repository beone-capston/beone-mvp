import { MemberLogout } from './auth/memberIdentity'
import { useContext, useMemo, useRef, useState } from 'react'
import type { CSSProperties } from 'react'
import { BenefitsPage } from './benefits/BenefitsPage'
import { benefitsApi } from './benefits/config'
import { seoulDate } from './benefits/model'
import type { BenefitsApi } from './benefits/model'
import { CardImportPage } from './card-import/CardImportPage'
import type { CardImport } from './card-import/CardImportPage'
import { CardServicesPage } from './card-services/CardServicesPage'
import { CardCarousel } from './components/CardCarousel'
import { Icon } from './components/Icon'
import { PaymentPreferenceMenu } from './components/PaymentPreferenceMenu'
import { PortfolioModeTabs } from './components/PortfolioModeTabs'
import { Sheet } from './components/Sheet'
import { mockCardSource } from './data/portfolio'
import type { CardSource, PortfolioMode, WalletCard } from './data/portfolio'
import { OrderWorkspace } from './orders/OrderWorkspace'
import { orderApi } from './orders/orderApi'
import type { OrderApi } from './orders/orderApi'
import type { CreatedOrder } from './orders/order'
import type { PreferenceApi } from './preferences/preferenceApi'
import { usePaymentPreference } from './preferences/usePaymentPreference'
import { PaymentFlow } from './payment/PaymentFlow'
import { apiPaymentGateway } from './payment/paymentApi'
import { sampleOrder } from './payment/fixtures'
import type { Approval, Attempt, OrderSelection, PaymentGateway } from './payment/contract'
import type { BenefitSelection } from './benefits/BenefitsPage'

const menus = ['혜택', '빠른결제', '모의결제'] as const
type Menu = (typeof menus)[number]
interface ModeState {
  activeIndex: number
}

const mockDevice = import.meta.env.DEV && import.meta.env.VITE_DEVICE_AUTH_MODE === 'mock'

function App({ cardSource = mockCardSource, orders = orderApi, paymentGateway, mockAuth = mockDevice, benefits = benefitsApi, virtualBenefits, preferenceApi, cardImporter, mockOrders = false }: {
  cardSource?: CardSource; orders?: OrderApi; paymentGateway?: PaymentGateway; mockAuth?: boolean; benefits?: BenefitsApi; virtualBenefits?: BenefitsApi; preferenceApi?: PreferenceApi; cardImporter?: CardImport; mockOrders?: boolean
}) {
  const [menu, setMenu] = useState<Menu>('빠른결제')
  const [mode, setMode] = useState<PortfolioMode>('MY_CARDS')
  const [modes, setModes] = useState<Record<PortfolioMode, ModeState>>({
    MY_CARDS: { activeIndex: 0 },
    VIRTUAL_PORTFOLIO: { activeIndex: 0 },
  })
  const gateway = paymentGateway ?? apiPaymentGateway
  const [selectedOrders, setSelectedOrders] = useState<Partial<Record<PortfolioMode, OrderSelection>>>({})
  const [sampleOrders, setSampleOrders] = useState<Partial<Record<PortfolioMode, CreatedOrder>>>(
    () => {
      if (gateway.kind !== 'mock') return {}
      return { MY_CARDS: mockOrders ? { ...sampleOrder, orderDate: seoulDate() } : sampleOrder }
    },
  )
  const [paidIds, setPaidIds] = useState<Partial<Record<PortfolioMode, string[]>>>({})
  const [benefitSelection, setBenefitSelection] = useState<Partial<Record<PortfolioMode, BenefitSelection>>>({})
  const logout = useContext(MemberLogout)
  const [accountOpen, setAccountOpen] = useState(false)
  const [loggingOut, setLoggingOut] = useState(false)
  const [logoutError, setLogoutError] = useState(false)
  const logoutPending = useRef(false)
  const [notice, setNotice] = useState('')
  const [services, setServices] = useState<WalletCard | null>(null)
  const [importOpen, setImportOpen] = useState(false)
  const [importedCards, setImportedCards] = useState(() => cardImporter?.repository?.list() ?? [])
  const importTrigger = useRef<HTMLElement | null>(null)
  const [authOpen, setAuthOpen] = useState(false)
  const ownPreference = usePaymentPreference(preferenceApi, 'MY_CARDS')
  const virtualPreference = usePaymentPreference(preferenceApi, 'VIRTUAL_PORTFOLIO')
  const settings = mode === 'MY_CARDS' ? ownPreference : virtualPreference
  const cards = useMemo(() => {
    const base = cardSource.getCards(mode)
    const fromServer = settings.cards?.flatMap(card => card.wallet ?? []) ?? []
    if (settings.cards?.some(card => card.wallet)) return fromServer
    if (mode !== 'MY_CARDS' || !cardImporter) return base
    return [...importedCards, ...base.filter(card => card.source !== 'DESIGN_SAMPLE')]
  }, [cardImporter, cardSource, importedCards, mode, settings.cards])
  const activeIndex = Math.max(0, Math.min(modes[mode].activeIndex, cards.length - 1))
  const preference = settings.value
  const preferenceCards = settings.cards ?? cards
  const missingFixedCard = preference?.selectionMode === 'FIXED_REPRESENTATIVE' &&
    !preferenceCards.some(card => card.id === preference.representativeCardId && card.canBeRepresentative)
  const myMode = mode === 'MY_CARDS'
  const servicePage = services && services.source !== 'DESIGN_SAMPLE' ? services : null
  const serviceTrigger = useRef<HTMLElement | null>(null)

  function openCardImport() {
    if (!cardImporter) {
      setNotice('카드 추가 기능은 준비 중이에요.')
      return
    }
    importTrigger.current = document.activeElement instanceof HTMLElement ? document.activeElement : null
    setNotice('')
    setImportOpen(true)
    window.scrollTo(0, 0)
  }

  function closeCardImport() {
    const next = cardImporter?.repository?.list() ?? []
    if (next.some(card => !importedCards.some(existing => existing.id === card.id))) {
      setModes(current => ({ ...current, MY_CARDS: { activeIndex: 0 } }))
      ownPreference.reload()
    }
    if (cardImporter && !cardImporter.repository) ownPreference.reload()
    setImportedCards(next)
    setMode('MY_CARDS')
    setImportOpen(false)
    setMenu('빠른결제')
    window.scrollTo(0, 0)
    requestAnimationFrame(() => importTrigger.current?.focus({ preventScroll: true }))
  }

  function openServices(card: WalletCard) {
    setServices(card)
    if (card.source !== 'DESIGN_SAMPLE') {
      serviceTrigger.current = document.activeElement instanceof HTMLElement ? document.activeElement : null
      requestAnimationFrame(() => {
        document.querySelector<HTMLElement>('.services-page h1 button')?.focus()
        window.scrollTo(0, 0)
      })
    }
  }

  function closeServices() {
    setServices(null)
    requestAnimationFrame(() => {
      window.scrollTo(0, 0)
      serviceTrigger.current?.focus({ preventScroll: true })
    })
  }

  function selectMode(value: PortfolioMode) {
    const nextSettings = value === 'MY_CARDS' ? ownPreference : virtualPreference
    if (!nextSettings.value && nextSettings.error) nextSettings.reload()
    setMode(value)
    setNotice('')
    setServices(null)
    setAuthOpen(false)
  }

  function selectMenu(value: Menu) {
    setMenu(value)
    setNotice('')
  }

  function openSelectedOrder(orderMode: PortfolioMode, order: CreatedOrder, source: OrderSelection['source'] = 'server') {
    setSelectedOrders(current => ({ ...current, [orderMode]: { order, source: mockOrders ? 'mock' : source } }))
    selectMenu('빠른결제')
    window.scrollTo(0, 0)
  }

  function openPayment() {
    if (!preference || settings.saving || settings.needsReload || missingFixedCard) {
      setNotice(missingFixedCard ? '대표 카드를 다시 선택해 주세요.' : '결제 선택 방식을 먼저 확인해 주세요.')
      return
    }
    setAuthOpen(true)
    setNotice('')
    window.scrollTo(0, 0)
  }

  function returnToOrders(attempt?: Attempt, approval?: Approval, cancelled = false) {
    if (attempt && approval?.status === 'APPROVED') {
      const { selection, mode: orderMode } = attempt
      setBenefitSelection(current => ({ ...current, [orderMode]: {
        month: selection.order.orderDate.slice(0, 7), cardId: approval.userCardId,
      } }))
      const order = { ...selection.order, status: cancelled ? 'CANCELLED' as const : 'PAID' as const }
      setSelectedOrders(current => ({ ...current, [orderMode]: { ...selection, order } }))
      if (selection.source === 'mock' && sampleOrders[orderMode]?.orderId === order.orderId) setSampleOrders(current => ({ ...current, [orderMode]: order }))
      else if (!cancelled) setPaidIds(current => ({ ...current, [orderMode]: [...(current[orderMode] ?? []), order.orderId] }))
    }
    setAuthOpen(false)
    selectMenu('모의결제')
    window.scrollTo(0, 0)
  }

  return (
    <div className={importOpen ? "wallet-app wallet-card-import" : servicePage ? "wallet-app wallet-services-open" : authOpen ? "wallet-app wallet-payment" : menu === '혜택' ? "wallet-app wallet-benefits" : "wallet-app"}>
      <header className="wallet-header">
        <a
          className="brand"
          href="#main"
          aria-label="B:ONE 빠른결제 홈"
          onClick={event => {
            if (authOpen) event.preventDefault()
            else if (importOpen) {
              event.preventDefault()
              closeCardImport()
            } else if (servicePage) {
              event.preventDefault()
              closeServices()
            } else selectMenu('빠른결제')
          }}
          aria-disabled={authOpen || undefined}
          tabIndex={authOpen ? -1 : undefined}
        >
          <span className="brand-symbol">
            <img src="/beone-logo-blue.png" alt="" />
          </span>
          <span>
            B<span className="brand-colon">:</span>ONE
          </span>
        </a>
        {!authOpen && !importOpen && myMode && menu === '빠른결제' && (
          <div className="header-actions">
            <button
              aria-label="카드 추가"
              onClick={openCardImport}
            >
              <Icon name="plus" />
            </button>
            <button
              aria-label="마이페이지"
              onClick={() => setAccountOpen(true)}
            >
              <Icon name="more" />
            </button>
          </div>
        )}
      </header>

      {!authOpen && !importOpen && !servicePage && menu === '빠른결제' && <PortfolioModeTabs mode={mode} onChange={selectMode} />}
      <main id="main">
        {importOpen && cardImporter && <CardImportPage importer={cardImporter} onClose={closeCardImport} />}
        {servicePage && <CardServicesPage key={servicePage.id} card={servicePage} mode={mode}
          staticCatalog={!myMode && Boolean(virtualBenefits)} onClose={closeServices} />}
        <div id="portfolio-panel" role="tabpanel" aria-labelledby={'mode-' + mode} hidden={authOpen || importOpen || Boolean(servicePage) || menu !== '빠른결제'}>
          {menu === '빠른결제' ? (
            <section className="quick-payment" key={mode}>
              <h1 className="sr-only">B:ONE 빠른결제</h1>
              <p className="sr-only">
                {myMode
                  ? cardImporter ? '등록한 내 카드 목록입니다.' : '디자인 확인용 샘플 카드입니다. 실제 등록 카드가 아닙니다.'
                  : '가상 포트폴리오 시연용 카드입니다.'}
              </p>
              <div className="card-section-heading">
                <h2>
                  나의 카드 <span>{cards.length}</span>
                </h2>
                {preference ? <PaymentPreferenceMenu
                  key={mode}
                  cards={preferenceCards}
                  preference={preference}
                  onChange={settings.save}
                  saving={settings.saving}
                  error={settings.error}
                  refresh={settings.reload}
                /> : !settings.error && <div className="preference-status" role="status">
                  {!settings.supported ? '이 포트폴리오의 결제 설정은 지원하지 않아요.'
                    : '결제 설정 확인 중…'}
                </div>}
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
                  onCardTap={card => { if (card.source !== 'MOCK_IMPORTED' || gateway.kind === 'mock') openPayment() }}
                  onServices={openServices}
                />
              ) : (
                <div className="empty-state">
                  <Icon name="card" />
                  <h3>등록된 카드가 없어요</h3>
                  {myMode && (
                    <button
                      className="primary-button"
                      onClick={openCardImport}
                    >
                      카드 추가
                    </button>
                  )}
                </div>
              )}
            </section>
          ) : null}
        </div>
        <BenefitsPage mode={mode} api={myMode ? benefits : virtualBenefits ?? benefits} recentPayment={benefitSelection[mode]}
          active={!authOpen && !importOpen && menu === '혜택'} />
        {(['MY_CARDS', 'VIRTUAL_PORTFOLIO'] as const).map(orderMode => (
          <OrderWorkspace key={orderMode} mode={orderMode} api={orders}
            active={!authOpen && !importOpen && menu === '모의결제' && mode === orderMode && settings.supported} onSelect={openSelectedOrder}
            paidIds={paidIds[orderMode]} sampleOrder={sampleOrders[orderMode]} />
        ))}
        {menu === '모의결제' && !settings.supported && <p className="preference-status">이 포트폴리오의 모의결제는 지원하지 않아요.</p>}
        {authOpen && preference && <PaymentFlow mode={mode} preference={preference} selection={selectedOrders[mode]}
          cards={preferenceCards} gateway={gateway} mockAuth={mockAuth}
          onBack={() => { setAuthOpen(false); window.scrollTo(0, 0) }} onReturn={returnToOrders} />}
      </main>

      {!authOpen && !importOpen && !servicePage && <nav
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
      </nav>}

      {notice && (
        <div className="toast" role="status">
          <span>{notice}</span>
          <button className="close-button" aria-label="안내 닫기" onClick={() => setNotice('')}>
            ×
          </button>
        </div>
      )}
      {accountOpen && <Sheet title="마이페이지" onClose={() => {
        if (!loggingOut) { setAccountOpen(false); setLogoutError(false) }
      }}>
        {logoutError && <p className="sheet-description" role="alert">로그아웃하지 못했어요. 다시 시도해 주세요.</p>}
        <button className="primary-button" type="button" disabled={!logout || loggingOut} onClick={async () => {
          if (!logout || logoutPending.current) return
          logoutPending.current = true
          setLoggingOut(true)
          setLogoutError(false)
          try { await logout() }
          catch { setLogoutError(true) }
          finally { logoutPending.current = false; setLoggingOut(false) }
        }}>{loggingOut ? '로그아웃 중' : '로그아웃'}</button>
      </Sheet>}
      {services && !servicePage && (
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
    </div>
  )
}
export default App
