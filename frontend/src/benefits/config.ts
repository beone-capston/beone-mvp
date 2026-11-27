import { workspaceContext } from '../data/workspaceRequest'
import { createCardImportApi } from '../card-import/api'
import { AuthApiError } from '../auth/api'
import { BenefitsError, createBenefitsApi } from './api'
import type { BenefitsApi } from './model'

export const benefitsApi: BenefitsApi = createBenefitsApi(workspaceContext)

if (workspaceContext === 'member') {
  const cardImporter = createCardImportApi(workspaceContext)
  benefitsApi.refreshCodefPerformance = async signal => {
    try {
      const result = await cardImporter.sync(signal)
      if (result.partialFailures.some(failure => failure.target === 'PERFORMANCES')) {
        throw new BenefitsError('카드사 실적을 가져오지 못했어요.')
      }
    } catch (error) {
      if (error instanceof AuthApiError) {
        if (error.status === 404) throw new BenefitsError('이 환경에서는 카드사 실적 조회를 사용할 수 없어요.')
        if (error.code === 'CODEF_NOT_CONNECTED' || error.code === 'CODEF_RECONNECT_REQUIRED') {
          throw new BenefitsError('카드 추가에서 카드사 계정을 다시 연결해 주세요.')
        }
      }
      throw error
    }
  }
}
