import { useCallback, useEffect, useRef, useState } from 'react'
import type { PaymentPreference, PortfolioMode, WalletCard } from '../data/portfolio'
import { authMessages } from '../hooks/useNativeAuth'
import { merchantLabel } from '../orders/merchantClasses'
import { formatWon, orderName, paymentMethods } from '../orders/order'
import { Icon } from '../components/Icon'
import { PaymentError, retryWindow } from './contract'
import type { Approval, Attempt, OrderSelection, PaymentGateway } from './contract'
import { activationDuration, usePaymentAuth } from './deviceAuth'
import { PaymentResult } from './PaymentResult'
import './payment.css'

type Stage =
  | { kind: 'ready'; notice?: string; blocked?: boolean }
  | { kind: 'processing'; attempt: Attempt }
  | { kind: 'result'; attempt: Attempt; approval: Approval }
  | { kind: 'uncertain'; attempt: Attempt; message: string }

const errorMessages: Record<string, [number, string]> = {
  INVALID_REQUEST: [400, '주문과 결제 요청을 확인해 주세요.'],
  ORDER_NOT_FOUND: [404, '선택한 주문을 찾을 수 없어요.'],
  RECOMMENDATION_NOT_FOUND: [404, '추천 정보를 찾을 수 없어요.'],
  SIMULATED_PAYMENT_NOT_FOUND: [404, '모의 승인 정보를 찾을 수 없어요.'],
  NOT_FOUND: [404, '현재 환경에서 모의 승인 API를 사용할 수 없어요.'],
  ORDER_ALREADY_PAID: [409, '이미 처리된 주문이에요. 주문 목록을 확인해 주세요.'],
  IDEMPOTENCY_KEY_REUSED: [409, '이미 사용한 승인 요청 키예요. 주문 상태를 확인해 주세요.'],
  RECOMMENDATION_NOT_DETERMINED: [409, '추천 카드를 확정할 수 없어요. 카드 상태를 확인해 주세요.'],
  REPRESENTATIVE_CARD_NOT_ELIGIBLE: [409, '설정한 대표 카드로 결제할 수 없어요.'],
  INTERNAL_SERVER_ERROR: [500, '서버 오류로 승인 결과를 확인하지 못했어요.'],
}
const selectionLabels = { AUTO_RECOMMEND: '자동 추천', FIXED_REPRESENTATIVE: '대표 카드 고정' }

export function PaymentFlow({ mode, preference, selection, cards, gateway, mockAuth,
  onBack, onReturn }: {
  mode: PortfolioMode
  preference: PaymentPreference
  selection: OrderSelection | undefined
  cards: readonly WalletCard[]
  gateway: PaymentGateway
  mockAuth: boolean
  onBack: () => void
  onReturn: (attempt?: Attempt, approval?: Approval) => void
}) {
  const auth = usePaymentAuth(mockAuth)
  const { request: requestAuth, reset: resetAuth, clearCheck, active, paymentReady } = auth
  const requested = useRef(false)
  const mounted = useRef(false)
  const check = useRef<Attempt | null>(null)
  const inFlight = useRef(false)
  const [checking, setChecking] = useState(false)
  const [replayExpired, setReplayExpired] = useState(false)
  const [stage, setStage] = useState<Stage>({ kind: 'ready' })

  useEffect(() => {
    mounted.current = true
    return () => { mounted.current = false }
  }, [])

  useEffect(() => {
    if (!requested.current) {
      requested.current = true
      requestAuth()
    }
  }, [requestAuth])

  const approve = useCallback(async (attempt: Attempt) => {
    if (inFlight.current) return
    inFlight.current = true
    clearCheck()
    setStage({ kind: 'processing', attempt })
    try {
      const approval = await gateway.approve(attempt)
      if (mounted.current) setStage({ kind: 'result', approval, attempt })
    } catch (error) {
      if (!mounted.current) return
      const known = error instanceof PaymentError ? errorMessages[error.code] : undefined
      const message = known && error instanceof PaymentError && known[0] === error.status
        ? known[1] : '모의 승인 요청의 결과를 확인할 수 없어요.'
      if (!(error instanceof PaymentError) || error.uncertain) setStage({ kind: 'uncertain', attempt, message })
      else setStage({ kind: 'ready', notice: message,
        blocked: ['ORDER_ALREADY_PAID', 'ORDER_NOT_FOUND', 'IDEMPOTENCY_KEY_REUSED'].includes(error.code) })
    } finally {
      inFlight.current = false
    }
  }, [clearCheck, gateway])

  useEffect(() => {
    if (!check.current || !paymentReady) return
    const attempt = check.current
    check.current = null
    setChecking(false)
    void approve(attempt)
  }, [approve, paymentReady])

  useEffect(() => {
    if (!checking) return
    if (!active && check.current) {
      check.current = null
      setChecking(false)
      return
    }
    const timer = setTimeout(() => {
      check.current = null
      setChecking(false)
      resetAuth('NO_RESPONSE')
      setStage({ kind: 'ready', notice: '기기 활성 상태 응답을 받지 못했어요. 다시 인증해 주세요.' })
    }, 8000)
    return () => clearTimeout(timer)
  }, [checking, active, resetAuth])

  useEffect(() => {
    if (stage.kind !== 'uncertain') return
    const timer = setTimeout(() => setReplayExpired(true), Math.max(0, stage.attempt.startedAt + retryWindow - Date.now()))
    return () => clearTimeout(timer)
  }, [stage])

  const order = stage.kind === 'ready' ? selection?.order : stage.attempt.selection.order
  // 재인증 중(busy)에는 활성 시간이 남아 있어도 제출하지 않는다(A1)
  const canSubmit = stage.kind === 'ready' && !stage.blocked && auth.active && !auth.busy && !checking && !auth.checking &&
    selection?.order.status === 'CREATED' && Boolean(selection.order.orderId) &&
    Number.isSafeInteger(selection.order.amountWon) && selection.order.amountWon > 0 &&
    (gateway.kind === 'mock' || selection.source === 'server')

  function begin() {
    if (!canSubmit || !selection || check.current || inFlight.current) return
    const attempt: Attempt = {
      mode, selection, scenario: 'approved', preference, key: crypto.randomUUID(), startedAt: Date.now(), body: {},
    }
    check.current = attempt
    setChecking(true)
    auth.checkPayment()
  }

  function restart() {
    auth.reset()
    setStage({ kind: 'ready' })
  }

  if (stage.kind === 'result') return (
    <PaymentResult attempt={stage.attempt} approval={stage.approval} cards={cards}
      gateway={gateway} onReturn={() => onReturn(stage.attempt, stage.approval)} onRestart={restart} />
  )

  if (stage.kind === 'processing' || stage.kind === 'uncertain') {
    const processing = stage.kind === 'processing'
    const canReplay = !replayExpired
    return (
      <section className="payment-page" aria-label="모의 결제">
        <h1>모의 결제</h1>
        <div className="virtual-payment-panel payment-processing" aria-busy={processing}>
          {processing && <span className="payment-spinner" aria-hidden="true" />}
          <h2 role="status">{processing ? '결제 중' : '승인 결과 미확인'}</h2>
          {!processing && <p role="alert">{stage.message} 같은 요청으로 결과를 다시 확인할 수 있어요.</p>}
          {order && <div className="payment-processing-summary">
            <strong>{orderName(order)}</strong><strong>{formatWon(order.amountWon)} <small>원</small></strong>
          </div>}
        </div>
        <div className="payment-footer">
          <p className="payment-disclaimer">실제 금융거래는 발생하지 않습니다.</p>
          {processing ? <button className="payment-primary" disabled>처리 중</button> : (
            <>
              <button className="payment-primary" disabled={!canReplay} onClick={() => {
                if (Date.now() - stage.attempt.startedAt < retryWindow) void approve(stage.attempt)
                else setReplayExpired(true)
              }}>결과 다시 확인</button>
              {!canReplay && <p role="status">24시간 재확인 범위가 지났어요. 주문 상태를 확인해 주세요.</p>}
              {!canReplay && <button className="payment-text-button" onClick={() => onReturn()}>주문 목록으로</button>}
            </>
          )}
        </div>
      </section>
    )
  }

  return (
    <section className="payment-page" role="dialog" aria-label="가상카드 기기 인증">
      <div className="payment-title">
        <button className="payment-back" aria-label="빠른결제로 돌아가기" disabled={checking}
          onClick={() => { auth.reset(); onBack() }}><Icon name="chevron" /></button>
        <h1>카드 사용</h1>
      </div>
      <div className="virtual-payment-panel payment-ready-card">
        <p className="payment-availability" role="status" data-active={auth.active}>
          {auth.active ? '● 결제 가능' : auth.busy ? '기기 인증 중' : '결제 대기'}
        </p>
        {auth.active ? (
          <div className="payment-timer" aria-label="남은 활성 시간">
            <svg viewBox="0 0 116 116" aria-hidden="true">
              <circle cx="58" cy="58" r="56" className="payment-timer-track" />
              <circle cx="58" cy="58" r="56" pathLength="100" strokeDasharray="100"
                strokeDashoffset={100 - auth.remainingMs / activationDuration * 100} />
            </svg>
            <strong>{Math.ceil(auth.remainingMs / 1000)}<small>초</small></strong>
            <span>남은 활성 시간</span>
          </div>
        ) : (
          <div className="payment-auth-controls">
            <p role="status">{authMessages[auth.reason]}</p>
            <button className="payment-secondary" disabled={auth.busy || (!mockAuth && (auth.reason === 'BROWSER' || auth.reason === 'NO_RESPONSE'))}
              onClick={() => auth.request()}>다시 인증</button>
          </div>
        )}
        {order ? (
          <div className="payment-order">
            <details><summary><span>선택한 주문</span><span>주문 정보 <Icon name="chevron" /></span></summary>
              <dl>
                <div><dt>업종</dt><dd>{merchantLabel(order.merchantClass)}</dd></div>
                <div><dt>결제일</dt><dd>{order.orderDate.replaceAll('-', '.')}</dd></div>
                <div><dt>결제 시간</dt><dd>{order.orderTime ?? '입력 안 함'}</dd></div>
                <div><dt>결제 방식</dt><dd>{paymentMethods[order.paymentMethod]}</dd></div>
              </dl>
            </details>
            <div className="payment-order-total">
              <strong>{orderName(order)}</strong><strong>{formatWon(order.amountWon)}<small> 원</small></strong>
            </div>
            <p>{order.orderDate.replaceAll('-', '.')} · {paymentMethods[order.paymentMethod]}</p>
            {order.status !== 'CREATED' && <p role="status">이미 처리된 주문이에요.</p>}
          </div>
        ) : (
          <div className="payment-order">
            <p>모의결제 탭에서 주문을 선택해 주세요.</p>
            <button className="payment-text-button" onClick={() => onReturn()}>주문 목록 열기</button>
          </div>
        )}
      </div>
      <p className="payment-selection">카드 선택 <span>{selectionLabels[preference.selectionMode]}</span></p>
      <div className="payment-footer">
        {stage.notice && <p role="alert">{stage.notice}</p>}
        <button className="payment-primary" disabled={!canSubmit} onClick={begin}>
          {checking ? '활성 상태 확인 중' : '가상 주문서 결제'}
        </button>
      </div>
    </section>
  )
}
