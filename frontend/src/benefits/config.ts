import { createBenefitsApi } from './api'
import { mockBenefitsApi } from './fixtures'

const setting = import.meta.env.VITE_BENEFITS_CONTEXT
const local = ['localhost', '127.0.0.1', '[::1]'].includes(window.location.hostname)
const useMock = import.meta.env.DEV && (setting === 'mock' || (local && !setting))

export const benefitsApi = useMock ? mockBenefitsApi
  : createBenefitsApi(setting === 'member' || (!import.meta.env.DEV && setting !== 'demo') ? 'member' : 'demo')
