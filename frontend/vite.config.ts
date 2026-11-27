import react from '@vitejs/plugin-react'
import { loadEnv } from 'vite'
import { localPreview } from './dev/localPreview.ts'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig(({ mode, command }) => {
  const verifier = loadEnv(mode, process.cwd(), 'BEONE_LOCAL_PREVIEW_').BEONE_LOCAL_PREVIEW_SHA256 ?? ''
  return {
    plugins: [react(), localPreview(verifier)],
    define: {
      'import.meta.env.BEONE_PREVIEW_ENABLED': JSON.stringify(command === 'serve' && /^[0-9a-f]{64}$/.test(verifier)),
    },
    server: {
      proxy: {
        '/api/': {
          target: 'http://127.0.0.1:8080',
        },
      },
    },
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
    },
  }
})
