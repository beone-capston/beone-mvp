import { readHeldCards, toWalletCard } from '../data/heldCards'
import type { WalletCard } from '../data/portfolio'
import { AuthApiError } from '../auth/api'
import type { PaymentPreference, PortfolioMode } from '../data/portfolio'
import { workspaceRequest } from '../data/workspaceRequest'
import type { WorkspaceContext } from '../data/workspaceRequest'

export type PreferenceDto = {
  selectionMode: PaymentPreference['selectionMode']
  representativeUserCardId: string | null
  updatedAt: string | null
}
type PreferenceInput = Omit<PreferenceDto, 'updatedAt'>
export type PreferenceCard = { id: string; name: string; canBeRepresentative: boolean; wallet?: WalletCard }

export interface PreferenceApi {
  supports(mode: PortfolioMode): boolean
  read(mode: PortfolioMode, signal: AbortSignal): Promise<PreferenceDto>
  cards(mode: PortfolioMode, signal: AbortSignal): Promise<PreferenceCard[]>
  save(mode: PortfolioMode, data: PreferenceInput): Promise<PreferenceDto>
}

export const userCardId = (value: unknown): value is string => typeof value === 'string' && /^uc-[1-9]\d{0,18}$/.test(value)
export const defaultDto = (): PreferenceDto => ({ selectionMode: 'AUTO_RECOMMEND', representativeUserCardId: null, updatedAt: null })
export const toPreference = (dto: PreferenceDto): PaymentPreference => ({
  selectionMode: dto.selectionMode, representativeCardId: dto.representativeUserCardId,
})
export const toPreferenceInput = (value: PaymentPreference): PreferenceInput => ({
  selectionMode: value.selectionMode, representativeUserCardId: value.representativeCardId,
})

export function parsePreference(value: unknown): PreferenceDto {
  const dto = value as Partial<PreferenceDto> | null
  if (!dto || (dto.selectionMode !== 'AUTO_RECOMMEND' && dto.selectionMode !== 'FIXED_REPRESENTATIVE') ||
    !(userCardId(dto.representativeUserCardId) || (dto.representativeUserCardId === null && dto.selectionMode === 'AUTO_RECOMMEND')) ||
    !(dto.updatedAt === null || (typeof dto.updatedAt === 'string' && Number.isFinite(Date.parse(dto.updatedAt))))) {
    throw new Error('INVALID_PREFERENCE_RESPONSE')
  }
  return { selectionMode: dto.selectionMode, representativeUserCardId: dto.representativeUserCardId, updatedAt: dto.updatedAt }
}

async function request(context: WorkspaceContext, mode: PortfolioMode, path: string, signal?: AbortSignal, input?: PreferenceInput) {
  const response = await workspaceRequest(context, mode, path, {
    signal, method: input ? 'PUT' : 'GET',
    headers: input ? { 'Content-Type': 'application/json' } : undefined,
    body: input ? JSON.stringify(input) : undefined,
  })
  const payload = await response.json().catch(() => null)
  if (!response.ok) throw new AuthApiError(response.status, payload, null)
  if (response.status !== 200 || !payload || typeof payload !== 'object' || !('data' in payload)) throw new Error('INVALID_RESPONSE')
  return payload.data
}

export function createPreferenceApi(context: WorkspaceContext): PreferenceApi {
  return {
    supports: mode => context === 'demo' || mode === 'MY_CARDS',
    async read(mode, signal) { return parsePreference(await request(context, mode, 'payment-preference', signal)) },
    async cards(mode, signal) {
      const held = await readHeldCards(context, mode, signal)
      // CODEF sync persists candidates before selection. Only confirmed CODEF cards enter the wallet.
      return held.filter(card => card.holdingSource !== 'CODEF' || card.mapping?.status === 'USER_CONFIRMED')
        .map(toWalletCard).map(wallet => ({ id: wallet.id, name: wallet.name,
          canBeRepresentative: wallet.canBeRepresentative, wallet }))
    },
    async save(mode, input) {
      const id = input.representativeUserCardId
      if (id === null ? input.selectionMode === 'FIXED_REPRESENTATIVE' : !userCardId(id)) {
        throw new AuthApiError(400, { code: 'INVALID_REQUEST', message: '대표 카드를 확인해 주세요.', errors: [] }, null)
      }
      return parsePreference(await request(context, mode, 'payment-preference', undefined, input))
    },
  }
}

export function preferenceError(error: unknown) {
  if (error instanceof AuthApiError) {
    switch (error.code) {
      case 'INVALID_REQUEST': return error.fields[0]?.message ?? '설정 입력을 확인해 주세요.'
      case 'UNAUTHENTICATED': return '다시 로그인한 뒤 설정을 확인해 주세요.'
      case 'CSRF_INVALID': return '보안 확인에 실패했어요. 다시 저장해 주세요.'
      case 'USER_CARD_NOT_FOUND': return '보유 카드 목록에서 대표 카드를 찾을 수 없어요.'
      case 'REPRESENTATIVE_CARD_NOT_ELIGIBLE': return '선택한 카드는 대표 카드로 지정할 수 없어요.'
      case 'NOT_FOUND': return '이 환경에서는 결제 설정 API를 사용할 수 없어요.'
    }
  }
  return '다시 시도해 주세요.'
}
