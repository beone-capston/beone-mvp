import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { Icon } from '../components/Icon'
import type { WalletCard } from '../data/portfolio'
import { bucketAmounts, demoMode, matchesCatalog, offlineCatalog, readMonth, readProduct, requestMessage, seoulToday, staticRuleApplies } from './api'
import type { MonthlyState } from './api'
import { bucketName, catalog, details, otherServices } from './catalog'
import type { Bucket, Product, ProductId, Service } from './catalog'
import { merchantLabel } from './merchantLabels'
import './card-services.css'

const won = (value: number) => value.toLocaleString('ko-KR') + '원'
const performanceLabel = (value: number) => value % 10000 === 0 ? (value / 10000) + '만원' : won(value)
const tierLabel = (value: number, next?: number) => next ?
  `${performanceLabel(value)} 이상 ~ ${performanceLabel(next)} 미만` : `${performanceLabel(value)} 이상`

function Row({ label, children }: { label: string; children: ReactNode }) {
  return <div className="services-row"><dt>{label}</dt><dd>{children}</dd></div>
}

function calculation(tier: Service['tiers'][number], fuel = false) {
  const value = tier.calculation.value
  if (!value) return fuel ? '리터당 100원' : '확인 필요'
  if (value.type === 'RATE' && value.basisPoints !== undefined) return value.basisPoints / 100 + '%'
  return value.won === undefined ? '확인 필요' : won(value.won)
}

function BucketState({ product, bucket, month, state, unsupported = false }: {
  product: Product; bucket: Bucket; month: string; state?: MonthlyState; unsupported?: boolean
}) {
  const { used, remaining, source, updatedAt, belowTier } = bucketAmounts(product, bucket.id, month, state)
  const sourceLabel = ({ MANUAL: '수동 입력', SYNTHETIC: '가상 데이터', INFERRED: '추정' } as Record<string, string>)[source ?? ''] ?? source

  return <>
    <Row label="이번 달 사용 혜택액">
      {used === null ? '확인 필요' : <>
        {won(used)}<small>{sourceLabel}{updatedAt && ` · ${updatedAt.slice(0, 10)} 기준`}</small>
      </>}
    </Row>
    <Row label="잔여 한도">
      {unsupported ? '계산 미지원' : belowTier ? '실적 미충족' :
        remaining === null ? '확인 필요' : <>{won(remaining)}<small>조회 기준 버킷 잔액</small></>}
    </Row>
  </>
}

function isShared(product: Product, bucket: Bucket) {
  return product.limitBuckets.some(item => item.parent === bucket.id) ||
    product.services.filter(service => service.limitBuckets.includes(bucket.id)).length > 1
}

function ServiceItem({ product, service, month, asOf, monthly, server }: {
  product: Product; service: Service; month: string; asOf: string; monthly?: MonthlyState
  server?: Awaited<ReturnType<typeof readProduct>>['services'][number]
}) {
  const id = product.cardId as ProductId
  const detail = details[id][service.id]
  const name = server?.name && server.name !== service.id ? server.name : detail.name
  const target = detail.target ?? (service.target.classes?.map(merchantLabel).join('·') || '전체 가맹점')
  const bucket = product.limitBuckets.find(item => service.limitBuckets.includes(item.id))
  const shared = bucket && isShared(product, bucket)
  const ownBucket = shared ? undefined : bucket
  const sharedId = shared ? bucket.id : bucket?.parent
  const unsupported = server ? server.supportStatus.startsWith('UNSUPPORTED') : service.requiresSelection
  const isBase = id === '09271' && service.id === 'BASE'
  const headline = calculation(service.tiers[0], id === '09271' && service.id === 'FUEL')
  const kind = service.kind === 'POINT_ACCRUAL' ? '적립' : '할인'
  const tiers = ownBucket?.tiers.length && ownBucket.tiers.length >= service.tiers.length ? ownBucket.tiers : service.tiers
  const minimum = service.tiers[0].minimumPerformance
  const exclusions = [...service.excludedClasses.map(code => code === 'TRANSIT_INTERCITY_BUS' ? '시외·고속버스' : merchantLabel(code)), ...service.unevaluatedExclusions]
  if (id === '01664' && service.id !== 'TRANSIT' && service.id !== 'TELECOM') {
    exclusions.push('상품 페이지 기준 입점·결제대행(PG) 전용 가맹점 제외. 상품권·선불카드 구매는 제외될 수 있음.')
  }
  if (detail.note) exclusions.push(detail.note)
  const chosen = monthly?.selectedServices.filter(item => item.usable && item.effectiveFrom <= asOf)
  const selection = monthly?.serviceSelectionStatus === 'NONE' && monthly.noServiceSelection?.usable &&
    monthly.noServiceSelection.effectiveFrom <= asOf ? '선택 없음' :
    monthly?.serviceSelectionStatus === 'SELECTED' && chosen?.length === 1 ?
      chosen[0].serviceId === service.id ? '선택됨' : '선택 안 됨' : '확인 필요'

  return <article className="services-item" aria-label={name}>
    <div className="services-item-heading">
      <h3>{name}</h3>
      <p>{service.tiers.length === 1 ? <>
        <strong>{headline.replace(/%$/, '')}</strong>
        <small>{headline.endsWith('%') ? '% ' : ' '}{kind}</small>
      </> : <small>건당 정액 {kind}</small>}</p>
    </div>
    <dl>
      <Row label="대상">{target}</Row>
      {detail.payment && <Row label="결제 방식">{detail.payment}</Row>}
      {tiers.length === 1 && <Row label="전월 실적">{minimum ? tierLabel(minimum) : '조건 없음'}</Row>}
      {(service.minimumAmount > 0 || id === '09174') &&
        <Row label="최소 결제금액">{service.minimumAmount ? won(service.minimumAmount) : '등록 규칙상 제한 없음'}</Row>}
      {detail.noAmountLimits && <Row label="최소 결제금액 · 대상 상한">상품설명서 기준 제한 없음</Row>}
      {(detail.targetLimit || service.maximumTargetAmount) &&
        <Row label="할인 대상금액 상한">{detail.targetLimit ?? (service.maximumTargetAmount !== null && won(service.maximumTargetAmount))}</Row>}
      {detail.maximumBenefit && <Row label="건당 최대 할인액">{won(detail.maximumBenefit)}</Row>}
      {detail.frequency && <Row label="횟수 제한">{detail.frequency}</Row>}
      {isBase && <Row label="월 적립 한도">제한 없음</Row>}
      {ownBucket && tiers.length === 1 &&
        <Row label="월 할인 한도">{won(ownBucket.tiers[0].monthlyCap)}</Row>}
      {sharedId && <Row label="공유 한도"><a href={'#services-limit-' + sharedId}>{bucketName(sharedId)} 적용</a></Row>}
      {service.requiresSelection && <Row label="택1 서비스">주유·생활업종 중 하나 선택<small>현재 상태 · {selection}</small><small>최초 등록 다음 달부터 변경 신청 가능. 신청 다음 달 1일 적용.</small></Row>}
      {unsupported && <Row label="앱 계산">계산 미지원</Row>}
    </dl>
    {tiers.length > 1 && <dl className="services-tiers" aria-label="전월 실적 구간별 조건">
      {tiers.map((tier, index) => {
        const calc = service.tiers.findLast(item => item.minimumPerformance <= tier.minimumPerformance)
        const cap = ownBucket?.tiers.find(item => item.minimumPerformance === tier.minimumPerformance)
        return <Row key={tier.minimumPerformance} label={tierLabel(tier.minimumPerformance, tiers[index + 1]?.minimumPerformance)}>
          {service.tiers.length > 1 && calc && <span>건당 {calculation(calc)} {kind}</span>}
          {cap && <span>월 {won(cap.monthlyCap)}</span>}
        </Row>
      })}
    </dl>}
    <dl>
      {ownBucket && <BucketState product={product} bucket={ownBucket} month={month} state={monthly} unsupported={unsupported} />}
      {shared && <Row label="사용 혜택액·잔여 한도"><a href={'#services-limit-' + bucket.id}>{bucketName(bucket.id)}에서 확인</a></Row>}
      {isBase && <Row label="이번 달 적립액">확인 필요</Row>}
      {(detail.payout || id === '01664' || id === '01914') &&
        <Row label="지급 방식">{detail.payout ?? (id === '01664' && service.id === 'TRANSIT' ?
          '시내버스·지하철 청구할인, 고속·시외버스 환급할인' : '환급할인')}</Row>}
    </dl>
    {exclusions.length > 0 && <div className="services-exclusions">
      <h4>제외·주의 항목</h4>
      <ul>{exclusions.map(item => <li key={item}>{item}</li>)}</ul>
    </div>}
  </article>
}

function Common({ product, month, monthly }: { product: Product; month: string; monthly?: MonthlyState }) {
  const policy = product.policy
  const grace = policy.newCardGrace.value
  const id = product.cardId as ProductId
  return <section className="services-box services-common">
    <h2>공통 안내</h2>
    <dl>
      <Row label="실적 산정 기준">{id === '01664' ? '국내 이용일 기준' : '국내 승인금액 기준, 해외는 매입 완료 기준'}
        {id === '01914' && <small>첵첵·위글위글 첵첵 동일 계열 카드 포함</small>}
      </Row>
      <Row label="실적 제외">{policy.performanceExcludedClasses.map(merchantLabel).join('·')}
        {policy.excludesBenefitedSales.value && '·할인받은 이용금액 전체'}
        {id === '01664' && '·무승인금액(상품 페이지 기준)'}
      </Row>
      <Row label="신규 발급 유예">
        {grace.span === 'DAYS_FROM_REGISTRATION' ? '사용등록일 포함 60일' : '사용등록일부터 다음 달 말일까지'} ·{' '}
        {performanceLabel(grace.deemedPerformance)} 구간 적용
        <small>대상 · {grace.services.map(item => details[id][item].name).join('·')}</small>
        {id === '01664' && <small>월 10,000원 이내, 교통·통신·금융수수료 제외</small>}
      </Row>
      <Row label="한도 이월">이월 없음</Row>
      {(id === '01664' || id === '01914') && <Row label="한도 차감 순서">매입 순서, 동일 계열 카드 합산</Row>}
      <Row label="취소·환불">
        {policy.cancellationAttribution.value === 'RECEIPT_MONTH' ? '취소 전표가 접수된 달의 실적에 반영' : '취소금액은 실적에서 제외, 귀속월 확인 필요'}
        {(id === '09174' || id === '01664') && <small>취소 전표가 매입되기 전까지 한도 복원이나 할인이 지연될 수 있어요.</small>}
      </Row>
    </dl>
    {product.limitBuckets.filter(bucket => isShared(product, bucket)).map(bucket =>
      <section key={bucket.id} className="services-shared" id={'services-limit-' + bucket.id}>
        <h3>{bucketName(bucket.id)}</h3>
        <dl>
          {bucket.tiers.map((tier, index) => <Row key={tier.minimumPerformance} label={tierLabel(tier.minimumPerformance, bucket.tiers[index + 1]?.minimumPerformance)}>
            {won(tier.monthlyCap)}
          </Row>)}
          <BucketState product={product} bucket={bucket} month={month} state={monthly} />
        </dl>
      </section>)}
    <div className="services-sources">
      <h3>공식 상품설명서·약관과 출처</h3>
      <ul>{product.sources.map((source, i) => <li key={source.uri}>
        <a href={source.uri} target="_blank" rel="noreferrer">{i === 0 ? 'KB국민카드 상품설명서' : id === '09271' ? 'KB국민 신용카드 설명서' : 'KB국민카드 공식 상품 안내'}</a>
        <small>{source.locator}</small>
      </li>)}</ul>
      <p>규칙 버전 {product.ruleVersion} · {product.validity.from}부터 적용</p>
    </div>
  </section>
}

type Result = {
  product?: Awaited<ReturnType<typeof readProduct>>
  monthly?: MonthlyState
  productError?: string
  monthError?: string
}

export function CardServicesPage({ card, onClose }: { card: WalletCard; onClose: () => void }) {
  const [asOf] = useState(seoulToday)
  const month = asOf.slice(0, 7)
  const [result, setResult] = useState<Result>({})
  const [attempt, setAttempt] = useState(0)
  const id = card.cardProductId
  const product = id && Object.hasOwn(catalog, id) ? catalog[id as ProductId] : undefined
  const extra = product && otherServices[product.cardId as ProductId]

  useEffect(() => {
    if (!product || offlineCatalog) return
    const controller = new AbortController()
    const { signal } = controller
    readProduct(product.cardId, asOf, signal).then(
      value => { if (!signal.aborted) setResult(current => ({ ...current, product: value })) },
      error => { if (!signal.aborted) setResult(current => ({ ...current, productError: requestMessage(error) })) },
    )
    if (demoMode) readMonth(product.cardId, month, signal).then(
      value => { if (!signal.aborted) setResult(current => ({ ...current, monthly: value })) },
      error => { if (!signal.aborted) setResult(current => ({ ...current, monthError: requestMessage(error) })) },
    )
    return () => controller.abort()
  }, [asOf, attempt, month, product])

  function retry() {
    setResult({})
    setAttempt(value => value + 1)
  }

  const message = !product ? '이 카드의 부가서비스 안내는 준비 중이에요.' :
    result.productError ?? (offlineCatalog ? !staticRuleApplies(product, asOf) && '기준일에 유효한 규칙이 없어요.' :
      !result.product ? '상품 정보를 불러오는 중이에요.' :
      result.product.ruleStatus !== 'VERIFIED' ? '기준일에 유효한 규칙이 없어요.' :
      !matchesCatalog(product, result.product) && '현재 서버 규칙과 상세 안내 버전이 달라 혜택 조건을 표시할 수 없어요.')

  return <section className="services-page" aria-labelledby="card-services-title">
    <h1 id="card-services-title"><button type="button" aria-label="빠른결제로 돌아가기" onClick={onClose}><Icon name="chevron" /></button>부가서비스</h1>
    {message ? <div className="services-box services-status" role="status">
      {message}{result.productError && <button onClick={retry}>다시 시도</button>}
    </div> : product && <>
      <section className="services-summary" aria-label="카드 요약">
        <div className="services-card-heading">
          <div className={'services-mini-card ' + card.theme} aria-hidden="true"><span>{card.caption}</span><i /><b>KB국민</b></div>
          <div><p>KB국민카드·{card.type}카드</p><h2>{result.product?.cardName ?? card.name}</h2></div>
        </div>
        <div className="services-badges">
          <span>혜택 {product.services.length}개</span>
          {extra && <span>기타 서비스 1개</span>}
          <span>{product.services.some(service => service.tiers[0].minimumPerformance === 0) ?
            '기본 적립 실적 조건 없음' : `전월 실적 ${performanceLabel(Math.min(...product.services.map(service => service.tiers[0].minimumPerformance)))}부터`}</span>
        </div>
      </section>
      <section className="services-box services-period">
        <h2>실적·한도 기준</h2>
        <dl>
          <Row label="실적 산정 기간">전월 1일 ~ 말일</Row>
          <Row label="한도 기간">매월 1일 ~ 말일</Row>
        </dl>
      </section>
      <section aria-labelledby="services-list-heading">
        <h2 className="services-section-title" id="services-list-heading">혜택 및 이용 조건</h2>
        <div className="services-box services-list">
          {product.services.map(service => <ServiceItem key={service.id} product={product} service={service}
            monthly={result.monthly} month={month} asOf={asOf} server={result.product?.services.find(item => item.serviceId === service.id)} />)}
          {extra && <article className="services-item" aria-label={extra.name}>
            <div className="services-item-heading"><h3>{extra.name}</h3><small>기타 서비스</small></div>
            <dl><Row label="대상">{extra.target}</Row><Row label="이용 조건">{extra.performance}</Row></dl>
            {extra.note && <p className="services-exclusions">{extra.note}</p>}
          </article>}
        </div>
      </section>
      <div className="services-month-status">
        <p>사용액 조회 기준 · {month.replace('-', '년 ')}월</p>
        <p>{result.monthError ?? (offlineCatalog ? '상품설명서 기준 · 월 사용액은 API 연결 후 조회할 수 있어요.' :
          !demoMode ? '보유 카드의 월 사용액은 로컬·데모 API에서 조회할 수 있어요.' :
          !result.monthly ? '월 사용액을 불러오는 중이에요.' :
          '가상 포트폴리오 조회값이에요. 스냅샷 이후 승인·취소가 있으면 현재 잔액은 확인이 필요해요.')}</p>
        {result.monthError && <button onClick={retry}>다시 시도</button>}
      </div>
      <Common product={product} month={month} monthly={result.monthly} />
    </>}
    <footer className="services-footer"><button className="primary-button" onClick={onClose}>확인</button></footer>
  </section>
}
