import { useId, useLayoutEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Icon } from '../components/Icon'
import type { PortfolioMode } from '../data/portfolio'
import { merchantLabel } from './merchantClasses'
import { emptyDraft, formatWon, orderName, paymentMethods, validateOrder } from './order'
import type { CreatedOrder, OrderDraft, OrderErrors } from './order'
import { OrderForm } from './OrderForm'
import { OrderRequestError } from './orderApi'
import type { OrderApi } from './orderApi'
import './orders.css'

const statusLabels = { PAID: '모의 승인됨', CANCELLED: '취소됨' } as const

function OrderCard({ order, pending, onSelect, mockApproval }: {
  order: CreatedOrder
  pending: boolean
  mockApproval?: boolean
  onSelect: () => void
}) {
  const [expanded, setExpanded] = useState(false)
  const id = useId()
  const date = order.orderDate.replaceAll('-', '.')
  const method = paymentMethods[order.paymentMethod]
  const fields = [
    ['가맹점명', orderName(order)],
    ['가맹점 업종', merchantLabel(order.merchantClass)],
    ['결제일', date],
    ['결제 시간', order.orderTime ?? '입력 안 함'],
    ['결제 방식', method],
  ]

  return (
    <article className="order-card">
      <h2>
        <button type="button" className="order-toggle" aria-expanded={expanded} aria-controls={id}
          onClick={() => setExpanded(value => !value)}>
          <span className="order-card-label">
            <span className="order-card-name">{orderName(order)}</span>
            <small>{date} · {method}</small>
          </span>
          <span className="order-card-price">{formatWon(order.amountWon)} <small>원</small></span>
          <Icon name="chevron" />
        </button>
      </h2>
      <div className="order-accordion" data-expanded={expanded} id={id} inert={!expanded}>
        <div className="order-accordion-clip">
          <div className="order-details">
            <dl>
              {fields.map(([label, value]) => (
                <div key={label}><dt>{label}</dt><dd>{value}</dd></div>
              ))}
            </dl>
            {order.status === 'CREATED' ? (
              <button className="order-handoff" type="button" disabled={pending} onClick={onSelect}>
                {pending ? '주문 확인 중…' : '빠른결제로 이동'} <span aria-hidden="true">›</span>
              </button>
            ) : <p className="order-status">{mockApproval ? '개발용 ' : ''}{statusLabels[order.status]}</p>}
          </div>
        </div>
      </div>
    </article>
  )
}

function lookupMessage(error: unknown) {
  switch (error instanceof OrderRequestError ? error.kind : 'unexpected') {
    case 'missing': return '주문을 찾을 수 없어요.'
    case 'unavailable': return '현재 환경에서 주문 API를 사용할 수 없어요.'
    case 'network': return '주문을 조회할 수 없어요. 연결을 확인하고 다시 시도해 주세요.'
    case 'server': return '서버 오류로 주문을 조회하지 못했어요.'
    default: return '주문 조회 응답을 확인할 수 없어요.'
  }
}

export function OrderWorkspace({ mode, api, active, onSelect, paidIds, sampleOrder, mockApproval }: {
  mode: PortfolioMode
  api: OrderApi
  active: boolean
  onSelect: (mode: PortfolioMode, order: CreatedOrder, source?: 'server' | 'mock') => void
  paidIds?: readonly string[]
  sampleOrder?: CreatedOrder
  mockApproval?: boolean
}) {
  const [orders, setOrders] = useState<CreatedOrder[]>([])
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(emptyDraft)
  const [errors, setErrors] = useState<OrderErrors>({})
  const [feedback, setFeedback] = useState<{ text: string; error?: boolean }>()
  const [creating, setCreating] = useState(false)
  const [checking, setChecking] = useState<string>()
  const creatingRef = useRef(false)
  const checkingRef = useRef(false)
  const visible = useRef(active)
  useLayoutEffect(() => { visible.current = active }, [active])

  function navigate(edit: boolean) {
    setEditing(edit)
    setFeedback(undefined)
    if (active) window.scrollTo(0, 0)
  }

  function update(field: keyof OrderDraft, value: string) {
    setDraft(current => ({ ...current, [field]: value }))
    setErrors(current => ({ ...current, [field]: undefined }))
    setFeedback(undefined)
  }

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (creatingRef.current) return
    const { errors: invalid, input } = validateOrder(draft)
    setErrors(invalid)
    setFeedback(undefined)
    if (!input) return

    creatingRef.current = true
    setCreating(true)
    try {
      const order = await api.create(mode, input)
      setOrders(current => [order, ...current.filter(item => item.orderId !== order.orderId)])
      setDraft(emptyDraft)
      setEditing(false)
      setFeedback({ text: '주문을 생성했어요.' })
      if (visible.current) window.scrollTo(0, 0)
    } catch (error) {
      const kind = error instanceof OrderRequestError ? error.kind : 'unexpected'
      if (error instanceof OrderRequestError && kind === 'validation') {
        const fieldErrors: OrderErrors = {}
        for (const { field } of error.fields) {
          if (field && Object.hasOwn(emptyDraft, field)) fieldErrors[field as keyof OrderDraft] = '입력값을 확인해 주세요.'
        }
        setErrors(fieldErrors)
      }
      const messages = {
        validation: '주문 입력값을 확인해 주세요.',
        missing: '주문 생성 경로를 확인할 수 없어요.',
        unavailable: '현재 환경에서 주문 생성 API를 사용할 수 없어요.',
        network: '연결이 끊겨 주문 생성 여부를 확인하지 못했어요. 다시 시도하면 주문이 중복될 수 있어요.',
        server: '서버 오류로 생성 여부를 확인하지 못했어요. 다시 시도하면 주문이 중복될 수 있어요.',
        unexpected: '주문 생성 응답을 확인하지 못했어요. 다시 시도하면 주문이 중복될 수 있어요.',
      }
      setFeedback({ text: messages[kind], error: true })
    } finally {
      creatingRef.current = false
      setCreating(false)
    }
  }

  async function choose(orderId: string) {
    if (checkingRef.current) return
    checkingRef.current = true
    setChecking(orderId)
    setFeedback(undefined)
    try {
      const current = await api.detail(mode, orderId)
      setOrders(items => items.map(item => item.orderId === orderId ? current : item))
      if (current.status === 'CREATED') {
        if (visible.current) onSelect(mode, current)
      } else {
        setFeedback({ text: '이미 처리된 주문이에요.', error: true })
      }
    } catch (error) {
      setFeedback({ text: lookupMessage(error), error: true })
    } finally {
      checkingRef.current = false
      setChecking(undefined)
    }
  }

  if (!active) return null
  const updatedOrders = orders.map(order => paidIds?.includes(order.orderId) && order.status === 'CREATED'
    ? { ...order, status: 'PAID' as const } : order)

  return (
    <div className="order-workspace">
      {editing ? (
        <OrderForm draft={draft} errors={errors} pending={creating}
          onChange={update} onSubmit={create} onBack={() => navigate(false)} />
      ) : (
        <section className="order-screen">
          <div className="order-title">
            <h1>주문 목록</h1>
            <span>총 {orders.length + Number(Boolean(sampleOrder))}건</span>
          </div>
          <div className="orders-list" aria-busy={checking !== undefined}>
            {orders.length === 0 && !sampleOrder && <p className="order-empty">이번 실행에서 생성한 주문이 없어요.</p>}
            {sampleOrder && <div className="order-mock">
              <small>개발용 목 주문</small>
              <OrderCard order={sampleOrder} pending={false} mockApproval onSelect={() => onSelect(mode, sampleOrder, 'mock')} />
            </div>}
            {updatedOrders.map(order => (
              <OrderCard key={order.orderId} order={order} pending={checking !== undefined}
                mockApproval={mockApproval && paidIds?.includes(order.orderId)}
                onSelect={() => void choose(order.orderId)} />
            ))}
          </div>
          <button className="order-primary order-new" type="button" onClick={() => navigate(true)}>
            <Icon name="plus" /> 주문 생성
          </button>
        </section>
      )}
      {feedback && (
        <p className="order-feedback" role={feedback.error ? 'alert' : 'status'}>{feedback.text}</p>
      )}
      <p className="order-disclaimer">실제 결제 및 금융거래는 발생하지 않습니다.</p>
    </div>
  )
}
