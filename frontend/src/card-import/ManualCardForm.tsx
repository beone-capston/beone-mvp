import { useEffect, useRef, useState } from 'react'
import { AuthApiError } from '../auth/api'
import { Icon } from '../components/Icon'
import type { CardImportAdapter } from './contract'

export function ManualCardForm({ adapter, onBack, onComplete }: {
  adapter: CardImportAdapter; onBack: () => void; onComplete: () => void
}) {
  const [name, setName] = useState('')
  const [product, setProduct] = useState('')
  const [products, setProducts] = useState<Awaited<ReturnType<NonNullable<CardImportAdapter['products']>>>>()
  const [revision, setRevision] = useState(0)
  const [loadError, setLoadError] = useState(false)
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)
  const request = useRef<AbortController | null>(null)
  const heading = useRef<HTMLHeadingElement>(null)
  useEffect(() => { heading.current?.focus(); return () => request.current?.abort() }, [])
  useEffect(() => {
    const controller = new AbortController()
    queueMicrotask(() => {
      if (controller.signal.aborted) return
      void adapter.products?.(controller.signal).then(value => {
        if (!controller.signal.aborted) setProducts(value)
      }, () => { if (!controller.signal.aborted) setLoadError(true) })
    })
    return () => controller.abort()
  }, [adapter, revision])

  return <form className="card-import card-import--editing" onSubmit={async event => {
    event.preventDefault()
    const displayName = name.trim()
    if (!adapter.registerManual || request.current || !displayName || displayName.length > 30) return
    const controller = new AbortController()
    request.current = controller
    setPending(true)
    setError('')
    try {
      await adapter.registerManual({
        displayName,
        ...(product === 'unsupported' ? { notSupported: true } : product ? { cardProductId: product } : {}),
      }, controller.signal)
      if (!controller.signal.aborted) onComplete()
    } catch (problem) {
      if (!controller.signal.aborted) setError(problem instanceof AuthApiError && problem.code === 'USER_CARD_DUPLICATED'
        ? '이미 등록된 카드 상품이에요.' : '카드를 등록하지 못했어요. 목록을 확인한 뒤 다시 시도해 주세요.')
    } finally {
      request.current = null
      if (!controller.signal.aborted) setPending(false)
    }
  }}>
    <h1 ref={heading} tabIndex={-1}><button className="card-import-back" type="button" onClick={onBack} disabled={pending}
      aria-label="카드 가져오기로 돌아가기"><Icon name="chevron" /></button>카드 직접 등록</h1>
    <section className="card-import-connect">
      <label>표시 이름
        <input value={name} maxLength={30} onChange={event => setName(event.target.value)} placeholder="카드번호를 제외한 이름을 입력해 주세요" disabled={pending} />
      </label>
      <label>카드 상품
        <select value={product} onChange={event => setProduct(event.target.value)} disabled={pending}>
          <option value="">아직 모름</option>
          {products?.map(item => <option key={item.cardProductId} value={item.cardProductId}>{item.cardName}</option>)}
          <option value="unsupported">목록에 없는 카드</option>
        </select>
      </label>
      {loadError && <button type="button" className="preference-retry" onClick={() => {
        setLoadError(false)
        setRevision(value => value + 1)
      }}>카드 상품 다시 조회</button>}
      {error && <p className="card-import-error" role="alert">{error}</p>}
    </section>
    <div className="card-import-actions">
      <button type="submit" className="primary-button" disabled={pending || !name.trim()}>{pending ? '등록 중' : '등록하기'}</button>
    </div>
  </form>
}
