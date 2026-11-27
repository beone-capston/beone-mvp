import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { ManualCardForm } from './ManualCardForm'
import { AuthApiError } from '../auth/api'
import { Icon } from '../components/Icon'
import { cardDesigns } from '../data/portfolio'
import type { CardImportAdapter, SyncResponse } from './contract'
import type { ImportedCardRepository } from './repository'
import './card-import.css'

export type CardImport = { adapter: CardImportAdapter; repository?: ImportedCardRepository }
type Phase = 'editing' | 'connecting' | 'querying' | 'failed' | 'results' | 'registering'

const steps = ['카드사 계정 연결', '보유 카드·이용정보 조회', '내 카드 목록 반영']

export function CardImportPage({ importer, onClose }: { importer: CardImport; onClose: () => void }) {
  const { adapter, repository } = importer
  const [manual, setManual] = useState(false)
  const [phase, setPhase] = useState<Phase>('editing')
  const [loginId, setLoginId] = useState('')
  const [password, setPassword] = useState('')
  const [consent, setConsent] = useState(false)
  const [connected, setConnected] = useState(false)
  const [result, setResult] = useState<SyncResponse>()
  const [selected, setSelected] = useState<string[]>([])
  const [failed, setFailed] = useState<string[]>([])
  const [registered, setRegistered] = useState(() => repository?.list().flatMap(card => card.cardProductId ?? []) ?? [])
  const [failureText, setFailureText] = useState('')
  const operation = useRef<AbortController | null>(null)
  const heading = useRef<HTMLHeadingElement>(null)
  const screen = phase === 'connecting' || phase === 'querying' ? 'loading'
    : phase === 'registering' ? 'results' : phase
  const invalidId = /^\s|\s$|[\p{Cc}\p{Cf}\p{Cs}\p{Cn}\p{Zl}\p{Zp}\uFFFD]/u.test(loginId)
  const invalidPassword = /\p{Cc}/u.test(password)
  const valid = Boolean(loginId && password && !invalidId && !invalidPassword && consent)

  useEffect(() => () => operation.current?.abort(), [])
  useEffect(() => { heading.current?.focus() }, [screen])

  async function query(event?: FormEvent<HTMLFormElement>) {
    event?.preventDefault()
    if (operation.current || (!connected && !valid)) return
    const controller = new AbortController()
    operation.current = controller
    const { signal } = controller
    setResult(undefined)
    setSelected([])
    setFailed([])
    try {
      if (!connected) {
        setPhase('connecting')
        const input = { organization: '0301', loginType: 'ID', loginId, password } as const
        setPassword('')
        setLoginId('')
        const connection = await adapter.connect(input, signal)
        if (signal.aborted) return
        if (connection.reconnectRequired || connection.status !== 'CONNECTED') throw new Error('RECONNECT_REQUIRED')
        setConnected(true)
      }
      setPhase('querying')
      const response = await adapter.sync(signal)
      if (signal.aborted) return
      if (response.heldCards.fetched === null) throw new Error('HELD_CARDS_UNAVAILABLE')
      setResult(response)
      setPhase('results')
    } catch (error) {
      if (!signal.aborted) {
        const code = error instanceof AuthApiError ? error.code : ''
        if (['CODEF_LOGIN_FAILED', 'CODEF_RECONNECT_REQUIRED', 'CODEF_NOT_CONNECTED'].includes(code) ||
          (error instanceof Error && error.message === 'RECONNECT_REQUIRED')) setConnected(false)
        setFailureText(code === 'NOT_FOUND' ? '이 환경에서는 카드사 연결을 사용할 수 없어요.' : '')
        setPhase('failed')
      }
    } finally {
      operation.current = null
    }
  }

  const candidates = result?.heldCards.newCandidates ?? []
  const rows = candidates.map(candidate => {
    const product = candidate.mappingStatus === 'CANDIDATE'
      ? cardDesigns.find(card => card.cardProductId === candidate.candidateCardProductId)
      : undefined
    return { candidate, product, alreadyRegistered: Boolean(product && registered.includes(product.cardProductId)) }
  })
  const selectable = rows.filter(row => row.product && !row.alreadyRegistered)

  async function register() {
    if (operation.current || phase !== 'results') return
    const pending = selectable.filter(row => selected.includes(row.candidate.userCardId))
    if (!pending.length) return
    const controller = new AbortController()
    operation.current = controller
    const { signal } = controller
    setPhase('registering')
    const incomplete: string[] = []
    try {
      for (const row of pending) {
        const { userCardId } = row.candidate
        const productId = row.product?.cardProductId
        if (!productId) continue
        try {
          const confirmed = await adapter.confirm(userCardId, {
            status: 'USER_CONFIRMED', cardProductId: productId, confirmationMethod: 'CODEF_CANDIDATE',
          }, signal)
          if (signal.aborted) return
          if (confirmed.userCardId !== userCardId || confirmed.mapping.cardProductId !== productId) {
            throw new Error('MAPPING_RESPONSE_MISMATCH')
          }
          repository?.add(confirmed)
          setRegistered(current => [...current, productId])
        } catch {
          if (signal.aborted) return
          incomplete.push(userCardId)
        }
      }
      if (!incomplete.length) {
        onClose()
        return
      }
      setFailed(incomplete)
      setSelected(incomplete)
      setPhase('results')
    } finally {
      operation.current = null
    }
  }

  if (manual) return <ManualCardForm adapter={adapter} onBack={() => setManual(false)} onComplete={onClose} />

  if (screen === 'editing') return (
    <form className="card-import card-import--editing" onSubmit={query} autoComplete="off">
      <h1 ref={heading} tabIndex={-1}>
        <button className="card-import-back" type="button" aria-label="메인으로 돌아가기" onClick={onClose}><Icon name="chevron" /></button>
        카드 추가
      </h1>
      <section className="card-import-connect">
        <h2>카드사 연결하기</h2>
        <label>카드사 홈페이지 아이디
          <input autoComplete="off" autoCapitalize="none" spellCheck={false} value={loginId}
            onChange={event => setLoginId(event.target.value)} placeholder="아이디를 입력해 주세요"
            aria-invalid={invalidId || undefined} aria-describedby={invalidId ? 'card-import-id-error' : repository ? 'card-import-test' : undefined} />
        </label>
        {invalidId && <p id="card-import-id-error" className="card-import-error" role="alert">아이디의 앞뒤 공백과 사용할 수 없는 문자를 확인해 주세요.</p>}
        <label>카드사 홈페이지 비밀번호
          <input type="password" autoComplete="off" value={password} onChange={event => setPassword(event.target.value)}
            placeholder="비밀번호를 입력해 주세요" aria-invalid={invalidPassword || undefined}
            aria-describedby={invalidPassword ? 'card-import-password-error' : repository ? 'card-import-test' : undefined} />
        </label>
        {invalidPassword && <p id="card-import-password-error" className="card-import-error" role="alert">비밀번호에 사용할 수 없는 문자가 있어요.</p>}
        {repository && <p id="card-import-test" className="card-import-test">테스트용 아이디와 비밀번호를 입력해 주세요.</p>}
      </section>
      <section className="card-import-disclosure">
        <h2>연결 후 조회하는 정보</h2>
        <dl>
          <div><dt>보유카드</dt><dd>내 카드 목록</dd></div>
          <div><dt>이용내역</dt><dd>조회 기간 내 승인 내역</dd></div>
          <div><dt>카드사 실적</dt><dd>계산 확정값이 아닌 참고 정보</dd></div>
        </dl>
        <label className="card-import-consent">
          <input type="checkbox" checked={consent} onChange={event => setConsent(event.target.checked)} />
          조회 항목과 연결 안내 동의
        </label>
      </section>
      <div className="card-import-actions">
        <button type="submit" className="primary-button" disabled={!valid}>연결하고 카드 가져오기</button>
        {adapter.registerManual && <button type="button" className="card-import-secondary" onClick={() => {
          setLoginId('')
          setPassword('')
          setConsent(false)
          setManual(true)
        }}>카드 직접 등록</button>}
      </div>
    </form>
  )

  if (screen === 'loading') return (
    <section className="card-import card-import--loading" aria-labelledby="card-loading-title" aria-busy="true">
      <h1 id="card-loading-title" ref={heading} tabIndex={-1}>카드사 연결중</h1>
      <ol className="card-import-steps">
        {steps.map((step, index) => {
          const current = phase === 'connecting' ? 0 : 1
          return <li key={step} aria-current={index === current ? 'step' : undefined} data-complete={index < current || undefined}>
            <span>0{index + 1}</span>{step}{index < current && <span className="sr-only"> 완료</span>}
          </li>
        })}
      </ol>
      <div className="card-import-progress" role="progressbar" aria-label={phase === 'connecting' ? steps[0] : steps[1]}><span /></div>
      <div className="card-import-actions"><button className="primary-button" disabled>카드사 연결 중</button></div>
    </section>
  )

  if (screen === 'failed') return (
    <section className="card-import card-import--failed" aria-labelledby="card-failed-title">
      <span className="card-import-warning" aria-hidden="true">!</span>
      <h1 id="card-failed-title" ref={heading} tabIndex={-1}>카드를<br />가져오지 못했어요</h1>
      <p className="card-import-failure-note" role="alert">
        {failureText || (connected ? '잠시 후 카드 가져오기를 다시 시도해주세요' : '연결 정보를 확인한 뒤 다시 시도해주세요')}
      </p>
      <div className="card-import-actions">
        <button className="primary-button" onClick={() => {
          if (connected) void query()
          else {
            setConsent(false)
            setPhase('editing')
          }
        }}>다시 가져오기</button>
        {adapter.registerManual && <button type="button" className="card-import-secondary" onClick={() => {
          setPhase('editing')
          setConsent(false)
          setManual(true)
        }}>카드 직접 등록</button>}
        <button className="card-import-secondary" onClick={onClose}>메인으로 돌아가기</button>
      </div>
    </section>
  )

  return (
    <section className="card-import card-import--results" aria-labelledby="card-results-title" aria-busy={phase === 'registering'}>
      <h1 id="card-results-title" ref={heading} tabIndex={-1}>내 카드를 가져왔어요</h1>
      <div className="card-import-list-heading"><h2>가져온 카드</h2><span>{result?.heldCards.fetched}개</span></div>
      {result?.partialFailures.length ? <p className="card-import-error" role="status">이용내역 또는 카드사 실적 일부를 조회하지 못했어요.</p> : null}
      {rows.length === 0 && <p className="card-import-empty">조회된 카드가 없어요.</p>}
      {rows.length > 0 && selectable.length === 0 && <p className="card-import-empty">선택할 수 있는 카드가 없어요.</p>}
      <div className="card-import-list">
        {rows.map(({ candidate, product, alreadyRegistered }) => {
          const disabled = !product || alreadyRegistered
          const id = candidate.userCardId
          return <label className="card-import-row" key={id} data-unavailable={disabled || undefined}>
            <span>
              <strong>{candidate.cardName}</strong>
              {product && product.name !== candidate.cardName && <span className="card-import-product">{product.name}</span>}
              <small>KB국민카드{product ? ` · ${product.type}` : ''}</small>
              <small className="card-import-benefit">{alreadyRegistered ? '이미 등록됨' : product ? '혜택 정보 있음' : '혜택 정보 없음 · 등록 불가'}</small>
              {failed.includes(id) && <small className="card-import-error" role="alert">등록하지 못했어요. 다시 시도해주세요.</small>}
            </span>
            <input type="checkbox" disabled={disabled || phase === 'registering'} checked={selected.includes(id) && !disabled}
              aria-label={candidate.cardName}
              onChange={event => setSelected(current => event.target.checked ? [...current, id] : current.filter(value => value !== id))} />
          </label>
        })}
      </div>
      <div className="card-import-actions">
        <button className="primary-button" onClick={register} disabled={phase === 'registering' || !selectable.some(row => selected.includes(row.candidate.userCardId))}>
          {phase === 'registering' ? '등록 중' : '등록하기'}
        </button>
      </div>
    </section>
  )
}
