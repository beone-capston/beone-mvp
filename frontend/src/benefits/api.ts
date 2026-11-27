import { authBaseUrl } from '../auth/api'
import type { PortfolioMode } from '../data/portfolio'
import { parsePaymentDetail } from '../payment/paymentApi'
import type { BenefitsApi, HeldCard, Ledger, Monthly, Product, ProductSummary, StateValue } from './model'

type Context = 'demo' | 'member'
export class BenefitsError extends Error {
  constructor(message: string) { super(message) }
}
const isWon = (value: unknown) => typeof value === 'number' && Number.isSafeInteger(value)
const nullableWon = (value: unknown) => value === null || isWon(value)
const validAmount = (value: StateValue<number>) => value && nullableWon(value.value) &&
  typeof value.usable === 'boolean' && typeof value.source === 'string' &&
  (value.period === null || (typeof value.period?.from === 'string' && typeof value.period?.to === 'string'))
const invalid = () => new BenefitsError('서버의 조회 응답을 확인할 수 없어요.')

export function createBenefitsApi(context: Context): BenefitsApi {
  const base = context === 'demo'
    ? (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/+$/, '')
    : authBaseUrl
  async function get<T>(path: string, signal: AbortSignal, mode?: PortfolioMode): Promise<T> {
    if (context === 'member' && mode === 'VIRTUAL_PORTFOLIO') {
      throw new BenefitsError('회원 환경에서는 가상 포트폴리오를 조회할 수 없어요.')
    }
    const response = await fetch(base + '/api/v1/' + path, {
      method: 'GET', signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]), cache: 'no-store',
      credentials: context === 'member' && mode ? 'include' : 'omit',
      headers: context === 'demo' && mode ? { 'X-BeONE-Demo-User': mode } : {},
    }).catch(() => { throw new BenefitsError('서버에 연결할 수 없어요. 연결 상태를 확인해 주세요.') })
    const body = await response.json().catch(() => null)
    if (response.status === 401) throw new BenefitsError('로그인 상태가 만료됐어요. 다시 로그인해 주세요.')
    if (response.status === 403) throw new BenefitsError('이 정보를 조회할 권한이 없어요.')
    if (response.status === 404 && body?.code === 'NOT_FOUND') {
      throw new BenefitsError('현재 환경에서 이 조회 API를 사용할 수 없어요.')
    }
    if (response.status === 404) throw new BenefitsError('요청한 카드 또는 거래 정보를 찾을 수 없어요.')
    if (!response.ok) throw new BenefitsError('조회에 실패했어요. 다시 시도해 주세요.')
    if (body?.data == null) throw invalid()
    return body.data
  }
  return {
    context,
    async cards(mode, signal) {
      const data = await get<{ items: HeldCard[] }>('me/cards', signal, mode)
      if (!Array.isArray(data.items) || !data.items.every(card => /^uc-\d+$/.test(card?.userCardId ?? '') &&
        typeof card.displayName === 'string')) throw invalid()
      return data.items
    },
    async products(asOf, signal) {
      const data = await get<{ items: ProductSummary[] }>('card-products?asOf=' + encodeURIComponent(asOf), signal)
      if (!Array.isArray(data.items) || !data.items.every(card => typeof card?.cardProductId === 'string' &&
        typeof card.cardName === 'string')) throw invalid()
      return data.items
    },
    async product(id, asOf, signal) {
      const data = await get<Product>('card-products/' + encodeURIComponent(id) + '?asOf=' + encodeURIComponent(asOf), signal)
      if (data.cardProductId !== id || data.asOf !== asOf || !Array.isArray(data.services) ||
        !data.services.every(item => typeof item?.serviceId === 'string' && Array.isArray(item.limitBucketIds)) ||
        !Array.isArray(data.limitBuckets) || !data.limitBuckets.every(item => typeof item?.limitBucketId === 'string' &&
          Array.isArray(item.sharedByServiceIds) && Array.isArray(item.tiers) && item.tiers.every(tier =>
            isWon(tier?.minimumPerformanceWon) && isWon(tier?.monthlyCapWon)))) throw invalid()
      return data
    },
    async monthly(mode, card, month, signal) {
      if (!/^uc-\d+$/.test(card)) throw invalid()
      const data = await get<Monthly>('me/cards/' + encodeURIComponent(card) + '/monthly-states/' + month, signal, mode)
      if (data.userCardId !== card || data.month !== month || !validAmount(data.previousMonthPerformanceWon) ||
        !Array.isArray(data.limitBucketUsages) || !data.limitBucketUsages.every(usage => validAmount(usage) &&
          typeof usage.limitBucketId === 'string') || !validAmount(data.otherBucketsUsedWon) ||
        !Array.isArray(data.selectedServices) || !(data.afterSnapshot === null ||
          (isWon(data.afterSnapshot?.approvalCount) && isWon(data.afterSnapshot.approvedAmountWon) &&
            isWon(data.afterSnapshot.cancellationCount)))) throw invalid()
      return data
    },
    async ledger(mode, month, signal, card) {
      const params = new URLSearchParams({ month, source: 'SIMULATION' })
      if (card) {
        if (!/^uc-\d+$/.test(card)) throw invalid()
        params.set('userCardId', card)
      }
      const data = await get<Ledger>('me/ledger-transactions?' + params, signal, mode)
      if (data.month !== month || !Array.isArray(data.items) || !Array.isArray(data.coverage) ||
        !data.items.every(row => row && isWon(row.amountWon) && typeof row.transactionDate === 'string' &&
          (row.entryType === 'APPROVAL' || row.entryType === 'CANCELLATION')) ||
        !data.coverage.every(row => row && typeof row.complete === 'boolean' && Array.isArray(row.incompleteReasons))) throw invalid()
      return data
    },
    async payment(mode, id, signal) {
      try { return parsePaymentDetail(await get('me/simulated-payments/' + encodeURIComponent(id), signal, mode), id) }
      catch (error) { if (error instanceof BenefitsError) throw error; throw invalid() }
    },
  }
}
