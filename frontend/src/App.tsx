import { useMemo, useRef, useState } from 'react'
import type { CSSProperties } from 'react'
import { BenefitsPage } from './benefits/BenefitsPage'
import { benefitsApi } from './benefits/config'
import type { BenefitsApi } from './benefits/model'
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
import { formatWon, orderName } from './orders/order'
import type { CreatedOrder } from './orders/order'
import type { PreferenceApi } from './preferences/preferenceApi'
import { usePaymentPreference } from './preferences/usePaymentPreference'
import { PaymentFlow } from './payment/PaymentFlow'
import { apiPaymentGateway } from './payment/paymentApi'
import { createMockPaymentGateway } from './payment/mockPayment'
import { sampleOrder } from './payment/fixtures'
import type { Approval, Attempt, OrderSelection, PaymentGateway } from './payment/contract'

const menus = ['혜택', '빠른결제', '모의결제'] as const
type Menu = (typeof menus)[number]
interface ModeState {
  activeIndex: number
}

const mockDevice = import.meta.env.DEV && import.meta.env.VITE_DEVICE_AUTH_MODE === 'mock'
const mockPayments = import.meta.env.DEV && import.meta.env.VITE_PAYMENT_API_MODE === 'mock'

function App({ cardSource = mockCardSource, orders = orderApi, paymentGateway, mockAuth = mockDevice, benefits = benefitsApi, preferenceApi, mockOrders = false }: {
  cardSource?: CardSource; orders?: OrderApi; paymentGateway?: PaymentGateway; mockAuth?: boolean; benefits?: BenefitsApi; preferenceApi?: PreferenceApi; mockOrders?: boolean
}) {
  const [menu, setMenu] = useState<Menu>('빠른결제')
  const [mode, setMode] = useState<PortfolioMode>('MY_CARDS')
  const [modes, setModes] = useState<Record<PortfolioMode, ModeState>>({
    MY_CARDS: { activeIndex: 0 },
    VIRTUAL_PORTFOLIO: { activeIndex: 0 },
  })
  const [gateway] = useState(() => paymentGateway ?? (mockPayments
    ? createMockPaymentGateway(Number(import.meta.env.VITE_PAYMENT_MOCK_DELAY_MS || 800) || 0)
    : apiPaymentGateway))
  const [selectedOrders, setSelectedOrders] = useState<Partial<Record<PortfolioMode, OrderSelection>>>({})
  const [sampleOrders, setSampleOrders] = useState<Partial<Record<PortfolioMode, CreatedOrder>>>(
    () => gateway.kind === 'mock' ? { MY_CARDS: sampleOrder, ...(mockOrders ? { VIRTUAL_PORTFOLIO: { ...sampleOrder, orderId: 'mock-virtual-order' } } : {}) } : {},
  )
  const [paidIds, setPaidIds] = useState<Partial<Record<PortfolioMode, string[]>>>({})
  const selectedOrder = selectedOrders[mode]?.order
  const [notice, setNotice] = useState('')
  const [services, setServices] = useState<WalletCard | null>(null)
  const [authOpen, setAuthOpen] = useState(false)
  const cards = useMemo(() => cardSource.getCards(mode), [cardSource, mode])
  const activeIndex = Math.max(0, Math.min(modes[mode].activeIndex, cards.length - 1))
  const ownPreference = usePaymentPreference(preferenceApi, 'MY_CARDS')
  const virtualPreference = usePaymentPreference(preferenceApi, 'VIRTUAL_PORTFOLIO')
  const settings = mode === 'MY_CARDS' ? ownPreference : virtualPreference
  const preference = settings.value
  const preferenceCards = settings.cards ?? cards
  const missingFixedCard = preference?.selectionMode === 'FIXED_REPRESENTATIVE' &&
    !preferenceCards.some(card => card.id === preference.representativeCardId && card.canBeRepresentative)
  const myMode = mode === 'MY_CARDS'
  const servicePage = services?.source === 'VIRTUAL' ? services : null
  const serviceTrigger = useRef<HTMLElement | null>(null)

  function openServices(card: WalletCard) {
    setServices(card)
    if (card.source === 'VIRTUAL') {
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

  function returnToOrders(attempt?: Attempt, approval?: Approval) {
    if (attempt && approval?.status === 'APPROVED') {
      const { selection, mode: orderMode } = attempt
      const order = { ...selection.order, status: 'PAID' as const }
      setSelectedOrders(current => ({ ...current, [orderMode]: { ...selection, order } }))
      if (selection.source === 'mock' && sampleOrders[orderMode]?.orderId === order.orderId) setSampleOrders(current => ({ ...current, [orderMode]: order }))
      else setPaidIds(current => ({ ...current, [orderMode]: [...(current[orderMode] ?? []), order.orderId] }))
    }
    setAuthOpen(false)
    selectMenu('모의결제')
    window.scrollTo(0, 0)
  }

  return (
    <div className={servicePage ? "wallet-app wallet-services-open" : authOpen ? "wallet-app wallet-payment" : menu === '혜택' ? "wallet-app wallet-benefits" : "wallet-app"}>
      <header className="wallet-header">
        <a
          className="brand"
          href="#main"
          aria-label="B:ONE 빠른결제 홈"
          onClick={event => {
            if (authOpen) event.preventDefault()
            else if (servicePage) {
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
        {!authOpen && myMode && menu === '빠른결제' && (
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

      {!authOpen && !servicePage && menu === '빠른결제' && <PortfolioModeTabs mode={mode} onChange={selectMode} />}
      <main id="main">
        {servicePage && <CardServicesPage key={servicePage.id} card={servicePage} onClose={closeServices} />}
        <div id="portfolio-panel" role="tabpanel" aria-labelledby={'mode-' + mode} hidden={authOpen || Boolean(servicePage) || menu !== '빠른결제'}>
          {menu === '빠른결제' ? (
            <section className="quick-payment" key={mode}>
              <h1 className="sr-only">B:ONE 빠른결제</h1>
              <p className="sr-only">
                {myMode
                  ? '디자인 확인용 샘플 카드입니다. 실제 등록 카드가 아닙니다.'
                  : '가상 포트폴리오 시연용 카드입니다.'}
              </p>
              {selectedOrder && (
                <div className="quick-selected-order" aria-label="선택한 가상 주문">
                  <strong>{orderName(selectedOrder)}</strong>
                  <span>{formatWon(selectedOrder.amountWon)}원</span>
                </div>
              )}
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
                /> : <div className="preference-status" role="status">
                  {!settings.supported ? '이 포트폴리오의 결제 설정은 지원하지 않아요.'
                    : settings.error ?? '결제 설정 확인 중…'}
                  {settings.error && <button type="button" className="preference-retry" onClick={settings.reload}>다시 조회</button>}
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
                  onCardTap={openPayment}
                  onServices={openServices}
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
          ) : null}
        </div>
        <BenefitsPage mode={mode} api={benefits} active={!authOpen && menu === '혜택'} />
        {(['MY_CARDS', 'VIRTUAL_PORTFOLIO'] as const).map(orderMode => (
          <OrderWorkspace key={orderMode} mode={orderMode} api={orders}
            active={!authOpen && menu === '모의결제' && mode === orderMode && settings.supported} onSelect={openSelectedOrder}
            paidIds={paidIds[orderMode]} sampleOrder={sampleOrders[orderMode]} mockApproval={gateway.kind === 'mock'} />
        ))}
        {menu === '모의결제' && !settings.supported && <p className="preference-status">이 포트폴리오의 모의결제는 지원하지 않아요.</p>}
        {authOpen && preference && <PaymentFlow mode={mode} preference={preference} selection={selectedOrders[mode]}
          cards={preferenceCards} gateway={gateway} mockAuth={mockAuth}
          onBack={() => { setAuthOpen(false); window.scrollTo(0, 0) }} onReturn={returnToOrders} />}
      </main>

      {!authOpen && !servicePage && <nav
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
