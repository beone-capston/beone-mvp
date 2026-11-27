import type { Credentials } from './api'

export async function tryLocalPreview(credentials: Credentials) {
  if (!import.meta.env.DEV || !import.meta.env.BEONE_PREVIEW_ENABLED || import.meta.env.MODE === 'test' ||
    !['localhost', '127.0.0.1', '[::1]'].includes(window.location.hostname) ||
    window.location.protocol !== 'http:') return false

  const response = await fetch('/__beone_local_preview/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(credentials), cache: 'no-store', credentials: 'omit',
  }).catch(() => null)
  return response?.status === 204
}
