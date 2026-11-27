import { useEffect, useEffectEvent, useState } from 'react'
import type { PortfolioMode } from '../data/portfolio'
import { Icon } from '../components/Icon'
import { Sheet } from '../components/Sheet'
import { merchantLabel } from '../orders/merchantClasses'
import { formatWon, paymentMethods } from '../orders/order'
import { bucketRows, monthPeriod, productId, seoulDate, shiftMonth } from './model'
import type { BenefitsApi, HeldCard, LedgerRow, Period, StateValue } from './model'
import './benefits.css'


type Result<T> = { key: string; value: T } | { key: string; error: string }

function useRead<T>(key: string | null, load: (signal: AbortSignal) => Promise<T>) {
  const read = useEffectEvent(load)
  const [state, setState] = useState<Result<T>>()
  const [attempt, setAttempt] = useState(0)
  const request = key === null ? null : key + ':' + attempt
  useEffect(() => {
    if (request === null) return
    const controller = new AbortController()
    read(controller.signal).then(value => {
      if (!controller.signal.aborted) setState({ key: request, value })
    }, (error: unknown) => {
      if (!controller.signal.aborted) setState({ key: request, error: error instanceof Error ? error.message : '조회하지 못했어요.' })
    })
    return () => controller.abort()
  }, [request])
  const current = state?.key === request ? state : undefined
  return {
    value: current && 'value' in current ? current.value : undefined,
    error: current && 'error' in current ? current.error : undefined,
    retry: () => setAttempt(value => value + 1),
  }
}

const tabs = ['혜택 현황', '모의 결제 내역'] as const
const filters = ['all', 'APPROVAL', 'CANCELLATION'] as const
const dotted = (date: string) => date.replaceAll('-', '.')
const formatPeriod = (period: Period | null) => period ? dotted(period.from) + ' ~ ' + dotted(period.to) : '기준 기간 확인 필요'
const won = (value: number | null | undefined) => value == null ? '확인 필요' : value === 0 ? '0원' : formatWon(value) + '원'
function amountWithUnit(value: number) {
  return <>{formatWon(value === 0 ? 0 : value)}<small className="benefit-unit">원</small></>
}
const sourceNames: Record<string, string> = { MANUAL: '수동 입력', CODEF: 'CODEF 조회', SEED: '시연 초기값', UNKNOWN: '확인 필요' }
const method = (code: string) => Object.hasOwn(paymentMethods, code) ? paymentMethods[code as keyof typeof paymentMethods] : '결제 방식 확인 필요'
const paymentStatuses: Record<string, string> = { APPROVED: '모의 승인', FAILED: '모의 승인 실패',
  PARTIALLY_CANCELLED: '부분 취소', CANCELLED: '취소 완료' }

function Feedback({ error, retry }: { error?: string; retry: () => void }) {
  return error ? <div className="benefit-feedback" role="alert">{error} <button onClick={retry}>다시 시도</button></div>
    : <p className="benefit-feedback" role="status">조회 중이에요.</p>
}

function Provenance({ state }: { state?: StateValue<number> }) {
  if (!state) return <p>이 항목의 입력 사용액이 없어요.</p>
  return (
    <>
      <p>출처 · {sourceNames[state.source] ?? state.source}</p>
      <p>기준 기간 · {formatPeriod(state.period)}</p>
      {state.updatedAt && <p>업데이트 · {state.updatedAt}</p>}
      {!state.usable && <p>계산에 사용할 수 없어요.{state.reason ? ' (' + state.reason + ')' : ''}</p>}
    </>
  )
}

function Overview({ api, mode, scope, month, card, cards, onCard }: {
  api: BenefitsApi; mode: PortfolioMode; scope: string; month: string; card: HeldCard
  cards: HeldCard[]; onCard: (id: string) => void
}) {
  const id = productId(card)
  const today = seoulDate()
  const asOf = today.slice(0, 7) === month ? today : monthPeriod(month).to
  const identity = scope + ':' + month + ':' + card.userCardId + ':' + id
  const monthly = useRead(identity, signal => api.monthly(mode, card.userCardId, month, signal))
  const rules = useRead(id ? identity : null, async signal => {
    const [summary, product] = await Promise.all([api.products(asOf, signal), api.product(id!, asOf, signal)])
    return { product, summary: summary.find(item => item.cardProductId === id) }
  })
  const [detail, setDetail] = useState<string>()
  const value = monthly.value
  const product = rules.value?.product
  const rows = product && value ? bucketRows(product, value, month) : []
  const active = rows.find(row => row.id === detail)
  return (
    <div className="benefit-overview">
      <label className="benefit-card-picker">
        <span>조회 카드</span>
        <select value={card.userCardId} onChange={event => onCard(event.target.value)}>
          {cards.map(item => <option key={item.userCardId} value={item.userCardId}>{item.displayName}</option>)}
        </select>
        <Icon name="chevron" />
      </label>
      {!value ? <Feedback error={monthly.error} retry={monthly.retry} /> : (
        <>
          <button className="benefit-performance" type="button" onClick={event => { event.currentTarget.focus({ preventScroll: true }); setDetail('performance') }}>
            <span>전월 인정 실적</span>
            <strong>{value.previousMonthPerformanceWon.value == null ? '확인 필요' : <>
              {formatWon(value.previousMonthPerformanceWon.value)} <small>원</small>
            </>}</strong>
            <small>{formatPeriod(value.previousMonthPerformanceWon.period)}</small>
          </button>
          {!value.snapshotAt && value.previousMonthPerformanceWon.value === null && value.limitBucketUsages.length === 0 &&
            <p className="benefit-feedback">이 달에 확인된 월간 상태값이 없어요.</p>}
          <h2>한도별 사용 현황</h2>
          {!id ? <p className="benefit-feedback">카드 상품 매핑이 확인되지 않았어요.</p>
            : !rules.value ? <Feedback error={rules.error} retry={rules.retry} />
              : !product?.rule ? <p className="benefit-feedback">{product?.ruleStatus === 'RULE_EXPIRED'
                ? '조회일 기준으로 적용 기간이 만료됐어요.' : '조회일에 적용할 검증 규칙이 없어요.'}</p>
                : rows.length === 0 ? <p className="benefit-feedback">확인할 수 있는 한도 묶음이 없어요.</p>
                  : <div className="benefit-buckets">
                    {rows.map(row => (
                      <button key={row.id} onClick={event => { event.currentTarget.focus({ preventScroll: true }); setDetail(row.id) }} type="button">
                        <span><strong>{row.title}</strong><small>{row.caption}</small></span>
                        <span><strong className={row.usage?.value == null ? 'benefit-unknown' : 'benefit-amount'}>
                          {row.usage?.value == null ? '확인 필요' : amountWithUnit(row.usage.value)}</strong><small>{row.limit === null ? '월 한도 확인 필요' : '월 한도 ' + won(row.limit)}</small></span>
                        <Icon name="chevron" />
                      </button>
                    ))}
                  </div>}
          {detail && <Sheet title={active?.title ?? '전월 인정 실적'} onClose={() => setDetail(undefined)}>
            <div className="benefit-sheet">
              <Provenance state={active ? active.usage : value.previousMonthPerformanceWon} />
              <p>입력 시점 · {value.snapshotAt ?? '확인 필요'}</p>
              <p>카드 사용 등록일 · {value.registeredOn?.value ?? '확인 필요'}</p>
              <p>스냅샷 이후 · {value.afterSnapshot
                ? `모의 승인 ${value.afterSnapshot.approvalCount}건 (${won(value.afterSnapshot.approvedAmountWon)}), 취소 ${value.afterSnapshot.cancellationCount}건`
                : '확인 필요'}</p>
              {active && <>
                <p>사용액 · {won(active.usage?.value)}</p><p>참고 월 한도 · {won(active.limit)}</p>
                {active.parent && <p>상위 통합 한도와 합산하지 않아요.</p>}
                <p>선택형 혜택 · {value.selectedServices.some(item => item.usable) ? '선택값 있음'
                  : value.noServiceSelection?.usable ? '선택 없음' : '확인 필요'}</p>
                <p>상품 · {rules.value?.summary?.cardName ?? product?.cardName}</p>
                <p>적용 규칙 · {product?.rule?.ruleVersion ?? '확인 필요'}</p>
              </>}
            </div>
          </Sheet>}
        </>
      )}
    </div>
  )
}

function History({ api, mode, scope, month, cards }: {
  api: BenefitsApi; mode: PortfolioMode; scope: string; month: string; cards: HeldCard[]
}) {
  const [filter, setFilter] = useState<(typeof filters)[number]>('all')
  const [cardId, setCardId] = useState('')
  const [selected, setSelected] = useState<LedgerRow>()
  const card = cards.find(item => item.userCardId === cardId)?.userCardId
  const key = scope + ':' + month + ':' + (card ?? '')
  const ledger = useRead(key, signal => api.ledger(mode, month, signal, card))
  const detail = useRead(selected?.simulatedPaymentId ? key + ':' + selected.simulatedPaymentId : null,
    signal => api.payment(mode, selected!.simulatedPaymentId!, signal))
  const name = (row: LedgerRow) => row.merchantName || merchantLabel(row.merchantClass)
  const cardName = (row: LedgerRow) => cards.find(card => card.userCardId === row.userCardId)?.displayName ?? '카드 정보 확인 필요'
  const items = ledger.value?.items.filter(row => row.source === 'SIMULATION') ?? []
  const visible = items.filter(row => filter === 'all' || row.entryType === filter).sort((a, b) =>
    b.transactionDate.localeCompare(a.transactionDate))
  const incomplete = ledger.value?.coverage.filter(row => row.source === 'SIMULATION' && !row.complete) ?? []
  return (
    <div className="benefit-history">
      <div className="benefit-filters" aria-label="거래 구분">
        {filters.map((item, index) => (
          <button type="button" key={item} aria-pressed={filter === item} onClick={() => setFilter(item)}>
            {['전체', '승인', '취소'][index]}{ledger.value && ` ${items.filter(row => item === 'all' || row.entryType === item).length}`}
          </button>
        ))}
      </div>
      <div className="benefit-history-meta">
        <span>{ledger.value ? `조회 내역 ${visible.length}건` : '조회 내역'}</span>
        {cards.length > 0 && <select aria-label="모의 결제 내역 조회 카드" value={card ?? ''}
          onChange={event => { setCardId(event.target.value); setSelected(undefined) }}>
          <option value="">전체 카드</option>
          {cards.map(item => <option key={item.userCardId} value={item.userCardId}>{item.displayName}</option>)}
        </select>}
      </div>
      {!ledger.value ? <Feedback error={ledger.error} retry={ledger.retry} /> : (
        <>
          {incomplete.length > 0 && <p className="benefit-feedback">
            일부 내역의 완전성을 확인할 수 없어요.
            {' '}{[...new Set(incomplete.flatMap(row => row.incompleteReasons))].join(' · ')}
          </p>}
          {visible.length === 0 && <p className="benefit-feedback">{items.length
            ? '이 필터에 해당하는 내역이 없어요.' : '이번 달 모의 결제 내역이 없어요.'}</p>}
          {visible.map((row, index) => (
            <div key={row.ledgerTransactionId}>
              {(index === 0 || visible[index - 1].transactionDate !== row.transactionDate) &&
                <p className="benefit-transaction-date">{dotted(row.transactionDate)}</p>}
              <button className="benefit-transaction" type="button" onClick={event => { event.currentTarget.focus({ preventScroll: true }); setSelected(row) }}>
                <span><strong>{name(row)}</strong><small>{cardName(row)}</small>
                  <small>{row.transactionTime ?? '시간 미입력'} · {method(row.paymentMethod)}</small></span>
                <span className="benefit-transaction-value"><strong>{amountWithUnit(row.amountWon)}</strong>
                  <small>{row.entryType === 'APPROVAL' ? '모의 승인' : '모의 취소'}</small></span>
                <Icon name="chevron" />
              </button>
            </div>
          ))}
        </>
      )}
      {selected && <Sheet title={selected.entryType === 'APPROVAL' ? '모의 승인 상세' : '모의 취소 상세'}
        onClose={() => setSelected(undefined)}>
        <div className="benefit-sheet">
          <p>{name(selected)} · {cardName(selected)}</p>
          <p>거래 금액 · {won(selected.amountWon)}</p>
          <p>거래 시각 · {dotted(selected.transactionDate)} {selected.transactionTime ?? '시간 미입력'}</p>
          <p>원장 ID · {selected.ledgerTransactionId}</p>
          {selected.originalTransactionId && <p>원 승인 ID · {selected.originalTransactionId}</p>}
          <p>귀속월 · {selected.attributionMonth?.status === 'CONFIRMED' ? selected.attributionMonth.value : '확인 필요'}</p>
          {selected.simulatedPaymentId ? detail.value ? <>
            <p>모의 결제 ID · {detail.value.simulatedPaymentId}</p>
            <p>결제 상태 · {paymentStatuses[detail.value.status] ?? '확인 필요'}</p>
            <p>승인 시 예상 혜택 · {won(detail.value.currentOrderBenefit.amountWon)}</p>
          </> : <Feedback error={detail.error} retry={detail.retry} />
            : <p>연결된 모의 결제 상세가 없어요.</p>}
        </div>
      </Sheet>}
    </div>
  )
}

function BenefitsContent({ api, mode, scope, month, tab, selectedId, onCard }: {
  api: BenefitsApi; mode: PortfolioMode; scope: string; month: string; tab: number
  selectedId?: string; onCard: (id: string) => void
}) {
  const cards = useRead(scope, signal => api.cards(mode, signal))
  if (tab === 1) return <History key={month} api={api} mode={mode} scope={scope} month={month} cards={cards.value ?? []} />
  if (!cards.value) return <Feedback error={cards.error} retry={cards.retry} />
  const selected = cards.value.find(card => card.userCardId === selectedId) ?? cards.value[0]
  if (!selected) return <p className="benefit-feedback">조회할 보유 카드가 없어요.</p>
  return <Overview key={month + ':' + selected.userCardId} api={api} mode={mode} scope={scope} month={month}
    cards={cards.value} card={selected} onCard={onCard} />
}

export type BenefitSelection = { month: string; cardId: string }

export function BenefitsPage({ mode, active, api, recentPayment }: {
  mode: PortfolioMode; active: boolean; api: BenefitsApi; recentPayment?: BenefitSelection
}) {
  const [months, setMonths] = useState<Partial<Record<PortfolioMode, { value: string; payment?: BenefitSelection }>>>({})
  const [selected, setSelected] = useState<Partial<Record<PortfolioMode, { value: string; payment?: BenefitSelection }>>>({})
  const [tab, setTab] = useState(0)
  const month = (months[mode]?.payment === recentPayment ? months[mode]?.value : undefined)
    ?? recentPayment?.month ?? seoulDate().slice(0, 7)
  const selectedId = (selected[mode]?.payment === recentPayment ? selected[mode]?.value : undefined)
    ?? recentPayment?.cardId
  const [year, monthNumber] = month.split('-')
  const scope = api.context + ':' + mode
  if (!active) return null

  function changeMonth(value: string) {
    if (/^(?!0000)\d{4}-(0[1-9]|1[0-2])$/.test(value)) {
      setMonths(previous => ({ ...previous, [mode]: { value, payment: recentPayment } }))
    }
  }

  return (
    <section className="benefits-page" aria-label="혜택">
      <header className="benefit-title">
        <h1>혜택</h1>
        <div className="benefit-context">
          <span>• {mode === 'MY_CARDS' ? '내 카드' : '가상 포트폴리오'}</span>
        </div>
      </header>
      <div className="benefit-month">
        <label>
          <span>{year}년 {Number(monthNumber)}월</span><Icon name="chevron" />
          <input type="month" aria-label="조회 월" value={month} onChange={event => changeMonth(event.target.value)} />
        </label>
        <button type="button" aria-label="이전 달" onClick={() => changeMonth(shiftMonth(month, -1))}><Icon name="chevron" /></button>
        <button type="button" aria-label="다음 달" onClick={() => changeMonth(shiftMonth(month, 1))}><Icon name="chevron" /></button>
      </div>
      <div className="benefit-tabs" role="tablist" aria-label="혜택 조회"
        onKeyDown={event => {
          if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
          event.preventDefault()
          const next = event.key === 'Home' ? 0 : event.key === 'End' ? 1 : 1 - tab
          setTab(next)
          event.currentTarget.querySelectorAll<HTMLButtonElement>('[role=tab]')[next]?.focus()
        }}>
        {tabs.map((label, index) => <button key={label} type="button" role="tab"
          aria-selected={tab === index} aria-controls="benefit-panel" id={'benefit-tab-' + index}
          tabIndex={tab === index ? 0 : -1} onClick={() => setTab(index)}>{label}</button>)}
      </div>
      <div id="benefit-panel" role="tabpanel" aria-labelledby={'benefit-tab-' + tab}>
        <BenefitsContent key={scope} api={api} mode={mode} scope={scope} month={month} tab={tab}
          selectedId={selectedId} onCard={value => setSelected(previous => ({
            ...previous, [mode]: { value, payment: recentPayment },
          }))} />
      </div>
    </section>
  )
}
