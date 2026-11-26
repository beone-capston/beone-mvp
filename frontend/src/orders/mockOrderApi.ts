import type { PortfolioMode } from '../data/portfolio'
import type { OrderDetail, OrderInput } from './order'
import { OrderRequestError } from './orderApi'
import type { OrderApi } from './orderApi'

export function createMockOrderApi(): OrderApi {
  const orders = new Map<string, OrderDetail>()
  const key = (mode: PortfolioMode, id: string) => mode + ':' + id
  let sequence = 1
  return {
    async create(mode, input: OrderInput) {
      const order: OrderDetail = {
        ...input, orderId: 'mock-order-' + sequence++, status: 'CREATED',
        createdAt: new Date().toISOString(), simulatedPaymentId: null, latestRecommendationId: null,
      }
      orders.set(key(mode, order.orderId), order)
      return { ...order }
    },
    async detail(mode, id) {
      const order = orders.get(key(mode, id))
      if (!order) throw new OrderRequestError('missing')
      return { ...order }
    },
  }
}
