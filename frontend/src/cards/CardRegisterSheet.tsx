import { useEffect, useId, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Sheet } from '../components/Sheet'
import type { CardRegistrar } from '../data/portfolio'
import { CardRequestError } from './cardApi'

// 보유 카드 수동 등록 시트. 기존 시트·버튼 스타일만 쓰는 최소 구성이다
const notSupported = 'NOT_SUPPORTED'
const messages: Record<string, string> = {
  validation: '카드 이름을 1~30자로 입력해 주세요.',
  duplicated: '같은 상품의 카드가 이미 등록돼 있어요.',
  product: '선택한 상품을 찾을 수 없어요. 다시 선택해 주세요.',
  unauthenticated: '로그인이 끝났어요. 다시 로그인해 주세요.',
  csrf: '보안 확인에 실패해 등록하지 않았어요. 다시 시도해 주세요.',
  unavailable: '현재 환경에서 카드 등록 API를 사용할 수 없어요.',
}

export function CardRegisterSheet({ registrar, onClose, onRegistered }: {
  registrar: CardRegistrar
  onClose: () => void
  onRegistered: () => void
}) {
  const id = useId()
  const [products, setProducts] = useState<ReadonlyArray<{ cardProductId: string; cardName: string }>>([])
  const [productsFailed, setProductsFailed] = useState(false)
  const [displayName, setDisplayName] = useState('')
  const [product, setProduct] = useState('')
  const [problem, setProblem] = useState('')
  const [pending, setPending] = useState(false)
  const sending = useRef(false)

  useEffect(() => {
    let active = true
    registrar.products().then(
      items => { if (active) setProducts(items) },
      () => { if (active) setProductsFailed(true) },
    )
    return () => { active = false }
  }, [registrar])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const name = displayName.trim()
    if (!name || name.length > 30) {
      setProblem(messages.validation)
      return
    }
    if (sending.current) return
    sending.current = true
    setPending(true)
    setProblem('')
    try {
      await registrar.register(product === notSupported ? { displayName: name, notSupported: true }
        : product ? { displayName: name, cardProductId: product } : { displayName: name })
      onRegistered()
    } catch (error) {
      const kind = error instanceof CardRequestError ? error.kind : 'unexpected'
      setProblem(messages[kind] ?? '카드를 등록하지 못했어요. 잠시 후 다시 시도해 주세요.')
    } finally {
      sending.current = false
      setPending(false)
    }
  }

  return (
    <Sheet title="카드 추가" onClose={onClose}>
      <p className="sheet-description">
        카드번호·CVV·유효기간은 받지 않아요. 카드 이름에도 카드번호를 넣지 마세요.
      </p>
      <form className="order-form" onSubmit={submit} noValidate aria-busy={pending}>
        <label htmlFor={id + 'name'}>카드 이름</label>
        <input id={id + 'name'} value={displayName} maxLength={30} disabled={pending}
          onChange={event => { setDisplayName(event.target.value); setProblem('') }} />
        <label htmlFor={id + 'product'}>카드 상품</label>
        <select id={id + 'product'} value={product} disabled={pending}
          onChange={event => setProduct(event.target.value)}>
          <option value="">아직 모름(추천에서 빠짐)</option>
          {products.map(item => <option key={item.cardProductId} value={item.cardProductId}>{item.cardName}</option>)}
          <option value={notSupported}>목록에 없는 카드(추천에서 빠짐)</option>
        </select>
        {productsFailed && <p className="sheet-description">상품 목록을 불러오지 못했어요. 상품 없이 등록할 수 있어요.</p>}
        {problem && <p role="alert">{problem}</p>}
        <button className="primary-button" type="submit" disabled={pending}>
          {pending ? '등록 중…' : '카드 등록'}
        </button>
      </form>
    </Sheet>
  )
}
