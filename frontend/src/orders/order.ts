import { isMerchantClass, merchantLabel } from './merchantClasses'

export const paymentMethods = {
  DIRECT: '일반 결제',
  ELIGIBLE_SIMPLE_PAY: '대상 간편결제',
  AUTOPAY: '자동납부',
} as const

export type PaymentMethod = keyof typeof paymentMethods
export type OrderStatus = 'CREATED' | 'PAID' | 'CANCELLED'

export interface OrderInput {
  amountWon: number
  orderDate: string
  orderTime: string | null
  merchantName: string | null
  merchantClass: string
  paymentMethod: PaymentMethod
}

export interface CreatedOrder extends OrderInput {
  orderId: string
  status: OrderStatus
  createdAt: string
}

export type OrderSummary = Omit<CreatedOrder, 'orderTime' | 'paymentMethod'>

export interface OrderDetail extends CreatedOrder {
  simulatedPaymentId: string | null
  latestRecommendationId: string | null
}

export type OrderDraft = Record<keyof OrderInput, string>
export type OrderErrors = Partial<OrderDraft>

export const emptyDraft: OrderDraft = {
  amountWon: '', merchantName: '', merchantClass: '', orderDate: '', orderTime: '', paymentMethod: 'DIRECT',
}

export function isOrderDate(value: string) {
  const parts = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!parts) return false
  const [year, month, day] = parts.slice(1).map(Number)
  const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0)
  const days = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
  return year > 0 && day >= 1 && day <= days[month - 1]
}

export function validateOrder(draft: OrderDraft): { errors: OrderErrors; input?: OrderInput } {
  const errors: OrderErrors = {}
  const rawAmount = draft.amountWon.trim()
  const amountWon = Number(rawAmount)
  const merchantName = draft.merchantName.trim() || null
  const orderTime = draft.orderTime.length === 5 ? draft.orderTime + ':00' : draft.orderTime || null
  if (!/^\d+$/.test(rawAmount) || !Number.isSafeInteger(amountWon) || amountWon < 1) {
    errors.amountWon = '1원 이상의 안전한 정수 금액을 입력해 주세요.'
  }
  if (merchantName && merchantName.length > 50) errors.merchantName = '가맹점명은 50자 이하여야 해요.'
  if (!isMerchantClass(draft.merchantClass)) errors.merchantClass = '가맹점·업종 분류를 선택해 주세요.'
  if (!isOrderDate(draft.orderDate)) errors.orderDate = '유효한 결제일을 입력해 주세요.'
  if (orderTime && !/^(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d$/.test(orderTime)) {
    errors.orderTime = '유효한 결제 시간을 입력해 주세요.'
  }
  if (!Object.hasOwn(paymentMethods, draft.paymentMethod)) errors.paymentMethod = '결제 방식을 선택해 주세요.'
  if (Object.keys(errors).length) return { errors }
  return {
    errors,
    input: {
      amountWon, merchantName, merchantClass: draft.merchantClass, orderDate: draft.orderDate, orderTime,
      paymentMethod: draft.paymentMethod as PaymentMethod,
    },
  }
}

const won = new Intl.NumberFormat('ko-KR')
export const formatWon = (amount: number) => won.format(amount)
export const orderName = (order: Pick<OrderInput, 'merchantName' | 'merchantClass'>) => order.merchantName || merchantLabel(order.merchantClass)
