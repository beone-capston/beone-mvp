// TEMP-WEBVIEW-FIXTURE 임시 화면 번들 설정. 출력은 이 폴더의 dist/이다.
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  build: { outDir: 'dist', emptyOutDir: true },
})
