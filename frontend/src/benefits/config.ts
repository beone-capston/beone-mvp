import { mockWorkspace, workspaceContext } from '../data/workspaceRequest'
import { createBenefitsApi } from './api'
import { mockBenefitsApi } from './fixtures'

const setting = import.meta.env.VITE_BENEFITS_CONTEXT
const useMock = import.meta.env.DEV && (setting === 'mock' || (!setting && mockWorkspace))

export const benefitsApi = useMock ? mockBenefitsApi
  : createBenefitsApi(setting === 'member' || setting === 'demo' ? setting : workspaceContext)
