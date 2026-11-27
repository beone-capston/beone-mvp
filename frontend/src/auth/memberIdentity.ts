import { createContext } from 'react'

export const MemberIdentity = createContext<string | null>(null)

export const MemberLogout = createContext<(() => Promise<void>) | null>(null)

export const LocalPreview = createContext(false)
