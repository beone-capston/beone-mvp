import { workspaceRequest } from '../data/workspaceRequest'
import type { WorkspaceContext } from '../data/workspaceRequest'
import type { PortfolioMode } from '../data/portfolio'
import { isOrderDate, paymentMethods } from './order'
import type { CreatedOrder, OrderDetail, OrderInput, OrderSummary } from './order'

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

function isOrderSummary(value: OrderSummary | null | undefined): value is OrderSummary {
  return Boolean(value && typeof value.orderId === 'string' && value.orderId &&
    ['CREATED', 'PAID', 'CANCELLED'].includes(value.status) &&
    typeof value.createdAt === 'string' && Number.isSafeInteger(value.amountWon) && value.amountWon > 0 &&
    typeof value.orderDate === 'string' && isOrderDate(value.orderDate) &&
    (value.merchantName === null || typeof value.merchantName === 'string') &&
    typeof value.merchantClass === 'string')
}

function isOrder(value: CreatedOrder | null | undefined): value is CreatedOrder {
  return Boolean(value && isOrderSummary(value) &&
    (value.orderTime === null || typeof value.orderTime === 'string') &&
    Object.hasOwn(paymentMethods, value.paymentMethod))
}

async function request(context: WorkspaceContext, mode: PortfolioMode, orderId?: string, input?: OrderInput) {
  const headers: Record<string, string> = {}
  if (input) headers['Content-Type'] = 'application/json'
  const path = 'orders' + (orderId === undefined ? '' : '/' + encodeURIComponent(orderId))
  const response = await workspaceRequest(context, mode, path, {
    method: input ? 'POST' : 'GET',
    headers,
    body: input ? JSON.stringify(input) : undefined,
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
  list?(mode: PortfolioMode, page: number, signal: AbortSignal): Promise<{ items: OrderSummary[]; hasNext: boolean }>
  create(mode: PortfolioMode, input: OrderInput): Promise<CreatedOrder>
  detail(mode: PortfolioMode, orderId: string): Promise<OrderDetail>
}

export function createOrderApi(context: WorkspaceContext): OrderApi {
  return {
    async list(mode, page, signal) {
      const response = await workspaceRequest(context, mode, 'orders?page=' + page, { signal })
        .catch(() => { throw new OrderRequestError('network') })
      const result = (await response.json().catch(() => null))?.data
      if (!response.ok || !Array.isArray(result?.items) || !result.items.every(isOrderSummary) ||
        result.page !== page || typeof result.hasNext !== 'boolean') throw new OrderRequestError('unexpected')
      return { items: result.items, hasNext: result.hasNext }
    },
    async create(mode, input) {
      const result = await request(context, mode, undefined, input)
      if (result.status !== 'CREATED') throw new OrderRequestError('unexpected')
      return result
    },
    async detail(mode, orderId) {
      const result = await request(context, mode, orderId) as OrderDetail
      if (result.orderId !== orderId ||
        ![result.simulatedPaymentId, result.latestRecommendationId].every(value => value === null || typeof value === 'string')) {
        throw new OrderRequestError('unexpected')
      }
      return result
    },
  }
}

const demoOrders = createOrderApi('demo')
export const orderApi: OrderApi = { create: demoOrders.create, detail: demoOrders.detail }
