import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // 개발 서버는 /api를 로컬 백엔드로 넘겨 화면과 API를 같은 origin으로 둔다(결정 37 O2-L)
  server: {
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: false },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
})
