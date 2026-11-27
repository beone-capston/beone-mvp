import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import type { PreferenceCard } from '../preferences/preferenceApi'
import { formatWon, orderName } from '../orders/order'
import { Sheet } from '../components/Sheet'
import { PaymentError } from './contract'
import { Icon } from '../components/Icon'
import type { Amount, Approval, Attempt, PaymentGateway, Recommendation, Rule } from './contract'

function benefit(amount: Amount) {
  return amount.amountWon === null ? '확인 필요' : formatWon(amount.amountWon) + '원'
}

function ruleDetails(rule: Rule | null) {
  if (!rule) return <p>표시할 적용 규칙 정보가 없어요.</p>
  return (
    <>
      <p>규칙 {rule.ruleVersion} · {rule.verificationStatus}</p>
      <ul>
        {rule.officialSources.map(source => (
          <li key={source.uri + source.locator}>
            {/^https?:\/\//i.test(source.uri) ? <a href={source.uri}>{source.locator} 약관 보기</a> : source.locator}
          </li>
        ))}
      </ul>
    </>
  )
}

function Section({ title, note, children }: { title: string; note?: string; children: ReactNode }) {
  return (
    <details>
      <summary>{title}<span>{note}<Icon name="chevron" /></span></summary>
      <div className="payment-result-content">{children}</div>
    </details>
  )
}

const reasons: Record<string, string> = {
  MONTHLY_STATE_CHANGED: '월간 상태 변경', MAPPING_CHANGED: '카드 매핑 변경',
  MONTHLY_TOTAL: '월간 총혜택 기준', CURRENT_ORDER: '현재 주문 혜택 기준',
  REPRESENTATIVE_CARD: '대표 카드 우선', CARD_ID: '동률 카드 ID 순서',
}
const differenceFields: Record<string, string> = {
  'selected.userCardId': '적용 카드',
  'selected.currentOrderBenefit.amountWon': '이번 주문 예상 혜택',
}

export function PaymentResult({ approval, attempt, gateway, cards, onReturn, onRestart }: {
  approval: Approval; attempt: Attempt; gateway: PaymentGateway; cards: readonly PreferenceCard[]
  onReturn: (cancelled?: boolean) => void; onRestart: () => void
}) {
  const [comparison, setComparison] = useState<Recommendation>()
  const [comparisonError, setComparisonError] = useState(false)
  const [request, setRequest] = useState(0)
  const [confirming, setConfirming] = useState(false)
  const [cancelling, setCancelling] = useState(false)
  const [cancelError, setCancelError] = useState('')
  const [cancelled, setCancelled] = useState(false)
  const cancelRequest = useRef<{ key: string; inFlight: boolean; uncertain: boolean } | null>(null)

  async function cancelPayment() {
    if (!gateway.cancel || cancelled || approval.cancellableAmountWon <= 0 || cancelRequest.current?.inFlight) return
    const attemptRequest = cancelRequest.current ??= { key: crypto.randomUUID(), inFlight: false, uncertain: false }
    attemptRequest.inFlight = true
    setCancelling(true)
    setCancelError('')
    try {
      await gateway.cancel(attempt.mode, approval.simulatedPaymentId, approval.cancellableAmountWon, attemptRequest.key)
      setCancelled(true)
      setConfirming(false)
    } catch (error) {
      attemptRequest.uncertain = !(error instanceof PaymentError) || error.uncertain
      if (!attemptRequest.uncertain) cancelRequest.current = null
      setCancelError(attemptRequest.uncertain ? '취소 결과를 확인하지 못했어요. 다시 확인해 주세요.' : '결제를 취소하지 못했어요. 다시 시도해 주세요.')
    } finally {
      attemptRequest.inFlight = false
      setCancelling(false)
    }
  }

  const success = approval.status === 'APPROVED'
  const reference = approval.recalculation.recommendationId
  const fixed = approval.selectionMode === 'FIXED_REPRESENTATIVE'
  const chosen = comparison?.comparedCards.find(card => card.userCardId === approval.userCardId)
  const optimum = comparison?.comparedCards.find(card =>
    card.userCardId === comparison.fixedCardComparison?.optimalUserCardId)
  const cardName = chosen?.displayName ?? cards.find(card => card.id === approval.userCardId)?.name ??
    '카드 정보 확인 필요 (' + approval.userCardId + ')'

  useEffect(() => {
    if (!success || !reference) return
    let active = true
    gateway.recommendation(attempt.mode, reference, approval.orderId).then(result => {
      if (active) setComparison(result)
    }, () => {
      if (active) setComparisonError(true)
    })
    return () => { active = false }
  }, [approval.orderId, attempt.mode, gateway, reference, request, success])

  const comparisonFeedback = comparisonError ? (
    <div role="status">비교 정보를 불러오지 못했어요.
      <button className="payment-text-button" onClick={() => {
        setComparisonError(false)
        setRequest(value => value + 1)
      }}>비교 다시 조회</button>
    </div>
  ) : !comparison && reference ? <p role="status">비교 정보 조회 중…</p> : null

  return (
    <section className="payment-page payment-result" aria-label="모의 결제 결과">
      <h1>모의 결제 결과</h1>
      <div className="virtual-payment-panel payment-result-card">
        <h2 role="status">{success && <span className="payment-check"><Icon name="check" /></span>}
          {cancelled ? '모의 결제 취소 완료' : success ? '모의 승인 완료' : '모의 승인 실패'}
        </h2>
        <p className="payment-result-merchant">{orderName(attempt.selection.order)}</p>
        <p className="payment-result-price">{formatWon(approval.amountWon)}<small>원</small></p>
        {success ? <>
          <div className="payment-result-card-info">
            <div><small>승인 카드</small><strong>{cardName}</strong></div>
            <span>{fixed ? '대표 카드 고정' : '자동 추천'}</span>
          </div>
          <div className="payment-benefit">
            <span>이번 주문 예상 혜택</span><strong>{benefit(approval.currentOrderBenefit)}</strong>
          </div>
        </> : <p className="payment-result-failure">서비스 내부의 모의 승인에서 실패했어요. 주문 상태는 유지돼요.</p>}
      </div>
      {success && <div className="payment-result-sections">
        <Section title="선택 이유 · 카드비교" note={approval.conditional ? '조건부 추천' : undefined}>
          {fixed && <p>결제 전에 설정한 대표 카드가 적용됐어요.</p>}
          {approval.conditional && <p>확인되지 않은 카드 상태에 따라 추천이 달라질 수 있어요.</p>}
          {approval.selectionBasis?.decidedBy && <p>선택 근거 · {reasons[approval.selectionBasis.decidedBy] ?? approval.selectionBasis.decidedBy}</p>}
          {Boolean(approval.selectionBasis?.tiedUserCardIds.length) &&
            <p>동률 카드 · {approval.selectionBasis?.tiedUserCardIds.join(', ')}</p>}
          {comparisonFeedback}
          {comparison && <>
            <ul>
              {comparison.comparedCards.map(card => <li key={card.userCardId}>{card.displayName} · {benefit(card.currentOrderBenefit)}</li>)}
              {comparison.excludedCards.map(card => <li key={card.userCardId}>{card.userCardId} · 비교 제외 · {card.detail ?? card.reason}</li>)}
            </ul>
            {fixed && optimum && <p>계산상 최적 카드 · {optimum.displayName}</p>}
            {comparison.fixedCardComparison?.differenceWon != null &&
              <p>대표 카드와 비교 카드의 차이 · {formatWon(comparison.fixedCardComparison.differenceWon)}원</p>}
          </>}
        </Section>
        <Section title="혜택 상세 · 약관 근거">
          {approval.mappingNote && <p>{approval.mappingNote}</p>}
          {ruleDetails(approval.appliedRule)}
          {chosen?.mapping?.note && chosen.mapping.note !== approval.mappingNote && <p>{chosen.mapping.note}</p>}
          {chosen?.services.map(service => <div key={service.serviceId} className="payment-service">
            <strong>{service.serviceId} · {benefit(service.contribution)}</strong>
            <p>{service.applicationStatus} · {service.adoption}</p>
            {service.limits.map(limit => <p key={limit.limitBucketId}>
              한도 {limit.limitWon === null ? '확인 필요' : formatWon(limit.limitWon) + '원'} ·
              {' '}입력 사용액 {limit.usedBeforeWon.value === null ? '확인 필요' : formatWon(limit.usedBeforeWon.value) + '원'}
              {' '}({limit.usedBeforeWon.source}) · 남은 한도 {limit.remainingAfterWon === null ? '확인 필요' : formatWon(limit.remainingAfterWon) + '원'}
            </p>)}
            {service.usageNote && <p>{service.usageNote}</p>}
          </div>)}
          {comparison?.notes.map(note => <p key={note}>{note}</p>)}
        </Section>
        <Section title="승인 정보 · 재계산 내역">
          <p>승인 시각 · {approval.approvedAt}</p><p>모의 결제 ID · {approval.simulatedPaymentId}</p>
          <p>주문 ID · {approval.orderId}</p>
          <p>{approval.recalculation.changed === null ? '이전 추천과 비교하지 않았어요.'
            : approval.recalculation.changed ? '승인 직전 재계산 결과가 달라졌어요.' : '이전 추천과 동일해요.'}</p>
          <ul>
            {approval.recalculation.differences.map(change => <li key={change.field}>
              {differenceFields[change.field] ?? change.field} · {change.before ?? '확인 필요'} → {change.after ?? '확인 필요'}
              {' '}({reasons[change.reason] ?? change.reason})
            </li>)}
          </ul>
        </Section>
      </div>}
      {success && !cancelled && approval.cancellableAmountWon > 0 &&
        <button className="payment-cancel" disabled={!gateway.cancel} onClick={() => setConfirming(true)}>결제 취소</button>}
      {confirming && <Sheet title="결제 취소" onClose={() => {
        if (!cancelling) setConfirming(false)
      }}>
        <p className="sheet-description">{formatWon(approval.cancellableAmountWon)}원을 전액 취소할까요?</p>
        {cancelError && <p className="sheet-description" role="alert">{cancelError}</p>}
        <button type="button" className="primary-button" disabled={cancelling} onClick={() => void cancelPayment()}>
          {cancelling ? '취소 중' : '결제 취소 확인'}
        </button>
      </Sheet>}
      <div className="payment-footer">
        <p className="payment-disclaimer">실제 금융거래는 발생하지 않습니다.</p>
        {!success && <button className="payment-secondary" onClick={onRestart}>새 승인 시도</button>}
        <button className="payment-primary" onClick={() => onReturn(cancelled)}>주문 목록으로</button>
      </div>
    </section>
  )
}
