import { useId, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Icon } from '../components/Icon'
import { MerchantSheet } from './MerchantSheet'
import { merchantLabel } from './merchantClasses'
import { formatWon, paymentMethods } from './order'
import type { OrderDraft, OrderErrors } from './order'

export function OrderForm({ draft, errors, pending, onChange, onSubmit, onBack }: {
  draft: OrderDraft
  errors: OrderErrors
  pending: boolean
  onChange: (field: keyof OrderDraft, value: string) => void
  onSubmit: (event: FormEvent<HTMLFormElement>) => void
  onBack: () => void
}) {
  const [open, setOpen] = useState(false)
  const [amountFocused, setAmountFocused] = useState(false)
  const anchor = useRef<HTMLButtonElement>(null)
  const id = useId()
  const errorId = (field: keyof OrderDraft) => errors[field] ? id + field + '-error' : undefined
  const error = (field: keyof OrderDraft) => errors[field] &&
    <small role="alert" id={errorId(field)}>{errors[field]}</small>
  const amount = Number(draft.amountWon)
  const displayAmount = !amountFocused && /^\d+$/.test(draft.amountWon) && Number.isSafeInteger(amount)
    ? formatWon(amount) : draft.amountWon

  return (
    <section className="order-screen" aria-labelledby={id + 'title'}>
      <div className="order-title order-form-title">
        <button type="button" aria-label="주문 목록으로 돌아가기" disabled={pending} onClick={onBack}>
          <Icon name="chevron" />
        </button>
        <h1 id={id + 'title'}>주문 생성</h1>
      </div>
      <form className="order-form" noValidate onSubmit={onSubmit} aria-busy={pending}>
        <fieldset className="order-form-card" disabled={pending}>
          <div className="order-amount-field">
            <label htmlFor={id + 'amount'}>결제 금액</label>
            <div className="order-amount-input">
              <input id={id + 'amount'} inputMode="numeric" value={displayAmount} placeholder="0"
                aria-invalid={Boolean(errors.amountWon)} aria-describedby={errorId('amountWon')}
                onFocus={() => setAmountFocused(true)} onBlur={() => setAmountFocused(false)}
                onChange={event => onChange('amountWon', event.target.value)} />
              <span>원</span>
            </div>
            {error('amountWon')}
          </div>
          <div className="order-fields">
            <div className="order-field">
              <label htmlFor={id + 'name'}>가맹점명 <span>(선택)</span></label>
              <input id={id + 'name'} value={draft.merchantName} placeholder="가맹점명을 입력해 주세요"
                aria-invalid={Boolean(errors.merchantName)} aria-describedby={errorId('merchantName')}
                onChange={event => onChange('merchantName', event.target.value)} />
              {error('merchantName')}
            </div>
            <div className="order-field">
              <label id={id + 'class'}>가맹점 업종 분류</label>
              <button ref={anchor} type="button" className="order-class-button"
                aria-label="가맹점 업종 분류"
                aria-haspopup="dialog" aria-expanded={open}
                aria-invalid={Boolean(errors.merchantClass)} aria-describedby={errorId('merchantClass')}
                onClick={() => setOpen(true)}>
                <span>{draft.merchantClass ? merchantLabel(draft.merchantClass) : '분류를 선택해 주세요'}</span>
                <Icon name="search" />
              </button>
              {error('merchantClass')}
            </div>
            <div className="order-date-row">
              <div className="order-field">
                <label htmlFor={id + 'date'}>결제일</label>
                <input id={id + 'date'} type="date" value={draft.orderDate}
                  aria-invalid={Boolean(errors.orderDate)} aria-describedby={errorId('orderDate')}
                  onChange={event => onChange('orderDate', event.target.value)} />
                {error('orderDate')}
              </div>
              <div className="order-field">
                <label htmlFor={id + 'time'}>시간 <span>(선택)</span></label>
                <input id={id + 'time'} type="time" step="1" value={draft.orderTime}
                  aria-invalid={Boolean(errors.orderTime)} aria-describedby={errorId('orderTime')}
                  onChange={event => onChange('orderTime', event.target.value)} />
                {error('orderTime')}
              </div>
            </div>
            <div className="order-field" role="group" aria-labelledby={id + 'method'}>
              <span id={id + 'method'} className="order-method-label">결제방식</span>
              <div className="order-methods">
                {Object.entries(paymentMethods).map(([code, label]) => (
                  <button key={code} type="button" aria-pressed={draft.paymentMethod === code}
                    onClick={() => onChange('paymentMethod', code)}>{label}</button>
                ))}
              </div>
              {error('paymentMethod')}
            </div>
          </div>
        </fieldset>
        <button className="order-primary" type="submit" disabled={pending}>{pending ? '주문 생성 중…' : '주문 생성'}</button>
      </form>
      {open && (
        <MerchantSheet anchor={anchor} selected={draft.merchantClass}
          onClose={() => setOpen(false)}
          onSelect={code => { onChange('merchantClass', code); setOpen(false) }} />
      )}
    </section>
  )
}
