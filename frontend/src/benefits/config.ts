import { workspaceContext } from '../data/workspaceRequest'
import { createBenefitsApi } from './api'

export const benefitsApi = createBenefitsApi(workspaceContext)
