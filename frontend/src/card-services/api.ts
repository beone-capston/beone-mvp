import { workspaceRequest } from '../data/workspaceRequest'
import type { WorkspaceContext } from '../data/workspaceRequest'
import type { PortfolioMode } from '../data/portfolio'
import type { Product } from './catalog'

type Usage = {
  value: number | null
  source: string
  period: { from: string; to: string } | null
  usable: boolean
  updatedAt: string | null
}

export type MonthlyState = {
  userCardId: string
  month: string
  snapshotAt: string | null
  limitBucketUsages: (Usage & { limitBucketId: string })[]
  previousMonthPerformanceWon: Usage
  registeredOn: Omit<Usage, 'value'> & { value: string | null }
  selectedServices: { serviceId: string; effectiveFrom: string; usable: boolean }[]
  serviceSelectionStatus: string
  noServiceSelection: { effectiveFrom: string; usable: boolean } | null
  afterSnapshot: { approvalCount: number; cancellationCount: number } | null
}

type ProductDetail = {
  cardProductId: string
  cardName: string
  asOf: string
  ruleStatus: string
  rule: { ruleVersion: string; verificationStatus: string } | null
  services: {
    serviceId: string
    name: string
    benefitKind: string
    supportStatus: string
    limitBucketIds: string[]
    requiresSelection: boolean
  }[]
  limitBuckets: {
    limitBucketId: string
    parentLimitBucketId: string | null
    tiers: { minimumPerformanceWon: number; monthlyCapWon: number }[]
    period: string
  }[]
}

export const demoMode = import.meta.env.VITE_CARD_SERVICES_CONTEXT === 'demo'
export const offlineCatalog = import.meta.env.DEV && !demoMode && !import.meta.env.VITE_API_BASE_URL

export function seoulToday() {
  return new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date())
}

export function staticRuleApplies(product: Product, asOf: string) {
  return product.status === 'VERIFIED' && product.validity.from <= asOf &&
    (product.validity.to === null || asOf <= product.validity.to)
}

export function matchesCatalog(product: Product, server: ProductDetail) {
  return server.ruleStatus === 'VERIFIED' && server.rule?.verificationStatus === 'VERIFIED' &&
    server.rule.ruleVersion === product.ruleVersion &&
    server.services.length === product.services.length &&
    product.services.every(service => server.services.some(remote =>
      remote.serviceId === service.id && remote.requiresSelection === service.requiresSelection &&
      remote.benefitKind === service.kind && remote.limitBucketIds.length === service.limitBuckets.length &&
      service.limitBuckets.every(id => remote.limitBucketIds.includes(id)))) &&
    server.limitBuckets.length === product.limitBuckets.length &&
    product.limitBuckets.every(bucket => server.limitBuckets.some(remote =>
      remote.limitBucketId === bucket.id && remote.parentLimitBucketId === bucket.parent &&
      remote.period === 'CALENDAR_MONTH' && remote.tiers.length === bucket.tiers.length &&
      bucket.tiers.every((tier, i) => remote.tiers[i]?.minimumPerformanceWon === tier.minimumPerformance &&
        remote.tiers[i]?.monthlyCapWon === tier.monthlyCap)))
}

class RequestFailure extends Error {
  readonly status: number

  constructor(status: number) {
    super(String(status))
    this.status = status
  }
}

export function requestMessage(error: unknown) {
  if (error instanceof RequestFailure) {
    if (error.status === 401) return '사용자 인증이 필요해요.'
    if (error.status === 403) return '이 정보를 조회할 권한이 없어요.'
    if (error.status === 404) return '서버에서 카드 또는 조회 경로를 찾을 수 없어요.'
  }
  return '서버에서 정보를 불러오지 못했어요.'
}

async function read<T>(path: string, signal: AbortSignal, personal = false): Promise<T> {
  const base = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
  const response = await fetch(base + '/api/v1' + path, {
    signal,
    headers: personal ? { 'X-BeONE-Demo-User': 'VIRTUAL_PORTFOLIO' } : undefined,
  })
  if (!response.ok) throw new RequestFailure(response.status)
  const result = await response.json()
  if (!result || typeof result !== 'object' || !result.data) throw new Error('Missing data')
  return result.data
}

export async function readProduct(id: string, asOf: string, signal: AbortSignal) {
  const result = await read<ProductDetail>(`/card-products/${encodeURIComponent(id)}?asOf=${asOf}`, signal)
  if (result.cardProductId !== id || result.asOf !== asOf ||
    !Array.isArray(result.services) || !Array.isArray(result.limitBuckets)) throw new Error('Invalid product')
  return result
}

export async function readMonth(id: string, month: string, signal: AbortSignal) {
  const me = await read<{ demoUser: string }>('/me', signal, true)
  if (me.demoUser !== 'VIRTUAL_PORTFOLIO') throw new RequestFailure(403)
  const cards = await read<{ items: { holdingSource: string; cardProductId?: string; userCardId: string }[] }>('/me/cards', signal, true)
  const matched = cards.items.filter(card => card.holdingSource === 'SYNTHETIC' && card.cardProductId === id)
  if (matched.length !== 1 || !matched[0].userCardId) throw new RequestFailure(404)
  const userCardId = matched[0].userCardId
  const result = await read<MonthlyState>(`/me/cards/${encodeURIComponent(userCardId)}/monthly-states/${month}`, signal, true)
  if (result.userCardId !== userCardId || result.month !== month ||
    !Array.isArray(result.limitBucketUsages) || !Array.isArray(result.selectedServices)) throw new Error('Invalid month')
  return result
}

export async function readHeldCardMonth(context: WorkspaceContext, mode: PortfolioMode, userCardId: string, month: string, signal: AbortSignal) {
  const response = await workspaceRequest(context, mode,
    `cards/${encodeURIComponent(userCardId)}/monthly-states/${month}`, { signal })
  if (!response.ok) throw new RequestFailure(response.status)
  const result = (await response.json())?.data as MonthlyState | undefined
  if (!result || result.userCardId !== userCardId || result.month !== month ||
    !Array.isArray(result.limitBucketUsages) || !Array.isArray(result.selectedServices)) throw new Error('Invalid month')
  return result
}

export function monthPeriod(month: string, offset = 0) {
  const [year, number] = month.split('-').map(Number)
  return {
    from: new Date(Date.UTC(year, number - 1 + offset, 1)).toISOString().slice(0, 10),
    to: new Date(Date.UTC(year, number + offset, 0)).toISOString().slice(0, 10),
  }
}

function scopedAmount(state: Usage | undefined, period: ReturnType<typeof monthPeriod>, allowNegative = false) {
  if (!state?.usable || state.source === 'UNKNOWN' || state.period?.from !== period.from ||
    state.period?.to !== period.to || typeof state.value !== 'number' ||
    !Number.isSafeInteger(state.value) || (!allowNegative && state.value < 0)) return null
  return state.value
}

export function bucketAmounts(product: Product, id: string, month: string, state?: MonthlyState) {
  const unknown = { used: null, remaining: null, source: undefined, updatedAt: undefined, belowTier: false }
  if (!state || state.month !== month) return unknown
  const bucket = product.limitBuckets.find(item => item.id === id)
  const usages = state.limitBucketUsages.filter(item => item.limitBucketId === id)
  const usage = usages.length === 1 ? usages[0] : undefined
  const period = monthPeriod(month)
  const used = scopedAmount(usage, period)
  const result = { ...unknown, used, source: usage?.source, updatedAt: usage?.updatedAt }
  if (!bucket || used === null || !state.snapshotAt || !state.afterSnapshot ||
    state.afterSnapshot.approvalCount !== 0 || state.afterSnapshot.cancellationCount !== 0) return result

  const affected = product.services.filter(service => service.limitBuckets.includes(id) ||
    service.limitBuckets.some(child => product.limitBuckets.some(item => item.id === child && item.parent === id)))
  if (affected.some(service => service.requiresSelection)) return result

  // Derive only an unambiguous bucket balance, outside any possible new-card grace window.
  const registration = state.registeredOn
  if (affected.some(service => product.policy.newCardGrace.value.services.includes(service.id))) {
    if (!registration?.usable || registration.source === 'UNKNOWN' || !registration.value ||
      registration.period?.from !== registration.value || registration.period?.to !== registration.value ||
      !/^\d{4}-(0[1-9]|1[0-2])-([012]\d|3[01])$/.test(registration.value) ||
      Number.isNaN(Date.parse(registration.value))) return result
    const grace = product.policy.newCardGrace.value
    const end = grace.span === 'UNTIL_END_OF_NEXT_MONTH' ? monthPeriod(registration.value.slice(0, 7), 1).to :
      new Date(Date.parse(registration.value) + ((grace.days ?? 1) - 1) * 86400000).toISOString().slice(0, 10)
    if (end >= period.from) return result
  }
  const performance = scopedAmount(state.previousMonthPerformanceWon, monthPeriod(month, -1), true)
  if (performance === null) return result
  const tier = bucket.tiers.findLast(item => item.minimumPerformance <= performance)
  return tier ? { ...result, remaining: Math.max(0, tier.monthlyCap - used) } : { ...result, belowTier: true }
}
