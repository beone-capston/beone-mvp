import type { PortfolioMode } from '../data/portfolio'
import { isOrderDate, paymentMethods } from './order'
import type { CreatedOrder, OrderDetail, OrderInput } from './order'

type ErrorKind = 'validation' | 'missing' | 'unavailable' | 'server' | 'network' | 'unexpected'

export class OrderRequestError extends Error {
  readonly kind: ErrorKind
  readonly fields: Array<{ field: string | null }>

  constructor(kind: ErrorKind, fields: Array<{ field: string | null }> = []) {
    super(kind)
    this.kind = kind
    this.fields = fields
  }
}

const baseUrl = (import.meta.env.VITE_API_BASE_URL ??
  (import.meta.env.DEV ? 'http://localhost:8080' : '')).replace(/\/+$/, '')

function isOrder(value: CreatedOrder | null | undefined): value is CreatedOrder {
  return Boolean(value && typeof value.orderId === 'string' && value.orderId &&
    ['CREATED', 'PAID', 'CANCELLED'].includes(value.status) &&
    typeof value.createdAt === 'string' && Number.isSafeInteger(value.amountWon) && value.amountWon > 0 &&
    typeof value.orderDate === 'string' && isOrderDate(value.orderDate) &&
    (value.orderTime === null || typeof value.orderTime === 'string') &&
    (value.merchantName === null || typeof value.merchantName === 'string') &&
    typeof value.merchantClass === 'string' && Object.hasOwn(paymentMethods, value.paymentMethod))
}

async function request(mode: PortfolioMode, orderId?: string, input?: OrderInput) {
  const headers: Record<string, string> = { 'X-BeONE-Demo-User': mode }
  if (input) headers['Content-Type'] = 'application/json'
  const path = '/api/v1/me/orders' + (orderId === undefined ? '' : '/' + encodeURIComponent(orderId))
  const response = await fetch(baseUrl + path, {
    method: input ? 'POST' : 'GET',
    headers,
    credentials: 'omit',
    cache: 'no-store',
    body: input ? JSON.stringify(input) : undefined,
    signal: AbortSignal.timeout(15000),
  }).catch(() => { throw new OrderRequestError('network') })

  const body = await response.json().catch(() => null)
  if (response.status === 400 && body?.code === 'INVALID_REQUEST') {
    const fields: Array<{ field: string | null }> = Array.isArray(body.errors) ? body.errors : []
    throw new OrderRequestError('validation', fields.filter(item =>
      item && (typeof item.field === 'string' || item.field === null),
    ))
  }
  if (response.status === 404 && body?.code === 'ORDER_NOT_FOUND') throw new OrderRequestError('missing')
  if (response.status === 404 && body?.code === 'NOT_FOUND') throw new OrderRequestError('unavailable')
  if (response.status >= 500) throw new OrderRequestError('server')
  if (response.status !== (input ? 201 : 200) || !isOrder(body?.data)) {
    throw new OrderRequestError('unexpected')
  }
  return body.data
}

export interface OrderApi {
  create(mode: PortfolioMode, input: OrderInput): Promise<CreatedOrder>
  detail(mode: PortfolioMode, orderId: string): Promise<OrderDetail>
}

export const orderApi: OrderApi = {
  async create(mode, input) {
    const result = await request(mode, undefined, input)
    if (result.status !== 'CREATED') throw new OrderRequestError('unexpected')
    return result
  },
  async detail(mode, orderId) {
    const result = await request(mode, orderId) as OrderDetail
    if (result.orderId !== orderId ||
      ![result.simulatedPaymentId, result.latestRecommendationId].every(value => value === null || typeof value === 'string')) {
      throw new OrderRequestError('unexpected')
    }
    return result
  },
}
