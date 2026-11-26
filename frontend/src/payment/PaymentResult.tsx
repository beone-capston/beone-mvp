import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import type { WalletCard } from '../data/portfolio'
import { formatWon, orderName } from '../orders/order'
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
  approval: Approval; attempt: Attempt; gateway: PaymentGateway; cards: readonly WalletCard[]
  onReturn: () => void; onRestart: () => void
}) {
  const [comparison, setComparison] = useState<Recommendation>()
  const [comparisonError, setComparisonError] = useState(false)
  const [request, setRequest] = useState(0)
  const success = approval.status === 'APPROVED'
  const reference = approval.recalculation.recommendationId
  const fixed = approval.selectionMode === 'FIXED_REPRESENTATIVE'
  const chosen = comparison?.comparedCards.find(card => card.userCardId === approval.userCardId)
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
          {success ? '모의 승인 완료' : '모의 승인 실패'}
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
      {success && <button className="payment-cancel" disabled title="결제 취소는 준비 중이에요.">결제 취소</button>}
      <div className="payment-footer">
        <p className="payment-disclaimer">{gateway.kind === 'mock' ? '개발용 합성 결과 · ' : ''}실제 금융거래는 발생하지 않습니다.</p>
        {!success && <button className="payment-secondary" onClick={onRestart}>새 승인 시도</button>}
        <button className="payment-primary" onClick={onReturn}>주문 목록으로</button>
      </div>
    </section>
  )
}
