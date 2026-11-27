import type { PortfolioMode } from '../data/portfolio'
import type { PaymentDetail } from '../payment/contract'

export type Period = { from: string; to: string }
export type StateValue<T> = { value: T | null; source: string; period: Period | null; updatedAt: string | null; usable: boolean; reason: string | null }
export type CodefPerformanceReference = { currentUseWon: number | null; period: Period; fetchedAt: string | null }
export type HeldCard = { userCardId: string; displayName: string; holdingSource: string; cardProductId?: string | null;
  mapping: { status: string; cardProductId: string | null } | null }
export type ProductSummary = { cardProductId: string; cardName: string; ruleStatus: string }
export type Product = ProductSummary & {
  asOf: string
  rule: { ruleVersion: string; verificationStatus: string; validFrom: string; validTo: string | null; performanceWindow: string | null } | null
  services: { serviceId: string; name: string; benefitKind: string; supportStatus: string;
    limitBucketIds: string[]; requiresSelection: boolean }[]
  limitBuckets: { limitBucketId: string; tiers: { minimumPerformanceWon: number; monthlyCapWon: number }[];
    parentLimitBucketId: string | null; period: string | null; sharedByServiceIds: string[] }[]
}
export type Monthly = {
  userCardId: string; month: string; snapshotAt: string | null
  previousMonthPerformanceWon: StateValue<number> & { codefReference?: CodefPerformanceReference | null }
  limitBucketUsages: (StateValue<number> & { limitBucketId: string })[]
  otherBucketsUsedWon: StateValue<number>; registeredOn: StateValue<string>
  selectedServices: { serviceId: string; effectiveFrom: string; usable: boolean }[]
  serviceSelectionStatus: string; noServiceSelection: { effectiveFrom: string; usable: boolean } | null
  afterSnapshot: { approvalCount: number; approvedAmountWon: number; cancellationCount: number } | null
}
export type LedgerRow = {
  ledgerTransactionId: string; userCardId: string; source: 'SIMULATION' | 'CODEF'
  entryType: 'APPROVAL' | 'CANCELLATION'; amountWon: number; transactionDate: string; transactionTime: string | null
  merchantClass: string; merchantName: string | null; paymentMethod: string
  originalTransactionId: string | null; simulatedPaymentId: string | null
  attributionMonth: { value: string | null; status: string }
}
export type Ledger = { month: string; items: LedgerRow[]; coverage: {
  userCardId: string; source: string; period: Period; complete: boolean; incompleteReasons: string[]
}[] }
export interface BenefitsApi {
  context: 'demo' | 'member' | 'mock'
  cards(mode: PortfolioMode, signal: AbortSignal): Promise<HeldCard[]>
  products(asOf: string, signal: AbortSignal): Promise<ProductSummary[]>
  product(id: string, asOf: string, signal: AbortSignal): Promise<Product>
  monthly(mode: PortfolioMode, card: string, month: string, signal: AbortSignal): Promise<Monthly>
  refreshCodefPerformance?(signal: AbortSignal): Promise<void>
  ledger(mode: PortfolioMode, month: string, signal: AbortSignal, card?: string): Promise<Ledger>
  payment(mode: PortfolioMode, id: string, signal: AbortSignal): Promise<PaymentDetail>
}

export function productId(card: HeldCard) {
  return card.holdingSource === 'SYNTHETIC' ? card.cardProductId ?? null
    : card.mapping?.status === 'USER_CONFIRMED' ? card.mapping.cardProductId : null
}

export const seoulDate = () => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Seoul',
  year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())

export function shiftMonth(month: string, offset: number) {
  const [year, value] = month.split('-').map(Number)
  const index = year * 12 + value - 1 + offset
  return String(Math.floor(index / 12)).padStart(4, '0') + '-' + String(index % 12 + 1).padStart(2, '0')
}

export function monthPeriod(month: string): Period {
  const [year, value] = month.split('-').map(Number)
  const last = new Date(Date.UTC(year + (year < 100 ? 400 : 0), value, 0)).getUTCDate()
  return { from: month + '-01', to: month + '-' + last }
}

// Presentation labels for service/bucket keys in the current rule snapshots.
const names: Record<string, Record<string, string>> = {
  '09271': { FUEL: '주유소', STORE: '대형마트' },
  '01664': {
    COMMON: '생활 할인 통합 한도', TRANSIT: '대중교통', TELECOM: '이동통신요금',
    CGV: 'CGV', OUTBACK_VIPS: '아웃백·빕스', STARBUCKS: '스타벅스', THEME_PARK: '놀이공원',
    GS25: 'GS25', KYOBO: '교보문고',
  },
  '01914': {
    COMMON: '통합 할인 한도', CU: 'CU', STARBUCKS: '스타벅스', CGV: 'CGV', TENBYTEN: '텐바이텐',
    SIMPLE_PAY: '간편 결제', BEAUTY: '올리브영', BOOKSTORE: '온라인 서점', CULTURE: '공연 예매', TRANSIT: '대중교통',
  },
  '09174': {
    GREAT: '스타벅스', ENJOY: '버거·패스트푸드', CHECK: '간편 결제', BASIC: '통신·교통 통합 한도',
    BASIC_TELECOM: '통신요금', BASIC_TRANSIT: '대중교통',
  },
}
const bucketCaptions: Record<string, string> = { GREAT: '커피 할인', ENJOY: '외식 할인', CHECK: '간편 결제 할인' }
const supported = new Set(['SUPPORTED', 'SUPPORTED_SINGLE_MONTHLY_LIMIT'])

export function bucketRows(product: Product, monthly: Monthly, month: string) {
  const current = monthPeriod(month)
  const previous = monthPeriod(shiftMonth(month, -1))
  const performance = monthly.previousMonthPerformanceWon
  const rule = product.rule
  const matches = (a: Period | null, b: Period) => a?.from === b.from && a.to === b.to
  const coverage = rule?.verificationStatus === 'VERIFIED' && rule.validFrom <= current.from &&
    (rule.validTo === null || rule.validTo >= current.to)
  return product.limitBuckets.map((bucket, index) => {
    const { limitBucketId: id, parentLimitBucketId: parent } = bucket
    const services = product.services.filter(service => bucket.sharedByServiceIds.includes(service.serviceId) ||
      service.limitBucketIds.includes(id))
    const titles = services.map(service => names[product.cardProductId]?.[service.serviceId] ??
      (service.name !== service.serviceId ? service.name : null)).filter(Boolean)
    const title = names[product.cardProductId]?.[id] ?? (titles.length ? [...new Set(titles)].join(' · ') : '혜택 한도 ' + (index + 1))
    const usage = monthly.limitBucketUsages.find(item => item.limitBucketId === id)
    const compatible = services.length > 0 && services.every(service => supported.has(service.supportStatus))
    const tier = coverage && compatible && bucket.period === 'CALENDAR_MONTH' &&
      rule?.performanceWindow === 'PREVIOUS_CALENDAR_MONTH' && performance.usable &&
      performance.value !== null && matches(performance.period, previous)
      ? bucket.tiers.filter(tier => tier.minimumPerformanceWon <= performance.value!)
        .reduce<(typeof bucket.tiers)[number] | undefined>((best, value) =>
          !best || value.minimumPerformanceWon > best.minimumPerformanceWon ? value : best, undefined)
      : undefined
    return { id, parent, title, usage, limit: tier?.monthlyCapWon ?? null,
      caption: services.some(service => service.requiresSelection) ? '선택형 혜택'
        : !compatible ? '지원 범위 확인 필요' : parent ? '통합 한도에 속한 사용액'
          : (product.cardProductId === '09174' ? bucketCaptions[id] : undefined) ?? '기준 사용액' }
  })
}
