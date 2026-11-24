// 보유 카드 목록·수동 등록과 카드 상품 목록 호출(docs/api/cards.md, docs/api/products.md)
import { apiBaseUrl, commonFailure, fetchCsrfHeader, notifyUnauthenticated } from '../api/client'

export interface UserCard {
  userCardId: string
  displayName: string
  cardProductId: string | null
  eligible: boolean
}

export interface CardProduct {
  cardProductId: string
  cardName: string
  cardType: string
}

// 상품을 고르면 USER_CONFIRMED, 목록에 없으면 NOT_SUPPORTED, 모르면 둘 다 생략(UNMAPPED)
export type CardRegistration =
  | { displayName: string; cardProductId: string }
  | { displayName: string; notSupported: true }
  | { displayName: string }

type Failure = 'unauthenticated' | 'csrf' | 'unavailable' | 'validation' | 'duplicated' | 'product' | 'network' | 'unexpected'

export class CardRequestError extends Error {
  readonly kind: Failure

  constructor(kind: Failure) {
    super(kind)
    this.kind = kind
  }
}

const string = (v: unknown): v is string => typeof v === 'string'
const record = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v)

function userCard(v: unknown): UserCard | null {
  if (!record(v) || !string(v.userCardId) || !v.userCardId || !string(v.displayName)) return null
  const mapping = record(v.mapping) ? v.mapping : null
  const eligibility = record(v.recommendationEligibility) ? v.recommendationEligibility : null
  const productId = mapping && string(mapping.cardProductId) ? mapping.cardProductId : null
  return {
    userCardId: v.userCardId,
    displayName: v.displayName,
    cardProductId: productId,
    eligible: eligibility?.eligible === true,
  }
}

async function call(method: 'GET' | 'POST', path: string, body?: object): Promise<{ status: number; data: unknown }> {
  // 시연 헤더는 local·demo용이다. member·prod는 읽지 않는다. 빌드 분기는 N18 결정 대기
  const headers: Record<string, string> = { 'X-BeONE-Demo-User': 'MY_CARDS' }
  if (body) {
    const csrf = await fetchCsrfHeader()
    if (!csrf) throw new CardRequestError('csrf')
    Object.assign(headers, { 'Content-Type': 'application/json' }, csrf)
  }
  let response: Response
  try {
    response = await fetch(apiBaseUrl + path, {
      method, headers, credentials: 'include', cache: 'no-store',
      body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(15000),
    })
  } catch {
    throw new CardRequestError('network')
  }
  const json: unknown = await response.json().catch(() => null)
  const code = record(json) ? json.code : undefined
  const common = commonFailure(response.status, code)
  if (common === 'unauthenticated') notifyUnauthenticated()
  if (common) throw new CardRequestError(common)
  if (response.status === 400 && code === 'INVALID_REQUEST') throw new CardRequestError('validation')
  if (response.status === 409 && code === 'USER_CARD_DUPLICATED') throw new CardRequestError('duplicated')
  if (response.status === 404 && code === 'CARD_PRODUCT_NOT_FOUND') throw new CardRequestError('product')
  if (!response.ok || !record(json)) throw new CardRequestError('unexpected')
  return { status: response.status, data: json.data }
}

export const cardApi = {
  async list(): Promise<UserCard[]> {
    const { status, data } = await call('GET', '/api/v1/me/cards')
    const items = record(data) && Array.isArray(data.items) ? data.items.map(userCard) : null
    if (status !== 200 || !items || items.some(item => item === null)) throw new CardRequestError('unexpected')
    return items as UserCard[]
  },
  async products(): Promise<CardProduct[]> {
    const { status, data } = await call('GET', '/api/v1/card-products')
    const items = record(data) && Array.isArray(data.items) ? data.items : null
    if (status !== 200 || !items) throw new CardRequestError('unexpected')
    return items.filter((item): item is CardProduct => record(item) &&
      string(item.cardProductId) && string(item.cardName) && string(item.cardType))
  },
  async register(input: CardRegistration): Promise<UserCard> {
    const { status, data } = await call('POST', '/api/v1/me/cards', input)
    const card = userCard(data)
    if (status !== 201 || !card) throw new CardRequestError('unexpected')
    return card
  },
}

export type CardApi = typeof cardApi
