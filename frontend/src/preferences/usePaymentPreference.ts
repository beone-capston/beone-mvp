import { AuthApiError } from '../auth/api'
import { useEffect, useRef, useState } from 'react'
import type { PaymentPreference, PortfolioMode } from '../data/portfolio'
import { defaultPreference } from '../data/portfolio'
import { preferenceError, toPreference, toPreferenceInput } from './preferenceApi'
import type { PreferenceApi, PreferenceCard } from './preferenceApi'

type State = {
  value?: PaymentPreference
  cards?: PreferenceCard[]
  error?: string
  saving?: boolean
  needsReload?: boolean
}

export function usePaymentPreference(api: PreferenceApi | undefined, mode: PortfolioMode) {
  const [state, setState] = useState<State>(() => api ? {} : { value: defaultPreference() })
  const [revision, setRevision] = useState(0)
  const busy = useRef(false)
  const supported = !api || api.supports(mode)

  useEffect(() => {
    if (!api || !supported) return
    const controller = new AbortController()
    const { signal } = controller
    queueMicrotask(() => {
      if (signal.aborted) return
      void Promise.allSettled([api.read(mode, signal), api.cards(mode, signal)]).then(([dto, cards]) => {
        if (signal.aborted) return
        if (dto.status === 'fulfilled' && cards.status === 'fulfilled') {
          setState({ value: toPreference(dto.value), cards: cards.value })
        } else {
          const problem = dto.status === 'rejected' ? dto.reason : cards.status === 'rejected' ? cards.reason : undefined
          setState({ error: preferenceError(problem), cards: cards.status === 'fulfilled' ? cards.value : undefined })
        }
      })
    })
    return () => controller.abort()
  }, [api, mode, revision, supported])

  function reload() {
    if (busy.current) return
    setState({})
    setRevision(value => value + 1)
  }

  function save(value: PaymentPreference) {
    if (!state.value || busy.current || !supported) return false
    if (!state.needsReload && state.value.selectionMode === value.selectionMode &&
      state.value.representativeCardId === value.representativeCardId) return true
    if (value.selectionMode === 'FIXED_REPRESENTATIVE' && !value.representativeCardId) return false
    if (!api) {
      setState({ value })
      return true
    }

    busy.current = true
    setState(current => ({ ...current, error: undefined, saving: true }))
    return api.save(mode, toPreferenceInput(value)).then(dto => {
      setState(current => ({ ...current, value: toPreference(dto), saving: false, needsReload: false }))
      return true
    }, error => {
      setState(current => ({ ...current, error: preferenceError(error), saving: false, needsReload: !(error instanceof AuthApiError) || error.status >= 500 }))
      return false
    }).finally(() => { busy.current = false })
  }

  return { ...state, supported, reload, save }
}
